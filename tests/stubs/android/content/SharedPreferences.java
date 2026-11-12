package android.content;

/** Bouchon pour les tests sur la JVM (seules les méthodes utilisées par l'appli). */
public interface SharedPreferences {
    String getString(String k, String d);

    int getInt(String k, int d);

    boolean getBoolean(String k, boolean d);

    boolean contains(String k);

    java.util.Map<String, ?> getAll();

    Editor edit();

    interface Editor {
        Editor putString(String k, String v);

        Editor putInt(String k, int v);

        Editor putBoolean(String k, boolean v);

        Editor remove(String k);

        void apply();
    }
}
