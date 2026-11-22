package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The walk-around casino: the room and everything in it, the people, walking, punching and
 * security. Plain Java with no Android parts, so it can be previewed and tested off the phone.
 *
 * Directions: x runs west to east, z runs north (negative) to south (positive), y is up.
 * A yaw of 0 faces north; forward is (sin yaw, 0, -cos yaw).
 */
final class CasinoWorld {

    // ================================================================== matrices

    /** 4x4 matrices, column-major like OpenGL. */
    static final class Mat {
        private Mat() {}

        static void identity(float[] m) {
            for (int i = 0; i < 16; i++) m[i] = 0;
            m[0] = m[5] = m[10] = m[15] = 1;
        }

        static void mul(float[] out, float[] a, float[] b) {
            float[] r = new float[16];
            for (int c = 0; c < 4; c++) {
                for (int row = 0; row < 4; row++) {
                    float s = 0;
                    for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k];
                    r[c * 4 + row] = s;
                }
            }
            System.arraycopy(r, 0, out, 0, 16);
        }

        static void perspective(float[] m, float fovyDeg, float aspect, float near, float far) {
            float f = (float) (1 / Math.tan(Math.toRadians(fovyDeg) / 2));
            for (int i = 0; i < 16; i++) m[i] = 0;
            m[0] = f / aspect;
            m[5] = f;
            m[10] = (far + near) / (near - far);
            m[11] = -1;
            m[14] = 2 * far * near / (near - far);
        }

        static void lookAt(float[] m, float ex, float ey, float ez, float cx, float cy, float cz) {
            float fx = cx - ex, fy = cy - ey, fz = cz - ez;
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            fx /= fl;
            fy /= fl;
            fz /= fl;
            // side = f x up(0,1,0)
            float sx = -fz, sy = 0, sz = fx;
            float sl = (float) Math.sqrt(sx * sx + sz * sz);
            sx /= sl;
            sz /= sl;
            // u = s x f
            float ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
            m[0] = sx;
            m[4] = sy;
            m[8] = sz;
            m[1] = ux;
            m[5] = uy;
            m[9] = uz;
            m[2] = -fx;
            m[6] = -fy;
            m[10] = -fz;
            m[3] = m[7] = m[11] = 0;
            m[12] = -(sx * ex + sy * ey + sz * ez);
            m[13] = -(ux * ex + uy * ey + uz * ez);
            m[14] = fx * ex + fy * ey + fz * ez;
            m[15] = 1;
        }

        /** m = m * translate. */
        static void translate(float[] m, float x, float y, float z) {
            for (int r = 0; r < 4; r++) m[12 + r] += m[r] * x + m[4 + r] * y + m[8 + r] * z;
        }

        /** m = m * scale. */
        static void scale(float[] m, float x, float y, float z) {
            for (int r = 0; r < 4; r++) {
                m[r] *= x;
                m[4 + r] *= y;
                m[8 + r] *= z;
            }
        }

        /** m = m * rotation about x. A positive angle swings a hanging limb forward. */
        static void rotX(float[] m, float a) {
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            for (int r = 0; r < 4; r++) {
                float y = m[4 + r], z = m[8 + r];
                m[4 + r] = y * c + z * s;
                m[8 + r] = -y * s + z * c;
            }
        }

        /** m = m * rotation about y. */
        static void rotY(float[] m, float a) {
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            for (int r = 0; r < 4; r++) {
                float x = m[r], z = m[8 + r];
                m[r] = x * c - z * s;
                m[8 + r] = x * s + z * c;
            }
        }

        /** m = m * rotation about z. */
        static void rotZ(float[] m, float a) {
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            for (int r = 0; r < 4; r++) {
                float x = m[r], y = m[4 + r];
                m[r] = x * c + y * s;
                m[4 + r] = -x * s + y * c;
            }
        }

