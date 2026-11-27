package app.easywake;
import java.util.*;
public class ZF {
  public static void main(String[] a){
    CasinoWorld w=new CasinoWorld(5); CasinoWorld.Mesh m=w.mesh; float[] d=m.data; int S=13;
    List<float[]> q=new ArrayList<>(); // axis, plane, u0,v0,u1,v1, color, normalSign, idx
    for(int i=0;i+6*S<=m.floats;i+=6*S){
      float nx=d[i+3],ny=d[i+4],nz=d[i+5]; int ax=-1;
      if(Math.abs(ny)>0.99) ax=1; else if(Math.abs(nx)>0.99) ax=0; else if(Math.abs(nz)>0.99) ax=2;
      if(ax<0) continue;
      float[] mn={1e9f,1e9f,1e9f}, mx={-1e9f,-1e9f,-1e9f};
      for(int k=0;k<6;k++) for(int c=0;c<3;c++){ float v=d[i+k*S+c]; mn[c]=Math.min(mn[c],v); mx[c]=Math.max(mx[c],v);}
      int u=ax==0?1:0, v=ax==2?1:2;
      float col=d[i+6]*1000+d[i+7]*100+d[i+8]*10+d[i+12]*7+d[i+11];
      q.add(new float[]{ax,(mn[ax]+mx[ax])/2,mn[u],mn[v],mx[u],mx[v],col,Math.signum(ax==0?nx:ax==1?ny:nz),i});
    }
    // bucket by axis + plane coarse
    Map<String,List<float[]>> b=new HashMap<>();
    for(float[] f:q){ String k=(int)f[0]+":"+Math.round(f[1]*20); b.computeIfAbsent(k,z->new ArrayList<>()).add(f); }
    Map<String,Integer> spots=new TreeMap<>(); int exact=0,near=0;
    for(float[] f:q){
      for(int dk=-1;dk<=1;dk++){
        List<float[]> L=b.get((int)f[0]+":"+(Math.round(f[1]*20)+dk)); if(L==null) continue;
        for(float[] g:L){ if(g[8]<=f[8]) continue; if(g[7]!=f[7]) continue;
          float dp=Math.abs(g[1]-f[1]); if(dp>0.025f) continue; if(dp<1e-4 && g[6]==f[6]) continue;
          float ou=Math.min(f[4],g[4])-Math.max(f[2],g[2]), ov=Math.min(f[5],g[5])-Math.max(f[3],g[3]);
          if(ou<=0.01f||ov<=0.01f) continue;
          if(dp<1e-4) exact++; else near++;
          float cx=(Math.max(f[2],g[2])+Math.min(f[4],g[4]))/2, cy=(Math.max(f[3],g[3])+Math.min(f[5],g[5]))/2;
          String ax=f[0]==1?"floor y":(f[0]==0?"wall x":"wall z");
          String key=String.format("%s=%.3f dp=%.3f  near (%s)", ax, f[1], dp, f[0]==1?String.format("x%.0f z%.0f",Math.floor(cx/4)*4,Math.floor(cy/4)*4):String.format("%.0f,%.0f",Math.floor(cx),Math.floor(cy)));
          spots.merge(key,1,Integer::sum);
        }
      }
    }
    System.out.println("exact coplanar overlaps="+exact+" near(<2.5cm)="+near);
    int n=0; for(Map.Entry<String,Integer> e: spots.entrySet()){ if(!e.getKey().startsWith("floor y=0.0") && !e.getKey().startsWith("floor y=-")) { System.out.println(e.getValue()+"  "+e.getKey()); if(++n>80)break; } }
  }
}
