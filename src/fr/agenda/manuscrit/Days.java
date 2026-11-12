package fr.agenda.manuscrit;

import java.util.Calendar;
import java.util.Locale;

/** Dates as "epoch days" (number of days since 1970-01-01). No time zone pitfalls. */
final class Days {
    private Days() {}

    private static final String[] DAY_NAMES = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
    private static final String[] DAY_NAMES_EN = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] DAY_SHORT = {"Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam.", "Dim."};
    private static final String[] DAY_SHORT_EN = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] DAY_LETTER = {"L", "M", "M", "J", "V", "S", "D"};
    private static final String[] DAY_LETTER_EN = {"M", "T", "W", "T", "F", "S", "S"};
    private static final String[] MONTHS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet",
            "août", "septembre", "octobre", "novembre", "décembre"};
    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final String[] MONTHS_SHORT = {"janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.",
            "août", "sept.", "oct.", "nov.", "déc."};
    private static final String[] MONTHS_SHORT_EN = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
            "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** Nom du jour (0 = lundi), dans la langue de l'interface. */
    static String dayName(int dow) {
        return (L.en ? DAY_NAMES_EN : DAY_NAMES)[dow];
    }

    /** « Lun. » / « Mon ». */
    static String dayShort(int dow) {
        return (L.en ? DAY_SHORT_EN : DAY_SHORT)[dow];
    }

    /** « L » / « M ». */
    static String dayLetter(int dow) {
        return (L.en ? DAY_LETTER_EN : DAY_LETTER)[dow];
    }

    /** Mois (1..12) : « novembre » / « November ». */
    static String month(int m) {
        return (L.en ? MONTHS_EN : MONTHS)[m - 1];
    }

    /** Mois abrégé : « nov. » / « Nov ». */
    static String monthShort(int m) {
        return (L.en ? MONTHS_SHORT_EN : MONTHS_SHORT)[m - 1];
    }

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
        return d == 1 && !L.en ? "1er" : String.valueOf(d);
    }

    /** « Jeudi 5 novembre 2026 » / « Thursday, November 5, 2026 » */
    static String longTitle(long day) {
        int[] a = toYmd(day);
        if (L.en) return dayName(dayOfWeek(day)) + ", " + month(a[1]) + " " + a[2] + ", " + a[0];
        return dayName(dayOfWeek(day)) + " " + dayNum(a[2]) + " " + month(a[1]) + " " + a[0];
    }

    /** « 5 novembre 2026 » / « November 5, 2026 » */
    static String dateLine(long day) {
        int[] a = toYmd(day);
        if (L.en) return month(a[1]) + " " + a[2] + ", " + a[0];
        return dayNum(a[2]) + " " + month(a[1]) + " " + a[0];
    }

    /** « Jeu. 5 nov. » / « Thu, Nov 5 » */
    static String shortTitle(long day) {
        int[] a = toYmd(day);
        if (L.en) return dayShort(dayOfWeek(day)) + ", " + monthShort(a[1]) + " " + a[2];
        return dayShort(dayOfWeek(day)) + " " + dayNum(a[2]) + " " + monthShort(a[1]);
    }

    /** Deux jours qui se suivent : « Jeudi 5 et vendredi 6 novembre 2026 » / « Thursday 5 & Friday 6 November 2026 ». */
    static String spreadTitle(long d) {
        int[] a = toYmd(d), b = toYmd(d + 1);
        if (L.en) {
            String first = dayName(dayOfWeek(d)) + " " + a[2], second = dayName(dayOfWeek(d + 1)) + " " + b[2];
            if (a[1] == b[1]) return first + " & " + second + " " + month(a[1]) + " " + a[0];
            if (a[0] == b[0]) return first + " " + month(a[1]) + " & " + second + " " + month(b[1]) + " " + b[0];
            return first + " " + month(a[1]) + " " + a[0] + " & " + second + " " + month(b[1]) + " " + b[0];
        }
        String first = dayName(dayOfWeek(d)) + " " + dayNum(a[2]);
        String second = dayName(dayOfWeek(d + 1)).toLowerCase(Locale.FRENCH) + " " + dayNum(b[2]);
        if (a[1] == b[1]) return first + " et " + second + " " + month(a[1]) + " " + a[0];
        if (a[0] == b[0]) {
            return first + " " + month(a[1]) + " et " + second + " " + month(b[1]) + " " + b[0];
        }
        return first + " " + month(a[1]) + " " + a[0] + " et " + second + " " + month(b[1]) + " " + b[0];
    }

    /** Version courte : « Jeu. 5 – Ven. 6 nov. » / « Thu 5 – Fri 6 Nov » */
    static String spreadShort(long d) {
        int[] a = toYmd(d), b = toYmd(d + 1);
        if (a[1] == b[1]) {
            return dayShort(dayOfWeek(d)) + " " + dayNum(a[2]) + " – " + dayShort(dayOfWeek(d + 1)) + " "
                    + dayNum(b[2]) + " " + monthShort(b[1]);
        }
        return shortTitle(d) + " – " + shortTitle(d + 1);
    }

    /**
     * « 2 – 8 nov. 2026 », « 27 oct. – 2 nov. 2026 », « 29 déc. 2025 – 4 janv. 2026 »
     * / « Nov 2 – 8, 2026 », « Oct 27 – Nov 2, 2026 », « Dec 29, 2025 – Jan 4, 2026 »
     */
    static String weekRange(long weekStart) {
        int[] a = toYmd(weekStart);
        int[] b = toYmd(weekStart + 6);
        if (L.en) {
            if (a[0] != b[0]) return monthShort(a[1]) + " " + a[2] + ", " + a[0] + " – " + monthShort(b[1]) + " " + b[2] + ", " + b[0];
            if (a[1] != b[1]) return monthShort(a[1]) + " " + a[2] + " – " + monthShort(b[1]) + " " + b[2] + ", " + b[0];
            return monthShort(a[1]) + " " + a[2] + " – " + b[2] + ", " + b[0];
        }
        String end = dayNum(b[2]) + " " + monthShort(b[1]) + " " + b[0];
        if (a[0] != b[0]) return dayNum(a[2]) + " " + monthShort(a[1]) + " " + a[0] + " – " + end;
        if (a[1] != b[1]) return dayNum(a[2]) + " " + monthShort(a[1]) + " – " + end;
        return dayNum(a[2]) + " – " + end;
    }

    /** « Novembre 2026 » / « November 2026 » */
    static String monthTitle(int y, int m) {
        String s = month(m);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + " " + y;
    }
}
