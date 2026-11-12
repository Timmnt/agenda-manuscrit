package android.os;
public class Handler {
  public static final java.util.ArrayList<Runnable> QUEUE = new java.util.ArrayList<>();
  public Handler(Looper l){}
  public boolean post(Runnable r){ synchronized(QUEUE){ QUEUE.add(r);} return true; }
  public static void drain(){ java.util.ArrayList<Runnable> l; synchronized(QUEUE){ l=new java.util.ArrayList<>(QUEUE); QUEUE.clear(); } for(Runnable r: l) r.run(); }
}
