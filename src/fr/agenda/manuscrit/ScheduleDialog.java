package fr.agenda.manuscrit;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Emploi du temps de la semaine : les cours s'affichent en petit en haut de chaque jour de l'agenda. */
final class ScheduleDialog {
    private ScheduleDialog() {}

    static String hm(int min) {
        return String.format(Locale.FRANCE, "%dh%02d", min / 60, min % 60);
    }

    /** L'activité demande l'autorisation de lire l'agenda (fenêtre d'Android). */
    interface CalendarAsker {
        void askCalendar(Runnable granted, Runnable refused);
    }

    static void show(final Context c, final Runnable changed, final CalendarAsker asker) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, "Emploi du temps"));
        TextView hint = Kit.text(c, "Tes cours s'affichent discrètement en haut de chaque jour de l'agenda.", 14, Ui.GREY, false);
        hint.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
        body.addView(hint);

        final int[] day = {Math.min(Days.dayOfWeek(Days.today()), 6)};
        final LinearLayout tabs = new LinearLayout(c);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(Kit.px(c, 18), 0, Kit.px(c, 18), Kit.px(c, 8));
        final TextView[] tab = new TextView[7];
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] fill = new Runnable[1];
        for (int i = 0; i < 7; i++) {
            final int k = i;
            tab[i] = Kit.text(c, Days.DAY_SHORT[i].replace(".", ""), 14, Ui.INK, true);
            tab[i].setGravity(Gravity.CENTER);
            tab[i].setPadding(0, Kit.px(c, 8), 0, Kit.px(c, 8));
            tab[i].setClickable(true);
            tab[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    day[0] = k;
                    fill[0].run();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Kit.px(c, 2), 0, Kit.px(c, 2), 0);
            tabs.addView(tab[i], lp);
        }
        body.addView(tabs);
        body.addView(list);

        TextView add = Kit.button(c, "+ Ajouter un cours", true);
        add.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        add.setPadding(Kit.px(c, 22), Kit.px(c, 14), Kit.px(c, 22), Kit.px(c, 14));
        body.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(Kit.divider(c));

        LinearLayout swRow = new LinearLayout(c);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(Kit.px(c, 22), Kit.px(c, 12), Kit.px(c, 18), Kit.px(c, 12));
        swRow.addView(Kit.text(c, "Afficher les cours sur les pages", 16, Ui.INK, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(c);
        sw.setChecked(Settings.showSchedule);
        swRow.addView(sw);
        body.addView(swRow);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Settings.setShowSchedule(on);
                if (changed != null) changed.run();
            }
        });

        body.addView(Kit.divider(c));
        addGoogleSection(c, body, changed, asker);

        TextView close = Kit.button(c, "Fermer", true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, null, close), 480);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
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
                for (int i = 0; i < 7; i++) {
                    boolean on = i == day[0];
                    tab[i].setBackground(on ? Kit.rounded(Ui.ACCENT_FILL, Ui.dp(c, 16), 0, 0) : null);
                    tab[i].setTextColor(on ? 0xFFFFFFFF : (i >= 5 ? Ui.WEEKEND : Ui.INK));
                }
                list.removeAllViews();
                List<Settings.Slot> slots = Settings.slotsFor(day[0]);
                if (slots.isEmpty()) {
                    TextView none = Kit.text(c, "Aucun cours ce jour-là.", 15, Ui.LIGHT_GREY, false);
                    none.setPadding(Kit.px(c, 22), Kit.px(c, 12), Kit.px(c, 22), Kit.px(c, 6));
                    list.addView(none);
                }
                for (final Settings.Slot s : slots) {
                    Settings.Subject sub = Settings.subject(s.subjectId);
                    LinearLayout row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Kit.px(c, 22), Kit.px(c, 11), Kit.px(c, 18), Kit.px(c, 11));
                    Kit.ripple(row, false);
                    TextView time = Kit.text(c, hm(s.start) + " – " + hm(s.end), 15, Ui.INK, true);
                    row.addView(time, new LinearLayout.LayoutParams(Kit.px(c, 118), ViewGroup.LayoutParams.WRAP_CONTENT));
                    row.addView(new SubjectsDialog.Dot(c, sub != null ? sub.color : 0xFF8A919C),
                            new LinearLayout.LayoutParams(Kit.px(c, 12), Kit.px(c, 12)));
                    TextView nm = Kit.text(c, (sub != null ? sub.name : "?") + (s.room.isEmpty() ? "" : "  ·  " + s.room),
                            15, Ui.INK, false);
                    nm.setPadding(Kit.px(c, 10), 0, 0, 0);
                    nm.setSingleLine(true);
                    row.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            editSlot(c, s, s.dow, saved);
                        }
                    });
                    list.addView(row);
                }
            }
        };
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editSlot(c, null, day[0], saved);
            }
        });
        fill[0].run();
        d.show();
    }

    // ------------------------------------------------------------------ Google Agenda

    private static void addGoogleSection(final Context c, LinearLayout body, final Runnable changed, final CalendarAsker asker) {
        LinearLayout gRow = new LinearLayout(c);
        gRow.setOrientation(LinearLayout.HORIZONTAL);
        gRow.setGravity(Gravity.CENTER_VERTICAL);
        gRow.setPadding(Kit.px(c, 22), Kit.px(c, 14), Kit.px(c, 18), Kit.px(c, 4));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Kit.text(c, "Google Agenda", 16, Ui.INK, true));
        TextView sub = Kit.text(c, "Tes événements s'ajoutent aux pastilles, à côté des cours. Lecture seule : rien n'est modifié dans ton agenda.",
                13, Ui.GREY, false);
        sub.setPadding(0, Kit.px(c, 3), Kit.px(c, 12), 0);
        texts.addView(sub);
        gRow.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch gsw = new Switch(c);
        gsw.setChecked(Settings.google && CalendarSource.hasPermission(c));
        gRow.addView(gsw);
        body.addView(gRow);

        final LinearLayout calRow = new LinearLayout(c);
        calRow.setOrientation(LinearLayout.HORIZONTAL);
        calRow.setGravity(Gravity.CENTER_VERTICAL);
        calRow.setPadding(Kit.px(c, 22), Kit.px(c, 12), Kit.px(c, 18), Kit.px(c, 14));
        Kit.ripple(calRow, false);
        calRow.addView(Kit.text(c, "Agendas affichés", 15, Ui.INK, false),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView calValue = Kit.text(c, "", 15, Ui.ACCENT, true);
        calValue.setGravity(Gravity.END);
        calValue.setSingleLine(true);
        calValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        calValue.setPadding(Kit.px(c, 12), 0, 0, 0);
        calRow.addView(calValue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(calRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable update = new Runnable() {
            @Override
            public void run() {
                boolean on = Settings.google && CalendarSource.hasPermission(c);
                calRow.setVisibility(on ? View.VISIBLE : View.GONE);
                if (on) calValue.setText(summary(CalendarSource.calendars(c)) + "  ›");
            }
        };
        update.run();
        calRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCalendars(c, new Runnable() {
                    @Override
                    public void run() {
                        update.run();
                        if (changed != null) changed.run();
                    }
                });
            }
        });

        final boolean[] silent = {false};
        gsw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                if (silent[0]) return;
                if (!on) {
                    Settings.setGoogle(false);
                    update.run();
                    if (changed != null) changed.run();
                    return;
                }
                final Runnable enable = new Runnable() {
                    @Override
                    public void run() {
                        CalendarSource.refreshPermission();
                        Settings.setGoogle(true);
                        CalendarSource.invalidate();
                        update.run();
                        if (changed != null) changed.run();
                        if (CalendarSource.calendars(c).isEmpty()) showNoCalendar(c);
                    }
                };
                if (CalendarSource.hasPermission(c)) {
                    enable.run();
                } else if (asker != null) {
                    asker.askCalendar(enable, new Runnable() {
                        @Override
                        public void run() {
                            silent[0] = true;
                            gsw.setChecked(false);
                            silent[0] = false;
                        }
                    });
                } else {
                    silent[0] = true;
                    gsw.setChecked(false);
                    silent[0] = false;
                }
            }
        });
    }

    /** « Perso, Cours » ou « 4 agendas ». */
    private static String summary(List<CalendarSource.Cal> cals) {
        ArrayList<String> names = new ArrayList<>();
        for (CalendarSource.Cal cal : cals) if (CalendarSource.isSelected(cal)) names.add(cal.name);
        if (cals.isEmpty()) return "Aucun sur la tablette";
        if (names.isEmpty()) return "Aucun";
        if (names.size() <= 2) return android.text.TextUtils.join(", ", names);
        return names.size() + " agendas";
    }

    private static final String SYNC_HELP = "Un agenda manque ou reste vide ? Dans l'appli Google Agenda : menu ☰ → Paramètres → "
            + "touche l'agenda → active « Synchroniser ». Ton compte : Paramètres d'Android → Comptes → Google → "
            + "Synchronisation du compte → Agenda.";

    private static void showNoCalendar(Context c) {
        new android.app.AlertDialog.Builder(c)
                .setTitle("Aucun agenda sur la tablette")
                .setMessage("L'app lit l'agenda synchronisé sur la tablette, sans connexion à part.\n\n"
                        + "1. Paramètres d'Android → Comptes → ajoute ton compte Google (s'il n'y est pas).\n"
                        + "2. Dans ce compte, active la synchronisation « Agenda ».\n"
                        + "3. Installe l'appli Google Agenda si besoin, puis attends quelques instants : "
                        + "tes événements apparaîtront tout seuls.")
                .setPositiveButton("OK", null)
                .show();
    }

    /** Choix des agendas affichés (regroupés par compte). */
    static void chooseCalendars(final Context c, final Runnable done) {
        final List<CalendarSource.Cal> cals = CalendarSource.calendars(c);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, "Agendas à afficher"));
        final List<CheckBox> boxes = new ArrayList<>();
        if (cals.isEmpty()) {
            TextView none = Kit.text(c, "Aucun agenda trouvé sur la tablette. Ajoute ton compte Google dans les "
                    + "Paramètres d'Android (Comptes), puis active la synchronisation de l'agenda.", 15, Ui.GREY, false);
            none.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
            body.addView(none);
        }
        String account = null;
        for (final CalendarSource.Cal cal : cals) {
            if (!cal.account.equals(account)) {
                account = cal.account;
                TextView h = Kit.text(c, account.isEmpty() ? "Sur la tablette" : account, 13, Ui.GREY, true);
                h.setPadding(Kit.px(c, 22), Kit.px(c, 10), Kit.px(c, 22), Kit.px(c, 4));
                h.setSingleLine(true);
                h.setEllipsize(android.text.TextUtils.TruncateAt.END);
                body.addView(h);
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Kit.px(c, 22), Kit.px(c, 8), Kit.px(c, 14), Kit.px(c, 8));
            Kit.ripple(row, false);
            row.addView(new SubjectsDialog.Dot(c, cal.color), new LinearLayout.LayoutParams(Kit.px(c, 12), Kit.px(c, 12)));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Kit.px(c, 12), 0, Kit.px(c, 8), 0);
            TextView nm = Kit.text(c, cal.name, 15, Ui.INK, false);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(nm);
            String note = !cal.synced ? "Pas synchronisé sur la tablette" : cal.noise() ? "Anniversaires / météo" : null;
            if (note != null) texts.addView(Kit.text(c, note, 12, Ui.LIGHT_GREY, false));
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final CheckBox cb = new CheckBox(c);
            cb.setChecked(CalendarSource.isSelected(cal));
            row.addView(cb);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cb.toggle();
                }
            });
            boxes.add(cb);
            body.addView(row);
        }
        TextView help = Kit.text(c, SYNC_HELP, 13, Ui.GREY, false);
        help.setPadding(Kit.px(c, 22), Kit.px(c, 12), Kit.px(c, 22), Kit.px(c, 8));
        body.addView(help);

        TextView cancel = Kit.button(c, "Annuler", false);
        TextView ok = Kit.button(c, "OK", true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, null, cancel, ok), 460);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!cals.isEmpty()) {
                    ArrayList<Long> ids = new ArrayList<>();
                    boolean same = true; // identique au choix automatique : on reste en automatique
                    for (int i = 0; i < cals.size(); i++) {
                        boolean on = boxes.get(i).isChecked();
                        if (on) ids.add(cals.get(i).id);
                        if (on != cals.get(i).autoOn()) same = false;
                    }
                    long[] arr = new long[ids.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
                    Settings.setCalendarIds(same ? null : arr);
                    CalendarSource.invalidate();
                }
                d.dismiss();
                if (done != null) done.run();
            }
        });
        d.show();
    }

    /** Ajout (slot == null) ou modification d'un cours. */
    static void editSlot(final Context c, final Settings.Slot slot, final int dow, final Runnable saved) {
        if (Settings.subjects.isEmpty()) {
            Toast.makeText(c, "Ajoute d'abord une matière (menu ⋮ → Matières).", Toast.LENGTH_LONG).show();
            return;
        }
        int defStart = 8 * 60;
        for (Settings.Slot s : Settings.slotsFor(dow)) defStart = Math.max(defStart, s.end);
        if (defStart > 22 * 60) defStart = 8 * 60;
        final int[] subj = {slot != null ? slot.subjectId : Settings.subjects.get(0).id};
        final int[] start = {slot != null ? slot.start : defStart};
        final int[] end = {slot != null ? slot.end : Math.min(defStart + 120, 23 * 60 + 59)};

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, (slot == null ? "Nouveau cours · " : "Cours · ") + Days.DAY_NAMES[dow]));

        TextView l1 = Kit.text(c, "Matière", 14, Ui.GREY, true);
        l1.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(l1);
        final Kit.Flow chips = new Kit.Flow(c, Kit.px(c, 8), Kit.px(c, 8));
        chips.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 14));
        final List<TextView> chipViews = new ArrayList<>();
        final List<Settings.Subject> subs = Settings.subjects;
        final Runnable styleChips = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < chipViews.size(); i++) {
                    Settings.Subject s = subs.get(i);
                    boolean on = s.id == subj[0];
                    TextView t = chipViews.get(i);
                    t.setBackground(Kit.rounded(on ? s.color : Ui.SURFACE_HI, Ui.dp(c, 16), s.color, Math.max(1, Kit.px(c, 1))));
                    t.setTextColor(on ? 0xFFFFFFFF : Ui.INK);
                }
            }
        };
        for (final Settings.Subject s : subs) {
            TextView t = Kit.text(c, s.name, 14, Ui.INK, true);
            t.setPadding(Kit.px(c, 14), Kit.px(c, 7), Kit.px(c, 14), Kit.px(c, 7));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    subj[0] = s.id;
                    styleChips.run();
                }
            });
            chipViews.add(t);
            chips.addView(t);
        }
        styleChips.run();
        body.addView(chips);

        TextView l2 = Kit.text(c, "Horaires", 14, Ui.GREY, true);
        l2.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(l2);
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        times.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 14));
        final TextView from = Kit.pill(c, "De " + hm(start[0]), false);
        final TextView to = Kit.pill(c, "à " + hm(end[0]), false);
        times.addView(from);
        View gap = new View(c);
        times.addView(gap, new LinearLayout.LayoutParams(Kit.px(c, 10), 1));
        times.addView(to);
        body.addView(times);
        from.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(c, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker tp, int h, int m) {
                        int dur = end[0] - start[0];
                        start[0] = h * 60 + m;
                        end[0] = Math.min(start[0] + Math.max(dur, 30), 23 * 60 + 59);
                        from.setText("De " + hm(start[0]));
                        to.setText("à " + hm(end[0]));
                    }
                }, start[0] / 60, start[0] % 60, true).show();
            }
        });
        to.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(c, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker tp, int h, int m) {
                        end[0] = h * 60 + m;
                        to.setText("à " + hm(end[0]));
                    }
                }, end[0] / 60, end[0] % 60, true).show();
            }
        });

        TextView l3 = Kit.text(c, "Salle (facultatif)", 14, Ui.GREY, true);
        l3.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(l3);
        final EditText room = Kit.field(c, slot != null ? slot.room : "", "ex. B204");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
        body.addView(room, rlp);

        TextView del = slot == null ? null : Kit.button(c, "Supprimer", false);
        if (del != null) del.setTextColor(Ui.DANGER);
        TextView cancel = Kit.button(c, "Annuler", false);
        TextView ok = Kit.button(c, slot == null ? "Ajouter" : "Enregistrer", true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, del, cancel, ok), 440);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (end[0] <= start[0]) {
                    Toast.makeText(c, "L'heure de fin doit être après le début.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayList<Settings.Slot> l = new ArrayList<>();
                for (Settings.Slot s : Settings.slots) if (s != slot) l.add(s);
                l.add(new Settings.Slot(dow, start[0], end[0], subj[0], room.getText().toString().trim()));
                Settings.setSlots(l);
                d.dismiss();
                saved.run();
            }
        });
        if (del != null) {
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ArrayList<Settings.Slot> l = new ArrayList<>();
                    for (Settings.Slot s : Settings.slots) if (s != slot) l.add(s);
                    Settings.setSlots(l);
                    d.dismiss();
                    saved.run();
                }
            });
        }
        d.show();
    }
}
