package app.easywake;
import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO; import java.util.*;
/** "Claude mode" for the casino world, off-device: seeded fixed-step runs, a motion trace of every
 *  person, bump/stall counts by place, and a top-down trail map over the solids. */
public class CM {
  public static void main(String[] a) throws Exception {
    float secs=Float.parseFloat(a[0]); String out=a[1];
    float mx0=Float.parseFloat(a[2]),mz0=Float.parseFloat(a[3]),mx1=Float.parseFloat(a[4]),mz1=Float.parseFloat(a[5]);
    CasinoWorld w=new CasinoWorld(5); float dt=1/30f;
    Map<CasinoWorld.Person,float[]> last=new HashMap<>(); Map<CasinoWorld.Person,Float> stall=new HashMap<>();
    Map<String,int[]> byPlace=new TreeMap<>(); int bumps=0, walkFrames=0, stalls=0;
    java.util.List<float[]> trail=new ArrayList<>(), bumpAt=new ArrayList<>(), stallAt=new ArrayList<>();
    for(int f=0; f<secs/dt; f++){
      for(CasinoWorld.Person p: w.people) if(p!=w.player && p.active) last.put(p,new float[]{p.x,p.z,p.walk});
      w.update(dt,0,0);
      for(CasinoWorld.Person p: w.people){ if(p==w.player||!p.active||p.sitting||p.down>0||p.ko||p.dead) continue;
        float[] l=last.get(p); if(l==null) continue;
        float moved=(float)Math.hypot(p.x-l[0],p.z-l[1]); float want=p.speed*dt;
        if(p.x>=mx0&&p.x<=mx1&&p.z>=mz0&&p.z<=mz1) trail.add(new float[]{p.x,p.z,p.kind});
        if(p.walk>0.6f && want>0){ walkFrames++;
          String pl=CasinoPlaces.placeName(p.x,p.z); int[] c=byPlace.computeIfAbsent(pl,k->new int[3]); c[0]++;
          if(moved<want*0.55f){ bumps++; c[1]++; bumpAt.add(new float[]{p.x,p.z});
            float s=stall.getOrDefault(p,0f)+dt; stall.put(p,s); if(s>1.0f&&s<1.0f+dt*1.5f){stalls++; c[2]++; stallAt.add(new float[]{p.x,p.z});} }
          else stall.put(p,0f);
        }
      }
    }
    System.out.printf("CM run %.0fs: walking frames %d, bumped %d (%.1f%%), stalls >1s %d%n",secs,walkFrames,bumps,bumps*100f/Math.max(1,walkFrames),stalls);
    for(Map.Entry<String,int[]> e: byPlace.entrySet()){ int[] c=e.getValue(); if(c[0]<200) continue; System.out.printf("  %-20s walk %6d  bump %5.1f%%  stalls %d%n",e.getKey(),c[0],c[1]*100f/c[0],c[2]); }
    // top-down map
    int S=Integer.parseInt(a.length>6?a[6]:"20"); int W=(int)((mx1-mx0)*S), H=(int)((mz1-mz0)*S);
    BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB); Graphics2D g=img.createGraphics();
    g.setColor(new Color(20,20,24)); g.fillRect(0,0,W,H);
    for(float[] s: w.walls){ g.setColor(s.length>5?new Color(70,70,110):new Color(110,110,110)); g.fillRect((int)((s[0]-mx0)*S),(int)((s[1]-mz0)*S),Math.max(1,(int)((s[2]-s[0])*S)),Math.max(1,(int)((s[3]-s[1])*S))); }
    for(float[] t: trail){ g.setColor(new Color(60,200,255,40)); g.fillRect((int)((t[0]-mx0)*S),(int)((t[1]-mz0)*S),2,2); }
    for(float[] t: bumpAt){ if(t[0]<mx0||t[0]>mx1||t[1]<mz0||t[1]>mz1) continue; g.setColor(new Color(255,150,0,90)); g.fillRect((int)((t[0]-mx0)*S)-1,(int)((t[1]-mz0)*S)-1,3,3); }
    for(float[] t: stallAt){ if(t[0]<mx0||t[0]>mx1||t[1]<mz0||t[1]>mz1) continue; g.setColor(Color.RED); g.fillOval((int)((t[0]-mx0)*S)-4,(int)((t[1]-mz0)*S)-4,8,8); }
    for(CasinoWorld.Spot sp: w.spots){ if(sp.x<mx0||sp.x>mx1||sp.z<mz0||sp.z>mz1) continue; g.setColor(new Color(120,255,120)); g.fillRect((int)((sp.x-mx0)*S)-2,(int)((sp.z-mz0)*S)-2,4,4);}
    ImageIO.write(img,"png",new File(out));
  }
}
