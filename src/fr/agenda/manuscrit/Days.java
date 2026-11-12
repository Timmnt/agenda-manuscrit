package fr.agenda.manuscrit;

import java.util.Calendar;
import java.util.Locale;

/** Dates as "epoch days" (number of days since 1970-01-01). No time zone pitfalls. */
final class Days {
    private Days() {}

    static final String[] DAY_NAMES = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
    static final String[] DAY_SHORT = {"Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam.", "Dim."};
    static final String[] DAY_LETTER = {"L", "M", "M", "J", "V", "S", "D"};
    static final String[] MONTHS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet",
            "août", "septembre", "octobre", "novembre", "décembre"};
    static final String[] MONTHS_SHORT = {"janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.",
            "août", "sept.", "oct.", "nov.", "déc."};

    /** y, m (1..12), d (1..31) -> epoch day. */
    static long fromYmd(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** epoch day -> {y, m (1..12), d}. */
    static int[] toYmd(long z) {
        z += 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        if (m <= 2) y++;
        return new int[]{(int) y, (int) m, (int) d};
    }

    /** 0 = lundi ... 6 = dimanche. (1970-01-01 était un jeudi.) */
    static int dayOfWeek(long day) {
        int r = (int) ((day + 3) % 7);
        if (r < 0) r += 7;
        return r;
    }

    static long weekStart(long day) {
        return day - dayOfWeek(day);
    }

    static long today() {
        Calendar c = Calendar.getInstance();
        return fromYmd(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Numéro de semaine ISO 8601 (celui des agendas français). */
    static int isoWeek(long day) {
        long thursday = day - dayOfWeek(day) + 3;
        int year = toYmd(thursday)[0];
        long jan1 = fromYmd(year, 1, 1);
        return (int) ((thursday - jan1) / 7) + 1;
    }

    static int daysInMonth(int y, int m) {
        long first = fromYmd(y, m, 1);
        long next = m == 12 ? fromYmd(y + 1, 1, 1) : fromYmd(y, m + 1, 1);
        return (int) (next - first);
    }

    static String key(long day) {
        int[] a = toYmd(day);
        return String.format(Locale.US, "%04d-%02d-%02d", a[0], a[1], a[2]);
    }

    /** Parse "yyyy-MM-dd", returns Long.MIN_VALUE if invalid. */
    static long parseKey(String s) {
        try {
            if (s == null || s.length() != 10 || s.charAt(4) != '-' || s.charAt(7) != '-') return Long.MIN_VALUE;
            int y = Integer.parseInt(s.substring(0, 4));
            int m = Integer.parseInt(s.substring(5, 7));
            int d = Integer.parseInt(s.substring(8, 10));
            if (m < 1 || m > 12 || d < 1 || d > 31) return Long.MIN_VALUE;
            return fromYmd(y, m, d);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    static String dayNum(int d) {
        return d == 1 ? "1er" : String.valueOf(d);
    }

    /** "Jeudi 5 novembre 2026" */
    static String longTitle(long day) {
        int[] a = toYmd(day);
        return DAY_NAMES[dayOfWeek(day)] + " " + dayNum(a[2]) + " " + MONTHS[a[1] - 1] + " " + a[0];
    }

    /** "5 novembre 2026" */
    static String dateLine(long day) {
        int[] a = toYmd(day);
        return dayNum(a[2]) + " " + MONTHS[a[1] - 1] + " " + a[0];
    }

    /** "Jeu. 5 nov." */
    static String shortTitle(long day) {
        int[] a = toYmd(day);
        return DAY_SHORT[dayOfWeek(day)] + " " + dayNum(a[2]) + " " + MONTHS_SHORT[a[1] - 1];
    }

    /** Deux jours qui se suivent : "Jeudi 5 et vendredi 6 novembre 2026". */
    static String spreadTitle(long d) {
        int[] a = toYmd(d), b = toYmd(d + 1);
        String first = DAY_NAMES[dayOfWeek(d)] + " " + dayNum(a[2]);
        String second = DAY_NAMES[dayOfWeek(d + 1)].toLowerCase(Locale.FRENCH) + " " + dayNum(b[2]);
        if (a[1] == b[1]) return first + " et " + second + " " + MONTHS[a[1] - 1] + " " + a[0];
        if (a[0] == b[0]) {
            return first + " " + MONTHS[a[1] - 1] + " et " + second + " " + MONTHS[b[1] - 1] + " " + b[0];
        }
        return first + " " + MONTHS[a[1] - 1] + " " + a[0] + " et " + second + " " + MONTHS[b[1] - 1] + " " + b[0];
    }

    /** Version courte : "Jeu. 5 – Ven. 6 nov." */
    static String spreadShort(long d) {
        int[] a = toYmd(d), b = toYmd(d + 1);
        if (a[1] == b[1]) {
            return DAY_SHORT[dayOfWeek(d)] + " " + dayNum(a[2]) + " – " + DAY_SHORT[dayOfWeek(d + 1)] + " "
                    + dayNum(b[2]) + " " + MONTHS_SHORT[b[1] - 1];
        }
        return shortTitle(d) + " – " + shortTitle(d + 1);
    }

    /** "2 – 8 nov. 2026", "27 oct. – 2 nov. 2026", "29 déc. 2025 – 4 janv. 2026" */
    static String weekRange(long weekStart) {
        int[] a = toYmd(weekStart);
        int[] b = toYmd(weekStart + 6);
        String end = dayNum(b[2]) + " " + MONTHS_SHORT[b[1] - 1] + " " + b[0];
        if (a[0] != b[0]) return dayNum(a[2]) + " " + MONTHS_SHORT[a[1] - 1] + " " + a[0] + " – " + end;
        if (a[1] != b[1]) return dayNum(a[2]) + " " + MONTHS_SHORT[a[1] - 1] + " – " + end;
        return dayNum(a[2]) + " – " + end;
    }

    static String monthTitle(int y, int m) {
        String s = MONTHS[m - 1];
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + " " + y;
    }
}
