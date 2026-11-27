package app.easywake;
public class T16 {
  // Drive east on a street, turn right (south) onto an avenue at the junction, holding gas.
  static String run(float speedAtTurn, boolean right){
    CasinoWorld w=new CasinoWorld(77);
    CasinoTraffic.Car car=null;
    for (CasinoTraffic.Car c: w.traffic.cars) if(c.role==CasinoTraffic.PARKED && c.type!=CasinoTraffic.POLICE){car=c;break;}
    float sz=CasinoCity.ST_Z[1], ax=CasinoCity.AVE_X[2];
    car.x=ax-60; car.z=sz+(right?CasinoCity.LANE:-CasinoCity.LANE); car.h=(float)Math.PI/2; w.traffic.enter(car);
    // clear traffic nearby
    for (CasinoTraffic.Car c: w.traffic.cars) if(c!=car && Math.hypot(c.x-ax,c.z-sz)<70){c.x=500;c.z=500;}
    float t=0; float hp0=car.health; boolean turned=false; float maxDev=0;
    while(t<20){
      float sx=0, sy=-1;
      if(!turned){
        if(car.speed>speedAtTurn) sy=0.6f; // brake to target
        if(car.x>ax-8) { sx=right?1:-1; sy= car.speed>speedAtTurn?0.5f:-0.3f; }
        float want=right?(float)Math.PI:0f;
        if(car.x>ax-8 && Math.abs(CasinoWorld.angleDiff(car.h,want))<0.25f) turned=true;
      }
      for (CasinoTraffic.Car c: w.traffic.cars) if(c!=car){c.x=500;c.z=500;c.rail=false;}
      w.update(1/30f,sx,sy); t+=1/30f;
      if(turned && t>0) maxDev=Math.max(maxDev, Math.abs(car.x-ax));
      if(turned && Math.abs(car.z-sz)>25) break;
    }

    return String.format("turn at %2.0f m/s %s: made it=%b  heading ok=%b  off-center=%.1f m  damage=%.0f",speedAtTurn,right?"right":"left",turned && Math.abs(car.z-sz)>25, turned, maxDev, hp0-car.health);
  }
  public static void main(String[] a){
    for(float v: new float[]{8,12,16}) { System.out.println(run(v,true)); System.out.println(run(v,false)); }
  }
}
