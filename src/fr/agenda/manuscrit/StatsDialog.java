package fr.agenda.manuscrit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;

/** Statistiques : série de jours où tout est coché, record, et graphique des cases du mois. */
final class StatsDialog {
    private StatsDialog() {}

    static void show(Context c, PageStore store) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, L.s("Statistiques", "Statistics")));
        body.addView(new StatsView(c, store), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Kit.px(c, 430)));
        TextView close = Kit.button(c, L.s("Fermer", "Close"), true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, null, close), 560);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        d.show();
    }

    private static final class StatsView extends View {
        private final PageStore store;
        private final HashMap<Long, PageStore.Summary> sums;
        private final PageRenderer renderer = new PageRenderer();
        private final Obj flame = PageRenderer.makeSticker(7);
        private final float dp, sp;
        private final int current, best;
        private final boolean anyBox;
        private final long today;
        private int year, month;

        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        private float navY;

        StatsView(Context c, PageStore store) {
            super(c);
            this.store = store;
            this.sums = store.summaries();
            dp = Ui.dp(c, 1);
            sp = Ui.sp(c, 1);
            today = Days.today();
            int[] s = Streaks.compute(sums, today);
            current = s[0];
            best = s[1];
            boolean any = false;
            for (PageStore.Summary x : sums.values()) if (x != null && x.boxes > 0) any = true;
            anyBox = any;
            int[] ymd = Days.toYmd(today);
            year = ymd[0];
            month = ymd[1];
        }

        private static String jours(int n) {
            return L.n(n, "jour", "jours", "day", "days");
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = 22 * dp, w = getWidth();

            // série en cours
            float fs = 58 * dp;
            int save = c.save();
            c.translate(pad + fs / 2, 14 * dp + fs / 2);
            c.scale(fs / 100f, fs / 100f);
            renderer.drawObj(c, flame);
            c.restoreToCount(save);
            float tx = pad + fs + 14 * dp;
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(bold);
            text.setTextSize(30 * sp);
            text.setColor(Ui.INK);
            c.drawText(jours(current), tx, 44 * dp, text);
            text.setTypeface(regular);
            text.setTextSize(13 * sp);
            text.setColor(Ui.GREY);
            c.drawText(L.s("d'affilée avec toutes les cases cochées", "in a row with every box checked"), tx, 66 * dp, text);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(medium);
            text.setTextSize(13 * sp);
            c.drawText(L.s("Record", "Best"), w - pad, 30 * dp, text);
            text.setTypeface(bold);
            text.setTextSize(20 * sp);
            text.setColor(Ui.ACCENT);
            c.drawText(jours(best), w - pad, 56 * dp, text);

            // navigation dans les mois
            navY = 112 * dp;
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(medium);
            text.setTextSize(16 * sp);
            text.setColor(Ui.INK);
            c.drawText(Days.monthTitle(year, month), w / 2, navY + 6 * dp, text);
            fill.setColor(Ui.INK);
            fill.setStrokeWidth(2.2f * dp);
            fill.setStrokeCap(Paint.Cap.ROUND);
            float ax = pad + 14 * dp, bx = w - pad - 14 * dp, h6 = 6 * dp;
            c.drawLine(ax + h6 / 2, navY - h6, ax - h6 / 2, navY, fill);
            c.drawLine(ax - h6 / 2, navY, ax + h6 / 2, navY + h6, fill);
            c.drawLine(bx - h6 / 2, navY - h6, bx + h6 / 2, navY, fill);
            c.drawLine(bx + h6 / 2, navY, bx - h6 / 2, navY + h6, fill);

            // chiffres du mois
            long first = Days.fromYmd(year, month, 1);
            int dim = Days.daysInMonth(year, month);
            int boxes = 0, checked = 0, written = 0, maxBoxes = 4;
            for (int d = 0; d < dim; d++) {
                PageStore.Summary s = sums.get(first + d);
                if (store.hasContent(first + d)) written++;
                if (s == null) continue;
                boxes += s.boxes;
                checked += s.checked;
                maxBoxes = Math.max(maxBoxes, s.boxes);
            }
            text.setTypeface(regular);
            text.setTextSize(14 * sp);
            text.setColor(Ui.GREY);
            c.drawText(L.f("%d %s sur %d  ·  %s où tu as écrit", "%d of %d %s  ·  %s with writing",
                    L.en ? new Object[]{checked, boxes, boxes == 1 ? "box checked" : "boxes checked", jours(written)}
                            : new Object[]{checked, checked > 1 ? "cases cochées" : "case cochée", boxes, jours(written)}),
                    w / 2, navY + 38 * dp, text);

            // graphique : une barre par jour (cochées en bleu, restantes en gris)
            float top = navY + 64 * dp, bottom = getHeight() - 52 * dp, left = pad, right = w - pad;
            float colW = (right - left) / dim, barW = Math.max(2 * dp, colW * 0.62f);
            fill.setColor(Ui.BAR_LINE);
            c.drawRect(left, bottom, right, bottom + Math.max(1, dp), fill);
            for (int d = 0; d < dim; d++) {
                long day = first + d;
                PageStore.Summary s = sums.get(day);
                float cx = left + colW * (d + 0.5f);
                if (s != null && s.boxes > 0) {
                    float hTot = (bottom - top) * s.boxes / maxBoxes;
                    float hChk = (bottom - top) * s.checked / maxBoxes;
                    r.set(cx - barW / 2, bottom - hTot, cx + barW / 2, bottom);
                    fill.setColor(0xFF3A404B);
                    c.drawRoundRect(r, barW / 3, barW / 3, fill);
                    if (hChk > 0) {
                        r.set(cx - barW / 2, bottom - hChk, cx + barW / 2, bottom);
                        fill.setColor(s.checked >= s.boxes ? 0xFF3FB27F : Ui.ACCENT_FILL);
                        c.drawRoundRect(r, barW / 3, barW / 3, fill);
                    }
                }
                int dn = d + 1;
                if (dn == 1 || dn % 5 == 0 || day == today) {
                    text.setTextSize(11 * sp);
                    text.setTypeface(day == today ? bold : regular);
                    text.setColor(day == today ? Ui.ACCENT : Ui.GREY);
                    c.drawText(String.valueOf(dn), cx, bottom + 18 * dp, text);
                }
            }
            if (boxes == 0) {
                text.setTypeface(regular);
                text.setTextSize(14 * sp);
                text.setColor(Ui.LIGHT_GREY);
                c.drawText(L.s("Aucune case à cocher ce mois-ci", "No check boxes this month"), w / 2, (top + bottom) / 2, text);
            }
            text.setTextSize(12.5f * sp);
            text.setColor(Ui.GREY);
            text.setTypeface(regular);
            String legend = anyBox ? L.s("Vert : tout coché ce jour-là · bleu : coché · gris : reste à faire", "Green: all checked that day · blue: checked · grey: still to do")
                    : L.s("Astuce : dessine un petit carré à côté d'un devoir, il devient une case à cocher.", "Tip: draw a small square next to a task and it becomes a check box.");
            c.drawText(legend, w / 2, getHeight() - 14 * dp, text);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(ev.getY() - navY) < 26 * dp) {
                if (ev.getX() < getWidth() * 0.3f) shift(-1);
                else if (ev.getX() > getWidth() * 0.7f) shift(1);
            }
            return true;
        }

        private void shift(int d) {
            month += d;
            if (month < 1) {
                month = 12;
                year--;
            } else if (month > 12) {
                month = 1;
                year++;
            }
            playSoundEffect(android.view.SoundEffectConstants.CLICK);
            invalidate();
        }
    }
}
