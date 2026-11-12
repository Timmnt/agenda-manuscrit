package fr.agenda.manuscrit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Couleurs (thème sombre, pages blanches, accents bleus) et petites aides d'interface. */
final class Ui {
    private Ui() {}

    // ---- interface sombre
    static final int DESK = 0xFF121418;        // fond autour des pages
    static final int BAR = 0xFF1B1E24;         // barres du haut et du bas
    static final int BAR_LINE = 0xFF2C3039;    // séparateurs, bordures
    static final int SURFACE = 0xFF22262E;     // menus, fenêtres, cartes
    static final int SURFACE_HI = 0xFF2A2F38;  // élément survolé / champ
    static final int CARD = 0xFF1B1E24;
    static final int INK = 0xFFE8EAEF;         // texte principal
    static final int GREY = 0xFF9AA1AD;        // texte secondaire
    static final int LIGHT_GREY = 0xFF565C67;  // désactivé
    static final int ACCENT = 0xFF6BA6FF;      // texte / icônes bleus sur fond sombre
    static final int ACCENT_FILL = 0xFF2F6BE0; // aplats bleus (texte blanc dessus)
    static final int ACCENT_SOFT = 0xFF233654; // fond d'un outil sélectionné
    static final int ACCENT_LINE = 0xFF34507F; // contour bleu discret
    static final int WEEKEND = 0xFF9CB9E8;     // samedi / dimanche (bleu clair)
    static final int DANGER = 0xFFFF7A7A;

    // ---- pages (blanches)
    static final int PAPER = 0xFFFFFFFF;
    static final int PAPER_INK = 0xFF23252C;
    static final int PAPER_GREY = 0xFF7B7F8B;
    static final int PAPER_ACCENT = 0xFF2B5FD0;
    static final int PAPER_WEEKEND = 0xFF4A70B4;

    static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    static float sp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().scaledDensity;
    }

    /** Couleur très sombre ? (pour entourer la pastille d'encre noire sur fond sombre) */
    static boolean isDark(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return r * 299 + g * 587 + b * 114 < 60 * 1000;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Assombrit une couleur (f = 0.7 -> 70 % de la luminosité). */
    static int darker(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * f), g = (int) (((color >> 8) & 0xFF) * f), b = (int) ((color & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Couleur d'une matière (gris si elle a été supprimée). */
    static int subjectColor(int id) {
        Settings.Subject s = Settings.subject(id);
        return s != null ? s.color : 0xFF8A919C;
    }

    /**
     * Points sous un jour du calendrier : un point par matière posée ce jour-là (3 au plus),
     * sinon un point bleu s'il y a quelque chose d'écrit.
     */
    static void drawDayDots(Canvas c, Paint p, float cx, float cy, float r, PageStore.Summary s,
                            boolean content, boolean onAccent) {
        if (s != null && s.subjects.length > 0) {
            int n = s.subjects.length;
            float gap = r * 2.7f, x0 = cx - gap * (n - 1) / 2f;
            for (int i = 0; i < n; i++) {
                float x = x0 + gap * i;
                if (onAccent) {
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(x, cy, r * 1.45f, p);
                }
                p.setColor(subjectColor(s.subjects[i]));
                c.drawCircle(x, cy, r, p);
            }
        } else if (content) {
            p.setColor(onAccent ? 0xFFFFFFFF : ACCENT);
            c.drawCircle(cx, cy, r, p);
        }
    }
}
