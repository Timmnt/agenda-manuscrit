package fr.agenda.manuscrit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * Trois "écrans" de pages (précédent, courant, suivant) : 1 page en portrait, 2 côte à côte en paysage.
 * Le doigt tourne la page (glissement), zoome (pincement) et fait défiler une page allongée ;
 * le stylet, lui, n'est jamais intercepté (il écrit).
 * Animation au choix : glissement, ou page qui tourne comme du papier.
 */
final class BookPager extends ViewGroup implements InkPageView.Host {

    interface Listener {
        void onPageShown(long firstKey);

        void onPageEdited(Page p);

        void onSelectionChanged(InkPageView v);

        void onStylusButtonUndo(InkPageView v);

        void onInsertPlaced();

        void onHint(int hint);
    }

    /** Un ensemble de pages : l'agenda (un jour = une page) ou le carnet "En vrac". */
    interface Book {
        boolean canGo(long start, int per, int dir);

        long normalize(long start, int per);

        boolean showable(long key);
    }

    static final Book AGENDA = new Book() {
        @Override
        public boolean canGo(long start, int per, int dir) { return true; }

        @Override
        public long normalize(long start, int per) { return start; }

        @Override
        public boolean showable(long key) { return !Geo.isVrac(key); }
    };

    /** Le carnet "En vrac" : pages 1, 2, 3... jusqu'à la première page vierge après la dernière écrite. */
    static Book vrac(final PageStore store) {
        return new Book() {
            @Override
            public boolean canGo(long start, int per, int dir) {
                int i = Geo.vracIndex(start);
                return dir > 0 ? i + per <= store.lastVracPage() + 1 : i - per >= 1;
            }

            @Override
            public long normalize(long start, int per) {
                int i = Geo.isVrac(start) ? Math.max(1, Geo.vracIndex(start)) : 1;
                if (per == 2 && i % 2 == 0) i--; // en paysage : pages 1-2, 3-4...
                return Geo.vrac(i);
            }

            @Override
            public boolean showable(long key) {
                return Geo.isVrac(key) && Geo.vracIndex(key) >= 1;
            }
        };
    }

    private static final int IDLE = 0, TRACKING = 1, SWIPE = 2, PANZOOM = 3, IGNORED = 4;
    /** Après une activité du stylet (contact ou survol), les doigts ne tournent pas la page (paume). */
    private static final long STYLUS_GUARD_MS = 350;

    private final ToolState tools;
    private final PageStore store;
    private final Listener listener;
    /** views[slot * 2 + côté] : slot 0 = précédent, 1 = courant, 2 = suivant ; côté 0 = gauche, 1 = droite. */
    private final InkPageView[] views = new InkPageView[6];
    private final float dp, gap, slop, flingVelocity, palmSize;

    private Book book = AGENDA;
    private long start;        // première page affichée (celle de gauche en paysage)
    private int per = 1;       // pages par écran : 1 en portrait, 2 en paysage
    private boolean bound;
    private Page activePage;   // dernière page modifiée (cible d'annuler / effacer)
    private float offset;
    private ValueAnimator anim;

    // geste des doigts
    private int state = IDLE;
    private InkPageView target;
    private float totalDx, totalDy, dragBase, lastAx, lastAy, lastSpan, spanMoved;
    private int trackedFingers;
    private long lastStylus = -100000;
    private final float[] histX = new float[8];
    private final long[] histT = new long[8];
    private int histN;

