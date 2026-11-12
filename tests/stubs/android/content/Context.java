package android.content;
public class Context {
  public static final int MODE_PRIVATE = 0;
  private final java.io.File dir;
  public Context(java.io.File dir){this.dir=dir;}
  public java.io.File getFilesDir(){return dir;}
  public Context getApplicationContext(){return this;}
  public SharedPreferences getSharedPreferences(String n, int m){ return null; }
}
