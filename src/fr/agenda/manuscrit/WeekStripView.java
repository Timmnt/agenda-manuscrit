package fr.agenda.manuscrit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

/** Petite bande "L M M J V S D" dans la barre du haut : jour affiché, aujourd'hui, jours remplis. */
final class WeekStripView extends View {
    interface Listener {
        void onPick(long day);
    }

    private final PageStore store;
    private final Listener listener;
    private final float dp;
    private long day;
    private int span = 1;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private int pressed = -1;

    WeekStripView(Context c, PageStore store, Listener listener) {
        super(c);
        this.store = store;
        this.listener = listener;
        this.dp = Ui.dp(c, 1);
        text.setTextAlign(Paint.Align.CENTER);
        setContentDescription("Jours de la semaine");
    }

    /** @param span nombre de jours affichés (1 en portrait, 2 en paysage). */
    void setDay(long d, int span) {
        day = d;
        this.span = span;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize((int) (7 * 40 * dp), wSpec), resolveSize((int) (48 * dp), hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        long ws = Days.weekStart(day), today = Days.today();
        float cw = getWidth() / 7f, h = getHeight();
        for (int i = 0; i < 7; i++) {
            long d = ws + i;
            float cx = cw * (i + 0.5f);
            boolean sel = d >= day && d < day + span, isToday = d == today;
            float rad = Math.min(cw, h) * 0.40f;
            float cy = h * 0.5f;
            if (sel) {
                fill.setColor(Ui.ACCENT_FILL);
                c.drawCircle(cx, cy, rad, fill);
                if (i < 6 && d + 1 < day + span) c.drawRect(cx, cy - rad, cx + cw, cy + rad, fill); // jours groupés
            } else if (i == pressed) {
                fill.setColor(Ui.ACCENT_SOFT);
                c.drawCircle(cx, cy, rad, fill);
            }
            text.setTextSize(10 * dp);
            text.setTypeface(regular);
            text.setColor(sel ? 0xDDFFFFFF : Ui.GREY);
            c.drawText(Days.DAY_LETTER[i], cx, cy - rad * 0.28f, text);
            text.setTextSize(14 * dp);
            text.setTypeface(sel || isToday ? bold : regular);
            text.setColor(sel ? 0xFFFFFFFF : (isToday ? Ui.ACCENT : Ui.INK));
            c.drawText(String.valueOf(Days.toYmd(d)[2]), cx, cy + rad * 0.52f, text);
            Ui.drawDayDots(c, fill, cx, cy + rad * 0.80f, 1.8f * dp, store.summary(d), store.hasContent(d), sel);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int i = (int) (ev.getX() / (getWidth() / 7f));
        if (i < 0) i = 0;
        if (i > 6) i = 6;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = i;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (pressed == i) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    listener.onPick(Days.weekStart(day) + i);
                }
                pressed = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
        }
        return true;
    }
}
