package fr.agenda.manuscrit;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.CalendarContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Lecture seule de l'agenda de la tablette (Google Agenda synchronisé par le compte Google).
 * Les événements sont lus par blocs de 32 jours en arrière-plan et gardés en mémoire ; le rendu des
 * pages ne fait jamais attendre : il dessine ce qui est déjà là, puis la page se redessine à l'arrivée.
 */
final class CalendarSource {
    private CalendarSource() {}

    private static final String TAG = "AgendaCal";
    private static final int BLOCK = 32;
    private static final int KEEP_BLOCKS = 10;
    private static final List<CalEvents.DayEv> NONE = Collections.emptyList();

    /** Un agenda présent sur la tablette. */
    static final class Cal {
        final long id;
        final String name, account, owner;
        final int color;
        final boolean visible, synced;

        Cal(long id, String name, String account, String owner, int color, boolean visible, boolean synced) {
            this.id = id;
            this.name = name == null || name.trim().isEmpty() ? "Agenda" : name.trim();
            this.account = account == null ? "" : account;
            this.owner = owner == null ? "" : owner;
            this.color = color;
            this.visible = visible;
            this.synced = synced;
        }

        /** Anniversaires des contacts et météo : pas utiles dans un agenda de cours. */
        boolean noise() {
            return owner.contains("#contacts@") || owner.contains("#weather@");
        }

        /** Coché par défaut tant que l'on n'a pas choisi soi-même. */
        boolean autoOn() {
            return visible && synced && !noise();
        }
    }

    interface Listener {
        void onCalendarChanged();
    }

    private static final class Block {
        int gen = -1;
        boolean loading;
        long lastUse;
        Map<Long, List<CalEvents.DayEv>> byDay; // null tant que jamais lu
    }

    private static Context app;
    private static Handler main;
    private static ExecutorService exec;
    private static ContentObserver observer;
    private static volatile boolean permitted;
    private static final Object lock = new Object();
    private static final HashMap<Long, Block> blocks = new HashMap<>();
    private static int generation;
    private static long useClock;
    private static final ArrayList<Listener> listeners = new ArrayList<>(); // fil principal uniquement

