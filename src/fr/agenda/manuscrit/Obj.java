package fr.agenda.manuscrit;

/**
 * Objet posé sur une page : case à cocher, tampon (DS, TP...), pastille de matière ou autocollant.
 * (x, y) = centre, (w, h) = taille, en unités de page. Immuable.
 */
final class Obj extends Item {
    static final int BOX = 1, STAMP = 2, SUBJECT = 3, STICKER = 4;

    final int type;
    /** Tampon : n° du tampon ; matière : identifiant ; autocollant : n° du dessin. */
    final int kind;
    final int color;
    final float x, y, w, h;
    final boolean checked;
    /** Nom de la matière (gardé au cas où elle serait supprimée des réglages). */
    final String text;

    Obj(int type, int kind, int color, float x, float y, float w, float h, boolean checked, String text) {
        this.type = type;
        this.kind = kind;
        this.color = color;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.checked = checked;
        this.text = text == null ? "" : text;
    }

    boolean isBox() {
        return type == BOX;
    }

    Obj withChecked(boolean c) {
        return new Obj(type, kind, color, x, y, w, h, c, text);
    }

    /** Même objet, centré en (nx, ny). */
    Obj at(float nx, float ny) {
        return new Obj(type, kind, color, nx, ny, w, h, checked, text);
    }

    @Override
    float left() { return x - w / 2; }

    @Override
    float top() { return y - h / 2; }

    @Override
    float right() { return x + w / 2; }

    @Override
    float bottom() { return y + h / 2; }

    @Override
    boolean hit(float px, float py, float r) {
        return px >= left() - r && px <= right() + r && py >= top() - r && py <= bottom() + r;
    }

    @Override
    Item transformed(float ax, float ay, float k, float dx, float dy) {
        return new Obj(type, kind, color, ax + (x - ax) * k + dx, ay + (y - ay) * k + dy, w * k, h * k, checked, text);
    }

    @Override
    Item recolored(int c) {
        if (type != BOX || c == color) return this;
        return new Obj(type, kind, c, x, y, w, h, checked, text);
    }

    @Override
    float insideFraction(float[] poly, int n) {
        float ix = w * 0.25f, iy = h * 0.25f;
        int in = 0;
        if (Shapes.inside(poly, n, x, y)) in++;
        if (Shapes.inside(poly, n, x - ix, y - iy)) in++;
        if (Shapes.inside(poly, n, x + ix, y - iy)) in++;
        if (Shapes.inside(poly, n, x - ix, y + iy)) in++;
        if (Shapes.inside(poly, n, x + ix, y + iy)) in++;
        return in / 5f;
    }
}
