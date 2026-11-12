package fr.agenda.manuscrit;

import java.util.*;

public class CalTests {
    static int fails = 0;
    static void check(boolean ok, String what) {
        if (!ok) { fails++; System.out.println("ÉCHEC : " + what); }
    }
    static Map<Long, List<CalEvents.DayEv>> spread(CalEvents.Inst in, long from, long to) {
        Map<Long, List<CalEvents.DayEv>> m = new HashMap<>();
        CalEvents.spread(in, from, to, m);
        return m;
    }
    public static void main(String[] a) {
        long d0 = Days.fromYmd(2026, 11, 6);
        // jours juliens
        check(CalEvents.toJulian(0) == 2440588L, "julien 1970");
        check(CalEvents.fromJulian(2461351L) == Days.fromYmd(2026, 11, 6), "julien 6 nov 2026 = " + CalEvents.toJulian(d0));
        check(CalEvents.floorDiv(-1, 32) == -1 && CalEvents.floorDiv(31, 32) == 0 && CalEvents.floorDiv(-32, 32) == -1
                && CalEvents.floorDiv(-33, 32) == -2 && CalEvents.floorDiv(64, 32) == 2, "floorDiv");

        // événement simple 14h-15h30
        Map<Long, List<CalEvents.DayEv>> m = spread(new CalEvents.Inst(d0, 840, d0, 930, false, "Réunion projet", "C12", 0xFF039BE5), d0 - 5, d0 + 5);
        check(m.size() == 1 && m.get(d0).size() == 1, "simple : 1 jour");
        CalEvents.DayEv e = m.get(d0).get(0);
        check(e.start == 840 && e.end == 930 && !e.allDay && !e.fromPrev, "simple : heures");

        // toute la journée sur 3 jours (fin inclusive : le calendrier d'Android ramène la fin au dernier jour)
        m = spread(new CalEvents.Inst(d0, 0, d0 + 2, 1440, true, "Vacances", "", 0), d0 - 5, d0 + 5);
        check(m.size() == 3 && m.get(d0).get(0).allDay && !m.get(d0).get(0).fromPrev && m.get(d0 + 1).get(0).fromPrev, "3 jours toute la journée");
        // fenêtre partielle
        m = spread(new CalEvents.Inst(d0, 0, d0 + 2, 1440, true, "Vacances", "", 0), d0 + 1, d0 + 10);
        check(m.size() == 2 && !m.containsKey(d0), "fenêtre partielle");

        // à cheval sur minuit : 23h -> 1h
        m = spread(new CalEvents.Inst(d0, 1380, d0 + 1, 60, false, "Soirée", "", 0), d0 - 1, d0 + 3);
        check(m.size() == 2, "minuit : 2 jours");
        check(m.get(d0).get(0).start == 1380 && m.get(d0).get(0).end == 1440 && !m.get(d0).get(0).allDay, "minuit jour 1");
        check(m.get(d0 + 1).get(0).start == 0 && m.get(d0 + 1).get(0).end == 60 && m.get(d0 + 1).get(0).fromPrev, "minuit jour 2");
        // vendredi 18h -> dimanche 12h : samedi = journée entière
        m = spread(new CalEvents.Inst(d0, 1080, d0 + 2, 720, false, "Week-end ski", "", 0), d0 - 1, d0 + 3);
        check(m.size() == 3 && m.get(d0 + 1).get(0).allDay && !m.get(d0).get(0).allDay && !m.get(d0 + 2).get(0).allDay, "week-end");
        // finit à minuit pile, sans normalisation : pas de pastille vide le lendemain
        m = spread(new CalEvents.Inst(d0, 1200, d0 + 1, 0, false, "Concert", "", 0), d0 - 1, d0 + 3);
        check(m.size() == 1, "fin à minuit pile");
        // très long : limité à MAX_SPAN
        m = spread(new CalEvents.Inst(d0, 0, d0 + 400, 1440, true, "Semestre", "", 0), d0 - 1000, d0 + 1000);
        check(m.size() == CalEvents.MAX_SPAN + 1, "long limité : " + m.size());
        // sans titre ni lieu ignoré, sans titre avec lieu gardé
        check(spread(new CalEvents.Inst(d0, 600, d0, 660, false, "  ", null, 0), d0, d0).isEmpty(), "vide ignoré");
        check(spread(new CalEvents.Inst(d0, 600, d0, 660, false, null, "Gymnase", 0), d0, d0).get(d0).get(0).title.equals("(Sans titre)"), "sans titre");

        // reconnaissance des matières
        List<Settings.Subject> subs = Settings.defaultSubjects();
        check(CalEvents.matchSubject("TD de maths", subs).name.equals("Maths"), "maths");
        check(CalEvents.matchSubject("TD MATH", subs).name.equals("Maths"), "math sans s");
        check(CalEvents.matchSubject("TP electronique numérique", subs).name.equals("Électronique"), "électronique sans accent");
        check(CalEvents.matchSubject("Mathématiques", subs) == null, "pas de faux positif mathématiques");
        check(CalEvents.matchSubject("Physiques-chimie", subs).name.equals("Physique"), "physiques-chimie");
        check(CalEvents.matchSubject("Anniversaire Léa", subs) == null, "rien");
        check(CalEvents.matchSubject("Projet informatique", subs).name.equals("Informatique"), "le plus long gagne");
        check(CalEvents.norm("  Élève — d'été  ").equals("eleve d ete"), "norm : " + CalEvents.norm("  Élève — d'été  "));

        // fusion
        List<Settings.Slot> slots = new ArrayList<>();
        slots.add(new Settings.Slot(4, 480, 600, 1, "B204"));    // 8h-10h Maths
        slots.add(new Settings.Slot(4, 615, 735, 6, ""));        // 10h15-12h15 Anglais
        List<CalEvents.DayEv> evs = new ArrayList<>();
        evs.add(new CalEvents.DayEv(480, 600, false, false, "Maths", "B204", 0xFF039BE5)); // doublon du cours
        evs.add(new CalEvents.DayEv(840, 900, false, false, "Dentiste", "", 0xFF33B679));
        evs.add(new CalEvents.DayEv(840, 900, false, false, "Dentiste", "", 0xFFE67C73));   // même événement, autre agenda
        evs.add(new CalEvents.DayEv(0, 1440, true, false, "Armistice", "", 0xFF0B8043));
        evs.add(new CalEvents.DayEv(0, 1440, true, true, "Stage", "", 0xFF8E24AA));
        evs.add(new CalEvents.DayEv(540, 600, false, false, "Colle de physique", "", 0xFF039BE5));
        List<CalEvents.Chip> ch = CalEvents.chips(slots, evs, subs);
        StringBuilder sb = new StringBuilder();
        for (CalEvents.Chip c : ch) sb.append(CalEvents.label(c, 0)).append(" | ");
        System.out.println(sb);
        check(ch.size() == 6, "fusion : 6 pastilles (" + ch.size() + ")");
        check(ch.get(0).title.equals("Stage") && ch.get(1).title.equals("Armistice"), "toute la journée d'abord");
        check(ch.get(2).title.equals("Maths") && !ch.get(2).google, "cours de l'emploi du temps gardé");
        check(ch.get(3).title.equals("Colle de physique") && ch.get(3).color == 0xFF7B3FC4, "couleur de la matière reprise");
        check(ch.get(4).title.equals("Anglais") && ch.get(5).title.equals("Dentiste") && ch.get(5).color == 0xFF33B679, "ordre des heures");

        // textes
        check(CalEvents.label(ch.get(2), 0).equals("8h–10h  Maths · B204"), "label 0 : " + CalEvents.label(ch.get(2), 0));
        check(CalEvents.label(ch.get(2), 1).equals("8h–10h  Maths"), "label 1");
        check(CalEvents.label(ch.get(2), 2).equals("8h Maths"), "label 2");
        check(CalEvents.label(ch.get(4), 0).equals("10h15–12h15  Anglais"), "label 10h15");
        CalEvents.Chip cont = new CalEvents.Chip(false, true, true, 0, 60, "Soirée", "", 0);
        check(CalEvents.label(cont, 0).equals("→1h  Soirée"), "suite de la veille : " + CalEvents.label(cont, 0));
        CalEvents.Chip lng = new CalEvents.Chip(false, true, false, 600, 660, "Rendez-vous avec la conseillère d'orientation", "", 0);
        check(CalEvents.label(lng, 2).length() <= 20 && CalEvents.label(lng, 2).endsWith("…"), "titre coupé : " + CalEvents.label(lng, 2));
        CalEvents.Chip open = new CalEvents.Chip(false, true, false, 1080, 1440, "Fête", "", 0);
        check(CalEvents.label(open, 0).equals("18h  Fête"), "jusqu'à minuit : " + CalEvents.label(open, 0));

        // texte complet (pastilles qui passent à la ligne)
        CalEvents.Chip full = new CalEvents.Chip(false, true, false, 610, 730, "Sciences de l'ingénieur", "Amphi A", 0);
        check(CalEvents.fullLabel(full, 200).equals("10h10–12h10  Sciences de l'ingénieur · Amphi A"), "complet : " + CalEvents.fullLabel(full, 200));
        check(CalEvents.fullLabel(lng, 200).contains("conseillère d'orientation"), "titre long entier");
        check(CalEvents.fullLabel(new CalEvents.Chip(true, true, false, 0, 1440, "Armistice", "", 0), 200).equals("Armistice"), "toute la journée");

        // ids
        check(Arrays.equals(Settings.decodeIds(Settings.encodeIds(new long[]{3, 12, 7})), new long[]{3, 12, 7}), "ids");
        check(Settings.decodeIds("").length == 0 && Settings.decodeIds("4,x,5").length == 2, "ids invalides");

        // DayEv.equals (détection de changement)
        check(new CalEvents.DayEv(1, 2, false, false, "a", "", 0).equals(new CalEvents.DayEv(1, 2, false, false, "a", "", 0)), "equals");
        check(!new CalEvents.DayEv(1, 2, false, false, "a", "", 0).equals(new CalEvents.DayEv(1, 3, false, false, "a", "", 0)), "equals 2");

        System.out.println(fails == 0 ? "TESTS AGENDA GOOGLE OK" : fails + " échec(s)");
        if (fails > 0) System.exit(1);
    }
}
