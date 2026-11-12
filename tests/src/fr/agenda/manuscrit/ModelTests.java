package fr.agenda.manuscrit;
import java.io.*; import java.util.*;
public class ModelTests {
  static int fails = 0;
  static void check(boolean c, String m){ if(!c){fails++; System.out.println("FAIL "+m);} else System.out.println("ok   "+m); }
  static Stroke stroke(int color, float... xy){ Stroke s=new Stroke(color,3f,0,xy.length/2); for(int i=0;i<xy.length;i+=2) s.add(xy[i],xy[i+1],1f); s.trim(); return s; }
  static Stroke path(List<float[]> pts){ Stroke s=new Stroke(0xFF000000,3f,0,pts.size()); for(float[] p:pts) s.add(p[0],p[1],0.8f); s.trim(); return s; }
  static Random rnd = new Random(42);
  /** Trait dense (un point tous les ~1,5 unités), comme le donne un vrai stylet. */
  static Stroke dense(float... xy){ List<float[]> p=new ArrayList<>(); p.add(new float[]{xy[0],xy[1]});
    for(int i=2;i<xy.length;i+=2){ float ax=xy[i-2],ay=xy[i-1],bx=xy[i],by=xy[i+1]; int m=Math.max(1,(int)(Math.hypot(bx-ax,by-ay)/1.5)); for(int k=1;k<=m;k++) p.add(new float[]{ax+(bx-ax)*k/m, ay+(by-ay)*k/m}); }
    return path(p); }
  // carré dessiné à la main : côtés avec bruit, coins éventuellement arrondis, rotation, fermeture imparfaite
  static Stroke handSquare(float cx, float cy, float a, double rotDeg, float noise, float corner, float gapFrac){
    List<float[]> pts = new ArrayList<>();
    float[][] c = {{-a/2,-a/2},{a/2,-a/2},{a/2,a/2},{-a/2,a/2},{-a/2,-a/2}};
    double r = Math.toRadians(rotDeg);
    for (int s=0;s<4;s++){
      for (int k=0;k<20;k++){
        float t=k/20f; float x=c[s][0]+(c[s+1][0]-c[s][0])*t, y=c[s][1]+(c[s+1][1]-c[s][1])*t;
        if (corner>0 && (t<0.12f)) { float u=t/0.12f; x = x*(1-corner*(1-u)) ; y = y*(1-corner*(1-u)); }
        if (s==3 && t>1-gapFrac) break;
        x += (float)(rnd.nextGaussian()*noise); y += (float)(rnd.nextGaussian()*noise);
        double xr = x*Math.cos(r)-y*Math.sin(r), yr = x*Math.sin(r)+y*Math.cos(r);
        pts.add(new float[]{cx+(float)xr, cy+(float)yr});
      }
    }
    return path(pts);
  }

