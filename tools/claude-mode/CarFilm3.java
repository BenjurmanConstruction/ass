package app.easywake;
public class CarFilm3 { public static void main(String[] a) throws Exception {
  PV.init(); CasinoWorld w=PV.w; w.player.scale=0.01f;
  CasinoTraffic.Car car=null; for(CasinoTraffic.Car c: w.traffic.cars) if(c.role==CasinoTraffic.PARKED&&c.type!=CasinoTraffic.POLICE){car=c;break;}
  for(CasinoTraffic.Car c: w.traffic.cars) if(c!=car && Math.hypot(c.x+33,c.z)<12){c.x=500;}
  car.x=-33; car.z=0; car.h=(float)Math.PI/2; car.color=0xFFC8CCD2; car.type=CasinoTraffic.SEDAN;
  for(int i=0;i<3;i++) w.update(1/30f,0,0); car.x=-33; car.z=0; car.speed=0; car.h=(float)Math.PI/2; car.driver=true;
  Preview.shot(w,"in_side.png",-33,0,(float)Math.PI,0.12f,4.2f);
  Preview.shot(w,"in_front.png",-33,0,-1.2f,0.25f,4.5f);
  Preview.shot(w,"in_top.png",-33,0,-0.5f,0.7f,4.5f);
  car.type=CasinoTraffic.SUV; car.color=0xFF2A4A2A;
  Preview.shot(w,"in_suv.png",-33,0,2.6f,0.2f,5f);
}}
