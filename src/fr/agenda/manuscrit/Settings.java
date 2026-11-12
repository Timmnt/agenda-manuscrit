package fr.agenda.manuscrit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Réglages de l'app partagés par tous les écrans (et lus par le rendu en arrière-plan) :
 * fonds de page, animation, bouton du stylet, matières, emploi du temps, Google Agenda.
 * Les listes sont remplacées en bloc (jamais modifiées sur place).
 */
final class Settings {
    private Settings() {}

    static final int BG_LINED = 0, BG_SEYES = 1, BG_GRID = 2, BG_DOTS = 3, BG_BLANK = 4;
    static final String[] BG_NAMES = {"Ligné", "Seyès (grands carreaux)", "Petits carreaux", "Pointillés", "Blanc"};

    static final int BTN_ERASER = 0, BTN_HIGHLIGHTER = 1, BTN_LASSO = 2, BTN_UNDO = 3, BTN_NONE = 4;
    static final String[] BTN_NAMES = {"Gomme", "Surligneur", "Lasso", "Annuler", "Rien"};

    static final int[] PALETTE = {
            0xFF2B5FD0, 0xFF1E88E5, 0xFF0F8C8C, 0xFF16915A, 0xFF7CB342, 0xFFF2B300,
            0xFFE07B00, 0xFFD62F3C, 0xFFC2185B, 0xFF7B3FC4, 0xFF8D6E63, 0xFF607D8B};

    static final class Subject {
        final int id;
        final String name;
        final int color;

