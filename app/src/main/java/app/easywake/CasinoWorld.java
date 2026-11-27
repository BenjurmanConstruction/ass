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

        /**
         * Moves every flat piece of ground (roads, sidewalks, floors, paint, crosswalks) to the
         * end, lowest first, and returns the vertex where they start. The renderer draws them
         * last without writing depth, so overlapping layers paint in order and never flicker.
         */
        int sortGround() {
            int tri = 3 * STRIDE, n = floats / tri;
            float[] out = new float[floats];
            java.util.List<Integer> ground = new java.util.ArrayList<>();
            int o = 0;
            for (int t = 0; t < n; t++) {
                int i = t * tri;
                boolean flat = data[i + 4] > 0.99f;
                for (int k = 0; k < 3 && flat; k++) {
                    float y = data[i + k * STRIDE + 1];
                    if (y < -0.01f || y > 0.06f) flat = false;
                }
                if (flat) ground.add(t);
                else {
                    System.arraycopy(data, i, out, o, tri);
                    o += tri;
                }
            }
            final float[] d = data;
            java.util.Collections.sort(ground, (a, b) -> Float.compare(
                    d[a * tri + 1] + d[a * tri + STRIDE + 1] + d[a * tri + 2 * STRIDE + 1],
                    d[b * tri + 1] + d[b * tri + STRIDE + 1] + d[b * tri + 2 * STRIDE + 1]));
            int start = o / STRIDE;
            for (int t : ground) {
                System.arraycopy(data, t * tri, out, o, tri);
                o += tri;
            }
            data = out;
            return start;
        }

        /** One triangle, with an upward normal for flat tops. map: world box to uv box. */
        void vertexTri(float[] a, float[] b, float[] c, int color, float emit, int tex,
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

    /** The casino's main room. */
    static final float X0 = -18, X1 = 18, Z0 = -14, Z1 = 14, CEIL = 5.4f;
    /** The whole walkable world. */
    static final float WX0 = -112, WX1 = 112, WZ0 = -82, WZ1 = 80;
    static final float SPAWN_X = 0, SPAWN_Z = 11.5f;
    /** Where security drops you: the sidewalk outside the front door. */
    static final float OUT_X = 0, OUT_Z = 16.5f;
    /** Wall thickness. */
    static final float T = 0.24f;
    static final float PI = (float) Math.PI;
    static final float NORTH = 0, EAST = PI / 2, SOUTH = PI, WEST = -PI / 2;

    // Palette: dark and warm, not candy.
    static final int WALL = 0xFF2B1A14, WAINSCOT = 0xFF1A0F0B, GOLD = 0xFFB8862E,
            CEILING = 0xFF120D0C, CABINET = 0xFF16161C, FELT = 0xFF0E5A34, RAIL = 0xFF3A2114,
            MARBLE = 0xFF8A8078, BRASS = 0xFFC9A24A, STONE = 0xFF3A3029;

    /** What a spot is. */
    static final int GAME = 0, DOOR = 1, LOOT = 2, NOTE = 3, CHIP = 4, KEY = 5, SHOP = 6,
            HIDE = 7, RANGE_START = 8;

    /** Something you can walk up to and use. */
    static final class Spot {
        int type;
        String id, label, key, unlock, text, chip;
        int extra, pins, reward, capacity, used;
        float x, z, y = 0.9f, yaw;
        boolean vip;
        Door door;

        Spot(String label, String key, int extra, String unlock, float x, float z, float yaw) {
            this.type = GAME;
            this.label = label;
            this.key = key;
            this.extra = extra;
            this.unlock = unlock;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
        }

        Spot(int type, String id, String label, float x, float z) {
            this.type = type;
            this.id = id;
            this.label = label;
            this.x = x;
            this.z = z;
        }
    }

    /** A door in a doorway. Locked ones need picking (pins) or a key card (key). */
    static final class Door {
        final String id, label;
        String key;
        int pins;
        final float x, z, w;
        final boolean alongX;
        int color;
        /** 0 shut, 1 swung open. */
        float open;
        /** Seconds an employee is holding it open. */
        float held;
        boolean unlocked;
        /** Uniform that opens it without picking: 1 staff, 2 security, 0 none. */
        int access;

        Door(String id, String label, float x, float z, boolean alongX, float w, int pins,
             int color) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.z = z;
            this.alongX = alongX;
            this.w = w;
            this.pins = pins;
            this.color = color;
        }

        boolean shut() {
            return !unlocked && held <= 0 && open < 0.5f;
        }

        float[] box() {
            float hw = w / 2, t = 0.12f;
            return alongX ? new float[]{x - hw, z - t, x + hw, z + t}
                    : new float[]{x - t, z - hw, x + t, z + hw};
        }
    }

    final List<Spot> spots = new ArrayList<>();
    /** Things you can't walk through: x0, z0, x1, z1, height. */
    final List<float[]> walls = new ArrayList<>();
    final List<Door> doors = new ArrayList<>();
    /** Rooms with a ceiling: x0, z0, x1, z1, ceiling height. Outside is anywhere else. */
    final List<float[]> regions = new ArrayList<>();
    /** Off-limits areas: x0, z0, x1, z1, level (1 staff, 2 private, 3 nobody). */
    final List<float[]> zones = new ArrayList<>();
    /** Lights that make pools on the floor: x, z, radius, strength, r, g, b. */
    final List<float[]> lamps = new ArrayList<>();
    /** Things that are done or found, saved by the phone: "door:vault", "chip:moon"... */
    final java.util.Set<String> flags = new java.util.HashSet<>();
    final Mesh mesh = new Mesh();

    float[] boardPanel;
    final float[][] holdemSeats = new float[6][];
    float[] dealer, croupier, cashier;
    final List<float[]> machines = new ArrayList<>();
    final List<float[]> islandSeats = new ArrayList<>();
    /** Seats elsewhere for regulars: x, z, facing. */
    final List<float[]> extraSeats = new ArrayList<>();
    /** Workers elsewhere: x, z, facing, and a name index. */
    final List<float[]> extraStaff = new ArrayList<>();
    final List<String> extraStaffNames = new ArrayList<>();

    private void build() {
        Mesh m = mesh;
        // Floor, ceiling and roof.
        m.floor(X0, Z0, X1, Z1, 0, 0xFFFFFFFF, 2, 2.2f);
        m.ceiling(X0, Z0, X1, Z1, CEIL, CEILING, 0);
        m.floor(X0 - T / 2, Z0 - T / 2, X1 + T / 2, Z1 + T / 2, CEIL + 0.3f, 0xFF15110F, 0, 1);
        regions.add(new float[]{X0, Z0, X1, Z1, CEIL});
        for (float x = -14; x <= 14.1f; x += 7) {
            for (float z = -10; z <= 10.1f; z += 6.5f) {
                m.ceiling(x - 1.3f, z - 1.3f, x + 1.3f, z + 1.3f, CEIL - 0.02f, 0xFF0A0807, 0);
                m.ceiling(x - 0.9f, z - 0.9f, x + 0.9f, z + 0.9f, CEIL - 0.04f, 0xFFFFD9A0,
                        0.85f);
                lamps.add(new float[]{x, z, 3, 0.35f, 1, 0.85f, 0.6f});
            }
        }
        // Walls: thick stone outside, wood and gold trim inside.
        wallX(Z0, X0 - T / 2, X1 + T / 2, CEIL + 0.3f, STONE, 15.2f, 16.4f);
        decorX(Z0 + T / 2 + 0.01f, X0, X1, SOUTH, 15.2f, 16.4f);
        wallX(Z1, X0 - T / 2, X1 + T / 2, CEIL + 0.3f, STONE, -1.6f, 1.6f);
        decorX(Z1 - T / 2 - 0.01f, X0, X1, NORTH, -1.6f, 1.6f);
        wallZ(X0, Z0, Z1, CEIL + 0.3f, STONE);
        decorZ(X0 + T / 2 + 0.01f, Z0, Z1, EAST);
        wallZ(X1, Z0, Z1, CEIL + 0.3f, STONE);
        decorZ(X1 - T / 2 - 0.01f, Z0, Z1, WEST);
        // Brass frame on the way out.
        m.boxAA(-1.75f, 0, Z1 - 0.3f, -1.6f, 3.0f, Z1 + 0.3f, BRASS, 0.15f);
        m.boxAA(1.6f, 0, Z1 - 0.3f, 1.75f, 3.0f, Z1 + 0.3f, BRASS, 0.15f);
        m.boxAA(-1.75f, 2.52f, Z1 - 0.32f, 1.75f, 3.0f, Z1 + 0.32f, BRASS, 0.15f);
        // The staff door, tucked in the north-east corner.
        Door staff = new Door("staff", "Staff Only", 15.8f, Z0, true, 1.2f, 3, 0xFF4A4E55);
        staff.access = 1;
        doors.add(staff);
        m.panel(15.8f, Z0 + T / 2 + 0.01f, SOUTH, 1.6f, 2.7f, 3.1f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.STAFF));

        // Pillars with brass bands.
        float[][] pillars = {{-6, -3}, {3, -3}, {-6, 8}, {5, 8}};
        for (float[] p : pillars) {
            m.boxAA(p[0] - 0.45f, 0, p[1] - 0.45f, p[0] + 0.45f, CEIL, p[1] + 0.45f, MARBLE, 0);
            m.boxAA(p[0] - 0.5f, 0, p[1] - 0.5f, p[0] + 0.5f, 0.3f, p[1] + 0.5f, 0xFF2A2420, 0);
            m.boxAA(p[0] - 0.48f, 2.6f, p[1] - 0.48f, p[0] + 0.48f, 2.72f, p[1] + 0.48f, BRASS,
                    0.25f);
            m.boxAA(p[0] - 0.5f, CEIL - 0.4f, p[1] - 0.5f, p[0] + 0.5f, CEIL, p[1] + 0.5f,
                    0xFF2A2420, 0);
            solid(p[0] - 0.5f, p[1] - 0.5f, p[0] + 0.5f, p[1] + 0.5f, CEIL);
        }

        float wz = Z0 + T / 2;
        // ---- Penny Palace: four games on the north wall under the live jackpot board.
        for (int i = 0; i < 4; i++) {
            float x = -15 + i * 1.9f;
            cabinet(x, wz + 0.33f, SOUTH, 5 + i, true);
            spots.add(new Spot(SlotMath.NAMES[5 + i], "penny", 5 + i, "parlor", x, wz + 1.63f,
                    NORTH));
        }
        float boardX = -15 + 1.5f * 1.9f;
        m.boxAA(boardX - 3.0f, 2.45f, wz, boardX + 3.0f, 4.75f, wz + 0.12f, 0xFF050507, 0);
        m.panel(boardX, wz + 0.15f, SOUTH, 5.8f, 2.5f, 4.7f, 0xFFFFFFFF, 1,
                CasinoArt.JACKPOT_BOARD);
        boardPanel = new float[]{boardX, wz + 0.13f};
        m.panel(boardX, wz + 0.05f, SOUTH, 4.6f, 4.8f, 5.3f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.PENNY));
        // A penny island with regulars glued to their stools.
        for (int i = 0; i < 3; i++) {
            float x = -14.5f + i * 1.0f;
            cabinetSmall(x, -6.6f, SOUTH, 5 + (i % 4));
            cabinetSmall(x, -7.4f, NORTH, 5 + ((i + 2) % 4));
        }
        solid(-15.1f, -7.9f, -12.0f, -6.1f, 2.2f);
        m.boxAA(-15.1f, 0, -7.1f, -11.9f, 0.08f, -6.9f, 0xFF4CE0A0, 0.8f);

        // ---- Slot Parlor: the five coupon machines on the north wall.
        for (int i = 0; i < 5; i++) {
            float x = 4 + i * 1.9f;
            cabinet(x, wz + 0.33f, SOUTH, i, true);
            spots.add(new Spot(SlotMath.NAMES[i], "machine", i, "parlor", x, wz + 1.63f,
                    NORTH));
        }
        m.panel(4 + 2 * 1.9f, wz + 0.05f, SOUTH, 4.6f, 3.0f, 3.5f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.PARLOR));

        // ---- The high-stakes pool table under its own lamp.
        poolTable(11.5f, -8.0f, 1);

        // ---- A service bar on the west wall, where the cocktail waitress picks up drinks.
        m.boxAA(X0 + 0.05f, 0, -1.0f, X0 + 0.85f, 1.05f, 1.6f, 0xFF2A1A12, 0);
        m.boxAA(X0 + 0.02f, 1.05f, -1.05f, X0 + 0.9f, 1.1f, 1.65f, 0xFFC8A870, 0.15f);
        for (int i = 0; i < 6; i++) {
            float bz = -0.8f + i * 0.4f;
            m.boxAA(X0 + 0.15f, 1.1f, bz, X0 + 0.27f, 1.42f, bz + 0.12f,
                    new int[]{0xFF2A6A3A, 0xFFB8862A, 0xFF8A2A2A, 0xFFD8D8E8}[i % 4], 0.3f);
        }
        m.boxAA(X0 + 0.02f, 0.3f, -1.0f, X0 + 0.04f, 0.34f, 1.6f, 0xFFFF6A8A, 1);
        solid(X0, -1.0f, X0 + 0.9f, 1.6f, 1.1f);
        extraStaff.add(new float[]{-16.6f, 0.3f, WEST, STAFF_OUTFIT, 0});
        extraStaffNames.add("Cocktail waitress");

        // ---- Video slots island: eight themed machines back to back, open to everyone.
        for (int i = 0; i < 4; i++) {
            float x = 7.4f + i * 2.1f;
            int a = CasinoArt.THEMED + i, b2 = CasinoArt.THEMED + 4 + i;
            cabinet(x, -3.4f, NORTH, a, true);
            spots.add(new Spot(ThemeSlotMath.THEMES[i].name, "themeslot", i, null, x, -4.7f,
                    SOUTH));
            cabinet(x, -2.6f, SOUTH, b2, true);
            spots.add(new Spot(ThemeSlotMath.THEMES[4 + i].name, "themeslot", 4 + i, null, x,
                    -1.3f, NORTH));
        }
        solid(6.7f, -3.75f, 14.4f, -2.25f, 2.4f);
        m.boxAA(6.6f, 0, -3.05f, 14.5f, 0.1f, -2.95f, 0xFFFF6AD5, 0.9f);
        // A sign hung over the island.
        m.boxAA(8.9f, 3.3f, -3.06f, 12.9f, 4.1f, -2.94f, 0xFF050507, 0);
        m.panel(10.9f, -3.08f, NORTH, 3.8f, 3.35f, 4.05f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.VIDEO_SLOTS));
        m.panel(10.9f, -2.92f, SOUTH, 3.8f, 3.35f, 4.05f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.VIDEO_SLOTS));
        m.boxAA(10.88f, 4.1f, -3.01f, 10.92f, CEIL, -2.99f, 0xFF2A2A2A, 0);

        // ---- Classic slots island (Lucky Sevens faces south, Royal Riches north).
        cabinet(-2.6f, -8.0f, SOUTH, 9, false);
        cabinet(-1.4f, -8.0f, SOUTH, 9, false);
        cabinet(-2.6f, -8.8f, NORTH, 10, false);
        cabinet(-1.4f, -8.8f, NORTH, 10, false);
        solid(-3.1f, -9.3f, -0.9f, -7.5f, 2.4f);
        spots.add(new Spot("Lucky Sevens", "slot", 0, "slot", -2.0f, -6.7f, NORTH));
        spots.add(new Spot("Royal Riches", "slot2", 0, "slot2", -2.0f, -10.1f, SOUTH));
        hangingSign(-2.0f, -8.4f, 3.6f, CasinoArt.CLASSIC, 0);

        // ---- Texas Hold'em table.
        float hx = 8, hz = 1.5f;
        m.cylinder(hx, hz, 0.7f, 0.35f, 0, 0.62f, 0xFF1A120E, 0, 0, 16);
        m.cylinder(hx, hz, 2.15f, 1.2f, 0.62f, 0.8f, RAIL, 0xFF2A1810, 0, 32);
        m.ovalTop(hx, hz, 1.85f, 0.95f, 0.81f, 0xFFFFFFFF, 32, CasinoArt.HOLDEM_FELT);
        m.cylinder(hx, hz, 2.18f, 1.23f, 0.79f, 0.84f, 0xFF120B08, 0, 0, 32);
        solid(hx - 2.0f, hz - 1.05f, hx + 2.0f, hz + 1.05f, 0.85f);
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
        solid(bx - 1.8f, bz - 0.5f, bx + 1.8f, bz + 1.0f, 0.85f);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * (0.2 + 0.2 * i);
            float sx = bx + (float) Math.cos(a) * 2.15f;
            float sz = bz - 0.4f + (float) Math.sin(a) * 1.75f;
            stool(sx, sz, 0xFF1A3A5A);
            blackjackSeats[i] = new float[]{sx, sz,
                    (float) Math.atan2(bx - sx, -((bz - 0.4f) - sz))};
        }
        dealer = new float[]{bx, bz - 1.0f, SOUTH};
        spots.add(new Spot("Blackjack", "blackjack", 0, "blackjack", bx, bz + 1.9f, NORTH));
        hangingSign(bx, bz, 3.8f, CasinoArt.BLACKJACK, 0);

        // ---- Roulette: the layout table and the wheel at its west end.
        float rx = -10, rz = 3;
        m.boxAA(rx - 1.5f, 0, rz - 0.75f, rx + 1.6f, 0.8f, rz + 0.75f, RAIL, 0);
        {
            CasinoArt.Region r = CasinoArt.ROULETTE_LAYOUT;
            float[] a = {rx - 1.35f, 0.812f, rz + 0.6f}, b = {rx + 1.45f, 0.812f, rz + 0.6f};
            float[] c = {rx + 1.45f, 0.812f, rz - 0.6f}, d = {rx - 1.35f, 0.812f, rz - 0.6f};
            m.quad(a, b, c, d, 0xFFFFFFFF, 0, 1, r.u0(), r.v0(), r.u1(), r.v1());
        }
        m.boxAA(rx - 2.6f, 0, rz - 0.75f, rx - 1.5f, 0.8f, rz + 0.75f, RAIL, 0);
        m.cylinder(rx - 2.05f, rz, 0.5f, 0.5f, 0.8f, 0.92f, 0xFF4A2A14, 0xFF6A3A1A, 0, 24);
        wheelTop(rx - 2.05f, rz, 0.46f, 0.925f);
        m.cylinder(rx - 2.05f, rz, 0.05f, 0.05f, 0.92f, 1.05f, BRASS, BRASS, 0.3f, 8);
        solid(rx - 2.7f, rz - 0.85f, rx + 1.7f, rz + 0.85f, 1.0f);
        croupier = new float[]{rx - 2.05f, rz - 1.15f, SOUTH};
        spots.add(new Spot("Roulette", "roulette", 0, "roulette", rx, rz + 1.45f, NORTH));
        hangingSign(rx, rz, 3.8f, CasinoArt.ROULETTE, 0);

        // ---- Video poker bar along the east wall.
        float ex = X1 - T / 2;
        m.boxAA(ex - 1.0f, 0, -3.2f, ex, 1.05f, 4.2f, 0xFF2A1810, 0);
        m.boxAA(ex - 1.15f, 1.05f, -3.3f, ex, 1.12f, 4.3f, 0xFF120B08, 0);
        m.boxAA(ex - 1.02f, 0.06f, -3.2f, ex - 0.98f, 0.12f, 4.2f, 0xFF40C4FF, 0.9f);
        solid(ex - 1.2f, -3.3f, ex, 4.3f, 1.6f);
        for (int i = 0; i < 4; i++) {
            float z = -2.3f + i * 1.8f;
            barTop(ex - 0.55f, z, WEST);
            stool(ex - 1.75f, z, 0xFF1A3A5A);
        }
        spots.add(new Spot("Video Poker", "vpoker", 0, "vpoker", ex - 2.0f, 0.4f, EAST));
        m.panel(ex - 0.05f, 0.45f, WEST, 4.6f, 2.6f, 3.1f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.POKER));

        // ---- Cashier cage in the south-west corner.
        float cx0 = X0 + T / 2, cz = 9.6f;
        m.boxAA(cx0, 0, cz, -13.4f, 1.1f, cz + 0.5f, 0xFF2A1810, 0);
        m.boxAA(cx0, 1.1f, cz - 0.05f, -13.35f, 1.16f, cz + 0.55f, BRASS, 0.15f);
        for (float x = cx0 + 0.3f; x < -13.4f; x += 0.28f) {
            m.boxAA(x, 1.16f, cz + 0.2f, x + 0.04f, 2.6f, cz + 0.26f, BRASS, 0.1f);
        }
        m.boxAA(cx0, 2.6f, cz + 0.1f, -13.35f, 3.4f, cz + 0.4f, 0xFF1A0F0B, 0);
        m.panel(-15.7f, cz + 0.09f, NORTH, 3.6f, 2.68f, 3.32f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.CASHIER));
        m.boxAA(-13.4f, 0, cz, -13.3f, 3.4f, Z1 - T / 2, 0xFF2A1810, 0);
        solid(X0, cz, -13.3f, Z1, 3.4f);
        cashier = new float[]{-15.7f, cz + 1.0f, NORTH};
        spots.add(new Spot("Cashier", "cashier", 0, null, -15.7f, cz - 0.7f, SOUTH));

        // A low brass rail around the jackpot corner, purely for looks.
        m.boxAA(-16.5f, 0.85f, -11.0f, -7.6f, 0.92f, -10.92f, BRASS, 0.2f);

        // Everything outside the main room, then the city around it.
        CasinoPlaces.build(this);
        CasinoCity.build(this);
        groundStart = mesh.sortGround();
        nav = new CasinoNav(WX0, WZ0, WX1, WZ1, walls);
        for (Door d : doors) nav.doorway(d.x, d.z, d.alongX, d.w);
    }

    /** The walkable grid people find their way around with. */
    CasinoNav nav;

    /**
     * Walks someone toward a point the smart way: straight if nothing's in the way, otherwise
     * along a route round the obstacles, finding a new route if they stop getting closer.
     */
    void goTo(Person p, float tx, float tz, float dt) {
        if (nav == null || nav.clear(p.x, p.z, tx, tz)) {
            p.path = null;
            step(p, tx, tz, dt);
            return;
        }
        boolean moved = p.path != null && Math.hypot(p.ptx - tx, p.ptz - tz) > 1.2f;
        p.repath -= dt;
        if (p.path == null || moved || p.repath <= 0 && p.pathStuck > 1.0f) {
            p.path = nav.find(p.x, p.z, tx, tz, 6000);
            p.pathI = 0;
            p.ptx = tx;
            p.ptz = tz;
            p.repath = 1.5f;
            p.pathStuck = 0;
            if (p.path == null) {
                step(p, tx, tz, dt);
                return;
            }
        }
        // Skip ahead to the furthest waypoint we can already walk straight to.
        while (p.pathI < p.path.length / 2 - 1
                && nav.clear(p.x, p.z, p.path[p.pathI * 2 + 2], p.path[p.pathI * 2 + 3])) {
            p.pathI++;
        }
        float wx = p.path[p.pathI * 2], wz = p.path[p.pathI * 2 + 1];
        if (Math.hypot(wx - p.x, wz - p.z) < 0.3f && p.pathI < p.path.length / 2 - 1) {
            p.pathI++;
            wx = p.path[p.pathI * 2];
            wz = p.path[p.pathI * 2 + 1];
        }
        float bx = p.x, bz = p.z;
        step(p, wx, wz, dt);
        float got = (float) Math.hypot(p.x - bx, p.z - bz);
        p.pathStuck = got < p.speed * dt * 0.4f ? p.pathStuck + dt : 0;
    }

    /** People ease apart instead of walking through each other. */
    private void crowd() {
        for (int i = 0; i < people.size(); i++) {
            Person a = people.get(i);
            if (!a.active || a.down > 0 || a.ko || a.dead || a.fall > 0) continue;
            for (int j = i + 1; j < people.size(); j++) {
                Person b = people.get(j);
                if (!b.active || b.down > 0 || b.ko || b.dead || b.fall > 0) continue;
                if (a.sitting && b.sitting) continue;
                float dx = b.x - a.x, dz = b.z - a.z;
                float d2 = dx * dx + dz * dz;
                float min = 0.62f;
                if (d2 >= min * min || d2 < 1e-6f) continue;
                float d = (float) Math.sqrt(d2);
                float push = (min - d) * 0.5f;
                float ux = dx / d, uz = dz / d;
                // The player isn't shoved about; everyone else gives way.
                // The player and anyone seated stay put; walkers give way.
                boolean fa = a == player || a.sitting, fb = b == player || b.sitting;
                float ka = fa ? 0 : (fb ? 1 : 0.5f);
                float kb = fb ? 0 : (fa ? 1 : 0.5f);
                a.x -= ux * push * 2 * ka;
                a.z -= uz * push * 2 * ka;
                b.x += ux * push * 2 * kb;
                b.z += uz * push * 2 * kb;
            }
        }
    }

    /** First vertex of the ground layers at the end of the mesh. */
    int groundStart;

    final float[][] blackjackSeats = new float[4][];

    // ---------------------------------------------------------------- building helpers

    /** Splits a to b into the solid stretches between gaps (pairs of start, end). */
    static List<float[]> split(float a, float b, float[] gaps) {
        List<float[]> out = new ArrayList<>();
        float cur = a;
        for (int i = 0; i + 1 < gaps.length; i += 2) {
            if (gaps[i] > cur) out.add(new float[]{cur, gaps[i]});
            cur = Math.max(cur, gaps[i + 1]);
        }
        if (cur < b) out.add(new float[]{cur, b});
        return out;
    }

    /** A thick wall running east-west at z, with doorway gaps. */
    void wallX(float z, float x0, float x1, float h, int color, float... gaps) {
        for (float[] s : split(x0, x1, gaps)) {
            mesh.boxAA(s[0], 0, z - T / 2, s[1], h, z + T / 2, color, 0);
            solid(s[0], z - T / 2, s[1], z + T / 2, h);
        }
        for (int i = 0; i + 1 < gaps.length; i += 2) {
            mesh.boxAA(gaps[i], 2.6f, z - T / 2, gaps[i + 1], h, z + T / 2, color, 0);
            walls.add(new float[]{gaps[i], z - T / 2, gaps[i + 1], z + T / 2, h, 2.6f});
        }
    }

    /** A thick wall running north-south at x, with doorway gaps. */
    void wallZ(float x, float z0, float z1, float h, int color, float... gaps) {
        for (float[] s : split(z0, z1, gaps)) {
            mesh.boxAA(x - T / 2, 0, s[0], x + T / 2, h, s[1], color, 0);
            solid(x - T / 2, s[0], x + T / 2, s[1], h);
        }
        for (int i = 0; i + 1 < gaps.length; i += 2) {
            mesh.boxAA(x - T / 2, 2.6f, gaps[i], x + T / 2, h, gaps[i + 1], color, 0);
            walls.add(new float[]{x - T / 2, gaps[i], x + T / 2, gaps[i + 1], h, 2.6f});
        }
    }

    /** The casino's wood-and-gold wall dressing on one face, skipping doorways. */
    private void decorX(float z, float x0, float x1, float faceYaw, float... gaps) {
        for (float[] s : split(x0, x1, gaps)) decor((s[0] + s[1]) / 2, z, faceYaw, s[1] - s[0]);
        for (int i = 0; i + 1 < gaps.length; i += 2) {
            float c = (gaps[i] + gaps[i + 1]) / 2, w = gaps[i + 1] - gaps[i];
            mesh.panel(c, z, faceYaw, w, 2.6f, CEIL, WALL, 0, null);
        }
    }

    private void decorZ(float x, float z0, float z1, float faceYaw) {
        decor(x, (z0 + z1) / 2, faceYaw, z1 - z0);
    }

    private void decor(float cx, float cz, float faceYaw, float len) {
        float fx = (float) Math.sin(faceYaw) * 0.006f, fz = (float) -Math.cos(faceYaw) * 0.006f;
        mesh.panel(cx, cz, faceYaw, len, 0, CEIL, WALL, 0, null);
        mesh.panel(cx + fx * 2, cz + fz * 2, faceYaw, len, 0, 1.1f, WAINSCOT, 0, null);
        mesh.panel(cx + fx * 4, cz + fz * 4, faceYaw, len, 1.08f, 1.14f, GOLD, 0.25f, null);
        mesh.panel(cx + fx * 4, cz + fz * 4, faceYaw, len, CEIL - 0.3f, CEIL - 0.24f, GOLD,
                0.25f, null);
        mesh.panel(cx + fx * 4, cz + fz * 4, faceYaw, len, 0, 0.12f, 0xFF0C0806, 0, null);
    }

    void solid(float x0, float z0, float x1, float z1) {
        solid(x0, z0, x1, z1, 2.4f);
    }

    void solid(float x0, float z0, float x1, float z1, float h) {
        walls.add(new float[]{x0, z0, x1, z1, h});
    }

    /** A full-size slot cabinet with its screen, topper and stool. */
    void cabinet(float x, float z, float yaw, int art, boolean stool) {
        Mesh m = mesh;
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        int glow = CasinoArt.GLOW[art];
        m.box(x, z, yaw, 0.38f, 0.3f, 0, 0.95f, CABINET, 0);
        m.box(x + fx * 0.32f, z + fz * 0.32f, yaw, 0.36f, 0.06f, 0.88f, 0.98f, 0xFF22222A, 0);
        for (int b = 0; b < 4; b++) {
            float off = -0.24f + b * 0.16f;
            float rx = (float) -Math.cos(yaw) * off, rz = (float) -Math.sin(yaw) * off;
            m.box(x + fx * 0.33f + rx, z + fz * 0.33f + rz, yaw, 0.05f, 0.03f, 0.98f, 1.01f,
                    b == 3 ? 0xFF4CAF50 : glow, 0.9f);
        }
        m.box(x - fx * 0.05f, z - fz * 0.05f, yaw, 0.37f, 0.22f, 0.95f, 1.92f, CABINET, 0);
        m.panel(x + fx * 0.172f, z + fz * 0.172f, yaw, 0.62f, 1.06f, 1.78f, 0xFFFFFFFF, 0.92f,
                CasinoArt.screen(art));
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float rx = (float) -Math.cos(yaw) * 0.355f * sgn;
            float rz = (float) -Math.sin(yaw) * 0.355f * sgn;
            m.box(x + fx * 0.15f + rx, z + fz * 0.15f + rz, yaw, 0.018f, 0.03f, 0.98f, 1.9f,
                    glow, 0.95f);
        }
        m.box(x - fx * 0.05f, z - fz * 0.05f, yaw, 0.39f, 0.24f, 1.92f, 2.4f, 0xFF0E0E12, 0);
        m.panel(x + fx * 0.192f, z + fz * 0.192f, yaw, 0.74f, 1.95f, 2.37f, 0xFFFFFFFF, 1,
                CasinoArt.topper(art));
        m.box(x + fx * 0.31f, z + fz * 0.31f, yaw, 0.38f, 0.01f, 0.02f, 0.06f, glow, 1);
        solid(Math.min(x - 0.4f, x + fx * 0.36f), Math.min(z - 0.4f, z + fz * 0.36f),
                Math.max(x + 0.4f, x + fx * 0.36f), Math.max(z + 0.4f, z + fz * 0.36f), 2.4f);
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

    private void barTop(float x, float z, float yaw) {
        Mesh m = mesh;
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        m.box(x, z, yaw, 0.32f, 0.26f, 1.12f, 1.2f, CABINET, 0);
        m.box(x - fx * 0.12f, z - fz * 0.12f, yaw, 0.3f, 0.08f, 1.2f, 1.62f, CABINET, 0);
        m.panel(x - fx * 0.035f, z - fz * 0.035f, yaw, 0.52f, 1.23f, 1.6f, 0xFFFFFFFF, 0.92f,
                CasinoArt.screen(CasinoArt.VIDEO_POKER));
    }

    /**
     * A nine-foot pool table, long side along x, with a lamp over it and a rack set up.
     * venue: 0 a bar game, 1 the casino's high-stakes table.
     */
    void poolTable(float x, float z, int venue) {
        Mesh m = mesh;
        float hx = 1.42f, hz = 0.78f;
        int wood = venue == 1 ? 0xFF3A1A10 : 0xFF5A3418;
        int felt = venue == 1 ? 0xFF1A3A7A : 0xFF167A44;
        // Legs and body.
        for (float sx : new float[]{-1, 1}) {
            for (float sz : new float[]{-1, 1}) {
                m.boxAA(x + sx * (hx - 0.22f) - 0.1f, 0, z + sz * (hz - 0.2f) - 0.1f,
                        x + sx * (hx - 0.22f) + 0.1f, 0.6f, z + sz * (hz - 0.2f) + 0.1f, wood, 0);
            }
        }
        m.boxAA(x - hx + 0.06f, 0.55f, z - hz + 0.06f, x + hx - 0.06f, 0.74f, z + hz - 0.06f,
                wood, 0);
        // Rails, then the felt sitting between them.
        float r = 0.13f;
        m.boxAA(x - hx, 0.74f, z - hz, x + hx, 0.84f, z - hz + r, wood, 0);
        m.boxAA(x - hx, 0.74f, z + hz - r, x + hx, 0.84f, z + hz, wood, 0);
        m.boxAA(x - hx, 0.74f, z - hz + r, x - hx + r, 0.84f, z + hz - r, wood, 0);
        m.boxAA(x + hx - r, 0.74f, z - hz + r, x + hx, 0.84f, z + hz - r, wood, 0);
        m.boxAA(x - hx + r, 0.74f, z - hz + r, x + hx - r, 0.8f, z + hz - r, felt, 0.08f);
        // Diamonds on the rails.
        for (int i = 1; i < 8; i++) {
            if (i == 4) continue;
            float dx = x - hx + 2 * hx * i / 8f;
            m.boxAA(dx - 0.02f, 0.842f, z - hz + 0.05f, dx + 0.02f, 0.846f, z - hz + 0.08f,
                    0xFFF2E6C8, 0.3f);
            m.boxAA(dx - 0.02f, 0.842f, z + hz - 0.08f, dx + 0.02f, 0.846f, z + hz - 0.05f,
                    0xFFF2E6C8, 0.3f);
        }
        // Pockets.
        float[][] pk = {{-hx + r, -hz + r}, {0, -hz + r}, {hx - r, -hz + r}, {-hx + r, hz - r},
                {0, hz - r}, {hx - r, hz - r}};
        for (float[] p : pk) {
            m.boxAA(x + p[0] - 0.07f, 0.79f, z + p[1] - 0.07f, x + p[0] + 0.07f, 0.805f,
                    z + p[1] + 0.07f, 0xFF050505, 0);
        }
        // A racked triangle of balls and the cue ball.
        int[] cols = {0xFFF5C518, 0xFF1E4FD8, 0xFFD62828, 0xFF6A2C91, 0xFFF77F00, 0xFF1B7F3B,
                0xFF7A1E1E, 0xFF141414};
        int k = 0;
        float bs = 0.06f;
        for (int row = 0; row < 5; row++) {
            for (int j = 0; j <= row; j++) {
                float bx = x + 0.55f + row * bs * 0.87f, bz = z + (j - row / 2f) * bs;
                m.boxAA(bx - 0.028f, 0.8f, bz - 0.028f, bx + 0.028f, 0.856f, bz + 0.028f,
                        cols[k++ % cols.length], 0.15f);
            }
        }
        m.boxAA(x - 0.75f, 0.8f, z - 0.028f, x - 0.694f, 0.856f, z + 0.028f, 0xFFF4F1E8, 0.2f);
        // Two cues leaning on the table.
        m.boxAA(x + hx + 0.02f, 0.05f, z - 0.35f, x + hx + 0.05f, 1.45f, z - 0.32f,
                0xFFC99A5A, 0);
        m.boxAA(x + hx + 0.02f, 0.05f, z + 0.32f, x + hx + 0.05f, 1.45f, z + 0.35f,
                0xFFC99A5A, 0);
        // The lamp: a long shade hanging on two rods.
        m.boxAA(x - 1.0f, 2.55f, z - 0.2f, x + 1.0f, 2.72f, z + 0.2f,
                venue == 1 ? 0xFF1A1A30 : 0xFF1E3A22, 0);
        m.boxAA(x - 0.95f, 2.54f, z - 0.17f, x + 0.95f, 2.55f, z + 0.17f, 0xFFFFF0C8, 1);
        m.boxAA(x - 0.62f, 2.72f, z - 0.015f, x - 0.6f, 4.2f, z + 0.015f, 0xFF2A2A2A, 0);
        m.boxAA(x + 0.6f, 2.72f, z - 0.015f, x + 0.62f, 4.2f, z + 0.015f, 0xFF2A2A2A, 0);
        lamps.add(new float[]{x, z, 2.5f, 0.4f, 1, 0.9f, 0.7f});
        solid(x - hx, z - hz, x + hx, z + hz, 0.9f);
        Spot s = new Spot(venue == 1 ? "Eight-ball (high stakes)" : "Eight-ball", "pool",
                venue, null, x, z + hz + 0.55f, NORTH);
        spots.add(s);
        Spot s2 = new Spot(venue == 1 ? "Eight-ball (high stakes)" : "Eight-ball", "pool",
                venue, null, x, z - hz - 0.55f, SOUTH);
        spots.add(s2);
    }

    void stool(float x, float z, int seat) {
        mesh.cylinder(x, z, 0.04f, 0.04f, 0, 0.62f, 0xFF2A2420, 0, 0, 6);
        mesh.cylinder(x, z, 0.2f, 0.2f, 0.0f, 0.03f, 0xFF2A2420, 0xFF2A2420, 0, 10);
        mesh.cylinder(x, z, 0.22f, 0.22f, 0.58f, 0.68f, shade(seat, 0.8f), seat, 0, 12);
    }

    void hangingSign(float x, float z, float y, int sign, float yaw) {
        Mesh m = mesh;
        m.boxAA(x - 0.015f, y + 0.5f, z - 0.015f, x + 0.015f, CEIL, z + 0.015f, 0xFF2A2420, 0);
        m.box(x, z, NORTH + yaw, 1.45f, 0.06f, y - 0.02f, y + 0.52f, 0xFF050507, 0);
        CasinoArt.Region r = CasinoArt.sign(sign);
        m.panel(x, z + 0.085f, SOUTH, 2.8f, y, y + 0.5f, 0xFFFFFFFF, 1, r);
        m.panel(x, z - 0.085f, NORTH, 2.8f, y, y + 0.5f, 0xFFFFFFFF, 1, r);
    }

    private void halfMoon(float cx, float cz, float rx, float rz, CasinoArt.Region felt) {
        Mesh m = mesh;
        int segs = 20;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float x0 = cx + (float) Math.cos(a0) * rx, z0 = cz + (float) Math.sin(a0) * rz;
            float x1 = cx + (float) Math.cos(a1) * rx, z1 = cz + (float) Math.sin(a1) * rz;
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

    // ================================================================== lighting map

    /** Light map resolution: texels per metre is 1 / LM_STEP. */
    static final float LM_STEP = 0.33f;
    static final int LM_W = (int) ((WX1 - WX0) / LM_STEP), LM_H = (int) ((WZ1 - WZ0) / LM_STEP);

    /** Which room a point is in, or -1 outside. */
    int regionAt(float x, float z) {
        for (int i = 0; i < regions.size(); i++) {
            float[] r = regions.get(i);
            if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) return i;
        }
        return -1;
    }

    /**
     * Bakes the floor light pools and which places are indoors into one picture: RGB is the
     * extra light (half scale), alpha is 255 indoors and 0 outside.
     */
    int[] bakeLight() {
        int[] out = new int[LM_W * LM_H];
        float[] acc = new float[LM_W * LM_H * 3];
        int[] reg = new int[LM_W * LM_H];
        for (int j = 0; j < LM_H; j++) {
            for (int i = 0; i < LM_W; i++) {
                reg[j * LM_W + i] = regionAt(WX0 + (i + 0.5f) * LM_STEP, WZ0 + (j + 0.5f) * LM_STEP);
            }
        }
        for (float[] l : lamps) {
            int lr = regionAt(l[0], l[1]);
            float rad = l[2];
            int i0 = Math.max(0, (int) ((l[0] - rad * 2.6f - WX0) / LM_STEP));
            int i1 = Math.min(LM_W - 1, (int) ((l[0] + rad * 2.6f - WX0) / LM_STEP));
            int j0 = Math.max(0, (int) ((l[1] - rad * 2.6f - WZ0) / LM_STEP));
            int j1 = Math.min(LM_H - 1, (int) ((l[1] + rad * 2.6f - WZ0) / LM_STEP));
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    int k = j * LM_W + i;
                    if (reg[k] != lr) continue;
                    float dx = WX0 + (i + 0.5f) * LM_STEP - l[0];
                    float dz = WZ0 + (j + 0.5f) * LM_STEP - l[1];
                    float v = (float) Math.exp(-(dx * dx + dz * dz) / (rad * rad)) * l[3];
                    acc[k * 3] += v * l[4];
                    acc[k * 3 + 1] += v * l[5];
                    acc[k * 3 + 2] += v * l[6];
                }
            }
        }
        for (int k = 0; k < out.length; k++) {
            int r = Math.min(255, (int) (acc[k * 3] * 0.5f * 255));
            int g = Math.min(255, (int) (acc[k * 3 + 1] * 0.5f * 255));
            int b = Math.min(255, (int) (acc[k * 3 + 2] * 0.5f * 255));
            out[k] = ((reg[k] >= 0 ? 255 : 0) << 24) | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    // ================================================================== people

    static final int WANDER = 0, SEATED = 1, STAFF = 2, GUARD = 3, PLAYER = 4, ROUTE = 5,
            COP = 6;
    /** Outfits. Staff and cooks get into staff areas; security gets in almost anywhere. */
    static final int CIVILIAN = 0, STAFF_OUTFIT = 1, SECURITY = 2, COOK = 3;
    static final String[] OUTFIT_NAMES = {"your own clothes", "a staff uniform",
            "a security uniform", "a cook's whites"};

    static final class Person {
        int kind;
        String name;
        float x, z, yaw, scale = 1;
        int skin, shirt, pants, hair;
        boolean vest, hat, tie;
        /** Your own style: 0 means none. */
        int jacket, shoes, hatType, hatColor, glasses, chain, chainColor, watch;
        boolean suit, shoeGlow;
        int outfit;
        float homeX, homeZ, homeYaw;
        boolean sitting;
        float tx, tz, speed = 1.1f, phase, walk, idle, stuck, lastDist;
        float down, fall, fallRaw, kx, kz, angry, punch, shake, pushX, pushZ;
        boolean tough, swung;
        float bleed;
        String bubble;
        float bubbleT;
        boolean active = true;
        /** Where a wanderer roams: x0, z0, x1, z1. */
        float[] area;
        /** Out cold, dead, found by someone, outfit taken, stuffed somewhere. */
        boolean ko, dead, found, stripped, hidden;
        /** A loop of stops: x, z, seconds to wait, facing (NaN for any), sit (1). */
        float[][] route;
        int routeI;
        float wait;
        boolean target;
        float flee;
        /** When the player last hit them, for blaming car accidents. */
        float hitAt = -100;
        String[] talk;
        float talkT;
        float health = 100;
        /** Seconds since this guard last fired. */
        float gunCool;
        // Fighting: the current move and its timing, see CasinoFight.
        int act;
        float actT, actWind, actDur, blockAt, blockRoll = -1;
        boolean actHit, guard;
        float dodgeX, dodgeZ;
        boolean fighter, startedIt, reported, robbed;
        int style;
        float power = 1, fightCool, cower, challengeT, comboAt; int combo;
        /** A route being walked (x, z pairs), where it's headed, and how it's going. */
        float[] path;
        int pathI;
        float ptx, ptz, repath, pathStuck;
        /** How high the seat is (0.68 a stool, 0.45 a booth). */
        float seatY = 0.68f;
        /** A badly hurt fighter has made up their mind: run or fight on. */
        boolean hurtDecided;
        /** A glass in hand (its color), how long it lasts, and sipping. */
        int carry;
        float drinkT, sipT, sip;
        /** Serving staff: where they pour, who they serve, and how far across the counter. */
        float[] tap;
        List<Person> patrons;
        float reach;
        int drinkColor, serveState;
        float serveT;
        Person serving;
        String[] serveLines;
        final int id = nextPersonId++;
    }

    private static int nextPersonId;
    static final float DOWN_TIME = 3.4f;
    static final float PUNCH_TIME = 0.42f;
    static final float SPEED = 3.3f, DRAG_SPEED = 1.7f;

    final List<Person> people = new ArrayList<>();
    final Person player = new Person();
    final List<Person> guards = new ArrayList<>();
    private final Random rnd;
    float time;
    float camYaw = 0, camPitch = 0.32f, camDist = 4.6f;
    /** Bits for the phone to play sounds: 1 a hit landed, 2 a swing missed, 4 a shot. */
    int sounds;
    /** A short message to show, or null. */
    String toast;
    /** Above 0 while security is walking you out (the screen fades). */
    float escort;
    private float tableTalk = 6;

    // Suspicion and alarm.
    float suspicion;
    boolean alarm;
    float alarmT;
    /** Someone's eyes on you right now, for the overlay. */
    boolean watched;
    private boolean warned;
    /** Lock picking in progress (the phone sets this). */
    boolean picking;
    boolean hasPistol;
    Person dragging;
    private int playerShirt, playerPants;

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
    static final String[] TRESPASS = {"Hey! You can't be back here.",
            "Excuse me. Staff only.", "Who let you in here?", "You lost, pal?"};
    static final String[] ODD = {"What are you doing?", "Is that... a body?",
            "Hey. HEY. What is that?", "I'm calling someone."};
    static final String[] BODY_FOUND = {"Oh my god! Somebody help!", "Security! Body!",
            "Is he breathing?!", "Nobody touch anything!"};
    static final String[] PANIC = {"AAAH!", "Run!", "He's got a gun!", "Not today!",
            "I'm too young for this!"};
    static final String[] ALARM_GUARD = {"Stop right there!", "Got eyes on him!",
            "Don't let him leave!"};

    CasinoTraffic traffic;
    CasinoFight fight;
    /** Coupons found on someone you knocked out, for the phone to pay. */
    int cashDue;
    /** Parked cars to place when the world starts: x, z, heading, color, type. */
    final List<float[]> parkedCars = new ArrayList<>();
    /** Smoke and fire: x, y, z, rise speed, life, size, 1 if fire. */
    final List<float[]> smokes = new ArrayList<>();
    private float lastLoud = -9;

    CasinoWorld(long seed) {
        rnd = new Random(seed);
        build();
        setupPeople();
        traffic = new CasinoTraffic(this, seed);
        traffic.spawnAll();
        fight = new CasinoFight(this, seed);
    }

    Person newPerson(int kind) {
        Person p = new Person();
        p.kind = kind;
        p.skin = SKIN[rnd.nextInt(SKIN.length)];
        p.shirt = SHIRT[rnd.nextInt(SHIRT.length)];
        p.pants = PANTS[rnd.nextInt(PANTS.length)];
        p.hair = HAIR[rnd.nextInt(HAIR.length)];
        p.scale = 0.93f + rnd.nextFloat() * 0.14f;
        p.tough = rnd.nextFloat() < 0.3f;
        p.phase = rnd.nextFloat() * 6;
        p.health = 100;
        people.add(p);
        return p;
    }

    static void dress(Person p, int outfit) {
        p.outfit = outfit;
        p.vest = false;
        p.tie = false;
        switch (outfit) {
            case STAFF_OUTFIT:
                p.shirt = 0xFFE8E4DC;
                p.pants = 0xFF141418;
                p.vest = true;
                break;
            case SECURITY:
                p.shirt = 0xFF141418;
                p.pants = 0xFF141418;
                p.tie = true;
                break;
            case COOK:
                p.shirt = 0xFFF2F0EA;
                p.pants = 0xFF5A5A60;
                break;
            default:
                break;
        }
    }

    private void setupPeople() {
        player.kind = PLAYER;
        player.x = SPAWN_X;
        player.z = SPAWN_Z;
        player.yaw = NORTH;
        player.skin = 0xFFE0AC84;
        player.shirt = playerShirt = 0xFF1F6F78;
        player.pants = playerPants = 0xFF22252B;
        player.hair = 0xFF1A120C;
        camYaw = NORTH;

        float[] casinoArea = {X0 + 1, Z0 + 1.5f, X1 - 1, Z1 - 1};
        for (int i = 0; i < 7; i++) wanderer(casinoArea);
        for (float[] a : CasinoPlaces.OUTSIDE_AREAS) {
            wanderer(a);
            if (a[2] - a[0] > 40) wanderer(a);
        }
        int[] taken = {0, 2, 3, 5};
        for (int t : taken) {
            if (t >= islandSeats.size()) continue;
            float[] s = islandSeats.get(t);
            seat(newPerson(SEATED), s[0], s[1], s[2]);
        }
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
        for (float[] s : extraSeats) {
            seat(newPerson(SEATED), s[0], s[1], s[2], s.length > 3 ? s[3] : 0.68f);
        }
        staff(dealer, "Dealer", STAFF_OUTFIT);
        staff(croupier, "Croupier", STAFF_OUTFIT);
        staff(cashier, "Cashier", STAFF_OUTFIT);
        for (int i = 0; i < extraStaff.size(); i++) {
            float[] s = extraStaff.get(i);
            Person p = staff(s, extraStaffNames.get(i), (int) s[3]);
            if (s.length > 4 && s[4] > 0) p.talk = CasinoPlaces.DICE_TALK;
        }
        setupServers();
        // Security on patrol: one on the floor, one in the back.
        Person g1 = guard(CasinoPlaces.FLOOR_PATROL);
        Person g2 = guard(CasinoPlaces.BACK_PATROL);
        g1.name = "Security";
        g2.name = "Security";
    }

    private void wanderer(float[] area) {
        Person p = newPerson(WANDER);
        p.area = area;
        float[] at = openSpot(area);
        p.x = at[0];
        p.z = at[1];
        p.speed = 0.9f + rnd.nextFloat() * 0.5f;
        pickTarget(p);
    }

    private Person guard(float[][] route) {
        Person g = newPerson(GUARD);
        dress(g, SECURITY);
        g.scale = 1.1f;
        g.speed = 1.2f;
        g.tough = true;
        g.health = 150;
        g.route = route;
        g.x = route[0][0];
        g.z = route[0][1];
        guards.add(g);
        return g;
    }

    private void seat(Person p, float x, float z, float yaw) {
        seat(p, x, z, yaw, 0.68f);
    }

    private void seat(Person p, float x, float z, float yaw, float seatY) {
        p.seatY = seatY;
        p.homeX = p.x = x;
        p.homeZ = p.z = z;
        p.homeYaw = p.yaw = yaw;
        p.sitting = true;
    }

    private Person staff(float[] at, String name, int outfit) {
        Person p = newPerson(STAFF);
        p.homeX = p.x = at[0];
        p.homeZ = p.z = at[1];
        p.homeYaw = p.yaw = at[2];
        dress(p, outfit);
        p.name = name;
        return p;
    }

    private float[] openSpot(float[] a) {
        for (int tries = 0; tries < 200; tries++) {
            float x = a[0] + rnd.nextFloat() * (a[2] - a[0]);
            float z = a[1] + rnd.nextFloat() * (a[3] - a[1]);
            if (free(x, z, 0.9f)) return new float[]{x, z};
        }
        return new float[]{(a[0] + a[2]) / 2, (a[1] + a[3]) / 2};
    }

    private boolean free(float x, float z, float margin) {
        for (float[] w : walls) {
            if (w.length > 5) continue;
            if (x > w[0] - margin && x < w[2] + margin && z > w[1] - margin && z < w[3] + margin) {
                return false;
            }
        }
        return true;
    }

    private void pickTarget(Person p) {
        float[] t = openSpot(p.area == null ? new float[]{X0 + 1, Z0 + 1.5f, X1 - 1, Z1 - 1}
                : p.area);
        p.tx = t[0];
        p.tz = t[1];
        p.stuck = 0;
        p.lastDist = 1e9f;
    }

    // ================================================================== cars

    static final int TAXI_YELLOW = 0xFFE8B420;
    static final int SEDAN = 0, SUV = 1, TAXI = 2, PICKUP = 3;

    // ================================================================== moving

    private final List<Person> all = new ArrayList<>();

    List<Person> allPeople() {
        all.clear();
        all.add(player);
        all.addAll(people);
        return all;
    }

    /**
     * Moves everything forward by dt seconds. sx, sy: the joystick, -1..1, with sy negative
     * meaning forward.
     */
    void update(float dt, float sx, float sy) {
        dt = Math.min(dt, 0.05f);
        if (fight != null) dt *= fight.timeScale();
        time += dt;
        sounds = 0;

        if (escort > 0) {
            float before = escort;
            escort -= dt;
            if (before > 0.6f && escort <= 0.6f) {
                if (dragging != null) dragging = null;
                player.x = OUT_X;
                player.z = OUT_Z;
                player.yaw = SOUTH;
                camYaw = SOUTH;
                alarm = false;
                suspicion = 0;
                for (Person g : guards) {
                    g.x = g.route[0][0];
                    g.z = g.route[0][1];
                    g.routeI = 0;
                }
                toast = downed ? "WASTED. You woke up outside the casino. The job's off."
                        : "Security threw you out. Lie low for a bit.";
                if (traffic.driving != null) exitCar(false);
                if (traffic.busted) {
                    player.x = CasinoCity.BUSTED_X;
                    player.z = CasinoCity.BUSTED_Z;
                    player.yaw = WEST;
                    camYaw = WEST;
                    traffic.fineDue = 200;
                    toast = "BUSTED. You paid a fine and walked out of the station.";
                    traffic.busted = false;
                    abortContract();
                }
                if (downed) {
                    abortContract();
                    downed = false;
                }
                traffic.reset();
                health = 100;
                aiming = false;
            }
            sx = sy = 0;
        }

        // ---- the player
        Person me = player;
        float carX = sx, carY = sy;
        if (traffic.driving != null) sx = sy = 0;
        float mag = (float) Math.min(1, Math.hypot(sx, sy));
        if (picking || me.down > 0 || (CasinoFight.busy(me) && me.act != CasinoFight.HIT)) mag = 0;
        if (me.act == CasinoFight.BLOCK) mag *= 0.4f;
        float speed = dragging != null ? DRAG_SPEED : (aiming ? 1.9f : SPEED);
        // Stick pushed all the way: run.
        running = mag > 0.9f && dragging == null && !aiming && me.act == CasinoFight.NONE;
        if (running) speed *= 1.65f;
        if (mag > 0.08f) {
            float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
            float rx = (float) Math.cos(camYaw), rz = (float) Math.sin(camYaw);
            float dx = rx * sx - fx * sy, dz = rz * sx - fz * sy;
            float l = (float) Math.hypot(dx, dz);
            dx /= l;
            dz /= l;
            float want = aiming ? camYaw : (float) Math.atan2(dx, -dz);
            me.yaw = turn(me.yaw, want, dt * 12);
            me.x += dx * speed * mag * dt;
            me.z += dz * speed * mag * dt;
            me.walk = mag;
            me.phase += dt * 9.5f * mag * Math.min(1.3f, speed / SPEED);
            // The camera swings round behind you unless you've just looked around.
            if (!aiming && lookT <= 0 && mag > 0.3f) {
                float diff = Math.abs(angleDiff(me.yaw, camYaw));
                if (diff < 2.0f) camYaw = turn(camYaw, me.yaw, dt * (running ? 2.4f : 1.5f) * mag);
            }
        } else {
            me.walk = Math.max(0, me.walk - dt * 6);
        }
        me.x += me.pushX * dt;
        me.z += me.pushZ * dt;
        me.pushX *= (float) Math.pow(0.02, dt);
        me.pushZ *= (float) Math.pow(0.02, dt);
        collide(me, 0.33f);
        if (me.shake > 0) me.shake -= dt;
        if (me.bleed > 0) me.bleed -= dt;
        if (dragging != null) {
            float fx = (float) Math.sin(me.yaw), fz = (float) -Math.cos(me.yaw);
            Person b = dragging;
            b.x += (me.x - fx * 1.0f - b.x) * Math.min(1, dt * 10);
            b.z += (me.z - fz * 1.0f - b.z) * Math.min(1, dt * 10);
            b.yaw = me.yaw + PI;
        }

        if (me.punch > 0) me.punch -= dt;
        fight.update(dt);

        traffic.update(dt, escort > 0 ? 0 : carX, escort > 0 ? 0 : carY);
        CasinoTraffic.Car car = traffic.driving;
        if (car != null) {
            me.x = car.x;
            me.z = car.z;
            me.yaw = car.h;
            me.walk = 0;
            if (Math.abs(car.speed) > 1.5f && lookT <= 0) {
                float follow = car.speed < 0 ? car.h + PI : car.h;
                camYaw = turn(camYaw, follow, dt * 2.2f);
            }
        }
        if (lookT > 0) lookT -= dt;
        updateSmoke(dt);
        updateBlood(dt);
        updateDoors(dt);
        crowd();

        // ---- everyone else
        for (Person p : people) {
            if (!p.active) continue;
            if (p.bubbleT > 0) p.bubbleT -= dt;
            if (p.angry > 0) p.angry -= dt;
            if (p.bleed > 0) {
                p.bleed -= dt;
                if (p.down <= 0 && !p.ko && !p.dead && rnd.nextFloat() < dt * 1.5f) {
                    float fx = (float) Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
                    drops.add(new float[]{p.x + fx * 0.18f, 1.62f * p.scale, p.z + fz * 0.18f,
                            0, 0, 0, 0.03f});
                }
            }
            if (p.ko || p.dead) {
                p.fallRaw = Math.min(1, p.fallRaw + dt * 3.3f);
                p.fall = ease(p.fallRaw);
                if (p != dragging) {
                    p.x += p.kx * dt;
                    p.z += p.kz * dt;
                    p.kx *= (float) Math.pow(0.01, dt);
                    p.kz *= (float) Math.pow(0.01, dt);
                    collide(p, 0.3f);
                }
                p.walk = 0;
                p.sitting = false;
                continue;
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
                    if (!p.fighter) {
                        say(p, p.kind == STAFF ? STAFF_OUCH : OUCH);
                        p.angry = 5;
                        if (p.kind == WANDER) {
                            p.speed = 1.9f;
                            pickTarget(p);
                        }
                    }
                }
                continue;
            }
            if (p.punch > 0) p.punch -= dt;
            if (p.fighter && fight.think(p, dt)) continue;
            fight.animate(p, dt);
            if (CasinoFight.busy(p)) {
                p.walk = Math.max(0, p.walk - dt * 6);
                continue;
            }
            if (p.cower > 0) {
                p.cower -= dt;
                p.walk = 0;
                p.act = CasinoFight.BLOCK;
                if (p.cower <= 0) p.act = CasinoFight.NONE;
                continue;
            }
            if (p.flee > 0) {
                p.flee -= dt;
                float dx = p.x - me.x, dz = p.z - me.z;
                float l = Math.max(0.01f, (float) Math.hypot(dx, dz));
                float sp = p.speed;
                p.speed = 3.0f;
                p.sitting = false;
                step(p, p.x + dx / l * 3, p.z + dz / l * 3, dt);
                p.speed = sp;
                continue;
            }
            if (p.kind == GUARD && alarm && escort <= 0 && nearCasino(me)) {
                chase(p, dt);
                continue;
            }
            if (p.kind == COP) {
                copThink(p, dt);
                continue;
            }
            if (p.carry != 0 && p.sitting) drinking(p, dt);
            if (p.patrons != null && p.kind == STAFF) {
                serve(p, dt);
                continue;
            }
            switch (p.kind) {
                case WANDER:
                    wander(p, dt);
                    break;
                case GUARD:
                case ROUTE:
                    followRoute(p, dt);
                    break;
                default:
                    goHome(p, dt);
                    break;
            }
            if (p.challengeT > 0 && p.kind == WANDER) {
                p.tx = me.x;
                p.tz = me.z;
                p.idle = 0;
            }
            if (p.talk != null && dist(p, me) < 9) {
                p.talkT -= dt;
                if (p.talkT <= 0) {
                    p.talkT = 6 + rnd.nextFloat() * 7;
                    say(p, p.talk);
                }
            }
        }

        tableTalk -= dt;
        if (tableTalk <= 0) {
            tableTalk = 7 + rnd.nextFloat() * 8;
            List<Person> seated = new ArrayList<>();
            for (Person p : people) {
                if (p.name != null && p.kind == SEATED && p.sitting && p.down <= 0 && !p.ko
                        && !p.dead) {
                    seated.add(p);
                }
            }
            if (!seated.isEmpty() && dist2(8, 1.5f, me.x, me.z) < 9 * 9) {
                say(seated.get(rnd.nextInt(seated.size())), TABLE_TALK);
            }
        }

        updateCombat(dt);
        updateRange(dt);
        updateSuspicion(dt);
        if (alarm && escort <= 0) {
            alarmT -= dt;
            if (alarmT <= 0) {
                alarm = false;
                suspicion = 0.5f;
                toast = "Security lost you.";
            }
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
        p.stuck += dt;
        if (p.stuck > 4f) {
            // Not getting anywhere: go somewhere else.
            if (d > p.lastDist - 0.5f) pickTarget(p);
            p.lastDist = d;
            p.stuck = 0;
        }
        goTo(p, p.tx, p.tz, dt);
    }

    /** Walks a loop of stops, waiting (and maybe sitting) at each. */
    private void followRoute(Person p, float dt) {
        float[] s = p.route[p.routeI];
        float d = (float) Math.hypot(s[0] - p.x, s[1] - p.z);
        if (d > 0.15f) {
            p.sitting = false;
            float sp = p.speed;
            if (p.kind == GUARD) p.speed = 1.25f;
            if (d > 1.0f) goTo(p, s[0], s[1], dt);
            else step(p, s[0], s[1], dt, false);
            p.speed = sp;
            p.wait = s[2];
            return;
        }
        p.x = s[0];
        p.z = s[1];
        if (!Float.isNaN(s[3])) p.yaw = turn(p.yaw, s[3], dt * 6);
        p.sitting = s.length > 4 && s[4] > 0;
        p.walk = Math.max(0, p.walk - dt * 5);
        p.wait -= dt;
        if (p.wait <= 0) {
            p.routeI = (p.routeI + 1) % p.route.length;
            p.wait = p.route[p.routeI][2];
        }
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
        // Once you've drawn blood with a gun, they shoot back instead of grabbing you.
        if (armedThreat && d < 16 && clear(g.x, g.z, me.x, me.z)) {
            g.walk = Math.max(0, g.walk - dt * 5);
            g.yaw = turn(g.yaw, (float) Math.atan2(me.x - g.x, -(me.z - g.z)), dt * 8);
            g.gunCool -= dt;
            if (g.gunCool <= 0) {
                g.gunCool = 0.9f + rnd.nextFloat() * 0.6f;
                guardFires(g, d);
            }
            if (d > 7) {
                float sp = g.speed;
                g.speed = 1.6f;
                goTo(g, me.x, me.z, dt);
                g.speed = sp;
            }
            return;
        }
        float sp = g.speed;
        g.speed = 3.45f;
        goTo(g, me.x, me.z, dt);
        g.speed = sp;
    }

    // ================================================================== drinks

    private static final String[] BAR_LINES = {"Here you go.", "On the house. Kidding.",
            "Easy on that one.", "Fresh one."};
    private static final String[] COFFEE_LINES = {"Refill, hon.", "Careful, it's hot.",
            "There you go, sugar."};
    private static final String[] ANOTHER = {"Another round!", "Same again?", "I'm empty here.",
            "Barkeep!"};

    /** Who pours where and whom they serve (staff named in CasinoPlaces, plus the casino's). */
    private void setupServers() {
        for (Person p : people) {
            if (p.kind != STAFF || p.name == null) continue;
            if (p.name.equals("Bartender")) {
                serverFor(p, new float[]{37, 5.4f, SOUTH}, 26, 4, 48, 14, 2.3f, 0xFFE8A030,
                        BAR_LINES);
            } else if (p.name.equals("Waitress")) {
                serverFor(p, new float[]{-12.5f, 39.4f, NORTH}, -21, 36, -9.5f, 37.4f, 1.9f,
                        0xFF3A2414, COFFEE_LINES);
            } else if (p.name.equals("Cocktail waitress")) {
                serverFor(p, new float[]{-16.6f, 0.3f, WEST}, X0, Z0, X1, Z1, -0.85f, 0xFFFF6A8A,
                        BAR_LINES);
            }
        }
    }

    private void serverFor(Person p, float[] tap, float x0, float z0, float x1, float z1,
                           float reach, int color, String[] lines) {
        p.tap = tap;
        p.reach = reach;
        p.drinkColor = color;
        p.serveLines = lines;
        p.patrons = new ArrayList<>();
        for (Person q : people) {
            if (q.kind == SEATED && q.homeX > x0 && q.homeX < x1 && q.homeZ > z0
                    && q.homeZ < z1) {
                p.patrons.add(q);
                // Some already have a drink when you walk in.
                if (rnd.nextBoolean()) {
                    q.carry = color;
                    q.drinkT = 10 + rnd.nextFloat() * 40;
                }
            }
        }
        if (p.patrons.isEmpty()) p.patrons = null;
    }

    /** Pour, carry it over, hand it across, head back. */
    private void serve(Person p, float dt) {
        p.sitting = false;
        switch (p.serveState) {
            case 0: { // back at the taps: pour if someone needs one
                if (!walkTo(p, p.tap[0], p.tap[1], dt)) return;
                p.yaw = turn(p.yaw, p.tap[2], dt * 6);
                Person next = null;
                for (Person q : p.patrons) {
                    if (q.carry == 0 && q.sitting && q.down <= 0 && !q.ko && !q.dead) {
                        next = q;
                        break;
                    }
                }
                p.serveT += dt;
                if (next == null) {
                    p.serveT = 0;
                    return;
                }
                if (p.serveT > 2.0f) {
                    p.serveT = 0;
                    p.carry = p.drinkColor;
                    p.serving = next;
                    p.serveState = 1;
                }
                return;
            }
            case 1: { // carrying it over
                Person q = p.serving;
                if (q == null || !q.sitting || q.dead || q.ko || q.carry != 0) {
                    p.serveState = 3;
                    return;
                }
                float fx = (float) Math.sin(q.homeYaw), fz = (float) -Math.cos(q.homeYaw);
                float sx = q.homeX + fx * p.reach, sz = q.homeZ + fz * p.reach;
                if (p.reach < 0) {
                    // Beside the guest rather than squarely behind them.
                    sx += fz * 0.45f;
                    sz -= fx * 0.45f;
                }
                if (!walkTo(p, sx, sz, dt)) return;
                p.yaw = turn(p.yaw, (float) Math.atan2(q.x - p.x, -(q.z - p.z)), dt * 8);
                p.serveT += dt;
                if (p.serveT > 0.9f) {
                    p.serveT = 0;
                    q.carry = p.carry;
                    q.drinkT = (p.reach < 0 ? 70 : 30) + rnd.nextFloat() * 30;
                    q.sipT = 2;
                    p.carry = 0;
                    if (dist(p, player) < 10) say(p, p.serveLines);
                    p.serveState = 3;
                }
                return;
            }
            default: // heading back
                p.serving = null;
                if (walkTo(p, p.tap[0], p.tap[1], dt)) p.serveState = 0;
        }
    }

    /** Walks toward a point the smart way; true once there. */
    private boolean walkTo(Person p, float x, float z, float dt) {
        if (Math.hypot(x - p.x, z - p.z) < 0.25f) {
            p.walk = Math.max(0, p.walk - dt * 5);
            return true;
        }
        float sp = p.speed;
        p.speed = 1.25f;
        goTo(p, x, z, dt);
        p.speed = sp;
        return false;
    }

    /** A seated guest with a glass: a sip now and then, and a shout when it's empty. */
    private void drinking(Person p, float dt) {
        p.drinkT -= dt;
        if (p.sip > 0) p.sip -= dt;
        p.sipT -= dt;
        if (p.sipT <= 0) {
            p.sipT = 5 + rnd.nextFloat() * 7;
            p.sip = 1.4f;
        }
        if (p.drinkT <= 0) {
            p.carry = 0;
            p.sip = 0;
            if (dist(p, player) < 9 && rnd.nextFloat() < 0.6f) say(p, ANOTHER);
        }
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
        float sp = p.speed;
        p.speed = 1.2f;
        step(p, p.homeX, p.homeZ, dt, d > 1.2f);
        p.speed = sp;
    }

    private void step(Person p, float tx, float tz, float dt) {
        step(p, tx, tz, dt, true);
    }

    /** True when stepping into the road now would put someone in front of a car. */
    private boolean carComing(Person p, float tx, float tz) {
        if (CasinoCity.onRoad(p.x, p.z) || !CasinoCity.onRoad(tx, tz)) return false;
        for (CasinoTraffic.Car c : traffic.cars) {
            if (Math.abs(c.speed) < 2) continue;
            float fx = CasinoTraffic.fx(c.h) * Math.signum(c.speed);
            float fz = CasinoTraffic.fz(c.h) * Math.signum(c.speed);
            float dx = tx - c.x, dz = tz - c.z;
            float ahead = dx * fx + dz * fz;
            float side = Math.abs(dx * fz - dz * fx);
            if (ahead > -3 && ahead < 22 && side < 3.5f) return true;
        }
        return false;
    }

    private void step(Person p, float tx, float tz, float dt, boolean solid) {
        if (p != player && p.kind != GUARD && p.kind != COP && p.flee <= 0
                && carComing(p, tx, tz)) {
            p.walk = Math.max(0, p.walk - dt * 5);
            return;
        }
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

    /** Pushes someone out of walls, furniture and shut doors. */
    private void collide(Person p, float r) {
        for (int pass = 0; pass < 2; pass++) {
            for (float[] w : walls) {
                if (w.length < 6) push(p, r, w);
            }
            for (Door d : doors) {
                if (d.shut()) push(p, r, d.box());
            }
        }
        p.x = Math.max(WX0 + r, Math.min(WX1 - r, p.x));
        p.z = Math.max(WZ0 + r, Math.min(WZ1 - r, p.z));
    }

    private static void push(Person p, float r, float[] w) {
        if (p.x < w[0] - r || p.x > w[2] + r || p.z < w[1] - r || p.z > w[3] + r) return;
        float cx = Math.max(w[0], Math.min(p.x, w[2]));
        float cz = Math.max(w[1], Math.min(p.z, w[3]));
        float dx = p.x - cx, dz = p.z - cz;
        float d2 = dx * dx + dz * dz;
        if (d2 >= r * r) return;
        if (d2 < 1e-8f) {
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

    // ================================================================== doors

    private void updateDoors(float dt) {
        for (Door d : doors) {
            if (d.held > 0) d.held -= dt;
            // Employees and targets have keys: the door opens for them, briefly.
            if (!d.unlocked) {
                for (Person p : people) {
                    if (!p.active || p.ko || p.dead) continue;
                    if (p.kind != ROUTE && p.kind != GUARD) continue;
                    if (dist2(p.x, p.z, d.x, d.z) < 1.6f * 1.6f) d.held = 2.2f;
                }
            }
            if (!d.unlocked && canOpen(d)
                    && dist2(player.x, player.z, d.x, d.z) < 1.7f * 1.7f) {
                d.held = 1.5f;
            }
            float want = d.unlocked || d.held > 0 ? 1 : 0;
            d.open += Math.max(-dt * 2.5f, Math.min(dt * 2.5f, want - d.open));
        }
    }

    /** Your uniform lets you through this door. */
    boolean canOpen(Door d) {
        return d.access > 0 && allowed(player.outfit) >= d.access;
    }

    void unlockDoor(Door d) {
        d.unlocked = true;
        flags.add("door:" + d.id);
    }

    /** Applies saved flags: unlocked doors, found things. */
    void applyFlags() {
        for (Door d : doors) {
            if (flags.contains("door:" + d.id)) {
                d.unlocked = true;
                d.open = 1;
            }
        }
    }

    // ================================================================== suspicion

    /** Whether w can see the point x, z: in range, in front, nothing solid between. */
    boolean canSee(Person w, float x, float z, float range) {
        float dx = x - w.x, dz = z - w.z;
        float d = (float) Math.hypot(dx, dz);
        if (d > range || d < 0.01f) return d < 0.01f;
        float fx = (float) Math.sin(w.yaw), fz = (float) -Math.cos(w.yaw);
        if ((dx * fx + dz * fz) / d < 0.42f) return false;
        return clear(w.x, w.z, x, z);
    }

    /** Nothing tall between two points. */
    boolean clear(float ax, float az, float bx, float bz) {
        for (float[] w : walls) {
            if (w[4] < 1.7f || w.length > 5) continue;
            if (hits(ax, az, bx, bz, w)) return false;
        }
        for (Door d : doors) {
            if (d.shut() && hits(ax, az, bx, bz, d.box())) return false;
        }
        return true;
    }

    private static boolean hits(float ax, float az, float bx, float bz, float[] w) {
        float t0 = 0, t1 = 1;
        float dx = bx - ax, dz = bz - az;
        float[] p = {-dx, dx, -dz, dz};
        float[] q = {ax - w[0], w[2] - ax, az - w[1], w[3] - az};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1e-7f) {
                if (q[i] < 0) return false;
            } else {
                float t = q[i] / p[i];
                if (p[i] < 0) t0 = Math.max(t0, t);
                else t1 = Math.min(t1, t);
                if (t0 > t1) return false;
            }
        }
        return true;
    }

    /** Who's looking at you right now (for the lock screen warning). */
    Person watcher;

    /** How restricted a place is: 0 public, 1 staff, 2 private, 3 nobody. */
    int zoneAt(float x, float z) {
        int lvl = 0;
        for (float[] zn : zones) {
            if (x >= zn[0] && x <= zn[2] && z >= zn[1] && z <= zn[3]) lvl = Math.max(lvl, (int) zn[4]);
        }
        return lvl;
    }

    static int allowed(int outfit) {
        return outfit == SECURITY ? 2 : (outfit == CIVILIAN ? 0 : 1);
    }

    boolean trespassing() {
        return zoneAt(player.x, player.z) > allowed(player.outfit);
    }

    boolean awake(Person p) {
        return p.active && !p.ko && !p.dead && p.down <= 0 && p.kind != PLAYER;
    }

    private void updateSuspicion(float dt) {
        if (escort > 0) return;
        Person me = player;
        boolean trespass = trespassing();
        float rate = 0;
        Person who = null;
        for (Person w : people) {
            if (!awake(w) || w == dragging) continue;
            if (!canSee(w, me.x, me.z, 9)) continue;
            float r = 0;
            if (trespass) r += w.kind == GUARD ? 0.4f : 0.18f;
            if (dragging != null) r += 1.6f;
            if (picking) {
                float dd = dist(w, me);
                if (w.kind == GUARD) r += dd < 6 ? 0.45f : 0.2f;
                else if (w.kind == STAFF || w.kind == ROUTE) r += dd < 6 ? 0.22f : 0;
                else r += dd < 3.5f ? 0.12f : 0;
            }
            if (me.outfit == SECURITY && w.kind == GUARD && dist(w, me) < 3.5f) r += 0.35f;
            if (weapon != null && !inRange(me.x, me.z) && !weapon.melee) {
                float dd = dist(w, me);
                if (aiming) {
                    r += w.kind == GUARD ? 1.2f : (dd < 10 ? 0.6f : 0);
                    if (w.kind != GUARD && dd < 10 && w.flee <= 0 && rnd.nextFloat() < 0.02f) {
                        w.flee = 4;
                        say(w, PANIC);
                    }
                } else if (!weapon.concealable && me.outfit != SECURITY) {
                    r += w.kind == GUARD ? 0.5f : (dd < 6 ? 0.15f : 0);
                }
            }
            if (r > rate) {
                rate = r;
                who = w;
            }
        }
        if (softShoes()) rate *= 0.7f;
        watched = rate > 0;
        watcher = who;
        // Bodies lying in plain sight.
        for (Person b : people) {
            if (!b.active || !(b.ko || b.dead) || b.found || b == dragging) continue;
            for (Person w : people) {
                if (w == b || !awake(w) || w.target) continue;
                if (!canSee(w, b.x, b.z, 9)) continue;
                b.found = true;
                bodyFound = true;
                say(w, BODY_FOUND);
                if (w.kind == WANDER || w.kind == SEATED) w.flee = 4;
                raiseAlarm("Someone found a body.");
                break;
            }
        }
        if (rate > 0) {
            suspicion += rate * dt;
            if (!warned && suspicion > 0.3f && who != null) {
                warned = true;
                say(who, dragging != null || picking ? ODD : TRESPASS);
            }
        } else {
            suspicion = Math.max(0, suspicion - 0.35f * dt);
            if (suspicion < 0.1f) warned = false;
        }
        if (suspicion >= 1 && !alarm) raiseAlarm("You've been spotted.");
    }

    /** Anyone who sees the player right now reacts to violence. Returns true if seen. */
    private boolean witnessed(float amount, boolean scary) {
        return witnessed(amount, scary, null);
    }

    private boolean witnessed(float amount, boolean scary, Person victim) {
        boolean seen = false;
        for (Person w : people) {
            if (!awake(w) || w == dragging || w == victim) continue;
            if (!canSee(w, player.x, player.z, 12)) continue;
            seen = true;
            if (scary && (w.kind == WANDER || w.kind == SEATED || w.kind == STAFF)) {
                w.flee = 4 + rnd.nextFloat() * 2;
                say(w, PANIC);
            }
        }
        if (seen) {
            suspicion += amount;
            if (suspicion >= 1) raiseAlarm("You've been spotted.");
        }
        return seen;
    }

    private void raiseAlarm(String why) {
        if (contract != null) spotted = true;
        suspicion = 1;
        alarmT = 35;
        if (!alarm) {
            alarm = true;
            toast = why + " Security's coming.";
            for (Person g : guards) {
                if (awake(g)) say(g, ALARM_GUARD);
            }
        }
    }

    // ================================================================== punching & takedowns

    void punch() {
        if (escort > 0 || dragging != null) return;
        fight.punchPressed();
    }

    void stepPerson(Person p, float tx, float tz, float dt) {
        goTo(p, tx, tz, dt);
    }

    void raiseAlarmPublic(String why) {
        if (nearCasino(player)) raiseAlarm(why);
    }

    /** You knocked your contract target out cold: they can be finished where they lie. */
    void knockedOutTarget(Person t) {
        toast = t.name + " is out cold. Finish the job.";
    }

    private Person target(float reach, float cone) {
        Person best = null;
        float bestD = reach;
        float fx = (float) Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        for (Person p : people) {
            if (!p.active || p.down > 0 || p.ko || p.dead) continue;
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
        if (p.kind == COP) traffic.addHeat(0.6f, "Assaulted an officer");
        else if (traffic.copSees()) traffic.report(0.5f, "Assault", true);
        sounds |= 1;
        p.hitAt = time;
        witnessed(0.45f, false);
        if (p.kind == GUARD) {
            say(p, new String[]{GUARD_LINES[2]});
            player.shake = 0.25f;
            raiseAlarm("You hit a guard.");
            return;
        }
        float fx = (float) Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        p.down = DOWN_TIME;
        p.kx = fx * 4.2f;
        p.kz = fz * 4.2f;
        p.yaw = (float) Math.atan2(-fx, fz);
        p.sitting = false;
        p.idle = 0;
        p.swung = false;
        p.bubble = null;
        p.bleed = 12;
        spray(p.x + fx * 0.1f, 1.62f * p.scale, p.z + fz * 0.1f, fx, fz, 16);
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

    /** Someone you can take down quietly: close, from behind, not on alert. */
    Person takedownTarget() {
        if (dragging != null || escort > 0) return null;
        Person best = null;
        float bestD = 1.35f;
        for (Person p : people) {
            if (!awake(p) || p.flee > 0) continue;
            if (p.kind == GUARD && alarm) continue;
            float dx = player.x - p.x, dz = player.z - p.z;
            float d = (float) Math.hypot(dx, dz);
            if (d > bestD || d < 1e-3f) continue;
            float fx = (float) Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
            // You have to be behind them.
            if ((dx * fx + dz * fz) / d > -0.25f) continue;
            best = p;
            bestD = d;
        }
        return best;
    }

    /** Takes someone down from behind: out cold, or for a contract target, for good. */
    void takedown(Person p) {
        player.yaw = (float) Math.atan2(p.x - player.x, -(p.z - player.z));
        player.punch = PUNCH_TIME;
        sounds |= 1;
        p.kx = (float) Math.sin(player.yaw) * 1.2f;
        p.kz = (float) -Math.cos(player.yaw) * 1.2f;
        p.bubble = null;
        boolean seen = witnessed(0.7f, true, p);
        p.yaw = player.yaw + PI;
        if (p.target) {
            kill(p, KILL_TAKEDOWN);
            if (seen) targetWitnessed = true;
        } else {
            p.ko = true;
            p.fallRaw = 0;
        }
    }

    // ================================================================== guns

    /** What's in your hands: null for fists. */
    CasinoWeapons.Gun weapon;
    boolean aiming, silencer, triggerHeld, downed;
    /** Looking up (-) or down (+) while aiming. */
    float aimPitch = 0.05f;
    final java.util.Map<String, Integer> ammo = new java.util.HashMap<>();
    float reloadT, fireCool, bloom, recoil;
    /** Your health, and seconds since you were last hit. */
    float health = 100, hurtT = 99;
    /** Guards shoot back once you've fired on people. */
    boolean armedThreat;
    /** Last hit marker: time, 1 hit, 2 kill, 3 headshot kill. */
    float markT = -9;
    int markKind;
    /** Bullet streaks: x0, y0, z0, x1, y1, z1, life. Sparks: x, y, z, vx, vy, vz, life. */
    final List<float[]> tracers = new ArrayList<>();
    final List<float[]> sparks = new ArrayList<>();
    private final float[] eye = new float[3], fwd = new float[3];

    /** Who the crosshair is locked onto, and seconds you're aiming by hand. */
    Person lock;
    float manualAim;

    private boolean hostile(Person p) {
        return p.kind == COP || p.target || (p.kind == GUARD && alarm) || p.angry > 0;
    }

    private boolean lockable(Person p, float range) {
        if (!p.active || p.dead || p.hidden || p.ko || p.fall > 0.5f || p == player) return false;
        float d = dist(p, player);
        return d > 0.6f && d < range && clear(player.x, player.z, p.x, p.z);
    }

    /** The best person near the crosshair: closest to the centre, enemies first. */
    Person findLock(float cone) {
        float range = weapon != null ? Math.min(weapon.range, 45) : 30;
        Person best = null;
        float bestScore = 1e9f;
        for (Person p : people) {
            if (!lockable(p, range)) continue;
            float ang = Math.abs(CasinoTraffic.angle(
                    (float) Math.atan2(p.x - player.x, -(p.z - player.z)) - camYaw));
            if (ang > cone) continue;
            float score = ang * 8 + dist(p, player) * 0.04f - (hostile(p) ? 1.2f : 0);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** Turns the aim toward the locked person's chest; k 1 snaps straight on. */
    private void trackLock(float k) {
        Person t = lock;
        float ty = (t.sitting ? 1.05f : 1.25f) * t.scale;
        for (int i = 0; i < 2; i++) {
            aimRay();
            aimEye();
            float dx = t.x - eye[0], dz = t.z - eye[2];
            float yaw = (float) Math.atan2(dx, -dz);
            float pitch = (float) Math.atan2(eye[1] - ty, Math.hypot(dx, dz));
            camYaw = turn(camYaw, yaw, k);
            aimPitch += (pitch - aimPitch) * Math.min(1, k);
            if (k < 1) break;
        }
    }

    void equip(CasinoWeapons.Gun g) {
        weapon = g;
        reloadT = 0;
        if (g == null) aiming = false;
        else if (!ammo.containsKey(g.id)) ammo.put(g.id, g.mag);
    }

    int ammoLeft() {
        return weapon == null ? 0 : ammo.containsKey(weapon.id) ? ammo.get(weapon.id) : 0;
    }

    void reload() {
        if (weapon == null || weapon.melee || reloadT > 0 || ammoLeft() == weapon.mag) return;
        reloadT = weapon.reload;
    }

    boolean inRange(float x, float z) {
        return x > 34 && x < 48 && z > 30 && z < 42;
    }

    /** Where the aim ray starts and points, from the last camera. */
    private void aimRay() {
        float cp = (float) Math.cos(aimPitch), sp = (float) Math.sin(aimPitch);
        fwd[0] = (float) Math.sin(camYaw) * cp;
        fwd[1] = -sp;
        fwd[2] = (float) -Math.cos(camYaw) * cp;
    }

    private void updateCombat(float dt) {
        if (fireCool > 0) fireCool -= dt;
        bloom = Math.max(0, bloom - dt * 2.5f);
        if (recoil > 0) {
            float r = Math.min(recoil, dt * 6 * Math.max(0.05f, recoil));
            recoil -= r;
        }
        if (reloadT > 0) {
            reloadT -= dt;
            if (reloadT <= 0 && weapon != null) {
                ammo.put(weapon.id, weapon.mag);
                sounds |= 64;
            }
        }
        // Aim assist: stay locked on whoever's under the crosshair unless you're steering.
        if (aiming && weapon != null && !weapon.melee && traffic.driving == null) {
            if (manualAim > 0) {
                manualAim -= dt;
            } else {
                float range = Math.min(weapon.range, 45) + 5;
                if (lock == null || !lockable(lock, range)) lock = findLock(0.5f);
                if (lock != null) trackLock(dt * 9);
            }
        } else {
            lock = null;
        }
        if (weapon != null && weapon.auto && triggerHeld && aiming) fire();
        // Health comes back if nobody's hit you for a few seconds.
        hurtT += dt;
        if (hurtT > 5 && health < 100) health = Math.min(100, health + 9 * dt);
        if (!alarm) armedThreat = false;
        for (int i = tracers.size() - 1; i >= 0; i--) {
            float[] t = tracers.get(i);
            t[6] -= dt;
            if (t[6] <= 0) tracers.remove(i);
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            float[] p = sparks.get(i);
            p[4] -= 9.8f * dt;
            p[0] += p[3] * dt;
            p[1] += p[4] * dt;
            p[2] += p[5] * dt;
            p[6] -= dt;
            if (p[6] <= 0 || p[1] < 0) sparks.remove(i);
        }
    }

    /** Pulls the trigger (or swings the knife). */
    void fire() {
        if (weapon == null || escort > 0 || dragging != null || picking) return;
        if (fireCool > 0 || reloadT > 0) return;
        if (weapon.melee) {
            stab();
            return;
        }
        if (ammoLeft() <= 0) {
            reload();
            return;
        }
        if (!aiming) {
            aiming = true;
            aimPitch = 0.05f;
            lock = findLock(0.75f);
        }
        if (lock != null && manualAim <= 0 && lockable(lock, weapon.range + 5)) trackLock(1);
        fireCool = weapon.delay;
        lastShotDraw = time;
        ammo.put(weapon.id, ammoLeft() - 1);
        player.yaw = camYaw;
        aimRay();
        aimEye();
        float ox = eye[0], oy = eye[1], oz = eye[2];
        float rx = (float) Math.cos(camYaw), rz = (float) Math.sin(camYaw);
        float mx = player.x + rx * 0.33f + fwd[0] * (0.55f + weapon.length),
                my = 1.38f - aimPitch * 0.3f, mz = player.z + rz * 0.33f + fwd[2] * (0.55f + weapon.length);
        boolean moving = player.walk > 0.3f;
        float spr = weapon.spread * (moving ? 1.7f : 1) + bloom * 0.02f;
        boolean anyKill = false, anyHit = false, head = false;
        Person victim = null;
        for (int k = 0; k < weapon.pellets; k++) {
            float dx = fwd[0] + (rnd.nextFloat() - 0.5f) * 2 * spr;
            float dy = fwd[1] + (rnd.nextFloat() - 0.5f) * 2 * spr;
            float dz = fwd[2] + (rnd.nextFloat() - 0.5f) * 2 * spr;
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l;
            dy /= l;
            dz /= l;
            float tw = rayWorld(ox, oy, oz, dx, dy, dz, weapon.range);
            Person hitP = null;
            boolean hitHead = false;
            float tp = tw;
            for (Person p : people) {
                if (!p.active || p.hidden) continue;
                float t = rayPerson(p, ox, oy, oz, dx, dy, dz, headOut);
                if (t > 0 && t < tp) {
                    tp = t;
                    hitP = p;
                    hitHead = headOut[0];
                }
            }
            int tgt = rangeHit(ox, oy, oz, dx, dy, dz, Math.min(tp, tw));
            float hx = ox + dx * tp, hy = oy + dy * tp, hz = oz + dz * tp;
            if (tgt >= 0) {
                float[] rt = rangeTargets.get(tgt);
                tp = rt[6];
                hx = ox + dx * tp;
                hy = oy + dy * tp;
                hz = oz + dz * tp;
                hitP = null;
            }
            if (k < 3) tracers.add(new float[]{mx, my, mz, hx, hy, hz, 0.07f});
            if (hitP != null) {
                anyHit = true;
                float dmg = weapon.damage * (hitHead ? weapon.headMult : 1);
                if (weapon.pellets > 1) dmg *= Math.max(0.3f, 1 - tp / weapon.range);
                boolean died = damage(hitP, dmg, dx, dz, hitHead);
                if (died) {
                    anyKill = true;
                    head |= hitHead;
                }
                victim = hitP;
            } else if (tgt < 0 && tw < weapon.range) {
                for (int i = 0; i < 4; i++) {
                    sparks.add(new float[]{hx - dx * 0.05f, hy, hz - dz * 0.05f,
                            -dx * 1.5f + (rnd.nextFloat() - 0.5f) * 2, rnd.nextFloat() * 2,
                            -dz * 1.5f + (rnd.nextFloat() - 0.5f) * 2, 0.35f});
                }
            }
        }
        if (anyHit) {
            markT = time;
            markKind = anyKill ? (head ? 3 : 2) : 1;
        }
        recoil += weapon.pellets > 1 ? 0.06f : (weapon.auto ? 0.012f : 0.035f);
        aimPitch -= weapon.pellets > 1 ? 0.05f : (weapon.auto ? 0.01f : 0.03f);
        aimPitch = Math.max(-0.7f, aimPitch);
        bloom = Math.min(2, bloom + (weapon.auto ? 0.25f : 0.6f));
        player.shake = Math.max(player.shake, weapon.pellets > 1 ? 0.12f : 0.05f);
        float loud = silenced() ? 0.12f : weapon.loud;
        sounds |= silenced() ? 8 : 4;
        if (!inRange(player.x, player.z)) {
            heard(loud, victim);
            if (anyHit) armedThreat = true;
        }
    }

    boolean silenced() {
        return silencer && weapon == CasinoWeapons.PISTOL;
    }

    private final boolean[] headOut = new boolean[1];
    private float lastShotDraw = -9;

    /** The knife: a quiet kill on whoever's right in front of you. */
    private void stab() {
        fireCool = weapon.delay;
        player.punch = PUNCH_TIME;
        Person p = target(1.5f, 0.8f);
        if (p == null) {
            sounds |= 2;
            return;
        }
        sounds |= 1;
        float fx = (float) Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        boolean seen = witnessed(0.8f, true, p);
        if (p.target && seen) targetWitnessed = true;
        damage(p, weapon.damage, fx, fz, false);
        markT = time;
        markKind = 2;
    }

    /** Hurts someone. Returns true if that killed them. */
    private boolean damage(Person p, float dmg, float dx, float dz, boolean head) {
        if (p.dead) return false;
        p.hitAt = time;
        p.health -= dmg;
        spray(p.x, (head ? 1.7f : 1.25f) * p.scale, p.z, dx, dz, head ? 18 : 10);
        p.bleed = 20;
        if (p.health <= 0) {
            p.kx = dx * 2.5f;
            p.kz = dz * 2.5f;
            p.yaw = (float) Math.atan2(-dx, dz);
            if (p.kind == GUARD && !p.target) raiseAlarm("You shot a guard.");
            kill(p, KILL_SHOT);
            return true;
        }
        // Still standing: stagger, and either run or come for you.
        p.kx = dx * 1.2f;
        p.kz = dz * 1.2f;
        p.ko = false;
        if (p.kind == COP) traffic.addHeat(0.6f, "Shot a cop");
        if (p.kind == GUARD) raiseAlarm("A guard took a bullet.");
        else {
            p.flee = 6;
            say(p, PANIC);
        }
        return false;
    }

    /** Everyone within earshot reacts to a gunshot. */
    private void heard(float loud, Person victim) {
        float r = loud * 28;
        boolean guardKnows = false;
        for (Person w : people) {
            if (!awake(w) || w == victim) continue;
            float d = dist(w, player);
            if (d > r) continue;
            boolean sees = canSee(w, player.x, player.z, 16);
            if (w.kind == GUARD) {
                if (sees || d < r * 0.5f) guardKnows = true;
            } else if (w.kind == COP) {
                continue;
            } else if (w.kind != ROUTE || !w.target) {
                if (sees || rnd.nextFloat() < 0.6f) {
                    w.flee = 5 + rnd.nextFloat() * 3;
                    if (rnd.nextFloat() < 0.5f) say(w, PANIC);
                }
                if (sees) suspicion += 0.5f;
            }
            if (w.target && sees) targetWitnessed = true;
        }
        lastLoud = loud > 0.3f ? time : lastLoud;
        if (loud > 0.3f) {
            boolean copNear = false;
            for (Person cp : traffic.cops) {
                if (cp.active && !cp.dead && dist(cp, player) < 35) copNear = true;
            }
            for (CasinoTraffic.Car pc : traffic.cars) {
                if (pc.role == CasinoTraffic.COP && !pc.wrecked
                        && dist2(pc.x, pc.z, player.x, player.z) < 35 * 35) {
                    copNear = true;
                }
            }
            boolean earshot = false;
            for (Person h : people) {
                if (awake(h) && h.kind != GUARD && dist(h, player) < 22) earshot = true;
            }
            if (copNear || earshot) traffic.shotsFired();
        }
        if (guardKnows && nearCasino(player)) raiseAlarm("Shots fired.");
        else if (loud > 0.3f) suspicion = Math.min(0.95f, suspicion + 0.3f);
        if (suspicion >= 1 && !alarm) raiseAlarm("Someone saw you shoot.");
        if (contract != null && (guardKnows || suspicion > 0.6f)) spotted = true;
    }

    private void guardFires(Person g, float d) {
        sounds |= 16;
        float chance = 0.62f - d * 0.018f - (player.walk > 0.3f ? 0.15f : 0)
                - (aiming ? 0 : 0.05f);
        boolean hit = rnd.nextFloat() < Math.max(0.12f, chance);
        float fx = (float) Math.sin(g.yaw), fz = (float) -Math.cos(g.yaw);
        float sx = g.x + fx * 0.6f, sz = g.z + fz * 0.6f;
        float tx = player.x + (hit ? 0 : (rnd.nextFloat() - 0.5f) * 1.6f);
        float tz = player.z + (hit ? 0 : (rnd.nextFloat() - 0.5f) * 1.6f);
        tracers.add(new float[]{sx, 1.4f, sz, tx, hit ? 1.3f : 0.8f + rnd.nextFloat(), tz,
                0.07f});
        g.punch = PUNCH_TIME * 0.5f;
        if (!hit) return;
        player.bleed = 6;
        spray(player.x, 1.3f, player.z, fx, fz, 8);
        hurtPlayer(16 + rnd.nextFloat() * 8);
    }

    void hurtPlayer(float amount) {
        if (escort > 0) return;
        health -= amount;
        hurtT = 0;
        player.shake = Math.max(player.shake, 0.3f);
        if (health <= 0) {
            health = 0;
            downed = true;
            dragging = null;
            escort = 1.6f;
            aiming = false;
        }
    }

    // ---------------------------------------------------------------- police on foot

    Person spawnCop(float x, float z) {
        Person p = newPerson(COP);
        p.x = x;
        p.z = z;
        p.shirt = 0xFF1E2A4A;
        p.pants = 0xFF141A2A;
        p.hatType = CasinoShop.CAP;
        p.hatColor = 0xFF141A2A;
        p.health = 120;
        p.speed = 2.9f;
        p.tough = true;
        p.name = "Police";
        collide(p, 0.3f);
        return p;
    }

    Person spawnPedestrian(float x, float z) {
        Person p = newPerson(WANDER);
        p.x = x;
        p.z = z;
        p.area = new float[]{x - 12, z - 12, x + 12, z + 12};
        collide(p, 0.3f);
        pickTarget(p);
        return p;
    }

    boolean isFree(float x, float z) {
        return x > WX0 + 1 && x < WX1 - 1 && z > WZ0 + 1 && z < WZ1 - 1 && free(x, z, 0.4f);
    }

    boolean nearCasino(Person me) {
        return me.x > -20 && me.x < 20 && me.z > -26 && me.z < 16;
    }

    private void copThink(Person p, float dt) {
        int st = traffic.stars();
        Person me = player;
        float d = dist(p, me);
        if (st == 0) {
            p.walk = Math.max(0, p.walk - dt * 4);
            return;
        }
        boolean lethal = st >= 2 || aiming;
        boolean los = d < 22 && clear(p.x, p.z, me.x, me.z);
        if (!los && traffic.unseen > 1.5f) {
            if (dist2(p.x, p.z, traffic.lastX, traffic.lastZ) > 2) {
                step(p, traffic.lastX, traffic.lastZ, dt);
            } else {
                p.walk = Math.max(0, p.walk - dt * 4);
                p.yaw += dt * 0.8f;
            }
            return;
        }
        if (lethal && los && (d < 14 || traffic.driving != null)) {
            p.walk = Math.max(0, p.walk - dt * 5);
            p.yaw = turn(p.yaw, (float) Math.atan2(me.x - p.x, -(me.z - p.z)), dt * 8);
            p.gunCool -= dt;
            if (p.gunCool <= 0) {
                p.gunCool = 0.8f + rnd.nextFloat() * 0.7f;
                copFires(p, d);
            }
            if (d > 9) {
                float sp = p.speed;
                p.speed = 1.8f;
                goTo(p, me.x, me.z, dt);
                p.speed = sp;
            }
            return;
        }
        if (!lethal && d < 1.0f && traffic.driving == null && escort <= 0) {
            traffic.busted = true;
            escort = 1.6f;
            say(p, new String[]{"You're under arrest.", "Hands behind your back.",
                    "Don't make this worse."});
            return;
        }
        if (!lethal && d < 6 && rnd.nextFloat() < dt * 0.4f) {
            say(p, new String[]{"Police! Stop right there!", "On the ground!", "Freeze!"});
        }
        goTo(p, me.x, me.z, dt);
    }

    private void copFires(Person p, float d) {
        sounds |= 16;
        float chance = 0.55f - d * 0.02f - (player.walk > 0.3f ? 0.15f : 0);
        boolean hit = rnd.nextFloat() < Math.max(0.1f, chance);
        float fx = (float) Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        float tx = player.x + (hit ? 0 : (rnd.nextFloat() - 0.5f) * 1.8f);
        float tz = player.z + (hit ? 0 : (rnd.nextFloat() - 0.5f) * 1.8f);
        tracers.add(new float[]{p.x + fx * 0.6f, 1.4f, p.z + fz * 0.6f, tx,
                hit ? 1.2f : 0.7f + rnd.nextFloat(), tz, 0.07f});
        p.punch = PUNCH_TIME * 0.5f;
        if (!hit) return;
        if (traffic.driving != null) {
            traffic.damage(traffic.driving, 3, false);
            if (rnd.nextFloat() < 0.35f) hurtPlayer(10);
            return;
        }
        player.bleed = 6;
        spray(player.x, 1.3f, player.z, fx, fz, 8);
        hurtPlayer(14 + rnd.nextFloat() * 8);
    }

    // ---------------------------------------------------------------- crimes

    /** Anyone awake who can see you right now. */
    boolean anyoneSees() {
        for (Person w : people) {
            if (awake(w) && canSee(w, player.x, player.z, 20)) return true;
        }
        return false;
    }

    /** A crime: the police only care if someone saw it or heard the shot. */
    void crimeSeen(float amount, String why) {
        boolean seen = anyoneSees() || time - lastLoud < 1.0f;
        if (!seen) return;
        traffic.report(amount, why, traffic.copSees());
    }

    /** A kill by you (or a car you were pushed into, etc). */
    void killBy(Person p, int how) {
        kill(p, how);
    }

    // ---------------------------------------------------------------- fire and smoke

    void smoke(float x, float y, float z, boolean fire) {
        if (smokes.size() > 160) return;
        smokes.add(new float[]{x + (rnd.nextFloat() - 0.5f) * 0.6f, y,
                z + (rnd.nextFloat() - 0.5f) * 0.6f, fire ? 2.2f : 1.0f,
                fire ? 0.5f : 1.6f, fire ? 0.25f : 0.3f, fire ? 1 : 0});
    }

    private void updateSmoke(float dt) {
        for (int i = smokes.size() - 1; i >= 0; i--) {
            float[] p = smokes.get(i);
            p[1] += p[3] * dt;
            p[4] -= dt;
            p[5] += dt * (p[6] > 0 ? 0.3f : 0.5f);
            if (p[4] <= 0) smokes.remove(i);
        }
    }

    /** A car blowing up: hurts everyone close, sets off a fireball. */
    void blast(float x, float z, boolean byPlayer) {
        for (int i = 0; i < 40; i++) {
            sparks.add(new float[]{x, 1.0f, z, (rnd.nextFloat() - 0.5f) * 14,
                    2 + rnd.nextFloat() * 7, (rnd.nextFloat() - 0.5f) * 14, 0.9f});
        }
        for (int i = 0; i < 24; i++) smoke(x, 0.6f + rnd.nextFloat(), z, i % 2 == 0);
        float dp = (float) Math.hypot(player.x - x, player.z - z);
        if (dp < 6) {
            player.shake = 0.8f;
            hurtPlayer((6 - dp) * 22);
        } else if (dp < 25) {
            player.shake = Math.max(player.shake, 0.3f);
        }
        for (Person p : people) {
            if (!p.active || p.dead) continue;
            float d = (float) Math.hypot(p.x - x, p.z - z);
            if (d > 5) continue;
            float k = (5 - d) * 2;
            p.kx = (p.x - x) / Math.max(0.3f, d) * k;
            p.kz = (p.z - z) / Math.max(0.3f, d) * k;
            if (byPlayer) killBy(p, KILL_BLAST);
            else kill(p, KILL_CAR);
        }
        if (byPlayer) crimeSeen(1, "Blew up a car");
    }

    /** Puts you back on your feet next to the car. */
    void exitCar(boolean hurried) {
        CasinoTraffic.Car c = traffic.driving;
        if (c == null) return;
        float[] at = traffic.exitSpot(c);
        traffic.driving = null;
        player.x = at[0];
        player.z = at[1];
        player.yaw = c.h;
        c.driver = false;
        c.role = c.type == CasinoTraffic.POLICE ? CasinoTraffic.PARKED : CasinoTraffic.PARKED;
    }

    // ---------------------------------------------------------------- the practice range

    /** Pop-up targets: x, z, up (0 flat..1 standing), state (0 down, 1 up, 2 hit),
     * timer, row, last hit distance. */
    final List<float[]> rangeTargets = new ArrayList<>();
    boolean rangeOn, rangeDone;
    float rangeTime, rangeSpawn;
    int rangeScore, rangeHits, rangeHeads, rangeShots;
    static final float ROUND = 40;

    void setupRange() {
        rangeTargets.clear();
        float[] rows = {35.0f, 38.0f, 41.0f};
        for (int r = 0; r < 3; r++) {
            for (int i = 0; i < 4; i++) {
                rangeTargets.add(new float[]{38.6f + i * 2.4f + (r % 2) * 0.6f, rows[r], 0, 0, 0, r,
                        0});
            }
        }
    }

    void startRound() {
        rangeOn = true;
        rangeDone = false;
        rangeTime = ROUND;
        rangeSpawn = 1;
        rangeScore = rangeHits = rangeHeads = 0;
        for (float[] t : rangeTargets) {
            t[2] = 0;
            t[3] = 0;
        }
    }

    private void updateRange(float dt) {
        if (rangeTargets.isEmpty()) setupRange();
        for (float[] t : rangeTargets) {
            float want = t[3] == 1 ? 1 : 0;
            t[2] += Math.max(-dt * 4, Math.min(dt * 5, want - t[2]));
            if (t[3] == 1) {
                t[4] -= dt;
                if (t[4] <= 0) t[3] = 0;
            } else if (t[3] == 2 && t[2] <= 0.01f) {
                t[3] = 0;
            }
        }
        if (!rangeOn) return;
        rangeTime -= dt;
        rangeSpawn -= dt;
        if (rangeSpawn <= 0) {
            rangeSpawn = 0.55f + rnd.nextFloat() * 0.5f;
            int tries = 0;
            while (tries++ < 10) {
                float[] t = rangeTargets.get(rnd.nextInt(rangeTargets.size()));
                if (t[3] != 0 || t[2] > 0.05f) continue;
                t[3] = 1;
                t[4] = 2.4f - t[5] * 0.25f;
                break;
            }
        }
        if (rangeTime <= 0) {
            rangeOn = false;
            rangeDone = true;
            for (float[] t : rangeTargets) t[3] = 0;
        }
    }

    /** Which standing target a ray hits before maxT, or -1. Scores it. */
    private int rangeHit(float ox, float oy, float oz, float dx, float dy, float dz,
                         float maxT) {
        int best = -1;
        float bestT = maxT;
        boolean head = false;
        for (int i = 0; i < rangeTargets.size(); i++) {
            float[] t = rangeTargets.get(i);
            if (t[3] != 1 || t[2] < 0.7f) continue;
            float tb = rayBox(ox, oy, oz, dx, dy, dz, t[0] - 0.28f, 0.55f, t[1] - 0.04f,
                    t[0] + 0.28f, 1.45f, t[1] + 0.04f);
            float th = rayBox(ox, oy, oz, dx, dy, dz, t[0] - 0.14f, 1.5f, t[1] - 0.04f,
                    t[0] + 0.14f, 1.8f, t[1] + 0.04f);
            if (th >= 0 && th < bestT) {
                bestT = th;
                best = i;
                head = true;
            }
            if (tb >= 0 && tb < bestT) {
                bestT = tb;
                best = i;
                head = false;
            }
        }
        if (best < 0) return -1;
        float[] t = rangeTargets.get(best);
        t[3] = 2;
        t[6] = bestT;
        sounds |= 32;
        if (rangeOn) {
            rangeHits++;
            if (head) rangeHeads++;
            rangeScore += (head ? 25 : 10) + (int) t[5] * 5;
        }
        markT = time;
        markKind = head ? 3 : 1;
        return best;
    }

    private void drawRange(PartSink s) {
        for (float[] t : rangeTargets) {
            if (far(t[0], t[1])) continue;
            box(s, t[0], 0.3f * t[2], t[1] + 0.06f, 0.08f, 0.6f * t[2] + 0.02f, 0.08f, 0xFF2A2420, 0);
            Mat.identity(tmp);
            Mat.translate(tmp, t[0], 0.06f + 0.49f * t[2], t[1]);
            Mat.rotX(tmp, -(1 - t[2]) * 1.5f);
            float[] base = tmp.clone();
            rpart(s, base, 0, 0.45f, 0, 0.56f, 0.9f, 0.05f, 0xFFC8A070, 0.1f);
            rpart(s, base, 0, 1.1f, 0, 0.28f, 0.3f, 0.05f, 0xFFC8A070, 0.1f);
            rpart(s, base, 0, 0.6f, -0.03f, 0.26f, 0.26f, 0.01f, 0xFFF2F2F2, 0.3f);
            rpart(s, base, 0, 0.6f, -0.035f, 0.12f, 0.12f, 0.01f, 0xFFE02020, 0.6f);
        }
    }

    private void rpart(PartSink s, float[] at, float x, float y, float z, float w, float h,
                       float d, int c, float glow) {
        copy(tmp, at);
        Mat.translate(tmp, x, y, z);
        Mat.scale(tmp, w, h, d);
        s.part(tmp, c, glow);
    }

    // ---------------------------------------------------------------- rays

    /** Distance along a ray to the first wall, door or the ground (or max). */
    float rayWorld(float ox, float oy, float oz, float dx, float dy, float dz, float max) {
        float best = max;
        if (dy < -1e-5f) best = Math.min(best, -oy / dy);
        for (float[] w : walls) {
            float y0 = w.length > 5 ? w[5] : 0;
            float t = rayBox(ox, oy, oz, dx, dy, dz, w[0], y0, w[1], w[2], w[4], w[3]);
            if (t >= 0 && t < best) best = t;
        }
        for (Door d : doors) {
            if (!d.shut()) continue;
            float[] b = d.box();
            float t = rayBox(ox, oy, oz, dx, dy, dz, b[0], 0, b[1], b[2], 2.5f, b[3]);
            if (t >= 0 && t < best) best = t;
        }
        return best;
    }

    static float rayBox(float ox, float oy, float oz, float dx, float dy, float dz, float x0,
                        float y0, float z0, float x1, float y1, float z1) {
        float tmin = 0, tmax = 1e9f;
        float[] o = {ox, oy, oz}, dd = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dd[i]) < 1e-7f) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1;
            } else {
                float t1 = (lo[i] - o[i]) / dd[i], t2 = (hi[i] - o[i]) / dd[i];
                if (t1 > t2) {
                    float t = t1;
                    t1 = t2;
                    t2 = t;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1;
            }
        }
        return tmin;
    }

    /** Distance along a ray to a person's body or head, or -1. head[0] says which. */
    float rayPerson(Person p, float ox, float oy, float oz, float dx, float dy, float dz,
                    boolean[] head) {
        head[0] = false;
        float sc = p.scale;
        if (p.fall > 0.5f) {
            float t = rayBox(ox, oy, oz, dx, dy, dz, p.x - 0.7f, 0, p.z - 0.7f, p.x + 0.7f,
                    0.35f, p.z + 0.7f);
            return t;
        }
        boolean sit = p.sitting;
        float hy = (sit ? 1.48f : 1.70f) * sc, hr = 0.17f * sc;
        // Head: a sphere.
        float lx = ox - p.x, ly = oy - hy, lz = oz - p.z;
        float b = lx * dx + ly * dy + lz * dz;
        float c = lx * lx + ly * ly + lz * lz - hr * hr;
        float disc = b * b - c;
        float th = -1;
        if (disc >= 0) {
            th = -b - (float) Math.sqrt(disc);
            if (th < 0) th = -1;
        }
        // Body: an upright cylinder.
        float br = 0.27f * sc, y0 = sit ? 0.35f * sc : 0, y1 = (sit ? 1.3f : 1.52f) * sc;
        float a2 = dx * dx + dz * dz;
        float tb = -1;
        if (a2 > 1e-8f) {
            float b2 = 2 * (lx * dx + lz * dz);
            float c2 = lx * lx + lz * lz - br * br;
            float disc2 = b2 * b2 - 4 * a2 * c2;
            if (disc2 >= 0) {
                float t = (-b2 - (float) Math.sqrt(disc2)) / (2 * a2);
                float y = oy + dy * t;
                if (t > 0 && y >= y0 && y <= y1) tb = t;
            }
        }
        if (th > 0 && (tb < 0 || th < tb + 0.1f)) {
            head[0] = true;
            return th;
        }
        return tb;
    }

    static final int KILL_TAKEDOWN = 0, KILL_SHOT = 1, KILL_CAR = 2, KILL_PUSH = 3,
            KILL_RUNOVER = 4, KILL_BLAST = 5;

    private void kill(Person p, int how) {
        if (p.dead) return;
        p.dead = true;
        p.ko = false;
        p.fallRaw = Math.max(p.fallRaw, p.fall);
        p.sitting = false;
        p.bleed = how == KILL_TAKEDOWN ? 0 : 30;
        p.bubble = null;
        if (p.target) {
            finishContract(how);
        } else if (how != KILL_CAR) {
            civKills++;
        }
        if (how != KILL_CAR) {
            if (p.kind == COP) {
                if (traffic.copSees()) traffic.report(1, "Killed a cop", true);
                else crimeSeen(1, "Killed a cop");
            } else {
                crimeSeen(how == KILL_RUNOVER ? 0.7f : 1, "Murder");
            }
        }
    }

    // ================================================================== bodies

    /** An out-cold or dead body near you. */
    Person bodyNear() {
        if (dragging != null) return null;
        Person best = null;
        float bestD = 1.5f;
        for (Person p : people) {
            if (!p.active || !(p.ko || p.dead)) continue;
            float d = dist(p, player);
            if (d < bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    void drag(Person b) {
        dragging = b;
    }

    void drop() {
        dragging = null;
    }

    /** A hiding place in reach with room left. */
    Spot hideNear() {
        for (Spot s : spots) {
            if (s.type != HIDE || s.used >= s.capacity) continue;
            if (dist2(s.x, s.z, player.x, player.z) < 1.6f * 1.6f) return s;
        }
        return null;
    }

    void hide(Spot s) {
        if (dragging == null) return;
        dragging.active = false;
        dragging.hidden = true;
        dragging = null;
        s.used++;
        toast = "Hidden in the " + s.label.toLowerCase(java.util.Locale.US) + ".";
    }

    void takeOutfit(Person b) {
        int mine = player.outfit;
        dress(player, b.outfit);
        player.jacket = 0;
        player.suit = false;
        b.stripped = true;
        b.outfit = CIVILIAN;
        if (mine != CIVILIAN) dress(b, mine);
        else {
            b.shirt = 0xFFD8D4CC;
            b.pants = 0xFF6A6A72;
            b.vest = b.tie = false;
        }
        warned = false;
        toast = "You're wearing " + OUTFIT_NAMES[player.outfit] + ".";
    }

    /** What you're wearing from the stores, one item id per slot. */
    String[] worn = new String[CasinoShop.GEAR];

    void ownClothes() {
        player.outfit = CIVILIAN;
        player.vest = player.tie = false;
        CasinoShop.apply(player, worn, playerShirt, playerPants);
    }

    /** Puts on new store clothes; in a uniform, only the bling shows until you change. */
    void restyle() {
        if (player.outfit == CIVILIAN) {
            ownClothes();
            return;
        }
        int shirt = player.shirt, pants = player.pants;
        boolean tie = player.tie, vest = player.vest;
        CasinoShop.apply(player, worn, playerShirt, playerPants);
        player.shirt = shirt;
        player.pants = pants;
        player.tie = tie;
        player.vest = vest;
        player.jacket = 0;
        player.suit = false;
    }

    boolean softShoes() {
        return "sneakers_soft".equals(worn[CasinoShop.SHOES]);
    }

    // ================================================================== contracts

    CasinoMissions.Contract contract;
    Person targetP, bodyguard;
    boolean spotted, bodyFound, targetWitnessed, contractDone;
    int civKills;
    /** How the target went down, once the contract's done. */
    int killedHow = -1;

    void startContract(CasinoMissions.Contract c) {
        abortContract();
        contract = c;
        spotted = bodyFound = targetWitnessed = contractDone = false;
        civKills = 0;
        killedHow = -1;
        Person t = newPerson(ROUTE);
        t.name = c.name;
        t.target = true;
        t.route = c.route;
        t.x = c.route[0][0];
        t.z = c.route[0][1];
        t.speed = 1.05f;
        t.shirt = c.shirt;
        t.pants = c.pants;
        t.hair = c.hair;
        t.hat = c.hat;
        t.scale = c.scale;
        t.talk = c.lines;
        t.talkT = 4;
        targetP = t;
        if (c.guardRoute != null) {
            Person g = newPerson(ROUTE);
            dress(g, SECURITY);
            g.shirt = 0xFF2A2A30;
            g.name = "Bodyguard";
            g.scale = 1.15f;
            g.tough = true;
            g.route = c.guardRoute;
            g.x = c.guardRoute[0][0];
            g.z = c.guardRoute[0][1];
            bodyguard = g;
        }
    }

    void abortContract() {
        if (targetP != null && !targetP.dead) people.remove(targetP);
        if (bodyguard != null && !bodyguard.ko && !bodyguard.dead) people.remove(bodyguard);
        targetP = bodyguard = null;
        contract = null;
    }

    private void finishContract(int how) {
        contractDone = true;
        killedHow = how;
        toast = "Target down.";
    }

    /** True when nothing went wrong: never spotted, nothing found, nobody else hurt. */
    boolean silentAssassin() {
        return !spotted && !bodyFound && !targetWitnessed && civKills == 0;
    }

    // ================================================================== nearby things

    /** What the main button does right now. */
    static final int A_NONE = 0, A_SPOT = 1, A_DOOR = 2, A_TAKEDOWN = 3, A_DRAG = 4,
            A_DROP = 5, A_HIDE = 6, A_OUTFIT = 7, A_CAR = 8, A_EXIT = 9, A_ROB = 10,
            A_FINISH = 11;

    static final class Action {
        final int type;
        final String label;
        final Object ref;

        Action(int type, String label, Object ref) {
            this.type = type;
            this.label = label;
            this.ref = ref;
        }
    }

    /** Up to two things you could do here, the more likely one first. */
    List<Action> actions() {
        List<Action> out = new ArrayList<>();
        if (escort > 0) return out;
        if (traffic.driving != null) {
            out.add(new Action(A_EXIT, "Get out", null));
            return out;
        }
        CasinoTraffic.Car near = traffic.carNear();
        if (near != null && dragging == null) {
            out.add(new Action(A_CAR, traffic.enterLabel(near), near));
        }
        if (dragging != null) {
            Spot h = hideNear();
            if (h != null) out.add(new Action(A_HIDE, "Hide body: " + h.label, h));
            out.add(new Action(A_DROP, "Drop body", null));
            return out;
        }
        Person t = takedownTarget();
        if (t != null) out.add(new Action(A_TAKEDOWN, t.target ? "Eliminate " + t.name
                : "Takedown", t));
        Person b = bodyNear();
        if (b != null && b.ko && b.target && !b.dead) {
            out.add(new Action(A_FINISH, "Finish " + b.name, b));
        } else if (b != null && b.ko && !b.robbed && !b.dead && b.kind != COP
                && b.kind != GUARD) {
            out.add(new Action(A_ROB, "Search pockets", b));
        }
        if (b != null) {
            out.add(new Action(A_DRAG, "Drag body", b));
            if (b.outfit != CIVILIAN && b.outfit != player.outfit) {
                out.add(new Action(A_OUTFIT, "Take " + OUTFIT_NAMES[b.outfit], b));
            }
        }
        if (out.size() >= 2) return out.subList(0, 2);
        Door door = null;
        float bestD = 1.6f * 1.6f;
        for (Door d : doors) {
            if (d.unlocked || canOpen(d)) continue;
            float dd = dist2(d.x, d.z, player.x, player.z);
            if (dd < bestD) {
                bestD = dd;
                door = d;
            }
        }
        Spot spot = null;
        float bestS = 1.5f * 1.5f;
        for (Spot s : spots) {
            if (!available(s)) continue;
            float dd = dist2(s.x, s.z, player.x, player.z);
            if (dd < bestS) {
                bestS = dd;
                spot = s;
            }
        }
        if (door != null && (spot == null || bestD < bestS)) {
            out.add(new Action(A_DOOR, doorLabel(door), door));
        } else if (spot != null) {
            out.add(new Action(A_SPOT, spotLabel(spot), spot));
        }
        if (out.size() > 2) return out.subList(0, 2);
        return out;
    }

    boolean available(Spot s) {
        switch (s.type) {
            case GAME:
                return !alarm;
            case LOOT:
                return !flags.contains("loot:" + s.id);
            case CHIP:
                return !flags.contains("chip:" + s.id);
            case KEY:
                return !flags.contains("key:" + s.id);
            case HIDE:
                return false;
            case RANGE_START:
                return !rangeOn && !alarm;
            default:
                return true;
        }
    }

    String doorLabel(Door d) {
        if (d.key != null) {
            return flags.contains("key:" + d.key) ? "Swipe key card" : d.label + ": key card";
        }
        return "Pick lock: " + d.label + " (" + d.pins + " pins)";
    }

    String spotLabel(Spot s) {
        switch (s.type) {
            case GAME:
                return "Play " + s.label;
            case LOOT:
                return (s.pins > 0 ? "Pick " : "Search ") + s.label
                        + (s.pins > 0 ? " (" + s.pins + " pins)" : "");
            case NOTE:
                return "Read " + s.label;
            case CHIP:
                return "Take the lucky chip";
            case KEY:
                return "Take " + s.label;
            case SHOP:
                return s.label;
            default:
                return s.label;
        }
    }

    void doWorldAction(Action a) {
        switch (a.type) {
            case A_TAKEDOWN:
                takedown((Person) a.ref);
                break;
            case A_DRAG:
                drag((Person) a.ref);
                break;
            case A_DROP:
                drop();
                break;
            case A_HIDE:
                hide((Spot) a.ref);
                break;
            case A_OUTFIT:
                takeOutfit((Person) a.ref);
                break;
            case A_CAR:
                aiming = false;
                traffic.enter((CasinoTraffic.Car) a.ref);
                camYaw = ((CasinoTraffic.Car) a.ref).h;
                break;
            case A_ROB: {
                Person v = (Person) a.ref;
                v.robbed = true;
                int cash = 10 + rnd.nextInt(70);
                if (v.vest || v.kind == STAFF) cash += 40;
                if (v.scale > 1.05f) cash += 30;
                cashDue += cash;
                toast = "Found " + cash + " coupons.";
                crimeSeen(0.3f, "Mugging");
                break;
            }
            case A_FINISH: {
                Person v = (Person) a.ref;
                player.yaw = (float) Math.atan2(v.x - player.x, -(v.z - player.z));
                player.punch = PUNCH_TIME;
                sounds |= 1;
                boolean seen = witnessed(0.7f, true, v);
                if (seen) targetWitnessed = true;
                kill(v, KILL_TAKEDOWN);
                break;
            }
            case A_EXIT:
                exitCar(false);
                break;
            default:
                break;
        }
    }

    // ================================================================== camera

    void camera(float aspect, float[] view, float[] proj, float[] eyeOut) {
        if (aiming && weapon != null && escort <= 0 && traffic.driving == null) {
            aimCamera(aspect, view, proj, eyeOut);
            return;
        }
        if (traffic.driving != null) {
            driveCamera(aspect, view, proj, eyeOut);
            return;
        }
        Person me = player;
        float tx = me.x, ty = 1.45f, tz = me.z;
        float cp = (float) Math.cos(camPitch), sp = (float) Math.sin(camPitch);
        float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
        int reg = regionAt(tx, tz);
        float ceil = reg >= 0 ? regions.get(reg)[4] - 0.25f : 30;
        float dist = camDist;
        // Pull in when a wall or anything tall is between you and the camera.
        for (int k = 1; k <= 20; k++) {
            float t = k / 20f * camDist;
            float px = tx - fx * cp * t, pz = tz - fz * cp * t, py = ty + sp * t;
            if (py > ceil || blocked(px, py, pz) || regionAt(px, pz) != reg) {
                dist = Math.max(0.6f, t - 0.35f);
                break;
            }
        }
        // Snap in when something's in the way, but ease back out so it doesn't pump.
        float since = Math.max(0, Math.min(0.1f, time - camDistAt));
        camDistAt = time;
        if (dist < camDistNow || camDistNow <= 0) camDistNow = dist;
        else camDistNow += (dist - camDistNow) * Math.min(1, since * 3);
        dist = camDistNow;
        float ex = tx - fx * cp * dist, ey = Math.min(ceil, ty + sp * dist),
                ez = tz - fz * cp * dist;
        if (me.shake > 0) {
            float k = me.shake * 0.12f;
            ex += (float) Math.sin(time * 71) * k;
            ey += (float) Math.cos(time * 53) * k;
        }
        Mat.lookAt(view, ex, ey, ez, tx, ty, tz);
        Mat.perspective(proj, 62, aspect, 0.3f, 120);
        eye[0] = me.x + (float) Math.cos(camYaw) * 0.55f;
        eye[1] = 1.62f;
        eye[2] = me.z + (float) Math.sin(camYaw) * 0.55f;
        if (eyeOut != null) {
            eyeOut[0] = ex;
            eyeOut[1] = ey;
            eyeOut[2] = ez;
        }
    }

    /** Behind and above the car, pulled in when something's in the way. */
    private void driveCamera(float aspect, float[] view, float[] proj, float[] eyeOut) {
        CasinoTraffic.Car c = traffic.driving;
        float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
        float tx = c.x + fx * 2, ty = 1.4f, tz = c.z + fz * 2;
        float back = 7.5f + Math.abs(c.speed) * 0.08f, up = 3.0f;
        float dist = back;
        for (int k = 1; k <= 12; k++) {
            float t = back * k / 12;
            if (blocked(c.x - fx * t, up * t / back + 0.8f, c.z - fz * t)) {
                dist = Math.max(2.5f, t - 0.5f);
                break;
            }
        }
        float ex = c.x - fx * dist, ey = 0.8f + up * dist / back, ez = c.z - fz * dist;
        if (player.shake > 0) {
            float k = player.shake * 0.12f;
            ex += (float) Math.sin(time * 71) * k;
            ey += (float) Math.cos(time * 53) * k;
        }
        Mat.lookAt(view, ex, ey, ez, tx, ty, tz);
        Mat.perspective(proj, 62 + Math.min(12, Math.abs(c.speed) * 0.5f), aspect, 0.4f, 140);
        if (eyeOut != null) {
            eyeOut[0] = ex;
            eyeOut[1] = ey;
            eyeOut[2] = ez;
        }
    }

    /** Where the aiming camera sits: behind your right shoulder (sets eye). */
    private void aimEye() {
        Person me = player;
        float rx = (float) Math.cos(camYaw), rz = (float) Math.sin(camYaw);
        float sx = me.x + rx * 0.8f, sy = 1.78f, sz = me.z + rz * 0.8f;
        float back = weapon != null && weapon.fov < 25 ? 0.2f : 2.0f;
        int reg = regionAt(me.x, me.z);
        if (blocked(sx, sy, sz) || regionAt(sx, sz) != reg) {
            sx = me.x;
            sz = me.z;
        }
        // Slide back from the shoulder until something's in the way.
        float ok = 0;
        for (int k = 1; k <= 10; k++) {
            float t = back * k / 10;
            float px = sx - fwd[0] * t, py = sy - fwd[1] * t, pz = sz - fwd[2] * t;
            if (blocked(px, py, pz) || regionAt(px, pz) != reg) break;
            ok = t;
        }
        ok = Math.max(0, ok - 0.15f);
        float ex = sx - fwd[0] * ok, ey = sy - fwd[1] * ok, ez = sz - fwd[2] * ok;
        eye[0] = ex;
        eye[1] = ey;
        eye[2] = ez;
    }

    /** Over the right shoulder, looking where the gun points. */
    private void aimCamera(float aspect, float[] view, float[] proj, float[] eyeOut) {
        Person me = player;
        aimRay();
        aimEye();
        float ex = eye[0], ey = eye[1], ez = eye[2];
        if (me.shake > 0) {
            float k = me.shake * 0.05f;
            ex += (float) Math.sin(time * 71) * k;
            ey += (float) Math.cos(time * 53) * k;
        }
        eye[0] = ex;
        eye[1] = ey;
        eye[2] = ez;
        Mat.lookAt(view, ex, ey, ez, ex + fwd[0] * 10, ey + fwd[1] * 10, ez + fwd[2] * 10);
        Mat.perspective(proj, weapon.fov, aspect, 0.25f, 140);
        if (eyeOut != null) System.arraycopy(eye, 0, eyeOut, 0, 3);
    }

    void aimLook(float dYaw, float dPitch) {
        manualAim = 0.45f;
        lock = null;
        float k = weapon != null ? weapon.fov / 62f : 1;
        camYaw += dYaw * k;
        aimPitch = Math.max(-0.7f, Math.min(0.9f, aimPitch + dPitch * k));
    }

    private boolean blocked(float x, float y, float z) {
        float m = 0.15f;
        for (float[] w : walls) {
            float y0 = w.length > 5 ? w[5] - 0.15f : -1;
            if (y < w[4] + 0.1f && y > y0 && x > w[0] - m && x < w[2] + m && z > w[1] - m
                    && z < w[3] + m) {
                return true;
            }
        }
        for (Door d : doors) {
            if (!d.shut()) continue;
            float[] w = d.box();
            if (y < 2.6f && x > w[0] - m && x < w[2] + m && z > w[1] - m && z < w[3] + m) {
                return true;
            }
        }
        return x < WX0 || x > WX1 || z < WZ0 || z > WZ1;
    }

    /** Seconds the camera stays where you dragged it before following the car again. */
    float lookT;
    boolean running;
    private float camDistNow, camDistAt;

    static float angleDiff(float a, float b) {
        float d = a - b;
        while (d > PI) d -= 2 * PI;
        while (d < -PI) d += 2 * PI;
        return d;
    }

    void orbit(float dYaw, float dPitch) {
        lookT = traffic != null && traffic.driving != null ? 1.5f : 2.0f;
        camYaw += dYaw;
        camPitch = Math.max(0.05f, Math.min(1.1f, camPitch + dPitch));
    }

    // ================================================================== blood

    final List<float[]> drops = new ArrayList<>();
    final List<float[]> splats = new ArrayList<>();
    static final float SPLAT_LIFE = 45;

    private void updateBlood(float dt) {
        for (int i = drops.size() - 1; i >= 0; i--) {
            float[] d = drops.get(i);
            d[4] -= 9.8f * dt;
            d[0] += d[3] * dt;
            d[1] += d[4] * dt;
            d[2] += d[5] * dt;
            if (d[1] <= 0.01f) {
                drops.remove(i);
                splats.add(new float[]{d[0], d[2], d[6] * (3.5f + rnd.nextFloat() * 3.5f),
                        rnd.nextFloat() * PI, 0});
            }
        }
        for (int i = splats.size() - 1; i >= 0; i--) {
            float[] sp = splats.get(i);
            sp[4] += dt;
            if (sp[4] > SPLAT_LIFE) splats.remove(i);
        }
        while (splats.size() > 160) splats.remove(0);
    }

    void spray(float x, float y, float z, float dx, float dz, int n) {
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

    // ================================================================== drawing things that move

    /** Receives one box: a model matrix for a unit cube, its color and how much it glows. */
    interface PartSink {
        void part(float[] model, int color, float glow);

        void shadow(float x, float z, float radius);
    }

    private final float[] base = new float[16], joint = new float[16], tmp = new float[16],
            shoulder = new float[16];

    /** Things farther than this from you aren't drawn, to keep older phones smooth. */
    static final float DRAW_FAR = 62;

    boolean far(float x, float z) {
        return dist2(x, z, player.x, player.z) > DRAW_FAR * DRAW_FAR;
    }

    void drawPeople(PartSink sink) {
        if (traffic.driving == null) drawPerson(player, sink);
        for (Person p : people) {
            if (p.active && !far(p.x, p.z)) drawPerson(p, sink);
        }
        for (float[] d : drops) {
            Mat.identity(tmp);
            Mat.translate(tmp, d[0], d[1], d[2]);
            Mat.scale(tmp, d[6], d[6] * 1.4f, d[6]);
            sink.part(tmp, 0xFFD01820, 0);
        }
        for (float[] sp : splats) {
            float grow = Math.min(1, 0.5f + sp[4] * 4);
            float fade = sp[4] > SPLAT_LIFE - 4 ? (SPLAT_LIFE - sp[4]) / 4 : 1;
            float r = sp[2] * grow * fade;
            Mat.identity(tmp);
            Mat.translate(tmp, sp[0], 0.016f, sp[1]);
            Mat.rotY(tmp, sp[3]);
            Mat.scale(tmp, r, 0.004f, r * 0.7f);
            sink.part(tmp, 0xFFC4161E, 0);
        }
        drawDoors(sink);
        drawPickups(sink);
        for (float[] t : tracers) {
            float dx = t[3] - t[0], dy = t[4] - t[1], dz = t[5] - t[2];
            float hz = (float) Math.hypot(dx, dz), len = (float) Math.hypot(hz, dy);
            if (len < 0.05f) continue;
            Mat.identity(tmp);
            Mat.translate(tmp, (t[0] + t[3]) / 2, (t[1] + t[4]) / 2, (t[2] + t[5]) / 2);
            Mat.rotY(tmp, (float) Math.atan2(-dz, dx));
            Mat.rotZ(tmp, (float) Math.atan2(dy, hz));
            Mat.scale(tmp, len, 0.018f, 0.018f);
            sink.part(tmp, 0xFFFFE8A0, 1);
        }
        for (float[] p : sparks) {
            Mat.identity(tmp);
            Mat.translate(tmp, p[0], p[1], p[2]);
            Mat.scale(tmp, 0.04f, 0.04f, 0.04f);
            sink.part(tmp, 0xFFFFC870, 1);
        }
        drawRange(sink);
        traffic.draw(sink);
        for (float[] p : smokes) {
            Mat.identity(tmp);
            Mat.translate(tmp, p[0], p[1], p[2]);
            Mat.rotY(tmp, p[0] * 3);
            Mat.scale(tmp, p[5], p[5], p[5]);
            sink.part(tmp, p[6] > 0 ? 0xFFFF8A2A : 0xFF3A3836, p[6] > 0 ? 1 : 0);
        }
        // A small red marker over the contract target.
        if (targetP != null && targetP.active && !targetP.dead) {
            Mat.identity(tmp);
            Mat.translate(tmp, targetP.x, headY(targetP) + 0.25f
                    + (float) Math.sin(time * 3) * 0.05f, targetP.z);
            Mat.rotY(tmp, time * 2);
            Mat.rotZ(tmp, PI / 4);
            Mat.scale(tmp, 0.14f, 0.14f, 0.04f);
            sink.part(tmp, 0xFFFF2A2A, 1);
        }
    }

    private void drawDoors(PartSink s) {
        for (Door d : doors) {
            float ang = d.open * 1.6f;
            Mat.identity(tmp);
            if (d.alongX) {
                Mat.translate(tmp, d.x - d.w / 2, 0, d.z);
                Mat.rotY(tmp, ang);
                Mat.translate(tmp, d.w / 2, 1.25f, 0);
                Mat.scale(tmp, d.w - 0.04f, 2.5f, 0.07f);
            } else {
                Mat.translate(tmp, d.x, 0, d.z - d.w / 2);
                Mat.rotY(tmp, -ang);
                Mat.translate(tmp, 0, 1.25f, d.w / 2);
                Mat.scale(tmp, 0.07f, 2.5f, d.w - 0.04f);
            }
            s.part(tmp, d.color, 0);
            // Handle or card reader.
            Mat.identity(tmp);
            boolean card = d.key != null;
            int c = card ? (d.unlocked ? 0xFF3CFF6A : 0xFFFF3030) : BRASS;
            if (d.alongX) Mat.translate(tmp, d.x + d.w / 2 + 0.18f, 1.15f, d.z + 0.14f);
            else Mat.translate(tmp, d.x + 0.14f, 1.15f, d.z + d.w / 2 + 0.18f);
            Mat.scale(tmp, card ? 0.12f : 0.06f, card ? 0.18f : 0.06f, card ? 0.06f : 0.06f);
            s.part(tmp, c, card ? 1 : 0.2f);
        }
    }

    private void drawPickups(PartSink s) {
        for (Spot sp : spots) {
            if (sp.type != CHIP && sp.type != KEY) continue;
            if (!available(sp)) continue;
            Mat.identity(tmp);
            Mat.translate(tmp, sp.x, sp.y + (float) Math.sin(time * 2 + sp.x) * 0.05f, sp.z);
            Mat.rotY(tmp, time * 1.8f + sp.z);
            if (sp.type == CHIP) {
                Mat.scale(tmp, 0.2f, 0.2f, 0.035f);
                s.part(tmp, 0xFFFFC94A, 0.8f);
            } else {
                Mat.scale(tmp, 0.22f, 0.14f, 0.02f);
                s.part(tmp, 0xFFE8E8F0, 0.6f);
            }
        }
    }

    /** White doors and a roof light bar on the last car drawn (a police sedan). */
    void drawPoliceBits(PartSink s, boolean siren) {
        for (int side = -1; side <= 1; side += 2) {
            cp(s, -0.2f, 0.62f, side * 0.906f, 1.9f, 0.4f, 0.01f, 0xFFE8E8EA, 0);
        }
        cp(s, -0.33f, 1.49f, 0, 0.22f, 0.08f, 1.2f, 0xFF202024, 0);
        boolean blink = ((int) (time * 6)) % 2 == 0;
        cp(s, -0.33f, 1.55f, 0.32f, 0.2f, 0.1f, 0.5f, 0xFF2A5AFF,
                siren ? (blink ? 1 : 0.25f) : 0.2f);
        cp(s, -0.33f, 1.55f, -0.32f, 0.2f, 0.1f, 0.5f, 0xFFFF2A2A,
                siren ? (blink ? 0.25f : 1) : 0.2f);
    }

    private final float[] car = new float[16];
    static final int GLASS = 0xFF161C28, TRIM = 0xFF1E1E22, TIRE = 0xFF121214,
            RIM = 0xFFB8BCC4;

    /**
     * One car. Its own front is +x; yaw turns it (0 drives east, PI west, -PI/2 faces south).
     */
    void drawCar(PartSink s, float x, float z, float yaw, int col, int type, float spin,
                 boolean lights) {
        Mat.identity(car);
        Mat.translate(car, x, 0, z);
        Mat.rotY(car, yaw);
        s.shadow(x, z, 2.3f);
        boolean suv = type == SUV, pickup = type == PICKUP;
        float len = suv ? 4.6f : (pickup ? 4.9f : 4.4f);
        float hl = len / 2, hw = suv || pickup ? 0.95f : 0.9f;
        float y0 = suv || pickup ? 0.42f : 0.34f;
        float y1 = suv ? 1.04f : (pickup ? 0.98f : 0.84f);
        float r = suv || pickup ? 0.4f : 0.34f;
        float wx = suv ? 1.5f : (pickup ? 1.6f : 1.42f);
        int dark = shade(col, 0.82f);

        // Body, bumpers, grille, lights.
        cp(s, 0, (y0 + y1) / 2, 0, len - 0.2f, y1 - y0, hw * 2, col, 0);
        cp(s, hl - 0.06f, y0 + 0.12f, 0, 0.14f, 0.26f, hw * 2 - 0.08f, TRIM, 0);
        cp(s, -hl + 0.06f, y0 + 0.12f, 0, 0.14f, 0.26f, hw * 2 - 0.08f, TRIM, 0);
        cp(s, hl - 0.1f, y1 - 0.22f, 0, 0.04f, 0.14f, hw * 0.8f, 0xFF0E0E10, 0);
        for (int side = -1; side <= 1; side += 2) {
            cp(s, hl - 0.11f, y1 - 0.16f, side * (hw - 0.26f), 0.05f, 0.1f, 0.26f,
                    lights ? 0xFFFFF8E8 : 0xFF9A9A92, lights ? 1 : 0.1f);
            cp(s, -hl + 0.11f, y1 - 0.14f, side * (hw - 0.24f), 0.05f, 0.12f, 0.3f,
                    0xFFE01818, lights ? 1 : 0.15f);
            // Dark wheel arches cut into the sides.
            cp(s, wx, y0 + 0.2f, side * (hw + 0.004f), r * 2.6f, 0.4f, 0.01f, 0xFF0C0C0E, 0);
            cp(s, -wx, y0 + 0.2f, side * (hw + 0.004f), r * 2.6f, 0.4f, 0.01f, 0xFF0C0C0E, 0);
            // A crease line down the side.
            cp(s, 0, y1 - 0.1f, side * (hw + 0.003f), len - 0.5f, 0.03f, 0.01f, dark, 0);
        }
        cp(s, -hl + 0.02f, y0 + 0.16f, 0, 0.02f, 0.12f, 0.34f, 0xFFE8E8E0, 0.3f);
        if (type == TAXI) {
            for (int side = -1; side <= 1; side += 2) {
                cp(s, 0.1f, y1 - 0.24f, side * (hw + 0.005f), 2.6f, 0.08f, 0.01f,
                        0xFF111111, 0);
            }
        }

        // Cabin: glass, sloped windshield and back glass, roof, pillars, mirrors.
        float cf, cb, top;
        if (suv) {
            cf = 0.75f;
            cb = -2.05f;
            top = 1.62f;
        } else if (pickup) {
            cf = 0.95f;
            cb = -0.35f;
            top = 1.62f;
        } else {
            cf = 0.95f;
            cb = -1.6f;
            top = 1.38f;
        }
        float slope = suv || pickup ? 0.35f : 0.5f;
        float gw = hw * 2 - 0.26f;
        cp(s, (cf - slope + cb + (suv || pickup ? 0 : slope)) / 2, (y1 + top) / 2, 0,
                (cf - slope) - (cb + (suv || pickup ? 0 : slope)), top - y1, gw, GLASS, 0.06f);
        slab(s, cf, y1, cf - slope, top, gw, 0.06f, GLASS, 0.06f);
        if (!suv && !pickup) slab(s, cb + slope, top, cb, y1, gw, 0.06f, GLASS, 0.06f);
        float roofF = cf - slope, roofB = suv || pickup ? cb : cb + slope;
        cp(s, (roofF + roofB) / 2, top + 0.04f, 0, roofF - roofB + 0.06f, 0.08f, gw + 0.06f,
                col, 0);
        cp(s, (roofF + roofB) / 2 - 0.1f, (y1 + top) / 2, 0, 0.1f, top - y1, gw + 0.04f,
                dark, 0);
        if (suv) {
            cp(s, (roofF + roofB) / 2, top + 0.1f, hw - 0.25f, roofF - roofB - 0.3f, 0.04f,
                    0.05f, TRIM, 0);
            cp(s, (roofF + roofB) / 2, top + 0.1f, -hw + 0.25f, roofF - roofB - 0.3f, 0.04f,
                    0.05f, TRIM, 0);
        }
        for (int side = -1; side <= 1; side += 2) {
            cp(s, cf - 0.15f, y1 + 0.1f, side * (hw + 0.06f), 0.1f, 0.08f, 0.12f, col, 0);
        }
        if (type == TAXI) {
            cp(s, (roofF + roofB) / 2, top + 0.17f, 0, 0.3f, 0.18f, 0.72f, 0xFFFFE28A, 0.9f);
        }
        if (pickup) {
            // The open bed with low walls and a tailgate.
            float bf = cb - 0.08f, bb = -hl + 0.1f;
            cp(s, (bf + bb) / 2, y1 + 0.02f, 0, bf - bb, 0.04f, hw * 2 - 0.1f, 0xFF15151A, 0);
            for (int side = -1; side <= 1; side += 2) {
                cp(s, (bf + bb) / 2, y1 + 0.2f, side * (hw - 0.05f), bf - bb, 0.36f, 0.1f,
                        col, 0);
            }
            cp(s, bb + 0.05f, y1 + 0.2f, 0, 0.1f, 0.36f, hw * 2 - 0.1f, col, 0);
            cp(s, bf, y1 + 0.2f, 0, 0.1f, 0.36f, hw * 2 - 0.1f, col, 0);
        }

        // Wheels: hexagon tires with a two-spoke rim that turns as the car rolls.
        for (int i = -1; i <= 1; i += 2) {
            for (int side = -1; side <= 1; side += 2) {
                wheel(s, i * wx, side * (hw - 0.06f), r, spin, side);
            }
        }
        // Headlights throw soft pools of light on the road ahead (a negative radius).
        if (lights) {
            float c = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
            for (float k : new float[]{hl + 1.6f, hl + 4.2f}) {
                s.shadow(x + c * k, z - sn * k, -(k < hl + 2 ? 1.7f : 2.6f));
            }
        }
    }

    private void wheel(PartSink s, float x, float z, float r, float spin, int side) {
        float a = r * 0.95f;
        for (int k = 0; k < 3; k++) {
            copy(tmp, car);
            Mat.translate(tmp, x, r, z);
            Mat.rotZ(tmp, spin + k * PI / 3);
            Mat.scale(tmp, 2 * a, 2 * a * 0.5774f, 0.26f);
            s.part(tmp, TIRE, 0);
        }
        for (int k = 0; k < 2; k++) {
            copy(tmp, car);
            Mat.translate(tmp, x, r, z + side * 0.135f);
            Mat.rotZ(tmp, spin + k * PI / 2);
            Mat.scale(tmp, a * 1.2f, a * 0.22f, 0.02f);
            s.part(tmp, RIM, 0.05f);
        }
    }

    /** A box on the car, in the car's own coordinates. */
    private void cp(PartSink s, float x, float y, float z, float w, float h, float d, int c,
                    float glow) {
        copy(tmp, car);
        Mat.translate(tmp, x, y, z);
        Mat.scale(tmp, w, h, d);
        s.part(tmp, c, glow);
    }

    /** A thin tilted panel on the car from (x0, y0) to (x1, y1), d wide. */
    private void slab(PartSink s, float x0, float y0, float x1, float y1, float d, float thick,
                      int c, float glow) {
        copy(tmp, car);
        Mat.translate(tmp, (x0 + x1) / 2, (y0 + y1) / 2, 0);
        Mat.rotZ(tmp, (float) Math.atan2(y1 - y0, x1 - x0));
        Mat.scale(tmp, (float) Math.hypot(x1 - x0, y1 - y0), thick, d);
        s.part(tmp, c, glow);
    }

    private void box(PartSink s, float x, float y, float z, float w, float h, float d, int c,
                     float glow) {
        Mat.identity(tmp);
        Mat.translate(tmp, x, y, z);
        Mat.scale(tmp, w, h, d);
        s.part(tmp, c, glow);
    }

    private final float[] armOut = new float[3], legOut = new float[2];
    private float armRoll;

    /** A fighter winding up a swing you can see coming. */
    private boolean telegraph(Person p) {
        return p != player && CasinoFight.attacking(p) && p.actT < p.actWind;
    }

    private static float pullOf(Person p) {
        return p.actT < p.actWind ? p.actT / Math.max(0.01f, p.actWind) : 0;
    }

    private static float extOf(Person p) {
        if (p.actT < p.actWind) return 0;
        float t = p.actT - p.actWind;
        if (t < 0.07f) return 1;
        return Math.max(0, 1 - (t - 0.07f) / Math.max(0.05f, p.actDur - p.actWind - 0.07f));
    }

    private static void mix(float[] out, float[] a, float[] b, float k) {
        for (int i = 0; i < out.length; i++) out[i] = a[i] + (b[i] - a[i]) * k;
    }

    /** Arm angle, elbow and roll for fighting poses; false to use the normal pose. */
    private boolean fightArm(Person p, int side, float[] out) {
        int m = p.act;
        boolean stance = p.guard || p.fighter;
        float[] g = {1.0f, 2.0f, -0.25f * side};
        if (m == CasinoFight.BLOCK) {
            out[0] = 1.85f;
            out[1] = 2.55f;
            out[2] = -0.35f * side;
            return true;
        }
        if (m == CasinoFight.STAGGER) {
            out[0] = 0.5f;
            out[1] = 0.4f;
            out[2] = 0.5f * side;
            return true;
        }
        if (CasinoFight.attacking(p)) {
            if (m >= CasinoFight.KICK) {
                float[] bal = {0.6f, 0.5f, 0.45f * side};
                System.arraycopy(stance ? g : bal, 0, out, 0, 3);
                return true;
            }
            int armSide = m == CasinoFight.JAB ? -1 : 1;
            if (side != armSide) {
                System.arraycopy(g, 0, out, 0, 3);
                return true;
            }
            float[] wp, sp;
            switch (m) {
                case CasinoFight.JAB:
                    wp = new float[]{0.9f, 2.1f, -0.1f * side};
                    sp = new float[]{1.6f, 0.05f, -0.15f * side};
                    break;
                case CasinoFight.CROSS:
                    wp = new float[]{0.7f, 2.2f, 0};
                    sp = new float[]{1.6f, 0.05f, -0.2f * side};
                    break;
                case CasinoFight.HOOK:
                    wp = new float[]{1.0f, 1.5f, 0.7f * side};
                    sp = new float[]{1.45f, 1.4f, -0.6f * side};
                    break;
                default:
                    wp = new float[]{0.1f, 2.0f, 0};
                    sp = new float[]{2.6f, 0.6f, -0.1f * side};
                    break;
            }
            if (p.actT < p.actWind) mix(out, g, wp, pullOf(p));
            else mix(out, g, sp, extOf(p));
            return true;
        }
        if (stance && p.walk < 0.95f) {
            System.arraycopy(g, 0, out, 0, 3);
            return true;
        }
        return false;
    }

    private boolean fightLeg(Person p, int side, float[] out) {
        int m = p.act;
        if (side != 1 || m < CasinoFight.KICK || m > CasinoFight.STOMP) return false;
        float[] g = {0, 0}, wp, sp;
        if (m == CasinoFight.KICK) {
            wp = new float[]{0.7f, 1.3f};
            sp = new float[]{1.55f, 0.05f};
        } else if (m == CasinoFight.ROUND) {
            wp = new float[]{0.9f, 1.4f};
            sp = new float[]{1.7f, 0.1f};
        } else {
            wp = new float[]{1.1f, 1.6f};
            sp = new float[]{0.25f, 0.1f};
        }
        if (p.actT < p.actWind) mix(out, g, wp, pullOf(p));
        else mix(out, g, sp, extOf(p));
        return true;
    }

    private static float fightTwist(Person p) {
        switch (p.act) {
            case CasinoFight.CROSS:
                return 0.45f * extOf(p) - 0.15f * pullOf(p);
            case CasinoFight.HOOK:
                return 0.7f * extOf(p) - 0.3f * pullOf(p);
            case CasinoFight.ROUND:
                return 1.2f * extOf(p) - 0.8f * pullOf(p);
            case CasinoFight.JAB:
                return -0.2f * extOf(p);
            default:
                return 0;
        }
    }

    private static float fightLean(Person p) {
        switch (p.act) {
            case CasinoFight.KICK:
            case CasinoFight.ROUND:
                return 0.28f * Math.max(pullOf(p), extOf(p));
            case CasinoFight.UPPER:
                return -0.2f * pullOf(p) + 0.12f * extOf(p);
            case CasinoFight.HIT:
                return 0.35f * Math.max(0, 1 - p.actT / Math.max(0.05f, p.actDur));
            case CasinoFight.STAGGER:
                return 0.22f + 0.08f * (float) Math.sin(p.actT * 14);
            case CasinoFight.DODGE:
                return 0.25f;
            case CasinoFight.BLOCK:
                return -0.1f;
            default:
                return p.guard || p.fighter ? -0.06f : 0;
        }
    }

    /** For checks off the phone: draws one person into any sink. */
    void drawPersonPublic(Person p, PartSink s) {
        drawPerson(p, s);
    }

    private void drawPerson(Person p, PartSink s) {
        float sc = p.scale;
        boolean sit = p.sitting && p.down <= 0 && !p.ko && !p.dead;
        s.shadow(p.x, p.z, 0.42f * sc * (1 + p.fall * 0.6f));
        Mat.identity(base);
        float bob = sit ? 0 : (float) Math.abs(Math.sin(p.phase)) * 0.04f * p.walk;
        // Sitting: hips on the seat, wherever it is.
        Mat.translate(base, p.x, (sit ? p.seatY + 0.06f - 0.86f * sc : 0) + bob, p.z);
        Mat.rotY(base, -p.yaw);
        Mat.scale(base, sc, sc, sc);
        if (p.fall > 0) {
            Mat.translate(base, 0, 0.14f * p.fall, 0.12f);
            Mat.rotX(base, p.fall * 1.5f);
            Mat.translate(base, 0, 0, -0.12f);
        }

        float swing = (float) Math.sin(p.phase) * 0.5f * p.walk;
        float hip = 0.86f;
        for (int side = -1; side <= 1; side += 2) {
            // Seated: thighs forward along the seat, shins down to the floor (tucked back a
            // little on a tall stool, where the feet don't reach).
            // On a tall stool the thighs slope down so the feet still reach the floor.
            // Hips at seat height; shins straight down; shoe soles on the floor.
            float thigh = (float) Math.acos(Math.max(0.07f, Math.min(0.88f,
                    ((p.seatY + 0.06f) / sc - 0.46f) / 0.43f)));
            float a = sit ? thigh + 0.04f * side : swing * side;
            float knee = sit ? thigh + 0.04f * side
                    : Math.max(0, -(float) Math.sin(p.phase) * side) * 0.7f * p.walk;
            if (p.fall > 0) {
                a = 0.15f * side;
                knee = 0.1f;
            } else if (fightLeg(p, side, legOut)) {
                a = legOut[0];
                knee = legOut[1];
            }
            copy(joint, base);
            Mat.translate(joint, 0.105f * side, hip, 0);
            Mat.rotX(joint, a);
            limb(s, joint, 0, -0.22f, 0, 0.17f, 0.44f, 0.2f, p.pants);
            Mat.translate(joint, 0, -0.43f, 0);
            Mat.rotX(joint, -knee);
            limb(s, joint, 0, -0.2f, 0, 0.155f, 0.42f, 0.18f, p.pants);
            if (side == 1 && telegraph(p) && p.act >= CasinoFight.KICK) {
                copy(tmp, joint);
                Mat.translate(tmp, 0, -0.42f, -0.05f);
                Mat.scale(tmp, 0.22f, 0.14f, 0.34f);
                s.part(tmp, 0xFFFFE070, 1);
            }
            if (p.shoes != 0) {
                copy(tmp, joint);
                Mat.translate(tmp, 0, -0.42f, -0.05f);
                Mat.scale(tmp, 0.16f, 0.08f, 0.28f);
                s.part(tmp, p.shoes, p.shoeGlow ? 0.35f : 0);
            } else {
                limb(s, joint, 0, -0.42f, -0.05f, 0.16f, 0.08f, 0.28f, 0xFF141210);
            }
        }
        copy(joint, base);
        float twist = 0;
        if (p.punch > 0) {
            float t = 1 - p.punch / PUNCH_TIME;
            twist = (float) Math.sin(Math.min(1, t * 2.2f) * Math.PI) * 0.35f;
        }
        twist += fightTwist(p);
        Mat.translate(joint, 0, hip, 0);
        Mat.rotY(joint, twist);
        float lean = fightLean(p);
        if (lean != 0) Mat.rotX(joint, lean);
        if (p.jacket != 0) {
            // A jacket hanging open over the shirt.
            limb(s, joint, 0, 0.3f, 0, 0.5f, 0.64f, 0.29f, p.jacket);
            limb(s, joint, 0, 0.38f, -0.146f, 0.16f, 0.46f, 0.01f, p.suit ? 0xFFE8E4DC : p.shirt);
        } else {
            limb(s, joint, 0, 0.31f, 0, 0.48f, 0.62f, 0.27f, p.shirt);
        }
        if (p.vest) {
            limb(s, joint, 0, 0.29f, -0.005f, 0.5f, 0.5f, 0.28f, 0xFF141418);
            limb(s, joint, 0, 0.5f, -0.005f, 0.16f, 0.22f, 0.285f, p.shirt);
        }
        if (p.tie) {
            if (p.jacket == 0) limb(s, joint, 0, 0.43f, -0.13f, 0.12f, 0.36f, 0.02f, 0xFFE8E4DC);
            limb(s, joint, 0, 0.42f, -0.153f, 0.04f, 0.32f, 0.01f, 0xFF7A1A1A);
        }
        if (p.chain > 0) {
            // A chain around the neck with a pendant; bigger and brighter the pricier it is.
            float th = p.chain == 1 ? 0.025f : 0.04f;
            float g = p.chain == 3 ? 0.75f : 0.35f;
            for (int side = -1; side <= 1; side += 2) {
                copy(tmp, joint);
                Mat.translate(tmp, side * 0.07f, 0.54f, -0.152f);
                Mat.rotZ(tmp, side * 0.55f);
                Mat.scale(tmp, th, 0.2f, 0.012f);
                s.part(tmp, p.chainColor, g);
            }
            copy(tmp, joint);
            Mat.translate(tmp, 0, 0.43f, -0.156f);
            Mat.rotZ(tmp, PI / 4);
            float pd = p.chain == 1 ? 0.05f : 0.08f;
            Mat.scale(tmp, pd, pd, 0.014f);
            s.part(tmp, p.chainColor, g + 0.1f);
        }
        if (p.outfit == COOK) {
            limb(s, joint, 0, 0.2f, -0.14f, 0.4f, 0.5f, 0.01f, 0xFFFFFFFF);
        }
        CasinoWeapons.Gun held = p == player ? weapon : null;
        boolean gun = held != null && !held.melee && aiming;
        boolean guardGun = (p.kind == GUARD && alarm && armedThreat
                || p.kind == COP && (traffic.stars() >= 2 || aiming)) && !p.ko && !p.dead;
        if (guardGun) gun = true;
        for (int side = -1; side <= 1; side += 2) {
            copy(shoulder, joint);
            Mat.translate(shoulder, 0.315f * side, 0.59f, 0);
            float a = -swing * side * 0.85f;
            float elbow = 0.25f * p.walk;
            if (sit) {
                a = 0.75f;
                elbow = 0.6f;
            }
            if (p == player && dragging != null) {
                a = -0.4f;
                elbow = 0.2f;
            }
            if (p.fall > 0) {
                a = 2.6f * p.fall;
                elbow = 0.2f;
            }
            if (p.punch > 0) {
                float t = 1 - p.punch / PUNCH_TIME;
                if (side == 1) {
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
            if (side == 1 && p.fall <= 0 && !gun) {
                if (p.sip > 0 && sit) {
                    // Glass up to the mouth.
                    float k = Math.min(1, Math.min(p.sip, 1.4f - p.sip) * 4);
                    a = 0.75f + (0.55f - 0.75f) * k;
                    elbow = 0.6f + (2.35f - 0.6f) * k;
                } else if (p.patrons != null && p.serveState == 0 && p.serveT > 0) {
                    a = 1.15f;
                    elbow = 0.55f + 0.25f * (float) Math.sin(p.serveT * 3);
                } else if (p.patrons != null && p.serveState == 1 && p.serveT > 0) {
                    a = 1.35f;
                    elbow = 0.15f;
                } else if (p.carry != 0 && !sit) {
                    a = 0.55f;
                    elbow = 1.25f;
                }
            }
            if (!gun && p.fall <= 0 && !sit && fightArm(p, side, armOut)) {
                a = armOut[0];
                elbow = armOut[1];
                armRoll = armOut[2];
            } else {
                armRoll = Float.NaN;
            }
            if (gun) {
                float ap = p == player ? aimPitch : 0;
                boolean two = held != null && held.twoHand;
                if (side == 1) {
                    a = 1.57f - ap;
                    elbow = 0;
                } else if (two || p.kind == GUARD) {
                    a = 1.45f - ap;
                    elbow = 0.35f;
                }
            }
            Mat.rotX(shoulder, a);
            Mat.rotZ(shoulder, !Float.isNaN(armRoll) && !gun ? armRoll
                    : (gun && side == -1 ? -0.55f : 0.06f) * side);
            int sleeve = p.jacket != 0 ? p.jacket : p.shirt;
            limb(s, shoulder, 0, -0.16f, 0, 0.13f, 0.34f, 0.14f, sleeve);
            Mat.translate(shoulder, 0, -0.32f, 0);
            Mat.rotX(shoulder, elbow);
            limb(s, shoulder, 0, -0.14f, 0, 0.12f, 0.3f, 0.13f,
                    p.jacket != 0 ? p.jacket : (p.tie || p.outfit == COOK ? p.shirt : p.skin));
            limb(s, shoulder, 0, -0.32f, 0, 0.12f, 0.1f, 0.12f, p.skin);
            if (telegraph(p) && p.act < CasinoFight.KICK
                    && side == (p.act == CasinoFight.JAB ? -1 : 1)) {
                copy(tmp, shoulder);
                Mat.translate(tmp, 0, -0.33f, 0);
                Mat.scale(tmp, 0.17f, 0.15f, 0.17f);
                s.part(tmp, 0xFFFFE070, 1);
            }
            if (held != null && held.melee && side == 1) {
                limb(s, shoulder, 0, -0.43f, -0.03f, 0.03f, 0.16f, 0.05f, 0xFFC8CCD4);
            }
            if (p.carry != 0 && side == 1 && !gun) {
                // The glass in hand.
                copy(tmp, shoulder);
                Mat.translate(tmp, 0, -0.4f, -0.08f);
                Mat.rotX(tmp, -(a + elbow));
                Mat.scale(tmp, 0.085f, 0.14f, 0.085f);
                s.part(tmp, p.carry, 0.25f);
            }
            if (p.watch != 0 && side == -1) {
                copy(tmp, shoulder);
                Mat.translate(tmp, 0, -0.27f, 0);
                Mat.scale(tmp, 0.135f, 0.05f, 0.145f);
                s.part(tmp, p.watch, 0.45f);
            }
            if (gun && side == 1) {
                float L = held != null ? held.length : 0.22f;
                limb(s, shoulder, 0, -0.34f - L / 2, -0.05f, 0.06f, L, 0.1f, 0xFF1A1A1E);
                if (held != null && held.twoHand) {
                    limb(s, shoulder, 0, -0.3f, -0.08f, 0.07f, 0.24f, 0.16f, 0xFF3A2418);
                }
                if (held == CasinoWeapons.PISTOL && silencer) {
                    limb(s, shoulder, 0, -0.34f - L - 0.08f, -0.05f, 0.05f, 0.16f, 0.05f,
                            0xFF2A2A2E);
                }
                if (held != null && time - lastShotDraw < 0.05f) {
                    copy(tmp, shoulder);
                    Mat.translate(tmp, 0, -0.36f - L - 0.06f, -0.05f);
                    Mat.scale(tmp, 0.12f, 0.12f, 0.12f);
                    s.part(tmp, 0xFFFFD27A, 1);
                }
            }
        }
        if (held != null && !held.concealable && !aiming) {
            copy(tmp, joint);
            Mat.translate(tmp, 0, 0.35f, 0.17f);
            Mat.rotZ(tmp, 0.7f);
            Mat.scale(tmp, 0.07f, held.length + 0.25f, 0.08f);
            s.part(tmp, 0xFF1A1A1E, 0);
        }
        limb(s, joint, 0, 0.66f, 0, 0.12f, 0.08f, 0.12f, p.skin);
        limb(s, joint, 0, 0.84f, 0, 0.28f, 0.3f, 0.28f, p.skin);
        limb(s, joint, 0, 1.0f, 0.02f, 0.3f, 0.06f, 0.3f, p.hair);
        limb(s, joint, 0, 0.9f, 0.13f, 0.3f, 0.16f, 0.04f, p.hair);
        int ht = p.hat ? CasinoShop.FEDORA : p.hatType;
        int hc = p.hatColor != 0 ? p.hatColor : 0xFF3A2A1A;
        if (ht == CasinoShop.FEDORA) {
            limb(s, joint, 0, 1.03f, 0.0f, 0.42f, 0.03f, 0.42f, hc);
            limb(s, joint, 0, 1.1f, 0.0f, 0.28f, 0.13f, 0.28f, hc);
            limb(s, joint, 0, 1.06f, 0.0f, 0.285f, 0.03f, 0.285f, 0xFF111111);
        } else if (ht == CasinoShop.CAP) {
            limb(s, joint, 0, 1.06f, 0.01f, 0.31f, 0.1f, 0.31f, hc);
            limb(s, joint, 0, 1.02f, -0.22f, 0.26f, 0.025f, 0.18f, hc);
        } else if (ht == CasinoShop.BEANIE) {
            limb(s, joint, 0, 1.06f, 0.01f, 0.31f, 0.14f, 0.31f, hc);
        } else if (ht == CasinoShop.COWBOY) {
            limb(s, joint, 0, 1.03f, 0.0f, 0.6f, 0.03f, 0.52f, hc);
            limb(s, joint, 0, 1.12f, 0.0f, 0.28f, 0.16f, 0.3f, hc);
        } else if (ht == CasinoShop.CROWN) {
            limb(s, joint, 0, 1.06f, 0.0f, 0.32f, 0.08f, 0.32f, hc);
            for (int k = 0; k < 4; k++) {
                copy(tmp, joint);
                Mat.translate(tmp, 0, 1.13f, 0);
                Mat.rotY(tmp, k * PI / 2);
                Mat.translate(tmp, 0, 0, 0.14f);
                Mat.scale(tmp, 0.08f, 0.1f, 0.03f);
                s.part(tmp, hc, 0.5f);
            }
            limb(s, joint, 0, 1.07f, -0.163f, 0.05f, 0.05f, 0.01f, 0xFFE0283A);
        }
        if (p.glasses != 0) {
            limb(s, joint, 0, 0.855f, -0.148f, 0.27f, 0.07f, 0.02f, 0xFF0A0A0E);
            if (p.glasses != 0xFF0A0A0C) {
                limb(s, joint, 0, 0.892f, -0.15f, 0.28f, 0.015f, 0.02f, p.glasses);
            }
        }
        if (p.outfit == COOK) limb(s, joint, 0, 1.1f, 0, 0.26f, 0.16f, 0.26f, 0xFFFFFFFF);
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
        s.part(tmp, color, 0);
    }

    static float headY(Person p) {
        if (p.fall > 0.3f) return 0.4f;
        return (p.sitting ? 1.62f : 1.84f) * p.scale + 0.25f;
    }

    // ================================================================== helpers

    private static void copy(float[] out, float[] in) {
        System.arraycopy(in, 0, out, 0, 16);
    }

    static float turn(float from, float to, float k) {
        float d = to - from;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return from + d * Math.min(1, k);
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    static float dist(Person a, Person b) {
        return (float) Math.hypot(a.x - b.x, a.z - b.z);
    }

    static float dist2(float ax, float az, float bx, float bz) {
        return (ax - bx) * (ax - bx) + (az - bz) * (az - bz);
    }
}
