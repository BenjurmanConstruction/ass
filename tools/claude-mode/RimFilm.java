package app.easywake;
public class RimFilm { public static void main(String[] a) throws Exception {
  PV.init(); CasinoWorld w=PV.w; w.player.scale=0.01f;
  java.util.List<CasinoTraffic.Car> mine=new java.util.ArrayList<>();
  for(CasinoTraffic.Car c: w.traffic.cars) if(Math.hypot(c.x+33,c.z)<16) c.x=500;
  for(int i=0;i<6;i++){ CasinoTraffic.Car c=new CasinoTraffic.Car(); c.x=-46+i*5.2f; c.z=0; c.h=(float)Math.PI/2; c.prevH=c.h; c.color=0xFF2A2A30; c.type=CasinoTraffic.SEDAN; c.role=CasinoTraffic.PARKED; c.rim=i; w.traffic.cars.add(c); mine.add(c);}
  w.update(1/30f,0,0);
  for(CasinoTraffic.Car c: mine){ c.x=-46+mine.indexOf(c)*5.2f; c.z=0; c.h=(float)Math.PI/2; c.speed=0; c.bounce=0;c.bounceV=0;c.roll=0;c.pitch=0;}
  Preview.shot(w,"rims.png",-33,0,0f,0.08f,9f); Preview.shot(w,"rims2.png",-46,0,0f,0.05f,2.6f);
  CasinoTraffic.Car x=mine.get(2); for(CasinoTraffic.Car c: mine) if(c!=x) c.x=500;
  x.x=-33; x.z=0; x.color=0xFFB81E5A; x.neon=0xFF30E0FF; x.stripes=0xFFF2F2F2; x.spoiler=true; x.lowered=true; x.tint=1; x.rim=2;
  Preview.shot(w,"custom.png",-33,0,-0.7f,0.22f,6f);
}}
