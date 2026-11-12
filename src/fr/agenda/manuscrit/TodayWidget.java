package fr.agenda.manuscrit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Widget d'écran d'accueil : la page de DEMAIN en miniature (ce qu'il y a à préparer) ;
 * un appui ouvre l'agenda sur demain. (Le nom de la classe reste TodayWidget : le changer
 * retirerait les widgets déjà posés.)
 */
public final class TodayWidget extends AppWidgetProvider {
    static final String ACTION_MIDNIGHT = "fr.agenda.manuscrit.WIDGET_MIDNIGHT";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
        scheduleMidnight(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        update(c, m, id);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (ACTION_MIDNIGHT.equals(intent.getAction())) updateAll(c);
        else super.onReceive(c, intent);
    }

    @Override
    public void onDisabled(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(midnightIntent(c));
    }

    /** Met à jour tous les widgets posés (appelé quand la page du jour change). */
    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            if (m == null) return;
            int[] ids = m.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
            if (ids == null || ids.length == 0) return;
            for (int id : ids) update(c, m, id);
            scheduleMidnight(c);
        } catch (RuntimeException e) {
            Log.w("AgendaWidget", "update", e);
        }
    }

    private static void update(Context c, AppWidgetManager m, int id) {
        Settings.init(c);
        Bundle o = m.getAppWidgetOptions(id);
        float density = c.getResources().getDisplayMetrics().density;
        RemoteViews rv = null;
        if (Build.VERSION.SDK_INT >= 31) rv = responsive(c, o, density);
        if (rv == null) {
            // Android 11 et avant : une image pour le paysage (large et bas), une pour le portrait (étroit et haut)
            int minW = opt(o, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260);
            int maxW = opt(o, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW);
            int minH = opt(o, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
            int maxH = opt(o, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH);
            RemoteViews land = views(c, maxW, minH, density);
            rv = (maxW == minW && maxH == minH) ? land : new RemoteViews(land, views(c, minW, maxH, density));
        }
        m.updateAppWidget(id, rv);
    }

    private static int opt(Bundle o, String key, int def) {
        int v = o == null ? 0 : o.getInt(key, 0);
        return v > 0 ? v : def;
    }

    /**
     * Android 12 et plus : le lanceur donne les tailles exactes du widget (une par orientation) ;
     * on fournit une image à chaque taille et Android affiche celle qui correspond.
     */
    private static RemoteViews responsive(Context c, Bundle o, float density) {
        try {
            if (o == null) return null;
            // OPTION_APPWIDGET_SIZES : liste de SizeF (Parcelable seulement depuis Android 12)
            ArrayList<? extends Parcelable> sizes = o.getParcelableArrayList("appWidgetSizes");
            if (sizes == null || sizes.isEmpty()) return null;
            Map<SizeF, RemoteViews> map = new HashMap<>();
            for (Object pa : sizes) {
                if (!(pa instanceof SizeF) || map.size() >= 4) continue;
                SizeF sz = (SizeF) pa;
                if (sz.getWidth() < 20 || sz.getHeight() < 20 || map.containsKey(sz)) continue;
                map.put(sz, views(c, Math.round(sz.getWidth()), Math.round(sz.getHeight()), density));
            }
            if (map.isEmpty()) return null;
            if (map.size() == 1) return map.values().iterator().next();
            // new RemoteViews(Map<SizeF, RemoteViews>) n'existe qu'à partir d'Android 12
            Constructor<RemoteViews> k = RemoteViews.class.getConstructor(Map.class);
            return k.newInstance(map);
        } catch (Exception e) {
            Log.w("AgendaWidget", "tailles", e);
            return null;
        }
    }

    /** Le widget pour une taille donnée (en dp) : l'image remplit exactement le cadre. */
    private static RemoteViews views(Context c, int wDp, int hDp, float density) {
        float w = wDp * density, h = hDp * density;
        float k = Math.min(1f, 1200f / Math.max(w, h)); // image nette sans être trop lourde pour le lanceur
        Bitmap b = render(c, Math.max(40, Math.round(w * k)), Math.max(40, Math.round(h * k)), 16 * density * k);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_today);
        rv.setImageViewBitmap(R.id.widget_image, b);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra(MainActivity.EXTRA_DAY_OFFSET, 1);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        return rv;
    }

    /** Le jour affiché : demain. */
    static long shownDay() {
        return Days.today() + 1;
    }

    /**
     * La page de demain sur toute la surface du widget : à la largeur du cadre, le haut de la page
     * si le cadre est plus bas qu'une page, la page prolongée (papier en plus) s'il est plus haut.
     */
    static Bitmap render(Context c, int w, int h, float radius) {
        PageStore store = PageStore.get(c);
        long today = Days.today(), day = today + 1;
        CalendarSource.init(c);
        CalendarSource.loadSync(day); // le widget n'attend pas une seconde lecture
        Page p = store.page(day);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float s = w / Geo.W;
        float paperH = Math.max(Shapes.pageHeight(p.items), h / s);
        Path clip = new Path();
        clip.addRoundRect(0, 0, w, h, radius, radius, Path.Direction.CW);
        cv.clipPath(clip);
        cv.scale(s, s);
        PageRenderer r = new PageRenderer();
        r.drawPaper(cv, day, today, paperH, Settings.bgAgenda, Settings.bandVisible());
        r.drawItems(cv, p.items, false, s);
        return b;
    }

    private static PendingIntent midnightIntent(Context c) {
        Intent i = new Intent(c, TodayWidget.class);
        i.setAction(ACTION_MIDNIGHT);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Juste après minuit, le widget passe au jour suivant (au prochain réveil de la tablette). */
    private static void scheduleMidnight(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 30);
        cal.set(Calendar.MILLISECOND, 0);
        am.set(AlarmManager.RTC, cal.getTimeInMillis(), midnightIntent(c));
    }
}
