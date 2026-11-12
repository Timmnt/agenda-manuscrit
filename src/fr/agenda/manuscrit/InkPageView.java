package fr.agenda.manuscrit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * Une page (un jour de l'agenda ou une page "En vrac").
 * Le stylet écrit / gomme / entoure au lasso ; le doigt touche (cocher une case, poser un objet,
 * déplacer une sélection) et laisse au pager le glissement, le zoom et le défilement.
 * L'affichage passe par une image de la zone visible, redessinée nette après chaque zoom.
 */
final class InkPageView extends View {

    interface Host {
        ToolState tools();

        PageStore store();

        void onPageEdited(InkPageView v, Page p);

        void onStylusActivity(long eventTime);

        /** Le stylet vient-il de toucher / survoler l'écran ? (un doigt est alors sans doute la paume) */
        boolean stylusRecent(long eventTime);

        void onSelectionChanged(InkPageView v);

        void onStylusButtonUndo(InkPageView v);

        void onInsertPlaced();

        void onHint(int hint);
    }

    static final int HINT_BOX = 1, HINT_EXTENDED = 2;

    /** MotionEvent.FLAG_CANCELED (API 33) : la paume a été rejetée par le système. */
    static final int FLAG_CANCELED = 0x20;

    /** Position de la feuille dans la vue : centrée, collée à droite (page de gauche) ou à gauche. */
    static final int ALIGN_CENTER = 0, ALIGN_END = 1, ALIGN_START = 2;

    private static final float MAX_ZOOM = 4f;
    private static final int M_NONE = 0, M_INK = 1, M_ERASE = 2, M_LASSO = 3, M_MOVE = 4, M_SCALE = 5,
            M_PLACE = 6, M_TAP = 7, M_PILL = 8;
    private static final int BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY | MotionEvent.BUTTON_SECONDARY;
    private static final int ACTION_BUTTON_PRESS = 11, ACTION_BUTTON_RELEASE = 12; // API 23

    static boolean isStylus(int toolType) {
        return toolType == MotionEvent.TOOL_TYPE_STYLUS
                || toolType == MotionEvent.TOOL_TYPE_ERASER
                || toolType == MotionEvent.TOOL_TYPE_MOUSE;
    }

    private final Host host;
    private final PageRenderer renderer = new PageRenderer();
    private final float dp, touchSlop;

    private Page page;
    private long key = Long.MIN_VALUE;
    private float pageH = Geo.H;
    private int align = ALIGN_CENTER;
    private boolean interactive;
    private float shade;

    // fenêtre d'affichage : coin (0, 0) de la page en (ox, oy) pixels, `scale` pixels par unité
    private float fit = 1f, scale = 1f, ox, oy, padL, padR, padY;
    private int vw, vh;
    private boolean layoutDirty = true;

    // image de la zone visible
    private Bitmap cache;
    private Canvas cacheCanvas;
    private boolean cacheValid;
    private float cScale, cOx, cOy;
    private long cToday;
    private int cRevision;

    // saisie en cours
    private int activeId = -1, activeTool, mode = M_NONE;
    private Stroke current;
    private float smoothP = -1f;
    private float lastX, lastY, lastP, downX, downY, lastEraseX, lastEraseY, placeX, placeY;
    private long downT;
    private boolean fingerBlocked, stylusBtnDown;
    private Edit eraseEdit;
    private Obj tapBox;
    /** Derniers traits d'encre, dessinés à la suite : un carré peut être fait en plusieurs traits. */
    private final ArrayList<Stroke> recentInk = new ArrayList<>();
    private long recentKey = Long.MIN_VALUE, lastInkUp;

    // lasso et sélection
    private float[] lasso = new float[512];
    private int lassoN;
    private ArrayList<Item> sel;
    private IdentityHashMap<Item, Boolean> selSet;
    private float selL, selT, selR, selB;
    private float tdx, tdy, tk = 1f;

    // curseur de gomme
    private boolean showCursor;
    private float cursorX, cursorY;

    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lassoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lassoFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint();
    private final Path lassoPath = new Path();
    private final RectF tmp = new RectF();
    private final RectF tmp2 = new RectF();

    private final Runnable crisp = new Runnable() {
        @Override
        public void run() {
            if (!viewportMatches()) {
                cacheValid = false;
                invalidate();
            }
        }
    };

    InkPageView(Context c, Host host) {
        super(c);
        this.host = host;
        this.dp = Ui.dp(c, 1);
        this.touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(1.5f * dp);
        cursorPaint.setColor(0xAA23252C);
        cursorFill.setColor(0x22FFFFFF);
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(0xFF2B5FD0);
        handle.setColor(0xFF2F6BE0);
        lassoPaint.setStyle(Paint.Style.STROKE);
        lassoPaint.setColor(0xFF2B5FD0);
        lassoPaint.setStrokeJoin(Paint.Join.ROUND);
        lassoFill.setColor(0x142B5FD0);
        pill.setColor(0xE622262E);
        pillText.setColor(0xFFFFFFFF);
        pillText.setTextSize(13 * Ui.sp(c, 1));
        pillText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        scrollPaint.setColor(0x998D96A6);
        setFocusable(false);
    }

    long key() { return key; }

    Page page() { return page; }

    void setInteractive(boolean on) {
        if (!on) {
            finishInput();
            clearSelection();
        }
        interactive = on;
    }

    void setAlign(int a) {
        if (a == align) return;
        align = a;
        layoutDirty = true;
        invalidate();
    }

    /** Affiche une page (ou rien si k == Long.MIN_VALUE). */
    void bind(long k) {
        finishInput();
        if (sel != null) {
            sel = null;
            selSet = null;
            host.onSelectionChanged(this);
        }
        key = k;
        page = k == Long.MIN_VALUE ? null : host.store().page(k);
        pageH = page == null ? Geo.H : Shapes.pageHeight(page.items);
        shade = 0;
        if (page == null && cache != null) {
            cache.recycle();
            cache = null;
            cacheCanvas = null;
        }
        layoutDirty = true;
        cacheValid = false;
        invalidate();
        removeCallbacks(prerender);
        if (page != null) post(prerender);
    }

    /** Prépare l'image d'une page voisine (pour qu'elle tourne sans à-coup). */
    private final Runnable prerender = new Runnable() {
        @Override
        public void run() {
            if (page == null || getWidth() <= 0 || getHeight() <= 0) return;
            ensureLayout();
            if (!cacheValid) renderCache();
        }
    };

    /** Le contenu a changé hors de la vue (annuler, déplacement vers ce jour...). */
    void refresh() {
        if (page != null) {
            if (sel != null) {
                for (Item it : sel) {
                    if (!page.contains(it)) {
                        clearSelection();
                        break;
                    }
                }
            }
            float nh = Shapes.pageHeight(page.items);
            if (nh > pageH) {
                pageH = nh;
                clampViewport();
            }
        }
        cacheValid = false;
        invalidate();
    }

    // --------------------------------------------------------------- mise en page

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutDirty = true;
    }

    private void ensureLayout() {
        if (layoutDirty || vw != getWidth() || vh != getHeight()) relayout();
    }

    private void relayout() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        layoutDirty = false;
        vw = w;
        vh = h;
        float outer = 12 * dp, spine = 5 * dp;
        padY = 10 * dp;
        padL = align == ALIGN_START ? spine : outer;
        padR = align == ALIGN_END ? spine : outer;
        fit = Math.max(0.01f, Math.min((w - padL - padR) / Geo.W, (h - 2 * padY) / Geo.H));
        resetViewport();
        cacheValid = false;
    }

    private float baseLeft(float pw) {
        if (align == ALIGN_END) return (float) Math.floor(vw - padR - pw);  // page de gauche : contre la reliure
        if (align == ALIGN_START) return (float) Math.ceil(padL);           // page de droite : contre la reliure
        return (float) Math.floor((vw - pw) / 2f);
    }

    private void resetViewport() {
        scale = fit;
        float pw = Geo.W * scale, ph = pageH * scale;
        ox = baseLeft(pw);
        oy = ph <= vh - 2 * padY + 0.5f ? (float) Math.floor((vh - ph) / 2f) : padY;
    }

    private void clampViewport() {
        float pw = Geo.W * scale, ph = pageH * scale;
        if (pw <= vw - padL - padR + 0.5f) ox = baseLeft(pw);
        else ox = clamp(ox, vw - padR - pw, padL);
        if (ph <= vh - 2 * padY + 0.5f) oy = (float) Math.floor((vh - ph) / 2f);
        else oy = clamp(oy, vh - padY - ph, padY);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private float px(float x) { return (x - ox) / scale; }

    private float py(float y) { return (y - oy) / scale; }

    // --------------------------------------------------------------- zoom / défilement (appelés par le pager)

    boolean isZoomed() { return scale > fit * 1.01f; }

    boolean canScroll() { return page != null && pageH * scale > vh - 2 * padY + 1; }

    /** Le doigt posé en (x, y) est-il pour la page (sélection, objet à poser, bouton de zoom) ? */
    boolean wantsFinger(float x, float y) {
        if (!interactive || page == null) return false;
        ensureLayout();
        if (host.tools().pendingInsert != null) return true;
        if (sel != null && (hitHandle(x, y) || hitSelection(x, y))) return true;
        return isZoomed() && pillContains(x, y);
    }

    void panZoom(float dx, float dy, float factor, float fx, float fy) {
        if (page == null) return;
        ensureLayout();
        float ns = clamp(scale * factor, fit, fit * MAX_ZOOM);
        float k = ns / scale;
        ox = fx - (fx - ox) * k + dx;
        oy = fy - (fy - oy) * k + dy;
        scale = ns;
        clampViewport();
        invalidate();
        if (sel != null) host.onSelectionChanged(this);
    }

    void endPanZoom() {
        if (scale != fit && scale < fit * 1.04f) {
            scale = fit;
            clampViewport();
        }
        removeCallbacks(crisp);
        post(crisp);
        invalidate();
        if (sel != null) host.onSelectionChanged(this);
    }

    void resetZoom() {
        scale = fit;
        clampViewport();
        cacheValid = false;
        invalidate();
        if (sel != null) host.onSelectionChanged(this);
    }

    // --------------------------------------------------------------- animation "page qui tourne"

    void setShade(float s) {
        if (s != shade) {
            shade = s;
            invalidate();
        }
    }

    float pageLeftPx() {
        ensureLayout();
        return ox;
    }

    float pageRightPx() {
        ensureLayout();
        return ox + Geo.W * scale;
    }

    // --------------------------------------------------------------- image de la zone visible

    private boolean viewportMatches() {
        return cacheValid && cScale == scale && cOx == ox && cOy == oy;
    }

    private void ensureCache() {
        if (cache == null || cache.getWidth() != vw || cache.getHeight() != vh) {
            if (cache != null) cache.recycle();
            cache = Bitmap.createBitmap(Math.max(1, vw), Math.max(1, vh), Bitmap.Config.ARGB_8888);
            cacheCanvas = new Canvas(cache);
            cacheValid = false;
        }
    }

    private int bgStyle() {
        return Geo.isVrac(key) ? Settings.bgVrac : Settings.bgAgenda;
    }

    private void renderCache() {
        ensureCache();
        cache.eraseColor(0);
        float pl = Math.max(0, ox), pt = Math.max(0, oy);
        float pr = Math.min(vw, ox + Geo.W * scale), pb = Math.min(vh, oy + pageH * scale);
        long today = Days.today();
        if (pr > pl && pb > pt) {
            int save = cacheCanvas.save();
            cacheCanvas.clipRect(pl, pt, pr, pb);
            cacheCanvas.translate(ox, oy);
            cacheCanvas.scale(scale, scale);
            renderer.drawPaper(cacheCanvas, key, today, pageH, bgStyle(), !Geo.isVrac(key) && Settings.bandVisible());
            renderer.drawItems(cacheCanvas, page.items, selSet, false, scale,
                    px(pl), py(pt), px(pr), py(pb));
            cacheCanvas.restoreToCount(save);
        }
        cScale = scale;
        cOx = ox;
        cOy = oy;
        cToday = today;
        cRevision = Settings.revision;
        cacheValid = true;
    }

    /** Ajoute un trait d'encre à l'image sans tout redessiner. */
    private void commitToCache(Stroke s) {
        if (!viewportMatches() || cache == null) {
            cacheValid = false;
            return;
        }
        int save = cacheCanvas.save();
        cacheCanvas.clipRect(Math.max(0, ox), Math.max(0, oy), Math.min(vw, ox + Geo.W * scale), Math.min(vh, oy + pageH * scale));
        cacheCanvas.translate(ox, oy);
        cacheCanvas.scale(scale, scale);
        renderer.drawPen(cacheCanvas, s, scale);
        cacheCanvas.restoreToCount(save);
    }

    // --------------------------------------------------------------- dessin

    @Override
    protected void onDraw(Canvas c) {
        if (page == null) return;
        ensureLayout();
        if (vw <= 0 || vh <= 0) return;
        if (cacheValid && (cToday != Days.today() || cRevision != Settings.revision)) cacheValid = false;
        if (!cacheValid) renderCache();

        float pr = ox + Geo.W * scale, pb = oy + pageH * scale;
        // ombre douce sous la feuille
        for (int i = 3; i >= 1; i--) {
            float e = i * dp;
            shadow.setColor((i == 1 ? 26 : i == 2 ? 15 : 8) << 24);
            tmp.set(ox - e * 0.5f, oy + e * 0.3f, pr + e * 0.5f, pb + e * 1.2f);
            c.drawRoundRect(tmp, 3 * dp + e, 3 * dp + e, shadow);
        }
        if (viewportMatches()) {
            c.drawBitmap(cache, 0, 0, null);
        } else {
            // pendant un zoom / défilement : l'ancienne image déplacée, puis une image nette
            float k = scale / cScale;
            int s = c.save();
            c.translate(ox - cOx * k, oy - cOy * k);
            c.scale(k, k);
            c.drawBitmap(cache, 0, 0, bmpPaint);
            c.restoreToCount(s);
            removeCallbacks(crisp);
            postDelayed(crisp, 140);
        }

        int save = c.save();
        c.clipRect(Math.max(0, ox), Math.max(0, oy), Math.min(vw, pr), Math.min(vh, pb));
        c.translate(ox, oy);
        c.scale(scale, scale);
        if (current != null) renderer.drawItem(c, current, scale);
        if (sel != null) drawSelection(c);
        if (mode == M_LASSO && lassoN > 1) drawLasso(c);
        if (mode == M_PLACE && host.tools().pendingInsert != null) {
            renderer.drawObj(c, host.tools().pendingInsert.at(placeX, placeY));
        }
        c.restoreToCount(save);

        if (showCursor) {
            float r = ToolState.ERASER_RADIUS * scale;
            c.drawCircle(cursorX, cursorY, r, cursorFill);
            c.drawCircle(cursorX, cursorY, r, cursorPaint);
        }
        if (canScroll()) drawScrollbar(c);
        if (isZoomed()) drawPill(c);
        if (shade > 0) {
            shadePaint.setColor(((int) (Math.min(1f, shade) * 255)) << 24);
            c.drawRect(ox, oy, pr, pb, shadePaint);
        }
    }

    private void drawSelection(Canvas c) {
        int s = c.save();
        c.translate(selL + tdx, selT + tdy);
        c.scale(tk, tk);
        c.translate(-selL, -selT);
        renderer.drawItems(c, sel, null, false, scale * tk, Float.NaN, 0, 0, 0);
        c.restoreToCount(s);
        float pad = 8 * dp / scale;
        float l = selL + tdx - pad, t = selT + tdy - pad;
        float r = selL + tdx + (selR - selL) * tk + pad, b = selT + tdy + (selB - selT) * tk + pad;
        frame.setStrokeWidth(1.6f * dp / scale);
        frame.setPathEffect(new DashPathEffect(new float[]{7 * dp / scale, 5 * dp / scale}, 0));
        tmp.set(l, t, r, b);
        c.drawRoundRect(tmp, 6 * dp / scale, 6 * dp / scale, frame);
        handle.setColor(0xFFFFFFFF);
        c.drawCircle(r, b, 11 * dp / scale, handle);
        handle.setColor(0xFF2F6BE0);
        c.drawCircle(r, b, 8.5f * dp / scale, handle);
    }

    private void drawLasso(Canvas c) {
        lassoPath.rewind();
        lassoPath.moveTo(lasso[0], lasso[1]);
        for (int i = 1; i < lassoN; i++) lassoPath.lineTo(lasso[i * 2], lasso[i * 2 + 1]);
        lassoPath.close();
        c.drawPath(lassoPath, lassoFill);
        lassoPaint.setStrokeWidth(1.8f * dp / scale);
        lassoPaint.setPathEffect(new DashPathEffect(new float[]{6 * dp / scale, 5 * dp / scale}, 0));
        c.drawPath(lassoPath, lassoPaint);
    }

    private void drawScrollbar(Canvas c) {
        float visible = vh - 2 * padY, content = pageH * scale;
        float len = Math.max(28 * dp, visible * visible / content);
        float pos = clamp((padY - oy) / Math.max(1f, content - visible), 0f, 1f);
        float top = padY + pos * (visible - len);
        float x = vw - 5 * dp;
        tmp.set(x - 2 * dp, top, x + 2 * dp, top + len);
        c.drawRoundRect(tmp, 2 * dp, 2 * dp, scrollPaint);
    }

    private String pillLabel() {
        return Math.round(scale / fit * 100) + L.s(" %", "%");
    }

    private void pillRect(RectF out) {
        float w = pillText.measureText(pillLabel()) + 46 * dp;
        out.set(vw - 14 * dp - w, 14 * dp, vw - 14 * dp, 48 * dp);
    }

    private boolean pillContains(float x, float y) {
        pillRect(tmp2);
        tmp2.inset(-6 * dp, -6 * dp);
        return tmp2.contains(x, y);
    }

    private void drawPill(Canvas c) {
        pillRect(tmp2);
        c.drawRoundRect(tmp2, 17 * dp, 17 * dp, pill);
        String s = pillLabel();
        c.drawText(s, tmp2.left + 14 * dp, tmp2.centerY() + 4.5f * dp, pillText);
        // petite croix "revenir à 100 %"
        float cx = tmp2.right - 16 * dp, cy = tmp2.centerY(), r = 4.5f * dp;
        pillText.setStrokeWidth(1.8f * dp);
        c.drawLine(cx - r, cy - r, cx + r, cy + r, pillText);
        c.drawLine(cx - r, cy + r, cx + r, cy - r, pillText);
    }

    // --------------------------------------------------------------- saisie

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!interactive || page == null) return interactive;
        ensureLayout();
        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                fingerBlocked = false;
                // fall through
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                boolean stylus = isStylus(ev.getToolType(idx));
                if (stylus) {
                    host.onStylusActivity(ev.getEventTime());
                    checkButton(ev);
                }
                if (activeId != -1) {
                    if (!isStylus(activeTool)) {
                        // geste au doigt interrompu : 2e doigt (glissement) ou stylet qui arrive
                        abortGesture();
                        if (stylus) start(ev, idx);
                        else fingerBlocked = true;
                    }
                    // stylet en cours : on ignore la paume / les doigts
                    return true;
                }
                if (!stylus) {
                    if (fingerBlocked) return true;
                    if (countFingers(ev) > 1) {
                        fingerBlocked = true;
                        return true;
                    }
                }
                start(ev, idx);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (activeId == -1) return true;
                int idx = ev.findPointerIndex(activeId);
                if (idx < 0) return true;
                if (isStylus(activeTool)) {
                    host.onStylusActivity(ev.getEventTime());
                    checkButton(ev);
                }
                int hs = ev.getHistorySize();
                for (int h = 0; h < hs; h++) {
                    move(ev.getHistoricalX(idx, h), ev.getHistoricalY(idx, h), ev.getHistoricalPressure(idx, h));
                }
                move(ev.getX(idx), ev.getY(idx), ev.getPressure(idx));
                invalidate();
                if (mode == M_MOVE || mode == M_SCALE) host.onSelectionChanged(this);
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                int idx = ev.getActionIndex();
                if (activeId != -1 && ev.getPointerId(idx) == activeId) {
                    if ((ev.getFlags() & FLAG_CANCELED) != 0) abortGesture();
                    else end(ev.getX(idx), ev.getY(idx), ev.getPressure(idx), ev.getEventTime());
                }
                if (action == MotionEvent.ACTION_UP) fingerBlocked = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (activeId != -1) {
                    if (mode == M_INK && isStylus(activeTool)) end(lastX, lastY, lastP, ev.getEventTime());
                    else abortGesture();
                }
                fingerBlocked = false;
                return true;
        }
        return true;
    }

    private static int countFingers(MotionEvent ev) {
        int f = 0;
        for (int i = 0; i < ev.getPointerCount(); i++) if (!isStylus(ev.getToolType(i))) f++;
        return f;
    }

    private void stop() {
        activeId = -1;
        mode = M_NONE;
    }

    private void start(MotionEvent ev, int idx) {
        float x = ev.getX(idx), y = ev.getY(idx);
        int tool = ev.getToolType(idx);
        boolean stylus = isStylus(tool);
        ToolState t = host.tools();
        activeId = ev.getPointerId(idx);
        activeTool = tool;
        lastX = downX = x;
        lastY = downY = y;
        lastP = ev.getPressure(idx);
        downT = ev.getEventTime();
        float lx = px(x), ly = py(y);
        boolean onPage = lx >= -8 && lx <= Geo.W + 8 && ly >= -8 && ly <= pageH + 8;
        if (stylus) requestUnbufferedDispatch(ev);

        if (isZoomed() && pillContains(x, y)) {
            mode = M_PILL;
            return;
        }
        if (t.pendingInsert != null) {
            if (onPage) {
                mode = M_PLACE;
                placeX = lx;
                placeY = ly;
                invalidate();
            } else {
                stop();
            }
            return;
        }
        if (sel != null) {
            if (hitHandle(x, y)) {
                mode = M_SCALE;
                return;
            }
            if (hitSelection(x, y)) {
                mode = M_MOVE;
                return;
            }
            clearSelection();
        }
        if (!stylus && !t.fingerDraw) {
            // le doigt touche : cocher une case, coller... sauf si c'est la paume pendant l'écriture
            if (host.stylusRecent(ev.getEventTime()) || ev.getTouchMajor(idx) > 70 * dp) stop();
            else mode = M_TAP;
            return;
        }
        if (!onPage) {
            stop();
            return;
        }

        int eff = t.tool;
        boolean btn = stylus && tool != MotionEvent.TOOL_TYPE_MOUSE && (ev.getButtonState() & BUTTONS) != 0;
        if (tool == MotionEvent.TOOL_TYPE_ERASER) {
            eff = ToolState.ERASER;
        } else if (btn) {
            switch (Settings.stylusButton) {
                case Settings.BTN_ERASER:
                    eff = ToolState.ERASER;
                    break;
                case Settings.BTN_HIGHLIGHTER:
                    eff = ToolState.HIGHLIGHTER;
                    break;
                case Settings.BTN_LASSO:
                    eff = ToolState.LASSO;
                    break;
                default:
                    break; // "Annuler" / "Rien" : l'outil courant
            }
        }
        switch (eff) {
            case ToolState.ERASER:
                mode = M_ERASE;
                eraseEdit = new Edit();
                lastEraseX = lx;
                lastEraseY = ly;
                eraseAt(lx, ly);
                showCursor = true;
                cursorX = x;
                cursorY = y;
                break;
            case ToolState.LASSO:
                mode = M_LASSO;
                lassoN = 0;
                addLasso(lx, ly);
                break;
            default: {
                mode = M_INK;
                boolean hlTool = eff == ToolState.HIGHLIGHTER;
                current = new Stroke(hlTool ? ToolState.HIGHLIGHT_COLOR : t.penColor(),
                        hlTool ? ToolState.HIGHLIGHT_WIDTH : t.penWidth(),
                        hlTool ? Stroke.FLAG_HIGHLIGHTER : 0, 256);
                smoothP = -1f;
                addPoint(lx, ly, lastP, true);
                tapBox = hlTool ? null : boxAt(lx, ly, 6f);
            }
        }
        invalidate();
    }

    private void move(float x, float y, float p) {
        lastX = x;
        lastY = y;
        lastP = p;
        float lx = px(x), ly = py(y);
        switch (mode) {
            case M_INK:
                if (current != null) addPoint(lx, ly, p, false);
                break;
            case M_ERASE:
                eraseSegment(lastEraseX, lastEraseY, lx, ly);
                lastEraseX = lx;
                lastEraseY = ly;
                cursorX = x;
                cursorY = y;
                break;
            case M_LASSO:
                addLasso(lx, ly);
                break;
            case M_MOVE:
                tdx = (x - downX) / scale;
                tdy = (y - downY) / scale;
                break;
            case M_SCALE: {
                float d = ((x - downX) + (y - downY)) / scale;
                float base = Math.max(10f, (selR - selL) + (selB - selT));
                tk = clamp(1f + d / base, 0.25f, 6f);
                break;
            }
            case M_PLACE:
                placeX = lx;
                placeY = ly;
                break;
            default:
                break;
        }
    }

    private void end(float x, float y, float p, long time) {
        move(x, y, p);
        int m = mode;
        stop();
        switch (m) {
            case M_INK:
                finishInk(time);
                break;
            case M_ERASE:
                showCursor = false;
                commitErase();
                break;
            case M_LASSO:
                finishLasso();
                break;
            case M_MOVE:
            case M_SCALE:
                commitTransform();
                break;
            case M_PLACE:
                place();
                break;
            case M_TAP:
                if (Math.hypot(x - downX, y - downY) < touchSlop * 1.2f && time - downT < 450) fingerTap(px(x), py(y));
                break;
            case M_PILL:
                if (pillContains(x, y)) resetZoom();
                break;
            default:
                break;
        }
        invalidate();
    }

    /** Annule le geste en cours (doigt interrompu, paume rejetée). */
    private void abortGesture() {
        switch (mode) {
            case M_ERASE:
                showCursor = false;
                commitErase(); // ce qui est déjà gommé reste gommé (et peut être annulé)
                break;
            case M_MOVE:
            case M_SCALE:
                tdx = tdy = 0;
                tk = 1f;
                if (sel != null) host.onSelectionChanged(this);
                break;
            case M_LASSO:
                lassoN = 0;
                break;
            default:
                break;
        }
        current = null;
        stop();
        invalidate();
    }

    /** Appelé quand le pager prend la main (glissement, zoom) : un geste au doigt est abandonné. */
    void abortFingerInput() {
        if (activeId != -1 && !isStylus(activeTool)) abortGesture();
        fingerBlocked = true;
    }

    /** Termine proprement tout geste (changement de page, perte d'interactivité). */
    private void finishInput() {
        if (activeId == -1) return;
        if (mode == M_INK && isStylus(activeTool)) end(lastX, lastY, lastP, SystemClock.uptimeMillis());
        else abortGesture();
    }

    // --------------------------------------------------------------- encre et cases

    private void addPoint(float lx, float ly, float pressure, boolean force) {
        ToolState t = host.tools();
        float f = 1f;
        if (isStylus(activeTool) && activeTool != MotionEvent.TOOL_TYPE_MOUSE && t.pressure && !current.isHighlighter()) {
            float pr = pressure;
            if (pr < 0.05f) pr = 0.05f;
            else if (pr > 1f) pr = 1f;
            smoothP = smoothP < 0 ? pr : smoothP * 0.55f + pr * 0.45f;
            f = 0.42f + 0.95f * smoothP;
        }
        if (current.n > 0 && !force) {
            float dx = lx - current.lastX(), dy = ly - current.lastY();
            if (dx * dx + dy * dy < 0.5f) return;
        }
        current.add(lx, ly, f);
    }

    private void finishInk(long time) {
        Stroke s = current;
        current = null;
        if (s == null || s.n == 0) return;
        s.trim();
        // un simple appui sur une case : on la coche / décoche
        float size = Math.max(s.maxX - s.minX, s.maxY - s.minY);
        if (tapBox != null && page.contains(tapBox) && time - downT < 350 && size < Math.max(6f, tapBox.h * 0.3f)) {
            setBoxChecked(tapBox, !tapBox.checked);
            return;
        }
        // une coche (ou une croix) dessinée dans une case vide : la case se coche
        if (!s.isHighlighter()) {
            Obj target = boxForCheck(s);
            if (target != null) {
                setBoxChecked(target, true);
                return;
            }
        }
        Edit e = new Edit();
        e.add(page, s);
        host.store().commit(e);
        if (s.isHighlighter()) cacheValid = false;
        else commitToCache(s);
        afterEdit();
        // un petit carré dessiné (en un ou plusieurs traits) devient une case à cocher, toujours de la
        // même taille que celle du menu Insérer ; annuler redonne le dessin
        if (!s.isHighlighter()) {
            if (recentKey != key || downT - lastInkUp > 1600) recentInk.clear();
            recentKey = key;
            lastInkUp = time;
            recentInk.add(s);
            while (recentInk.size() > 4) recentInk.remove(0);
            for (int i = recentInk.size() - 1; i >= 0; i--) {
                if (!page.contains(recentInk.get(i))) { // effacé ou annulé entre-temps
                    recentInk.subList(0, i + 1).clear();
                    break;
                }
            }
            Shapes.Box box = Shapes.findBox(recentInk);
            if (box != null) {
                int n = recentInk.size();
                ArrayList<Stroke> parts = new ArrayList<>(recentInk.subList(n - box.strokes, n));
                recentInk.clear();
                Obj b = PageRenderer.makeBox(box.cx, box.cy, PageRenderer.BOX_SIZE, s.color);
                Edit e2 = new Edit();
                if (parts.size() > 1) e2.remove(page, parts.subList(0, parts.size() - 1));
                e2.replace(page, s, b);
                host.store().commit(e2);
                cacheValid = false;
                afterEdit();
                host.onHint(HINT_BOX);
            }
        }
    }

    private Obj boxAt(float lx, float ly, float tol) {
        for (int i = page.items.size() - 1; i >= 0; i--) {
            Item it = page.items.get(i);
            if (it instanceof Obj && ((Obj) it).isBox() && it.hit(lx, ly, tol)) return (Obj) it;
        }
        return null;
    }

    /** Case vide dans laquelle ce trait (une coche, une croix) a été dessiné, ou null. */
    private Obj boxForCheck(Stroke s) {
        float sx = s.pts[0], sy = s.pts[1];
        float len = s.length();
        for (int i = page.items.size() - 1; i >= 0; i--) {
            Item it = page.items.get(i);
            if (!(it instanceof Obj)) continue;
            Obj o = (Obj) it;
            if (!o.isBox() || o.checked) continue;
            float e = o.h * 0.18f;
            if (sx < o.left() - e || sx > o.right() + e || sy < o.top() - e || sy > o.bottom() + e) continue;
            float m = o.h * 0.75f;
            if (s.minX >= o.left() - m && s.maxX <= o.right() + m && s.minY >= o.top() - m && s.maxY <= o.bottom() + m
                    && len < o.h * 5f && len > o.h * 0.4f) {
                return o;
            }
        }
        return null;
    }

    private void setBoxChecked(Obj o, boolean checked) {
        Edit e = new Edit();
        e.replace(page, o, o.withChecked(checked));
        host.store().commit(e);
        cacheValid = false;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        afterEdit();
    }

    private void fingerTap(float lx, float ly) {
        Obj b = boxAt(lx, ly, 14 * dp / scale);
        if (b != null) {
            setBoxChecked(b, !b.checked);
            return;
        }
        ToolState t = host.tools();
        if (t.tool == ToolState.LASSO && t.clip != null && lx >= 0 && lx <= Geo.W && ly >= 0 && ly <= pageH) paste(lx, ly);
    }

    private void afterEdit() {
        float nh = Shapes.pageHeight(page.items);
        if (nh > pageH) {
            pageH = nh;
            clampViewport();
            cacheValid = false;
            host.onHint(HINT_EXTENDED);
        }
        host.onPageEdited(this, page);
        invalidate();
    }

    // --------------------------------------------------------------- gomme

    private void eraseSegment(float x0, float y0, float x1, float y1) {
        float step = ToolState.ERASER_RADIUS * 0.5f;
        float dx = x1 - x0, dy = y1 - y0;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) Math.ceil(d / step));
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            eraseAt(x0 + dx * t, y0 + dy * t);
        }
    }

    private void eraseAt(float x, float y) {
        if (eraseEdit == null) return;
        boolean any = false;
        for (int i = page.items.size() - 1; i >= 0; i--) {
            if (page.items.get(i).hit(x, y, ToolState.ERASER_RADIUS)) {
                eraseEdit.removeAt(page, i);
                any = true;
            }
        }
        if (any) cacheValid = false;
    }

    private void commitErase() {
        Edit e = eraseEdit;
        eraseEdit = null;
        if (e != null && !e.isEmpty()) {
            host.store().commit(e);
            afterEdit();
        }
    }

    // --------------------------------------------------------------- lasso et sélection

    private void addLasso(float lx, float ly) {
        if (lassoN > 0) {
            float dx = lx - lasso[(lassoN - 1) * 2], dy = ly - lasso[(lassoN - 1) * 2 + 1];
            if (dx * dx + dy * dy < 4f) return;
        }
        if (lassoN * 2 + 2 > lasso.length) {
            float[] n = new float[lasso.length * 2];
            System.arraycopy(lasso, 0, n, 0, lassoN * 2);
            lasso = n;
        }
        lasso[lassoN * 2] = lx;
        lasso[lassoN * 2 + 1] = ly;
        lassoN++;
    }

    private void finishLasso() {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (int i = 0; i < lassoN; i++) {
            l = Math.min(l, lasso[i * 2]);
            r = Math.max(r, lasso[i * 2]);
            t = Math.min(t, lasso[i * 2 + 1]);
            b = Math.max(b, lasso[i * 2 + 1]);
        }
        int n = lassoN;
        lassoN = 0;
        if (n < 4 || Math.max(r - l, b - t) < 10f) {
            // simple appui avec le lasso : coller ce qui a été copié
            ToolState ts = host.tools();
            if (ts.clip != null && n > 0) paste(lasso[0], lasso[1]);
            return;
        }
        ArrayList<Item> found = new ArrayList<>();
        for (Item it : page.items) if (it.insideFraction(lasso, n) >= 0.6f) found.add(it);
        if (!found.isEmpty()) setSelection(found);
    }

    private void setSelection(ArrayList<Item> items) {
        sel = items;
        selSet = new IdentityHashMap<>();
        selL = selT = Float.MAX_VALUE;
        selR = selB = -Float.MAX_VALUE;
        for (Item it : items) {
            selSet.put(it, Boolean.TRUE);
            selL = Math.min(selL, it.left());
            selT = Math.min(selT, it.top());
            selR = Math.max(selR, it.right());
            selB = Math.max(selB, it.bottom());
        }
        tdx = tdy = 0;
        tk = 1f;
        cacheValid = false;
        host.onSelectionChanged(this);
        invalidate();
    }

    void clearSelection() {
        if (sel == null) return;
        sel = null;
        selSet = null;
        tdx = tdy = 0;
        tk = 1f;
        cacheValid = false;
        host.onSelectionChanged(this);
        invalidate();
    }

    boolean hasSelection() {
        return sel != null;
    }

    /** Cadre de la sélection, en pixels de cette vue. */
    void selectionBounds(RectF out) {
        float pad = 8 * dp;
        out.set(ox + (selL + tdx) * scale - pad, oy + (selT + tdy) * scale - pad,
                ox + (selL + tdx + (selR - selL) * tk) * scale + pad, oy + (selT + tdy + (selB - selT) * tk) * scale + pad);
    }

    private boolean hitSelection(float x, float y) {
        selectionBounds(tmp2);
        tmp2.inset(-10 * dp, -10 * dp);
        return tmp2.contains(x, y);
    }

    private boolean hitHandle(float x, float y) {
        selectionBounds(tmp2);
        return Math.hypot(x - tmp2.right, y - tmp2.bottom) <= 26 * dp;
    }

    private void commitTransform() {
        if (sel == null) return;
        boolean changed = Math.abs(tdx) > 0.5f || Math.abs(tdy) > 0.5f || Math.abs(tk - 1f) > 0.01f;
        if (!changed) {
            tdx = tdy = 0;
            tk = 1f;
            host.onSelectionChanged(this);
            return;
        }
        ArrayList<Item> news = new ArrayList<>(sel.size());
        for (Item it : sel) news.add(it.transformed(selL, selT, tk, tdx, tdy));
        Edit e = new Edit();
        e.replace(page, sel, news);
        host.store().commit(e);
        setSelection(news);
        afterEdit();
    }

    void recolorSelection(int color) {
        if (sel == null) return;
        ArrayList<Item> news = new ArrayList<>(sel.size());
        boolean any = false;
        for (Item it : sel) {
            Item n = it.recolored(color);
            any |= n != it;
            news.add(n);
        }
        if (!any) return;
        Edit e = new Edit();
        e.replace(page, sel, news);
        host.store().commit(e);
        setSelection(news);
        afterEdit();
    }

    void deleteSelection() {
        if (sel == null) return;
        Edit e = new Edit();
        e.remove(page, sel);
        host.store().commit(e);
        clearSelection();
        afterEdit();
    }

    void copySelection() {
        if (sel == null) return;
        ToolState t = host.tools();
        t.clip = new ArrayList<>(sel);
        t.clipL = selL;
        t.clipT = selT;
    }

    /** Déplace la sélection vers une autre page, à la suite de ce qui y est déjà écrit. */
    boolean moveSelectionTo(Page dest) {
        if (sel == null || dest == null || dest == page) return false;
        float dy = 0;
        if (!dest.items.isEmpty()) {
            float target = Math.max(Shapes.contentBottom(dest.items) + 18f, Geo.FIRST_RULE + 6f);
            dy = target - selT;
        }
        ArrayList<Item> moved = new ArrayList<>(sel.size());
        for (Item it : sel) moved.add(it.transformed(0, 0, 1f, 0, dy));
        Edit e = new Edit();
        e.remove(page, sel);
        e.insert(dest, dest.items.size(), moved);
        host.store().commit(e);
        clearSelection();
        afterEdit();
        return true;
    }

    private void paste(float lx, float ly) {
        ToolState t = host.tools();
        if (t.clip == null || t.clip.isEmpty()) return;
        ArrayList<Item> items = new ArrayList<>(t.clip.size());
        for (Item it : t.clip) items.add(it.transformed(t.clipL, t.clipT, 1f, lx - t.clipL, ly - t.clipT));
        Edit e = new Edit();
        e.insert(page, page.items.size(), items);
        host.store().commit(e);
        setSelection(items);
        afterEdit();
    }

    private void place() {
        ToolState t = host.tools();
        Obj proto = t.pendingInsert;
        t.pendingInsert = null;
        if (proto == null) return;
        float x = clamp(placeX, proto.w / 2, Geo.W - proto.w / 2);
        float y = Math.max(placeY, proto.h / 2);
        Edit e = new Edit();
        e.add(page, proto.at(x, y));
        host.store().commit(e);
        cacheValid = false;
        afterEdit();
        host.onInsertPlaced();
    }

    // --------------------------------------------------------------- stylet : survol et bouton

    private void checkButton(MotionEvent ev) {
        boolean down = (ev.getButtonState() & BUTTONS) != 0;
        if (down && !stylusBtnDown) {
            stylusBtnDown = true;
            if (Settings.stylusButton == Settings.BTN_UNDO) host.onStylusButtonUndo(this);
        } else if (!down) {
            stylusBtnDown = false;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if ((a == ACTION_BUTTON_PRESS || a == ACTION_BUTTON_RELEASE) && interactive
                && ev.getPointerCount() > 0 && isStylus(ev.getToolType(0))) {
            checkButton(ev);
            return true;
        }
        return super.onGenericMotionEvent(ev);
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        if (!interactive) return super.onHoverEvent(ev);
        int tool = ev.getToolType(0);
        if (isStylus(tool) && tool != MotionEvent.TOOL_TYPE_MOUSE) {
            host.onStylusActivity(ev.getEventTime());
            checkButton(ev);
            boolean btn = (ev.getButtonState() & BUTTONS) != 0;
            boolean eraserMode = host.tools().tool == ToolState.ERASER
                    || tool == MotionEvent.TOOL_TYPE_ERASER
                    || (btn && Settings.stylusButton == Settings.BTN_ERASER);
            int a = ev.getActionMasked();
            boolean show = eraserMode && a != MotionEvent.ACTION_HOVER_EXIT;
            if (show || showCursor) {
                showCursor = show;
                cursorX = ev.getX();
                cursorY = ev.getY();
                invalidate();
            }
            return true;
        }
        return super.onHoverEvent(ev);
    }
}
