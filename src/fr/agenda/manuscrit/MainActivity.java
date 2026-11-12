package fr.agenda.manuscrit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity
        implements BookPager.Listener, WeekView.Listener, PageStore.Listener, WeekStripView.Listener,
        CalendarSource.Listener, ScheduleDialog.CalendarAsker {

    private static final int MODE_DAY = 0, MODE_WEEK = 1, MODE_VRAC = 2;
    private static final int REQ_CALENDAR = 7;
    /** Décalage du jour à l'ouverture (1 = demain, depuis le widget). */
    static final String EXTRA_DAY_OFFSET = "fr.agenda.manuscrit.DAY_OFFSET";
    private int openOffset;
    /** Après un changement de langue, l'écran est reconstruit au même endroit. */
    private static int restoreMode = -1;
    private static long restoreDay, restoreVrac;
    private Runnable calGranted, calRefused;

    private ToolState tools;
    private PageStore store;
    private SharedPreferences prefs;
    private PageRenderer factory;

    private BookPager pager;
    private BookPager.Book vracBook;
    private WeekView weekView;
    private WeekStripView strip;
    private FrameLayout content;
    private LinearLayout dayContainer;
    private TextView title, todayBtn, segDay, segWeek, segVrac;
    private ToolButton moreBtn, hlBtn, eraserBtn, lassoBtn, insertBtn, undoBtn, redoBtn;
    private final ToolButton[] colorBtns = new ToolButton[ToolState.PEN_COUNT];
    private final ToolButton[] selColorBtns = new ToolButton[ToolState.PEN_COUNT];
    private final ToolButton[] widthBtns = new ToolButton[ToolState.PEN_WIDTHS.length];

    // éléments flottants
    private LinearLayout selBar, banner, snack;
    private TextView bannerText, snackText, snackA, snackB, moveBtn;
    private PopupWindow menuPopup, insertPopup, movePopup;

    private int mode = MODE_DAY;
    private long dayStart, vracStart = Geo.vrac(1);
    private long lastToday;
    private boolean wasStopped;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RectF rect = new RectF();

    private final Runnable widgetRun = new Runnable() {
        @Override
        public void run() {
            TodayWidget.updateAll(MainActivity.this);
        }
    };
    private final Runnable hideSnack = new Runnable() {
        @Override
        public void run() {
            snack.setVisibility(View.GONE);
        }
    };

    private float dp(float v) {
        return Ui.dp(this, v);
    }

    private int dpi(float v) {
        return Math.round(Ui.dp(this, v));
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Settings.init(this);
        CalendarSource.init(this);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BAR);
        w.setNavigationBarColor(Ui.BAR);
        w.getDecorView().setSystemUiVisibility(0); // icônes claires sur barres sombres

        prefs = getSharedPreferences("agenda", MODE_PRIVATE);
        tools = new ToolState(prefs);
        store = PageStore.get(this);
        vracBook = BookPager.vrac(store);
        factory = new PageRenderer();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.DESK);
        root.addView(buildTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(58)));
        root.addView(Kit.divider(this));

        content = new FrameLayout(this);
        dayContainer = new LinearLayout(this);
        dayContainer.setOrientation(LinearLayout.VERTICAL);
        pager = new BookPager(this, tools, store, this);
        dayContainer.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        dayContainer.addView(Kit.divider(this));
        dayContainer.addView(buildBottomBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(62)));
        content.addView(dayContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        weekView = new WeekView(this, store, this);
        weekView.setVisibility(View.INVISIBLE);
        content.addView(weekView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        selBar = buildSelectionBar();
        selBar.setVisibility(View.GONE);
        content.addView(selBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        banner = buildBanner();
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blp.topMargin = dpi(12);
        content.addView(banner, blp);

        snack = buildSnack();
        snack.setVisibility(View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = dpi(62 + 14);
        slp.leftMargin = slp.rightMargin = dpi(12);
        content.addView(snack, slp);

        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        store.addListener(this);
        CalendarSource.addListener(this);

        lastToday = Days.today();
        dayStart = lastToday + dayOffset(getIntent());
        pager.setBook(BookPager.AGENDA, dayStart);
        updateToolUi();
        applyWidthClass();
        if (restoreMode >= 0) { // retour au même endroit après un changement de langue
            int m = restoreMode;
            restoreMode = -1;
            dayStart = restoreDay;
            vracStart = restoreVrac;
            pager.setBook(BookPager.AGENDA, dayStart);
            if (m != MODE_DAY) setMode(m);
            updateTitle();
        }

        if (!prefs.getBoolean("helpShown2", false)) {
            prefs.edit().putBoolean("helpShown2", true).putBoolean("helpShown", true).apply();
            root.post(new Runnable() {
                @Override
                public void run() {
                    showHelp();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        tools.save();
        store.flush();
        handler.removeCallbacks(widgetRun);
        TodayWidget.updateAll(this);
    }

    @Override
    protected void onDestroy() {
        store.removeListener(this);
        CalendarSource.removeListener(this);
        weekView.dispose();
        dismissPopups();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        wasStopped = true;
    }

    private static int dayOffset(android.content.Intent i) {
        int o = i == null ? 0 : i.getIntExtra(EXTRA_DAY_OFFSET, 0);
        return o < 0 || o > 7 ? 0 : o;
    }

    /** Appui sur le widget alors que l'app est déjà lancée. */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openOffset = dayOffset(intent);
        if (!wasStopped) openOnStartDay(); // app déjà au premier plan (écran partagé) : on y va tout de suite
    }

    /**
     * À chaque ouverture de l'app (écran d'accueil, applis récentes...) : la page d'aujourd'hui ;
     * depuis le widget : la page de demain.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (wasStopped) {
            wasStopped = false;
            openOnStartDay();
        }
    }

    private void openOnStartDay() {
        dismissPopups();
        cancelInsert();
        pager.clearSelections();
        lastToday = Days.today();
        dayStart = lastToday + openOffset;
        openOffset = 0;
        if (mode == MODE_DAY) pager.setBook(BookPager.AGENDA, dayStart);
        else setMode(MODE_DAY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CalendarSource.refreshPermission(); // accordée ou retirée dans les réglages d'Android
        long t = Days.today();
        if (t != lastToday) {
            // l'app est restée ouverte pendant la nuit : on se replace sur aujourd'hui
            boolean wasOnToday = mode == MODE_DAY && pager.isVisible(lastToday);
            lastToday = t;
            if (wasOnToday) pager.setStart(t);
            else pager.refreshAll();
            strip.invalidate();
            weekView.invalidate();
        }
        updateTitle();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dismissPopups();
        applyWidthClass();
        updateTitle();
        content.post(new Runnable() {
            @Override
            public void run() {
                positionSelBar();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (tools.pendingInsert != null) cancelInsert();
        else if (pager.selectionView() != null) pager.clearSelections();
        else if (mode != MODE_DAY) setMode(MODE_DAY);
        else super.onBackPressed();
    }

    private void dismissPopups() {
        if (menuPopup != null) menuPopup.dismiss();
        if (insertPopup != null) insertPopup.dismiss();
        if (movePopup != null) movePopup.dismiss();
    }

    private boolean isWide() {
        return getResources().getConfiguration().screenWidthDp >= 720;
    }

    private void applyWidthClass() {
        int wdp = getResources().getConfiguration().screenWidthDp;
        strip.setVisibility(mode == MODE_DAY && isWide() ? View.VISIBLE : View.GONE);
        todayBtn.setVisibility(mode != MODE_VRAC && wdp >= 520 ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ barre du haut

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.BAR);
        bar.setPadding(dpi(4), 0, dpi(6), 0);

        ToolButton prev = new ToolButton(this, ToolButton.CHEV_L, L.s("Précédent", "Previous"));
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == MODE_WEEK) weekView.goPrev();
                else pager.goPrev();
            }
        });
        bar.addView(prev, new LinearLayout.LayoutParams(dpi(46), dpi(48)));
        ToolButton next = new ToolButton(this, ToolButton.CHEV_R, L.s("Suivant", "Next"));
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == MODE_WEEK) weekView.goNext();
                else pager.goNext();
            }
        });
        bar.addView(next, new LinearLayout.LayoutParams(dpi(46), dpi(48)));

        title = new TextView(this);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(Ui.INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dpi(8), dpi(6), dpi(8), dpi(6));
        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode != MODE_VRAC) pickDate();
            }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dpi(4);
        bar.addView(title, tlp);

        strip = new WeekStripView(this, store, this);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(dpi(7 * 40), dpi(50));
        stlp.rightMargin = dpi(10);
        bar.addView(strip, stlp);

        todayBtn = Kit.pill(this, L.s("Aujourd'hui", "Today"), false);
        todayBtn.setBackground(Kit.rounded(Ui.BAR, dp(18), Ui.ACCENT_LINE, Math.max(1, dpi(1))));
        todayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToday();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.rightMargin = dpi(10);
        bar.addView(todayBtn, plp);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(Kit.rounded(Ui.DESK, dp(19), Ui.BAR_LINE, Math.max(1, dpi(1))));
        seg.setPadding(dpi(3), dpi(3), dpi(3), dpi(3));
        segDay = segment(L.s("Jour", "Day"), MODE_DAY);
        segWeek = segment(L.s("Semaine", "Week"), MODE_WEEK);
        segVrac = segment(L.s("En vrac", "Notes"), MODE_VRAC);
        seg.addView(segDay);
        seg.addView(segWeek);
        seg.addView(segVrac);
        bar.addView(seg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        moreBtn = new ToolButton(this, ToolButton.MORE, L.s("Menu", "Menu"));
        moreBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dpi(44), dpi(48));
        mlp.leftMargin = dpi(4);
        bar.addView(moreBtn, mlp);
        updateSegments();
        return bar;
    }

    private TextView segment(String text, final int m) {
        TextView t = Kit.text(this, text, 14, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dpi(13), dpi(6), dpi(13), dpi(6));
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(m);
            }
        });
        return t;
    }

    private void updateSegments() {
        styleSegment(segDay, mode == MODE_DAY, Ui.ACCENT_FILL);
        styleSegment(segWeek, mode == MODE_WEEK, Ui.ACCENT_FILL);
        styleSegment(segVrac, mode == MODE_VRAC, 0xFF9A5F0C);
    }

    private void styleSegment(TextView t, boolean on, int color) {
        t.setBackground(on ? Kit.rounded(color, dp(16), 0, 0) : null);
        t.setTextColor(on ? 0xFFFFFFFF : Ui.INK);
    }

    // ------------------------------------------------------------------ barre d'outils

    private View buildBottomBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(Ui.BAR);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dpi(6), 0, dpi(6), 0);
        scroll.addView(bar, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < colorBtns.length; i++) {
            final int k = i;
            ToolButton b = new ToolButton(this, ToolButton.COLOR, L.f("Stylo, couleur %d (appui long pour la changer)", "Pen, colour %d (long-press to change it)", i + 1));
            b.setSwatch(Settings.penColors[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    InkPageView sv = pager.selectionView();
                    if (sv != null) {
                        sv.recolorSelection(Settings.penColors[k]); // une sélection change de couleur
                        return;
                    }
                    tools.colorIndex = k;
                    setTool(ToolState.PEN);
                }
            });
            b.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    choosePenColor(k);
                    return true;
                }
            });
            colorBtns[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(dpi(44), dpi(52)));
        }
        bar.addView(sep());
        for (int i = 0; i < widthBtns.length; i++) {
            final int k = i;
            ToolButton b = new ToolButton(this, ToolButton.WIDTH, L.s("Épaisseur ", "Width: ") + ToolState.widthName(i));
            b.setWidthUnits(ToolState.PEN_WIDTHS[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tools.widthIndex = k;
                    setTool(ToolState.PEN);
                }
            });
            widthBtns[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(dpi(40), dpi(52)));
        }
        bar.addView(sep());
        hlBtn = toolButton(ToolButton.HIGHLIGHTER, L.s("Surligneur", "Highlighter"), ToolState.HIGHLIGHTER);
        bar.addView(hlBtn, new LinearLayout.LayoutParams(dpi(48), dpi(52)));
        eraserBtn = toolButton(ToolButton.ERASER, L.s("Gomme", "Eraser"), ToolState.ERASER);
        bar.addView(eraserBtn, new LinearLayout.LayoutParams(dpi(48), dpi(52)));
        lassoBtn = toolButton(ToolButton.LASSO, L.s("Lasso : entourer pour déplacer, copier, reporter", "Lasso: circle to move, copy, reschedule"), ToolState.LASSO);
        bar.addView(lassoBtn, new LinearLayout.LayoutParams(dpi(48), dpi(52)));
        insertBtn = new ToolButton(this, ToolButton.INSERT, L.s("Insérer une case, un tampon, une matière, un autocollant", "Insert a check box, a stamp, a subject, a sticker"));
        insertBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInsertPanel();
            }
        });
        bar.addView(insertBtn, new LinearLayout.LayoutParams(dpi(48), dpi(52)));
        bar.addView(sep());
        undoBtn = new ToolButton(this, ToolButton.UNDO, L.s("Annuler", "Undo"));
        undoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoOn(pager.currentPage());
            }
        });
        bar.addView(undoBtn, new LinearLayout.LayoutParams(dpi(48), dpi(52)));
        redoBtn = new ToolButton(this, ToolButton.REDO, L.s("Rétablir", "Redo"));
        redoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Page p = pager.currentPage();
                if (p == null) return;
                pager.clearSelections();
                if (!store.redo(p).isEmpty()) pager.refreshAll();
                updateUndoRedo();
            }
        });
        bar.addView(redoBtn, new LinearLayout.LayoutParams(dpi(48), dpi(52)));
        return scroll;
    }

    private ToolButton toolButton(int icon, String desc, final int tool) {
        ToolButton b = new ToolButton(this, icon, desc);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTool(tools.tool == tool ? ToolState.PEN : tool);
            }
        });
        return b;
    }

    private void setTool(int tool) {
        if (tool != ToolState.LASSO) pager.clearSelections();
        tools.tool = tool;
        updateToolUi();
    }

    private View sep() {
        View v = new View(this);
        v.setBackgroundColor(Ui.BAR_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, dpi(1)), dpi(28));
        lp.leftMargin = dpi(8);
        lp.rightMargin = dpi(8);
        v.setLayoutParams(lp);
        return v;
    }

    private void updateToolUi() {
        for (int i = 0; i < colorBtns.length; i++) {
            colorBtns[i].setSelectedLook(tools.tool == ToolState.PEN && tools.colorIndex == i);
        }
        for (int i = 0; i < widthBtns.length; i++) {
            widthBtns[i].setSelectedLook(tools.tool == ToolState.PEN && tools.widthIndex == i);
        }
        hlBtn.setSelectedLook(tools.tool == ToolState.HIGHLIGHTER);
        eraserBtn.setSelectedLook(tools.tool == ToolState.ERASER);
        lassoBtn.setSelectedLook(tools.tool == ToolState.LASSO);
        insertBtn.setSelectedLook(tools.pendingInsert != null);
        updateUndoRedo();
        tools.save();
    }

    private void updateUndoRedo() {
        Page p = pager == null ? null : pager.currentPage();
        undoBtn.setEnabledLook(mode != MODE_WEEK && p != null && p.canUndo());
        redoBtn.setEnabledLook(mode != MODE_WEEK && p != null && p.canRedo());
    }

    private void undoOn(Page p) {
        if (p == null) return;
        pager.clearSelections();
        if (!store.undo(p).isEmpty()) pager.refreshAll();
        updateUndoRedo();
    }

    // ------------------------------------------------------------------ titre et modes

    private void updateTitle() {
        if (title == null || pager == null) return;
        int wdp = getResources().getConfiguration().screenWidthDp;
        if (mode == MODE_WEEK) {
            long ws = weekView.weekStart();
            String range = Days.weekRange(ws);
            title.setText(isWide() ? L.s("Semaine ", "Week ") + Days.isoWeek(ws) + "  ·  " + range : L.s("S", "W") + Days.isoWeek(ws) + " · " + range);
        } else if (mode == MODE_VRAC) {
            int n = Geo.vracIndex(pager.start());
            title.setText(pager.perScreen() == 2 ? L.s("En vrac · pages ", "Notes · pages ") + n + "–" + (n + 1) : L.s("En vrac · page ", "Notes · page ") + n);
        } else {
            long d = pager.start();
            if (pager.perScreen() == 2) title.setText(wdp >= 1100 ? Days.spreadTitle(d) : Days.spreadShort(d));
            else title.setText(wdp >= 600 ? Days.longTitle(d) : Days.shortTitle(d));
        }
        title.setContentDescription(mode == MODE_VRAC ? title.getText() : L.s("Choisir une date", "Pick a date"));
    }

    private void setMode(int m) {
        if (m == mode) return;
        dismissPopups();
        cancelInsert();
        pager.clearSelections();
        if (mode == MODE_VRAC) vracStart = pager.start();
        else if (mode == MODE_DAY) dayStart = pager.start();
        mode = m;
        if (m == MODE_WEEK) {
            weekView.setWeek(Days.weekStart(dayStart));
            weekView.setVisibility(View.VISIBLE);
            dayContainer.setVisibility(View.INVISIBLE);
        } else {
            if (m == MODE_VRAC) pager.setBook(vracBook, vracStart);
            else pager.setBook(BookPager.AGENDA, dayStart);
            dayContainer.setVisibility(View.VISIBLE);
            weekView.setVisibility(View.INVISIBLE);
        }
        updateSegments();
        applyWidthClass();
        updateTitle();
        updateUndoRedo();
    }

    private void goToday() {
        long t = Days.today();
        if (mode == MODE_WEEK) {
            weekView.setWeek(t);
            updateTitle();
        } else if (mode == MODE_VRAC) {
            dayStart = t;
            setMode(MODE_DAY);
        } else {
            pager.goTo(t);
        }
    }

    private void pickDate() {
        long cur = mode == MODE_WEEK ? weekView.weekStart() : pager.start();
        new CalendarDialog(this, store, cur, new CalendarDialog.OnPick() {
            @Override
            public void onPick(long day) {
                if (mode == MODE_WEEK) {
                    weekView.setWeek(day);
                    updateTitle();
                } else {
                    pager.goTo(day);
                }
            }
        }).show();
    }

    private void goToKey(long key) {
        if (Geo.isVrac(key)) {
            vracStart = key;
            if (mode == MODE_VRAC) pager.goTo(key);
            else setMode(MODE_VRAC);
        } else {
            dayStart = key;
            if (mode == MODE_DAY) pager.goTo(key);
            else setMode(MODE_DAY);
        }
    }

    private static String where(long key) {
        if (Geo.isVrac(key)) return L.f("dans « En vrac » (page %d)", "to Notes (page %d)", Geo.vracIndex(key));
        return L.s("au ", "to ") + (L.en ? Days.longTitle(key) : Days.longTitle(key).toLowerCase(Locale.FRENCH));
    }

    // ------------------------------------------------------------------ sélection au lasso

    private LinearLayout buildSelectionBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Kit.rounded(Ui.SURFACE, dp(24), Ui.BAR_LINE, Math.max(1, dpi(1))));
        b.setElevation(dp(8));
        b.setPadding(dpi(6), dpi(2), dpi(6), dpi(2));
        for (int i = 0; i < ToolState.PEN_COUNT; i++) {
            final int k = i;
            ToolButton c = new ToolButton(this, ToolButton.COLOR, L.f("Mettre la sélection en couleur %d", "Make the selection colour %d", i + 1));
            c.setSwatch(Settings.penColors[i]);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    InkPageView sv = pager.selectionView();
                    if (sv != null) sv.recolorSelection(Settings.penColors[k]);
                }
            });
            selColorBtns[i] = c;
            b.addView(c, new LinearLayout.LayoutParams(dpi(38), dpi(44)));
        }
        View s = new View(this);
        s.setBackgroundColor(Ui.BAR_LINE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.max(1, dpi(1)), dpi(24));
        slp.leftMargin = slp.rightMargin = dpi(6);
        b.addView(s, slp);
        TextView copy = smallButton(L.s("Copier", "Copy"), Ui.INK);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InkPageView sv = pager.selectionView();
                if (sv == null) return;
                sv.copySelection();
                setTool(ToolState.LASSO);
                showSnack(L.s("Copié. Avec le lasso, touche une page pour coller.", "Copied. With the lasso, tap a page to paste."), null, null, null, null);
            }
        });
        b.addView(copy);
        moveBtn = smallButton(L.s("Reporter…", "Move to…"), Ui.ACCENT);
        moveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoveMenu();
            }
        });
        b.addView(moveBtn);
        TextView del = smallButton(L.s("Supprimer", "Delete"), Ui.DANGER);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InkPageView sv = pager.selectionView();
                if (sv == null) return;
                final Page p = sv.page();
                sv.deleteSelection();
                pager.refreshAll();
                showSnack(L.s("Supprimé.", "Deleted."), L.s("Annuler", "Undo"), new Runnable() {
                    @Override
                    public void run() {
                        undoOn(p);
                    }
                }, null, null);
            }
        });
        b.addView(del);
        return b;
    }

    private TextView smallButton(String label, int color) {
        TextView t = Kit.text(this, label, 14, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dpi(10), dpi(10), dpi(10), dpi(10));
        Kit.ripple(t, false);
        t.setClickable(true);
        return t;
    }

    /** Place la barre d'actions au-dessus (ou au-dessous) de la sélection. */
    private void positionSelBar() {
        InkPageView v = pager.selectionView();
        if (v == null || mode == MODE_WEEK || content.getWidth() == 0) {
            selBar.setVisibility(View.GONE);
            if (movePopup != null) movePopup.dismiss();
            return;
        }
        moveBtn.setText(mode == MODE_VRAC ? L.s("Déplacer…", "Move…") : L.s("Reporter…", "Move to…"));
        v.selectionBounds(rect);
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        content.getLocationInWindow(b);
        float left = rect.left + a[0] - b[0], right = rect.right + a[0] - b[0];
        float top = rect.top + a[1] - b[1], bottom = rect.bottom + a[1] - b[1];
        selBar.setVisibility(View.VISIBLE);
        selBar.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int bw = selBar.getMeasuredWidth(), bh = selBar.getMeasuredHeight();
        float maxX = content.getWidth() - bw - dp(8), maxY = content.getHeight() - dp(62) - bh - dp(8);
        float x = Math.max(dp(8), Math.min(maxX, (left + right) / 2 - bw / 2f));
        float y = top - bh - dp(12);
        if (y < dp(8)) y = bottom + dp(12);
        y = Math.max(dp(8), Math.min(maxY, y));
        selBar.setTranslationX(x);
        selBar.setTranslationY(y);
    }

    private void showMoveMenu() {
        final InkPageView sv = pager.selectionView();
        if (sv == null || sv.page() == null) return;
        final long src = sv.page().key;
        LinearLayout box = popupBox();
        final PopupWindow pw = popup(box, 260);
        if (Geo.isVrac(src)) {
            long t = Days.today();
            box.addView(menuRow(L.s("Aujourd'hui", "Today"), null, true, moveTo(pw, t)));
            box.addView(menuRow(L.s("Demain", "Tomorrow"), null, true, moveTo(pw, t + 1)));
        } else {
            box.addView(menuRow(L.s("Au lendemain", "To the next day"), null, true, moveTo(pw, src + 1)));
            box.addView(menuRow(L.s("Au surlendemain", "In two days"), null, true, moveTo(pw, src + 2)));
            box.addView(menuRow(L.s("À lundi prochain", "To next Monday"), null, true, moveTo(pw, Days.weekStart(src) + 7)));
        }
        box.addView(menuRow(L.s("Choisir une date…", "Pick a date…"), null, true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                long from = Geo.isVrac(src) ? Days.today() : src;
                new CalendarDialog(MainActivity.this, store, from, new CalendarDialog.OnPick() {
                    @Override
                    public void onPick(long day) {
                        moveSelection(day);
                    }
                }).show();
            }
        }));
        if (!Geo.isVrac(src)) {
            box.addView(menuRow(L.s("Vers « En vrac »", "To Notes"), null, true, moveTo(pw, Geo.vrac(Math.max(1, store.lastVracPage())))));
        }
        movePopup = pw;
        pw.showAsDropDown(moveBtn, 0, dpi(4));
    }

    private Runnable moveTo(final PopupWindow pw, final long key) {
        return new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                moveSelection(key);
            }
        };
    }

    private void moveSelection(final long key) {
        InkPageView sv = pager.selectionView();
        if (sv == null || sv.page() == null) return;
        final Page src = sv.page();
        if (src.key == key) return;
        if (!sv.moveSelectionTo(store.page(key))) return;
        final Edit moveEdit = src.undo.isEmpty() ? null : src.undo.get(src.undo.size() - 1);
        pager.refreshAll();
        showSnack(L.s("Déplacé ", "Moved ") + where(key) + ".", L.s("Voir", "View"), new Runnable() {
            @Override
            public void run() {
                goToKey(key);
            }
        }, L.s("Annuler", "Undo"), new Runnable() {
            @Override
            public void run() {
                if (moveEdit != null && !src.undo.isEmpty() && src.undo.get(src.undo.size() - 1) == moveEdit) {
                    store.undo(src);
                    pager.refreshAll();
                    updateUndoRedo();
                }
            }
        });
    }

    // ------------------------------------------------------------------ bandeau, message

    private LinearLayout buildBanner() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Kit.rounded(Ui.ACCENT_FILL, dp(22), 0, 0));
        b.setElevation(dp(8));
        b.setPadding(dpi(18), dpi(4), dpi(6), dpi(4));
        bannerText = Kit.text(this, "", 15, 0xFFFFFFFF, true);
        b.addView(bannerText);
        TextView cancel = smallButton(L.s("Annuler", "Cancel"), 0xFFFFFFFF);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelInsert();
            }
        });
        b.addView(cancel);
        return b;
    }

    private void cancelInsert() {
        tools.pendingInsert = null;
        banner.setVisibility(View.GONE);
        updateToolUi();
    }

    private LinearLayout buildSnack() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Kit.rounded(0xFF2E333D, dp(14), Ui.BAR_LINE, Math.max(1, dpi(1))));
        b.setElevation(dp(10));
        b.setPadding(dpi(18), dpi(4), dpi(6), dpi(4));
        b.setMinimumHeight(dpi(48));
        snackText = Kit.text(this, "", 15, Ui.INK, false);
        snackText.setMaxWidth(dpi(420));
        b.addView(snackText);
        snackA = smallButton("", Ui.ACCENT);
        snackB = smallButton("", Ui.ACCENT);
        b.addView(snackA);
        b.addView(snackB);
        return b;
    }

    private void showSnack(String msg, String a, final Runnable ra, String bLabel, final Runnable rb) {
        snackText.setText(msg);
        setSnackAction(snackA, a, ra);
        setSnackAction(snackB, bLabel, rb);
        snack.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSnack);
        handler.postDelayed(hideSnack, 6000);
    }

    private void setSnackAction(TextView t, String label, final Runnable r) {
        if (label == null) {
            t.setVisibility(View.GONE);
            return;
        }
        t.setText(label);
        t.setVisibility(View.VISIBLE);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snack.setVisibility(View.GONE);
                r.run();
            }
        });
    }

    // ------------------------------------------------------------------ panneau "Insérer"

    private void showInsertPanel() {
        if (insertPopup != null && insertPopup.isShowing()) {
            insertPopup.dismiss();
            return;
        }
        if (mode == MODE_WEEK) return;
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dpi(16), dpi(10), dpi(16), dpi(14));

        final PopupWindow[] holder = new PopupWindow[1];

        section(v, L.s("Case à cocher", "Check box"), L.s("ou dessine un petit carré au stylet", "or draw a small square with the stylus"));
        Kit.Flow boxes = flow();
        boxes.addView(preview(PageRenderer.makeBox(tools.penColor()), L.s("une case à cocher", "a check box"), 58, 58, holder));
        v.addView(boxes);

        section(v, L.s("Tampons", "Stamps"), L.s("appui long pour modifier", "long-press to edit"));
        Kit.Flow stamps = flow();
        for (final Settings.Stamp st : Settings.stamps) {
            Obj o = factory.makeStamp(st);
            View pv = preview(o, L.f("le tampon « %s »", "the “%s” stamp", st.text), Math.min(300, (int) (o.w * 0.78f) + 18), 52, holder);
            pv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (holder[0] != null) holder[0].dismiss();
                    StampsDialog.editStamp(MainActivity.this, st, settingsChanged());
                    return true;
                }
            });
            stamps.addView(pv);
        }
        TextView newStamp = Kit.pill(this, L.s("+ Nouveau", "+ New"), false);
        newStamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (holder[0] != null) holder[0].dismiss();
                StampsDialog.editStamp(MainActivity.this, null, new Runnable() {
                    @Override
                    public void run() {
                        // le nouveau tampon est prêt à être posé
                        List<Settings.Stamp> l = Settings.stamps;
                        if (!l.isEmpty()) armInsert(factory.makeStamp(l.get(l.size() - 1)), L.f("le tampon « %s »", "the “%s” stamp", l.get(l.size() - 1).text));
                    }
                });
            }
        });
        stamps.addView(newStamp);
        TextView editStamps = Kit.pill(this, L.s("Modifier…", "Edit…"), false);
        editStamps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (holder[0] != null) holder[0].dismiss();
                StampsDialog.show(MainActivity.this, settingsChanged());
            }
        });
        stamps.addView(editStamps);
        v.addView(stamps);

        section(v, L.s("Matières", "Subjects"), null);
        Kit.Flow subs = flow();
        for (Settings.Subject s : Settings.subjects) {
            Obj o = factory.makeSubject(s);
            subs.addView(preview(o, "« " + s.name + " »", (int) (o.w * 0.78f) + 18, 48, holder));
        }
        TextView edit = Kit.pill(this, L.s("Modifier…", "Edit…"), false);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (holder[0] != null) holder[0].dismiss();
                SubjectsDialog.show(MainActivity.this, settingsChanged());
            }
        });
        subs.addView(edit);
        v.addView(subs);

        section(v, L.s("Autocollants", "Stickers"), L.s("emoji : appui long pour retirer", "emoji: long-press to remove"));
        Kit.Flow stick = flow();
        for (int k = 0; k < PageRenderer.STICKER_COUNT; k++) {
            stick.addView(preview(PageRenderer.makeSticker(k), L.f("l'autocollant « %s »", "the “%s” sticker", PageRenderer.stickerName(k)), 62, 62, holder));
        }
        for (final String e : Settings.emojis) {
            View pv = preview(PageRenderer.makeEmojiSticker(e), L.f("l'autocollant %s", "the %s sticker", e), 62, 62, holder);
            pv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (holder[0] != null) holder[0].dismiss();
                    StampsDialog.removeEmoji(MainActivity.this, e, null);
                    return true;
                }
            });
            stick.addView(pv);
        }
        TextView addEmoji = Kit.pill(this, L.s("+ Emoji", "+ Emoji"), false);
        addEmoji.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (holder[0] != null) holder[0].dismiss();
                StampsDialog.addEmoji(MainActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        List<String> l = Settings.emojis;
                        if (!l.isEmpty()) {
                            String e = l.get(l.size() - 1);
                            armInsert(PageRenderer.makeEmojiSticker(e), L.f("l'autocollant %s", "the %s sticker", e));
                        }
                    }
                });
            }
        });
        stick.addView(addEmoji);
        v.addView(stick);

        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        LinearLayout frame = popupBox();
        frame.setPadding(0, 0, 0, 0);
        frame.addView(sv);
        int width = Math.min(dpi(430), getResources().getDisplayMetrics().widthPixels - dpi(24));
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        int height = Math.min(frame.getMeasuredHeight(), maxH);
        PopupWindow pw = new PopupWindow(frame, width, height, true);
        pw.setElevation(dp(12));
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(0x00000000));
        holder[0] = pw;
        insertPopup = pw;
        int[] loc = new int[2];
        insertBtn.getLocationInWindow(loc);
        int x = Math.max(dpi(12), Math.min(getResources().getDisplayMetrics().widthPixels - width - dpi(12),
                loc[0] + insertBtn.getWidth() / 2 - width / 2));
        int y = loc[1] - height - dpi(8);
        pw.showAtLocation(content, Gravity.NO_GRAVITY, x, Math.max(dpi(8), y));
    }

    private void section(LinearLayout parent, String name, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dpi(2), dpi(12), 0, dpi(8));
        row.addView(Kit.text(this, name, 14, Ui.GREY, true));
        if (hint != null) {
            TextView h = Kit.text(this, "  ·  " + hint, 13, Ui.LIGHT_GREY, false);
            row.addView(h);
        }
        parent.addView(row);
    }

    private Kit.Flow flow() {
        return new Kit.Flow(this, dpi(8), dpi(8));
    }

    private View preview(final Obj o, final String label, int wDp, int hDp, final PopupWindow[] holder) {
        Kit.ObjPreview p = new Kit.ObjPreview(this, factory, o, wDp, hDp);
        p.setContentDescription(L.s("Poser ", "Place ") + label);
        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
                armInsert(o, label);
            }
        });
        return p;
    }

    /** Le prochain appui sur une page pose cet objet. */
    private void armInsert(Obj o, String label) {
        pager.clearSelections();
        tools.pendingInsert = o;
        bannerText.setText(L.s("Touche la page pour poser ", "Tap the page to place ") + label);
        banner.setVisibility(View.VISIBLE);
        updateToolUi();
    }

    /** Appui long sur un bouton de couleur : choisir la couleur de ce stylo. */
    private void choosePenColor(final int k) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(this, L.f("Couleur du stylo %d", "Pen colour %d", k + 1)));
        TextView hint = Kit.text(this, L.s("Ce que tu as déjà écrit garde sa couleur.", "What you've already written keeps its colour."), 14, Ui.GREY, false);
        hint.setPadding(dpi(22), 0, dpi(22), dpi(10));
        body.addView(hint);
        Kit.Flow pal = new Kit.Flow(this, dpi(10), dpi(10));
        pal.setPadding(dpi(22), 0, dpi(22), dpi(12));
        body.addView(pal);
        TextView reset = Kit.button(this, L.s("Par défaut", "Default"), false);
        TextView cancel = Kit.button(this, L.s("Annuler", "Cancel"), false);
        final android.app.Dialog d = Kit.sheet(this, body, Kit.footer(this, reset, cancel), 420);
        final Runnable apply = new Runnable() {
            @Override
            public void run() {
                colorBtns[k].setSwatch(Settings.penColors[k]);
                if (selColorBtns[k] != null) selColorBtns[k].setSwatch(Settings.penColors[k]);
                tools.colorIndex = k;
                setTool(ToolState.PEN);
                d.dismiss();
            }
        };
        for (final int col : Settings.PEN_PALETTE) {
            SubjectsDialog.Swatch sw = new SubjectsDialog.Swatch(this, col);
            sw.setSelected(col == Settings.penColors[k]);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.setPenColor(k, col);
                    apply.run();
                }
            });
            pal.addView(sw);
        }
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.setPenColor(k, Settings.DEFAULT_PEN_COLORS[k]);
                apply.run();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        d.show();
    }

    // ------------------------------------------------------------------ menu ⋮

    private LinearLayout popupBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dpi(6), 0, dpi(6));
        box.setBackground(Kit.rounded(Ui.SURFACE, dp(14), Ui.BAR_LINE, Math.max(1, dpi(1))));
        return box;
    }

    private PopupWindow popup(View v, int widthDp) {
        PopupWindow pw = new PopupWindow(v, dpi(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setElevation(dp(10));
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(0x00000000));
        return pw;
    }

    private void showMenu() {
        if (menuPopup != null && menuPopup.isShowing()) {
            menuPopup.dismiss();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        LinearLayout frame = popupBox();
        frame.setPadding(0, 0, 0, 0);
        frame.addView(sv);
        box.setPadding(0, dpi(6), 0, dpi(6));
        final PopupWindow pw = new PopupWindow(frame, dpi(320), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setElevation(dp(10));
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(0x00000000));

        if (mode != MODE_VRAC) {
            box.addView(menuRow(L.s("Aller à une date…", "Go to a date…"), null, true, new Runnable() {
                @Override
                public void run() {
                    pw.dismiss();
                    pickDate();
                }
            }));
        }
        box.addView(menuRow(L.s("Statistiques", "Statistics"), null, true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                StatsDialog.show(MainActivity.this, store);
            }
        }));
        box.addView(menuRow(L.s("Emploi du temps & Google Agenda…", "Timetable & Google Calendar…"), null, true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                ScheduleDialog.show(MainActivity.this, settingsChanged(), MainActivity.this);
            }
        }));
        box.addView(menuRow(L.s("Matières…", "Subjects…"), null, true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                SubjectsDialog.show(MainActivity.this, settingsChanged());
            }
        }));
        box.addView(menuRow(L.s("Tampons et autocollants…", "Stamps and stickers…"), null, true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                StampsDialog.show(MainActivity.this, settingsChanged());
            }
        }));
        box.addView(Kit.divider(this));
        final boolean vrac = mode == MODE_VRAC;
        box.addView(menuRow(vrac ? L.s("Fond des pages « En vrac »", "Notes page background") : L.s("Fond des pages", "Page background"), value(Settings.bgName(vrac ? Settings.bgVrac : Settings.bgAgenda)),
                true, new Runnable() {
                    @Override
                    public void run() {
                        pw.dismiss();
                        chooseBackground(vrac);
                    }
                }));
        box.addView(menuRow(L.s("Bouton du stylet", "Stylus button"), value(Settings.btnName(Settings.stylusButton)), true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                chooseStylusButton();
            }
        }));
        final Switch turn = menuSwitch(Settings.pageTurn);
        box.addView(menuRow(L.s("Page qui tourne", "Page-turn animation"), turn, true, new Runnable() {
            @Override
            public void run() {
                Settings.setPageTurn(!Settings.pageTurn);
                turn.setChecked(Settings.pageTurn);
                pager.settingsChanged();
            }
        }));
        final Switch finger = menuSwitch(tools.fingerDraw);
        box.addView(menuRow(L.s("Écrire aussi au doigt", "Also write with a finger"), finger, true, new Runnable() {
            @Override
            public void run() {
                tools.fingerDraw = !tools.fingerDraw;
                finger.setChecked(tools.fingerDraw);
                tools.save();
                Toast.makeText(MainActivity.this, tools.fingerDraw
                                ? L.s("Écriture au doigt activée : change de page en glissant avec deux doigts.", "Finger writing on: change pages by swiping with two fingers.")
                                : L.s("Le doigt sert de nouveau à tourner les pages ; seul le stylet écrit.", "Fingers turn pages again; only the stylus writes."),
                        Toast.LENGTH_LONG).show();
            }
        }));
        final Switch pressure = menuSwitch(tools.pressure);
        box.addView(menuRow(L.s("Sensibilité à la pression", "Pressure sensitivity"), pressure, true, new Runnable() {
            @Override
            public void run() {
                tools.pressure = !tools.pressure;
                pressure.setChecked(tools.pressure);
                tools.save();
            }
        }));
        box.addView(Kit.divider(this));
        Page p = pager.currentPage();
        boolean canClear = mode != MODE_WEEK && p != null && !p.items.isEmpty();
        box.addView(menuRow(L.s("Effacer cette page", "Clear this page"), null, canClear, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                confirmClear();
            }
        }));
        box.addView(menuRow("Langue · Language", value(Settings.langName(Settings.lang)), true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                chooseLanguage();
            }
        }));
        box.addView(menuRow(L.s("Aide", "Help"), null, true, new Runnable() {
            @Override
            public void run() {
                pw.dismiss();
                showHelp();
            }
        }));
        frame.measure(View.MeasureSpec.makeMeasureSpec(dpi(320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.8f);
        if (frame.getMeasuredHeight() > maxH) pw.setHeight(maxH);
        menuPopup = pw;
        pw.showAsDropDown(moreBtn, -dpi(320) + moreBtn.getWidth(), dpi(4));
    }

    private TextView value(String s) {
        return Kit.text(this, s, 14, Ui.ACCENT, false);
    }

    private Switch menuSwitch(boolean on) {
        Switch s = new Switch(this);
        s.setChecked(on);
        s.setClickable(false);
        s.setFocusable(false);
        return s;
    }

    private View menuRow(String text, View trailing, boolean enabled, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpi(18), 0, dpi(14), 0);
        row.setMinimumHeight(dpi(50));
        Kit.ripple(row, false);
        TextView t = Kit.text(this, text, 16, enabled ? Ui.INK : Ui.LIGHT_GREY, false);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) row.addView(trailing);
        if (enabled) {
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    action.run();
                }
            });
        }
        return row;
    }

    private Runnable settingsChanged() {
        return new Runnable() {
            @Override
            public void run() {
                pager.settingsChanged();
                weekView.invalidate();
                strip.invalidate();
            }
        };
    }

    private void chooseBackground(final boolean vrac) {
        int cur = vrac ? Settings.bgVrac : Settings.bgAgenda;
        new AlertDialog.Builder(this)
                .setTitle(vrac ? L.s("Fond des pages « En vrac »", "Notes page background") : L.s("Fond des pages de l'agenda", "Planner page background"))
                .setSingleChoiceItems(Settings.bgNames(), cur, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Settings.setBackground(vrac, which);
                        d.dismiss();
                        settingsChanged().run();
                    }
                })
                .setNegativeButton(L.s("Annuler", "Cancel"), null)
                .show();
    }

    /** Français, anglais ou langue de la tablette ; l'écran est reconstruit dans la nouvelle langue. */
    private void chooseLanguage() {
        String[] items = {L.s("Automatique (langue de la tablette)", "Automatic (tablet language)"), "Français", "English"};
        new AlertDialog.Builder(this)
                .setTitle("Langue · Language")
                .setSingleChoiceItems(items, Settings.lang, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == Settings.lang) return;
                        boolean wasEn = L.en;
                        Settings.setLang(which);
                        TodayWidget.updateAll(MainActivity.this);
                        if (L.en == wasEn) return; // « automatique » donne la même langue : rien à refaire
                        if (mode == MODE_DAY) dayStart = pager.start();
                        else if (mode == MODE_VRAC) vracStart = pager.start();
                        restoreMode = mode;
                        restoreDay = dayStart;
                        restoreVrac = vracStart;
                        recreate();
                    }
                })
                .setNegativeButton(L.s("Annuler", "Cancel"), null)
                .show();
    }

    private void chooseStylusButton() {
        new AlertDialog.Builder(this)
                .setTitle(L.s("Bouton du stylet", "Stylus button"))
                .setSingleChoiceItems(Settings.btnNames(), Settings.stylusButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Settings.setStylusButton(which);
                        d.dismiss();
                        String msg;
                        switch (which) {
                            case Settings.BTN_UNDO:
                                msg = L.s("Un appui sur le bouton du stylet annule le dernier geste.", "Pressing the stylus button undoes the last action.");
                                break;
                            case Settings.BTN_NONE:
                                msg = L.s("Le bouton du stylet ne fait plus rien dans l'agenda.", "The stylus button no longer does anything in the planner.");
                                break;
                            default:
                                msg = L.f("Garde le bouton enfoncé en touchant l'écran : %s.", "Hold the button down while touching the screen: %s.",
                                        Settings.btnName(which).toLowerCase(L.locale()));
                        }
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(L.s("Annuler", "Cancel"), null)
                .show();
    }

    private void confirmClear() {
        final Page p = pager.currentPage();
        if (p == null || p.items.isEmpty()) return;
        String what = Geo.isVrac(p.key) ? L.f("Tout ce qui est sur la page %d d'« En vrac »", "Everything on Notes page %d", Geo.vracIndex(p.key))
                : L.s("Tout ce qui est écrit le ", "Everything written on ")
                        + (L.en ? Days.longTitle(p.key) : Days.longTitle(p.key).toLowerCase(Locale.FRENCH));
        new AlertDialog.Builder(this)
                .setTitle(L.s("Effacer cette page ?", "Clear this page?"))
                .setMessage(what + L.s(" sera effacé. Tu pourras encore revenir en arrière avec le bouton Annuler.",
                        " will be erased. You can still go back with the Undo button."))
                .setNegativeButton(L.s("Garder", "Keep"), null)
                .setPositiveButton(L.s("Effacer", "Clear"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        pager.clearSelections();
                        Edit e = new Edit();
                        e.remove(p, new ArrayList<>(p.items));
                        store.commit(e);
                        pager.refreshAll();
                        updateUndoRedo();
                    }
                })
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(L.s("Ton agenda manuscrit", "Your handwritten planner"))
                .setMessage(L.s("• Écris avec ton stylet. Glisse au doigt pour changer de jour (ou les flèches en haut). "
                                + "À l'horizontale : deux jours côte à côte.\n\n"
                                + "• Cases à cocher : dessine un petit carré (même de travers, ou en 2 à 4 traits), il devient "
                                + "une case. Touche-la (doigt ou stylet) ou dessine une coche dedans pour la cocher.\n\n"
                                + "• Lasso (boucle en pointillés) : entoure ce que tu veux, puis déplace-le, agrandis-le avec la "
                                + "poignée bleue, change sa couleur, copie-le ou reporte-le à un autre jour.\n\n"
                                + "• Insérer (carré avec +) : tampons, pastilles de matières, autocollants. "
                                + "Choisis, puis touche la page à l'endroit voulu. « + Nouveau » crée ton propre tampon, "
                                + "« + Emoji » ajoute un autocollant ; appui long pour modifier ou retirer.\n\n"
                                + "• Appui long sur une couleur de la barre : change la couleur de ce stylo.\n\n"
                                + "• Pince à deux doigts pour zoomer. Quand tu écris en bas, la page s'allonge : fais-la "
                                + "défiler au doigt.\n\n"
                                + "• « En vrac » : un carnet à part, sans dates, pour les idées et les choses à faire un jour.\n\n"
                                + "• Menu ⋮ : emploi du temps et Google Agenda, matières, tampons, fond des pages, bouton du "
                                + "stylet, statistiques, animation de page, langue.\n\n"
                                + "• Google Agenda : tes événements s'affichent en haut de chaque jour, à côté des cours "
                                + "(anneau = événement Google, point plein = cours).\n\n"
                                + "• Tout est enregistré automatiquement. Ajoute le widget « Agenda » sur ton écran d'accueil : "
                                + "il montre la page de demain, pour voir ce qu'il reste à préparer.",
                        "• Write with your stylus. Swipe with a finger to change days (or use the arrows at the top). "
                                + "In landscape: two days side by side.\n\n"
                                + "• Check boxes: draw a small square (even crooked, or in 2 to 4 strokes) and it becomes "
                                + "a check box. Tap it (finger or stylus) or draw a tick inside to check it.\n\n"
                                + "• Lasso (dotted loop): circle what you want, then move it, enlarge it with the blue "
                                + "handle, change its colour, copy it or move it to another day.\n\n"
                                + "• Insert (square with +): stamps, subject tags, stickers. Pick one, then tap the page "
                                + "where you want it. “+ New” creates your own stamp, “+ Emoji” adds a sticker; "
                                + "long-press to edit or remove.\n\n"
                                + "• Long-press a colour in the toolbar to change that pen's colour.\n\n"
                                + "• Pinch with two fingers to zoom. When you write at the bottom, the page gets longer: "
                                + "scroll it with a finger.\n\n"
                                + "• Notes: a separate notebook with no dates, for ideas and someday to-dos.\n\n"
                                + "• Menu ⋮: timetable and Google Calendar, subjects, stamps, page background, stylus "
                                + "button, statistics, page-turn animation, language.\n\n"
                                + "• Google Calendar: your events appear at the top of each day, next to your classes "
                                + "(ring = Google event, solid dot = class).\n\n"
                                + "• Everything is saved automatically. Add the “Agenda” widget to your home screen: "
                                + "it shows tomorrow's page, so you can see what's left to prepare."))
                .setPositiveButton(L.s("C'est parti", "Let's go"), null)
                .show();
    }

    // ------------------------------------------------------------------ Google Agenda

    @Override
    public void onCalendarChanged() {
        pager.refreshAll();
        weekView.invalidate();
        handler.removeCallbacks(widgetRun);
        handler.postDelayed(widgetRun, 1500);
    }

    @Override
    public void askCalendar(Runnable granted, Runnable refused) {
        if (android.os.Build.VERSION.SDK_INT < 23 || CalendarSource.hasPermission(this)) {
            granted.run();
            return;
        }
        calGranted = granted;
        calRefused = refused;
        requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, REQ_CALENDAR);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_CALENDAR) return;
        Runnable ok = calGranted, no = calRefused;
        calGranted = calRefused = null;
        boolean granted = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted) {
            if (ok != null) ok.run();
            return;
        }
        if (no != null) no.run();
        if (android.os.Build.VERSION.SDK_INT >= 23
                && !shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CALENDAR)) {
            // refus définitif : Android ne reposera plus la question, il faut passer par ses réglages
            new AlertDialog.Builder(this)
                    .setTitle(L.s("Accès à l'agenda refusé", "Calendar access denied"))
                    .setMessage(L.s("Pour afficher Google Agenda, autorise « Agenda » dans les autorisations de l'app, "
                                    + "puis réactive l'interrupteur.",
                            "To show Google Calendar, allow “Calendar” in the app's permissions, then turn the switch back on."))
                    .setPositiveButton(L.s("Ouvrir les réglages", "Open settings"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            try {
                                startActivity(new android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (RuntimeException ignored) {
                            }
                        }
                    })
                    .setNegativeButton(L.s("Plus tard", "Later"), null)
                    .show();
        } else {
            Toast.makeText(this, L.s("Sans accès à l'agenda, les événements Google ne peuvent pas s'afficher.", "Without calendar access, Google events can't be shown."), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ rappels de la pager

    @Override
    public void onPageShown(long first) {
        if (mode == MODE_VRAC) {
            vracStart = first;
        } else if (mode == MODE_DAY) {
            dayStart = first;
            if (strip != null) strip.setDay(first, pager.perScreen());
        }
        updateTitle();
        if (undoBtn != null) updateUndoRedo();
        if (selBar != null) positionSelBar();
    }

    @Override
    public void onPageEdited(Page p) {
        updateUndoRedo();
    }

    @Override
    public void onSelectionChanged(InkPageView v) {
        positionSelBar();
    }

    @Override
    public void onStylusButtonUndo(InkPageView v) {
        undoOn(v.page());
    }

    @Override
    public void onInsertPlaced() {
        banner.setVisibility(View.GONE);
        updateToolUi();
    }

    @Override
    public void onHint(int hint) {
        String k = hint == InkPageView.HINT_BOX ? "hintBox" : "hintExt";
        if (prefs.getBoolean(k, false)) return;
        prefs.edit().putBoolean(k, true).apply();
        Toast.makeText(this, hint == InkPageView.HINT_BOX
                        ? L.s("Ton carré est devenu une case à cocher : touche-la pour la cocher. (Annuler redonne ton dessin.)", "Your square became a check box: tap it to check it. (Undo brings your drawing back.)")
                        : L.s("La page s'est allongée : fais-la défiler vers le haut avec le doigt pour écrire plus bas.", "The page got longer: scroll it up with a finger to write further down."),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPageChanged(long key) {
        if (strip != null) strip.invalidate();
        if (key == PageStore.ALL || key == TodayWidget.shownDay()) {
            handler.removeCallbacks(widgetRun);
            handler.postDelayed(widgetRun, 1500);
        }
    }

    @Override
    public void onOpenDay(long day) {
        dayStart = day;
        setMode(MODE_DAY);
    }

    @Override
    public void onWeekChanged(long weekStart) {
        updateTitle();
    }

    @Override
    public void onPick(long day) {
        pager.goTo(day);
    }
}