    private static final Runnable invalidateLater = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    static synchronized void init(Context c) {
        if (app == null) {
            app = c.getApplicationContext();
            main = new Handler(Looper.getMainLooper());
            exec = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "agenda-google");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });
        }
        refreshPermission();
    }

    static boolean hasPermission(Context c) {
        return c.checkPermission(Manifest.permission.READ_CALENDAR, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    /** À rappeler au retour dans l'app (l'autorisation a pu être donnée dans les réglages d'Android). */
    static void refreshPermission() {
        if (app == null) return;
        boolean now = hasPermission(app);
        boolean was = permitted;
        permitted = now;
        if (now && observer == null && Looper.myLooper() == Looper.getMainLooper()) {
            observer = new ContentObserver(main) {
                @Override
                public void onChange(boolean selfChange) {
                    // une synchronisation envoie souvent plusieurs signaux d'affilée
                    main.removeCallbacks(invalidateLater);
                    main.postDelayed(invalidateLater, 800);
                }
            };
            try {
                app.getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI, true, observer);
            } catch (RuntimeException e) {
                Log.w(TAG, "observer", e);
                observer = null;
            }
        }
        if (now != was) invalidate();
    }

    static boolean enabled() {
        return app != null && permitted && Settings.google;
    }

    static void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------------ événements d'un jour

    /** Ce qui est déjà connu pour ce jour ; lance la lecture si besoin. Appelable depuis n'importe quel fil. */
    static List<CalEvents.DayEv> eventsFor(long day) {
        if (!enabled()) return NONE;
        long b = CalEvents.floorDiv(day, BLOCK);
        synchronized (lock) {
            Block bl = blocks.get(b);
            if (bl == null) {
                bl = new Block();
                blocks.put(b, bl);
                trim();
            }
            bl.lastUse = ++useClock;
            if (bl.gen != generation && !bl.loading) schedule(b, bl);
            if (bl.byDay == null) return NONE;
            List<CalEvents.DayEv> l = bl.byDay.get(day);
            return l == null ? NONE : l;
        }
    }

    /** Pour le widget : lit tout de suite le bloc du jour s'il n'est pas à jour. */
    static void loadSync(long day) {
        if (!enabled()) return;
        long b = CalEvents.floorDiv(day, BLOCK);
        int gen;
        synchronized (lock) {
            Block bl = blocks.get(b);
            if (bl != null && bl.gen == generation) return;
            gen = generation;
        }
        Map<Long, List<CalEvents.DayEv>> res = query(b);
        synchronized (lock) {
            Block bl = blocks.get(b);
            if (bl == null) {
                bl = new Block();
                blocks.put(b, bl);
                trim();
            }
            bl.byDay = res;
            bl.gen = gen;
            bl.lastUse = ++useClock;
        }
    }

    /** L'agenda a changé (synchronisation, autre choix d'agendas...) : on relit ce qui est affiché. */
    static void invalidate() {
        synchronized (lock) {
            generation++;
            for (Map.Entry<Long, Block> en : blocks.entrySet()) {
                Block bl = en.getValue();
                if (!bl.loading && enabled()) schedule(en.getKey(), bl);
            }
        }
    }

    /** Garde les blocs les plus récemment utilisés (appelé sous verrou). */
    private static void trim() {
        while (blocks.size() > KEEP_BLOCKS) {
            Long oldest = null;
            long t = Long.MAX_VALUE;
            for (Map.Entry<Long, Block> en : blocks.entrySet()) {
                if (en.getValue().lastUse < t && !en.getValue().loading) {
                    t = en.getValue().lastUse;
                    oldest = en.getKey();
                }
            }
            if (oldest == null) return;
            blocks.remove(oldest);
        }
    }

    /** Appelé sous verrou. */
    private static void schedule(final long b, Block bl) {
        if (exec == null) return;
        bl.loading = true;
        final int gen = generation;
        exec.execute(new Runnable() {
            @Override
            public void run() {
                Map<Long, List<CalEvents.DayEv>> res = query(b);
                boolean changed;
                synchronized (lock) {
                    Block cur = blocks.get(b);
                    if (cur == null) return;
                    cur.loading = false;
                    changed = cur.byDay == null ? !res.isEmpty() : !cur.byDay.equals(res);
                    cur.byDay = res;
                    cur.gen = gen;
                    if (gen != generation && enabled()) schedule(b, cur);
                }
                if (changed) main.post(fireChanged);
            }
        });
    }

    private static final Runnable fireChanged = new Runnable() {
        @Override
        public void run() {
            Settings.bump(); // pages et miniatures se redessinent
            for (Listener l : new ArrayList<>(listeners)) l.onCalendarChanged();
        }
    };

    // ------------------------------------------------------------------ requêtes

    private static final String[] INST_COLS = {
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.END_DAY,
            CalendarContract.Instances.START_MINUTE,
            CalendarContract.Instances.END_MINUTE,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
    };

    /** Les événements des 32 jours du bloc, rangés par jour. */
    private static Map<Long, List<CalEvents.DayEv>> query(long b) {
        HashMap<Long, List<CalEvents.DayEv>> out = new HashMap<>();
        if (app == null || !permitted) return out;
        long from = b * BLOCK, to = from + BLOCK - 1;
        Cursor cur = null;
        try {
            ContentResolver cr = app.getContentResolver();
            long[] ids = selectedIds(cr);
            if (ids.length == 0) return out;
            StringBuilder sel = new StringBuilder(CalendarContract.Instances.CALENDAR_ID).append(" IN (");
            for (int i = 0; i < ids.length; i++) sel.append(i == 0 ? "" : ",").append(ids[i]);
            sel.append(')');
            Uri.Builder ub = CalendarContract.Instances.CONTENT_BY_DAY_URI.buildUpon();
            ContentUris.appendId(ub, CalEvents.toJulian(from));
            ContentUris.appendId(ub, CalEvents.toJulian(to));
            cur = cr.query(ub.build(), INST_COLS, sel.toString(), null,
                    CalendarContract.Instances.START_DAY + "," + CalendarContract.Instances.START_MINUTE);
            if (cur == null) return out;
            while (cur.moveToNext()) {
                if (!cur.isNull(8) && cur.getInt(8) == CalendarContract.Events.STATUS_CANCELED) continue;
                if (!cur.isNull(9) && cur.getInt(9) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue;
                int color = cur.isNull(7) ? 0 : (cur.getInt(7) | 0xFF000000);
                CalEvents.Inst in = new CalEvents.Inst(
                        CalEvents.fromJulian(cur.getLong(3)), cur.getInt(5),
                        CalEvents.fromJulian(cur.getLong(4)), cur.getInt(6),
                        cur.getInt(2) != 0, cur.getString(0), cur.getString(1), color);
                CalEvents.spread(in, from, to, out);
            }
        } catch (RuntimeException e) { // autorisation retirée, fournisseur absent...
            Log.w(TAG, "query", e);
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    private static long[] selectedIds(ContentResolver cr) {
        long[] chosen = Settings.calendarIds;
        if (chosen != null) return chosen;
        ArrayList<Long> l = new ArrayList<>();
        for (Cal c : calendars(cr)) if (c.autoOn()) l.add(c.id);
        long[] out = new long[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** Agendas de la tablette, rangés par compte. */
    static List<Cal> calendars(Context c) {
        if (!hasPermission(c)) return new ArrayList<>();
        return calendars(c.getContentResolver());
    }

    private static List<Cal> calendars(ContentResolver cr) {
        ArrayList<Cal> out = new ArrayList<>();
        String[] cols = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS,
        };
        Cursor cur = null;
        try {
            cur = cr.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null,
                    CalendarContract.Calendars.ACCOUNT_NAME + "," + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
            if (cur == null) return out;
            while (cur.moveToNext()) {
                out.add(new Cal(cur.getLong(0), cur.getString(1), cur.getString(2), cur.getString(3),
                        cur.isNull(4) ? 0xFF9AA1AD : (cur.getInt(4) | 0xFF000000),
                        cur.isNull(5) || cur.getInt(5) != 0,
                        cur.isNull(6) || cur.getInt(6) != 0));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "calendars", e);
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    /** Ids cochés pour l'affichage du choix (choix manuel ou automatique). */
    static boolean isSelected(Cal c) {
        long[] chosen = Settings.calendarIds;
        if (chosen == null) return c.autoOn();
        for (long id : chosen) if (id == c.id) return true;
        return false;
    }
}
