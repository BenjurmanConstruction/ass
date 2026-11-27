package app.easywake;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders the casino in software with the same lighting the phone uses, to PNG files. */
public class Preview {
    static class J2D implements CasinoArt.Painter {
        final Graphics2D g;

        J2D(BufferedImage img) {
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        Color c(int argb) {
            return new Color(argb, true);
        }

        public void rect(float x, float y, float w, float h, int color) {
            g.setColor(c(color));
            g.fill(new Rectangle2D.Float(x, y, w, h));
        }

        public void roundRect(float x, float y, float w, float h, float r, int color) {
            g.setColor(c(color));
            g.fill(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
        }

        public void oval(float cx, float cy, float rx, float ry, int color) {
            g.setColor(c(color));
            g.fill(new Ellipse2D.Float(cx - rx, cy - ry, rx * 2, ry * 2));
        }

        public void line(float x1, float y1, float x2, float y2, float width, int color) {
            g.setColor(c(color));
            g.setStroke(new BasicStroke(width));
            g.draw(new Line2D.Float(x1, y1, x2, y2));
        }

        public void vgradient(float x, float y, float w, float h, int top, int bottom) {
            g.setPaint(new GradientPaint(x, y, c(top), x, y + h, c(bottom)));
            g.fill(new Rectangle2D.Float(x, y, w, h));
        }

        Font font(float size, boolean bold) {
            return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
        }

        public void text(String s, float x, float y, float size, int color, boolean bold,
                         int align) {
            g.setFont(font(size, bold));
            FontMetrics fm = g.getFontMetrics();
            float w = fm.stringWidth(s);
            float dx = align == 0 ? 0 : (align == 1 ? -w / 2 : -w);
            g.setColor(c(color));
            g.drawString(s, x + dx, y + (fm.getAscent() - fm.getDescent()) / 2f);
        }

        public float measure(String s, float size, boolean bold) {
            g.setFont(font(size, bold));
            return g.getFontMetrics().stringWidth(s);
        }
    }

    static int W = 960, H = 540;
    static int[] atlas, carpet, lm;
    static int[] px;
    static float[] zb;
    static float[] eye = new float[3];
    static float[] vp = new float[16];

    public static void main(String[] a) throws Exception {
        BufferedImage at = new BufferedImage(CasinoArt.ATLAS, CasinoArt.ATLAS_H, BufferedImage.TYPE_INT_ARGB);
        CasinoArt.paintAtlas(new J2D(at), new double[]{1234, 5678, 98765, 543210, 0, 0});
        BufferedImage cp = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        CasinoArt.paintCarpet(new J2D(cp));
        ImageIO.write(at, "png", new File("atlas.png"));
        ImageIO.write(cp, "png", new File("carpet.png"));
        atlas = at.getRGB(0, 0, CasinoArt.ATLAS, CasinoArt.ATLAS_H, null, 0, CasinoArt.ATLAS);
        carpet = cp.getRGB(0, 0, 256, 256, null, 0, 256);

        CasinoWorld w = new CasinoWorld(5);
        lm = w.bakeLight();
        for (int i = 0; i < 200; i++) w.update(0.033f, 0, 0);
        String[] views = a.length > 0 ? a : new String[]{"all"};
        Views.run(w, views[0]);
    }

    static void shot(CasinoWorld w, String name, float x, float z, float yaw, float pitch,
                     float dist) throws Exception {
        if (!Float.isNaN(x) && w.traffic.driving == null) {
            w.player.x = x;
            w.player.z = z;
            w.player.yaw = yaw;
        }
        if (w.traffic.driving == null) w.camYaw = yaw;
        if (w.aiming) w.player.yaw = yaw;
        if (name.contains("bling")) w.player.yaw = yaw + (float) Math.PI;
        w.camPitch = pitch;
        w.camDist = dist;
        float[] view = new float[16], proj = new float[16];
        w.camera(W / (float) H, view, proj, eye);
        CasinoWorld.Mat.mul(vp, proj, view);
        px = new int[W * H];
        zb = new float[W * H];
        java.util.Arrays.fill(zb, Float.MAX_VALUE);
        java.util.Arrays.fill(px, 0xFF05060B);
        CasinoWorld.Mesh m = w.mesh;
        drawMesh(m.data, m.floats, null, -1, 0);
        final float[] cube = cube();
        final java.util.List<float[]> shadows = new java.util.ArrayList<>();
        w.drawPeople(new CasinoWorld.PartSink() {
            public void part(float[] model, int color, float glow) {
                drawMesh(cube, cube.length, model, color, glow);
            }

            public void shadow(float sx, float sz, float r) {
                shadows.add(new float[]{sx, sz, r});
            }
        });
        for (float[] s : shadows) shadow(s[0], s[1], s[2]);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, W, H, px, 0, W);
        // Name tags and speech, like the phone's overlay.
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float[] o = new float[3];
        for (CasinoWorld.Person p : w.people) {
            if (!p.active || p.bubble == null || p.bubbleT <= 0) continue;
            if (!CasinoWorld.Mat.project(vp, p.x, CasinoWorld.headY(p), p.z, W, H, o)) continue;
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            int tw = g.getFontMetrics().stringWidth(p.bubble);
            g.setColor(new Color(0xEE111111, true));
            g.fillRoundRect((int) o[0] - tw / 2 - 8, (int) o[1] - 26, tw + 16, 24, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(p.bubble, o[0] - tw / 2f, o[1] - 9);
        }
        if (w.aiming) {
            g.setColor(Color.WHITE);
            g.fillRect(W / 2 - 16, H / 2 - 1, 10, 2);
            g.fillRect(W / 2 + 6, H / 2 - 1, 10, 2);
            g.fillRect(W / 2 - 1, H / 2 - 16, 2, 10);
            g.fillRect(W / 2 - 1, H / 2 + 6, 2, 10);
        }
        int by = H - 70;
        for (CasinoWorld.Action act : w.actions()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            int tw = g.getFontMetrics().stringWidth(act.label);
            g.setColor(new Color(0xDDE6B869, true));
            g.fillRoundRect(W / 2 - tw / 2 - 20, by, tw + 40, 44, 22, 22);
            g.setColor(Color.BLACK);
            g.drawString(act.label, W / 2 - tw / 2, by + 28);
            by -= 54;
        }
        ImageIO.write(img, "png", new File(name));
    }

    static float[] cube() {
        CasinoWorld.Mesh m = new CasinoWorld.Mesh();
        m.box(0, 0, (float) Math.PI, 0.5f, 0.5f, -0.5f, 0.5f, 0xFFFFFFFF, 0);
        // bottom face too
        m.quad(new float[]{-0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, -0.5f},
                new float[]{0.5f, -0.5f, 0.5f}, new float[]{-0.5f, -0.5f, 0.5f}, 0xFFFFFFFF, 0);
        float[] d = new float[m.floats];
        System.arraycopy(m.data, 0, d, 0, m.floats);
        return d;
    }

    static final int S = CasinoWorld.Mesh.STRIDE;

    /** Draws triangles. model may be null; tint multiplies the color when >= 0. */
    static void drawMesh(float[] d, int n, float[] model, int tint, float glow) {
        float tr = 1, tg = 1, tb = 1;
        if (tint != -1) {
            tr = ((tint >> 16) & 255) / 255f;
            tg = ((tint >> 8) & 255) / 255f;
            tb = (tint & 255) / 255f;
        }
        float[][] v = new float[3][];
        for (int t = 0; t + 3 * S <= n; t += 3 * S) {
            for (int k = 0; k < 3; k++) {
                int i = t + k * S;
                float x = d[i], y = d[i + 1], z = d[i + 2];
                float nx = d[i + 3], ny = d[i + 4], nz = d[i + 5];
                if (model != null) {
                    float X = model[0] * x + model[4] * y + model[8] * z + model[12];
                    float Y = model[1] * x + model[5] * y + model[9] * z + model[13];
                    float Z = model[2] * x + model[6] * y + model[10] * z + model[14];
                    float NX = model[0] * nx + model[4] * ny + model[8] * nz;
                    float NY = model[1] * nx + model[5] * ny + model[9] * nz;
                    float NZ = model[2] * nx + model[6] * ny + model[10] * nz;
                    float l = (float) Math.sqrt(NX * NX + NY * NY + NZ * NZ);
                    x = X;
                    y = Y;
                    z = Z;
                    nx = NX / l;
                    ny = NY / l;
                    nz = NZ / l;
                }
                // world pos, normal, color, uv, emit, tex
                v[k] = new float[]{x, y, z, nx, ny, nz, d[i + 6] * tr, d[i + 7] * tg,
                        d[i + 8] * tb, d[i + 9], d[i + 10], Math.max(d[i + 11], glow), d[i + 12]};
            }
            tri(v);
        }
    }

    /** Clip-space position of a world point: x, y, z, w. */
    static float[] clip(float[] p) {
        float x = p[0], y = p[1], z = p[2];
        return new float[]{vp[0] * x + vp[4] * y + vp[8] * z + vp[12],
                vp[1] * x + vp[5] * y + vp[9] * z + vp[13],
                vp[2] * x + vp[6] * y + vp[10] * z + vp[14],
                vp[3] * x + vp[7] * y + vp[11] * z + vp[15]};
    }

    static void tri(float[][] v) {
        // Clip against the near plane (w > 0.1), carrying the attributes along.
        java.util.List<float[]> poly = new java.util.ArrayList<>();
        java.util.List<float[]> cl = new java.util.ArrayList<>();
        for (float[] a : v) {
            poly.add(a);
            cl.add(clip(a));
        }
        java.util.List<float[]> outP = new java.util.ArrayList<>();
        java.util.List<float[]> outC = new java.util.ArrayList<>();
        float near = 0.1f;
        for (int i = 0; i < poly.size(); i++) {
            float[] pa = poly.get(i), ca = cl.get(i);
            float[] pb = poly.get((i + 1) % poly.size()), cb = cl.get((i + 1) % poly.size());
            boolean ina = ca[3] > near, inb = cb[3] > near;
            if (ina) {
                outP.add(pa);
                outC.add(ca);
            }
            if (ina != inb) {
                float t = (near - ca[3]) / (cb[3] - ca[3]);
                float[] p = new float[pa.length];
                for (int k = 0; k < p.length; k++) p[k] = pa[k] + (pb[k] - pa[k]) * t;
                float[] c = new float[4];
                for (int k = 0; k < 4; k++) c[k] = ca[k] + (cb[k] - ca[k]) * t;
                outP.add(p);
                outC.add(c);
            }
        }
        for (int i = 1; i + 1 < outP.size(); i++) {
            raster(outP.get(0), outC.get(0), outP.get(i), outC.get(i), outP.get(i + 1),
                    outC.get(i + 1));
        }
    }

    static void raster(float[] a, float[] ca, float[] b, float[] cb, float[] c, float[] cc) {
        float ax = (ca[0] / ca[3] * 0.5f + 0.5f) * W, ay = (1 - (ca[1] / ca[3] * 0.5f + 0.5f)) * H;
        float bx = (cb[0] / cb[3] * 0.5f + 0.5f) * W, by = (1 - (cb[1] / cb[3] * 0.5f + 0.5f)) * H;
        float cx = (cc[0] / cc[3] * 0.5f + 0.5f) * W, cy = (1 - (cc[1] / cc[3] * 0.5f + 0.5f)) * H;
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-6f) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int x1 = Math.min(W - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int y1 = Math.min(H - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        float iwa = 1 / ca[3], iwb = 1 / cb[3], iwc = 1 / cc[3];
        int nA = a.length;
        float[] at = new float[nA];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float pxf = x + 0.5f, pyf = y + 0.5f;
                float w0 = ((bx - pxf) * (cy - pyf) - (by - pyf) * (cx - pxf)) / area;
                float w1 = ((cx - pxf) * (ay - pyf) - (cy - pyf) * (ax - pxf)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float iw = w0 * iwa + w1 * iwb + w2 * iwc;
                float depth = 1 / iw;
                int idx = y * W + x;
                if (depth >= zb[idx]) continue;
                zb[idx] = depth;
                for (int k = 0; k < nA; k++) {
                    at[k] = (a[k] * w0 * iwa + b[k] * w1 * iwb + c[k] * w2 * iwc) / iw;
                }
                px[idx] = shade(at);
            }
        }
    }

    // ---- the same lighting as the phone's shader
    static int shade(float[] a) {
        float r = a[6], g = a[7], b = a[8];
        float tex = a[12];
        if (tex > 0.5f) {
            int t = tex < 1.5f ? sample(atlas, CasinoArt.ATLAS, CasinoArt.ATLAS_H, a[9], a[10], false)
                    : sample(carpet, 256, 256, a[9], a[10], true);
            r *= ((t >> 16) & 255) / 255f;
            g *= ((t >> 8) & 255) / 255f;
            b *= (t & 255) / 255f;
        }
        float nx = a[3], ny = a[4], nz = a[5];
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        float lu = (a[0] - CasinoWorld.WX0) / (CasinoWorld.WX1 - CasinoWorld.WX0);
        float lv = (a[2] - CasinoWorld.WZ0) / (CasinoWorld.WZ1 - CasinoWorld.WZ0);
        int L = sample(lm, CasinoWorld.LM_W, CasinoWorld.LM_H, lu, lv, false);
        float in = ((L >>> 24) & 255) / 255f;
        float pr = ((L >> 16) & 255) / 255f * 2, pg = ((L >> 8) & 255) / 255f * 2,
                pb = (L & 255) / 255f * 2;
        float h = ny * 0.5f + 0.5f;
        float ar = mix(mix(0.05f, 0.12f, h), mix(0.16f, 0.36f, h), in);
        float ag = mix(mix(0.055f, 0.13f, h), mix(0.12f, 0.30f, h), in);
        float ab = mix(mix(0.08f, 0.20f, h), mix(0.11f, 0.26f, h), in);
        float lx = 0.25f, ly = 0.9f, lz = 0.35f;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float dif = Math.max(0, (nx * lx + ny * ly + nz * lz) / ll);
        float k = 0.35f + 0.65f * h;
        float lr = ar + dif * mix(0.16f, 0.62f, in) + pr * k;
        float lg = ag + dif * mix(0.18f, 0.54f, in) + pg * k;
        float lb = ab + dif * mix(0.28f, 0.44f, in) + pb * k;
        float e = a[11];
        float outR = r * lr * (1 - e) + r * e * 1.1f;
        float outG = g * lg * (1 - e) + g * e * 1.1f;
        float outB = b * lb * (1 - e) + b * e * 1.1f;
        float dx = a[0] - eye[0], dy = a[1] - eye[1], dz = a[2] - eye[2];
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float fog = Math.max(0, Math.min(1, (dist - mix(14, 10, in)) / mix(45, 30, in))) * 0.8f;
        outR = outR * (1 - fog) + mix(0.02f, 0.035f, in) * fog;
        outG = outG * (1 - fog) + mix(0.025f, 0.028f, in) * fog;
        outB = outB * (1 - fog) + mix(0.045f, 0.026f, in) * fog;
        return 0xFF000000 | (cl(outR) << 16) | (cl(outG) << 8) | cl(outB);
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static int cl(float v) {
        return Math.max(0, Math.min(255, (int) (Math.pow(Math.max(0, v), 1.0) * 255)));
    }

    static int sample(int[] img, int w, int h, float u, float v, boolean wrap) {
        if (wrap) {
            u -= Math.floor(u);
            v -= Math.floor(v);
        }
        float x = u * w - 0.5f, y = v * h - 0.5f;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        int c00 = at(img, w, h, x0, y0, wrap), c10 = at(img, w, h, x0 + 1, y0, wrap);
        int c01 = at(img, w, h, x0, y0 + 1, wrap), c11 = at(img, w, h, x0 + 1, y0 + 1, wrap);
        int out = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            float v0 = ((c00 >>> sh) & 255) * (1 - fx) + ((c10 >>> sh) & 255) * fx;
            float v1 = ((c01 >>> sh) & 255) * (1 - fx) + ((c11 >>> sh) & 255) * fx;
            out |= ((int) (v0 * (1 - fy) + v1 * fy) & 255) << sh;
        }
        return out;
    }

    static int at(int[] img, int w, int h, int x, int y, boolean wrap) {
        if (wrap) {
            x = ((x % w) + w) % w;
            y = ((y % h) + h) % h;
        } else {
            x = Math.max(0, Math.min(w - 1, x));
            y = Math.max(0, Math.min(h - 1, y));
        }
        return img[y * w + x];
    }

    static void shadow(float sx, float sz, float r) {
        if (r < 0) return;
        // Darken the floor in a soft disc, drawn as a flat fan just above the carpet.
        int segs = 20;
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            float[][] p = {{sx, 0.01f, sz}, {sx + (float) Math.cos(a0) * r, 0.01f,
                    sz + (float) Math.sin(a0) * r}, {sx + (float) Math.cos(a1) * r, 0.01f,
                    sz + (float) Math.sin(a1) * r}};
            float[][] c = {clip(p[0]), clip(p[1]), clip(p[2])};
            if (c[0][3] < 0.1f || c[1][3] < 0.1f || c[2][3] < 0.1f) continue;
            float[] xs = new float[3], ys = new float[3];
            for (int k = 0; k < 3; k++) {
                xs[k] = (c[k][0] / c[k][3] * 0.5f + 0.5f) * W;
                ys[k] = (1 - (c[k][1] / c[k][3] * 0.5f + 0.5f)) * H;
            }
            float area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (ys[1] - ys[0]) * (xs[2] - xs[0]);
            if (Math.abs(area) < 1e-6f) continue;
            int x0 = Math.max(0, (int) Math.min(xs[0], Math.min(xs[1], xs[2])));
            int x1 = Math.min(W - 1, (int) Math.max(xs[0], Math.max(xs[1], xs[2])) + 1);
            int y0 = Math.max(0, (int) Math.min(ys[0], Math.min(ys[1], ys[2])));
            int y1 = Math.min(H - 1, (int) Math.max(ys[0], Math.max(ys[1], ys[2])) + 1);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float pxf = x + 0.5f, pyf = y + 0.5f;
                    float w0 = ((xs[1] - pxf) * (ys[2] - pyf) - (ys[1] - pyf) * (xs[2] - pxf)) / area;
                    float w1 = ((xs[2] - pxf) * (ys[0] - pyf) - (ys[2] - pyf) * (xs[0] - pxf)) / area;
                    float w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    // w0 is the centre's weight: 1 in the middle, 0 at the rim.
                    float k = 0.55f * (float) Math.pow(w0, 0.8);
                    int idx = y * W + x;
                    float depth = 1 / (w0 / c[0][3] + w1 / c[1][3] + w2 / c[2][3]);
                    if (zb[idx] < depth - 0.05f) continue;
                    int col = px[idx];
                    int rr = (int) (((col >> 16) & 255) * (1 - k));
                    int gg = (int) (((col >> 8) & 255) * (1 - k));
                    int bb = (int) ((col & 255) * (1 - k));
                    px[idx] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                }
            }
        }
    }
}
