package fr.agenda.manuscrit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Générateurs de tracés « à la main » pour éprouver la reconnaissance des carrés. */
public class Gen {
    final Random rnd;

    Gen(long seed) {
        rnd = new Random(seed);
    }

    float U(double a, double b) {
        return (float) (a + rnd.nextDouble() * (b - a));
    }

    static float dist(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    static Stroke toStroke(List<float[]> pts) {
        Stroke s = new Stroke(0xFF1C1D22, 3f, 0, pts.size());
        for (float[] p : pts) s.add(p[0], p[1], 0.8f);
        s.trim();
        return s;
    }

    /** Ré-échantillonne une polyligne à pas constant. */
    static List<float[]> resample(List<float[]> in, float step) {
        ArrayList<float[]> out = new ArrayList<>();
        out.add(in.get(0));
        float carry = 0;
        for (int i = 1; i < in.size(); i++) {
            float[] a = in.get(i - 1), b = in.get(i);
            float d = dist(a, b);
            float pos = step - carry;
            while (pos <= d) {
                float t = pos / d;
                out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
                pos += step;
            }
            carry = d - (pos - step);
        }
        return out;
    }

    static void quad(List<float[]> out, float[] p0, float[] c, float[] p1, int n) {
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n, u = 1 - t;
            out.add(new float[]{u * u * p0[0] + 2 * u * t * c[0] + t * t * p1[0], u * u * p0[1] + 2 * u * t * c[1] + t * t * p1[1]});
        }
    }

    /**
     * Contour fermé d'un polygone : coins arrondis (coupe = round x demi-côté le plus court),
     * côtés bombés (flèche = bow[i] x longueur, vers l'extérieur si > 0). Le premier point n'est pas répété.
     */
    static List<float[]> outline(float[][] v, float round, float[] bow) {
        int k = v.length;
        float cx = 0, cy = 0;
        for (float[] p : v) {
            cx += p[0] / k;
            cy += p[1] / k;
        }
        float[][] pin = new float[k][], pout = new float[k][];
        for (int i = 0; i < k; i++) {
            float[] prev = v[(i - 1 + k) % k], cur = v[i], next = v[(i + 1) % k];
            float l1 = dist(prev, cur), l2 = dist(cur, next);
            float c = round * Math.min(l1, l2) * 0.5f;
            pin[i] = new float[]{cur[0] + (prev[0] - cur[0]) * c / l1, cur[1] + (prev[1] - cur[1]) * c / l1};
            pout[i] = new float[]{cur[0] + (next[0] - cur[0]) * c / l2, cur[1] + (next[1] - cur[1]) * c / l2};
        }
        ArrayList<float[]> out = new ArrayList<>();
        out.add(pout[0]);
        for (int i = 0; i < k; i++) {
            int j = (i + 1) % k;
            float[] a = pout[i], b = pin[j];
            float mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
            float dx = b[0] - a[0], dy = b[1] - a[1];
            float L = (float) Math.hypot(dx, dy);
            float nx = -dy / Math.max(L, 1e-3f), ny = dx / Math.max(L, 1e-3f);
            if ((mx - cx) * nx + (my - cy) * ny < 0) {
                nx = -nx;
                ny = -ny;
            }
            float off = 2 * bow[i] * dist(v[i], v[j]);
            quad(out, a, new float[]{mx + nx * off, my + ny * off}, b, 24);
            if (round > 0) quad(out, b, v[j], pout[j], 16);
            else out.add(pout[j]);
        }
        out.remove(out.size() - 1); // = pout[0]
        return out;
    }

