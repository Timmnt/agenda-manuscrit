package fr.agenda.manuscrit;

import android.app.Dialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Petits éléments d'interface construits en code (thème sombre, accents bleus). */
final class Kit {
    private Kit() {}

    static int px(Context c, float dp) {
        return Math.round(Ui.dp(c, dp));
    }

    static GradientDrawable rounded(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    static void ripple(View v, boolean borderless) {
        TypedArray a = v.getContext().obtainStyledAttributes(new int[]{
                borderless ? android.R.attr.selectableItemBackgroundBorderless : android.R.attr.selectableItemBackground});
        v.setBackground(a.getDrawable(0));
        a.recycle();
    }

    static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s, 20, Ui.INK, true);
        t.setPadding(px(c, 22), px(c, 20), px(c, 22), px(c, 8));
        return t;
    }

    /** Bouton texte (strong = bleu, sinon gris ; danger = rouge). */
    static TextView button(Context c, String label, boolean strong) {
        TextView t = text(c, label, 15, strong ? Ui.ACCENT : Ui.GREY, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(c, 16), px(c, 10), px(c, 16), px(c, 10));
        t.setMinWidth(px(c, 56));
        ripple(t, false);
        t.setClickable(true);
        return t;
    }

    static TextView pill(Context c, String label, boolean filled) {
        TextView t = text(c, label, 14, filled ? 0xFFFFFFFF : Ui.ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(c, 14), px(c, 7), px(c, 14), px(c, 7));
        t.setBackground(rounded(filled ? Ui.ACCENT_FILL : Ui.SURFACE, Ui.dp(c, 18),
                filled ? Ui.ACCENT_FILL : Ui.ACCENT_LINE, Math.max(1, px(c, 1))));
        t.setClickable(true);
        return t;
    }

    static EditText field(Context c, String value, String hint) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.LIGHT_GREY);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setBackground(rounded(Ui.SURFACE_HI, Ui.dp(c, 10), Ui.BAR_LINE, Math.max(1, px(c, 1))));
        e.setPadding(px(c, 14), px(c, 10), px(c, 14), px(c, 10));
        if (value != null) e.setSelection(value.length());
        return e;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.BAR_LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(c, 1))));
        return v;
    }

    /** Rangée de boutons alignés à droite (avec, éventuellement, un bouton à gauche). */
    static LinearLayout footer(Context c, View left, View... right) {
        LinearLayout f = new LinearLayout(c);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(px(c, 10), px(c, 4), px(c, 10), px(c, 12));
        if (left != null) f.addView(left);
        f.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        for (View v : right) f.addView(v);
        return f;
    }

    /** Fenêtre sombre aux coins arrondis, contenu défilant si besoin. */
    static Dialog sheet(Context c, View body, View footer, int widthDp) {
        final Dialog d = new Dialog(c, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(Ui.SURFACE, Ui.dp(c, 22), 0, 0));
        root.setClipToOutline(true);
        ScrollView sv = new ScrollView(c);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (footer != null) root.addView(footer);
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(0x00000000));
        d.setCanceledOnTouchOutside(true);
        final int width = px(c, widthDp);
        d.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface di) {
                Window win = d.getWindow();
                if (win == null) return;
                int maxW = (int) (d.getContext().getResources().getDisplayMetrics().widthPixels * 0.94f);
                win.setLayout(Math.min(width, maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        return d;
    }

    // ------------------------------------------------------------------ disposition en lignes qui passent à la ligne

    static final class Flow extends ViewGroup {
        private final int hGap, vGap;

        Flow(Context c, int hGapPx, int vGapPx) {
            super(c);
            hGap = hGapPx;
            vGap = vGapPx;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) maxW = Integer.MAX_VALUE / 2;
            int x = 0, y = 0, rowH = 0, usedW = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                x += cw + hGap;
                usedW = Math.max(usedW, x - hGap);
                rowH = Math.max(rowH, chh);
            }
            int w = MeasureSpec.getMode(wSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(wSpec)
                    : usedW + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(w, y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                int left = getPaddingLeft() + x, top = getPaddingTop() + y;
                ch.layout(left, top, left + cw, top + chh);
                x += cw + hGap;
                rowH = Math.max(rowH, chh);
            }
        }
    }

    // ------------------------------------------------------------------ aperçu d'un objet (tampon, matière...)

    static final class ObjPreview extends View {
        private final Obj obj;
        private final PageRenderer renderer;
        private final int w, h;
        private final Paint paper = new Paint(Paint.ANTI_ALIAS_FLAG);

        ObjPreview(Context c, PageRenderer renderer, Obj obj, int wDp, int hDp) {
            super(c);
            this.obj = obj;
            this.renderer = renderer;
            this.w = px(c, wDp);
            this.h = px(c, hDp);
            paper.setColor(0xFFFFFFFF);
            setClickable(true);
            setBackground(rounded(0xFFFFFFFF, Ui.dp(c, 12), 0, 0));
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = Ui.dp(getContext(), 8);
            float s = Math.min((getWidth() - 2 * pad) / obj.w, (getHeight() - 2 * pad) / obj.h);
            s = Math.min(s, Ui.dp(getContext(), 1) * 1.1f);
            int save = c.save();
            c.translate(getWidth() / 2f, getHeight() / 2f);
            c.scale(s, s);
            renderer.drawObj(c, obj.at(0, 0));
            c.restoreToCount(save);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            setAlpha(pressed ? 0.6f : 1f);
        }
    }
}
