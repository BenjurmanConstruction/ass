package app.easywake;
public class TSit { public static void main(String[] a){
  CasinoWorld w=new CasinoWorld(5); for(int i=0;i<60;i++) w.update(1/30f,0,0);
  for(CasinoWorld.Person p: w.people){ if(!p.sitting||!p.active) continue;
    final float[] min={1e9f}; final float[] cube={-0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f};
    java.util.List<CasinoWorld.Person> only=new java.util.ArrayList<>(w.people); 
    for(CasinoWorld.Person q: w.people) if(q!=p) q.active=!q.active? q.active : q.active; // no-op
    float[] save=new float[0];
    w.drawPersonPublic(p, new CasinoWorld.PartSink(){ public void part(float[] m,int c,float g){ for(int k=0;k<4;k++){ float x=cube[k*3],y=cube[k*3+1],z=cube[k*3+2]; float wy=m[1]*x+m[5]*y+m[9]*z+m[13]; min[0]=Math.min(min[0],wy);} } public void shadow(float x,float z,float r){} });
    System.out.printf("%-20s seat %.2f m at (%.1f,%.1f): lowest point %.2f m above the floor%n", p.name==null?"guest":p.name, p.seatY, p.x, p.z, min[0]);
  }
}}
