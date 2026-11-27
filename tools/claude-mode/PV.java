package app.easywake;
import java.awt.image.BufferedImage;
public class PV {
  static CasinoWorld w;
  static void init() throws Exception {
    BufferedImage at = new BufferedImage(CasinoArt.ATLAS, CasinoArt.ATLAS_H, BufferedImage.TYPE_INT_ARGB);
    CasinoArt.paintAtlas(new Preview.J2D(at), new double[]{1234, 5678, 98765, 543210, 0, 0});
    BufferedImage cp = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
    CasinoArt.paintCarpet(new Preview.J2D(cp));
    Preview.atlas = at.getRGB(0, 0, CasinoArt.ATLAS, CasinoArt.ATLAS_H, null, 0, CasinoArt.ATLAS);
    Preview.carpet = cp.getRGB(0, 0, 256, 256, null, 0, 256);
    w = new CasinoWorld(5); Preview.lm = w.bakeLight();
    int steps = System.getenv("STEPS") != null ? Integer.parseInt(System.getenv("STEPS")) : 30;
    for (int i = 0; i < steps; i++) w.update(0.033f, 0, 0);
  }
  // args: name x z yaw pitch dist  (repeat)
  public static void main(String[] a) throws Exception {
    init();
    if (System.getenv("HIDE") != null) w.player.scale = 0.01f;
    for(int i=0;i+5<a.length;i+=6) Preview.shot(w, a[i]+".png", Float.parseFloat(a[i+1]), Float.parseFloat(a[i+2]), Float.parseFloat(a[i+3]), Float.parseFloat(a[i+4]), Float.parseFloat(a[i+5]));
  }
}