        /** Projects a world point to screen pixels. Returns false if it's behind the camera. */
        static boolean project(float[] viewProj, float x, float y, float z, int w, int h,
                               float[] out) {
            float cx = viewProj[0] * x + viewProj[4] * y + viewProj[8] * z + viewProj[12];
            float cy = viewProj[1] * x + viewProj[5] * y + viewProj[9] * z + viewProj[13];
            float cw = viewProj[3] * x + viewProj[7] * y + viewProj[11] * z + viewProj[15];
            if (cw < 0.1f) return false;
            out[0] = (cx / cw * 0.5f + 0.5f) * w;
            out[1] = (1 - (cy / cw * 0.5f + 0.5f)) * h;
            out[2] = cw;
            return true;
        }
    }

    // ================================================================== mesh

    /**
     * Triangles for the GPU. Each vertex is 13 floats: position, normal, color, texture
     * coordinates, glow (0 lit normally, 1 shines by itself) and which texture (0 none,
     * 1 the art sheet, 2 the carpet).
     */
    static final class Mesh {
        static final int STRIDE = 13;
        float[] data = new float[STRIDE * 3 * 4096];
        int floats;

        int vertices() {
            return floats / STRIDE;
        }

        private void vertex(float x, float y, float z, float nx, float ny, float nz, int color,
                            float u, float v, float emit, float tex) {
            if (floats + STRIDE > data.length) {
                float[] bigger = new float[data.length * 2];
                System.arraycopy(data, 0, bigger, 0, floats);
                data = bigger;
            }
            float[] d = data;
            int i = floats;
            d[i] = x;
            d[i + 1] = y;
            d[i + 2] = z;
            d[i + 3] = nx;
            d[i + 4] = ny;
            d[i + 5] = nz;
            d[i + 6] = ((color >> 16) & 255) / 255f;
            d[i + 7] = ((color >> 8) & 255) / 255f;
            d[i + 8] = (color & 255) / 255f;
            d[i + 9] = u;
            d[i + 10] = v;
            d[i + 11] = emit;
            d[i + 12] = tex;
            floats += STRIDE;
        }

        /**
         * A four-cornered face: a bottom-left, b bottom-right, c top-right, d top-left as seen
         * from the front.
         */
        void quad(float[] a, float[] b, float[] c, float[] d, int color, float emit, int tex,
                  float u0, float v0, float u1, float v1) {
            float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2];
            float fx = d[0] - a[0], fy = d[1] - a[1], fz = d[2] - a[2];
            float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 0) {
                nx /= l;
                ny /= l;
                nz /= l;
            }
            vertex(a[0], a[1], a[2], nx, ny, nz, color, u0, v1, emit, tex);
            vertex(b[0], b[1], b[2], nx, ny, nz, color, u1, v1, emit, tex);
            vertex(c[0], c[1], c[2], nx, ny, nz, color, u1, v0, emit, tex);
            vertex(a[0], a[1], a[2], nx, ny, nz, color, u0, v1, emit, tex);
            vertex(c[0], c[1], c[2], nx, ny, nz, color, u1, v0, emit, tex);
            vertex(d[0], d[1], d[2], nx, ny, nz, color, u0, v0, emit, tex);
        }

        void quad(float[] a, float[] b, float[] c, float[] d, int color, float emit) {
            quad(a, b, c, d, color, emit, 0, 0, 0, 1, 1);
        }

        /** A flat upward face on the floor or a table, with world-sized texture repeats. */
        void floor(float x0, float z0, float x1, float z1, float y, int color, int tex,
                   float tile) {
            float[] a = {x0, y, z1}, b = {x1, y, z1}, c = {x1, y, z0}, d = {x0, y, z0};
            quad(a, b, c, d, color, 0, tex, x0 / tile, z0 / tile, x1 / tile, z1 / tile);
        }

        /** A ceiling face (looking down). */
        void ceiling(float x0, float z0, float x1, float z1, float y, int color, float emit) {
            float[] a = {x0, y, z0}, b = {x1, y, z0}, c = {x1, y, z1}, d = {x0, y, z1};
            quad(a, b, c, d, color, emit);
        }

        /**
         * A box turned to face one way. cx, cz is the middle of its footprint, half-width
         * across its face, half-depth front to back. Five faces (no bottom).
         */
        void box(float cx, float cz, float yaw, float hw, float hd, float y0, float y1,
                 int color, float emit) {
            float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
            // The box's right side, as seen by someone standing in front of it.
            float rx = (float) -Math.cos(yaw), rz = (float) -Math.sin(yaw);
            float[][] p = new float[8][];
            int k = 0;
            for (int dy = 0; dy < 2; dy++) {
                for (int df = -1; df <= 1; df += 2) {
                    for (int dr = -1; dr <= 1; dr += 2) {
                        p[k++] = new float[]{cx + fx * hd * df + rx * hw * dr, dy == 0 ? y0 : y1,
                                cz + fz * hd * df + rz * hw * dr};
                    }
                }
            }
            // Index: dy*4 + (df==1?2:0) + (dr==1?1:0). Front is df = +1.
            float[] lbB = p[0], rbB = p[1], lfB = p[2], rfB = p[3];
            float[] lbT = p[4], rbT = p[5], lfT = p[6], rfT = p[7];
            int side = shade(color, 0.86f);
            quad(lfB, rfB, rfT, lfT, color, emit); // front
            quad(rbB, lbB, lbT, rbT, shade(color, 0.8f), emit); // back
            quad(rfB, rbB, rbT, rfT, side, emit); // right
            quad(lbB, lfB, lfT, lbT, side, emit); // left
            quad(lfT, rfT, rbT, lbT, shade(color, 1.08f), emit); // top
        }

        /** An axis-lined box from corner to corner. */
        void boxAA(float x0, float y0, float z0, float x1, float y1, float z1, int color,
                   float emit) {
            box((x0 + x1) / 2, (z0 + z1) / 2, (float) Math.PI, (x1 - x0) / 2, (z1 - z0) / 2, y0,
                    y1, color, emit);
        }

        /**
         * A flat picture standing upright, facing yaw, centered on cx, cz, from y0 to y1.
         */
        void panel(float cx, float cz, float yaw, float w, float y0, float y1, int color,
                   float emit, CasinoArt.Region r) {
            float rx = (float) -Math.cos(yaw), rz = (float) -Math.sin(yaw);
            float[] a = {cx - rx * w / 2, y0, cz - rz * w / 2};
            float[] b = {cx + rx * w / 2, y0, cz + rz * w / 2};
            float[] c = {cx + rx * w / 2, y1, cz + rz * w / 2};
            float[] d = {cx - rx * w / 2, y1, cz - rz * w / 2};
            if (r == null) quad(a, b, c, d, color, emit);
            else quad(a, b, c, d, color, emit, 1, r.u0(), r.v0(), r.u1(), r.v1());
        }

        /** An upright oval post or table: sides and a top. */
        void cylinder(float cx, float cz, float rx, float rz, float y0, float y1, int side,
                      int top, float emit, int segs) {
            for (int i = 0; i < segs; i++) {
                double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
                float x0 = cx + (float) Math.cos(a0) * rx, z0 = cz + (float) Math.sin(a0) * rz;
                float x1 = cx + (float) Math.cos(a1) * rx, z1 = cz + (float) Math.sin(a1) * rz;
                float[] a = {x1, y0, z1}, b = {x0, y0, z0}, c = {x0, y1, z0}, d = {x1, y1, z1};
                quad(a, b, c, d, side, emit);
                if (top != 0) {
                    float[] m = {cx, y1, cz};
                    vertexTri(m, new float[]{x1, y1, z1}, new float[]{x0, y1, z0}, top, emit,
                            0, null);
                }
            }
        }

        /** A flat oval top, optionally with the art sheet stretched over its box. */
        void ovalTop(float cx, float cz, float rx, float rz, float y, int color, int segs,
                     CasinoArt.Region r) {
            for (int i = 0; i < segs; i++) {
                double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
                float[] p0 = {cx + (float) Math.cos(a0) * rx, y, cz + (float) Math.sin(a0) * rz};
                float[] p1 = {cx + (float) Math.cos(a1) * rx, y, cz + (float) Math.sin(a1) * rz};
                vertexTri(new float[]{cx, y, cz}, p1, p0, color, 0, r == null ? 0 : 1,
                        r == null ? null : new float[]{cx - rx, cz - rz, cx + rx, cz + rz,
                                r.u0(), r.v0(), r.u1(), r.v1()});
            }
        }

        /** One triangle, with an upward normal for flat tops. map: world box to uv box. */
        private void vertexTri(float[] a, float[] b, float[] c, int color, float emit, int tex,
                               float[] map) {
            float[][] ps = {a, b, c};
            for (float[] q : ps) {
                float u = 0, v = 0;
                if (map != null) {
                    u = map[4] + (q[0] - map[0]) / (map[2] - map[0]) * (map[6] - map[4]);
                    v = map[5] + (q[2] - map[1]) / (map[3] - map[1]) * (map[7] - map[5]);
                }
                vertex(q[0], q[1], q[2], 0, 1, 0, color, u, v, emit, tex);
            }
        }
    }

    static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int g = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ================================================================== layout

    static final float X0 = -18, X1 = 18, Z0 = -14, Z1 = 14, CEIL = 5.4f;
    static final float SPAWN_X = 0, SPAWN_Z = 11.5f;

    /** Something you can walk up to and play. */
    static final class Spot {
        final String label, key, unlock;
        final int extra;
        final float x, z, yaw;

        Spot(String label, String key, int extra, String unlock, float x, float z, float yaw) {
            this.label = label;
            this.key = key;
            this.extra = extra;
            this.unlock = unlock;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
        }
    }

    /** A hanging or wall sign whose words the phone can float as a label. */
    final List<Spot> spots = new ArrayList<>();
    /** Things you can't walk through: x0, z0, x1, z1. */
    final List<float[]> walls = new ArrayList<>();
    final Mesh mesh = new Mesh();
    /** Where on the wall the live jackpot board sits, for redrawing. */
    static final float PI = (float) Math.PI;
    static final float NORTH = 0, EAST = PI / 2, SOUTH = PI, WEST = -PI / 2;

    // Palette: dark and warm, not candy.
    static final int WALL = 0xFF2B1A14, WAINSCOT = 0xFF1A0F0B, GOLD = 0xFFB8862E,
            CEILING = 0xFF120D0C, CABINET = 0xFF16161C, FELT = 0xFF0E5A34, RAIL = 0xFF3A2114,
            MARBLE = 0xFF8A8078, BRASS = 0xFFC9A24A;

    private void build() {
        Mesh m = mesh;
        // Floor and ceiling.
        m.floor(X0, Z0, X1, Z1, 0, 0xFFFFFFFF, 2, 2.2f);
        m.ceiling(X0, Z0, X1, Z1, CEIL, CEILING, 0);
        // Light panels in the ceiling, set in a darker coffer.
        for (float x = -14; x <= 14.1f; x += 7) {
            for (float z = -10; z <= 10.1f; z += 6.5f) {
                m.ceiling(x - 1.3f, z - 1.3f, x + 1.3f, z + 1.3f, CEIL - 0.02f, 0xFF0A0807, 0);
                m.ceiling(x - 0.9f, z - 0.9f, x + 0.9f, z + 0.9f, CEIL - 0.04f, 0xFFFFD9A0,
                        0.85f);
            }
        }
        // Walls: wood above a dark lower panel, with gold trim lines.
        wall(X0, Z0, X1, Z0, SOUTH); // north wall faces south
        wall(X1, Z1, X0, Z1, NORTH); // south wall
        wall(X0, Z1, X0, Z0, EAST); // west wall
        wall(X1, Z0, X1, Z1, WEST); // east wall
        // The way out: a lit doorway in the south wall.
        m.panel(0, Z1 - 0.02f, NORTH, 3.2f, 0, 3.0f, 0xFF3A2A18, 0.15f, null);
        m.panel(0, Z1 - 0.03f, NORTH, 2.8f, 0, 2.8f, 0xFFFFE6B0, 0.55f, null);
        m.boxAA(-1.8f, 0, Z1 - 0.25f, -1.55f, 3.2f, Z1, BRASS, 0.1f);
        m.boxAA(1.55f, 0, Z1 - 0.25f, 1.8f, 3.2f, Z1, BRASS, 0.1f);

        // Pillars with brass bands.
        float[][] pillars = {{-6, -3}, {3, -3}, {-6, 8}, {5, 8}};
        for (float[] p : pillars) {
            m.boxAA(p[0] - 0.45f, 0, p[1] - 0.45f, p[0] + 0.45f, CEIL, p[1] + 0.45f, MARBLE, 0);
            m.boxAA(p[0] - 0.5f, 0, p[1] - 0.5f, p[0] + 0.5f, 0.3f, p[1] + 0.5f, 0xFF2A2420, 0);
            m.boxAA(p[0] - 0.48f, 2.6f, p[1] - 0.48f, p[0] + 0.48f, 2.72f, p[1] + 0.48f, BRASS,
                    0.25f);
            m.boxAA(p[0] - 0.5f, CEIL - 0.4f, p[1] - 0.5f, p[0] + 0.5f, CEIL, p[1] + 0.5f,
                    0xFF2A2420, 0);
            solid(p[0] - 0.5f, p[1] - 0.5f, p[0] + 0.5f, p[1] + 0.5f);
        }

        // ---- Penny Palace: four games on the north wall under the live jackpot board.
        for (int i = 0; i < 4; i++) {
            float x = -15 + i * 1.9f;
            cabinet(x, Z0 + 0.45f, SOUTH, 5 + i, true);
            spots.add(new Spot(SlotMath.NAMES[5 + i], "penny", 5 + i, "parlor", x, Z0 + 1.75f,
                    NORTH));
        }
        float boardX = -15 + 1.5f * 1.9f;
        m.boxAA(boardX - 3.0f, 2.45f, Z0, boardX + 3.0f, 4.75f, Z0 + 0.12f, 0xFF050507, 0);
        m.panel(boardX, Z0 + 0.13f, SOUTH, 5.8f, 2.5f, 4.7f, 0xFFFFFFFF, 1,
                CasinoArt.JACKPOT_BOARD);
        boardPanel = new float[]{boardX, Z0 + 0.13f};
        m.panel(boardX, Z0 + 0.05f, SOUTH, 4.6f, 4.8f, 5.3f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.PENNY));
        // A penny island with regulars glued to their stools.
        for (int i = 0; i < 3; i++) {
            float x = -14.5f + i * 1.0f;
            cabinetSmall(x, -6.6f, SOUTH, 5 + (i % 4));
            cabinetSmall(x, -7.4f, NORTH, 5 + ((i + 2) % 4));
        }
        solid(-15.1f, -7.9f, -12.0f, -6.1f);
        m.boxAA(-15.1f, 0, -7.1f, -11.9f, 0.08f, -6.9f, 0xFF4CE0A0, 0.8f);

        // ---- Slot Parlor: the five coupon machines on the north wall.
        for (int i = 0; i < 5; i++) {
            float x = 4 + i * 1.9f;
            cabinet(x, Z0 + 0.45f, SOUTH, i, true);
            spots.add(new Spot(SlotMath.NAMES[i], "machine", i, "parlor", x, Z0 + 1.75f,
                    NORTH));
        }
        m.panel(4 + 2 * 1.9f, Z0 + 0.05f, SOUTH, 4.6f, 3.0f, 3.5f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.PARLOR));

        // ---- Classic slots island (Lucky Sevens faces south, Royal Riches north).
        cabinet(-2.6f, -8.0f, SOUTH, 9, false);
        cabinet(-1.4f, -8.0f, SOUTH, 9, false);
        cabinet(-2.6f, -8.8f, NORTH, 10, false);
        cabinet(-1.4f, -8.8f, NORTH, 10, false);
        solid(-3.1f, -9.3f, -0.9f, -7.5f);
        spots.add(new Spot("Lucky Sevens", "slot", 0, "slot", -2.0f, -6.7f, NORTH));
        spots.add(new Spot("Royal Riches", "slot2", 0, "slot2", -2.0f, -10.1f, SOUTH));
        hangingSign(-2.0f, -8.4f, 3.6f, CasinoArt.CLASSIC, 0);

        // ---- Texas Hold'em table.
        float hx = 8, hz = 1.5f;
        m.cylinder(hx, hz, 0.7f, 0.35f, 0, 0.62f, 0xFF1A120E, 0, 0, 16);
        m.cylinder(hx, hz, 2.15f, 1.2f, 0.62f, 0.8f, RAIL, 0xFF2A1810, 0, 32);
        m.ovalTop(hx, hz, 1.85f, 0.95f, 0.81f, 0xFFFFFFFF, 32, CasinoArt.HOLDEM_FELT);
        m.cylinder(hx, hz, 2.18f, 1.23f, 0.79f, 0.84f, 0xFF120B08, 0, 0, 32);
        solid(hx - 2.0f, hz - 1.05f, hx + 2.0f, hz + 1.05f);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 2 + i * Math.PI / 3;
            float sx = hx + (float) Math.cos(a) * 2.75f, sz = hz + (float) Math.sin(a) * 1.75f;
            stool(sx, sz, 0xFF5A1A1A);
            holdemSeats[i] = new float[]{sx, sz, (float) Math.atan2(hx - sx, -(hz - sz))};
        }
        spots.add(new Spot("Texas Hold'em", "holdem", 0, "holdem", holdemSeats[0][0],
                holdemSeats[0][1] + 0.6f, NORTH));
        hangingSign(hx, hz, 3.8f, CasinoArt.HOLDEM, 0);

        // ---- Blackjack: a half-moon table, dealer on the flat north side.
        float bx = -1, bz = 2.5f;
        m.boxAA(bx - 1.6f, 0, bz - 0.35f, bx + 1.6f, 0.78f, bz + 0.2f, 0xFF1A120E, 0);
        halfMoon(bx, bz - 0.4f, 1.75f, 1.25f, CasinoArt.BLACKJACK_FELT);
        solid(bx - 1.8f, bz - 0.5f, bx + 1.8f, bz + 1.0f);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * (0.2 + 0.2 * i);
            stool(bx + (float) Math.cos(a) * 2.15f, bz - 0.4f + (float) Math.sin(a) * 1.75f,
                    0xFF1A3A5A);
        }
        dealer = new float[]{bx, bz - 1.0f, SOUTH};
        spots.add(new Spot("Blackjack", "blackjack", 0, "blackjack", bx, bz + 1.9f, NORTH));
        hangingSign(bx, bz, 3.8f, CasinoArt.BLACKJACK, 0);

        // ---- Roulette: the layout table and the wheel at its west end.
        float rx = -10, rz = 3;
        m.boxAA(rx - 1.5f, 0, rz - 0.75f, rx + 1.6f, 0.8f, rz + 0.75f, RAIL, 0);
        {
            // The betting layout on top.
            CasinoArt.Region r = CasinoArt.ROULETTE_LAYOUT;
            float[] a = {rx - 1.35f, 0.812f, rz + 0.6f}, b = {rx + 1.45f, 0.812f, rz + 0.6f};
            float[] c = {rx + 1.45f, 0.812f, rz - 0.6f}, d = {rx - 1.35f, 0.812f, rz - 0.6f};
            m.quad(a, b, c, d, 0xFFFFFFFF, 0, 1, r.u0(), r.v0(), r.u1(), r.v1());
        }
        m.boxAA(rx - 2.6f, 0, rz - 0.75f, rx - 1.5f, 0.8f, rz + 0.75f, RAIL, 0);
        m.cylinder(rx - 2.05f, rz, 0.5f, 0.5f, 0.8f, 0.92f, 0xFF4A2A14, 0xFF6A3A1A, 0, 24);
        wheelTop(rx - 2.05f, rz, 0.46f, 0.925f);
        m.cylinder(rx - 2.05f, rz, 0.05f, 0.05f, 0.92f, 1.05f, BRASS, BRASS, 0.3f, 8);
        solid(rx - 2.7f, rz - 0.85f, rx + 1.7f, rz + 0.85f);
        croupier = new float[]{rx - 2.05f, rz - 1.15f, SOUTH};
        spots.add(new Spot("Roulette", "roulette", 0, "roulette", rx, rz + 1.45f, NORTH));
        hangingSign(rx, rz, 3.8f, CasinoArt.ROULETTE, 0);

        // ---- Video poker bar along the east wall.
        m.boxAA(X1 - 1.0f, 0, -3.2f, X1, 1.05f, 4.2f, 0xFF2A1810, 0);
        m.boxAA(X1 - 1.15f, 1.05f, -3.3f, X1, 1.12f, 4.3f, 0xFF120B08, 0);
        m.boxAA(X1 - 1.02f, 0.06f, -3.2f, X1 - 0.98f, 0.12f, 4.2f, 0xFF40C4FF, 0.9f);
        solid(X1 - 1.2f, -3.3f, X1, 4.3f);
        for (int i = 0; i < 4; i++) {
            float z = -2.3f + i * 1.8f;
            barTop(X1 - 0.55f, z, WEST);
            stool(X1 - 1.75f, z, 0xFF1A3A5A);
        }
        spots.add(new Spot("Video Poker", "vpoker", 0, "vpoker", X1 - 2.0f, 0.4f, EAST));
        m.panel(X1 - 0.05f, 0.45f, WEST, 4.6f, 2.6f, 3.1f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.POKER));

        // ---- Cashier cage in the south-west corner.
        float cz = 9.6f;
        m.boxAA(X0, 0, cz, -13.4f, 1.1f, cz + 0.5f, 0xFF2A1810, 0);
        m.boxAA(X0, 1.1f, cz - 0.05f, -13.35f, 1.16f, cz + 0.55f, BRASS, 0.15f);
        for (float x = X0 + 0.3f; x < -13.4f; x += 0.28f) {
            m.boxAA(x, 1.16f, cz + 0.2f, x + 0.04f, 2.6f, cz + 0.26f, BRASS, 0.1f);
        }
        m.boxAA(X0, 2.6f, cz + 0.1f, -13.35f, 3.4f, cz + 0.4f, 0xFF1A0F0B, 0);
        m.panel(-15.7f, cz + 0.09f, NORTH, 3.6f, 2.68f, 3.32f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.CASHIER));
        m.boxAA(-13.4f, 0, cz, -13.3f, 3.4f, Z1, 0xFF2A1810, 0);
        solid(X0, cz, -13.3f, Z1);
        cashier = new float[]{-15.7f, cz + 1.0f, NORTH};
        spots.add(new Spot("Cashier", "cashier", 0, null, -15.7f, cz - 0.7f, SOUTH));

        // A low brass rail around the jackpot corner, purely for looks.
        m.boxAA(-16.5f, 0.85f, -11.0f, -7.6f, 0.92f, -10.92f, BRASS, 0.2f);
    }

    float[] boardPanel;
    final float[][] holdemSeats = new float[6][];
    float[] dealer, croupier, cashier;

    private void wall(float ax, float az, float bx, float bz, float faceYaw) {
        float cx = (ax + bx) / 2, cz = (az + bz) / 2;
        float len = (float) Math.hypot(bx - ax, bz - az);
        float fx = (float) Math.sin(faceYaw) * 0.001f, fz = (float) -Math.cos(faceYaw) * 0.001f;
        mesh.panel(cx, cz, faceYaw, len, 0, CEIL, WALL, 0, null);
        mesh.panel(cx + fx * 2, cz + fz * 2, faceYaw, len, 0, 1.1f, WAINSCOT, 0, null);
        mesh.panel(cx + fx * 4, cz + fz * 4, faceYaw, len, 1.08f, 1.14f, GOLD, 0.25f, null);
        mesh.panel(cx + fx * 4, cz + fz * 4, faceYaw, len, CEIL - 0.3f, CEIL - 0.24f, GOLD,
                0.25f, null);
        mesh.panel(cx + fx * 4, cz + fz * 4, faceYaw, len, 0, 0.12f, 0xFF0C0806, 0, null);
    }

    private void solid(float x0, float z0, float x1, float z1) {
        walls.add(new float[]{x0, z0, x1, z1});
    }

    /** A full-size slot cabinet with its screen, topper and stool. */
    private void cabinet(float x, float z, float yaw, int art, boolean stool) {
        Mesh m = mesh;
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        int glow = CasinoArt.GLOW[art];
        m.box(x, z, yaw, 0.38f, 0.3f, 0, 0.95f, CABINET, 0);
        m.box(x + fx * 0.32f, z + fz * 0.32f, yaw, 0.36f, 0.06f, 0.88f, 0.98f, 0xFF22222A, 0);
        // Buttons on the deck.
        for (int b = 0; b < 4; b++) {
            float off = -0.24f + b * 0.16f;
            float rx = (float) -Math.cos(yaw) * off, rz = (float) -Math.sin(yaw) * off;
            m.box(x + fx * 0.33f + rx, z + fz * 0.33f + rz, yaw, 0.05f, 0.03f, 0.98f, 1.01f,
                    b == 3 ? 0xFF4CAF50 : glow, 0.9f);
        }
        m.box(x - fx * 0.05f, z - fz * 0.05f, yaw, 0.37f, 0.22f, 0.95f, 1.92f, CABINET, 0);
        m.panel(x + fx * 0.172f, z + fz * 0.172f, yaw, 0.62f, 1.06f, 1.78f, 0xFFFFFFFF, 0.92f,
                CasinoArt.screen(art));
        // Glow strips up both sides of the screen.
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float rx = (float) -Math.cos(yaw) * 0.355f * sgn;
            float rz = (float) -Math.sin(yaw) * 0.355f * sgn;
            m.box(x + fx * 0.15f + rx, z + fz * 0.15f + rz, yaw, 0.018f, 0.03f, 0.98f, 1.9f,
                    glow, 0.95f);
        }
        m.box(x - fx * 0.05f, z - fz * 0.05f, yaw, 0.39f, 0.24f, 1.92f, 2.4f, 0xFF0E0E12, 0);
        m.panel(x + fx * 0.192f, z + fz * 0.192f, yaw, 0.74f, 1.95f, 2.37f, 0xFFFFFFFF, 1,
                CasinoArt.topper(art));
        // Floor glow under the front edge.
        m.box(x + fx * 0.31f, z + fz * 0.31f, yaw, 0.38f, 0.01f, 0.02f, 0.06f, glow, 1);
        solid(Math.min(x - 0.4f, x + fx * 0.36f), Math.min(z - 0.4f, z + fz * 0.36f),
                Math.max(x + 0.4f, x + fx * 0.36f), Math.max(z + 0.4f, z + fz * 0.36f));
        if (stool) stool(x + fx * 0.85f, z + fz * 0.85f, 0xFF5A1A1A);
        machines.add(new float[]{x, z, yaw, art});
    }

    /** A slimmer cabinet for the island banks, with a seat for a regular. */
    private void cabinetSmall(float x, float z, float yaw, int art) {
        Mesh m = mesh;
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        int glow = CasinoArt.GLOW[art];
        m.box(x, z, yaw, 0.46f, 0.36f, 0, 0.92f, CABINET, 0);
        m.box(x - fx * 0.08f, z - fz * 0.08f, yaw, 0.45f, 0.28f, 0.92f, 1.75f, CABINET, 0);
        m.panel(x + fx * 0.205f, z + fz * 0.205f, yaw, 0.72f, 1.0f, 1.62f, 0xFFFFFFFF, 0.9f,
                CasinoArt.screen(art));
        m.box(x - fx * 0.08f, z - fz * 0.08f, yaw, 0.47f, 0.3f, 1.75f, 2.1f, 0xFF0E0E12, 0);
        m.panel(x + fx * 0.225f, z + fz * 0.225f, yaw, 0.86f, 1.78f, 2.08f, 0xFFFFFFFF, 1,
                CasinoArt.topper(art));
        m.box(x + fx * 0.36f, z + fz * 0.36f, yaw, 0.44f, 0.04f, 0.88f, 0.96f, glow, 0.85f);
        stool(x + fx * 0.95f, z + fz * 0.95f, 0xFF5A1A1A);
        islandSeats.add(new float[]{x + fx * 0.95f, z + fz * 0.95f, yaw + PI});
    }

    /** A low bar-top video poker machine set into the bar. */
    private void barTop(float x, float z, float yaw) {
        Mesh m = mesh;
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        m.box(x, z, yaw, 0.32f, 0.26f, 1.12f, 1.2f, CABINET, 0);
        m.box(x - fx * 0.12f, z - fz * 0.12f, yaw, 0.3f, 0.08f, 1.2f, 1.62f, CABINET, 0);
        m.panel(x - fx * 0.035f, z - fz * 0.035f, yaw, 0.52f, 1.23f, 1.6f, 0xFFFFFFFF, 0.92f,
                CasinoArt.screen(CasinoArt.VIDEO_POKER));
    }

    private void stool(float x, float z, int seat) {
        mesh.cylinder(x, z, 0.04f, 0.04f, 0, 0.62f, 0xFF2A2420, 0, 0, 6);
        mesh.cylinder(x, z, 0.2f, 0.2f, 0.0f, 0.03f, 0xFF2A2420, 0xFF2A2420, 0, 10);
        mesh.cylinder(x, z, 0.22f, 0.22f, 0.58f, 0.68f, shade(seat, 0.8f), seat, 0, 12);
    }

    private void hangingSign(float x, float z, float y, int sign, float yaw) {
        Mesh m = mesh;
        m.boxAA(x - 0.015f, y + 0.5f, z - 0.015f, x + 0.015f, CEIL, z + 0.015f, 0xFF2A2420, 0);
        m.box(x, z, NORTH + yaw, 1.45f, 0.06f, y - 0.02f, y + 0.52f, 0xFF050507, 0);
        CasinoArt.Region r = CasinoArt.sign(sign);
        m.panel(x, z + 0.065f, SOUTH, 2.8f, y, y + 0.5f, 0xFFFFFFFF, 1, r);
        m.panel(x, z - 0.065f, NORTH, 2.8f, y, y + 0.5f, 0xFFFFFFFF, 1, r);
    }

    private void halfMoon(float cx, float cz, float rx, float rz, CasinoArt.Region felt) {
        Mesh m = mesh;
        int segs = 20;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float x0 = cx + (float) Math.cos(a0) * rx, z0 = cz + (float) Math.sin(a0) * rz;
            float x1 = cx + (float) Math.cos(a1) * rx, z1 = cz + (float) Math.sin(a1) * rz;
            // Padded rail edge.
            m.quad(new float[]{x0, 0.62f, z0}, new float[]{x1, 0.62f, z1},
                    new float[]{x1, 0.82f, z1}, new float[]{x0, 0.82f, z0}, RAIL, 0);
            float ix0 = cx + (float) Math.cos(a0) * (rx - 0.22f);
            float iz0 = cz + (float) Math.sin(a0) * (rz - 0.22f);
            float ix1 = cx + (float) Math.cos(a1) * (rx - 0.22f);
            float iz1 = cz + (float) Math.sin(a1) * (rz - 0.22f);
            m.quad(new float[]{ix0, 0.82f, iz0}, new float[]{ix1, 0.82f, iz1},
                    new float[]{x1, 0.82f, z1}, new float[]{x0, 0.82f, z0}, 0xFF2A1810, 0);
            float[] map = {cx - rx, cz - 0.3f, cx + rx, cz + rz, felt.u0(), felt.v0(), felt.u1(),
                    felt.v1()};
            m.vertexTri(new float[]{cx, 0.8f, cz}, new float[]{ix1, 0.8f, iz1},
                    new float[]{ix0, 0.8f, iz0}, 0xFFFFFFFF, 0, 1, map);
        }
        m.quad(new float[]{cx + rx, 0.62f, cz}, new float[]{cx - rx, 0.62f, cz},
                new float[]{cx - rx, 0.82f, cz}, new float[]{cx + rx, 0.82f, cz}, RAIL, 0);
    }

    /** The top of the roulette wheel: alternating red and black pockets. */
    private void wheelTop(float cx, float cz, float r, float y) {
        int segs = 37;
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            int c = i == 0 ? 0xFF1B8A4C : (i % 2 == 0 ? 0xFF111111 : 0xFFB71C1C);
            float[] p0 = {cx + (float) Math.cos(a0) * r, y, cz + (float) Math.sin(a0) * r};
            float[] p1 = {cx + (float) Math.cos(a1) * r, y, cz + (float) Math.sin(a1) * r};
            mesh.vertexTri(new float[]{cx, y, cz}, p1, p0, c, 0.05f, 0, null);
        }
        mesh.cylinder(cx, cz, r * 0.45f, r * 0.45f, y, y + 0.03f, 0xFF6A3A1A, 0xFF8A5A2A, 0,
                16);
    }

    final List<float[]> machines = new ArrayList<>();
    final List<float[]> islandSeats = new ArrayList<>();

    // ================================================================== people

    static final int WANDER = 0, SEATED = 1, STAFF = 2, GUARD = 3, PLAYER = 4;

    static final class Person {
        int kind;
        String name;
        float x, z, yaw, scale = 1;
        int skin, shirt, pants, hair;
        boolean vest, hat;
        // Where a seated or working person belongs.
        float homeX, homeZ, homeYaw;
        boolean sitting;
        // Walking.
        float tx, tz, speed = 1.1f, phase, walk, idle, stuck, lastDist;
        // Hit and falling: down counts down from DOWN_TIME; fall is 0 standing, 1 flat.
        float down, fall, kx, kz, angry, punch, shake, pushX, pushZ;
        boolean tough, swung;
        /** Seconds of bloody nose left. */
        float bleed;
        String bubble;
        float bubbleT;
        boolean active = true;
    }

    static final float DOWN_TIME = 3.4f;
    static final float PUNCH_TIME = 0.42f;
    static final float SPEED = 3.3f;

    final List<Person> people = new ArrayList<>();
    /** Flying drops of blood: x, y, z, vx, vy, vz, size. */
    final List<float[]> drops = new ArrayList<>();
    /** Blood on the carpet: x, z, size, turn, age. */
    final List<float[]> splats = new ArrayList<>();
    static final float SPLAT_LIFE = 45;
    final Person player = new Person();
    Person guard;
    private final Random rnd;
    float time;
    float camYaw = 0, camPitch = 0.32f, camDist = 4.6f;
    /** Bits for the phone to play sounds: 1 a punch landed, 2 a swing missed. */
    int sounds;
    /** A short message to show, or null. */
    String toast;
    /** Above 0 while security is walking you out (the screen fades). */
    float escort;
    private final List<Float> recentHits = new ArrayList<>();
    private float tableTalk = 6;

    private static final int[] SKIN = {0xFFF1C9A5, 0xFFE0AC84, 0xFFC68A5E, 0xFF9C6644,
            0xFF6E4630, 0xFFF5D7BE};
    private static final int[] SHIRT = {0xFF3B4A5C, 0xFF5C3B3B, 0xFF2F4F3F, 0xFF6B5B45,
            0xFF4A3B5C, 0xFF7A6A58, 0xFF2E2E36, 0xFF5A4632, 0xFF6F7C85, 0xFF8A3A2E};
    private static final int[] PANTS = {0xFF1E2228, 0xFF2B2F3A, 0xFF3A3328, 0xFF23262B,
            0xFF4A4A52};
    private static final int[] HAIR = {0xFF1A120C, 0xFF3A2414, 0xFF6A4A2A, 0xFFB0A090,
            0xFF2A2A2A, 0xFFD8C8A8};

    static final String[] OUCH = {"What the hell, man?", "Are you out of your mind?",
            "My drink!", "Really? Over a slot machine?", "I felt that in my soul.",
            "That's assault, buddy.", "You hit like my aunt.", "Security! SECURITY!",
            "I was on a hot streak!", "Do that again. I dare you.", "My tooth is loose.",
            "Who raised you?", "I'm telling the pit boss.", "Ow. Ow. Ow."};
    static final String[] SWING_BACK = {"How's that feel?", "Not so funny now.",
            "Sit down.", "Told you."};
    static final String[] TABLE_TALK = {"Fold, you coward.", "I can smell the bluff.",
            "Deal faster, my blood pressure's dropping.", "Nice hat. Did it come with the "
            + "free buffet?", "All in. Cry about it.", "That's my chair, sweetheart.",
            "I've got a system.", "Ship it!"};
    static final String[] STAFF_OUCH = {"Sir, I just work here.", "I'm calling security.",
            "That's going in my report.", "I don't get paid enough for this."};
    static final String[] GUARD_LINES = {"Alright, champ. Let's go.",
            "Hands where I can see them.", "Nice try.", "Walk with me."};

    CasinoWorld(long seed) {
        rnd = new Random(seed);
        build();
        setupPeople();
    }

    private Person newPerson(int kind) {
        Person p = new Person();
        p.kind = kind;
        p.skin = SKIN[rnd.nextInt(SKIN.length)];
        p.shirt = SHIRT[rnd.nextInt(SHIRT.length)];
        p.pants = PANTS[rnd.nextInt(PANTS.length)];
        p.hair = HAIR[rnd.nextInt(HAIR.length)];
        p.scale = 0.93f + rnd.nextFloat() * 0.14f;
        p.tough = rnd.nextFloat() < 0.3f;
        p.phase = rnd.nextFloat() * 6;
        people.add(p);
        return p;
    }

    private void setupPeople() {
        player.kind = PLAYER;
        player.x = SPAWN_X;
        player.z = SPAWN_Z;
        player.yaw = NORTH;
        player.skin = 0xFFE0AC84;
        player.shirt = 0xFF1F6F78;
        player.pants = 0xFF22252B;
        player.hair = 0xFF1A120C;
        camYaw = NORTH;

        for (int i = 0; i < 8; i++) {
            Person p = newPerson(WANDER);
            float[] at = openSpot();
            p.x = at[0];
            p.z = at[1];
            p.speed = 0.9f + rnd.nextFloat() * 0.5f;
            pickTarget(p);
        }
        // Regulars at the island machines.
        int[] taken = {0, 2, 3, 5};
        for (int t : taken) {
            if (t >= islandSeats.size()) continue;
            float[] s = islandSeats.get(t);
            seat(newPerson(SEATED), s[0], s[1], s[2]);
        }
        // The hold'em regulars (seat 0 is yours).
        String[] names = {"Rusty", "Big Tony", "Grandma Mae", "Chad", "Jolene"};
        int[] shirts = {0xFF7A4A2A, 0xFF2E2E36, 0xFF8A6A8A, 0xFFD8D8DC, 0xFF8A3A4A};
        for (int i = 1; i < 6; i++) {
            float[] s = holdemSeats[i];
            Person p = newPerson(SEATED);
            p.name = names[i - 1];
            p.shirt = shirts[i - 1];
            if (i == 2) {
                p.scale = 1.12f;
                p.tough = true;
            }
            if (i == 3) {
                p.scale = 0.88f;
                p.hair = 0xFFE0E0E0;
            }
            if (i == 4) p.hat = true;
            seat(p, s[0], s[1], s[2]);
        }
        // Staff.
        staff(dealer, "Dealer");
        staff(croupier, "Croupier");
        staff(cashier, "Cashier");
        // Security waits out of sight until needed.
        guard = newPerson(GUARD);
        guard.shirt = 0xFF141418;
        guard.pants = 0xFF141418;
        guard.scale = 1.12f;
        guard.speed = 3.5f;
        guard.active = false;
        guard.name = "Security";
    }

    private void seat(Person p, float x, float z, float yaw) {
        p.homeX = p.x = x;
        p.homeZ = p.z = z;
        p.homeYaw = p.yaw = yaw;
        p.sitting = true;
    }

    private void staff(float[] at, String name) {
        Person p = newPerson(STAFF);
        p.homeX = p.x = at[0];
        p.homeZ = p.z = at[1];
        p.homeYaw = p.yaw = at[2];
        p.shirt = 0xFFE8E4DC;
        p.vest = true;
        p.pants = 0xFF141418;
        p.name = name;
    }

    private float[] openSpot() {
        for (int tries = 0; tries < 200; tries++) {
            float x = -16 + rnd.nextFloat() * 32;
            float z = -11 + rnd.nextFloat() * 23;
            if (free(x, z, 0.9f)) return new float[]{x, z};
        }
        return new float[]{0, 6};
    }

    private boolean free(float x, float z, float margin) {
        if (x < X0 + 1 || x > X1 - 1 || z < Z0 + 1.5f || z > Z1 - 1) return false;
        for (float[] w : walls) {
            if (x > w[0] - margin && x < w[2] + margin && z > w[1] - margin && z < w[3] + margin) {
                return false;
            }
        }
        return true;
    }

    private void pickTarget(Person p) {
        float[] t = openSpot();
        p.tx = t[0];
        p.tz = t[1];
        p.stuck = 0;
        p.lastDist = 1e9f;
    }

    // ================================================================== moving

    /**
     * Moves everything forward by dt seconds. sx, sy: the joystick, -1..1, with sy negative
     * meaning forward.
     */
    void update(float dt, float sx, float sy) {
        dt = Math.min(dt, 0.05f);
        time += dt;
        sounds = 0;

        if (escort > 0) {
            float before = escort;
            escort -= dt;
            if (before > 0.6f && escort <= 0.6f) {
                // Behind the fade: back to the door.
                player.x = SPAWN_X;
                player.z = SPAWN_Z;
                player.yaw = NORTH;
                camYaw = NORTH;
                guard.active = false;
                recentHits.clear();
                toast = "Security walked you out. Keep your hands to yourself.";
            }
            sx = sy = 0;
        }

        // ---- the player
        Person me = player;
        float mag = (float) Math.min(1, Math.hypot(sx, sy));
        if (me.down > 0) mag = 0;
        if (mag > 0.08f) {
            float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
            float rx = (float) Math.cos(camYaw), rz = (float) Math.sin(camYaw);
            float dx = rx * sx - fx * sy, dz = rz * sx - fz * sy;
            float l = (float) Math.hypot(dx, dz);
            dx /= l;
            dz /= l;
            float want = (float) Math.atan2(dx, -dz);
            me.yaw = turn(me.yaw, want, dt * 12);
            me.x += dx * SPEED * mag * dt;
            me.z += dz * SPEED * mag * dt;
            me.walk = mag;
            me.phase += dt * 9.5f * mag;
        } else {
            me.walk = Math.max(0, me.walk - dt * 6);
        }
        // Being shoved by someone swinging back.
        me.x += me.pushX * dt;
        me.z += me.pushZ * dt;
        me.pushX *= (float) Math.pow(0.02, dt);
        me.pushZ *= (float) Math.pow(0.02, dt);
        collide(me, 0.33f);
        if (me.shake > 0) me.shake -= dt;

        if (me.punch > 0) {
            float before = me.punch;
            me.punch -= dt;
            float hitAt = PUNCH_TIME - 0.14f;
            if (before > hitAt && me.punch <= hitAt) landPunch();
        }

        // ---- blood
        for (int i = drops.size() - 1; i >= 0; i--) {
            float[] d = drops.get(i);
            d[4] -= 9.8f * dt;
            d[0] += d[3] * dt;
            d[1] += d[4] * dt;
            d[2] += d[5] * dt;
            if (d[1] <= 0.01f) {
                drops.remove(i);
                if (d[0] > X0 && d[0] < X1 && d[2] > Z0 && d[2] < Z1) {
                    splats.add(new float[]{d[0], d[2], d[6] * (3.5f + rnd.nextFloat() * 3.5f),
                            rnd.nextFloat() * PI, 0});
                }
            }
        }
        for (int i = splats.size() - 1; i >= 0; i--) {
            float[] sp = splats.get(i);
            sp[4] += dt;
            if (sp[4] > SPLAT_LIFE) splats.remove(i);
        }
        while (splats.size() > 140) splats.remove(0);
        if (me.bleed > 0) me.bleed -= dt;

        // ---- everyone else
        for (Person p : people) {
            if (!p.active) continue;
            if (p.bubbleT > 0) p.bubbleT -= dt;
            if (p.angry > 0) p.angry -= dt;
            if (p.bleed > 0) {
                p.bleed -= dt;
                // The odd drip while they stand there.
                if (p.down <= 0 && rnd.nextFloat() < dt * 1.5f) {
                    float fx = (float) Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
                    drops.add(new float[]{p.x + fx * 0.18f, 1.62f * p.scale, p.z + fz * 0.18f,
                            0, 0, 0, 0.03f});
                }
            }
            if (p.down > 0) {
                p.down -= dt;
                float t = DOWN_TIME - p.down;
                p.fall = t < 0.3f ? t / 0.3f : (p.down < 0.6f ? Math.max(0, p.down / 0.6f) : 1);
                p.fall = ease(p.fall);
                p.x += p.kx * dt;
                p.z += p.kz * dt;
                p.kx *= (float) Math.pow(0.01, dt);
                p.kz *= (float) Math.pow(0.01, dt);
                collide(p, 0.3f);
                p.walk = 0;
                if (p.down <= 0) {
                    p.fall = 0;
                    say(p, p.kind == STAFF ? STAFF_OUCH : OUCH);
                    p.angry = 5;
                    if (p.kind == WANDER) {
                        p.speed = 1.9f;
                        pickTarget(p);
                    }
                }
                continue;
            }
            if (p.punch > 0) {
                float before = p.punch;
                p.punch -= dt;
                float hitAt = PUNCH_TIME - 0.14f;
                if (before > hitAt && p.punch <= hitAt) npcLands(p);
                continue;
            }
            switch (p.kind) {
                case WANDER:
                    wander(p, dt);
                    break;
                case GUARD:
                    chase(p, dt);
                    break;
                default:
                    goHome(p, dt);
                    break;
            }
            // A tough one who just got up may come back for you.
            if (p.tough && p.angry > 0 && !p.swung && me.down <= 0 && escort <= 0) {
                float d = dist(p, me);
                if (d < 1.1f) {
                    p.yaw = (float) Math.atan2(me.x - p.x, -(me.z - p.z));
                    p.punch = PUNCH_TIME;
                    p.swung = true;
                } else if (d < 4 && p.kind == WANDER) {
                    p.tx = me.x;
                    p.tz = me.z;
                }
            }
        }

        // The hold'em table never shuts up.
        tableTalk -= dt;
        if (tableTalk <= 0) {
            tableTalk = 7 + rnd.nextFloat() * 8;
            List<Person> seated = new ArrayList<>();
            for (Person p : people) {
                if (p.name != null && p.kind == SEATED && p.sitting && p.down <= 0) seated.add(p);
            }
            if (!seated.isEmpty() && dist2(8, 1.5f, me.x, me.z) < 9 * 9) {
                say(seated.get(rnd.nextInt(seated.size())), TABLE_TALK);
            }
        }

        // Clean up old hits for the security count.
        while (!recentHits.isEmpty() && time - recentHits.get(0) > 60) recentHits.remove(0);
        if (recentHits.size() >= 3 && !guard.active && escort <= 0) {
            guard.active = true;
            guard.x = SPAWN_X + 0.8f;
            guard.z = Z1 - 0.6f;
            guard.yaw = NORTH;
            guard.down = 0;
            guard.fall = 0;
            say(guard, new String[]{GUARD_LINES[0]});
            toast = "Security's coming for you.";
        }
    }

    private void wander(Person p, float dt) {
        if (p.idle > 0) {
            p.idle -= dt;
            p.walk = Math.max(0, p.walk - dt * 4);
            if (p.idle <= 0) pickTarget(p);
            return;
        }
        float d = (float) Math.hypot(p.tx - p.x, p.tz - p.z);
        if (d < 0.4f) {
            p.idle = 1.5f + rnd.nextFloat() * 5;
            if (p.angry <= 0) p.speed = 0.9f + rnd.nextFloat() * 0.5f;
            return;
        }
        // Give up on targets we can't reach.
        p.stuck += dt;
        if (p.stuck > 1.2f) {
            if (d > p.lastDist - 0.3f) pickTarget(p);
            p.lastDist = d;
            p.stuck = 0;
        }
        step(p, p.tx, p.tz, dt);
    }

    private void chase(Person g, float dt) {
        Person me = player;
        float d = dist(g, me);
        if (escort > 0) {
            g.walk = 0;
            return;
        }
        if (d < 0.95f) {
            escort = 1.4f;
            say(g, new String[]{GUARD_LINES[3]});
            return;
        }
        step(g, me.x, me.z, dt);
    }

    private void goHome(Person p, float dt) {
        float d = (float) Math.hypot(p.homeX - p.x, p.homeZ - p.z);
        if (d < 0.12f) {
            p.x = p.homeX;
            p.z = p.homeZ;
            p.yaw = turn(p.yaw, p.homeYaw, dt * 8);
            p.sitting = p.kind == SEATED;
            p.walk = Math.max(0, p.walk - dt * 5);
            return;
        }
        p.sitting = false;
        // Walk straight back; tables are in the way, so slip through them on the last bit.
        float sp = p.speed;
        p.speed = 1.2f;
        step(p, p.homeX, p.homeZ, dt, d > 1.2f);
        p.speed = sp;
    }

    private void step(Person p, float tx, float tz, float dt) {
        step(p, tx, tz, dt, true);
    }

    private void step(Person p, float tx, float tz, float dt, boolean solid) {
        float dx = tx - p.x, dz = tz - p.z;
        float d = (float) Math.hypot(dx, dz);
        if (d < 1e-4f) return;
        float want = (float) Math.atan2(dx, -dz);
        p.yaw = turn(p.yaw, want, dt * 6);
        float fx = (float) Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        float go = Math.min(d, p.speed * dt);
        p.x += fx * go;
        p.z += fz * go;
        p.walk = Math.min(1, p.walk + dt * 4);
        p.phase += dt * 6.2f * p.speed / 1.1f * 0.9f;
        if (solid) collide(p, 0.3f);
    }

    /** Pushes someone out of walls and furniture. */
    private void collide(Person p, float r) {
        for (int pass = 0; pass < 2; pass++) {
            for (float[] w : walls) {
                float cx = Math.max(w[0], Math.min(p.x, w[2]));
                float cz = Math.max(w[1], Math.min(p.z, w[3]));
                float dx = p.x - cx, dz = p.z - cz;
                float d2 = dx * dx + dz * dz;
                if (d2 >= r * r) continue;
                if (d2 < 1e-8f) {
                    // Inside: push out the nearest side.
                    float l = p.x - w[0], rr = w[2] - p.x, t = p.z - w[1], b = w[3] - p.z;
                    float min = Math.min(Math.min(l, rr), Math.min(t, b));
                    if (min == l) p.x = w[0] - r;
                    else if (min == rr) p.x = w[2] + r;
                    else if (min == t) p.z = w[1] - r;
                    else p.z = w[3] + r;
                } else {
                    float d = (float) Math.sqrt(d2);
                    p.x = cx + dx / d * r;
                    p.z = cz + dz / d * r;
                }
            }
        }
        p.x = Math.max(X0 + r, Math.min(X1 - r, p.x));
        p.z = Math.max(Z0 + r, Math.min(Z1 - r, p.z));
    }

    // ================================================================== punching

    /** Starts a punch, if not already swinging. */
    void punch() {
        if (player.punch > 0 || player.down > 0 || escort > 0) return;
        // Turn toward whoever's closest in front, so you don't have to aim perfectly.
        Person t = target(1.6f, 1.1f);
        if (t != null) player.yaw = (float) Math.atan2(t.x - player.x, -(t.z - player.z));
        player.punch = PUNCH_TIME;
    }

    /** The nearest person within reach and roughly in front. */
    private Person target(float reach, float cone) {
        Person best = null;
        float bestD = reach;
        float fx = (float) Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        for (Person p : people) {
            if (!p.active || p.down > 0) continue;
            float dx = p.x - player.x, dz = p.z - player.z;
            float d = (float) Math.hypot(dx, dz);
            if (d > bestD || d < 1e-3f) continue;
            float cos = (dx * fx + dz * fz) / d;
            if (cos < Math.cos(cone)) continue;
            best = p;
            bestD = d;
        }
        return best;
    }

    private void landPunch() {
        Person p = target(1.25f, 0.9f);
        if (p == null) {
            sounds |= 2;
            return;
        }
        sounds |= 1;
        if (p.kind == GUARD) {
            say(p, new String[]{GUARD_LINES[2]});
            player.shake = 0.25f;
            return;
        }
        float fx = (float) Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        p.down = DOWN_TIME;
        p.kx = fx * 4.2f;
        p.kz = fz * 4.2f;
        // Fall away from the punch.
        p.yaw = (float) Math.atan2(-fx, fz);
        p.sitting = false;
        p.idle = 0;
        p.swung = false;
        p.bubble = null;
        p.bleed = 12;
        spray(p.x + fx * 0.1f, 1.62f * p.scale, p.z + fz * 0.1f, fx, fz, 16);
        recentHits.add(time);
    }

    private void npcLands(Person p) {
        Person me = player;
        if (dist(p, me) > 1.4f) return;
        sounds |= 1;
        float dx = me.x - p.x, dz = me.z - p.z;
        float d = Math.max(0.01f, (float) Math.hypot(dx, dz));
        me.pushX = dx / d * 6;
        me.pushZ = dz / d * 6;
        me.shake = 0.4f;
        me.bleed = 8;
        spray(me.x, 1.62f, me.z, dx / d, dz / d, 10);
        say(p, SWING_BACK);
    }

    /** A burst of blood from a hit, flying mostly the way the punch went. */
    private void spray(float x, float y, float z, float dx, float dz, int n) {
        for (int i = 0; i < n; i++) {
            float sp = 1.5f + rnd.nextFloat() * 2.5f;
            drops.add(new float[]{x, y, z,
                    dx * sp + (rnd.nextFloat() - 0.5f) * 1.6f,
                    0.5f + rnd.nextFloat() * 2.2f,
                    dz * sp + (rnd.nextFloat() - 0.5f) * 1.6f,
                    0.025f + rnd.nextFloat() * 0.035f});
        }
    }

    private void say(Person p, String[] lines) {
        p.bubble = lines[rnd.nextInt(lines.length)];
        p.bubbleT = 3.2f;
    }

    // ================================================================== nearby game

    /** The game spot you're standing at, or null. */
    Spot nearbySpot() {
        Spot best = null;
        float bestD = 1.5f * 1.5f;
        for (Spot s : spots) {
            float d = dist2(s.x, s.z, player.x, player.z);
            if (d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return escort > 0 || guard.active ? null : best;
    }

    // ================================================================== camera

    /** Fills view and projection for the current camera. */
    void camera(float aspect, float[] view, float[] proj, float[] eyeOut) {
        Person me = player;
        float tx = me.x, ty = 1.45f, tz = me.z;
        float cp = (float) Math.cos(camPitch), sp = (float) Math.sin(camPitch);
        float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
        float dist = camDist;
        // Pull in so the camera stays inside the room.
        for (int i = 0; i < 12; i++) {
            float ex = tx - fx * cp * dist, ez = tz - fz * cp * dist, ey = ty + sp * dist;
            if (ex > X0 + 0.25f && ex < X1 - 0.25f && ez > Z0 + 0.25f && ez < Z1 - 0.25f
                    && ey < CEIL - 0.2f) {
                break;
            }
            dist *= 0.85f;
        }
        float ex = tx - fx * cp * dist, ey = ty + sp * dist, ez = tz - fz * cp * dist;
        if (me.shake > 0) {
            float k = me.shake * 0.12f;
            ex += (float) Math.sin(time * 71) * k;
            ey += (float) Math.cos(time * 53) * k;
        }
        Mat.lookAt(view, ex, ey, ez, tx, ty, tz);
        Mat.perspective(proj, 62, aspect, 0.1f, 60);
        if (eyeOut != null) {
            eyeOut[0] = ex;
            eyeOut[1] = ey;
            eyeOut[2] = ez;
        }
    }

    void orbit(float dYaw, float dPitch) {
        camYaw += dYaw;
        camPitch = Math.max(0.05f, Math.min(1.1f, camPitch + dPitch));
    }

    // ================================================================== drawing people

    /** Receives one box of a person: a model matrix for a unit cube, and its color. */
    interface PartSink {
        void part(float[] model, int color);

        void shadow(float x, float z, float radius);
    }

    private final float[] base = new float[16], joint = new float[16], tmp = new float[16],
            shoulder = new float[16];

    void drawPeople(PartSink sink) {
        drawPerson(player, sink);
        for (Person p : people) {
            if (p.active) drawPerson(p, sink);
        }
        for (float[] d : drops) {
            Mat.identity(tmp);
            Mat.translate(tmp, d[0], d[1], d[2]);
            Mat.scale(tmp, d[6], d[6] * 1.4f, d[6]);
            sink.part(tmp, 0xFFD01820);
        }
        for (float[] sp : splats) {
            // Spreads a little as it lands, then shrinks away near the end.
            float grow = Math.min(1, 0.5f + sp[4] * 4);
            float fade = sp[4] > SPLAT_LIFE - 4 ? (SPLAT_LIFE - sp[4]) / 4 : 1;
            float r = sp[2] * grow * fade;
            Mat.identity(tmp);
            Mat.translate(tmp, sp[0], 0.006f, sp[1]);
            Mat.rotY(tmp, sp[3]);
            Mat.scale(tmp, r, 0.004f, r * 0.7f);
            sink.part(tmp, 0xFFC4161E);
        }
    }

    private void drawPerson(Person p, PartSink s) {
        float sc = p.scale;
        boolean sit = p.sitting && p.down <= 0;
        s.shadow(p.x, p.z, 0.42f * sc * (1 + p.fall * 0.6f));
        Mat.identity(base);
        float bob = sit ? 0 : (float) Math.abs(Math.sin(p.phase)) * 0.04f * p.walk;
        Mat.translate(base, p.x, (sit ? -0.22f : 0) + bob, p.z);
        Mat.rotY(base, -p.yaw);
        Mat.scale(base, sc, sc, sc);
        if (p.fall > 0) {
            // Tip over backward about the heels, and end up lying on the floor.
            Mat.translate(base, 0, 0.14f * p.fall, 0.12f);
            Mat.rotX(base, p.fall * 1.5f);
            Mat.translate(base, 0, 0, -0.12f);
        }

        float swing = (float) Math.sin(p.phase) * 0.5f * p.walk;
        float hip = 0.86f;
        // Legs: thigh then shin.
        for (int side = -1; side <= 1; side += 2) {
            float a = sit ? 1.5f : swing * side;
            float knee = sit ? -1.45f : Math.max(0, -(float) Math.sin(p.phase + side * 0.0f)
                    * side) * 0.7f * p.walk;
            if (p.fall > 0) {
                a = 0.15f * side;
                knee = 0.1f;
            }
            copy(joint, base);
            Mat.translate(joint, 0.105f * side, hip, 0);
            Mat.rotX(joint, a);
            limb(s, joint, 0, -0.22f, 0, 0.17f, 0.44f, 0.2f, p.pants);
            Mat.translate(joint, 0, -0.43f, 0);
            Mat.rotX(joint, -knee);
            limb(s, joint, 0, -0.2f, 0, 0.155f, 0.42f, 0.18f, p.pants);
            // Shoe.
            limb(s, joint, 0, -0.42f, -0.05f, 0.16f, 0.08f, 0.28f, 0xFF141210);
        }
        // Torso.
        copy(joint, base);
        float twist = 0;
        if (p.punch > 0) {
            float t = 1 - p.punch / PUNCH_TIME;
            twist = (float) Math.sin(Math.min(1, t * 2.2f) * Math.PI) * 0.35f;
        }
        Mat.translate(joint, 0, hip, 0);
        Mat.rotY(joint, twist);
        limb(s, joint, 0, 0.31f, 0, 0.48f, 0.62f, 0.27f, p.shirt);
        if (p.vest) {
            limb(s, joint, 0, 0.29f, -0.005f, 0.5f, 0.5f, 0.28f, 0xFF141418);
            limb(s, joint, 0, 0.5f, -0.005f, 0.16f, 0.22f, 0.285f, p.shirt);
        }
        if (p.kind == GUARD) {
            // A white shirt and tie under the dark jacket.
            limb(s, joint, 0, 0.43f, -0.13f, 0.12f, 0.36f, 0.02f, 0xFFE8E4DC);
            limb(s, joint, 0, 0.42f, -0.141f, 0.04f, 0.32f, 0.01f, 0xFF7A1A1A);
        }
        // Arms.
        float[] shoulder = this.shoulder;
        for (int side = -1; side <= 1; side += 2) {
            copy(shoulder, joint);
            Mat.translate(shoulder, 0.315f * side, 0.59f, 0);
            float a = -swing * side * 0.85f;
            float elbow = 0.25f * p.walk;
            if (sit) {
                a = 0.75f;
                elbow = 0.6f;
            }
            if (p.fall > 0) {
                a = 2.6f * p.fall;
                elbow = 0.2f;
            }
            if (p.punch > 0) {
                float t = 1 - p.punch / PUNCH_TIME;
                if (side == 1) {
                    // Wind up, snap out, pull back.
                    float out = t < 0.25f ? -t / 0.25f * 0.3f
                            : (t < 0.45f ? -0.3f + (t - 0.25f) / 0.2f * 1.9f
                            : 1.6f * (1 - (t - 0.45f) / 0.55f));
                    a = out;
                    elbow = Math.max(0, 1.6f - out * 1.6f) * 0.6f;
                } else {
                    a = 0.9f;
                    elbow = 1.7f;
                }
            }
            Mat.rotX(shoulder, a);
            Mat.rotZ(shoulder, 0.06f * side);
            limb(s, shoulder, 0, -0.16f, 0, 0.13f, 0.34f, 0.14f, p.shirt);
            Mat.translate(shoulder, 0, -0.32f, 0);
            Mat.rotX(shoulder, elbow);
            limb(s, shoulder, 0, -0.14f, 0, 0.12f, 0.3f, 0.13f,
                    p.kind == GUARD ? p.shirt : p.skin);
            limb(s, shoulder, 0, -0.32f, 0, 0.12f, 0.1f, 0.12f, p.skin);
        }
        // Neck, head and hair.
        limb(s, joint, 0, 0.66f, 0, 0.12f, 0.08f, 0.12f, p.skin);
        limb(s, joint, 0, 0.84f, 0, 0.28f, 0.3f, 0.28f, p.skin);
        limb(s, joint, 0, 1.0f, 0.02f, 0.3f, 0.06f, 0.3f, p.hair);
        limb(s, joint, 0, 0.9f, 0.13f, 0.3f, 0.16f, 0.04f, p.hair);
        if (p.hat) {
            limb(s, joint, 0, 1.03f, 0.0f, 0.42f, 0.03f, 0.42f, 0xFF3A2A1A);
            limb(s, joint, 0, 1.1f, 0.0f, 0.28f, 0.13f, 0.28f, 0xFF3A2A1A);
        }
        // A simple brow line so heads read as facing a way, nothing more.
        limb(s, joint, 0, 0.88f, -0.142f, 0.2f, 0.035f, 0.01f, shade(p.skin, 0.55f));
        if (p.bleed > 0) {
            limb(s, joint, 0, 0.77f, -0.143f, 0.07f, 0.09f, 0.01f, 0xFF8E0E12);
            limb(s, joint, 0.02f, 0.4f, -0.137f, 0.1f, 0.12f, 0.01f, 0xFF6E0A0E);
        }
    }

    private void limb(PartSink s, float[] at, float x, float y, float z, float w, float h,
                      float d, int color) {
        copy(tmp, at);
        Mat.translate(tmp, x, y, z);
        Mat.scale(tmp, w, h, d);
        s.part(tmp, color);
    }

    /** Where a person's head is, for speech bubbles and name tags. */
    static float headY(Person p) {
        if (p.fall > 0.3f) return 0.4f;
        return (p.sitting ? 1.62f : 1.84f) * p.scale + 0.25f;
    }

    // ================================================================== helpers

    private static void copy(float[] out, float[] in) {
        System.arraycopy(in, 0, out, 0, 16);
    }

    private static float turn(float from, float to, float k) {
        float d = to - from;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return from + d * Math.min(1, k);
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float dist(Person a, Person b) {
        return (float) Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static float dist2(float ax, float az, float bx, float bz) {
        return (ax - bx) * (ax - bx) + (az - bz) * (az - bz);
    }
}
