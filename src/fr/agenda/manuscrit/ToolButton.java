package fr.agenda.manuscrit;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Bouton de barre d'outils dessiné en code (icône, pastille de couleur ou épaisseur). */
final class ToolButton extends View {
    static final int CHEV_L = 1, CHEV_R = 2, UNDO = 3, REDO = 4, ERASER = 5, HIGHLIGHTER = 6, MORE = 7,
            COLOR = 8, WIDTH = 9, WEEK = 10, DAY = 11, LASSO = 12, INSERT = 13;

    private final int kind;
    private int color;
    private float widthUnits;
    private boolean selectedLook;
    private boolean enabledLook = true;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final DashPathEffect dash = new DashPathEffect(new float[]{2.6f, 2.3f}, 0);
    private final RectF rf = new RectF();
    private final float dp;

    ToolButton(Context c, int kind, String description) {
        super(c);
        this.kind = kind;
        this.dp = Ui.dp(c, 1);
        setContentDescription(description);
        setClickable(true);
        setFocusable(true);
        TypedArray a = c.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackgroundBorderless});
        setBackground(a.getDrawable(0));
        a.recycle();
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setSwatch(int color) {
        this.color = color;
        invalidate();
    }

    void setWidthUnits(float w) {
        this.widthUnits = w;
        invalidate();
    }

    void setSelectedLook(boolean s) {
        if (s != selectedLook) {
            selectedLook = s;
            invalidate();
        }
    }

    void setEnabledLook(boolean e) {
        if (e != enabledLook) {
            enabledLook = e;
            setEnabled(e);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float box = Math.min(w, h);
        int alpha = enabledLook ? 255 : 70;

        if (kind == COLOR) {
            float rad = box * 0.27f;
            if (selectedLook) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * dp);
                p.setColor(Ui.ACCENT);
                c.drawCircle(cx, cy, rad + 5 * dp, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(cx, cy, rad, p);
            if (Ui.isDark(color)) { // encre noire : liseré clair pour la voir sur fond sombre
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f * dp);
                p.setColor(0xFF6E7582);
                c.drawCircle(cx, cy, rad, p);
            }
            return;
        }

        if (selectedLook) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.ACCENT_SOFT);
            float s = box * 0.42f;
            rf.set(cx - s, cy - s, cx + s, cy + s);
            c.drawRoundRect(rf, 10 * dp, 10 * dp, p);
        }

        if (kind == WIDTH) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(selectedLook ? Ui.ACCENT : Ui.INK);
            p.setAlpha(alpha);
            c.drawCircle(cx, cy, Math.max(1.5f * dp, widthUnits * 1.1f * dp), p);
            return;
        }

        // icônes vectorielles dans une boîte 24 x 24
        float s = 24 * dp > box * 0.62f ? box * 0.62f / 24f : dp;
        int save = c.save();
        c.translate(cx - 12 * s, cy - 12 * s);
        c.scale(s, s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(selectedLook ? Ui.ACCENT : Ui.INK);
        p.setAlpha(alpha);
        path.rewind();
        switch (kind) {
            case CHEV_L:
                path.moveTo(15, 5);
                path.lineTo(8, 12);
                path.lineTo(15, 19);
                c.drawPath(path, p);
                break;
            case CHEV_R:
                path.moveTo(9, 5);
                path.lineTo(16, 12);
                path.lineTo(9, 19);
                c.drawPath(path, p);
                break;
            case UNDO:
            case REDO:
                if (kind == REDO) {
                    c.translate(24, 0);
                    c.scale(-1, 1);
                }
                path.moveTo(8.5f, 4.5f);
                path.lineTo(4, 9);
                path.lineTo(8.5f, 13.5f);
                path.moveTo(4, 9);
                path.lineTo(14, 9);
                path.cubicTo(17.8f, 9, 20, 11.6f, 20, 14.5f);
                path.cubicTo(20, 17.4f, 17.8f, 20, 14, 20);
                path.lineTo(10, 20);
                c.drawPath(path, p);
                break;
            case ERASER:
                path.moveTo(14, 3.5f);
                path.lineTo(20.5f, 10);
                path.lineTo(11.5f, 19);
                path.lineTo(7.5f, 19);
                path.lineTo(3.5f, 15);
                path.close();
                path.moveTo(8.5f, 9);
                path.lineTo(15, 15.5f);
                path.moveTo(11.5f, 19);
                path.lineTo(20.5f, 19);
                c.drawPath(path, p);
                break;
            case HIGHLIGHTER: {
                // trait jaune "surligné" + feutre
                Paint.Style st = p.getStyle();
                p.setStyle(Paint.Style.FILL);
                int keep = p.getColor();
                p.setColor(0xFFFFD21A);
                p.setAlpha(enabledLook ? 255 : 70);
                rf.set(3, 18.5f, 21, 22);
                c.drawRoundRect(rf, 1.5f, 1.5f, p);
                p.setColor(keep);
                p.setStyle(st);
                path.moveTo(15.5f, 2.5f);
                path.lineTo(21, 8);
                path.lineTo(12.5f, 16.5f);
                path.lineTo(7, 11);
                path.close();
                path.moveTo(7, 11);
                path.lineTo(5, 15.5f);
                path.lineTo(8, 16.5f);
                path.lineTo(12.5f, 16.5f);
                c.drawPath(path, p);
                break;
            }
            case MORE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 5, 1.8f, p);
                c.drawCircle(12, 12, 1.8f, p);
                c.drawCircle(12, 19, 1.8f, p);
                break;
            case WEEK:
                rf.set(3, 4, 21, 20);
                c.drawRoundRect(rf, 2.5f, 2.5f, p);
                p.setStrokeWidth(1.6f);
                c.drawLine(9, 4, 9, 20, p);
                c.drawLine(15, 4, 15, 20, p);
                c.drawLine(3, 12, 21, 12, p);
                break;
            case LASSO:
                // boucle en pointillés + petite queue
                p.setPathEffect(dash);
                rf.set(3.5f, 3.5f, 20.5f, 15f);
                c.drawOval(rf, p);
                p.setPathEffect(null);
                path.moveTo(7.8f, 13.6f);
                path.cubicTo(6.2f, 16.5f, 9.8f, 18f, 7.2f, 21f);
                c.drawPath(path, p);
                break;
            case INSERT:
                // autocollant : carré arrondi avec un coin replié et un +
                path.moveTo(15.5f, 20.5f);
                path.lineTo(7f, 20.5f);
                path.quadTo(3.5f, 20.5f, 3.5f, 17f);
                path.lineTo(3.5f, 7f);
                path.quadTo(3.5f, 3.5f, 7f, 3.5f);
                path.lineTo(17f, 3.5f);
                path.quadTo(20.5f, 3.5f, 20.5f, 7f);
                path.lineTo(20.5f, 15.5f);
                path.close();
                path.moveTo(20.5f, 15.5f);
                path.lineTo(15.5f, 15.5f);
                path.lineTo(15.5f, 20.5f);
                c.drawPath(path, p);
                c.drawLine(12f, 7.5f, 12f, 13.5f, p);
                c.drawLine(9f, 10.5f, 15f, 10.5f, p);
                break;
            case DAY:
                rf.set(5, 3, 19, 21);
                c.drawRoundRect(rf, 2.5f, 2.5f, p);
                p.setStrokeWidth(1.6f);
                c.drawLine(8, 8, 16, 8, p);
                c.drawLine(8, 12, 16, 12, p);
                c.drawLine(8, 16, 13, 16, p);
                break;
        }
        c.restoreToCount(save);
    }
}
