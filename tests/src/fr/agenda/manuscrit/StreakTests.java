package fr.agenda.manuscrit;
import java.util.*;
public class StreakTests {
  static int fails=0; static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL "+m);} else System.out.println("ok   "+m); }
  static PageStore.Summary s(int b,int c){ return new PageStore.Summary(b,c,new int[0]); }
  public static void main(String[] a){
    long t=1000; Map<Long,PageStore.Summary> m=new HashMap<>();
    check(Arrays.equals(Streaks.compute(m,t),new int[]{0,0}),"aucune case : 0 / 0");
    m.put(t-5L,s(2,2)); m.put(t-4L,s(3,3)); m.put(t-3L,s(1,0)); m.put(t-2L,s(2,2)); m.put(t-1L,s(4,4)); m.put(t,s(2,1));
    m.put(t-6L,PageStore.Summary.EMPTY);
    int[] r=Streaks.compute(m,t);
    check(r[0]==2,"série en cours = 2 (aujourd'hui pas fini ne casse pas) : "+r[0]);
    check(r[1]==2,"record = 2 : "+r[1]);
    m.put(t,s(2,2)); r=Streaks.compute(m,t); check(r[0]==3 && r[1]==3,"aujourd'hui fini : 3 / 3");
    m.put(t-7L,s(1,1)); m.put(t-8L,s(1,1)); m.put(t-9L,s(1,1)); m.put(t-10L,s(1,1)); r=Streaks.compute(m,t);
    check(r[1]==6 && r[0]==3,"record ancien 6 (jours sans case ignorés), en cours 3 : "+r[1]+"/"+r[0]);
    m.put(t+3L,s(5,0)); r=Streaks.compute(m,t); check(r[0]==3,"les jours futurs ne comptent pas");
    System.out.println(fails==0?"STREAKS OK":fails+" ÉCHEC(S)"); System.exit(fails==0?0:1);
  }
}
