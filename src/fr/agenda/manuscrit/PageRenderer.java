package fr.agenda.manuscrit;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Dessine une page (papier, en-tête, fond ligné / Seyès / carreaux / points, emploi du temps)
 * et ses éléments (traits, cases, tampons, matières, autocollants), en unités de page.
 * Une instance par thread (les Paint ne sont pas partageables).
 */
final class PageRenderer {
    static final float W = Geo.W;
    static final float H = Geo.H;
    static final float HEADER_LINE = Geo.HEADER_LINE;
    static final float FIRST_RULE = Geo.FIRST_RULE;
    static final float RULE_STEP = Geo.RULE_STEP;
    static final float MARGIN_X = Geo.MARGIN_X;

    static final int VRAC_ACCENT = 0xFFB8730F;
    private static final int RULE = 0xFFD6E1EF;
    private static final int MARGIN = 0xFFF0BDBD;
    private static final int SEYES_MAIN = 0xFFA4B3E6;
    private static final int SEYES_FINE = 0xFFDCE3F7;
    private static final int SEYES_VERT = 0xFFC5CFF0;
    private static final int GRID = 0xFFDDE4EF;
    private static final int DOT = 0xFFBCC6D6;
    private static final float SMALL_STEP = 34f;

    /** Tampons d'avant la 2.5 (objets sans texte : le n° du tampon renvoie ici). */
    static final String[] STAMP_TEXT = {"DS", "TP", "RENDU", "EXAM", "IMPORTANT"};
    static final String[] STAMP_TEXT_EN = {"TEST", "LAB", "DUE", "EXAM", "IMPORTANT"};
    static final int[] STAMP_COLOR = {0xFFD62F3C, 0xFF16915A, 0xFFE07B00, 0xFF7B3FC4, 0xFFD62F3C};
    /** Tampon : kind = 1 pour un tampon plein (texte blanc sur fond de couleur), 0 pour un contour. */
    static final int STAMP_OUTLINE = 0, STAMP_FILLED = 1;
    /** Autocollant emoji : kind = EMOJI, l'emoji est dans le texte de l'objet. */
    static final int EMOJI = -1;
    static final int STICKER_COUNT = 8;

    /** Nom d'un autocollant dessiné (pour l'accessibilité). */
    static String stickerName(int k) {
        switch (k) {
            case 0: return L.s("étoile", "star");
            case 1: return L.s("cœur", "heart");
            case 2: return L.s("éclair", "lightning");
            case 3: return L.s("soleil", "sun");
            case 4: return L.s("sourire", "smiley");
            case 5: return L.s("idée", "idea");
            case 6: return L.s("horloge", "clock");
            default: return L.s("flamme", "flame");
        }
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ostroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emoji = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffColorFilter emojiShadow = new PorterDuffColorFilter(0x2E000000, PorterDuff.Mode.SRC_IN);
    private final Path path = new Path();
    private final Path path2 = new Path();
    private final RectF tmp = new RectF();
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private float[] dots = new float[0];

    PageRenderer() {
        line.setStyle(Paint.Style.STROKE);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        hl.setStyle(Paint.Style.STROKE);
        hl.setStrokeCap(Paint.Cap.ROUND);
        hl.setStrokeJoin(Paint.Join.ROUND);
        ostroke.setStyle(Paint.Style.STROKE);
        ostroke.setStrokeCap(Paint.Cap.ROUND);
        ostroke.setStrokeJoin(Paint.Join.ROUND);
        emoji.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.6f);
    }

    // ------------------------------------------------------------------ papier

    /**
     * @param height  hauteur de la page (H, ou plus si elle a été allongée)
     * @param bg      fond (Settings.BG_...)
     * @param schedule afficher dans l'en-tête les cours et les événements Google du jour
     */
    void drawPaper(Canvas c, long key, long today, float height, int bg, boolean schedule) {
        fill.setColor(Ui.PAPER);
        c.drawRect(0, 0, W, height, fill);
        if (Geo.isVrac(key)) drawVracHeader(c, key);
        else drawDayHeader(c, key, today);
        drawPattern(c, bg, height);
        if (schedule && !Geo.isVrac(key)) drawSchedule(c, key); // par-dessus les lignes
    }

