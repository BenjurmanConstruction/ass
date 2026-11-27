package app.easywake;
public class TGarage { public static void main(String[] a) throws Exception {
  CasinoWorld w=new CasinoWorld(5); System.out.println("empty: "+w.garageSave());
  CasinoTraffic.Car c=w.traffic.cars.get(3); c.driver=false; c.rail=false; c.role=CasinoTraffic.PARKED; float[] b=CasinoCity.BAYS[1];
  c.x=(b[0]+b[2])/2; c.z=(b[1]+b[3])/2; c.rim=5; c.neon=0xFF30E0FF; c.spoiler=true; c.engine=2; c.color=0xFFC81E2A;
  String s=w.garageSave(); System.out.println("saved: "+s);
  CasinoWorld w2=new CasinoWorld(9); w2.loadGarage(s); System.out.println("reloaded: "+w2.garageSave()+"  match="+s.equals(w2.garageSave()));
  // drive it out: still reachable spot?
  System.out.println("spot open at the garage: "+w2.nav.open(-74,-41.6f));
}}