  // carré réaliste : coins arrondis de rayon rho (fraction du côté), tremblement, rotation, trou ou dépassement à la fin
  static Stroke roundSquare(float cx, float cy, float a, double rotDeg, float noise, float rho, float endFrac){ return roundSquare(cx,cy,a,rotDeg,noise,rho,endFrac,rnd.nextFloat()); }
  static Stroke roundSquare(float cx, float cy, float a, double rotDeg, float noise, float rho, float endFrac, float startFrac){
    List<float[]> pts=new ArrayList<>(); float r=rho*a, h=a/2; double rot=Math.toRadians(rotDeg);
    // parcours du périmètre en partant du milieu du haut, sens horaire
    List<float[]> base=new ArrayList<>();
    float[][] centers={{h-r,-h+r},{h-r,h-r},{-h+r,h-r},{-h+r,-h+r}}; double[] a0={-90,0,90,180};
    base.add(new float[]{0,-h});
    for(int c=0;c<4;c++){ float[] ce=centers[c];
      // côté droit jusqu'au début de l'arc
      double st=Math.toRadians(a0[c]); float sx=ce[0]+(float)(r*Math.cos(st)), sy=ce[1]+(float)(r*Math.sin(st)); base.add(new float[]{sx,sy});
      for(int k=1;k<=6;k++){ double t=Math.toRadians(a0[c]+90.0*k/6); base.add(new float[]{ce[0]+(float)(r*Math.cos(t)), ce[1]+(float)(r*Math.sin(t))}); } }
    base.add(new float[]{0,-h});
    // échantillonnage tous les ~1.5 unités
    List<float[]> dense=new ArrayList<>(); for(int i=1;i<base.size();i++){ float[] A=base.get(i-1),B=base.get(i); double d=Math.hypot(B[0]-A[0],B[1]-A[1]); int m=Math.max(1,(int)(d/1.5)); for(int k=0;k<m;k++){ float t=k/(float)m; dense.add(new float[]{A[0]+(B[0]-A[0])*t, A[1]+(B[1]-A[1])*t}); } }
    int keep = (int)(dense.size()*(1+endFrac)); 
    float wob=0;
    int off=(int)(startFrac*dense.size());
    for(int i=0;i<keep;i++){ float[] q=dense.get((i+off)%dense.size()); wob = wob*0.8f + (float)rnd.nextGaussian()*noise*0.6f; float x=q[0]+wob+(float)rnd.nextGaussian()*noise*0.3f, y=q[1]+wob*0.7f+(float)rnd.nextGaussian()*noise*0.3f;
      double xr=x*Math.cos(rot)-y*Math.sin(rot), yr=x*Math.sin(rot)+y*Math.cos(rot); pts.add(new float[]{cx+(float)xr, cy+(float)yr}); }
    return path(pts);
  }
  static Stroke ellipse(float cx, float cy, float rx, float ry, float noise){ List<float[]> p=new ArrayList<>(); double t0=rnd.nextDouble()*2*Math.PI, ext=(rnd.nextDouble()-0.4)*0.25; int N=(int)(Math.PI*(rx+ry)/1.2); float wob=0;
    for(int i=0;i<=N*(1+ext);i++){double t=t0+2*Math.PI*i/N; wob=wob*0.85f+(float)rnd.nextGaussian()*noise*0.5f; p.add(new float[]{cx+(float)((rx+wob)*Math.cos(t)+rnd.nextGaussian()*noise*0.25), cy+(float)((ry+wob)*Math.sin(t)+rnd.nextGaussian()*noise*0.25)});} return path(p); }
  static Stroke triangle(float cx, float cy, float a){ List<float[]> p=new ArrayList<>(); float[][] v={{cx,cy-a/2},{cx+a/2,cy+a/2},{cx-a/2,cy+a/2},{cx,cy-a/2}}; for(int s=0;s<3;s++) for(int k=0;k<20;k++){float t=k/20f; p.add(new float[]{v[s][0]+(v[s+1][0]-v[s][0])*t, v[s][1]+(v[s+1][1]-v[s][1])*t});} p.add(v[3]); return path(p); }
  static Stroke circle(float cx, float cy, float r, float noise){ return ellipse(cx, cy, r, r, noise); }
  static Stroke letterD(float x, float y, float s){ List<float[]> p=new ArrayList<>(); for(int i=0;i<=15;i++) p.add(new float[]{x, y+s*i/15f}); for(int i=0;i<=30;i++){double t=Math.PI/2 - Math.PI*i/30; p.add(new float[]{x+(float)(s*0.5*Math.cos(t)), y+s/2-(float)(s/2*Math.sin(t))});} return path(p); }

