package app.easywake;
import java.util.*;
public class TS { public static void main(String[] a){
  int N=Integer.parseInt(a[0]);
  for(int t=0;t<ThemeSlotMath.THEMES.length;t++){ Random r=new Random(7+t); double tot=0, sq=0; int hits=0, feat=0, big=0; double max=0;
    for(int i=0;i<N;i++){ ThemeSlotMath.Spin s=ThemeSlotMath.spin(t,r); tot+=s.total; if(s.total>0)hits++; if(s.freeAwarded>0||s.chests!=null)feat++; if(s.total>=20)big++; max=Math.max(max,s.total);}
    ThemeSlotMath.Theme th=ThemeSlotMath.THEMES[t];
    double rtp=tot/N; System.out.printf("%-18s RTP %.3f  -> scale for 0.95: %.3f   hit %.0f%%  feature 1 in %.0f  20x+ 1 in %.0f  max %.0fx%n",th.name,rtp,th.scale*0.95/rtp,hits*100.0/N,N/(double)Math.max(1,feat),N/(double)Math.max(1,big),max);}
}}