    /**
     * Tracé à la main d'un contour fermé : départ quelque part, fin avant (end < 0) ou après (end > 0)
     * le point de départ, tremblement, rotation, petites amorces au début / à la fin.
     */
    Stroke draw(List<float[]> closed, float start, float end, float noise, double rotDeg, float cx, float cy, float hook) {
        ArrayList<float[]> loop = new ArrayList<>(closed);
        loop.add(closed.get(0));
        List<float[]> dense = resample(loop, 0.5f);
        int m = dense.size() - 1;
        float spacing = U(0.8, 2.6);
        int stride = Math.max(1, Math.round(spacing / 0.5f));
        int total = Math.round(m * (1 + end));
        int off = Math.round(start * m);
        ArrayList<float[]> pts = new ArrayList<>();
        double r = Math.toRadians(rotDeg), co = Math.cos(r), si = Math.sin(r);
        float wx = 0, wy = 0;
        float size = 0;
        for (float[] p : closed) size = Math.max(size, Math.max(Math.abs(p[0]), Math.abs(p[1])));
        // amorce : petit trait d'arrivée du stylet
        if (hook > 0) {
            float[] p0 = dense.get(off % m);
            double ang = rnd.nextDouble() * 2 * Math.PI;
            float hl = hook * size * 2;
            for (int i = 4; i >= 1; i--) pts.add(new float[]{p0[0] + (float) Math.cos(ang) * hl * i / 4, p0[1] + (float) Math.sin(ang) * hl * i / 4});
        }
        for (int i = 0; i <= total; i += stride) {
            float[] q = dense.get((i + off) % m);
            wx = wx * 0.92f + (float) rnd.nextGaussian() * noise * 0.35f;
            wy = wy * 0.92f + (float) rnd.nextGaussian() * noise * 0.35f;
            pts.add(new float[]{q[0] + wx + (float) rnd.nextGaussian() * noise * 0.2f, q[1] + wy + (float) rnd.nextGaussian() * noise * 0.2f});
        }
        if (hook > 0 && rnd.nextBoolean()) {
            float[] pl = pts.get(pts.size() - 1);
            double ang = rnd.nextDouble() * 2 * Math.PI;
            float hl = hook * size * 2;
            for (int i = 1; i <= 4; i++) pts.add(new float[]{pl[0] + (float) Math.cos(ang) * hl * i / 4, pl[1] + (float) Math.sin(ang) * hl * i / 4});
        }
        ArrayList<float[]> out = new ArrayList<>();
        for (float[] p : pts) out.add(new float[]{cx + (float) (p[0] * co - p[1] * si), cy + (float) (p[0] * si + p[1] * co)});
        return toStroke(out);
    }

    // ------------------------------------------------------------------ carrés

    /** Carré dessiné vite et mal ; level 0 = propre, 1 = franchement de travers. */
    Stroke square(float level) {
        float a = U(22, 120), asp = U(1 - 0.22 * level, 1 + 0.28 * level);
        float w = a, h = a * asp;
        float j = 0.11f * level;
        float[][] v = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
        for (float[] p : v) {
            p[0] += U(-j, j) * a;
            p[1] += U(-j, j) * a;
        }
        if (rnd.nextBoolean()) { // sens inverse
            float[] t = v[1];
            v[1] = v[3];
            v[3] = t;
        }
        float[] bow = new float[4];
        for (int i = 0; i < 4; i++) bow[i] = U(-0.04, 0.07) * level;
        float round = U(0, 0.08 + 0.32 * level);
        float end = U(-0.04 - 0.08 * level, 0.05 + 0.2 * level);
        float noise = U(0.2, 0.5 + 1.0 * level);
        double rot = U(-4 - 21 * level, 4 + 21 * level);
        float hook = rnd.nextFloat() < 0.3f * level ? U(0.02, 0.08) : 0;
        return draw(outline(v, round, bow), rnd.nextFloat(), end, noise, rot, 400, 400, hook);
    }


