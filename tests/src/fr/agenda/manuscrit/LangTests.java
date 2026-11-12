package fr.agenda.manuscrit;

import java.util.List;

/** Langue : dates, heures, textes et valeurs par défaut en français et en anglais. */
public class LangTests {
    static int fails = 0;

    static void eq(String got, String want, String what) {
        if (!got.equals(want)) {
            fails++;
            System.out.println("ÉCHEC : " + what + " : « " + got + " » au lieu de « " + want + " »");
        }
    }

    public static void main(String[] a) {
        long d = Days.fromYmd(2026, 11, 9); // lundi 9 novembre 2026
        L.apply(L.FR);
        eq(Days.dayName(Days.dayOfWeek(d)), "Lundi", "jour FR");
        eq(Days.dateLine(d), "9 novembre 2026", "date FR");
        eq(Days.longTitle(d), "Lundi 9 novembre 2026", "titre FR");
        eq(Days.dateLine(Days.fromYmd(2026, 12, 1)), "1er décembre 2026", "1er FR");
        eq(Days.weekRange(Days.weekStart(d)), "9 – 15 nov. 2026", "semaine FR");
        eq(Days.spreadTitle(d), "Lundi 9 et mardi 10 novembre 2026", "deux jours FR");
        eq(Settings.fmtTime(8 * 60), "8h", "heure FR");
        eq(Settings.fmtTime(10 * 60 + 10), "10h10", "heure FR 2");
        eq(L.n(1, "jour", "jours", "day", "days"), "1 jour", "singulier FR");
        eq(L.n(0, "jour", "jours", "day", "days"), "0 jour", "zéro FR");
        eq(L.f("le tampon « %s »", "the “%s” stamp", "DS"), "le tampon « DS »", "format FR");
        eq(Settings.defaultStamps().get(2).text, "RENDU", "tampon FR");
        eq(Settings.defaultSubjects().get(1).name, "Physique", "matière FR");

        L.apply(L.EN);
        eq(Days.dayName(Days.dayOfWeek(d)), "Monday", "day EN");
        eq(Days.dateLine(d), "November 9, 2026", "date EN");
        eq(Days.longTitle(d), "Monday, November 9, 2026", "title EN");
        eq(Days.dateLine(Days.fromYmd(2026, 12, 1)), "December 1, 2026", "1st EN");
        eq(Days.shortTitle(d), "Mon, Nov 9", "short EN");
        eq(Days.weekRange(Days.weekStart(d)), "Nov 9 – 15, 2026", "week EN");
        eq(Days.weekRange(Days.fromYmd(2026, 10, 26)), "Oct 26 – Nov 1, 2026", "week across months EN");
        eq(Days.weekRange(Days.fromYmd(2025, 12, 29)), "Dec 29, 2025 – Jan 4, 2026", "week across years EN");
        eq(Days.spreadTitle(d), "Monday 9 & Tuesday 10 November 2026", "two days EN");
        eq(Days.monthTitle(2026, 11), "November 2026", "month EN");
        eq(Days.dayLetter(1), "T", "letter EN");
        eq(Settings.fmtTime(8 * 60), "8:00", "time EN");
        eq(Settings.fmtTime(13 * 60 + 5), "13:05", "time EN 2");
        eq(L.n(1, "jour", "jours", "day", "days"), "1 day", "singular EN");
        eq(L.n(0, "jour", "jours", "day", "days"), "0 days", "zero EN");
        eq(L.f("le tampon « %s »", "the “%s” stamp", "TEST"), "the “TEST” stamp", "format EN");
        eq(Settings.defaultStamps().get(2).text, "DUE", "stamp EN");
        eq(Settings.defaultSubjects().get(1).name, "Physics", "subject EN");
        eq(Settings.bgName(Settings.BG_GRID), "Grid", "background EN");
        CalEvents.Chip ch = new CalEvents.Chip(false, true, false, 610, 730, "Lab", "A403", 0);
        eq(CalEvents.fullLabel(ch, 200), "10:10–12:10  Lab · A403", "pill EN");
        L.apply(L.FR);
        eq(CalEvents.fullLabel(ch, 200), "10h10–12h10  Lab · A403", "pastille FR");

        System.out.println(fails == 0 ? "TESTS DE LANGUE OK" : fails + " échec(s)");
        if (fails > 0) System.exit(1);
    }
}
