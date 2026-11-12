package fr.agenda.manuscrit;

/**
 * Élément posé sur une page : trait d'encre ou objet (case à cocher, tampon, matière, autocollant).
 * Un élément posé n'est plus jamais modifié : une modification crée un nouvel élément
 * (c'est ce qui rend annuler / rétablir et la sauvegarde en arrière-plan sûrs).
 */
abstract class Item {
    abstract float left();

    abstract float top();

    abstract float right();

    abstract float bottom();

    /** Le cercle (x, y, r) touche-t-il l'élément ? */
    abstract boolean hit(float x, float y, float r);

    /** Copie transformée : p' = a + (p - a) * k + d. */
    abstract Item transformed(float ax, float ay, float k, float dx, float dy);

    /** Copie d'une autre couleur (ou this si la couleur ne s'applique pas). */
    abstract Item recolored(int color);

    /** Part de l'élément (0..1) à l'intérieur du polygone (x0, y0, x1, y1, ...) de n points. */
    abstract float insideFraction(float[] poly, int n);
}