    BookPager(Context c, ToolState tools, PageStore store, Listener listener) {
        super(c);
        this.tools = tools;
        this.store = store;
        this.listener = listener;
        dp = Ui.dp(c, 1);
        gap = 18 * dp;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        flingVelocity = 650 * dp;
        palmSize = 90 * dp;
        // chaque pointeur va à la page qu'il touche : la paume sur la page de droite
        // n'empêche pas le stylet d'écrire sur celle de gauche
        setMotionEventSplittingEnabled(true);
        float camera = 7000 * c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < 6; i++) {
            views[i] = new InkPageView(c, this);
            views[i].setCameraDistance(camera);
            addView(views[i]);
        }
        setBackgroundColor(Ui.DESK);
    }

    // ------------------------------------------------------------ Host

    @Override
    public ToolState tools() { return tools; }

    @Override
    public PageStore store() { return store; }

    @Override
    public void onPageEdited(InkPageView v, Page p) {
        activePage = p;
        listener.onPageEdited(p);
    }

    @Override
    public void onStylusActivity(long eventTime) {
        lastStylus = Math.max(lastStylus, eventTime);
    }

    @Override
    public boolean stylusRecent(long eventTime) {
        return eventTime - lastStylus <= 500;
    }

    @Override
    public void onSelectionChanged(InkPageView v) { listener.onSelectionChanged(v); }

    @Override
    public void onStylusButtonUndo(InkPageView v) { listener.onStylusButtonUndo(v); }

    @Override
    public void onInsertPlaced() { listener.onInsertPlaced(); }

    @Override
    public void onHint(int hint) { listener.onHint(hint); }

    // ------------------------------------------------------------ navigation

    Book book() { return book; }

    boolean isVrac() { return book != AGENDA; }

    long start() { return start; }

    /** Nombre de pages affichées à l'écran (1 ou 2). */
    int perScreen() { return per; }

    boolean isVisible(long key) {
        for (int s = 0; s < per; s++) {
            InkPageView v = v(1, s);
            if (v.page() != null && v.key() == key) return true;
        }
        return false;
    }

    private InkPageView v(int slot, int side) { return views[slot * 2 + side]; }

    /** Pages affichées (1 ou 2). */
    InkPageView currentView(int side) { return side < per ? v(1, side) : null; }

    /** Page visée par annuler / rétablir / effacer : la dernière modifiée parmi celles affichées. */
    Page currentPage() {
        if (activePage != null) {
            for (int s = 0; s < per; s++) if (v(1, s).page() == activePage) return activePage;
        }
        return v(1, 0).page();
    }

    /** La vue (affichée) qui a une sélection au lasso, ou null. */
    InkPageView selectionView() {
        for (int s = 0; s < per; s++) if (v(1, s).hasSelection()) return v(1, s);
        return null;
    }

    void clearSelections() {
        for (InkPageView v : views) v.clearSelection();
    }

    void setBook(Book b, long first) {
        book = b;
        setStart(first);
    }

    void setStart(long first) {
        if (anim != null) anim.cancel();
        anim = null;
        start = book.normalize(first, per);
        bound = true;
        activePage = null;
        bindSlot(0);
        bindSlot(1);
        bindSlot(2);
        offset = 0;
        state = IGNORED;
        updateInteractive();
        applyOffsets();
        listener.onPageShown(start);
    }

    private void bindSlot(int slot) {
        for (int side = 0; side < 2; side++) {
            InkPageView pv = v(slot, side);
            long k = start + (long) (slot - 1) * per + side;
            if (side < per && book.showable(k)) {
                pv.setAlign(per == 1 ? InkPageView.ALIGN_CENTER
                        : side == 0 ? InkPageView.ALIGN_END : InkPageView.ALIGN_START);
                pv.bind(k);
            } else {
                pv.setInteractive(false);
                pv.bind(Long.MIN_VALUE);
            }
        }
    }

    void goTo(long key) {
        if (anim != null && anim.isRunning()) anim.end();
        if (isVisible(key)) return;
        long k = book.normalize(key, per);
        if (k == start + per && book.canGo(start, per, 1)) goNext();
        else if (k == start - per && book.canGo(start, per, -1)) goPrev();
        else setStart(k);
    }

    void goNext() {
        if (anim != null && anim.isRunning()) anim.end();
        animateTo(1);
    }

    void goPrev() {
        if (anim != null && anim.isRunning()) anim.end();
        animateTo(-1);
    }

    /** Redessine les pages (après annuler, déplacement vers un autre jour, réglages...). */
    void refreshAll() {
        for (InkPageView v : views) v.refresh();
    }

    /** Applique un changement d'animation (glissement / page qui tourne). */
    void settingsChanged() {
        applyOffsets();
        refreshAll();
    }

    private void updateInteractive() {
        for (int slot = 0; slot < 3; slot++) {
            for (int side = 0; side < 2; side++) {
                InkPageView pv = v(slot, side);
                boolean current = slot == 1 && side < per && pv.page() != null;
                pv.setInteractive(current);
                if (!current && pv.isZoomed()) pv.resetZoom();
            }
        }
    }

    // ------------------------------------------------------------ mise en page

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        if (w > 0 && h > 0) {
            int wanted = w > h ? 2 : 1; // tablette à l'horizontale : deux pages
            if (wanted != per) {
                if (anim != null) anim.cancel();
                anim = null;
                offset = 0;
                per = wanted;
                activePage = null;
                if (bound) {
                    start = book.normalize(start, per);
                    bindSlot(0);
                    bindSlot(1);
                    bindSlot(2);
                    updateInteractive();
                    post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onPageShown(start);
                        }
                    });
                }
            }
        }
        int cw = MeasureSpec.makeMeasureSpec(w / per, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (InkPageView v : views) v.measure(cw, ch);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cw = (r - l) / per;
        for (InkPageView v : views) v.layout(0, 0, cw, b - t);
        applyOffsets();
    }

    private void applyOffsets() {
        int w = getWidth();
        if (w <= 0) return;
        int cw = w / per;
        for (InkPageView pv : views) {
            pv.setRotationY(0);
            pv.setTranslationZ(0);
            pv.setShade(0);
        }
        if (!Settings.pageTurn) {
            float step = w + gap;
            for (int slot = 0; slot < 3; slot++) {
                for (int side = 0; side < 2; side++) {
                    InkPageView pv = v(slot, side);
                    pv.setTranslationX((slot - 1) * step + side * cw + offset);
                    show(pv, side < per);
                }
            }
            return;
        }

        // page qui tourne : chaque page reste à sa place et pivote autour de la reliure
        float t = Math.max(-1f, Math.min(1f, -offset / w));
        boolean[] vis = new boolean[6];
        for (int slot = 0; slot < 3; slot++) for (int side = 0; side < 2; side++) v(slot, side).setTranslationX(side * cw);
        if (per == 1) {
            InkPageView prev = v(0, 0), cur = v(1, 0), next = v(2, 0);
            vis[2] = true;
            if (t > 0) {
                vis[4] = true;
                turn(cur, cur.pageLeftPx(), -90f * t);
                cur.setShade(0.25f * t);
                next.setShade(0.45f * (1 - t));
            } else if (t < 0) {
                float s = -t;
                vis[0] = true;
                turn(prev, prev.pageLeftPx(), -90f * (1 - s));
                prev.setShade(0.25f * (1 - s));
                cur.setShade(0.45f * s);
            }
        } else {
            InkPageView l0 = v(0, 0), r0 = v(0, 1), l = v(1, 0), r = v(1, 1), l2 = v(2, 0), r2 = v(2, 1);
            if (t >= 0) {
                vis[2] = true;
                if (t == 0) {
                    vis[3] = true;
                } else {
                    vis[5] = true;
                    r2.setShade(0.45f * (1 - t));
                    if (t < 0.5f) {
                        vis[3] = true;
                        turn(r, r.pageLeftPx(), -180f * t);
                        r.setShade(0.3f * (t / 0.5f));
                    } else {
                        vis[4] = true;
                        turn(l2, l2.pageRightPx(), 180f * (1 - t));
                        l2.setShade(0.3f * ((1 - t) / 0.5f));
                        l.setShade(0.4f * ((t - 0.5f) / 0.5f));
                    }
                }
            } else {
                float s = -t;
                vis[3] = true;
                vis[0] = true;
                l0.setShade(0.45f * (1 - s));
                if (s < 0.5f) {
                    vis[2] = true;
                    turn(l, l.pageRightPx(), 180f * s);
                    l.setShade(0.3f * (s / 0.5f));
                } else {
                    vis[1] = true;
                    turn(r0, r0.pageLeftPx(), -180f * (1 - s));
                    r0.setShade(0.3f * ((1 - s) / 0.5f));
                    r.setShade(0.4f * ((s - 0.5f) / 0.5f));
                }
            }
        }
        for (int i = 0; i < 6; i++) show(views[i], vis[i]);
    }

    private void turn(InkPageView pv, float pivotX, float deg) {
        pv.setPivotX(pivotX);
        pv.setPivotY(pv.getHeight() / 2f);
        pv.setRotationY(deg);
        pv.setTranslationZ(4 * dp);
    }

    private static void show(InkPageView pv, boolean on) {
        int want = on && pv.page() != null ? VISIBLE : INVISIBLE;
        if (pv.getVisibility() != want) pv.setVisibility(want);
    }

    // ------------------------------------------------------------ doigts : tourner, zoomer, défiler

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if ((a == MotionEvent.ACTION_HOVER_MOVE || a == MotionEvent.ACTION_HOVER_ENTER)
                && ev.getPointerCount() > 0) {
            int tool = ev.getToolType(0);
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                onStylusActivity(ev.getEventTime());
            }
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    private InkPageView targetAt(float x) {
        for (int s = 0; s < per; s++) {
            InkPageView pv = v(1, s);
            if (pv.page() == null) continue;
            float l = pv.getLeft() + pv.getTranslationX();
            if (x >= l && x < l + pv.getWidth()) return pv;
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (anim != null && anim.isRunning()) anim.end();
            state = IDLE;
            target = null;
            histN = 0;
        }

        // inventaire des doigts encore posés
        int skip = (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP)
                ? ev.getActionIndex() : -1;
        boolean stylus = false, palm = false;
        int fingers = 0;
        float sx = 0, sy = 0;
        for (int i = 0; i < ev.getPointerCount(); i++) {
            if (InkPageView.isStylus(ev.getToolType(i))) {
                stylus = true;
                continue;
            }
            if (i == skip) continue;
            fingers++;
            sx += ev.getX(i);
            sy += ev.getY(i);
            if (ev.getTouchMajor(i) > palmSize) palm = true;
        }
        float ax = fingers > 0 ? sx / fingers : 0, ay = fingers > 0 ? sy / fingers : 0;
        float span = 0;
        if (fingers >= 2) {
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (i == skip || InkPageView.isStylus(ev.getToolType(i))) continue;
                span += (float) Math.hypot(ev.getX(i) - ax, ev.getY(i) - ay);
            }
            span /= fingers;
        }
        long now = ev.getEventTime();
        if (stylus) {
            onStylusActivity(now);
            finishNav(false, now);
            state = IGNORED;
        }
        boolean canceled = action == MotionEvent.ACTION_CANCEL
                || (ev.getFlags() & InkPageView.FLAG_CANCELED) != 0;
        int need = tools.fingerDraw ? 2 : 1;

        if (state == IDLE) {
            boolean down = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN;
            if (!down) {
                // un geste ne peut commencer qu'au moment où un doigt se pose
            } else if (stylus || palm || canceled || now - lastStylus <= STYLUS_GUARD_MS) {
                state = IGNORED;
            } else if (fingers >= need) {
                target = targetAt(ax);
                if (fingers == 1 && target != null
                        && target.wantsFinger(ax - target.getLeft() - target.getTranslationX(), ay - target.getTop())) {
                    state = IGNORED; // la page gère ce doigt (sélection, objet à poser...)
                } else {
                    state = TRACKING;
                    trackedFingers = fingers;
                    lastAx = ax;
                    lastAy = ay;
                    lastSpan = span;
                    totalDx = totalDy = spanMoved = 0;
                    pushHist(now);
                }
            }
        } else if (state == TRACKING || state == SWIPE || state == PANZOOM) {
            if (palm || canceled) {
                finishNav(false, now);
                state = IGNORED;
            } else if (fingers < need) {
                finishNav(true, now);
                state = IGNORED;
            } else {
                float ddx = 0, ddy = 0, ratio = 1f;
                if (fingers == trackedFingers) {
                    ddx = ax - lastAx;
                    ddy = ay - lastAy;
                    if (fingers >= 2 && lastSpan > 1 && span > 1) {
                        ratio = span / lastSpan;
                        spanMoved += Math.abs(span - lastSpan);
                    }
                }
                trackedFingers = fingers;
                lastAx = ax;
                lastAy = ay;
                lastSpan = span;
                totalDx += ddx;
                totalDy += ddy;
                pushHist(now);
                if (state == TRACKING) {
                    float adx = Math.abs(totalDx), ady = Math.abs(totalDy);
                    boolean zoomed = target != null && target.isZoomed();
                    if (target != null && fingers >= 2 && spanMoved > slop * 1.5f) {
                        startPanZoom();
                    } else if (target != null && zoomed && (adx > slop || ady > slop)) {
                        startPanZoom();
                    } else if (adx > slop * 1.5f && adx > ady * 1.4f) {
                        state = SWIPE;
                        dragBase = totalDx > 0 ? slop * 1.5f : -slop * 1.5f;
                        abortFingers();
                    } else if (ady > slop * 1.5f && ady > adx) {
                        if (target != null && target.canScroll()) startPanZoom();
                        else state = IGNORED;
                    }
                }
                if (state == SWIPE) {
                    float raw = totalDx - dragBase;
                    int dir = raw < 0 ? 1 : -1;
                    if (book.canGo(start, per, dir)) offset = raw;
                    else offset = Settings.pageTurn ? 0 : raw * 0.25f; // bout du carnet : léger rebond
                    applyOffsets();
                } else if (state == PANZOOM && target != null) {
                    target.panZoom(ddx, ddy, ratio, ax - target.getLeft() - target.getTranslationX(), ay - target.getTop());
                }
            }
        }

        super.dispatchTouchEvent(ev);
        return true;
    }

    private void startPanZoom() {
        state = PANZOOM;
        abortFingers();
    }

    private void abortFingers() {
        for (int s = 0; s < per; s++) v(1, s).abortFingerInput();
    }

    private void finishNav(boolean release, long now) {
        if (state == SWIPE) {
            if (release) release(now);
            else animateTo(0);
        } else if (state == PANZOOM && target != null) {
            target.endPanZoom();
        }
    }

    private void pushHist(long t) {
        if (histN == histX.length) {
            System.arraycopy(histX, 1, histX, 0, histN - 1);
            System.arraycopy(histT, 1, histT, 0, histN - 1);
            histN--;
        }
        histX[histN] = totalDx;
        histT[histN] = t;
        histN++;
    }

    private float velocity(long now) {
        if (histN < 2) return 0;
        int last = histN - 1;
        int first = last;
        while (first > 0 && now - histT[first - 1] <= 100) first--;
        if (first == last) first = last - 1;
        long dt = histT[last] - histT[first];
        if (dt <= 0) return 0;
        return (histX[last] - histX[first]) * 1000f / dt;
    }

    private void release(long now) {
        float w = getWidth() + (Settings.pageTurn ? 0 : gap);
        float v = velocity(now);
        int dir = 0;
        if (offset < -w * 0.2f || (v < -flingVelocity && offset < 0)) dir = 1;
        else if (offset > w * 0.2f || (v > flingVelocity && offset > 0)) dir = -1;
        animateTo(dir);
    }

    private void animateTo(int wanted) {
        final int dir = wanted != 0 && !book.canGo(start, per, wanted) ? 0 : wanted;
        if (getWidth() == 0) {
            if (dir != 0) shift(dir);
            return;
        }
        if (anim != null) anim.cancel();
        final boolean turnMode = Settings.pageTurn;
        final float w = getWidth() + (turnMode ? 0 : gap);
        final float targetOffset = -dir * w;
        float dist = Math.abs(targetOffset - offset);
        long dur = turnMode
                ? (long) Math.max(200, Math.min(480, 220 + 300 * dist / w))
                : (long) Math.max(140, Math.min(320, 150 + 170 * dist / w));
        anim = ValueAnimator.ofFloat(offset, targetOffset);
        anim.setDuration(dur);
        anim.setInterpolator(turnMode ? new AccelerateDecelerateInterpolator() : new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                offset = (Float) a.getAnimatedValue();
                applyOffsets();
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
                if (dir != 0) shift(dir);
                else applyOffsets();
            }
        });
        anim.start();
    }

    private void shift(int dir) {
        if (dir > 0) {
            InkPageView a = views[0], b = views[1];
            System.arraycopy(views, 2, views, 0, 4);
            views[4] = a;
            views[5] = b;
            start += per;
            bindSlot(2);
        } else {
            InkPageView a = views[4], b = views[5];
            System.arraycopy(views, 0, views, 2, 4);
            views[0] = a;
            views[1] = b;
            start -= per;
            bindSlot(0);
        }
        activePage = null;
        offset = 0;
        updateInteractive();
        applyOffsets();
        listener.onPageShown(start);
    }
}
