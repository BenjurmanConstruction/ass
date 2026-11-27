package app.easywake;
import java.util.*;
public class TP {
  public static void main(String[] a){
    Random r=new Random(1);
    // breaks
    int pots=0, scr=0; float spread=0; int N=200;
    for(int i=0;i<N;i++){ PoolGame g=new PoolGame(i); g.place(PoolGame.HEAD-0.05f,PoolGame.H/2+(r.nextFloat()-.5f)*0.2f);
      float ang=(float)Math.atan2(PoolGame.FOOT_Y-g.y[0],PoolGame.FOOT_X-g.x[0])+(float)r.nextGaussian()*0.004f;
      g.playOut(ang,0.95f,0); pots+=g.potted.size(); if(g.scratched)scr++;
      float mx=0,my=0;int n=0; for(int b=1;b<16;b++) if(g.on[b]){mx+=g.x[b];n++;} spread+= n>0?mx/n:0; }
    System.out.printf("break: avg potted %.2f, scratch %d%%, avg ball x %.2f (table %.2f)%n",pots/(float)N,scr*100/N,spread/N,PoolGame.W);
    float[][] skills={{0.2f,0.2f},{0.5f,0.5f},{0.9f,0.9f},{0.9f,0.2f},{0.7f,0.4f}};
    for(float[] sk: skills){
      int w0=0,turns=0,games=12; long t0=System.currentTimeMillis(); int potsTot=0,shots=0, fouls=0;
      for(int gi=0;gi<games;gi++){ PoolGame g=new PoolGame(100+gi); int t=0;
        while(g.winner<0 && t<300){ PoolGame.Plan p=PoolGame.plan(g, sk[g.turn], r); if(g.ballInHand) g.place(p.placeX>0?p.placeX:g.x[0], p.placeX>0?p.placeY:g.y[0]); int before=g.potted.size();
          g.playOut(p.angle,p.power,p.spin); t++; shots++; potsTot+=g.potted.size(); if(g.lastFoul!=null)fouls++; }
        if(g.winner==0)w0++; turns+=t; }
      System.out.printf("skill %.1f vs %.1f: first player wins %d/%d, avg shots %.0f, balls per shot %.2f, fouls %.0f%%, %.0f ms/shot%n",sk[0],sk[1],w0,games,turns/(float)games,potsTot/(float)shots,fouls*100f/shots,(System.currentTimeMillis()-t0)/(float)shots);
    }
  }
}
