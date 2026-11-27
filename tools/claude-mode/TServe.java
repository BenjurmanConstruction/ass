package app.easywake;
public class TServe { public static void main(String[] a){
  CasinoWorld w=new CasinoWorld(5); float dt=1/30f;
  java.util.Map<CasinoWorld.Person,int[]> st=new java.util.HashMap<>();
  for(CasinoWorld.Person p: w.people) if(p.patrons!=null){ st.put(p,new int[]{p.serveState,0,0}); System.out.println(p.name+" serves "+p.patrons.size()+" guests"); }
  java.util.Map<CasinoWorld.Person,Float> stuck=new java.util.HashMap<>();
  for(int f=0; f<180/dt; f++){ w.update(dt,0,0);
    for(CasinoWorld.Person p: st.keySet()){ int[] s=st.get(p);
      if(p.serveState!=s[0]){ if(s[0]==1 && p.serveState==3 && p.carry==0) s[1]++; s[0]=p.serveState; stuck.put(p,0f);}
      if((p.serveState==1||p.serveState==3) && p.walk<0.2f){ float t=stuck.getOrDefault(p,0f)+dt; stuck.put(p,t); if(t>3 && t<3+dt*1.5f){ s[2]++; System.out.printf("  %s seems stuck at (%.1f,%.1f) state %d%n",p.name,p.x,p.z,p.serveState);} } else stuck.put(p,0f);
    }
  }
  for(CasinoWorld.Person p: st.keySet()){ int held=0; for(CasinoWorld.Person q: p.patrons) if(q.carry!=0) held++;
    System.out.printf("%-18s drinks served in 3 min: %d, stuck episodes: %d, guests holding a drink now: %d/%d%n",p.name,st.get(p)[1],st.get(p)[2],held,p.patrons.size()); }
}}
