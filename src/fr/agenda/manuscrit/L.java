package fr.agenda.manuscrit;

import java.util.Locale;

/**
 * Langue de l'interface : français ou anglais. Chaque texte est écrit dans les deux langues à
 * l'endroit où il sert : L.s("Fermer", "Close"). Pur Java (testable hors Android).
 * Pour vérifier qu'aucun texte n'est oublié : python3 tools/check_i18n.py
 */
final class L {
    private L() {}

    /** Réglage : langue de la tablette, français, anglais. */
    static final int AUTO = 0, FR = 1, EN = 2;

    /** Langue effective : true = anglais. */
    static volatile boolean en = false;

    /** Applique le réglage (AUTO : français si la tablette est en français, anglais sinon). */
    static void apply(int choice) {
        if (choice == FR) en = false;
        else if (choice == EN) en = true;
        else en = !"fr".equals(Locale.getDefault().getLanguage());
    }

    /** Le texte dans la langue choisie. */
    static String s(String fr, String english) {
        return en ? english : fr;
    }

    /** Pareil, avec des valeurs à insérer (%s, %d). */
    static String f(String fr, String english, Object... args) {
        return String.format(Locale.ROOT, en ? english : fr, args);
    }

    /** Singulier / pluriel : n + " jour" / " jours". */
    static String n(int n, String frOne, String frMany, String enOne, String enMany) {
        return n + " " + (en ? (n == 1 ? enOne : enMany) : (n > 1 ? frMany : frOne));
    }

    /** Pour mettre en minuscules ou majuscules selon la langue. */
    static Locale locale() {
        return en ? Locale.ENGLISH : Locale.FRENCH;
    }
}
