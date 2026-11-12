package fr.agenda.manuscrit;

/** Géométrie d'une page, en "unités de page" (la largeur d'une page vaut toujours 1000). */
final class Geo {
    private Geo() {}

    static final float W = 1000f;
    /** Hauteur d'une page normale. */
    static final float H = 1350f;
    static final float HEADER_LINE = 178f;
    static final float FIRST_RULE = 236f;
    static final float RULE_STEP = 54f;
    static final float MARGIN_X = 104f;
    /** De combien la page s'allonge quand elle est pleine. */
    static final float EXT = 675f;

    /** Clés de page : un jour de l'agenda = numéro du jour ; une page "En vrac" = VRAC_BASE + n (n >= 1). */
    static final long VRAC_BASE = 1L << 40;

    static boolean isVrac(long key) {
        return key >= VRAC_BASE;
    }

    static long vrac(int n) {
        return VRAC_BASE + n;
    }

    static int vracIndex(long key) {
        return (int) (key - VRAC_BASE);
    }
}
