package fr.agenda.manuscrit;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Événements de l'agenda du téléphone (Google Agenda) ramenés jour par jour, puis fusionnés avec
 * l'emploi du temps pour les pastilles du haut de page. Pur Java : testable hors Android.
 */
final class CalEvents {
    private CalEvents() {}

    /** Jour julien du 1er janvier 1970 (le calendrier d'Android compte les jours ainsi). */
    static final long JULIAN_EPOCH = 2440588L;
    /** Un événement de plus de deux mois n'est pas étalé au-delà (vacances, semestre...). */
    static final int MAX_SPAN = 62;
    static final int DAY_MIN = 24 * 60;

    /** Une occurrence telle que la donne le calendrier : jours (depuis 1970) et minutes, heure locale. */
    static final class Inst {
        final long startDay, endDay;
        final int startMin, endMin;
        final boolean allDay;
        final String title, location;
        final int color;

        Inst(long startDay, int startMin, long endDay, int endMin, boolean allDay, String title, String location, int color) {
            this.startDay = startDay;
            this.startMin = startMin;
            this.endDay = Math.max(startDay, endDay);
            this.endMin = endMin;
            this.allDay = allDay;
            this.title = clean(title);
            this.location = clean(location);
            this.color = color;
        }
    }

    /** Un événement vu depuis un jour donné. */
    static final class DayEv {
        final int start, end;     // minutes de ce jour, 0..1440
        final boolean allDay;     // toute la journée (ou journée entière au milieu d'un long événement)
        final boolean fromPrev;   // a commencé un jour précédent
        final String title, location;
        final int color;

