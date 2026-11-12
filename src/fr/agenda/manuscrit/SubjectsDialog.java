package fr.agenda.manuscrit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Liste des matières (nom + couleur) : ajouter, renommer, changer de couleur, supprimer. */
final class SubjectsDialog {
    private SubjectsDialog() {}

    static void show(final Context c, final Runnable changed) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, L.s("Matières", "Subjects")));
        TextView hint = Kit.text(c, L.s("Pour les pastilles posées sur les pages, les points du calendrier et l'emploi du temps.", "Used for the tags on your pages, the calendar dots and the timetable."),
                14, Ui.GREY, false);
        hint.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 10));
        body.addView(hint);
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        TextView add = Kit.button(c, L.s("+ Ajouter une matière", "+ Add a subject"), true);
        add.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        add.setPadding(Kit.px(c, 22), Kit.px(c, 14), Kit.px(c, 22), Kit.px(c, 14));
        body.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = Kit.button(c, L.s("Fermer", "Close"), true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, null, close), 440);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        final Runnable[] fill = new Runnable[1];
        final Runnable saved = new Runnable() {
            @Override
            public void run() {
                fill[0].run();
                if (changed != null) changed.run();
            }
        };
        fill[0] = new Runnable() {
            @Override
            public void run() {
                list.removeAllViews();
                for (final Settings.Subject s : Settings.subjects) {
                    LinearLayout row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Kit.px(c, 22), Kit.px(c, 12), Kit.px(c, 18), Kit.px(c, 12));
                    Kit.ripple(row, false);
                    row.addView(new Dot(c, s.color), new LinearLayout.LayoutParams(Kit.px(c, 18), Kit.px(c, 18)));
                    TextView name = Kit.text(c, s.name, 16, Ui.INK, false);
                    name.setPadding(Kit.px(c, 14), 0, 0, 0);
                    row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    row.addView(Kit.text(c, L.s("Modifier", "Edit"), 14, Ui.GREY, false));
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            edit(c, s, saved);
                        }
                    });
                    list.addView(row);
                }
            }
        };
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(c, null, saved);
            }
        });
        fill[0].run();
        d.show();
    }

    /** Création (s == null) ou modification d'une matière. */
    static void edit(final Context c, final Settings.Subject s, final Runnable saved) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, s == null ? L.s("Nouvelle matière", "New subject") : L.s("Modifier la matière", "Edit subject")));
        final EditText name = Kit.field(c, s == null ? "" : s.name, L.s("Nom (ex. Maths)", "Name (e.g. Maths)"));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(Kit.px(c, 22), Kit.px(c, 6), Kit.px(c, 22), Kit.px(c, 14));
        body.addView(name, nlp);
        TextView lbl = Kit.text(c, L.s("Couleur", "Colour"), 14, Ui.GREY, true);
        lbl.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(lbl);
        final int[] color = {s != null ? s.color : Settings.PALETTE[Settings.subjects.size() % Settings.PALETTE.length]};
        final Kit.Flow pal = new Kit.Flow(c, Kit.px(c, 10), Kit.px(c, 10));
        pal.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
        final List<Swatch> swatches = new ArrayList<>();
        for (final int col : Settings.PALETTE) {
            final Swatch sw = new Swatch(c, col);
            sw.setSelected(col == color[0]);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    color[0] = col;
                    for (Swatch o : swatches) o.setSelected(o.color == col);
                }
            });
            swatches.add(sw);
            pal.addView(sw);
        }
        body.addView(pal);

        TextView del = s == null ? null : Kit.button(c, L.s("Supprimer", "Delete"), false);
        if (del != null) del.setTextColor(Ui.DANGER);
        TextView cancel = Kit.button(c, L.s("Annuler", "Cancel"), false);
        TextView ok = Kit.button(c, L.s("Enregistrer", "Save"), true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, del, cancel, ok), 420);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                    Toast.makeText(c, L.s("Donne un nom à la matière.", "Give the subject a name."), Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayList<Settings.Subject> l = new ArrayList<>(Settings.subjects);
                if (s == null) {
                    l.add(new Settings.Subject(Settings.nextSubjectId(), n, color[0]));
                } else {
                    for (int i = 0; i < l.size(); i++) {
                        if (l.get(i).id == s.id) l.set(i, new Settings.Subject(s.id, n, color[0]));
                    }
                }
                Settings.setSubjects(l);
                d.dismiss();
                saved.run();
            }
        });
        if (del != null) {
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(c)
                            .setTitle(L.f("Supprimer « %s » ?", "Delete “%s”?", s.name))
                            .setMessage(L.s("Les pastilles déjà posées sur tes pages restent (en gris) et ses cours sont retirés de l'emploi du temps.", "Tags already placed on your pages stay (in grey) and its classes are removed from the timetable."))
                            .setNegativeButton(L.s("Garder", "Keep"), null)
                            .setPositiveButton(L.s("Supprimer", "Delete"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface di, int which) {
                                    ArrayList<Settings.Subject> l = new ArrayList<>();
                                    for (Settings.Subject x : Settings.subjects) if (x.id != s.id) l.add(x);
                                    ArrayList<Settings.Slot> sl = new ArrayList<>();
                                    for (Settings.Slot x : Settings.slots) if (x.subjectId != s.id) sl.add(x);
                                    Settings.setSubjects(l);
                                    Settings.setSlots(sl);
                                    d.dismiss();
                                    saved.run();
                                }
                            })
                            .show();
                }
            });
        }
        d.show();
    }

    /** Pastille de couleur. */
    static final class Dot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dot(Context c, int color) {
            super(c);
            p.setColor(color);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, p);
        }
    }

    /** Pastille de couleur sélectionnable (palette). */
    static final class Swatch extends View {
        final int color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp;

        Swatch(Context c, int color) {
            super(c);
            this.color = color;
            this.dp = Ui.dp(c, 1);
            setClickable(true);
            setContentDescription(L.s("Couleur", "Colour"));
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = Math.round(40 * dp);
            setMeasuredDimension(s, s);
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (isSelected()) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * dp);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, 18 * dp, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(cx, cy, 13.5f * dp, p);
        }
    }
}
