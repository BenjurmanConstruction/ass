package app.easywake;
public class TDrive { public static void main(String[] a){
  CasinoWorld w=new CasinoWorld(77); CasinoTraffic.Car car=null;
  for (CasinoTraffic.Car c: w.traffic.cars) if(c.role==CasinoTraffic.PARKED && c.type!=CasinoTraffic.POLICE){car=c;break;}
  car.x=-20; car.z=CasinoCity.ST_Z[1]+2; car.h=(float)Math.PI/2; w.traffic.enter(car);
  float maxPitchB=0,maxPitchA=0,maxRoll=0,maxSlip=0,maxSteer=0; float t=0;
  // accelerate 3s, brake 1.5s, accelerate 2s, hard turn 1s, handbrake turn 1s
  while(t<9){ for(CasinoTraffic.Car c: w.traffic.cars) if(c!=car){c.x=500;c.z=500;c.rail=false;}
    float sx=0, sy=-1; car.handbrake=false;
    if(t>3&&t<4.5f) sy=1; if(t>6.5f&&t<7.5f){sx=1;} if(t>7.5f&&t<8.5f){sx=-1; car.handbrake=true;}
    w.update(1/30f,sx,sy); t+=1/30f;
    if(t<3) maxPitchA=Math.max(maxPitchA,car.pitch); if(t>3&&t<4.5f) maxPitchB=Math.min(maxPitchB,car.pitch);
    maxRoll=Math.max(maxRoll,Math.abs(car.roll)); maxSlip=Math.max(maxSlip,Math.abs(car.slip)); maxSteer=Math.max(maxSteer,Math.abs(car.steerVis)); }
  System.out.printf("nose up when flooring it %.3f rad, nose dive braking %.3f rad, body lean %.3f rad, slide %.1f m/s, front wheels steer up to %.0f deg, skid marks %d, hp %.0f%n",maxPitchA,maxPitchB,maxRoll,maxSlip,Math.toDegrees(maxSteer),w.traffic.skids.size(),car.health);
}}