    private void drawDayHeader(Canvas c, long day, long today) {
        int dow = Days.dayOfWeek(day);
        boolean isToday = day == today;
        boolean weekend = dow >= 5;
        int headColor = isToday ? Ui.PAPER_ACCENT : (weekend ? Ui.PAPER_WEEKEND : Ui.PAPER_INK);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(bold);
        text.setTextSize(66f);
        text.setColor(headColor);
        c.drawText(Days.dayName(dow), 60f, 98f, text);

        text.setTypeface(regular);
        text.setTextSize(34f);
        text.setColor(Ui.PAPER_GREY);
        c.drawText(Days.dateLine(day), 62f, 148f, text);

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(medium);
        text.setTextSize(28f);
        text.setColor(Ui.PAPER_GREY);
        c.drawText(L.s("Semaine ", "Week ") + Days.isoWeek(day), W - 60f, 96f, text);

        if (day == today + 1) {
            // demain : pastille claire, contour bleu
            text.setTypeface(bold);
            text.setTextSize(26f);
            String s = L.s("Demain", "Tomorrow");
            float tw = text.measureText(s);
            tmp.set(W - 60f - tw - 36f, 117f, W - 60f, 157f);
            fill.setColor(0xFFE8EFFC);
            c.drawRoundRect(tmp, 20f, 20f, fill);
            line.setColor(Ui.PAPER_ACCENT);
            line.setStrokeWidth(2.4f);
            c.drawRoundRect(tmp, 20f, 20f, line);
            text.setColor(Ui.PAPER_ACCENT);
            c.drawText(s, W - 60f - 18f, 146f, text);
        }
        if (isToday) {
            text.setTypeface(bold);
            text.setTextSize(26f);
            String s = L.s("Aujourd'hui", "Today");
            float tw = text.measureText(s);
            tmp.set(W - 60f - tw - 36f, 116f, W - 60f, 158f);
            fill.setColor(Ui.PAPER_ACCENT);
            c.drawRoundRect(tmp, 21f, 21f, fill);
            text.setColor(0xFFFFFFFF);
            c.drawText(s, W - 60f - 18f, 146f, text);
        }

        line.setColor(isToday ? Ui.PAPER_ACCENT : 0x552A2C33);
        line.setStrokeWidth(isToday ? 3f : 2.2f);
        c.drawLine(40f, HEADER_LINE, W - 40f, HEADER_LINE, line);
    }

    /**
     * Créneaux de cours du jour, en petites pastilles discrètes sous l'en-tête.
     * Format le plus complet qui tient sur une ligne : "8h–10h Maths · B204", puis "8h–10h Maths",
     * puis "8h Maths" ; s'il en reste trop, "+N".
     */
    /** Pastilles du haut de page : cours de l'emploi du temps et événements Google Agenda du jour. */
    private void drawSchedule(Canvas c, long day) {
        List<Settings.Slot> slots = Settings.showSchedule ? Settings.slotsFor(Days.dayOfWeek(day))
                : java.util.Collections.<Settings.Slot>emptyList();
        List<CalEvents.DayEv> evs = Settings.google ? CalendarSource.eventsFor(day)
                : java.util.Collections.<CalEvents.DayEv>emptyList();
        if (slots.isEmpty() && evs.isEmpty()) return;
        List<CalEvents.Chip> chips = CalEvents.chips(slots, evs, Settings.subjects);
        int n = chips.size();
        if (n == 0) return;
        // tout est affiché, en entier : quand une ligne est pleine, on passe à la ligne suivante
        // (chaque ligne de pastilles prend la place d'une ligne d'écriture)
        float left = 60f, maxX = W - 60f, hgt = 32f, gapX = 10f;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(medium);
        text.setTextSize(19f);
        float x = left;
        int row = 0;
        for (int i = 0; i < n; i++) {
            CalEvents.Chip ch = chips.get(i);
            String label = CalEvents.fullLabel(ch, 200);
            float w = chipWidth(ch, label);
            for (int max = Math.min(ch.title.length(), 120); w > maxX - left && max > 6; max -= 4) {
                label = CalEvents.fullLabel(ch, max); // titre plus long qu'une ligne entière : coupé
                w = chipWidth(ch, label);
            }
            if (x > left && x + w > maxX) {
                row++;
                x = left;
            }
            float top = row == 0 ? 189f : Geo.FIRST_RULE + (row - 1) * Geo.RULE_STEP + (Geo.RULE_STEP - hgt) / 2f;
            drawChip(c, ch, label, x, top, w, hgt);
            x += w + gapX;
        }
    }

