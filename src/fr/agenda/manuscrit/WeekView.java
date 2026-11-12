package fr.agenda.manuscrit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Vue d'ensemble d'une semaine : 7 miniatures de pages + un mini-calendrier du mois. */
final class WeekView extends View implements PageStore.Listener {

    interface Listener {
        void onOpenDay(long day);

        void onWeekChanged(long weekStart);
    }

    private static final class Thumb {
        final Bitmap bmp;
        final int version, w, h;
        final boolean today;
        final int revision;

        Thumb(Bitmap bmp, int version, int w, int h, boolean today, int revision) {
            this.bmp = bmp;
            this.version = version;
            this.w = w;
            this.h = h;
            this.today = today;
            this.revision = revision;
        }
    }

    private final PageStore store;
    private final Listener listener;
    private final float dp, slop, gap, labelH, pad, flingVelocity;

    private long weekStart;
    private float offset;
    private ValueAnimator anim;

    // grille
    private int cols = 3, rows = 3;
    private float cellW, cellH, originX, originY;
    private int thumbW = 1, thumbH = 1;

    // miniatures (rendues sur un thread à part)
    private final LruCache<Long, Thumb> thumbs;
    private final HashSet<String> pending = new HashSet<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PageRenderer bgRenderer = new PageRenderer();
    private long prefetched = Long.MIN_VALUE;

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paper = new Paint();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF monthRect = new RectF();
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);

    // toucher
    private float downX, downY;
    private boolean dragging, tracking;
    private float dragBase;
    private final float[] histX = new float[8];
    private final long[] histT = new long[8];
    private int histN;

    WeekView(Context c, PageStore store, Listener listener) {
        super(c);
        this.store = store;
        this.listener = listener;
        dp = Ui.dp(c, 1);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        gap = 12 * dp;
        labelH = 34 * dp;
        pad = 8 * dp;
        flingVelocity = 650 * dp;
        int maxBytes = (int) Math.min(Runtime.getRuntime().maxMemory() / 5, 96L * 1024 * 1024);
        thumbs = new LruCache<Long, Thumb>(maxBytes) {
            @Override
            protected int sizeOf(Long key, Thumb t) {
                return t.bmp.getByteCount();
            }
        };
        border.setStyle(Paint.Style.STROKE);
        paper.setColor(Ui.PAPER);
        store.addListener(this);
        setBackgroundColor(Ui.DESK);
    }

    long weekStart() { return weekStart; }

    /** Libère le thread de rendu et se désabonne (fin de l'activité). */
    void dispose() {
        store.removeListener(this);
        exec.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    void setWeek(long ws) {
        if (anim != null) anim.cancel();
        weekStart = Days.weekStart(ws);
        offset = 0;
        invalidate();
    }

    void goNext() {
        if (anim != null && anim.isRunning()) anim.end();
        animateTo(1);
    }

    void goPrev() {
        if (anim != null && anim.isRunning()) anim.end();
        animateTo(-1);
    }

    @Override
    public void onPageChanged(long day) {
        if (getVisibility() == VISIBLE) invalidate();
    }

    // ---------------------------------------------------------------- grille

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int[][] options = {{2, 4}, {3, 3}, {4, 2}, {7, 1}, {1, 7}};
        float best = -1;
        for (int[] o : options) {
            float cw = (w - (o[0] + 1) * gap) / o[0];
            float ch = (h - (o[1] + 1) * gap) / o[1];
            float s = Math.min((cw - 2 * pad) / PageRenderer.W, (ch - labelH - pad) / PageRenderer.H);
            if (s > best + 0.002f) {
                best = s;
                cols = o[0];
                rows = o[1];
            }
        }
        cellW = (w - (cols + 1) * gap) / cols;
        cellH = (h - (rows + 1) * gap) / rows;
        float s = Math.max(0.01f, best);
        thumbW = Math.max(1, (int) (PageRenderer.W * s));
        thumbH = Math.max(1, (int) (PageRenderer.H * s));
        originX = gap;
        originY = gap;
        thumbs.evictAll();
        prefetched = Long.MIN_VALUE;
    }

    private void cellRect(int slot, float ox, RectF out) {
        int col = slot % cols, row = slot / cols;
        float x = ox + originX + col * (cellW + gap);
        float y = originY + row * (cellH + gap);
        out.set(x, y, x + cellW, y + cellH);
    }

    /** Zone du mini-calendrier (cases restantes de la dernière ligne), ou vide. */
    private boolean monthArea(float ox, RectF out) {
        int slots = cols * rows;
        if (slots <= 7) return false;
        RectF a = new RectF(), b = new RectF();
        cellRect(7, ox, a);
        cellRect(slots - 1, ox, b);
        out.set(a.left, a.top, b.right, b.bottom);
        return true;
    }

    // ---------------------------------------------------------------- dessin

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth() + gap;
        long today = Days.today();
        for (int k = -1; k <= 1; k++) {
            float ox = k * w + offset;
            if (ox <= -getWidth() || ox >= getWidth()) continue;
            drawWeek(c, weekStart + 7L * k, ox, today);
        }
        if (offset == 0 && prefetched != weekStart) {
            prefetched = weekStart;
            final long ws = weekStart;
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (ws != weekStart) return;
                    long t = Days.today();
                    for (int i = 0; i < 7; i++) {
                        thumbFor(ws - 7 + i, t);
                        thumbFor(ws + 7 + i, t);
                    }
                }
            }, 250);
        }
    }

    private void drawWeek(Canvas c, long ws, float ox, long today) {
        for (int i = 0; i < 7; i++) {
            long d = ws + i;
            boolean isToday = d == today;
            cellRect(i, ox, r);
            card.setColor(Ui.CARD);
            c.drawRoundRect(r, 12 * dp, 12 * dp, card);
            border.setColor(isToday ? Ui.ACCENT_FILL : Ui.BAR_LINE);
            border.setStrokeWidth(isToday ? 2.5f * dp : 1f * dp);
            c.drawRoundRect(r, 12 * dp, 12 * dp, border);

            int dow = Days.dayOfWeek(d);
            int[] ymd = Days.toYmd(d);
            label.setTextAlign(Paint.Align.LEFT);
            label.setTypeface(isToday ? bold : medium);
            label.setTextSize(15 * dp);
            label.setColor(isToday ? Ui.ACCENT : (dow >= 5 ? Ui.WEEKEND : Ui.INK));
            float base = r.top + labelH * 0.66f;
            String name = cellW > 150 * dp ? Days.DAY_NAMES[dow] : Days.DAY_SHORT[dow];
            c.drawText(name + " " + Days.dayNum(ymd[2]), r.left + 12 * dp, base, label);
            if (isToday && cellW > 190 * dp) {
                label.setTextAlign(Paint.Align.RIGHT);
                label.setTypeface(medium);
                label.setTextSize(12 * dp);
                c.drawText("aujourd'hui", r.right - 12 * dp, base, label);
            }

            float tx = (float) Math.floor(r.left + (cellW - thumbW) / 2f);
            float ty = (float) Math.floor(r.top + labelH + (cellH - labelH - pad - thumbH) / 2f);
            Thumb t = thumbFor(d, today);
            if (t != null && t.w == thumbW && t.h == thumbH) {
                c.drawBitmap(t.bmp, tx, ty, bmpPaint);
            } else {
                c.drawRect(tx, ty, tx + thumbW, ty + thumbH, paper);
            }
        }
        if (monthArea(ox, monthRect)) drawMonth(c, ws, monthRect, today);
    }

    private Thumb thumbFor(final long day, final long today) {
        Page p = store.page(day);
        Thumb t = thumbs.get(day);
        boolean isToday = day == today;
        if (t == null || t.version != p.version || t.w != thumbW || t.h != thumbH || t.today != isToday
                || t.revision != Settings.revision) {
            request(day, p, today);
        }
        return t;
    }

    private void request(final long day, Page p, final long today) {
        final int w = thumbW, h = thumbH, ver = p.version, rev = Settings.revision;
        final boolean isToday = day == today;
        final String key = day + ":" + ver + ":" + w + "x" + h + ":" + isToday + ":" + rev;
        if (pending.contains(key) || exec.isShutdown()) return;
        pending.add(key);
        final ArrayList<Item> snapshot = new ArrayList<>(p.items);
        final int bg = Settings.bgAgenda;
        final boolean schedule = Settings.bandVisible();
        exec.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b;
                try {
                    b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            pending.remove(key);
                            thumbs.evictAll();
                        }
                    });
                    return;
                }
                Canvas c = new Canvas(b);
                float sx = w / PageRenderer.W, sy = h / PageRenderer.H;
                c.scale(sx, sy);
                bgRenderer.drawPaper(c, day, today, Geo.H, bg, schedule);
                bgRenderer.drawItems(c, snapshot, true, sx);
                final Bitmap done = b;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        pending.remove(key);
                        if (w != thumbW || h != thumbH) return;
                        Thumb old = thumbs.get(day);
                        if (old != null && old.version > ver && old.w == w && old.revision == rev) return;
                        thumbs.put(day, new Thumb(done, ver, w, h, isToday, rev));
                        invalidate();
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- mini-mois

    private void drawMonth(Canvas c, long ws, RectF area, long today) {
        card.setColor(Ui.CARD);
        c.drawRoundRect(area, 12 * dp, 12 * dp, card);
        border.setColor(Ui.BAR_LINE);
        border.setStrokeWidth(1f * dp);
        c.drawRoundRect(area, 12 * dp, 12 * dp, border);

        int[] ref = Days.toYmd(ws + 3);
        int y = ref[0], m = ref[1];
        label.setTextAlign(Paint.Align.LEFT);
        label.setTypeface(bold);
        label.setTextSize(15 * dp);
        label.setColor(Ui.INK);
        c.drawText(Days.monthTitle(y, m), area.left + 12 * dp, area.top + labelH * 0.66f, label);

        float gx = area.left + 8 * dp, gy = area.top + labelH;
        float gw = area.width() - 16 * dp, gh = area.height() - labelH - 8 * dp;
        float cw = gw / 7f, rh = gh / 7f;
        float ts = Math.min(Math.min(cw, rh) * 0.42f, 15 * dp);
        long first = Days.fromYmd(y, m, 1);
        int firstDow = Days.dayOfWeek(first);
        int dim = Days.daysInMonth(y, m);

        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(medium);
        label.setTextSize(ts * 0.9f);
        label.setColor(Ui.GREY);
        for (int i = 0; i < 7; i++) {
            c.drawText(Days.DAY_LETTER[i], gx + cw * (i + 0.5f), gy + rh * 0.62f, label);
        }

        int weekRow = (int) ((ws - Days.weekStart(first)) / 7);
        if (weekRow >= 0 && weekRow < 6) {
            r.set(gx + 2 * dp, gy + rh * (weekRow + 1) + 1 * dp, gx + gw - 2 * dp, gy + rh * (weekRow + 2) - 1 * dp);
            dot.setColor(Ui.ACCENT_SOFT);
            c.drawRoundRect(r, rh / 2, rh / 2, dot);
        }

        label.setTextSize(ts);
        for (int d = 1; d <= dim; d++) {
            int idx = firstDow + d - 1;
            int row = idx / 7 + 1, col = idx % 7;
            float cx = gx + cw * (col + 0.5f), cy = gy + rh * (row + 0.5f);
            long day = first + d - 1;
            boolean isToday = day == today;
            if (isToday) {
                dot.setColor(Ui.ACCENT_FILL);
                c.drawCircle(cx, cy, Math.min(cw, rh) * 0.40f, dot);
            }
            label.setTypeface(isToday ? bold : regular);
            label.setColor(isToday ? 0xFFFFFFFF : (col >= 5 ? Ui.WEEKEND : Ui.INK));
            c.drawText(String.valueOf(d), cx, cy + ts * 0.36f, label);
            Ui.drawDayDots(c, dot, cx, cy + Math.min(cw, rh) * 0.30f, 2.2f * dp, store.summary(day),
                    store.hasContent(day), isToday);
        }
    }

    private long monthDayAt(float x, float yy) {
        if (!monthArea(0, monthRect) || !monthRect.contains(x, yy)) return Long.MIN_VALUE;
        int[] ref = Days.toYmd(weekStart + 3);
        float gx = monthRect.left + 8 * dp, gy = monthRect.top + labelH;
        float gw = monthRect.width() - 16 * dp, gh = monthRect.height() - labelH - 8 * dp;
        float cw = gw / 7f, rh = gh / 7f;
        int col = (int) Math.floor((x - gx) / cw);
        int row = (int) Math.floor((yy - gy) / rh) - 1;
        if (col < 0 || col > 6 || row < 0 || row > 5) return Long.MIN_VALUE;
        long first = Days.fromYmd(ref[0], ref[1], 1);
        int d = row * 7 + col - Days.dayOfWeek(first) + 1;
        if (d < 1 || d > Days.daysInMonth(ref[0], ref[1])) return Long.MIN_VALUE;
        return first + d - 1;
    }

    // ---------------------------------------------------------------- toucher

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null && anim.isRunning()) anim.end();
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                tracking = true;
                histN = 0;
                push(0, ev.getEventTime());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!dragging) tracking = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) return true;
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    dragBase = dx > 0 ? slop : -slop;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    offset = dx - dragBase;
                    push(dx, ev.getEventTime());
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!tracking) return true;
                tracking = false;
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if (dragging) {
                    dragging = false;
                    push(dx, ev.getEventTime());
                    float w = getWidth() + gap;
                    float v = velocity(ev.getEventTime());
                    int dir = 0;
                    if (offset < -w * 0.2f || (v < -flingVelocity && offset < 0)) dir = 1;
                    else if (offset > w * 0.2f || (v > flingVelocity && offset > 0)) dir = -1;
                    animateTo(dir);
                } else if (Math.abs(dx) < slop * 1.5f && Math.abs(dy) < slop * 1.5f) {
                    tap(ev.getX(), ev.getY());
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                if (dragging) {
                    dragging = false;
                    animateTo(0);
                }
                return true;
        }
        return true;
    }

    private void push(float x, long t) {
        if (histN == histX.length) {
            System.arraycopy(histX, 1, histX, 0, histN - 1);
            System.arraycopy(histT, 1, histT, 0, histN - 1);
            histN--;
        }
        histX[histN] = x;
        histT[histN] = t;
        histN++;
    }

    private float velocity(long now) {
        if (histN < 2) return 0;
        int last = histN - 1, first = last;
        while (first > 0 && now - histT[first - 1] <= 100) first--;
        if (first == last) first = last - 1;
        long dt = histT[last] - histT[first];
        return dt <= 0 ? 0 : (histX[last] - histX[first]) * 1000f / dt;
    }

    private void tap(float x, float y) {
        for (int i = 0; i < 7; i++) {
            cellRect(i, 0, r);
            if (r.contains(x, y)) {
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                listener.onOpenDay(weekStart + i);
                return;
            }
        }
        long d = monthDayAt(x, y);
        if (d != Long.MIN_VALUE) {
            playSoundEffect(android.view.SoundEffectConstants.CLICK);
            listener.onOpenDay(d);
        }
    }

    private void animateTo(final int dir) {
        if (anim != null) anim.cancel();
        float w = getWidth() + gap;
        if (getWidth() == 0) {
            weekStart += 7L * dir;
            offset = 0;
            listener.onWeekChanged(weekStart);
            return;
        }
        float target = -dir * w;
        float dist = Math.abs(target - offset);
        anim = ValueAnimator.ofFloat(offset, target);
        anim.setDuration((long) Math.max(140, Math.min(320, 150 + 170 * dist / w)));
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                offset = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator a) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (canceled) return;
                offset = 0;
                if (dir != 0) {
                    weekStart += 7L * dir;
                    listener.onWeekChanged(weekStart);
                }
                invalidate();
            }
        });
        anim.start();
    }
}
