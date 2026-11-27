package app.easywake;
public class CarFilm { public static void main(String[] a) throws Exception {
  PV.init(); CasinoWorld w=PV.w; w.player.scale=0.01f;
  CasinoTraffic.Car car=null; for(CasinoTraffic.Car c: w.traffic.cars) if(c.role==CasinoTraffic.PARKED && c.type==CasinoTraffic.SEDAN){car=c;break;}
  if(car==null) for(CasinoTraffic.Car c: w.traffic.cars) if(c.role==CasinoTraffic.PARKED){car=c;break;}
  for(CasinoTraffic.Car c: w.traffic.cars) if(c!=car && Math.hypot(c.x+33,c.z)<12){c.x=500;}
  car.x=-33; car.z=0; car.h=(float)Math.PI/2; car.color=0xFF2A5AA8; car.type=CasinoTraffic.SEDAN;
  for(int i=0;i<5;i++) w.update(1/30f,0,0);
  Preview.shot(w,"car_clean_front.png",-33,0,-0.6f,0.25f,6.5f);
  Preview.shot(w,"car_clean_back.png",-33,0,2.4f,0.25f,6.5f);
  w.traffic.impact(car,40,true,1,0,18);   // front into a wall
  w.traffic.impact(car,25,true,0,1,12);   // right side (south)
  w.traffic.impact(car,30,true,-1,0,14);  // rear
  for(int i=0;i<20;i++) w.update(1/30f,0,0);
  Preview.shot(w,"car_hit_front.png",-33,0,-0.6f,0.25f,6.5f);
  Preview.shot(w,"car_hit_side.png",-33,0,0.9f+(float)Math.PI/2,0.2f,6f);
}}
