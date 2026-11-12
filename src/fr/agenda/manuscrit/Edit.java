package fr.agenda.manuscrit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Une modification annulable, éventuellement sur plusieurs pages (ex. : reporter une tâche à demain).
 * Elle est une suite d'étapes "retirer" / "insérer" appliquées immédiatement ; annuler les rejoue
 * à l'envers. Les éléments étant immuables, on les retrouve par identité.
 */
final class Edit {
    static final class Step {
        final Page page;
        final boolean insert;
        final Item[] items;
        final int[] idx; // positions (croissantes) dans la liste

        Step(Page page, boolean insert, Item[] items, int[] idx) {
            this.page = page;
            this.insert = insert;
            this.items = items;
            this.idx = idx;
        }
    }

    final ArrayList<Step> steps = new ArrayList<>();

    boolean isEmpty() {
        return steps.isEmpty();
    }

    Set<Page> pages() {
        LinkedHashSet<Page> out = new LinkedHashSet<>();
        for (Step s : steps) out.add(s.page);
        return out;
    }

    // ------------------------------------------------------------------ construction

    /** Retire ces éléments (par identité) ; ceux absents de la page sont ignorés. */
    void remove(Page p, Collection<? extends Item> items) {
        if (items.isEmpty()) return;
        IdentityHashMap<Item, Boolean> set = new IdentityHashMap<>();
        for (Item it : items) set.put(it, Boolean.TRUE);
        ArrayList<Item> list = p.items;
        int[] idx = new int[items.size()];
        int n = 0;
        for (int i = 0; i < list.size() && n < idx.length; i++) {
            if (set.containsKey(list.get(i))) idx[n++] = i;
        }
        if (n == 0) return;
        Item[] its = new Item[n];
        for (int k = 0; k < n; k++) its[k] = list.get(idx[k]);
        for (int k = n - 1; k >= 0; k--) list.remove(idx[k]);
        steps.add(new Step(p, false, its, trimmed(idx, n)));
    }

    /** Retire l'élément d'index i. */
    void removeAt(Page p, int i) {
        Item it = p.items.remove(i);
        steps.add(new Step(p, false, new Item[]{it}, new int[]{i}));
    }

    /** Insère les éléments à la suite, à partir de l'index at. */
    void insert(Page p, int at, List<? extends Item> items) {
        if (items.isEmpty()) return;
        ArrayList<Item> list = p.items;
        if (at < 0) at = 0;
        if (at > list.size()) at = list.size();
        int[] idx = new int[items.size()];
        for (int k = 0; k < idx.length; k++) idx[k] = at + k;
        list.addAll(at, items);
        steps.add(new Step(p, true, items.toArray(new Item[0]), idx));
    }

    void add(Page p, Item it) {
        ArrayList<Item> one = new ArrayList<>(1);
        one.add(it);
        insert(p, p.items.size(), one);
    }

    /** Remplace chaque olds[k] par news[k], à la même place (même ordre d'affichage). */
    void replace(Page p, List<? extends Item> olds, List<? extends Item> news) {
        IdentityHashMap<Item, Item> map = new IdentityHashMap<>();
        for (int k = 0; k < olds.size(); k++) map.put(olds.get(k), news.get(k));
        ArrayList<Item> list = p.items;
        int[] idx = new int[olds.size()];
        int n = 0;
        for (int i = 0; i < list.size() && n < idx.length; i++) {
            if (map.containsKey(list.get(i))) idx[n++] = i;
        }
        if (n == 0) return;
        idx = trimmed(idx, n);
        Item[] o = new Item[n], nw = new Item[n];
        for (int k = 0; k < n; k++) {
            o[k] = list.get(idx[k]);
            nw[k] = map.get(o[k]);
        }
        for (int k = n - 1; k >= 0; k--) list.remove(idx[k]);
        steps.add(new Step(p, false, o, idx));
        for (int k = 0; k < n; k++) list.add(idx[k], nw[k]);
        steps.add(new Step(p, true, nw, idx.clone()));
    }

    void replace(Page p, Item old, Item nw) {
        ArrayList<Item> a = new ArrayList<>(1), b = new ArrayList<>(1);
        a.add(old);
        b.add(nw);
        replace(p, a, b);
    }

    private static int[] trimmed(int[] a, int n) {
        if (a.length == n) return a;
        int[] r = new int[n];
        System.arraycopy(a, 0, r, 0, n);
        return r;
    }

    // ------------------------------------------------------------------ rejouer

    void undo() {
        for (int s = steps.size() - 1; s >= 0; s--) {
            Step st = steps.get(s);
            if (st.insert) removeIdentity(st.page.items, st.items);
            else insertAt(st.page.items, st.items, st.idx);
        }
    }

    void redo() {
        for (Step st : steps) {
            if (st.insert) insertAt(st.page.items, st.items, st.idx);
            else removeIdentity(st.page.items, st.items);
        }
    }

    private static void insertAt(ArrayList<Item> list, Item[] items, int[] idx) {
        for (int k = 0; k < items.length; k++) {
            int i = Math.min(idx[k], list.size());
            list.add(i, items[k]);
        }
    }

    private static void removeIdentity(ArrayList<Item> list, Item[] items) {
        for (Item it : items) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i) == it) {
                    list.remove(i);
                    break;
                }
            }
        }
    }
}
