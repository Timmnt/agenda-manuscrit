package fr.agenda.manuscrit;

import java.util.List;

/** Géométrie pure : reconnaissance des carrés (cases à cocher), polygone du lasso, hauteur de page. */
final class Shapes {
    private Shapes() {}

    /** Point dans un polygone (x0, y0, x1, y1, ...) de n points (règle pair-impair). */
    static boolean inside(float[] poly, int n, float x, float y) {
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i * 2], yi = poly[i * 2 + 1], xj = poly[j * 2], yj = poly[j * 2 + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ------------------------------------------------------------------ cases à cocher dessinées

    /** Un carré reconnu : centre, et nombre de traits utilisés (les derniers dessinés). */
    static final class Box {
        final float cx, cy;
        final int strokes;

        Box(float cx, float cy, int strokes) {
            this.cx = cx;
            this.cy = cy;
            this.strokes = strokes;
        }
    }

    /**
     * Cherche un carré dans les derniers traits (le plus récent en dernier) : le dernier seul,
     * puis les 2, 3 ou 4 derniers mis bout à bout (carré dessiné en plusieurs fois).
     */
    static Box findBox(List<Stroke> recent) {
        int n = recent.size();
        for (int k = 1; k <= Math.min(4, n); k++) {
            List<Stroke> sub = recent.subList(n - k, n);
            boolean small = true;
            for (Stroke s : sub) {
                if (s.isHighlighter() || s.maxX - s.minX > 200f || s.maxY - s.minY > 200f) small = false;
            }
            if (!small) break;
            Stroke s = k == 1 ? sub.get(0) : chain(sub, 0.3f);
            if (s == null) continue;
            float[] b = recognizeBox(s);
            if (b != null) return new Box(b[0], b[1], k);
        }
        return null;
    }

    private static final int M = 96; // points de la boucle ré-échantillonnée

    /**
     * Un carré dessiné à main levée ? Renvoie {centreX, centreY, côté moyen} ou null.
     * Le tracé est vu comme une boucle (amorce et dépassement coupés), ses 4 coins sont repérés aux
     * plus forts virages, et chaque côté est remplacé par une droite : le quadrilatère obtenu peut être
     * penché, en trapèze ou un peu de travers. On vérifie ensuite que le tracé le suit de près, que les
     * coins sont de vrais coins et que les côtés ne sont pas des arcs (ce qui écarte ronds, D, P, maisons...).
     * Seuils réglés sur 230 000 tracés simulés : aucun rond ni lettre pris pour une case.
     */
    static float[] recognizeBox(Stroke s) {
        if (s == null || s.n < 8 || s.isHighlighter()) return null;
        float[] p = s.pts;
        int n = s.n;
        float size = Math.max(s.maxX - s.minX, s.maxY - s.minY);
        if (size < 16f || size > 240f) return null;

        // --- bouclage : l'endroit où la fin revient le plus près du début (coupe amorce et dépassement)
        int lim1 = (int) (n * 0.25f), from2 = (int) (n * 0.55f);
        int st = Math.max(1, n / 160);
        float best = Float.MAX_VALUE;
        int bi = 0, bj = n - 1;
        for (int i = 0; i <= lim1; i += st) {
            for (int j = Math.max(from2, i + 8); j < n; j += st) {
                float dd = dist(p[i * 3], p[i * 3 + 1], p[j * 3], p[j * 3 + 1]);
                if (dd < best) {
                    best = dd;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (st > 1) {
            int ci = bi, cj = bj;
            for (int i = Math.max(0, ci - st); i <= Math.min(lim1, ci + st); i++) {
                for (int j = Math.max(from2, cj - st); j <= Math.min(n - 1, cj + st); j++) {
                    float dd = dist(p[i * 3], p[i * 3 + 1], p[j * 3], p[j * 3 + 1]);
                    if (dd < best) {
                        best = dd;
                        bi = i;
                        bj = j;
                    }
                }
            }
        }
        if (bj - bi < 8) return null;
        float total = 0, inner = 0;
        for (int i = 1; i < n; i++) {
            float dd = dist(p[(i - 1) * 3], p[(i - 1) * 3 + 1], p[i * 3], p[i * 3 + 1]);
            total += dd;
            if (i > bi && i <= bj) inner += dd;
        }
        float loopLen = inner + best;
        if (loopLen <= 0 || (total - inner) / loopLen > 0.33f) return null; // trop de traits en plus de la boucle

        // --- boucle refermée, ré-échantillonnée en M points réguliers, puis lissée
        float step = loopLen / M;
        float[] rx = new float[M], ry = new float[M];
        int cnt = bj - bi + 1;
        rx[0] = p[bi * 3];
        ry[0] = p[bi * 3 + 1];
        int m = 1;
        float carry = 0;
        for (int k = 1; k <= cnt && m < M; k++) {
            int a = bi + k - 1, b = k == cnt ? bi : bi + k;
            float ax = p[a * 3], ay = p[a * 3 + 1], bx = p[b * 3], by = p[b * 3 + 1];
            float dd = dist(ax, ay, bx, by);
            float pos = step - carry;
            while (pos <= dd && m < M) {
                float t = pos / dd;
                rx[m] = ax + (bx - ax) * t;
                ry[m] = ay + (by - ay) * t;
                m++;
                pos += step;
            }
            carry = dd - (pos - step);
        }
        while (m < M) {
            rx[m] = rx[m - 1];
            ry[m] = ry[m - 1];
            m++;
        }
        float[] sx = new float[M], sy = new float[M];
        for (int i = 0; i < M; i++) {
            float ax = 0, ay = 0;
            for (int k = -2; k <= 2; k++) {
                int j = ((i + k) % M + M) % M;
                ax += rx[j];
                ay += ry[j];
            }
            sx[i] = ax / 5f;
            sy[i] = ay / 5f;
        }

        // --- virages : une boucle simple tourne d'un tour complet
        double[] dth = new double[M];
        double tot = 0, prevTh = 0;
        for (int i = 0; i <= M; i++) {
            int c = i % M;
            double th = Math.atan2(sy[(c + 2) % M] - sy[(c - 2 + M) % M], sx[(c + 2) % M] - sx[(c - 2 + M) % M]);
            if (i > 0) {
                double v = th - prevTh;
                while (v > Math.PI) v -= 2 * Math.PI;
                while (v < -Math.PI) v += 2 * Math.PI;
                dth[i - 1] = v;
                tot += v;
            }
            prevTh = th;
        }
        double wind = Math.abs(tot) / (2 * Math.PI);
        if (wind < 0.8 || wind > 1.2) return null; // pas une boucle simple (8, étoile, gribouillis)
        double sg = tot >= 0 ? 1 : -1;

        // --- les 5 plus forts virages sur une fenêtre de 10 % du tour
        int win = Math.max(3, Math.round(M * 0.1f));
        double[] turn = new double[M];
        for (int i = 0; i < M; i++) {
            double sm = 0;
            for (int k = 0; k < win; k++) sm += dth[(i + k) % M];
            turn[i] = sm * sg;
        }
        boolean[] used = new boolean[M];
        float[] turns = new float[5];
        int[] cidx = new int[5];
        for (int c = 0; c < 5; c++) {
            double bv = -1e9;
            int bk = -1;
            for (int i = 0; i < M; i++) {
                if (!used[i] && turn[i] > bv) {
                    bv = turn[i];
                    bk = i;
                }
            }
            if (bk < 0) { // plus de place pour un coin de plus
                turns[c] = 0;
                cidx[c] = c == 0 ? 0 : cidx[c - 1];
                continue;
            }
            turns[c] = (float) Math.toDegrees(bv);
            cidx[c] = (bk + win / 2) % M;
            for (int k = -win + 1; k <= win - 1; k++) used[((bk + k) % M + M) % M] = true;
        }
        // 4 coins nets, pas de 5e coin (maison, pentagone)
        if (turns[3] < 46f || turns[4] > 29.3f) return null;
        // netteté : le virage de chaque coin se fait en peu de place (un arc de rond tourne lentement)
        int half = Math.max(2, win / 2);
        float sharpMin = Float.MAX_VALUE;
        for (int c = 0; c < 4; c++) {
            double bestS = -1e9;
            for (int o = -win / 2; o <= win / 2; o++) {
                double sm = 0;
                for (int k = 0; k < half; k++) sm += dth[((cidx[c] - half / 2 + o + k) % M + M) % M];
                bestS = Math.max(bestS, sm * sg);
            }
            sharpMin = Math.min(sharpMin, (float) Math.toDegrees(bestS));
        }
        int[] cs = {cidx[0], cidx[1], cidx[2], cidx[3]};
        java.util.Arrays.sort(cs);

        // --- côtés : pas des arcs (virage cumulé au milieu de chaque côté)
        for (int sd = 0; sd < 4; sd++) {
            int a = cs[sd], b = cs[(sd + 1) % 4];
            int len = ((b - a) % M + M) % M;
            if (len < 4) return null;
            double sm = 0;
            for (int i = a + Math.round(len * 0.2f); i < a + len - Math.round(len * 0.2f); i++) sm += dth[i % M] * sg;
            if (Math.toDegrees(sm) > 46f) return null;
        }

        // --- une droite par côté (ajustée sur le milieu du côté), les coins = intersections
        float[][] lp = new float[4][], ld = new float[4][];
        for (int sd = 0; sd < 4; sd++) {
            int a = cs[sd], b = cs[(sd + 1) % 4];
            int len = ((b - a) % M + M) % M;
            int from = a + Math.round(len * 0.2f), to = a + len - Math.round(len * 0.2f);
            double mx = 0, my = 0;
            int k = 0;
            for (int i = from; i <= to; i++) {
                mx += rx[i % M];
                my += ry[i % M];
                k++;
            }
            mx /= k;
            my /= k;
            double sxx = 0, syy = 0, sxy = 0;
            for (int i = from; i <= to; i++) {
                double ux = rx[i % M] - mx, uy = ry[i % M] - my;
                sxx += ux * ux;
                syy += uy * uy;
                sxy += ux * uy;
            }
            double ang = 0.5 * Math.atan2(2 * sxy, sxx - syy);
            lp[sd] = new float[]{(float) mx, (float) my};
            ld[sd] = new float[]{(float) Math.cos(ang), (float) Math.sin(ang)};
        }
        float[][] v = new float[4][];
        for (int sd = 0; sd < 4; sd++) {
            float[] p1 = lp[(sd + 3) % 4], d1 = ld[(sd + 3) % 4], p2 = lp[sd], d2 = ld[sd];
            float cr = d1[0] * d2[1] - d1[1] * d2[0];
            if (Math.abs(cr) < 0.42f) return null; // côtés voisins presque parallèles
            float t = ((p2[0] - p1[0]) * d2[1] - (p2[1] - p1[1]) * d2[0]) / cr;
            v[sd] = new float[]{p1[0] + d1[0] * t, p1[1] + d1[1] * t};
        }

        // --- le quadrilatère : convexe, angles pas trop loin de 90°, côtés comparables
        float sum = 0, minS = Float.MAX_VALUE, maxS = 0;
        int sgn = 0;
        for (int sd = 0; sd < 4; sd++) {
            float[] a = v[sd], b = v[(sd + 1) % 4], c = v[(sd + 2) % 4];
            float l = dist(a[0], a[1], b[0], b[1]);
            sum += l;
            minS = Math.min(minS, l);
            maxS = Math.max(maxS, l);
            float e1x = b[0] - a[0], e1y = b[1] - a[1], e2x = c[0] - b[0], e2y = c[1] - b[1];
            int sg2 = e1x * e2y - e1y * e2x > 0 ? 1 : -1;
            if (sgn == 0) sgn = sg2;
            else if (sg2 != sgn) return null;
            double cos = -(e1x * e2x + e1y * e2y) / (Math.hypot(e1x, e1y) * Math.hypot(e2x, e2y) + 1e-9);
            double inner2 = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
            if (Math.abs(inner2 - 90) > 40) return null;
        }
        float side = sum / 4f;
        if (side < 20f || side > 165f || minS < 0.59f * maxS) return null;
        if (sharpMin < (side < 32f ? 42f : 36f)) return null; // petits tracés : le tremblement compte plus
        if (best > 0.62f * side) return null; // pas refermé

        // --- le tracé suit le quadrilatère de près et le remplit
        double qa = 0, la = 0;
        for (int sd = 0; sd < 4; sd++) qa += v[sd][0] * v[(sd + 1) % 4][1] - v[(sd + 1) % 4][0] * v[sd][1];
        for (int i = 0; i < M; i++) la += rx[i] * ry[(i + 1) % M] - rx[(i + 1) % M] * ry[i];
        if (Math.abs(la) < 0.88 * Math.abs(qa)) return null;
        float fitMean = 0, fitMax = 0;
        for (int i = 0; i < M; i++) {
            float dm = Float.MAX_VALUE, dr = Float.MAX_VALUE;
            for (int sd = 0; sd < 4; sd++) {
                dm = Math.min(dm, segDist(sx[i], sy[i], v[sd], v[(sd + 1) % 4]));
                dr = Math.min(dr, segDist(rx[i], ry[i], v[sd], v[(sd + 1) % 4]));
            }
            fitMean += dm;
            fitMax = Math.max(fitMax, dr);
        }
        if (fitMean / M > 0.031f * side || fitMax > 0.205f * side) return null;
        return new float[]{(v[0][0] + v[1][0] + v[2][0] + v[3][0]) / 4f, (v[0][1] + v[1][1] + v[2][1] + v[3][1]) / 4f, side};
    }

    private static float segDist(float px, float py, float[] a, float[] b) {
        float vx = b[0] - a[0], vy = b[1] - a[1];
        float l2 = vx * vx + vy * vy;
        float t = l2 <= 0 ? 0 : ((px - a[0]) * vx + (py - a[1]) * vy) / l2;
        t = Math.max(0, Math.min(1, t));
        return dist(a[0] + vx * t, a[1] + vy * t, px, py);
    }

    /**
     * Met des traits bout à bout (retournés si besoin) en partant du dernier ; null si un raccord est
     * plus loin que maxJoinFrac x la taille de l'ensemble. Quand deux traits se croisent à un coin
     * (l'un dépasse), ils sont coupés au croisement.
     */
    static Stroke chain(List<Stroke> strokes, float maxJoinFrac) {
        int k = strokes.size();
        if (k == 0) return null;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (Stroke s : strokes) {
            if (s.n < 2) return null;
            x0 = Math.min(x0, s.minX);
            y0 = Math.min(y0, s.minY);
            x1 = Math.max(x1, s.maxX);
            y1 = Math.max(y1, s.maxY);
        }
        float maxJoin = maxJoinFrac * Math.max(x1 - x0, y1 - y0);
        boolean[] done = new boolean[k];
        java.util.ArrayDeque<int[]> dq = new java.util.ArrayDeque<>(); // {trait, retourné}
        Stroke last = strokes.get(k - 1);
        dq.add(new int[]{k - 1, 0});
        done[k - 1] = true;
        float hx = last.pts[0], hy = last.pts[1], tx = last.lastX(), ty = last.lastY();
        for (int added = 1; added < k; added++) {
            float bd = Float.MAX_VALUE;
            int bs = -1;
            boolean bRev = false, bHead = false;
            for (int i = 0; i < k; i++) {
                if (done[i]) continue;
                Stroke s = strokes.get(i);
                float ax = s.pts[0], ay = s.pts[1], ex = s.lastX(), ey = s.lastY();
                float d1 = dist(tx, ty, ax, ay), d2 = dist(tx, ty, ex, ey), d3 = dist(hx, hy, ex, ey), d4 = dist(hx, hy, ax, ay);
                if (d1 < bd) { bd = d1; bs = i; bRev = false; bHead = false; }
                if (d2 < bd) { bd = d2; bs = i; bRev = true; bHead = false; }
                if (d3 < bd) { bd = d3; bs = i; bRev = false; bHead = true; }
                if (d4 < bd) { bd = d4; bs = i; bRev = true; bHead = true; }
            }
            if (bs < 0 || bd > maxJoin) return null;
            done[bs] = true;
            Stroke s = strokes.get(bs);
            if (bHead) {
                dq.addFirst(new int[]{bs, bRev ? 1 : 0});
                hx = bRev ? s.lastX() : s.pts[0];
                hy = bRev ? s.lastY() : s.pts[1];
            } else {
                dq.addLast(new int[]{bs, bRev ? 1 : 0});
                tx = bRev ? s.pts[0] : s.lastX();
                ty = bRev ? s.pts[1] : s.lastY();
            }
        }
        // traits orientés dans l'ordre de la chaîne
        float[][] arr = new float[k][];
        int[] cnt = new int[k];
        int q = 0;
        for (int[] e : dq) {
            Stroke s = strokes.get(e[0]);
            float[] a = new float[s.n * 3];
            for (int i = 0; i < s.n; i++) {
                int src = e[1] == 0 ? i : s.n - 1 - i;
                a[i * 3] = s.pts[src * 3];
                a[i * 3 + 1] = s.pts[src * 3 + 1];
                a[i * 3 + 2] = s.pts[src * 3 + 2];
            }
            arr[q] = a;
            cnt[q] = s.n;
            q++;
        }
        int[] from = new int[k], to = new int[k];
        for (int i = 0; i < k; i++) to[i] = cnt[i] - 1;
        for (int j = 0; j + 1 < k; j++) {
            float[] a = arr[j], b = arr[j + 1];
            float bd = Float.MAX_VALUE;
            int bi = cnt[j] - 1, bj = 0;
            for (int i = Math.max(from[j] + 1, (int) (cnt[j] * 0.6f)); i < cnt[j]; i++) {
                for (int l = 0; l <= (int) (cnt[j + 1] * 0.4f); l++) {
                    float dx = a[i * 3] - b[l * 3], dy = a[i * 3 + 1] - b[l * 3 + 1];
                    float dd = dx * dx + dy * dy;
                    if (dd < bd - 1e-6f) {
                        bd = dd;
                        bi = i;
                        bj = l;
                    }
                }
            }
            to[j] = bi;
            from[j + 1] = bj;
        }
        int total = 0;
        for (int i = 0; i < k; i++) total += Math.max(0, to[i] - from[i] + 1);
        Stroke out = new Stroke(last.color, last.width, 0, Math.max(4, total));
        for (int i = 0; i < k; i++) {
            for (int j = from[i]; j <= to[i]; j++) out.add(arr[i][j * 3], arr[i][j * 3 + 1], arr[i][j * 3 + 2]);
        }
        out.trim();
        return out;
    }

    /** Bas du contenu (0 si la page est vide). */
    static float contentBottom(List<Item> items) {
        float b = 0;
        for (int i = 0; i < items.size(); i++) b = Math.max(b, items.get(i).bottom());
        return b;
    }

    /** Hauteur de la page : normale, ou allongée par tranches quand on écrit jusqu'en bas. */
    static float pageHeight(List<Item> items) {
        float bottom = contentBottom(items);
        float need = bottom + 2.5f * Geo.RULE_STEP;
        if (bottom <= 0 || need <= Geo.H) return Geo.H;
        return Geo.H + (float) Math.ceil((need - Geo.H) / Geo.EXT) * Geo.EXT;
    }
}
