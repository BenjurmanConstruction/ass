package app.easywake;
public class TPerf { public static void main(String[] a){ long t0=System.nanoTime(); CasinoWorld w=new CasinoWorld(5); long t1=System.nanoTime();
  for(int i=0;i<300;i++) w.update(1/30f,0,0); long t2=System.nanoTime(); long worst=0;
  for(int i=0;i<900;i++){ long s=System.nanoTime(); w.update(1/30f,0,0); worst=Math.max(worst,System.nanoTime()-s);} long t3=System.nanoTime();
  System.out.printf("world build %.0f ms (nav grid %dx%d), update avg %.2f ms, worst %.2f ms%n",(t1-t0)/1e6,w.nav.nx,w.nav.nz,(t3-t2)/900e6,worst/1e6);}}
