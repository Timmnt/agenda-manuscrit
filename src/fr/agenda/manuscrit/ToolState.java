package fr.agenda.manuscrit;

import android.content.SharedPreferences;

import java.util.ArrayList;

/** Outil courant (stylo / surligneur / gomme / lasso), couleur, épaisseur, objet à poser, presse-papiers. */
final class ToolState {
    static final int PEN = 0, HIGHLIGHTER = 1, ERASER = 2, LASSO = 3;

    /** Nombre de boutons de couleur ; les couleurs elles-mêmes sont réglables (Settings.penColors). */
    static final int PEN_COUNT = 4;
    static final float[] PEN_WIDTHS = {2.3f, 3.6f, 5.8f};
    static String widthName(int i) {
        return i == 0 ? L.s("fin", "thin") : i == 1 ? L.s("moyen", "medium") : L.s("épais", "thick");
    }

    static final int HIGHLIGHT_COLOR = 0x6EFFD21A;
    static final float HIGHLIGHT_WIDTH = 24f;
    static final float ERASER_RADIUS = 14f;

    int tool = PEN;
    int colorIndex = 0;
    int widthIndex = 1;
    boolean fingerDraw = false;
    boolean pressure = true;

    /** Objet à poser au prochain appui sur une page (null = aucun). */
    Obj pendingInsert;
    /** Presse-papiers du lasso (éléments + coin haut-gauche d'origine). */
    ArrayList<Item> clip;
    float clipL, clipT;

    private final SharedPreferences prefs;

    ToolState(SharedPreferences prefs) {
        this.prefs = prefs;
        colorIndex = clamp(prefs.getInt("color", 0), PEN_COUNT);
        widthIndex = clamp(prefs.getInt("width", 1), PEN_WIDTHS.length);
        fingerDraw = prefs.getBoolean("fingerDraw", false);
        pressure = prefs.getBoolean("pressure", true);
    }

    private static int clamp(int v, int n) {
        return v < 0 || v >= n ? 0 : v;
    }

    int penColor() { return Settings.penColors[colorIndex]; }

    float penWidth() { return PEN_WIDTHS[widthIndex]; }

    void save() {
        prefs.edit()
                .putInt("color", colorIndex)
                .putInt("width", widthIndex)
                .putBoolean("fingerDraw", fingerDraw)
                .putBoolean("pressure", pressure)
                .apply();
    }
}
