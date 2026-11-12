package fr.agenda.manuscrit;

/**
 * Un trait d'encre en coordonnées "page" (largeur de page = 1000 unités).
 * Chaque point = (x, y, f) où f est le facteur d'épaisseur issu de la pression.
 * Un trait terminé n'est plus jamais modifié (ce qui permet de le lire depuis un autre thread).
 */
final class Stroke extends Item {
    static final int FLAG_HIGHLIGHTER = 1;

    final int color;
    final float width;
    final int flags;
    float[] pts;
    int n;
    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    float maxF = 0f;

    Stroke(int color, float width, int flags, int capacity) {
        this.color = color;
        this.width = width;
        this.flags = flags;
        this.pts = new float[Math.max(capacity, 4) * 3];
    }

    boolean isHighlighter() {
        return (flags & FLAG_HIGHLIGHTER) != 0;
    }

    void add(float x, float y, float f) {
        if (n * 3 + 3 > pts.length) {
            float[] np = new float[pts.length * 2];
            System.arraycopy(pts, 0, np, 0, n * 3);
            pts = np;
        }
        int i = n * 3;
        pts[i] = x;
        pts[i + 1] = y;
        pts[i + 2] = f;
        n++;
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (f > maxF) maxF = f;
    }

    float lastX() { return pts[(n - 1) * 3]; }

    float lastY() { return pts[(n - 1) * 3 + 1]; }

    void trim() {
        if (pts.length != n * 3) {
            float[] np = new float[n * 3];
            System.arraycopy(pts, 0, np, 0, n * 3);
            pts = np;
        }
    }

    private float half() {
        return width * Math.max(maxF, 1f) * 0.5f;
    }

    @Override
    float left() { return n == 0 ? 0 : minX - half(); }

    @Override
    float top() { return n == 0 ? 0 : minY - half(); }

    @Override
    float right() { return n == 0 ? 0 : maxX + half(); }

    @Override
    float bottom() { return n == 0 ? 0 : maxY + half(); }

    float length() {
        float len = 0;
        for (int i = 1; i < n; i++) {
            float dx = pts[i * 3] - pts[(i - 1) * 3], dy = pts[i * 3 + 1] - pts[(i - 1) * 3 + 1];
            len += (float) Math.sqrt(dx * dx + dy * dy);
        }
        return len;
    }

    @Override
    boolean hit(float x, float y, float r) {
        if (n == 0) return false;
        float rr = r + half();
        if (x < minX - rr || x > maxX + rr || y < minY - rr || y > maxY + rr) return false;
        float r2 = rr * rr;
        if (n == 1) {
            float dx = x - pts[0], dy = y - pts[1];
            return dx * dx + dy * dy <= r2;
        }
        for (int i = 1; i < n; i++) {
            float ax = pts[(i - 1) * 3], ay = pts[(i - 1) * 3 + 1];
            float bx = pts[i * 3], by = pts[i * 3 + 1];
            float vx = bx - ax, vy = by - ay;
            float wx = x - ax, wy = y - ay;
            float len2 = vx * vx + vy * vy;
            float t = len2 > 0 ? (wx * vx + wy * vy) / len2 : 0;
            if (t < 0) t = 0;
            else if (t > 1) t = 1;
            float dx = ax + t * vx - x, dy = ay + t * vy - y;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    @Override
    Item transformed(float ax, float ay, float k, float dx, float dy) {
        Stroke s = new Stroke(color, width * k, flags, n);
        for (int i = 0; i < n; i++) {
            int j = i * 3;
            s.add(ax + (pts[j] - ax) * k + dx, ay + (pts[j + 1] - ay) * k + dy, pts[j + 2]);
        }
        s.trim();
        return s;
    }

    @Override
    Item recolored(int c) {
        if (isHighlighter() || c == color) return this;
        Stroke s = new Stroke(c, width, flags, n);
        for (int i = 0; i < n; i++) s.add(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]);
        s.trim();
        return s;
    }

    @Override
    float insideFraction(float[] poly, int pn) {
        if (n == 0) return 0;
        int step = Math.max(1, n / 48), total = 0, in = 0;
        for (int i = 0; i < n; i += step) {
            total++;
            if (Shapes.inside(poly, pn, pts[i * 3], pts[i * 3 + 1])) in++;
        }
        return in / (float) total;
    }
}
