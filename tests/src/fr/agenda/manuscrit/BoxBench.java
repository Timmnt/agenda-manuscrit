package fr.agenda.manuscrit;

import java.util.Collections;
import java.util.List;

/**
 * Reconnaissance des carrés sur des tracés simulés (voir Gen) : carrés plus ou moins bâclés, en 1 à 4 traits,
 * et formes qui ne doivent PAS devenir des cases (ronds, D, maisons, lettres...).
 * Usage : BoxBench [N]  (N tracés par catégorie, 600 par défaut ; 4000 pour une mesure fine).
 */
public class BoxBench {
    interface G { List<Stroke> make(Gen g); }

    static int fails = 0;
    static long fp = 0, neg = 0;

    static double rate(String name, G f, int n, long seed, boolean positive) {
        Gen g = new Gen(seed);
        int ok = 0;
        for (int i = 0; i < n; i++) {
            List<Stroke> l = f.make(g);
            boolean hit = false;
            if (positive) {
                Shapes.Box b = Shapes.findBox(l);
                hit = b != null && b.strokes == l.size();
            } else {
                for (int k = 1; k <= l.size(); k++) if (Shapes.findBox(l.subList(0, k)) != null) hit = true;
            }
            if (hit) ok++;
        }
        if (!positive) {
            fp += ok;
            neg += n;
        }
        System.out.printf("  %-30s %s %5d / %d  (%.1f %%)%n", name, positive ? "reconnus " : "faux pos.", ok, n, 100.0 * ok / n);
        return ok / (double) n;
    }

    static List<Stroke> one(Stroke s) {
        return Collections.singletonList(s);
    }

    static void expect(boolean ok, String what) {
        if (!ok) {
            fails++;
            System.out.println("ÉCHEC : " + what);
        }
    }

    public static void main(String[] a) {
        int n = a.length > 0 ? Integer.parseInt(a[0]) : 600;
        System.out.println("Reconnaissance des carrés (" + n + " tracés par catégorie)");
        expect(rate("carrés propres", new G() { public List<Stroke> make(Gen g) { return one(g.square(0.3f)); } }, n, 101, true) >= 0.98, "carrés propres >= 98 %");
        expect(rate("carrés un peu de travers", new G() { public List<Stroke> make(Gen g) { return one(g.square(0.7f)); } }, n, 102, true) >= 0.96, "carrés de travers >= 96 %");
        expect(rate("carrés bâclés", new G() { public List<Stroke> make(Gen g) { return one(g.square(1.0f)); } }, n, 103, true) >= 0.85, "carrés bâclés >= 85 %");
        expect(rate("boucles aux coins", new G() { public List<Stroke> make(Gen g) { return one(g.squareCorners(0.5f, 1, 1f)); } }, n, 104, true) >= 0.95, "boucles aux coins >= 95 %");
        for (int k = 2; k <= 4; k++) {
            final int kk = k;
            expect(rate("carrés en " + k + " traits", new G() { public List<Stroke> make(Gen g) { return g.squareStrokes(g.U(0, 1.2), kk); } }, n, 110 + k, true) >= 0.94, "carrés en " + k + " traits >= 94 %");
        }
        rate("ronds / ellipses", new G() { public List<Stroke> make(Gen g) { return one(g.ellipse()); } }, n * 3, 120, false);
        rate("formes rondes irrégulières", new G() { public List<Stroke> make(Gen g) { return one(g.blob()); } }, n, 121, false);
        rate("triangles", new G() { public List<Stroke> make(Gen g) { return one(g.polygon(3)); } }, n, 122, false);
        rate("pentagones", new G() { public List<Stroke> make(Gen g) { return one(g.polygon(5)); } }, n, 123, false);
        rate("maisons", new G() { public List<Stroke> make(Gen g) { return one(g.house()); } }, n, 124, false);
        rate("D", new G() { public List<Stroke> make(Gen g) { return one(g.letterD()); } }, n, 125, false);
        rate("a d g q", new G() { public List<Stroke> make(Gen g) { return one(g.loopStem()); } }, n, 126, false);
        rate("6 9 e", new G() { public List<Stroke> make(Gen g) { return one(g.loopTail()); } }, n, 127, false);
        rate("8", new G() { public List<Stroke> make(Gen g) { return one(g.eight()); } }, n, 128, false);
        rate("étoiles", new G() { public List<Stroke> make(Gen g) { return one(g.star()); } }, n, 129, false);
        rate("cœurs", new G() { public List<Stroke> make(Gen g) { return one(g.heart()); } }, n, 130, false);
        rate("P R b", new G() { public List<Stroke> make(Gen g) { return one(g.pLetter()); } }, n, 131, false);
        for (int kind = 0; kind <= 10; kind++) {
            final int kd = kind;
            rate("lettres en plusieurs traits #" + kind, new G() { public List<Stroke> make(Gen g) { return g.multiNegative(kd); } }, n / 2, 140 + kind, false);
        }
        System.out.printf("  faux positifs : %d sur %d%n", fp, neg);
        expect(fp * 2000 <= neg, "faux positifs <= 0,05 %");
        System.out.println(fails == 0 ? "TESTS DE RECONNAISSANCE OK" : fails + " échec(s)");
        if (fails > 0) System.exit(1);
    }
}
