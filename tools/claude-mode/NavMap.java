package app.easywake;
public class NavMap { public static void main(String[] a){ CasinoWorld w=new CasinoWorld(5); CasinoNav n=w.nav;
  float x0=Float.parseFloat(a[0]),z0=Float.parseFloat(a[1]),x1=Float.parseFloat(a[2]),z1=Float.parseFloat(a[3]);
  for(float z=z0; z<z1; z+=0.4f){ StringBuilder sb=new StringBuilder(String.format("%5.1f ",z)); for(float x=x0;x<x1;x+=0.4f){ char c=n.open(x,z)?'.':'#'; for(float[] s: w.walls) if(s.length<6&&x>s[0]&&x<s[2]&&z>s[1]&&z<s[3]) c='X'; sb.append(c);} System.out.println(sb);} 
  System.out.print("      "); for(float x=x0;x<x1;x+=0.4f) System.out.print(Math.abs(x-Math.round(x/4)*4)<0.2f?'|':' '); System.out.println(); }}
