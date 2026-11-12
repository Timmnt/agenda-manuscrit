package fr.agenda.manuscrit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Enregistre chaque page dans un fichier : "pages/AAAA-MM-JJ.ink" pour l'agenda, "vrac/0001.ink" pour
 * le carnet "En vrac". Gère aussi l'historique annuler / rétablir (en mémoire) et un résumé par jour
 * (cases cochées, matières) pour les points du calendrier et les statistiques.
 * Les méthodes s'appellent depuis le thread principal ; l'écriture se fait sur un thread dédié.
 */
final class PageStore {
    interface Listener {
        /** @param key la page modifiée, ou ALL quand tout est à redessiner. */
        void onPageChanged(long key);
    }

    static final long ALL = Long.MIN_VALUE;

    /** Résumé d'un jour : cases à cocher et matières posées (identifiants, 3 au plus). */
    static final class Summary {
        static final Summary EMPTY = new Summary(0, 0, new int[0]);
        final int boxes, checked;
        final int[] subjects;

        Summary(int boxes, int checked, int[] subjects) {
            this.boxes = boxes;
            this.checked = checked;
            this.subjects = subjects;
        }

        static Summary of(List<Item> items) {
            int boxes = 0, checked = 0, nc = 0;
            int[] cols = new int[3];
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (!(it instanceof Obj)) continue;
                Obj o = (Obj) it;
                if (o.type == Obj.BOX) {
                    boxes++;
                    if (o.checked) checked++;
                } else if (o.type == Obj.SUBJECT && nc < cols.length) {
                    boolean seen = false;
                    for (int k = 0; k < nc; k++) if (cols[k] == o.kind) seen = true;
                    if (!seen) cols[nc++] = o.kind;
                }
            }
            if (boxes == 0 && nc == 0) return EMPTY;
            int[] c = new int[nc];
            System.arraycopy(cols, 0, c, 0, nc);
            return new Summary(boxes, checked, c);
        }
    }

    private static final String TAG = "AgendaStore";
    static final int MAGIC = 0x41474431; // "AGD1"
    static final int FORMAT_V1 = 1, FORMAT = 2;
    private static final int TAG_STROKE = 1, TAG_OBJ = 2;
    private static final int MAX_UNDO = 200;
    private static PageStore instance;

    static synchronized PageStore get(Context c) {
        if (instance == null) instance = new PageStore(c.getApplicationContext());
        return instance;
    }

    private final File dayDir, vracDir;
    private final HashSet<Long> nonEmpty = new HashSet<>();
    private final HashMap<Long, Summary> summaries = new HashMap<>();
    private boolean summariesLoaded;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final HashMap<Long, Integer> latestQueued = new HashMap<>();
    private final Object queueLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final LinkedHashMap<Long, Page> cache = new LinkedHashMap<Long, Page>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Page> eldest) {
            // une page avec un historique reste en mémoire (une autre page peut y faire référence)
            return size() > 300 && !eldest.getValue().hasHistory();
        }
    };

    private PageStore(Context c) {
        dayDir = new File(c.getFilesDir(), "pages");
        vracDir = new File(c.getFilesDir(), "vrac");
        if (!dayDir.exists() && !dayDir.mkdirs()) Log.w(TAG, "mkdirs failed");
        if (!vracDir.exists() && !vracDir.mkdirs()) Log.w(TAG, "mkdirs failed");
        scanDir(dayDir, false);
        scanDir(vracDir, true);
        startSummaryScan();
    }

    private void scanDir(File dir, boolean vrac) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".tmp")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                continue;
            }
            if (!name.endsWith(".ink") || f.length() <= 12) continue;
            String base = name.substring(0, name.length() - 4);
            if (vrac) {
                try {
                    int n = Integer.parseInt(base);
                    if (n >= 1) nonEmpty.add(Geo.vrac(n));
                } catch (NumberFormatException ignored) {
                }
            } else {
                long d = Days.parseKey(base);
                if (d != Long.MIN_VALUE) nonEmpty.add(d);
            }
        }
    }

    /** Lit l'en-tête de chaque jour (rapide) pour les points du calendrier et les statistiques. */
    private void startSummaryScan() {
        final ArrayList<Long> keys = new ArrayList<>();
        for (Long k : nonEmpty) if (!Geo.isVrac(k)) keys.add(k);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final HashMap<Long, Summary> found = new HashMap<>();
                for (Long k : keys) {
                    Summary s = readSummary(fileFor(k));
                    if (s != null) found.put(k, s);
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Map.Entry<Long, Summary> e : found.entrySet()) {
                            // une page modifiée entre-temps a déjà un résumé plus récent
                            if (!summaries.containsKey(e.getKey()) && nonEmpty.contains(e.getKey())) {
                                summaries.put(e.getKey(), e.getValue());
                            }
                        }
                        summariesLoaded = true;
                        notifyListeners(ALL);
                    }
                });
            }
        }, "agenda-scan");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    void addListener(Listener l) { listeners.add(l); }

    void removeListener(Listener l) { listeners.remove(l); }

    private void notifyListeners(long key) {
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).onPageChanged(key);
    }

    boolean hasContent(long key) { return nonEmpty.contains(key); }

    /** Résumé du jour (null si inconnu ou vide). */
    Summary summary(long day) { return summaries.get(day); }

    boolean summariesLoaded() { return summariesLoaded; }

    /** Copie des résumés de tous les jours connus. */
    HashMap<Long, Summary> summaries() { return new HashMap<>(summaries); }

    /** Numéro de la dernière page "En vrac" non vide (0 si aucune). */
    int lastVracPage() {
        int m = 0;
        for (Long k : nonEmpty) if (Geo.isVrac(k)) m = Math.max(m, Geo.vracIndex(k));
        return m;
    }

    Page page(long key) {
        Page p = cache.get(key);
        if (p == null) {
            p = new Page(key);
            if (nonEmpty.contains(key)) read(fileFor(key), p.items);
            cache.put(key, p);
        }
        return p;
    }

    /** À appeler après toute modification d'une page : sauvegarde + notification. */
    void changed(Page p) {
        p.version++;
        cache.put(p.key, p); // cette instance reste la référence pour cette page
        final long key = p.key;
        if (p.items.isEmpty()) nonEmpty.remove(key);
        else nonEmpty.add(key);
        if (!Geo.isVrac(key)) {
            if (p.items.isEmpty()) summaries.remove(key);
            else summaries.put(key, Summary.of(p.items));
        }
        final int version = p.version;
        final ArrayList<Item> snapshot = new ArrayList<>(p.items);
        synchronized (queueLock) {
            latestQueued.put(key, version);
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (queueLock) {
                    Integer latest = latestQueued.get(key);
                    if (latest != null && latest != version) return; // une version plus récente suit
                    latestQueued.remove(key);
                }
                write(key, snapshot);
            }
        });
        notifyListeners(key);
    }

    // ---------------------------------------------------------------- annuler / rétablir

    /** Enregistre une modification déjà appliquée (sur une ou plusieurs pages). */
    void commit(Edit e) {
        if (e.isEmpty()) return;
        Set<Page> pages = e.pages();
        for (Page p : pages) {
            dropRedo(p);
            p.undo.add(e);
            if (p.undo.size() > MAX_UNDO) {
                Edit old = p.undo.remove(0);
                for (Page q : old.pages()) if (q != p) q.undo.remove(old);
            }
        }
        for (Page p : pages) changed(p);
    }

    private static void dropRedo(Page p) {
        for (Edit r : p.redo) {
            for (Page q : r.pages()) if (q != p) q.redo.remove(r);
        }
        p.redo.clear();
    }

    /** Annule la dernière modification de cette page. @return les pages touchées. */
    Set<Page> undo(Page p) {
        if (p.undo.isEmpty()) return Collections.emptySet();
        Edit e = p.undo.get(p.undo.size() - 1);
        e.undo();
        Set<Page> pages = e.pages();
        for (Page q : pages) {
            q.undo.remove(e);
            q.redo.add(e);
        }
        for (Page q : pages) changed(q);
        return pages;
    }

    Set<Page> redo(Page p) {
        if (p.redo.isEmpty()) return Collections.emptySet();
        Edit e = p.redo.get(p.redo.size() - 1);
        e.redo();
        Set<Page> pages = e.pages();
        for (Page q : pages) {
            q.redo.remove(e);
            q.undo.add(e);
        }
        for (Page q : pages) changed(q);
        return pages;
    }

    /** Attend la fin des écritures en cours (appelé en onPause). */
    void flush() {
        try {
            Future<?> f = io.submit(new Runnable() {
                @Override
                public void run() {}
            });
            f.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w(TAG, "flush", e);
        }
    }

    File fileFor(long key) {
        if (Geo.isVrac(key)) return new File(vracDir, String.format(Locale.US, "%04d.ink", Geo.vracIndex(key)));
        return new File(dayDir, Days.key(key) + ".ink");
    }

    // ---------------------------------------------------------------- disque

    private void write(long key, List<Item> items) {
        File target = fileFor(key);
        if (items.isEmpty()) {
            if (target.exists() && !target.delete()) Log.w(TAG, "delete failed " + target);
            return;
        }
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        DataOutputStream out = null;
        try {
            FileOutputStream fos = new FileOutputStream(tmp);
            out = new DataOutputStream(new BufferedOutputStream(fos, 1 << 16));
            writeItems(out, items);
            out.flush();
            fos.getFD().sync();
            out.close();
            out = null;
            if (!tmp.renameTo(target)) Log.e(TAG, "rename failed " + target);
        } catch (IOException e) {
            Log.e(TAG, "write " + target, e);
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
        }
    }

    static void writeItems(DataOutputStream out, List<Item> items) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(FORMAT);
        Summary s = Summary.of(items);
        out.writeInt(s.boxes);
        out.writeInt(s.checked);
        out.writeByte(s.subjects.length);
        for (int c : s.subjects) out.writeInt(c);
        out.writeInt(items.size());
        for (Item it : items) {
            if (it instanceof Stroke) {
                Stroke st = (Stroke) it;
                out.writeByte(TAG_STROKE);
                out.writeInt(st.color);
                out.writeFloat(st.width);
                out.writeInt(st.flags);
                out.writeInt(st.n);
                float[] a = st.pts;
                for (int i = 0, m = st.n * 3; i < m; i++) out.writeFloat(a[i]);
            } else {
                Obj o = (Obj) it;
                out.writeByte(TAG_OBJ);
                out.writeByte(o.type);
                out.writeInt(o.kind);
                out.writeInt(o.color);
                out.writeFloat(o.x);
                out.writeFloat(o.y);
                out.writeFloat(o.w);
                out.writeFloat(o.h);
                out.writeBoolean(o.checked);
                out.writeUTF(o.text);
            }
        }
    }

    /** Lit une page (format 1 = traits seuls des versions précédentes, ou format 2). */
    static void read(File f, List<Item> into) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16));
            if (in.readInt() != MAGIC) return;
            int format = in.readInt();
            if (format == FORMAT_V1) {
                int count = in.readInt();
                for (int k = 0; k < count; k++) {
                    Stroke s = readStroke(in);
                    if (s == null) return;
                    into.add(s);
                }
                return;
            }
            if (format != FORMAT) return;
            in.readInt();
            in.readInt();
            int nc = in.readUnsignedByte();
            for (int k = 0; k < nc; k++) in.readInt();
            int count = in.readInt();
            for (int k = 0; k < count; k++) {
                int tag = in.readUnsignedByte();
                if (tag == TAG_STROKE) {
                    Stroke s = readStroke(in);
                    if (s == null) return;
                    into.add(s);
                } else if (tag == TAG_OBJ) {
                    int type = in.readUnsignedByte();
                    int kind = in.readInt();
                    int color = in.readInt();
                    float x = in.readFloat(), y = in.readFloat(), w = in.readFloat(), h = in.readFloat();
                    boolean checked = in.readBoolean();
                    String text = in.readUTF();
                    into.add(new Obj(type, kind, color, x, y, w, h, checked, text));
                } else {
                    return;
                }
            }
        } catch (EOFException e) {
            Log.w(TAG, "fichier tronqué " + f + " : " + into.size() + " éléments récupérés");
        } catch (IOException e) {
            Log.e(TAG, "read " + f, e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static Stroke readStroke(DataInputStream in) throws IOException {
        int color = in.readInt();
        float width = in.readFloat();
        int flags = in.readInt();
        int n = in.readInt();
        if (n < 0 || n > 2_000_000) return null;
        Stroke s = new Stroke(color, width, flags, n);
        for (int i = 0; i < n; i++) s.add(in.readFloat(), in.readFloat(), in.readFloat());
        s.trim();
        return s;
    }

    /** Lit seulement le résumé en tête de fichier (null si illisible). */
    static Summary readSummary(File f) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 256));
            if (in.readInt() != MAGIC) return null;
            int format = in.readInt();
            if (format == FORMAT_V1) return Summary.EMPTY;
            if (format != FORMAT) return null;
            int boxes = in.readInt(), checked = in.readInt();
            int nc = in.readUnsignedByte();
            int[] c = new int[nc];
            for (int k = 0; k < nc; k++) c[k] = in.readInt();
            return new Summary(boxes, checked, c);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }
}
