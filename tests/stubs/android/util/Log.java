package android.util;
public final class Log {
  public static int w(String t, String m){System.err.println("W "+m);return 0;}
  public static int w(String t, String m, Throwable e){System.err.println("W "+m+" "+e);return 0;}
  public static int e(String t, String m){System.err.println("E "+m);return 0;}
  public static int e(String t, String m, Throwable e){System.err.println("E "+m+" "+e);return 0;}
}
