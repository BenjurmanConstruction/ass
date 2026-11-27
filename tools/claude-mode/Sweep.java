package app.easywake;
/** Claude-mode sweep: every spot standable and reachable, nobody starting inside a solid. */
public class Sweep { public static void main(String[] a){
  CasinoWorld w=new CasinoWorld(5); CasinoNav n=w.nav; int bad=0;
  float sx=w.player.x, sz=w.player.z;
  for(CasinoWorld.Spot s: w.spots){
    boolean open=n.open(s.x,s.z);
    float[] path=n.find(sx,sz,s.x,s.z,200000);
    float end= path==null?99:(float)Math.hypot(path[path.length-2]-s.x,path[path.length-1]-s.z);
    if(!open||path==null){ bad++; System.out.printf("SPOT %-34s at (%.1f,%.1f): %s%n",s.label,s.x,s.z,!open?"inside a solid's margin":"no route from the start"); }
  }
  for(CasinoWorld.Person p: w.people){ if(p.sitting||!p.active) continue; if(!n.open(p.x,p.z)) { bad++; System.out.printf("PERSON %s kind %d starts in a solid at (%.1f,%.1f)%n",p.name,p.kind,p.x,p.z);} }
  System.out.println("spots "+w.spots.size()+", problems "+bad);
}}