    private void drawChip(Canvas c, CalEvents.Chip ch, String label, float x, float top, float w, float hgt) {
        int col = ch.color != 0 ? ch.color : 0xFF9AA1AD;
        tmp.set(x, top, x + w, top + hgt);
        fill.setColor(Ui.PAPER);
        c.drawRoundRect(tmp, hgt / 2, hgt / 2, fill);
        fill.setColor(Ui.withAlpha(col, ch.allDay ? 0x30 : 0x1F));
        c.drawRoundRect(tmp, hgt / 2, hgt / 2, fill);
        float tx;
        if (ch.allDay) {
            // toute la journée : pas d'heure, un petit trait de couleur
            fill.setColor(col);
            tmp.set(x + 11f, top + 9f, x + 15f, top + hgt - 9f);
            c.drawRoundRect(tmp, 2f, 2f, fill);
            tx = x + 23f;
        } else if (ch.google) {
            // événement Google : un anneau (les cours de l'emploi du temps ont un point plein)
            ring.setColor(col);
            c.drawCircle(x + 16f, top + hgt / 2, 4.6f, ring);
            tx = x + 28f;
        } else {
            fill.setColor(col);
            c.drawCircle(x + 16f, top + hgt / 2, 5.5f, fill);
            tx = x + 28f;
        }
        text.setColor(0xFF3A3D46);
        c.drawText(label, tx, top + hgt / 2 + 6.5f, text);
    }

    private float chipWidth(CalEvents.Chip ch, String label) {
        return (ch.allDay ? 23f : 30f) + text.measureText(label) + 14f;
    }

    private void drawVracHeader(Canvas c, long key) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(bold);
        text.setTextSize(58f);
        text.setColor(VRAC_ACCENT);
        c.drawText(L.s("En vrac", "Notes"), 60f, 100f, text);

        text.setTypeface(regular);
        text.setTextSize(28f);
        text.setColor(Ui.PAPER_GREY);
        c.drawText(L.s("Idées, choses à faire un jour, brouillon…", "Ideas, someday to-dos, rough notes…"), 62f, 148f, text);

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTypeface(medium);
        text.setTextSize(28f);
        c.drawText(L.s("Page ", "Page ") + Geo.vracIndex(key), W - 60f, 96f, text);

