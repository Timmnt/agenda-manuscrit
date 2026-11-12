package fr.agenda.manuscrit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sélecteur de date maison : comme celui d'Android, mais avec un point sous chaque jour
 * où quelque chose est écrit.
 */
final class CalendarDialog extends Dialog {

    interface OnPick {
        void onPick(long day);
    }

    private final PageStore store;
    private final OnPick onPick;
    private final float dp;
    private long selected;
    private int year, month; // mois affiché (1..12)

    private final TextView yearTv, dateTv, monthTv;
    private final MonthView grid;

    CalendarDialog(Context c, PageStore store, long initial, OnPick onPick) {
        super(c, android.R.style.Theme_Material_Dialog_NoActionBar);
        this.store = store;
        this.onPick = onPick;
        this.dp = Ui.dp(c, 1);
        this.selected = initial;
        int[] ymd = Days.toYmd(initial);
        year = ymd[0];
        month = ymd[1];

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        bg.setCornerRadius(22 * dp);
        root.setBackground(bg);
        root.setClipToOutline(true);

        // bandeau bleu : année + date choisie
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(Ui.ACCENT_FILL);
        head.setPadding(px(26), px(20), px(26), px(18));
        yearTv = text(c, 15, 0xCCFFFFFF, false);
        dateTv = text(c, 32, 0xFFFFFFFF, true);
        head.addView(yearTv);
        head.addView(dateTv);
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // navigation de mois
        LinearLayout nav = new LinearLayout(c);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(px(10), px(6), px(10), 0);
        ToolButton prev = new ToolButton(c, ToolButton.CHEV_L, "Mois précédent");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(-1);
            }
        });
        ToolButton next = new ToolButton(c, ToolButton.CHEV_R, "Mois suivant");
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(1);
            }
        });
        monthTv = text(c, 16, Ui.INK, true);
        monthTv.setGravity(Gravity.CENTER);
        nav.addView(prev, new LinearLayout.LayoutParams(px(48), px(48)));
        nav.addView(monthTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(next, new LinearLayout.LayoutParams(px(48), px(48)));
        root.addView(nav);

        grid = new MonthView(c);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = px(14);
        glp.rightMargin = px(14);
        root.addView(grid, glp);

        // boutons
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(px(12), px(4), px(12), px(12));
        TextView todayBtn = button(c, "Aujourd'hui", false);
        todayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(Days.today(), true);
            }
        });
        TextView cancel = button(c, "Annuler", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        TextView ok = button(c, "OK", true);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                CalendarDialog.this.onPick.onPick(selected);
            }
        });
        foot.addView(todayBtn);
        foot.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        foot.addView(cancel);
        foot.addView(ok);
        root.addView(foot);

        setContentView(root);
        Window w = getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(0x00000000));
        setCanceledOnTouchOutside(true);
        refresh();
    }

    @Override
    public void show() {
        super.show();
        Window w = getWindow();
        if (w != null) {
            int maxW = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
            w.setLayout(Math.min(px(380), maxW), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private TextView text(Context c, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private TextView button(Context c, String label, boolean strong) {
        TextView t = text(c, 15, strong ? Ui.ACCENT : Ui.GREY, true);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(16), px(10), px(16), px(10));
        t.setMinWidth(px(56));
        android.content.res.TypedArray a = c.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
        t.setBackground(a.getDrawable(0));
        a.recycle();
        t.setClickable(true);
        return t;
    }

    private void shiftMonth(int delta) {
        month += delta;
        if (month < 1) {
            month = 12;
            year--;
        } else if (month > 12) {
            month = 1;
            year++;
        }
        refresh();
    }

    private void select(long day, boolean showMonth) {
        selected = day;
        if (showMonth) {
            int[] ymd = Days.toYmd(day);
            year = ymd[0];
            month = ymd[1];
        }
        refresh();
    }

    private void refresh() {
        int[] ymd = Days.toYmd(selected);
        yearTv.setText(String.valueOf(ymd[0]));
        dateTv.setText(Days.shortTitle(selected));
        monthTv.setText(Days.monthTitle(year, month));
        grid.invalidate();
    }

    // ------------------------------------------------------------------ grille du mois

    private final class MonthView extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface bold = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        private final float rowH = 46 * dp;
        private final float slop;
        private float downX, downY;
        private int pressed = -1;

        MonthView(Context c) {
            super(c);
            text.setTextAlign(Paint.Align.CENTER);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, (int) (rowH * 7 + 6 * dp));
        }

        private long firstDay() {
            return Days.fromYmd(year, month, 1);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cw = getWidth() / 7f;
            long first = firstDay();
            int offset = Days.dayOfWeek(first);
            int dim = Days.daysInMonth(year, month);
            long today = Days.today();

            text.setTypeface(regular);
            text.setTextSize(13 * dp);
            text.setColor(Ui.GREY);
            for (int i = 0; i < 7; i++) c.drawText(Days.DAY_LETTER[i], cw * (i + 0.5f), rowH * 0.6f, text);

            float rad = Math.min(cw, rowH) * 0.43f;
            for (int d = 1; d <= dim; d++) {
                int idx = offset + d - 1;
                int row = idx / 7 + 1, col = idx % 7;
                float cx = cw * (col + 0.5f), cy = rowH * (row + 0.5f);
                long day = first + d - 1;
                boolean sel = day == selected, isToday = day == today;
                if (sel) {
                    fill.setColor(Ui.ACCENT_FILL);
                    c.drawCircle(cx, cy, rad, fill);
                } else if (idx == pressed) {
                    fill.setColor(Ui.ACCENT_SOFT);
                    c.drawCircle(cx, cy, rad, fill);
                } else if (isToday) {
                    fill.setStyle(Paint.Style.STROKE);
                    fill.setStrokeWidth(1.5f * dp);
                    fill.setColor(Ui.ACCENT);
                    c.drawCircle(cx, cy, rad, fill);
                    fill.setStyle(Paint.Style.FILL);
                }
                text.setTextSize(15 * dp);
                text.setTypeface(sel || isToday ? bold : regular);
                text.setColor(sel ? 0xFFFFFFFF : isToday ? Ui.ACCENT : col >= 5 ? Ui.WEEKEND : Ui.INK);
                c.drawText(String.valueOf(d), cx, cy + 4 * dp, text);
                Ui.drawDayDots(c, fill, cx, cy + rad * 0.62f, 2.6f * dp, store.summary(day), store.hasContent(day), sel);
            }
        }

        private int cellAt(float x, float y) {
            int col = (int) (x / (getWidth() / 7f));
            int row = (int) (y / rowH) - 1;
            if (col < 0 || col > 6 || row < 0 || row > 5) return -1;
            int idx = row * 7 + col;
            int d = idx - Days.dayOfWeek(firstDay()) + 1;
            return d >= 1 && d <= Days.daysInMonth(year, month) ? idx : -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    pressed = cellAt(downX, downY);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pressed != -1 && (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop)) {
                        pressed = -1;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    float dx = ev.getX() - downX, dy = ev.getY() - downY;
                    if (Math.abs(dx) > 60 * dp && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                        shiftMonth(dx < 0 ? 1 : -1); // glisser pour changer de mois
                    } else if (pressed != -1 && pressed == cellAt(ev.getX(), ev.getY())) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        select(firstDay() + pressed - Days.dayOfWeek(firstDay()), false);
                    }
                    pressed = -1;
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    pressed = -1;
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
