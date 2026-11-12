package fr.agenda.manuscrit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/** Séries de jours où toutes les cases sont cochées (les jours sans case ne comptent pas et ne cassent rien). */
final class Streaks {
    private Streaks() {}

    /** @return {série en cours, record}. Aujourd'hui, s'il n'est pas fini, ne casse pas la série. */
    static int[] compute(Map<Long, PageStore.Summary> sums, long today) {
        ArrayList<Long> days = new ArrayList<>();
        for (Map.Entry<Long, PageStore.Summary> e : sums.entrySet()) {
            if (e.getValue() != null && e.getValue().boxes > 0 && e.getKey() <= today) days.add(e.getKey());
        }
        Collections.sort(days);
        int best = 0, run = 0;
        for (long d : days) {
            PageStore.Summary s = sums.get(d);
            if (s.checked >= s.boxes) {
                run++;
                best = Math.max(best, run);
            } else if (d != today) {
                run = 0;
            }
        }
        int current = 0;
        for (int i = days.size() - 1; i >= 0; i--) {
            long d = days.get(i);
            PageStore.Summary s = sums.get(d);
            if (s.checked >= s.boxes) current++;
            else if (d == today) continue;
            else break;
        }
        return new int[]{current, best};
    }
}