        line.setColor(Ui.withAlpha(VRAC_ACCENT, 0x88));
        line.setStrokeWidth(2.6f);
        c.drawLine(40f, HEADER_LINE, W - 40f, HEADER_LINE, line);
    }

    private void drawPattern(Canvas c, int bg, float height) {
        float bottom = height - 24f;
        switch (bg) {
            case Settings.BG_SEYES: {
                float fine = RULE_STEP / 4f;
                line.setColor(SEYES_FINE);
                line.setStrokeWidth(1.0f);
                for (float y = FIRST_RULE - 3 * fine; y < bottom; y += fine) {
                    if (Math.abs((y - FIRST_RULE) % RULE_STEP) < 0.5f) continue;
                    c.drawLine(30f, y, W - 30f, y, line);
                }
                line.setColor(SEYES_VERT);
                line.setStrokeWidth(1.1f);
                for (float x = MARGIN_X + RULE_STEP; x < W - 30f; x += RULE_STEP) {
                    c.drawLine(x, FIRST_RULE - 3 * fine, x, bottom, line);
                }
                line.setColor(SEYES_MAIN);
                line.setStrokeWidth(1.7f);
                for (float y = FIRST_RULE; y < bottom; y += RULE_STEP) c.drawLine(30f, y, W - 30f, y, line);
                drawMargin(c, height);
                break;
            }
            case Settings.BG_GRID: {
                line.setColor(GRID);
                line.setStrokeWidth(1.2f);
                float top = HEADER_LINE + 20f;
                for (float y = top; y < bottom; y += SMALL_STEP) c.drawLine(30f, y, W - 30f, y, line);
                for (float x = 32f; x < W - 29f; x += SMALL_STEP) c.drawLine(x, top, x, bottom, line);
                break;
            }
            case Settings.BG_DOTS: {
                float top = HEADER_LINE + 26f;
                int cols = (int) ((W - 64f) / SMALL_STEP) + 1, rows = (int) ((bottom - top) / SMALL_STEP) + 1;
                int need = cols * rows * 2;
                if (dots.length < need) dots = new float[need];
                int k = 0;
                for (int r = 0; r < rows; r++) {
                    for (int q = 0; q < cols; q++) {
                        dots[k++] = 32f + q * SMALL_STEP;
                        dots[k++] = top + r * SMALL_STEP;
                    }
                }
                line.setColor(DOT);
                line.setStrokeWidth(3.8f);
                line.setStrokeCap(Paint.Cap.ROUND);
                c.drawPoints(dots, 0, k, line);
                line.setStrokeCap(Paint.Cap.BUTT);
                break;
            }
            case Settings.BG_BLANK:
                break;
            default: { // ligné
                line.setColor(RULE);
                line.setStrokeWidth(1.7f);
                for (float y = FIRST_RULE; y < bottom; y += RULE_STEP) c.drawLine(30f, y, W - 30f, y, line);
                drawMargin(c, height);
            }
        }
    }

    private void drawMargin(Canvas c, float height) {
        line.setColor(MARGIN);
        line.setStrokeWidth(1.7f);
        c.drawLine(MARGIN_X, HEADER_LINE + 2f, MARGIN_X, height, line);
    }

    // ------------------------------------------------------------------ éléments

    /**
     * Dessine les éléments : surligneur, puis objets, puis encre (l'encre reste lisible par-dessus).
     * @param exclude éléments à ne pas dessiner (sélection en cours de déplacement), ou null
     * @param vl      zone visible (unités de page) pour ignorer ce qui est hors champ ; NaN = tout
     */
    void drawItems(Canvas c, List<Item> items, IdentityHashMap<Item, Boolean> exclude, boolean fast, float ppu,
                   float vl, float vt, float vr, float vb) {
        boolean cull = !Float.isNaN(vl);
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0, n = items.size(); i < n; i++) {
                Item it = items.get(i);
                int kind = it instanceof Obj ? 1 : (((Stroke) it).isHighlighter() ? 0 : 2);
                if (kind != pass) continue;
                if (exclude != null && exclude.containsKey(it)) continue;
                if (cull && (it.right() < vl || it.left() > vr || it.bottom() < vt || it.top() > vb)) continue;
                if (kind == 1) drawObj(c, (Obj) it);
                else if (kind == 0) drawHighlighter(c, (Stroke) it);
                else if (fast) drawPenFast(c, (Stroke) it, ppu);
                else drawPen(c, (Stroke) it, ppu);
            }
        }
    }

    void drawItems(Canvas c, List<Item> items, boolean fast, float ppu) {
        drawItems(c, items, null, fast, ppu, Float.NaN, 0, 0, 0);
    }

    void drawItem(Canvas c, Item it, float ppu) {
        if (it instanceof Obj) drawObj(c, (Obj) it);
        else if (((Stroke) it).isHighlighter()) drawHighlighter(c, (Stroke) it);
        else drawPen(c, (Stroke) it, ppu);
    }

    /** Trait à épaisseur variable (pression) lissé par des courbes quadratiques. */
    void drawPen(Canvas c, Stroke s, float ppu) {
        int n = s.n;
        if (n == 0) return;
        float[] a = s.pts;
        float bw = s.width;
        pen.setColor(s.color);
        if (n == 1) {
            fill.setColor(s.color);
            c.drawCircle(a[0], a[1], Math.max(bw * a[2], 0.6f) * 0.5f, fill);
            return;
        }
        float x0 = a[0], y0 = a[1], w0 = bw * a[2];
        float mx = (a[0] + a[3]) * 0.5f, my = (a[1] + a[4]) * 0.5f, mw = bw * (a[2] + a[5]) * 0.5f;
        seg(c, x0, y0, w0, mx, my, mw);
        for (int k = 1; k < n - 1; k++) {
            int i = k * 3;
            float nx = (a[i] + a[i + 3]) * 0.5f, ny = (a[i + 1] + a[i + 4]) * 0.5f;
            float nw = bw * (a[i + 2] + a[i + 5]) * 0.5f;
            quad(c, mx, my, mw, a[i], a[i + 1], nx, ny, nw, ppu);
            mx = nx;
            my = ny;
            mw = nw;
        }
        int last = (n - 1) * 3;
        seg(c, mx, my, mw, a[last], a[last + 1], bw * a[last + 2]);
    }

    private void seg(Canvas c, float x0, float y0, float w0, float x1, float y1, float w1) {
        pen.setStrokeWidth((w0 + w1) * 0.5f);
        c.drawLine(x0, y0, x1, y1, pen);
    }

    private void quad(Canvas c, float x0, float y0, float w0, float cx, float cy,
                      float x1, float y1, float w1, float ppu) {
        float len = dist(x0, y0, cx, cy) + dist(cx, cy, x1, y1);
        int k = (int) Math.ceil(len * ppu / 3f);
        if (k < 1) k = 1;
        else if (k > 24) k = 24;
        float px = x0, py = y0, pw = w0;
        for (int i = 1; i <= k; i++) {
            float t = i / (float) k, u = 1f - t;
            float x = u * u * x0 + 2f * u * t * cx + t * t * x1;
            float y = u * u * y0 + 2f * u * t * cy + t * t * y1;
            float w = w0 + (w1 - w0) * t;
            pen.setStrokeWidth((pw + w) * 0.5f);
            c.drawLine(px, py, x, y, pen);
            px = x;
            py = y;
            pw = w;
        }
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Version rapide (miniatures) : un seul chemin à épaisseur moyenne. */
    void drawPenFast(Canvas c, Stroke s, float ppu) {
        int n = s.n;
        if (n == 0) return;
        float[] a = s.pts;
        float sum = 0;
        for (int i = 0; i < n; i++) sum += a[i * 3 + 2];
        float w = Math.max(s.width * sum / n, 1.15f / ppu);
        if (n == 1) {
            fill.setColor(s.color);
            c.drawCircle(a[0], a[1], w * 0.5f, fill);
            return;
        }
        buildPath(s);
        pen.setColor(s.color);
        pen.setStrokeWidth(w);
        c.drawPath(path, pen);
    }

    void drawHighlighter(Canvas c, Stroke s) {
        if (s.n == 0) return;
        hl.setColor(s.color);
        hl.setStrokeWidth(s.width);
        if (s.n == 1) {
            fill.setColor(s.color);
            c.drawCircle(s.pts[0], s.pts[1], s.width * 0.5f, fill);
            return;
        }
        buildPath(s);
        c.drawPath(path, hl);
    }

    private void buildPath(Stroke s) {
        float[] a = s.pts;
        int n = s.n;
        path.rewind();
        path.moveTo(a[0], a[1]);
        float mx = (a[0] + a[3]) * 0.5f, my = (a[1] + a[4]) * 0.5f;
        path.lineTo(mx, my);
        for (int k = 1; k < n - 1; k++) {
            int i = k * 3;
            float nx = (a[i] + a[i + 3]) * 0.5f, ny = (a[i + 1] + a[i + 4]) * 0.5f;
            path.quadTo(a[i], a[i + 1], nx, ny);
        }
        int last = (n - 1) * 3;
        path.lineTo(a[last], a[last + 1]);
    }

    // ------------------------------------------------------------------ objets

    void drawObj(Canvas c, Obj o) {
        switch (o.type) {
            case Obj.BOX:
                drawBox(c, o);
                break;
            case Obj.STAMP:
                drawStamp(c, o);
                break;
            case Obj.SUBJECT:
                drawSubject(c, o);
                break;
            case Obj.STICKER:
                drawSticker(c, o);
                break;
        }
    }

    private void drawBox(Canvas c, Obj o) {
        float s = Math.min(o.w, o.h);
        float sw = s * 0.075f, r = s * 0.2f;
        tmp.set(o.x - s / 2 + sw / 2, o.y - s / 2 + sw / 2, o.x + s / 2 - sw / 2, o.y + s / 2 - sw / 2);
        if (o.checked) {
            fill.setColor(Ui.withAlpha(o.color, 0x26));
            c.drawRoundRect(tmp, r, r, fill);
        }
        ostroke.setColor(o.color);
        ostroke.setStrokeWidth(sw);
        c.drawRoundRect(tmp, r, r, ostroke);
        if (o.checked) {
            path.rewind();
            path.moveTo(o.x - s * 0.25f, o.y + s * 0.01f);
            path.lineTo(o.x - s * 0.07f, o.y + s * 0.2f);
            path.lineTo(o.x + s * 0.27f, o.y - s * 0.22f);
            ostroke.setStrokeWidth(s * 0.12f);
            c.drawPath(path, ostroke);
        }
    }

    private void drawStamp(Canvas c, Obj o) {
        String label;
        int col;
        boolean filled;
        if (o.text.isEmpty()) { // tampon d'avant la 2.5
            int k = Math.max(0, Math.min(STAMP_TEXT.length - 1, o.kind));
            label = (L.en ? STAMP_TEXT_EN : STAMP_TEXT)[k];
            col = STAMP_COLOR[k];
            filled = k == 4;
        } else {
            label = o.text;
            col = o.color;
            filled = o.kind == STAMP_FILLED;
        }
        float sw = o.h * 0.07f, r = o.h * 0.22f;
        tmp.set(o.left() + sw / 2, o.top() + sw / 2, o.right() - sw / 2, o.bottom() - sw / 2);
        fill.setColor(filled ? col : Ui.withAlpha(col, 0x16));
        c.drawRoundRect(tmp, r, r, fill);
        if (!filled) {
            ostroke.setColor(col);
            ostroke.setStrokeWidth(sw);
            c.drawRoundRect(tmp, r, r, ostroke);
        }
        text.setTypeface(bold);
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0.08f);
        float ts = o.h * 0.5f;
        text.setTextSize(ts);
        float tw = text.measureText(label), avail = o.w - o.h * 0.6f;
        if (tw > avail && tw > 0) {
            ts *= avail / tw;
            text.setTextSize(ts);
        }
        text.setColor(filled ? 0xFFFFFFFF : col);
        c.drawText(label, o.x, o.y + ts * 0.36f, text);
        text.setLetterSpacing(0f);
    }

    private void drawSubject(Canvas c, Obj o) {
        Settings.Subject sub = Settings.subject(o.kind);
        String name = sub != null ? sub.name : o.text;
        int col = sub != null ? sub.color : o.color;
        tmp.set(o.left(), o.top(), o.right(), o.bottom());
        fill.setColor(Ui.withAlpha(col, 0x26));
        c.drawRoundRect(tmp, o.h / 2, o.h / 2, fill);
        fill.setColor(col);
        c.drawCircle(o.left() + o.h * 0.5f, o.y, o.h * 0.17f, fill);
        text.setTypeface(medium);
        text.setTextAlign(Paint.Align.LEFT);
        float ts = o.h * 0.5f;
        text.setTextSize(ts);
        float avail = o.w - o.h * 1.15f, tw = text.measureText(name);
        if (tw > avail && tw > 0) {
            ts *= avail / tw;
            text.setTextSize(ts);
        }
        text.setColor(Ui.darker(col, 0.72f));
        c.drawText(name, o.left() + o.h * 0.85f, o.y + ts * 0.36f, text);
    }

    private void drawSticker(Canvas c, Obj o) {
        if (!o.text.isEmpty()) {
            drawEmoji(c, o);
            return;
        }
        float s = Math.min(o.w, o.h) / 100f;
        int save = c.save();
        c.translate(o.x, o.y);
        c.scale(s, s);
        c.save();
        c.translate(2.5f, 3.5f);
        sticker(c, o.kind, true);
        c.restore();
        sticker(c, o.kind, false);
        c.restoreToCount(save);
    }

    /** Autocollant emoji (ou quelques caractères), avec la même petite ombre que les autres. */
    private void drawEmoji(Canvas c, Obj o) {
        float size = Math.min(o.w, o.h) * 0.8f;
        emoji.setTextSize(size);
        float tw = emoji.measureText(o.text);
        if (tw > o.w * 0.95f && tw > 0) { // plusieurs caractères : on réduit pour tenir dans la case
            size *= o.w * 0.95f / tw;
            emoji.setTextSize(size);
        }
        Paint.FontMetrics fm = emoji.getFontMetrics();
        float base = o.y - (fm.ascent + fm.descent) / 2f;
        float k = Math.min(o.w, o.h) / 100f;
        emoji.setColor(0xFF000000);
        emoji.setColorFilter(emojiShadow);
        c.drawText(o.text, o.x + 2.5f * k, base + 3.5f * k, emoji);
        emoji.setColorFilter(null);
        emoji.setColor(0xFF2A2C33);
        c.drawText(o.text, o.x, base, emoji);
    }

    private void fillPath(Canvas c, Path p, int color) {
        fill.setColor(color);
        c.drawPath(p, fill);
    }

    private void strokePath(Canvas c, Path p, int color, float w) {
        ostroke.setColor(color);
        ostroke.setStrokeWidth(w);
        c.drawPath(p, ostroke);
    }

    /** Autocollants dessinés dans une boîte de 100 x 100 centrée sur 0. */
    private void sticker(Canvas c, int kind, boolean sh) {
        final int S = 0x26000000;
        switch (kind) {
            case 0: { // étoile
                path.rewind();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float r = i % 2 == 0 ? 47f : 20f;
                    float x = (float) (r * Math.cos(a)), y = (float) (r * Math.sin(a)) + 3f;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                fillPath(c, path, sh ? S : 0xFFFFC83D);
                strokePath(c, path, sh ? S : 0xFFE39B00, 4.5f);
                if (!sh) {
                    fill.setColor(0x80FFFFFF);
                    c.drawCircle(-9f, -9f, 5f, fill);
                }
                break;
            }
            case 1: { // cœur
                path.rewind();
                path.moveTo(0f, 40f);
                path.cubicTo(-60f, 2f, -44f, -48f, 0f, -18f);
                path.cubicTo(44f, -48f, 60f, 2f, 0f, 40f);
                path.close();
                fillPath(c, path, sh ? S : 0xFFFF4F64);
                strokePath(c, path, sh ? S : 0xFFD6334A, 4.5f);
                if (!sh) {
                    fill.setColor(0x80FFFFFF);
                    tmp.set(-30f, -24f, -16f, -12f);
                    c.drawOval(tmp, fill);
                }
                break;
            }
            case 2: { // éclair
                path.rewind();
                path.moveTo(8f, -48f);
                path.lineTo(-26f, 6f);
                path.lineTo(-2f, 6f);
                path.lineTo(-12f, 48f);
                path.lineTo(28f, -10f);
                path.lineTo(4f, -10f);
                path.lineTo(20f, -48f);
                path.close();
                fillPath(c, path, sh ? S : 0xFFFFD21A);
                strokePath(c, path, sh ? S : 0xFFE09A00, 4.5f);
                break;
            }
            case 3: { // soleil
                ostroke.setColor(sh ? S : 0xFFFFB300);
                ostroke.setStrokeWidth(8f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45 + 22.5);
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    c.drawLine(cs * 32f, sn * 32f, cs * 45f, sn * 45f, ostroke);
                }
                fill.setColor(sh ? S : 0xFFFFC107);
                c.drawCircle(0f, 0f, 24f, fill);
                if (!sh) {
                    ostroke.setColor(0xFFF08C00);
                    ostroke.setStrokeWidth(3.5f);
                    c.drawCircle(0f, 0f, 24f, ostroke);
                    fill.setColor(0x80FFFFFF);
                    c.drawCircle(-8f, -8f, 5f, fill);
                }
                break;
            }
            case 4: { // sourire
                fill.setColor(sh ? S : 0xFFFFD54F);
                c.drawCircle(0f, 0f, 43f, fill);
                if (sh) break;
                ostroke.setColor(0xFFE3A21A);
                ostroke.setStrokeWidth(4.5f);
                c.drawCircle(0f, 0f, 43f, ostroke);
                fill.setColor(0xFF5D4037);
                tmp.set(-20f, -20f, -9f, -2f);
                c.drawOval(tmp, fill);
                tmp.set(9f, -20f, 20f, -2f);
                c.drawOval(tmp, fill);
                ostroke.setColor(0xFF5D4037);
                ostroke.setStrokeWidth(6f);
                tmp.set(-24f, -16f, 24f, 26f);
                c.drawArc(tmp, 25f, 130f, false, ostroke);
                fill.setColor(0x55FF8A65);
                c.drawCircle(-26f, 10f, 7f, fill);
                c.drawCircle(26f, 10f, 7f, fill);
                break;
            }
            case 5: { // ampoule (idée)
                if (!sh) {
                    ostroke.setColor(0xFFFFC107);
                    ostroke.setStrokeWidth(5f);
                    int[] angs = {-150, -112, -68, -30};
                    for (int a : angs) {
                        double r = Math.toRadians(a);
                        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
                        c.drawLine(cs * 37f, -12f + sn * 37f, cs * 47f, -12f + sn * 47f, ostroke);
                    }
                }
                path.rewind();
                path.addCircle(0f, -12f, 27f, Path.Direction.CW);
                path2.rewind();
                path2.moveTo(-15f, 8f);
                path2.lineTo(15f, 8f);
                path2.lineTo(11f, 24f);
                path2.lineTo(-11f, 24f);
                path2.close();
                path.op(path2, Path.Op.UNION);
                fillPath(c, path, sh ? S : 0xFFFFE066);
                strokePath(c, path, sh ? S : 0xFFE0B000, 3.5f);
                tmp.set(-12f, 24f, 12f, 40f);
                fill.setColor(sh ? S : 0xFF90A4AE);
                c.drawRoundRect(tmp, 4f, 4f, fill);
                if (!sh) {
                    ostroke.setColor(0xFF607D8B);
                    ostroke.setStrokeWidth(2.5f);
                    c.drawLine(-10f, 30f, 10f, 30f, ostroke);
                    c.drawLine(-10f, 35f, 10f, 35f, ostroke);
                    fill.setColor(0x99FFFFFF);
                    c.drawCircle(-9f, -21f, 6f, fill);
                }
                break;
            }
            case 6: { // horloge
                fill.setColor(sh ? S : 0xFFFFFFFF);
                c.drawCircle(0f, 0f, 43f, fill);
                ostroke.setColor(sh ? S : 0xFF37474F);
                ostroke.setStrokeWidth(6.5f);
                c.drawCircle(0f, 0f, 41f, ostroke);
                if (sh) break;
                ostroke.setStrokeWidth(4f);
                for (int i = 0; i < 12; i++) {
                    double a = Math.toRadians(i * 30);
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    float r0 = i % 3 == 0 ? 27f : 31f;
                    c.drawLine(cs * r0, sn * r0, cs * 34f, sn * 34f, ostroke);
                }
                ostroke.setStrokeWidth(6f);
                c.drawLine(0f, 0f, 0f, -24f, ostroke);
                c.drawLine(0f, 0f, 17f, 9f, ostroke);
                fill.setColor(0xFFD62F3C);
                c.drawCircle(0f, 0f, 5f, fill);
                break;
            }
            default: { // flamme
                path.rewind();
                path.moveTo(4f, -48f);
                path.cubicTo(16f, -26f, 38f, -14f, 38f, 14f);
                path.cubicTo(38f, 36f, 21f, 48f, 0f, 48f);
                path.cubicTo(-21f, 48f, -38f, 36f, -38f, 14f);
                path.cubicTo(-38f, -4f, -26f, -16f, -18f, -30f);
                path.cubicTo(-14f, -16f, -6f, -12f, -1f, -10f);
                path.cubicTo(-4f, -26f, -2f, -38f, 4f, -48f);
                path.close();
                fillPath(c, path, sh ? S : 0xFFFF7A1A);
                strokePath(c, path, sh ? S : 0xFFE0560A, 3.5f);
                if (sh) break;
                path.rewind();
                path.moveTo(0f, 0f);
                path.cubicTo(10f, 12f, 21f, 20f, 21f, 31f);
                path.cubicTo(21f, 42f, 11f, 48f, 0f, 48f);
                path.cubicTo(-11f, 48f, -21f, 42f, -21f, 31f);
                path.cubicTo(-21f, 20f, -10f, 12f, 0f, 0f);
                path.close();
                fillPath(c, path, 0xFFFFC93C);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ fabrique d'objets (centrés en 0, 0)

    Obj makeStamp(Settings.Stamp st) {
        float h = 46f;
        text.setTypeface(bold);
        text.setLetterSpacing(0.08f);
        text.setTextSize(h * 0.5f);
        float tw = text.measureText(st.text);
        text.setLetterSpacing(0f);
        return new Obj(Obj.STAMP, st.filled ? STAMP_FILLED : STAMP_OUTLINE, st.color, 0, 0,
                Math.max(h * 1.4f, tw + h * 0.9f), h, false, st.text);
    }

    Obj makeSubject(Settings.Subject s) {
        float h = 40f;
        text.setTypeface(medium);
        text.setTextSize(h * 0.5f);
        float tw = text.measureText(s.name);
        return new Obj(Obj.SUBJECT, s.id, s.color, 0, 0, h * 0.85f + tw + h * 0.45f, h, false, s.name);
    }

    static Obj makeSticker(int kind) {
        return new Obj(Obj.STICKER, kind, 0, 0, 0, 84f, 84f, false, "");
    }

    static Obj makeEmojiSticker(String e) {
        return new Obj(Obj.STICKER, EMOJI, 0, 0, 0, 84f, 84f, false, e);
    }

    /** Taille d'une case à cocher (insérée depuis le menu ou dessinée). */
    static final float BOX_SIZE = 44f;

    static Obj makeBox(int color) {
        return new Obj(Obj.BOX, 0, color, 0, 0, BOX_SIZE, BOX_SIZE, false, "");
    }

    static Obj makeBox(float x, float y, float size, int color) {
        return new Obj(Obj.BOX, 0, color, x, y, size, size, false, "");
    }
}