    /**
     * Carré dont les coins ont des accidents : petite boucle (kind 1), aller-retour qui dépasse le coin (2),
     * ou encoche vers l'intérieur (3). Chaque coin a l'accident avec la probabilité prob.
     */
    Stroke squareCorners(float level, int kind, float prob) {
        float a = U(22, 120), asp = U(1 - 0.22 * level, 1 + 0.28 * level);
        float w = a, h = a * asp, j = 0.11f * level;
        float[][] v = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
        for (float[] p : v) { p[0] += U(-j, j) * a; p[1] += U(-j, j) * a; }
        ArrayList<float[]> c = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            float[] A = v[i], B = v[(i + 1) % 4];
            float L = dist(A, B);
            float ux = (B[0] - A[0]) / L, uy = (B[1] - A[1]) / L;
            for (int k = 0; k < 30; k++) c.add(new float[]{A[0] + (B[0] - A[0]) * k / 30f, A[1] + (B[1] - A[1]) * k / 30f});
            // arrivée au coin B
            if (rnd.nextFloat() < prob) {
                float[] C = v[(i + 2) % 4];
                float L2 = dist(B, C);
                float vx = (C[0] - B[0]) / L2, vy = (C[1] - B[1]) / L2;
                float e = U(0.03, 0.10) * a;
                if (kind == 1) { // petite boucle : on dépasse, on tourne autour et on repart
                    float r = e * 0.6f;
                    float cx = B[0] + ux * r - vx * r, cy = B[1] + uy * r - vy * r;
                    double a0 = Math.atan2(B[1] - cy, B[0] - cx);
                    double cross = ux * vy - uy * vx;
                    for (int k = 0; k <= 16; k++) {
                        double t = a0 + (cross > 0 ? 1 : -1) * 2 * Math.PI * k / 16;
                        c.add(new float[]{cx + (float) (r * 1.4 * Math.cos(t)), cy + (float) (r * 1.4 * Math.sin(t))});
                    }
                } else if (kind == 2) { // dépasse le coin puis revient
                    for (int k = 1; k <= 5; k++) c.add(new float[]{B[0] + ux * e * k / 5f, B[1] + uy * e * k / 5f});
                    for (int k = 4; k >= 0; k--) c.add(new float[]{B[0] + ux * e * k / 5f + vx * e * 0.15f * (5 - k) / 5f, B[1] + uy * e * k / 5f + vy * e * 0.15f * (5 - k) / 5f});
                } else { // encoche vers l'intérieur
                    float mx = -B[0], my = -B[1];
                    float ml = (float) Math.hypot(mx, my);
                    for (int k = 1; k <= 4; k++) c.add(new float[]{B[0] + mx / ml * e * k / 4f, B[1] + my / ml * e * k / 4f});
                    for (int k = 3; k >= 1; k--) c.add(new float[]{B[0] + mx / ml * e * k / 4f, B[1] + my / ml * e * k / 4f});
                }
            }
        }
        double rot = U(-4 - 21 * level, 4 + 21 * level);
        return draw(c, rnd.nextFloat() * 0.0f, U(-0.04 - 0.08 * level, 0.05 + 0.2 * level), U(0.2, 0.5 + 1.0 * level), rot, 400, 400, 0);
    }

    // ------------------------------------------------------------------ ce qui ne doit PAS devenir une case

    Stroke ellipse() {
        float rx = U(10, 60), ry = rx * U(0.45, 1);
        int n = 180;
        ArrayList<float[]> c = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            c.add(new float[]{(float) (rx * Math.cos(t)), (float) (ry * Math.sin(t))});
        }
        if (rnd.nextBoolean()) java.util.Collections.reverse(c);
        return draw(c, rnd.nextFloat(), U(-0.12, 0.35), U(0.2, 1.6), U(0, 360), 400, 400, rnd.nextFloat() < 0.3f ? U(0.02, 0.1) : 0);
    }

    /** Forme ronde irrégulière (œuf, patate, rond déformé). */
    Stroke blob() {
        float R = U(12, 55);
        float a2 = U(-0.12, 0.12), a3 = U(-0.08, 0.08), a4 = U(-0.03, 0.03), a5 = U(-0.05, 0.05);
        float p2 = U(0, 6.3f), p3 = U(0, 6.3f), p4 = U(0, 6.3f), p5 = U(0, 6.3f);
        ArrayList<float[]> c = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            double t = 2 * Math.PI * i / 180;
            double r = R * (1 + a2 * Math.cos(2 * t + p2) + a3 * Math.cos(3 * t + p3) + a4 * Math.cos(4 * t + p4) + a5 * Math.cos(5 * t + p5));
            c.add(new float[]{(float) (r * Math.cos(t)), (float) (r * Math.sin(t))});
        }
        return draw(c, rnd.nextFloat(), U(-0.1, 0.3), U(0.2, 1.4), 0, 400, 400, 0);
    }

    Stroke polygon(int k) {
        float R = U(14, 50);
        float[][] v = new float[k][];
        double ph = U(0, 6.3f);
        for (int i = 0; i < k; i++) {
            double t = ph + 2 * Math.PI * i / k;
            float rr = R * U(0.9, 1.1);
            v[i] = new float[]{(float) (rr * Math.cos(t)), (float) (rr * Math.sin(t))};
        }
        float[] bow = new float[k];
        for (int i = 0; i < k; i++) bow[i] = U(-0.03, 0.05);
        return draw(outline(v, U(0, 0.3), bow), rnd.nextFloat(), U(-0.08, 0.2), U(0.2, 1.2), 0, 400, 400, 0);
    }

    /** Maison : un carré avec un toit (5 coins). */
    Stroke house() {
        float a = U(24, 70), roof = a * U(0.35, 0.8);
        float[][] v = {{-a / 2, a / 2}, {-a / 2, -a / 2}, {0, -a / 2 - roof}, {a / 2, -a / 2}, {a / 2, a / 2}};
        float[] bow = new float[5];
        return draw(outline(v, U(0, 0.15), bow), rnd.nextFloat(), U(-0.05, 0.15), U(0.2, 1.0), U(-8, 8), 400, 400, 0);
    }

    /** Lettre D (trait vertical puis panse ronde), en un seul trait. */
    Stroke letterD() {
        float h = U(18, 80), w = h * U(0.5, 1.0);
        ArrayList<float[]> c = new ArrayList<>();
        float flat = w * U(0, 0.45); // partie droite en haut / en bas avant l'arrondi
        c.add(new float[]{-w / 2, -h / 2});
        for (int i = 1; i <= 20; i++) c.add(new float[]{-w / 2 + flat * i / 20f, -h / 2});
        float rx = w - flat, ry = h / 2;
        for (int i = 1; i <= 60; i++) {
            double t = -Math.PI / 2 + Math.PI * i / 60;
            c.add(new float[]{-w / 2 + flat + (float) (rx * Math.cos(t)), (float) (ry * Math.sin(t))});
        }
        for (int i = 1; i <= 20; i++) c.add(new float[]{-w / 2 + flat - flat * i / 20f, h / 2});
        for (int i = 1; i < 30; i++) c.add(new float[]{-w / 2, h / 2 - h * i / 30f});
        return draw(c, U(0, 0.05), U(-0.08, 0.15), U(0.2, 1.2), U(-8, 8), 400, 400, 0);
    }

    /** a, d, g, q : une boucle ronde puis une hampe. */
    Stroke loopStem() {
        float r = U(8, 30), ry = r * U(0.8, 1.3);
        ArrayList<float[]> c = new ArrayList<>();
        int n = 80;
        for (int i = 0; i <= n * 1.05; i++) {
            double t = -0.2 - 2 * Math.PI * i / n; // départ en haut à droite, sens anti-horaire
            c.add(new float[]{(float) (r * Math.cos(t)), (float) (ry * Math.sin(t))});
        }
        float stem = ry * U(0.5, 3.5) * (rnd.nextBoolean() ? 1 : -1);
        float[] last = c.get(c.size() - 1);
        for (int i = 1; i <= 25; i++) c.add(new float[]{r, last[1] + stem * i / 25f});
        return drawOpen(c, U(0.2, 1.2), U(-10, 10));
    }

    /** 6, 9, e, l : boucle avec une queue. */
    Stroke loopTail() {
        float r = U(8, 30);
        ArrayList<float[]> c = new ArrayList<>();
        float tail = r * U(1, 3);
        double dir = U(0, 6.3f);
        for (int i = 25; i >= 1; i--) c.add(new float[]{(float) (Math.cos(dir) * (r + tail * i / 25f)), (float) (Math.sin(dir) * (r + tail * i / 25f))});
        for (int i = 0; i <= 90; i++) {
            double t = dir + 2 * Math.PI * i / 85;
            c.add(new float[]{(float) (r * Math.cos(t)), (float) (r * Math.sin(t))});
        }
        return drawOpen(c, U(0.2, 1.2), 0);
    }

    /** 8 : deux boucles. */
    Stroke eight() {
        float r = U(7, 25);
        ArrayList<float[]> c = new ArrayList<>();
        for (int i = 0; i <= 160; i++) {
            double t = 2 * Math.PI * i / 160;
            c.add(new float[]{(float) (r * Math.sin(2 * t) * 0.8), (float) (-2 * r * Math.cos(t))});
        }
        return drawOpen(c, U(0.2, 1.2), U(-10, 10));
    }

    /** Étoile en un trait. */
    Stroke star() {
        float R = U(15, 50);
        ArrayList<float[]> v = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            double t = -Math.PI / 2 + i * 4 * Math.PI / 5;
            v.add(new float[]{(float) (R * Math.cos(t)), (float) (R * Math.sin(t))});
        }
        return drawOpen(resample(v, 0.5f), U(0.2, 1.2), U(-10, 10));
    }

    /** Cœur. */
    Stroke heart() {
        float s = U(1, 3.2f);
        ArrayList<float[]> c = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            double t = 2 * Math.PI * i / 180;
            c.add(new float[]{(float) (s * 16 * Math.pow(Math.sin(t), 3)), (float) (-s * (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)))});
        }
        return draw(c, 0, U(-0.05, 0.1), U(0.2, 1.0), 0, 400, 400, 0);
    }

    /** Lettres anguleuses fermées ou presque : A, R, P, B, 4, Q, b. */
    Stroke pLetter() {
        // P / R / b : hampe + boucle (rectangulaire arrondie ou ronde)
        float h = U(20, 80), w = h * U(0.35, 0.7);
        ArrayList<float[]> c = new ArrayList<>();
        for (int i = 0; i <= 30; i++) c.add(new float[]{0, h / 2 - h * i / 30f}); // remonte
        float bh = h * U(0.4, 0.6);
        for (int i = 1; i <= 50; i++) {
            double t = -Math.PI / 2 + Math.PI * i / 50;
            c.add(new float[]{(float) (w * Math.cos(t)), -h / 2 + bh / 2 + (float) (bh / 2 * Math.sin(t))});
        }
        for (int i = 1; i <= 10; i++) c.add(new float[]{0, -h / 2 + bh});
        return drawOpen(c, U(0.2, 1.2), U(-8, 8));
    }


    // ------------------------------------------------------------------ plusieurs traits

    /** Polyligne ouverte : coins arrondis, côtés bombés, bouts prolongés (ext > 0) ou raccourcis. */
    static List<float[]> openPath(float[][] v, float round, float bow, float ext0, float ext1) {
        int k = v.length;
        float[][] w = new float[k][];
        for (int i = 0; i < k; i++) w[i] = v[i].clone();
        float[] a = v[0], b = v[1];
        float l = dist(a, b);
        w[0] = new float[]{a[0] - (b[0] - a[0]) * ext0, a[1] - (b[1] - a[1]) * ext0};
        a = v[k - 1]; b = v[k - 2];
        w[k - 1] = new float[]{a[0] - (b[0] - a[0]) * ext1, a[1] - (b[1] - a[1]) * ext1};
        ArrayList<float[]> out = new ArrayList<>();
        out.add(w[0]);
        float[] cur = w[0];
        for (int i = 1; i < k; i++) {
            float[] nxt = w[i];
            float[] end = nxt;
            float[] after = null;
            if (i < k - 1 && round > 0) {
                float l1 = dist(w[i], w[i - 1]), l2 = dist(w[i], w[i + 1]);
                float c = round * Math.min(l1, l2) * 0.5f;
                end = new float[]{w[i][0] + (w[i - 1][0] - w[i][0]) * c / l1, w[i][1] + (w[i - 1][1] - w[i][1]) * c / l1};
                after = new float[]{w[i][0] + (w[i + 1][0] - w[i][0]) * c / l2, w[i][1] + (w[i + 1][1] - w[i][1]) * c / l2};
            }
            float mx = (cur[0] + end[0]) / 2, my = (cur[1] + end[1]) / 2, dx = end[0] - cur[0], dy = end[1] - cur[1];
            float L = (float) Math.hypot(dx, dy);
            float off = 2 * bow * L;
            quad(out, cur, new float[]{mx - dy / Math.max(L, 1e-3f) * off, my + dx / Math.max(L, 1e-3f) * off}, end, 24);
            if (after != null) {
                quad(out, end, w[i], after, 12);
                cur = after;
            } else cur = end;
        }
        return out;
    }

    /** Carré en k traits (2, 3 ou 4), chacun dans un sens au hasard ; la liste est dans l'ordre du dessin. */
    List<Stroke> squareStrokes(float level, int k) {
        float a = U(22, 120), asp = U(1 - 0.22 * level, 1 + 0.28 * level);
        float w = a, h = a * asp, j = 0.11f * level;
        float[][] v = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
        double rot = Math.toRadians(U(-4 - 21 * level, 4 + 21 * level));
        for (float[] p : v) {
            p[0] += U(-j, j) * a;
            p[1] += U(-j, j) * a;
            float x = p[0], y = p[1];
            p[0] = (float) (x * Math.cos(rot) - y * Math.sin(rot));
            p[1] = (float) (x * Math.sin(rot) + y * Math.cos(rot));
        }
        int s0 = rnd.nextInt(4);
        boolean ccw = rnd.nextBoolean();
        float[][] q = new float[5][];
        for (int i = 0; i < 5; i++) q[i] = v[((ccw ? s0 - i : s0 + i) % 4 + 4) % 4];
        int[][] parts = k == 2 ? new int[][]{{0, 1, 2}, {2, 3, 4}} : k == 3 ? new int[][]{{0, 1, 2}, {2, 3}, {3, 4}}
                : new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}};
        ArrayList<Stroke> out = new ArrayList<>();
        float noise = U(0.2, 0.5 + 1.0 * level), round = U(0, 0.08 + 0.3 * level);
        for (int[] part : parts) {
            float[][] pv = new float[part.length][];
            for (int i = 0; i < part.length; i++) pv[i] = q[part[i]];
            if (rnd.nextFloat() < 0.5f) { // tracé dans l'autre sens
                for (int i = 0; i < pv.length / 2; i++) {
                    float[] t = pv[i];
                    pv[i] = pv[pv.length - 1 - i];
                    pv[pv.length - 1 - i] = t;
                }
            }
            float lim = 0.1f + 0.08f * level;
            out.add(drawOpen(openPath(pv, round, U(-0.03, 0.05) * level, U(-lim, lim), U(-lim, lim)), noise, 0));
        }
        return out;
    }

    /** Ce qui s'écrit en plusieurs traits et ne doit pas devenir une case. */
    List<Stroke> multiNegative(int kind) {
        ArrayList<Stroke> out = new ArrayList<>();
        float h = U(18, 90), w = h * U(0.45, 0.9), n = U(0.2, 1.2);
        switch (kind) {
            case 0: { // D en deux traits
                out.add(drawOpen(openPath(new float[][]{{0, 0}, {0, h}}, 0, 0, U(-0.05, 0.1f), U(-0.05, 0.1f)), n, 0));
                ArrayList<float[]> c = new ArrayList<>();
                float flat = w * U(0, 0.4);
                c.add(new float[]{0, 0});
                c.add(new float[]{flat, 0});
                for (int i = 1; i <= 50; i++) {
                    double t = -Math.PI / 2 + Math.PI * i / 50;
                    c.add(new float[]{flat + (float) ((w - flat) * Math.cos(t)), h / 2 + (float) (h / 2 * Math.sin(t))});
                }
                c.add(new float[]{0, h});
                out.add(drawOpen(resample(c, 0.5f), n, 0));
                break;
            }
            case 1: { // P / R : hampe + boucle
                out.add(drawOpen(openPath(new float[][]{{0, h}, {0, 0}}, 0, 0, 0, U(-0.05, 0.1f)), n, 0));
                ArrayList<float[]> c = new ArrayList<>();
                float bh = h * U(0.4, 0.6);
                for (int i = 0; i <= 50; i++) {
                    double t = -Math.PI / 2 + Math.PI * i / 50;
                    c.add(new float[]{(float) (w * Math.cos(t)), bh / 2 + (float) (bh / 2 * Math.sin(t))});
                }
                c.add(new float[]{0, bh});
                if (rnd.nextBoolean()) c.add(new float[]{w, h}); // R
                out.add(drawOpen(resample(c, 0.5f), n, 0));
                break;
            }
            case 2: { // O en deux moitiés
                for (int half = 0; half < 2; half++) {
                    ArrayList<float[]> c = new ArrayList<>();
                    for (int i = 0; i <= 50; i++) {
                        double t = -Math.PI / 2 + Math.PI * (half + i / 50.0) * (1 + U(-0.05, 0.08));
                        c.add(new float[]{(float) (w / 2 * Math.cos(t)), (float) (h / 2 * Math.sin(t))});
                    }
                    out.add(drawOpen(c, n, 0));
                }
                break;
            }
            case 3: { // triangle en 3 traits
                float[][] v = {{0, 0}, {w / 2, h}, {-w / 2, h}, {0, 0}};
                for (int i = 0; i < 3; i++) out.add(drawOpen(openPath(new float[][]{v[i], v[i + 1]}, 0, 0, U(-0.05, 0.1f), U(-0.05, 0.1f)), n, 0));
                break;
            }
            case 4: { // E
                out.add(drawOpen(openPath(new float[][]{{0, 0}, {0, h}}, 0, 0, 0, 0), n, 0));
                for (int i = 0; i < 3; i++) out.add(drawOpen(openPath(new float[][]{{0, h * i / 2f}, {w, h * i / 2f}}, 0, 0, 0, 0), n, 0));
                break;
            }
            case 5: { // H
                out.add(drawOpen(openPath(new float[][]{{0, 0}, {0, h}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{w, 0}, {w, h}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{0, h / 2}, {w, h / 2}}, 0, 0, 0, 0), n, 0));
                break;
            }
            case 6: { // #
                out.add(drawOpen(openPath(new float[][]{{w * 0.33f, 0}, {w * 0.3f, h}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{w * 0.7f, 0}, {w * 0.67f, h}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{0, h * 0.35f}, {w, h * 0.33f}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{0, h * 0.68f}, {w, h * 0.66f}}, 0, 0, 0, 0), n, 0));
                break;
            }
            case 7: { // 4
                out.add(drawOpen(openPath(new float[][]{{w * 0.6f, 0}, {0, h * 0.65f}, {w, h * 0.65f}}, 0.1f, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{w * 0.7f, h * 0.2f}, {w * 0.7f, h}}, 0, 0, 0, 0), n, 0));
                break;
            }
            case 8: { // A
                out.add(drawOpen(openPath(new float[][]{{-w / 2, h}, {0, 0}, {w / 2, h}}, 0.1f, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{-w / 4, h * 0.6f}, {w / 4, h * 0.6f}}, 0, 0, 0, 0), n, 0));
                break;
            }
            case 9: { // B : hampe + deux boucles
                out.add(drawOpen(openPath(new float[][]{{0, 0}, {0, h}}, 0, 0, 0, 0), n, 0));
                ArrayList<float[]> c = new ArrayList<>();
                for (int lobe = 0; lobe < 2; lobe++)
                    for (int i = 0; i <= 40; i++) {
                        double t = -Math.PI / 2 + Math.PI * i / 40;
                        c.add(new float[]{(float) (w * (lobe == 0 ? 0.8 : 1) * Math.cos(t)), h * (lobe * 0.5f + 0.25f) + (float) (h / 4 * Math.sin(t))});
                    }
                out.add(drawOpen(c, n, 0));
                break;
            }
            default: { // T puis =, croix
                out.add(drawOpen(openPath(new float[][]{{0, 0}, {w, 0}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{w / 2, 0}, {w / 2, h}}, 0, 0, 0, 0), n, 0));
                out.add(drawOpen(openPath(new float[][]{{0, h}, {w, 0}}, 0, 0, 0, 0), n, 0));
            }
        }
        return out;
    }

    /** Tracé ouvert (pas de bouclage forcé). */
    Stroke drawOpen(List<float[]> c, float noise, double rotDeg) {
        List<float[]> dense = resample(c, 0.5f);
        float spacing = U(0.8, 2.6);
        int stride = Math.max(1, Math.round(spacing / 0.5f));
        ArrayList<float[]> out = new ArrayList<>();
        double r = Math.toRadians(rotDeg), co = Math.cos(r), si = Math.sin(r);
        float wx = 0, wy = 0;
        for (int i = 0; i < dense.size(); i += stride) {
            float[] q = dense.get(i);
            wx = wx * 0.92f + (float) rnd.nextGaussian() * noise * 0.35f;
            wy = wy * 0.92f + (float) rnd.nextGaussian() * noise * 0.35f;
            float x = q[0] + wx + (float) rnd.nextGaussian() * noise * 0.2f, y = q[1] + wy + (float) rnd.nextGaussian() * noise * 0.2f;
            out.add(new float[]{400 + (float) (x * co - y * si), 400 + (float) (x * si + y * co)});
        }
        return toStroke(out);
    }
}