        DayEv(int start, int end, boolean allDay, boolean fromPrev, String title, String location, int color) {
            this.start = start;
            this.end = end;
            this.allDay = allDay;
            this.fromPrev = fromPrev;
            this.title = title;
            this.location = location;
            this.color = color;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DayEv)) return false;
            DayEv e = (DayEv) o;
            return start == e.start && end == e.end && allDay == e.allDay && fromPrev == e.fromPrev
                    && color == e.color && title.equals(e.title) && location.equals(e.location);
        }

        @Override
        public int hashCode() {
            return ((start * 31 + end) * 31 + title.hashCode()) * 31 + color;
        }
    }

    /** Une pastille en haut de page : un cours de l'emploi du temps ou un événement Google. */
    static final class Chip {
        final boolean allDay, google, fromPrev;
        final int start, end;
        final String title, room;
        final int color;

        Chip(boolean allDay, boolean google, boolean fromPrev, int start, int end, String title, String room, int color) {
            this.allDay = allDay;
            this.google = google;
            this.fromPrev = fromPrev;
            this.start = start;
            this.end = end;
            this.title = title;
            this.room = room;
            this.color = color;
        }
    }

    static String clean(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    static long fromJulian(long julian) {
        return julian - JULIAN_EPOCH;
    }

    static long toJulian(long epochDay) {
        return epochDay + JULIAN_EPOCH;
    }

    /** Division entière vers le bas (Math.floorDiv n'existe qu'à partir d'Android 7). */
    static long floorDiv(long a, long b) {
        long q = a / b;
        if ((a % b != 0) && ((a < 0) != (b < 0))) q--;
        return q;
    }

    /** Répartit une occurrence sur les jours [from, to] qu'elle touche. */
    static void spread(Inst in, long from, long to, Map<Long, List<DayEv>> out) {
        long a = Math.max(from, in.startDay);
        long b = Math.min(to, Math.min(in.endDay, in.startDay + MAX_SPAN));
        if (in.title.isEmpty() && in.location.isEmpty()) return;
        String title = in.title.isEmpty() ? L.s("(Sans titre)", "(No title)") : in.title;
        for (long d = a; d <= b; d++) {
            DayEv e;
            if (in.allDay) {
                e = new DayEv(0, DAY_MIN, true, d > in.startDay, title, in.location, in.color);
            } else {
                int s = d == in.startDay ? clampMin(in.startMin) : 0;
                int t = d == in.endDay ? clampMin(in.endMin) : DAY_MIN;
                if (t < s) t = s;
                boolean whole = s == 0 && t >= DAY_MIN && (in.endDay > in.startDay);
                if (d == in.endDay && d > in.startDay && t == 0) continue; // finit à minuit pile
                e = new DayEv(s, t, whole, d > in.startDay, title, in.location, in.color);
            }
            List<DayEv> l = out.get(d);
            if (l == null) {
                l = new ArrayList<>();
                out.put(d, l);
            }
            l.add(e);
        }
    }

    private static int clampMin(int m) {
        return m < 0 ? 0 : Math.min(m, DAY_MIN);
    }

    // ------------------------------------------------------------------ fusion avec l'emploi du temps

    /** Les pastilles d'un jour : d'abord « toute la journée », puis tout le reste dans l'ordre des heures. */
    static List<Chip> chips(List<Settings.Slot> slots, List<DayEv> evs, List<Settings.Subject> subjects) {
        ArrayList<Chip> allDay = new ArrayList<>();
        ArrayList<Chip> timed = new ArrayList<>();
        for (Settings.Slot sl : slots) {
            Settings.Subject sub = find(subjects, sl.subjectId);
            timed.add(new Chip(false, false, false, sl.start, sl.end, sub != null ? sub.name : "?", sl.room,
                    sub != null ? sub.color : 0));
        }
        ArrayList<String> seen = new ArrayList<>();
        for (DayEv e : evs) {
            Settings.Subject sub = matchSubject(e.title, subjects);
            String sig = (e.allDay ? "J" : e.start + "-" + e.end) + ":" + norm(e.title);
            if (seen.contains(sig)) continue; // même événement dans deux agendas
            seen.add(sig);
            if (!e.allDay && duplicatesSlot(e, sub, slots)) continue; // le cours est déjà dans l'emploi du temps
            int col = sub != null ? sub.color : e.color;
            Chip ch = new Chip(e.allDay, true, e.fromPrev, e.start, e.end, e.title, e.location, col);
            (e.allDay ? allDay : timed).add(ch);
        }
        Collections.sort(allDay, new Comparator<Chip>() {
            @Override
            public int compare(Chip a, Chip b) {
                if (a.fromPrev != b.fromPrev) return a.fromPrev ? -1 : 1; // ce qui dure depuis plusieurs jours d'abord
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        Collections.sort(timed, new Comparator<Chip>() {
            @Override
            public int compare(Chip a, Chip b) {
                if (a.start != b.start) return a.start - b.start;
                if (a.google != b.google) return a.google ? 1 : -1;
                return a.end - b.end;
            }
        });
        ArrayList<Chip> out = new ArrayList<>(allDay.size() + timed.size());
        out.addAll(allDay);
        out.addAll(timed);
        return out;
    }

    private static Settings.Subject find(List<Settings.Subject> subjects, int id) {
        for (Settings.Subject s : subjects) if (s.id == id) return s;
        return null;
    }

    /** Un cours Google aux mêmes heures (à 10 min près) que la même matière de l'emploi du temps. */
    static boolean duplicatesSlot(DayEv e, Settings.Subject sub, List<Settings.Slot> slots) {
        for (Settings.Slot sl : slots) {
            if (Math.abs(sl.start - e.start) > 10 || Math.abs(sl.end - e.end) > 10) continue;
            if (sub != null && sub.id == sl.subjectId) return true;
        }
        return false;
    }

    /** La matière nommée dans un titre (« TD de maths », « TP Électronique »...), la plus précise s'il y en a plusieurs. */
    static Settings.Subject matchSubject(String title, List<Settings.Subject> subjects) {
        String[] words = words(title);
        if (words.length == 0) return null;
        Settings.Subject best = null;
        int bestLen = 0;
        for (Settings.Subject s : subjects) {
            String[] w = words(s.name);
            if (w.length == 0) continue;
            int len = 0;
            for (String x : w) len += x.length();
            if (len <= bestLen || !containsRun(words, w)) continue;
            best = s;
            bestLen = len;
        }
        return best;
    }

    private static boolean containsRun(String[] hay, String[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (!hay[i + j].equals(needle[j])) continue outer;
            return true;
        }
        return false;
    }

    /** Minuscules, sans accents ni ponctuation. */
    static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;
            sb.append(Character.isLetterOrDigit(ch) ? Character.toLowerCase(ch) : ' ');
        }
        return sb.toString().trim().replaceAll(" +", " ");
    }

    /** Mots normalisés, pluriel en -s retiré (« maths » = « math »). */
    static String[] words(String s) {
        String n = norm(s);
        if (n.isEmpty()) return new String[0];
        String[] w = n.split(" ");
        for (int i = 0; i < w.length; i++) {
            if (w[i].length() > 3 && w[i].endsWith("s")) w[i] = w[i].substring(0, w[i].length() - 1);
        }
        return w;
    }

    // ------------------------------------------------------------------ textes

    static String cut(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    /** Texte complet d'une pastille : heures, titre entier (coupé à titleMax), salle. */
    static String fullLabel(Chip ch, int titleMax) {
        String title = cut(ch.title, titleMax);
        if (ch.allDay) return ch.room.isEmpty() ? title : title + " · " + cut(ch.room, 40);
        String time;
        if (ch.fromPrev) time = "→" + Settings.fmtTime(ch.end);
        else if (ch.end > ch.start && ch.end < DAY_MIN) time = Settings.fmtTime(ch.start) + "–" + Settings.fmtTime(ch.end);
        else time = Settings.fmtTime(ch.start);
        String s = time + "  " + title;
        return ch.room.isEmpty() ? s : s + " · " + cut(ch.room, 40);
    }

    /** Texte d'une pastille ; niveau 0 = complet, 1 = sans la salle, 2 = très court. */
    static String label(Chip ch, int level) {
        int max = level == 0 ? 30 : level == 1 ? 24 : 14;
        String title = cut(ch.title, max);
        if (ch.allDay) return title;
        String time;
        if (ch.fromPrev) time = "→" + Settings.fmtTime(ch.end);
        else if (level < 2 && ch.end > ch.start && ch.end < DAY_MIN) time = Settings.fmtTime(ch.start) + "–" + Settings.fmtTime(ch.end);
        else time = Settings.fmtTime(ch.start);
        if (level >= 2) return time + " " + title;
        String s = time + "  " + title;
        return level == 0 && !ch.room.isEmpty() ? s + " · " + cut(ch.room, 18) : s;
    }
}
