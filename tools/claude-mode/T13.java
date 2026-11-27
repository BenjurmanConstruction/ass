package app.easywake;
public class T13 {
  static CasinoWorld.Person setup(CasinoWorld w, int style, float dx){
    w.player.x=-30; w.player.z=16;
    for (CasinoWorld.Person p: w.people) if(!p.fighter && Math.hypot(p.x+30,p.z-16)<30) p.active=false;
    CasinoWorld.Person v=w.spawnPedestrian(-30+dx,16);
    v.x=-30+dx; v.z=16; v.active=true; w.fight.makeFighter(v, style); v.startedIt=true; v.reported=true; return v;
  }
  static float[] run(int seed,int style,int mode,int foes){
    CasinoWorld w=new CasinoWorld(seed); CasinoWorld.Person[] v=new CasinoWorld.Person[foes];
    for(int i=0;i<foes;i++) v[i]=setup(w,style,2+i*0.8f);
    float t=0, next=0; boolean blocking=false; float minHp=100;
    while(t<60){
      if(mode==1 && t>=next){ if(Math.random()<0.75) w.fight.punchPressed(); else w.fight.kickPressed(); next=t+0.28f; }
      if(mode==2){ // smart: block when someone is winding up, punch otherwise
        boolean threat=false; for(CasinoWorld.Person p: v) if(CasinoFight.attacking(p)&&p.actT<p.actWind&&!p.ko) threat=true;
        if(threat && !blocking){ w.fight.block(true); blocking=true; }
        else if(!threat && blocking){ w.fight.block(false); blocking=false; }
        else if(!threat && t>=next){ w.fight.punchPressed(); next=t+0.3f; }
      }
      w.update(1/30f,0,0); t+=1/30f; minHp=Math.min(minHp,w.health);
      boolean all=true, anyKo=false; for(CasinoWorld.Person p: v){ if(p.ko) anyKo=true; if(!(p.ko||!p.fighter)) all=false; }
      if(all) return new float[]{t,minHp,anyKo?1:2};
      if(w.health<=0) return new float[]{t,minHp,0};
    }
    return new float[]{t,minHp,-1};
  }
  public static void main(String[] a){
    String[] names={"brawler","boxer","drunk","bruiser"};
    String[] modes={"stand still","button-mash","block+punch"};
    for(int foes=1;foes<=2;foes++) for(int s=0;s<4;s++) for(int m=0;m<3;m++){
      int ko=0,fled=0,lost=0,to=0; float tt=0,hp=0; int n=10;
      for(int i=0;i<n;i++){ float[] r=run(100+i*7+s,s,m,foes); if(r[2]==1)ko++; else if(r[2]==2)fled++; else if(r[2]==0)lost++; else to++; tt+=r[0]; hp+=r[1]; }
      System.out.printf("%d x %-8s %-12s KO %2d ran %2d lost %2d draw %2d  avg %4.1fs  lowest hp %3.0f%n",foes,names[s],modes[m],ko,fled,lost,to,tt/n,hp/n);
    }
  }
}
