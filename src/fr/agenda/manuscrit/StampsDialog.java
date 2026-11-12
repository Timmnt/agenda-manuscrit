package fr.agenda.manuscrit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tampons (texte, couleur, contour ou plein) et autocollants emoji : ajouter, modifier, retirer.
 * Ce qui est déjà posé sur les pages ne change pas : chaque objet garde son texte et sa couleur.
 */
final class StampsDialog {
    private StampsDialog() {}

    private static final int MAX_STAMP_CHARS = 18;
    private static final int MAX_EMOJI_CODEPOINTS = 8;

    static void show(final Context c, final Runnable changed) {
        final PageRenderer factory = new PageRenderer();
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, L.s("Tampons et autocollants", "Stamps and stickers")));
        TextView hint = Kit.text(c, L.s("Ils apparaissent dans le panneau Insérer. Ce qui est déjà posé sur tes pages ne change pas.", "They appear in the Insert panel. Whatever is already on your pages doesn't change."),
                14, Ui.GREY, false);
        hint.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 6));
        body.addView(hint);

        body.addView(header(c, L.s("Tampons", "Stamps")));
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        TextView addStamp = Kit.button(c, L.s("+ Nouveau tampon", "+ New stamp"), true);
        addStamp.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        addStamp.setPadding(Kit.px(c, 22), Kit.px(c, 12), Kit.px(c, 22), Kit.px(c, 12));
        body.addView(addStamp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body.addView(header(c, L.s("Autocollants emoji", "Emoji stickers")));
        TextView eh = Kit.text(c, L.s("Touche un emoji pour le retirer.", "Tap an emoji to remove it."), 13, Ui.LIGHT_GREY, false);
        eh.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(eh);
        final Kit.Flow emojis = new Kit.Flow(c, Kit.px(c, 8), Kit.px(c, 8));
        emojis.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
        body.addView(emojis);

        TextView reset = Kit.button(c, L.s("Par défaut", "Defaults"), false);
        TextView close = Kit.button(c, L.s("Fermer", "Close"), true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, reset, close), 460);
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
                for (final Settings.Stamp st : Settings.stamps) {
                    LinearLayout row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Kit.px(c, 18), Kit.px(c, 6), Kit.px(c, 18), Kit.px(c, 6));
                    Kit.ripple(row, false);
                    Obj o = factory.makeStamp(st);
                    Kit.ObjPreview pv = new Kit.ObjPreview(c, factory, o, Math.min(260, (int) (o.w * 0.78f) + 18), 50);
                    pv.setClickable(false);
                    row.addView(pv);
                    row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
                    row.addView(Kit.text(c, L.s("Modifier", "Edit"), 14, Ui.GREY, false));
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            editStamp(c, st, saved);
                        }
                    });
                    list.addView(row);
                }
                if (Settings.stamps.isEmpty()) {
                    TextView none = Kit.text(c, L.s("Aucun tampon.", "No stamps."), 14, Ui.LIGHT_GREY, false);
                    none.setPadding(Kit.px(c, 22), Kit.px(c, 4), Kit.px(c, 22), Kit.px(c, 4));
                    list.addView(none);
                }
                emojis.removeAllViews();
                for (final String e : Settings.emojis) {
                    Kit.ObjPreview pv = new Kit.ObjPreview(c, factory, PageRenderer.makeEmojiSticker(e), 56, 56);
                    pv.setContentDescription(L.s("Retirer l'autocollant ", "Remove sticker ") + e);
                    pv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            removeEmoji(c, e, saved);
                        }
                    });
                    emojis.addView(pv);
                }
                TextView add = Kit.pill(c, L.s("+ Ajouter un emoji", "+ Add an emoji"), false);
                add.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addEmoji(c, saved);
                    }
                });
                emojis.addView(add);
            }
        };
        addStamp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editStamp(c, null, saved);
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(c)
                        .setTitle(L.s("Revenir aux tampons et autocollants d'origine ?", "Go back to the original stamps and stickers?"))
                        .setMessage(L.s("Tes tampons et emoji ajoutés seront retirés du panneau Insérer (ceux déjà posés sur les pages restent).", "Your added stamps and emoji will be removed from the Insert panel (those already on your pages stay)."))
                        .setNegativeButton(L.s("Annuler", "Cancel"), null)
                        .setPositiveButton(L.s("Rétablir", "Restore"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface di, int which) {
                                Settings.setStamps(Settings.defaultStamps());
                                Settings.setEmojis(Settings.defaultEmojis());
                                saved.run();
                            }
                        })
                        .show();
            }
        });
        fill[0].run();
        d.show();
    }

    private static TextView header(Context c, String s) {
        TextView t = Kit.text(c, s, 14, Ui.GREY, true);
        t.setPadding(Kit.px(c, 22), Kit.px(c, 14), Kit.px(c, 22), Kit.px(c, 6));
        return t;
    }

    /** Création (st == null) ou modification d'un tampon, avec aperçu en direct. */
    static void editStamp(final Context c, final Settings.Stamp st, final Runnable saved) {
        final PageRenderer factory = new PageRenderer();
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, st == null ? L.s("Nouveau tampon", "New stamp") : L.s("Modifier le tampon", "Edit stamp")));

        final int[] color = {st != null ? st.color : Settings.PALETTE[(Settings.stamps.size() * 5 + 7) % Settings.PALETTE.length]};
        final boolean[] filled = {st != null && st.filled};
        final String[] label = {st != null ? st.text : ""};

        final StampPreview preview = new StampPreview(c, factory);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Kit.px(c, 76));
        plp.setMargins(Kit.px(c, 22), Kit.px(c, 4), Kit.px(c, 22), Kit.px(c, 10));
        body.addView(preview, plp);
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                String t = label[0].trim();
                preview.set(new Settings.Stamp(0, t.isEmpty() ? L.s("TEXTE", "TEXT") : t, color[0], filled[0]));
            }
        };

        final EditText name = Kit.field(c, label[0], L.s("Texte (ex. TP NOTÉ, À REVOIR, ORAL)", "Text (e.g. QUIZ, REVIEW, ORAL)"));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_STAMP_CHARS)});
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
                label[0] = s.toString();
                refresh.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
        body.addView(name, nlp);

        TextView sl = Kit.text(c, L.s("Style", "Style"), 14, Ui.GREY, true);
        sl.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(sl);
        LinearLayout styles = new LinearLayout(c);
        styles.setOrientation(LinearLayout.HORIZONTAL);
        styles.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 14));
        body.addView(styles);
        final LinearLayout stylesRow = styles;
        final Runnable[] drawStyles = new Runnable[1];
        final View.OnClickListener styleClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filled[0] = Boolean.TRUE.equals(v.getTag());
                drawStyles[0].run();
                refresh.run();
            }
        };
        drawStyles[0] = new Runnable() {
            @Override
            public void run() {
                // deux pastilles « Contour » / « Plein », la choisie en plein
                stylesRow.removeAllViews();
                TextView outline = Kit.pill(c, L.s("Contour", "Outline"), !filled[0]), plain = Kit.pill(c, L.s("Plein", "Filled"), filled[0]);
                outline.setTag(Boolean.FALSE);
                plain.setTag(Boolean.TRUE);
                outline.setOnClickListener(styleClick);
                plain.setOnClickListener(styleClick);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Kit.px(c, 10);
                stylesRow.addView(outline, lp);
                stylesRow.addView(plain);
            }
        };
        drawStyles[0].run();

        TextView cl = Kit.text(c, L.s("Couleur", "Colour"), 14, Ui.GREY, true);
        cl.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 8));
        body.addView(cl);
        Kit.Flow pal = new Kit.Flow(c, Kit.px(c, 10), Kit.px(c, 10));
        pal.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 12));
        final List<SubjectsDialog.Swatch> swatches = new ArrayList<>();
        for (final int col : Settings.PALETTE) {
            final SubjectsDialog.Swatch sw = new SubjectsDialog.Swatch(c, col);
            sw.setSelected(col == color[0]);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    color[0] = col;
                    for (SubjectsDialog.Swatch o : swatches) o.setSelected(o.color == col);
                    refresh.run();
                }
            });
            swatches.add(sw);
            pal.addView(sw);
        }
        body.addView(pal);

        refresh.run();

        TextView del = st == null ? null : Kit.button(c, L.s("Supprimer", "Delete"), false);
        if (del != null) del.setTextColor(Ui.DANGER);
        TextView cancel = Kit.button(c, L.s("Annuler", "Cancel"), false);
        TextView ok = Kit.button(c, L.s("Enregistrer", "Save"), true);
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
                String t = label[0].replace('\t', ' ').replace('\n', ' ').trim();
                if (t.isEmpty()) {
                    Toast.makeText(c, L.s("Écris le texte du tampon.", "Type the stamp's text."), Toast.LENGTH_SHORT).show();
                    return;
                }
                t = t.toUpperCase(L.locale());
                ArrayList<Settings.Stamp> l = new ArrayList<>(Settings.stamps);
                if (st == null) {
                    l.add(new Settings.Stamp(Settings.nextStampId(), t, color[0], filled[0]));
                } else {
                    for (int i = 0; i < l.size(); i++) {
                        if (l.get(i).id == st.id) l.set(i, new Settings.Stamp(st.id, t, color[0], filled[0]));
                    }
                }
                Settings.setStamps(l);
                d.dismiss();
                if (saved != null) saved.run();
            }
        });
        if (del != null) {
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(c)
                            .setTitle(L.f("Supprimer le tampon « %s » ?", "Delete the “%s” stamp?", st.text))
                            .setMessage(L.s("Il disparaît du panneau Insérer ; ceux déjà posés sur tes pages restent.", "It disappears from the Insert panel; the ones already on your pages stay."))
                            .setNegativeButton(L.s("Garder", "Keep"), null)
                            .setPositiveButton(L.s("Supprimer", "Delete"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface di, int which) {
                                    ArrayList<Settings.Stamp> l = new ArrayList<>();
                                    for (Settings.Stamp x : Settings.stamps) if (x.id != st.id) l.add(x);
                                    Settings.setStamps(l);
                                    d.dismiss();
                                    if (saved != null) saved.run();
                                }
                            })
                            .show();
                }
            });
        }
        d.show();
    }

    /** Ajoute un autocollant : un emoji (clavier emoji) ou quelques caractères. */
    static void addEmoji(final Context c, final Runnable added) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Kit.title(c, L.s("Nouvel autocollant", "New sticker")));
        TextView hint = Kit.text(c, L.s("Tape un emoji avec le clavier (touche 😊), ou un ou deux caractères (★, ✓, ?!).", "Type an emoji with the keyboard (😊 key), or one or two characters (★, ✓, ?!)."),
                14, Ui.GREY, false);
        hint.setPadding(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 10));
        body.addView(hint);
        final EditText field = Kit.field(c, "", L.s("Emoji", "Emoji"));
        field.setTextSize(28);
        field.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Kit.px(c, 22), 0, Kit.px(c, 22), Kit.px(c, 14));
        body.addView(field, lp);
        TextView cancel = Kit.button(c, L.s("Annuler", "Cancel"), false);
        TextView ok = Kit.button(c, L.s("Ajouter", "Add"), true);
        final Dialog d = Kit.sheet(c, body, Kit.footer(c, null, cancel, ok), 400);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String e = field.getText().toString().replace('\t', ' ').replace('\n', ' ').trim();
                if (e.isEmpty()) {
                    Toast.makeText(c, L.s("Tape un emoji.", "Type an emoji."), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (e.codePointCount(0, e.length()) > MAX_EMOJI_CODEPOINTS) {
                    Toast.makeText(c, L.s("C'est un peu long pour un autocollant : un emoji ou deux caractères suffisent.", "That's a bit long for a sticker: one emoji or two characters is enough."), Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayList<String> l = new ArrayList<>(Settings.emojis);
                l.remove(e);
                l.add(e);
                Settings.setEmojis(l);
                d.dismiss();
                if (added != null) added.run();
            }
        });
        d.show();
    }

    static void removeEmoji(final Context c, final String e, final Runnable done) {
        new AlertDialog.Builder(c)
                .setTitle(L.f("Retirer l'autocollant %s ?", "Remove the %s sticker?", e))
                .setMessage(L.s("Il disparaît du panneau Insérer ; ceux déjà posés sur tes pages restent.", "It disappears from the Insert panel; the ones already on your pages stay."))
                .setNegativeButton(L.s("Garder", "Keep"), null)
                .setPositiveButton(L.s("Retirer", "Remove"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int which) {
                        ArrayList<String> l = new ArrayList<>(Settings.emojis);
                        l.remove(e);
                        Settings.setEmojis(l);
                        if (done != null) done.run();
                    }
                })
                .show();
    }

    /** Aperçu en direct du tampon en cours de modification, sur fond de papier. */
    private static final class StampPreview extends View {
        private final PageRenderer renderer;
        private Obj obj;

        StampPreview(Context c, PageRenderer renderer) {
            super(c);
            this.renderer = renderer;
            setBackground(Kit.rounded(0xFFFFFFFF, Ui.dp(c, 14), 0, 0));
        }

        void set(Settings.Stamp st) {
            obj = renderer.makeStamp(st);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (obj == null) return;
            float pad = Ui.dp(getContext(), 10);
            float s = Math.min((getWidth() - 2 * pad) / obj.w, (getHeight() - 2 * pad) / obj.h);
            s = Math.min(s, Ui.dp(getContext(), 1) * 1.3f);
            int save = c.save();
            c.translate(getWidth() / 2f, getHeight() / 2f);
            c.scale(s, s);
            renderer.drawObj(c, obj.at(0, 0));
            c.restoreToCount(save);
        }
    }
}
