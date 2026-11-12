package fr.agenda.manuscrit;

import java.util.ArrayList;

/** Le contenu d'une page (un jour de l'agenda ou une page "En vrac") + son historique (en mémoire). */
final class Page {
    final long key;
    final ArrayList<Item> items = new ArrayList<>();
    int version;

    /** Modifications annulables / rétablissables touchant cette page (gérées par PageStore). */
    final ArrayList<Edit> undo = new ArrayList<>();
    final ArrayList<Edit> redo = new ArrayList<>();

    Page(long key) {
        this.key = key;
    }

    boolean canUndo() { return !undo.isEmpty(); }

    boolean canRedo() { return !redo.isEmpty(); }

    boolean hasHistory() { return !undo.isEmpty() || !redo.isEmpty(); }

    boolean isEmpty() { return items.isEmpty(); }

    boolean contains(Item it) {
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i) == it) return true;
        return false;
    }
}