        Subject(int id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    /** Tampon de la palette « Insérer » : texte, couleur, contour ou plein. */
    static final class Stamp {
        final int id;
        final String text;
        final int color;
        final boolean filled;

        Stamp(int id, String text, int color, boolean filled) {
            this.id = id;
            this.text = text;
            this.color = color;
            this.filled = filled;
        }
    }

    /** Couleurs proposées pour les stylos (les 4 boutons de la barre sont réglables). */
    static final int[] PEN_PALETTE = {
            0xFF1C1D22, 0xFF5F6470, 0xFF1A2A6C, 0xFF1F5AD6, 0xFF0288D1, 0xFF0F8C8C,
            0xFF16915A, 0xFF7CB342, 0xFFF2B300, 0xFFE07B00, 0xFFD62F3C, 0xFFC2185B,
            0xFF7B3FC4, 0xFF8D6E63};
    static final int[] DEFAULT_PEN_COLORS = {0xFF1C1D22, 0xFF1F5AD6, 0xFFD62F3C, 0xFF16915A};

    static final class Slot {
        final int dow;        // 0 = lundi
        final int start, end; // minutes depuis minuit
        final int subjectId;
        final String room;

        Slot(int dow, int start, int end, int subjectId, String room) {
            this.dow = dow;
            this.start = start;
            this.end = end;
            this.subjectId = subjectId;
            this.room = room == null ? "" : room;
        }
    }

    static volatile int bgAgenda = BG_LINED;
    static volatile int bgVrac = BG_DOTS;
    static volatile boolean pageTurn = true;
    static volatile boolean showSchedule = true;
    static volatile int stylusButton = BTN_ERASER;
    static volatile List<Subject> subjects = defaultSubjects();
    static volatile List<Stamp> stamps = defaultStamps();
    /** Autocollants ajoutés par l'utilisateur (emoji ou quelques caractères). */
    static volatile List<String> emojis = defaultEmojis();
    static volatile int[] penColors = DEFAULT_PEN_COLORS.clone();
    static volatile List<Slot> slots = Collections.emptyList();
    /** Événements de Google Agenda (calendrier de la tablette) dans les pastilles. */
    static volatile boolean google = false;
    /** Agendas choisis à la main ; null = choix automatique (agendas visibles, sans anniversaires ni météo). */
    static volatile long[] calendarIds = null;
    /** Incrémenté à chaque changement : les pages et miniatures se redessinent. */
    static volatile int revision;

    private static SharedPreferences prefs;

    static synchronized void init(Context c) {
        if (prefs != null) return;
        prefs = c.getApplicationContext().getSharedPreferences("agenda", Context.MODE_PRIVATE);
        bgAgenda = clamp(prefs.getInt("bgAgenda", BG_LINED), BG_NAMES.length);
        bgVrac = clamp(prefs.getInt("bgVrac", BG_DOTS), BG_NAMES.length);
        pageTurn = prefs.getBoolean("pageTurn", true);
        showSchedule = prefs.getBoolean("showSchedule", true);
        stylusButton = clamp(prefs.getInt("stylusButton", BTN_ERASER), BTN_NAMES.length);
        String subj = prefs.getString("subjects", null);
        if (subj != null) subjects = decodeSubjects(subj);
        String st = prefs.getString("stamps", null);
        if (st != null) stamps = decodeStamps(st);
        String em = prefs.getString("emojis", null);
        if (em != null) emojis = decodeEmojis(em);
        String pc = prefs.getString("penColors", null);
        if (pc != null) penColors = decodePenColors(pc);
        String sl = prefs.getString("slots", null);
        if (sl != null) slots = decodeSlots(sl);
        google = prefs.getBoolean("google", false);
        String ids = prefs.getString("calendarIds", null);
        calendarIds = ids == null ? null : decodeIds(ids);
    }

    private static int clamp(int v, int n) {
        return v < 0 || v >= n ? 0 : v;
    }

    static void bump() {
        revision++;
    }

    static void setBackground(boolean vrac, int bg) {
        if (vrac) bgVrac = bg;
        else bgAgenda = bg;
        if (prefs != null) prefs.edit().putInt(vrac ? "bgVrac" : "bgAgenda", bg).apply();
        bump();
    }

    static void setPageTurn(boolean on) {
        pageTurn = on;
        if (prefs != null) prefs.edit().putBoolean("pageTurn", on).apply();
    }

    static void setShowSchedule(boolean on) {
        showSchedule = on;
        if (prefs != null) prefs.edit().putBoolean("showSchedule", on).apply();
        bump();
    }

    static void setGoogle(boolean on) {
        google = on;
        if (prefs != null) prefs.edit().putBoolean("google", on).apply();
        bump();
    }

    static void setCalendarIds(long[] ids) {
        calendarIds = ids == null ? null : ids.clone();
        if (prefs != null) {
            if (ids == null) prefs.edit().remove("calendarIds").apply();
            else prefs.edit().putString("calendarIds", encodeIds(ids)).apply();
        }
        bump();
    }

    /** Le bandeau du haut de page (cours et / ou événements) a-t-il quelque chose à montrer ? */
    static boolean bandVisible() {
        return showSchedule || google;
    }

    static void setStylusButton(int b) {
        stylusButton = b;
        if (prefs != null) prefs.edit().putInt("stylusButton", b).apply();
    }

    static void setSubjects(List<Subject> list) {
        subjects = Collections.unmodifiableList(new ArrayList<>(list));
        if (prefs != null) prefs.edit().putString("subjects", encodeSubjects(subjects)).apply();
        bump();
    }

    static void setSlots(List<Slot> list) {
        ArrayList<Slot> copy = new ArrayList<>(list);
        sortSlots(copy);
        slots = Collections.unmodifiableList(copy);
        if (prefs != null) prefs.edit().putString("slots", encodeSlots(slots)).apply();
        bump();
    }

    static Subject subject(int id) {
        for (Subject s : subjects) if (s.id == id) return s;
        return null;
    }

    static int nextSubjectId() {
        int m = 0;
        for (Subject s : subjects) m = Math.max(m, s.id);
        return m + 1;
    }

    static List<Slot> slotsFor(int dow) {
        ArrayList<Slot> out = new ArrayList<>();
        for (Slot s : slots) if (s.dow == dow) out.add(s);
        return out;
    }

    static void sortSlots(List<Slot> list) {
        Collections.sort(list, new Comparator<Slot>() {
            @Override
            public int compare(Slot a, Slot b) {
                if (a.dow != b.dow) return a.dow - b.dow;
                if (a.start != b.start) return a.start - b.start;
                return a.end - b.end;
            }
        });
    }

    /** 480 -> "8h", 615 -> "10h15". */
    static String fmtTime(int min) {
        int h = min / 60, m = min % 60;
        return m == 0 ? h + "h" : h + "h" + (m < 10 ? "0" : "") + m;
    }

    static List<Subject> defaultSubjects() {
        ArrayList<Subject> l = new ArrayList<>();
        l.add(new Subject(1, "Maths", 0xFF2B5FD0));
        l.add(new Subject(2, "Physique", 0xFF7B3FC4));
        l.add(new Subject(3, "Électronique", 0xFFE07B00));
        l.add(new Subject(4, "Automatique", 0xFF0F8C8C));
        l.add(new Subject(5, "Informatique", 0xFF16915A));
        l.add(new Subject(6, "Anglais", 0xFFD62F3C));
        l.add(new Subject(7, "Projet", 0xFFC2185B));
        return Collections.unmodifiableList(l);
    }

    static List<Stamp> defaultStamps() {
        ArrayList<Stamp> l = new ArrayList<>();
        l.add(new Stamp(1, "DS", 0xFFD62F3C, false));
        l.add(new Stamp(2, "TP", 0xFF16915A, false));
        l.add(new Stamp(3, "RENDU", 0xFFE07B00, false));
        l.add(new Stamp(4, "EXAM", 0xFF7B3FC4, false));
        l.add(new Stamp(5, "IMPORTANT", 0xFFD62F3C, true));
        return Collections.unmodifiableList(l);
    }

    static List<String> defaultEmojis() {
        ArrayList<String> l = new ArrayList<>();
        String[] e = {"\uD83D\uDCDA", "\u270F\uFE0F", "\u23F0", "\uD83D\uDCCC", "\uD83C\uDFAF", "\uD83C\uDF89"};
        for (String x : e) l.add(x);
        return Collections.unmodifiableList(l);
    }

    static void setStamps(List<Stamp> list) {
        stamps = Collections.unmodifiableList(new ArrayList<>(list));
        if (prefs != null) prefs.edit().putString("stamps", encodeStamps(stamps)).apply();
    }

    static int nextStampId() {
        int m = 0;
        for (Stamp s : stamps) m = Math.max(m, s.id);
        return m + 1;
    }

    static void setEmojis(List<String> list) {
        emojis = Collections.unmodifiableList(new ArrayList<>(list));
        if (prefs != null) prefs.edit().putString("emojis", encodeEmojis(emojis)).apply();
    }

    static void setPenColor(int index, int color) {
        int[] p = penColors.clone();
        if (index < 0 || index >= p.length) return;
        p[index] = color;
        penColors = p;
        if (prefs != null) prefs.edit().putString("penColors", encodePenColors(p)).apply();
    }

    // ------------------------------------------------------------------ encodage (texte simple)

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    static String encodeSubjects(List<Subject> list) {
        StringBuilder sb = new StringBuilder();
        for (Subject s : list) sb.append(s.id).append('\t').append(s.color).append('\t').append(clean(s.name)).append('\n');
        return sb.toString();
    }

    static List<Subject> decodeSubjects(String str) {
        ArrayList<Subject> out = new ArrayList<>();
        for (String line : str.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 3) continue;
            try {
                out.add(new Subject(Integer.parseInt(f[0]), f[2], Integer.parseInt(f[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        return Collections.unmodifiableList(out);
    }

    static String encodeStamps(List<Stamp> list) {
        StringBuilder sb = new StringBuilder();
        for (Stamp s : list) {
            sb.append(s.id).append('\t').append(s.color).append('\t').append(s.filled ? 1 : 0).append('\t')
                    .append(clean(s.text)).append('\n');
        }
        return sb.toString();
    }

    static List<Stamp> decodeStamps(String str) {
        ArrayList<Stamp> out = new ArrayList<>();
        for (String line : str.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 4 || f[3].trim().isEmpty()) continue;
            try {
                out.add(new Stamp(Integer.parseInt(f[0]), f[3], Integer.parseInt(f[1]), "1".equals(f[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        return Collections.unmodifiableList(out);
    }

    static String encodeEmojis(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String e : list) sb.append(clean(e)).append('\n');
        return sb.toString();
    }

    static List<String> decodeEmojis(String str) {
        ArrayList<String> out = new ArrayList<>();
        for (String line : str.split("\n")) if (!line.trim().isEmpty()) out.add(line.trim());
        return Collections.unmodifiableList(out);
    }

    static String encodePenColors(int[] c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) sb.append(i == 0 ? "" : ",").append(c[i]);
        return sb.toString();
    }

    static int[] decodePenColors(String str) {
        int[] out = DEFAULT_PEN_COLORS.clone();
        String[] f = str.split(",");
        for (int i = 0; i < Math.min(f.length, out.length); i++) {
            try {
                out[i] = Integer.parseInt(f[i].trim()) | 0xFF000000;
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static String encodeIds(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        return sb.toString();
    }

    static long[] decodeIds(String str) {
        ArrayList<Long> l = new ArrayList<>();
        for (String f : str.split(",")) {
            try {
                if (!f.trim().isEmpty()) l.add(Long.parseLong(f.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        long[] out = new long[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    static String encodeSlots(List<Slot> list) {
        StringBuilder sb = new StringBuilder();
        for (Slot s : list) {
            sb.append(s.dow).append('\t').append(s.start).append('\t').append(s.end).append('\t')
                    .append(s.subjectId).append('\t').append(clean(s.room)).append('\n');
        }
        return sb.toString();
    }

    static List<Slot> decodeSlots(String str) {
        ArrayList<Slot> out = new ArrayList<>();
        for (String line : str.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 5) continue;
            try {
                int dow = Integer.parseInt(f[0]), st = Integer.parseInt(f[1]), en = Integer.parseInt(f[2]);
                if (dow < 0 || dow > 6 || st < 0 || en > 24 * 60 || en <= st) continue;
                out.add(new Slot(dow, st, en, Integer.parseInt(f[3]), f[4]));
            } catch (NumberFormatException ignored) {
            }
        }
        sortSlots(out);
        return Collections.unmodifiableList(out);
    }
}
