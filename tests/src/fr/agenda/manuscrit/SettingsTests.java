package fr.agenda.manuscrit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Réglages : encodage / décodage des tampons, autocollants emoji et couleurs des stylos. */
public class SettingsTests {
    static int fails = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            fails++;
            System.out.println("ÉCHEC : " + what);
        }
    }

    public static void main(String[] a) {
        // tampons
        List<Settings.Stamp> st = new ArrayList<>(Settings.defaultStamps());
        st.add(new Settings.Stamp(9, "À REVOIR\tvite\n", 0xFF123456, true));
        List<Settings.Stamp> back = Settings.decodeStamps(Settings.encodeStamps(st));
        check(back.size() == st.size(), "tampons : nombre");
        check(back.get(0).text.equals("DS") && !back.get(0).filled, "tampon DS");
        check(back.get(4).text.equals("IMPORTANT") && back.get(4).filled, "tampon IMPORTANT plein");
        Settings.Stamp last = back.get(back.size() - 1);
        check(last.id == 9 && last.color == 0xFF123456 && last.filled && last.text.equals("À REVOIR vite"),
                "tampon perso (tabulation et retour à la ligne nettoyés) : " + last.text);
        check(Settings.decodeStamps("x\ty\n1\t2\t0\t\n\n").isEmpty(), "tampons invalides ignorés");

        // autocollants emoji
        List<String> em = new ArrayList<>(Settings.defaultEmojis());
        em.add("👻"); // fantôme
        em.add("?!");
        check(Settings.decodeEmojis(Settings.encodeEmojis(em)).equals(em), "emoji : aller-retour");
        check(Settings.decodeEmojis("\n \n").isEmpty(), "emoji : lignes vides ignorées");

        // couleurs des stylos
        int[] pc = {0xFF000000, 0xFF0288D1, 0xFFE07B00, 0xFF7B3FC4};
        check(Arrays.equals(Settings.decodePenColors(Settings.encodePenColors(pc)), pc), "stylos : aller-retour");
        int[] partial = Settings.decodePenColors("-16777216,abc");
        check(partial[0] == 0xFF000000 && partial[1] == Settings.DEFAULT_PEN_COLORS[1]
                && partial[3] == Settings.DEFAULT_PEN_COLORS[3], "stylos : valeurs invalides = couleurs par défaut");

        System.out.println(fails == 0 ? "TESTS DES RÉGLAGES OK" : fails + " échec(s)");
        if (fails > 0) System.exit(1);
    }
}