  public static void main(String[] a) throws Exception {
    // ---------------- reconnaissance des carrés
    check(Shapes.recognizeBox(handSquare(300,400,40,0,0.3f,0,0)) != null, "carré net reconnu");
    check(Shapes.recognizeBox(roundSquare(300,400,40,0,0.5f,0.1f,0f,0f)) != null, "carré commencé dans un coin");
    check(Shapes.recognizeBox(roundSquare(300,400,36,4,0.5f,0.25f,-0.05f,0.3f)) != null, "carré tremblé, coins arrondis, tourné 4°, pas fermé");
    check(Shapes.recognizeBox(roundSquare(300,400,50,22,0.6f,0.15f,0.05f,0.6f)) != null, "carré penché de 22°");
    check(Shapes.recognizeBox(roundSquare(300,400,140,-3,0.8f,0.1f,0.02f,0.2f)) != null, "grand carré (140)");
    check(Shapes.recognizeBox(dense(100,100, 140,104, 150,150, 96,142, 101,103)) != null, "quadrilatère de travers (trapèze)");
    { List<Stroke> two = new ArrayList<>();
      two.add(dense(100,140, 100,100, 140,100)); two.add(dense(140,101, 141,140, 101,141));
      Shapes.Box b = Shapes.findBox(two); check(b != null && b.strokes == 2, "carré en deux traits (Γ puis ⅃)"); }
    { List<Stroke> four = new ArrayList<>();
      four.add(dense(96,100, 145,101)); four.add(dense(140,95, 141,146)); four.add(dense(104,141, 146,140)); four.add(dense(100,96, 99,145));
      Shapes.Box b = Shapes.findBox(four); check(b != null && b.strokes == 4 && Math.abs(b.cx-120)<6 && Math.abs(b.cy-120)<6, "carré en quatre traits qui se croisent aux coins"); }
    { List<Stroke> l = new ArrayList<>(); l.add(dense(100,100, 100,150)); l.add(dense(120,100, 120,150));
      check(Shapes.findBox(l) == null, "deux traits parallèles refusés"); }
    check(Shapes.recognizeBox(handSquare(300,400,55,-6,0.5f,0.2f,0.1f)) != null, "carré 55, tourné -6°");
    int okCount=0; for(int i=0;i<500;i++){ if(Shapes.recognizeBox(roundSquare(300,400,26+rnd.nextFloat()*50,(rnd.nextFloat()-0.5)*14,0.3f+rnd.nextFloat()*1.4f,rnd.nextFloat()*0.2f,-0.1f+rnd.nextFloat()*0.25f))!=null) okCount++; }
    System.out.println("     carrés manuscrits réalistes reconnus : "+okCount+"/500");
    check(okCount >= 450, "carrés réalistes (même très tremblés) reconnus >= 90 %");
    int okDent=0; for(int i=0;i<200;i++){ if(Shapes.recognizeBox(handSquare(300,400,30+rnd.nextFloat()*40,(rnd.nextFloat()-0.5)*10,0.2f+rnd.nextFloat()*0.4f,rnd.nextFloat()*0.3f,rnd.nextFloat()*0.12f))!=null) okDent++; }
    System.out.println("     carrés irréguliers (encoches profondes aux coins) reconnus : "+okDent+"/200");
    check(okDent >= 140, "carrés à encoches profondes reconnus >= 70 %");
    int ell=0; for(int i=0;i<200;i++) if(Shapes.recognizeBox(ellipse(300,300,15+rnd.nextFloat()*25,15+rnd.nextFloat()*25,0.3f+rnd.nextFloat()))!=null) ell++;
    System.out.println("     ellipses prises pour des carrés : "+ell+"/200");
    check(ell == 0, "aucune ellipse reconnue");
    check(Shapes.recognizeBox(triangle(300,300,40)) == null, "triangle refusé");
    int circ=0; for(int i=0;i<200;i++) if(Shapes.recognizeBox(circle(300,300,15+rnd.nextFloat()*30,0.3f+rnd.nextFloat()*1.5f))!=null) circ++;
    System.out.println("     ronds pris pour des carrés : "+circ+"/200");
    check(circ == 0, "aucun rond reconnu");
    check(Shapes.recognizeBox(letterD(100,100,40)) == null, "lettre D refusée");
    check(Shapes.recognizeBox(handSquare(300,400,200,0,0.5f,0,0)) == null, "grand carré (cadre) refusé");
    Stroke rect = stroke(0, 100,100, 180,100, 180,130, 100,130, 100,100, 120,100,140,100,160,100); 
    check(Shapes.recognizeBox(rect) == null, "rectangle allongé refusé");
    check(Shapes.recognizeBox(handSquare(300,400,40,0,0.3f,0,0.6f)) == null, "U ouvert refusé");

    // ---------------- polygone / lasso
    float[] poly = {0,0, 100,0, 100,100, 0,100};
    check(Shapes.inside(poly,4,50,50) && !Shapes.inside(poly,4,150,50), "point dans polygone");
    Stroke inS = stroke(0, 10,10, 50,50, 90,90); check(inS.insideFraction(poly,4) == 1f, "trait entièrement dans le lasso");
    Obj ob = new Obj(Obj.STAMP,0,0xFFFF0000, 95,50, 40,20, false, ""); float fr = ob.insideFraction(poly,4); check(fr > 0.3f && fr < 0.9f, "objet à cheval : fraction "+fr);

    // ---------------- hauteur de page
    ArrayList<Item> items = new ArrayList<>();
    check(Shapes.pageHeight(items) == Geo.H, "page vide = hauteur normale");
    items.add(stroke(0, 100,1262, 200,1270)); check(Shapes.pageHeight(items) == Geo.H + Geo.EXT, "écrire en bas allonge la page : "+Shapes.pageHeight(items));
    items.clear(); items.add(stroke(0, 100,900, 200,900)); check(Shapes.pageHeight(items) == Geo.H, "milieu de page : pas d'allongement");

    // ---------------- Edit / historique
    File dir = java.nio.file.Files.createTempDirectory("agenda").toFile();
    android.content.Context ctx = new android.content.Context(dir);
    // fichier au format 1 (versions précédentes) avant de démarrer le magasin
    new File(dir,"pages").mkdirs();
    long today = Days.fromYmd(2026,11,6);
    File v1 = new File(dir,"pages/"+Days.key(today)+".ink");
    Stroke old1 = stroke(0xFF1F5AD6, 120,300, 300,310, 500,305);
    try (DataOutputStream o = new DataOutputStream(new FileOutputStream(v1))) {
      o.writeInt(PageStore.MAGIC); o.writeInt(1); o.writeInt(1);
      o.writeInt(old1.color); o.writeFloat(old1.width); o.writeInt(old1.flags); o.writeInt(old1.n); for(int i=0;i<old1.n*3;i++) o.writeFloat(old1.pts[i]);
    }
    PageStore st = PageStore.get(ctx);
    Thread.sleep(300); android.os.Handler.drain();
    check(st.summariesLoaded(), "résumés chargés");
    check(st.hasContent(today) && st.summary(today) == PageStore.Summary.EMPTY, "ancien fichier : contenu détecté, résumé vide");
    Page p = st.page(today);
    check(p.items.size()==1 && Arrays.equals(((Stroke)p.items.get(0)).pts, old1.pts), "ancien format (v1) relu à l'identique");

    Edit e = new Edit(); Stroke s2 = stroke(0, 100,400, 300,400); e.add(p, s2); st.commit(e);
    Obj box = new Obj(Obj.BOX,0,0xFF000000, 80,460, 44,44, false, ""); Edit e2 = new Edit(); e2.add(p, box); st.commit(e2);
    Edit e3 = new Edit(); e3.replace(p, box, box.withChecked(true)); st.commit(e3);
    check(p.items.size()==3 && ((Obj)p.items.get(2)).checked, "case cochée en place");
    check(st.summary(today).boxes==1 && st.summary(today).checked==1, "résumé : 1 case, 1 cochée");
    st.undo(p); check(!((Obj)p.items.get(2)).checked && p.items.get(2)==box, "annuler : case décochée (même objet)");
    st.redo(p); check(((Obj)p.items.get(2)).checked, "rétablir : recochée");
    // gomme : plusieurs retraits successifs
    List<Item> before = new ArrayList<>(p.items);
    Edit er = new Edit(); er.removeAt(p, 1); er.removeAt(p, 0); st.commit(er);
    check(p.items.size()==1, "gomme : 2 éléments retirés");
    st.undo(p); check(p.items.equals(before), "annuler gomme : ordre exact restauré");
    // déplacement vers un autre jour (2 pages)
    Page q = st.page(today+1);
    Edit mv = new Edit(); List<Item> sel = new ArrayList<>(); sel.add(p.items.get(1)); sel.add(p.items.get(2));
    List<Item> moved = new ArrayList<>(); for(Item it: sel) moved.add(it.transformed(0,0,1,0,-100));
    mv.remove(p, sel); mv.insert(q, q.items.size(), moved); st.commit(mv);
    check(p.items.size()==1 && q.items.size()==2, "reporter à demain : 2 éléments déplacés");
    check(p.canUndo() && q.canUndo(), "historique sur les deux pages");
    st.undo(q); check(p.items.equals(before) && q.items.isEmpty(), "annuler depuis demain : tout revient");
    check(p.canRedo() && q.canRedo() && p.undo.size()==q.undo.size()-0 - 0 + p.undo.size()-q.undo.size() + q.undo.size(), "rétablir disponible des deux côtés");
    st.redo(p); check(p.items.size()==1 && q.items.size()==2, "rétablir depuis aujourd'hui");
    st.undo(p);
    Edit nw = new Edit(); nw.add(q, stroke(0,1,1,2,2)); st.commit(nw);
    check(!q.canRedo() && !p.canRedo(), "nouvelle modif sur demain : rétablir effacé des deux côtés");

    // ---------------- format 2 aller-retour
    Obj stamp = new Obj(Obj.STAMP, 2, 0xFFE07B00, 500, 600, 120, 46, false, "");
    Obj subj = new Obj(Obj.SUBJECT, 3, 0xFFE07B00, 300, 650, 160, 40, false, "Électronique");
    Obj subj2 = new Obj(Obj.SUBJECT, 1, 0xFF2B5FD0, 300, 700, 120, 40, false, "Maths");
    Obj sticker = new Obj(Obj.STICKER, 5, 0, 700, 700, 84, 84, false, "");
    Stroke hl = new Stroke(0x6EFFD21A, 24f, Stroke.FLAG_HIGHLIGHTER, 2); hl.add(10,10,1); hl.add(300,12,1); hl.trim();
    Edit e4 = new Edit(); e4.insert(p, p.items.size(), Arrays.<Item>asList(stamp, subj, subj2, sticker, hl)); st.commit(e4);
    st.flush();
    File f2 = st.fileFor(today);
    List<Item> back = new ArrayList<>(); PageStore.read(f2, back);
    check(back.size()==p.items.size(), "v2 : nombre d'éléments relus "+back.size());
    boolean same = true;
    for (int i=0;i<back.size();i++){ Item x=back.get(i), y=p.items.get(i);
      if (x.getClass()!=y.getClass()) same=false;
      else if (x instanceof Stroke){ Stroke sx=(Stroke)x, sy=(Stroke)y; same &= sx.color==sy.color && sx.width==sy.width && sx.flags==sy.flags && Arrays.equals(sx.pts, sy.pts); }
      else { Obj ox=(Obj)x, oy=(Obj)y; same &= ox.type==oy.type && ox.kind==oy.kind && ox.color==oy.color && ox.x==oy.x && ox.y==oy.y && ox.w==oy.w && ox.h==oy.h && ox.checked==oy.checked && ox.text.equals(oy.text); } }
    check(same, "v2 : tous les champs identiques (traits, cases, tampons, matières, autocollants)");
    PageStore.Summary sm = PageStore.readSummary(f2);
    check(sm.boxes==1 && sm.checked==1 && sm.subjects.length==2 && sm.subjects[0]==3 && sm.subjects[1]==1, "résumé en tête de fichier (cases + couleurs matières)");
    // vrac
    Page v = st.page(Geo.vrac(1)); Edit ev = new Edit(); ev.add(v, stroke(0,5,5,50,50)); st.commit(ev); st.flush();
    check(new File(dir,"vrac/0001.ink").exists() && st.lastVracPage()==1, "page vrac 1 enregistrée à part");
    check(!st.summaries().containsKey(Geo.vrac(1)), "le vrac ne compte pas dans les statistiques");
    // fichier tronqué
    byte[] all = java.nio.file.Files.readAllBytes(f2.toPath()); File tr = new File(dir,"tr.ink"); java.nio.file.Files.write(tr.toPath(), Arrays.copyOf(all, all.length-20));
    List<Item> part = new ArrayList<>(); PageStore.read(tr, part); check(part.size()==back.size()-1, "fichier tronqué : récupère tout sauf le dernier ("+part.size()+")");
    // effacer tout -> fichier supprimé
    Edit clr = new Edit(); clr.remove(v, new ArrayList<>(v.items)); st.commit(clr); st.flush();
    check(!new File(dir,"vrac/0001.ink").exists() && st.lastVracPage()==0, "page vidée : fichier supprimé");

    // ---------------- réglages
    List<Settings.Subject> subs = new ArrayList<>(Settings.defaultSubjects()); subs.add(new Settings.Subject(9,"Tab\tet\nligne",0xFF123456));
    List<Settings.Subject> sb = Settings.decodeSubjects(Settings.encodeSubjects(subs));
    check(sb.size()==8 && sb.get(7).name.equals("Tab et ligne") && sb.get(7).color==0xFF123456 && sb.get(2).name.equals("Électronique"), "matières : encodage aller-retour");
    List<Settings.Slot> sl = Arrays.asList(new Settings.Slot(1, 615, 735, 3, "B204"), new Settings.Slot(0, 480, 600, 1, ""), new Settings.Slot(0, 300, 200, 1, "bad"));
    List<Settings.Slot> sl2 = Settings.decodeSlots(Settings.encodeSlots(sl));
    check(sl2.size()==2 && sl2.get(0).dow==0 && sl2.get(1).room.equals("B204"), "emploi du temps : encodage, tri, créneau invalide ignoré");
    check(Settings.fmtTime(480).equals("8h") && Settings.fmtTime(615).equals("10h15") && Settings.fmtTime(545).equals("9h05"), "format des heures");

    System.out.println(fails==0 ? "\nTOUS LES TESTS DU MODÈLE OK" : "\n"+fails+" ÉCHEC(S)");
    System.exit(fails==0?0:1);
  }
}
