package app.easywake;

import static app.easywake.CasinoWorld.EAST;
import static app.easywake.CasinoWorld.NORTH;
import static app.easywake.CasinoWorld.SOUTH;
import static app.easywake.CasinoWorld.T;
import static app.easywake.CasinoWorld.WEST;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The city around the casino: a grid of two-lane roads, blocks of buildings with lit
 * windows, street lamps, the police HQ and the spray shop. The casino block in the middle
 * is built by CasinoWorld and CasinoPlaces.
 */
final class CasinoCity {
    private CasinoCity() {}

    /** Avenue centres (north-south roads) and street centres (east-west roads). */
    static final float[] AVE_X = {-92, -54, 54, 92};
    static final float[] ST_Z = {-62, -36, 22, 60};
    static final float HALF = 4, LANE = 2, WALK = 3;
    static final String[] AVE_NAMES = {"West Ring Road", "West Avenue", "East Avenue",
            "East Ring Road"};
    static final String[] ST_NAMES = {"North Ring Road", "Back Street", "The Strip",
            "Harbor Road"};

    static final float X_MIN = AVE_X[0] - HALF, X_MAX = AVE_X[3] + HALF;
    static final float Z_MIN = ST_Z[0] - HALF, Z_MAX = ST_Z[3] + HALF;

    /** Spray shop paint booth (drive in to lose the cops): x0, z0, x1, z1. */
    static final float[] SPRAY = {-74, 31, -66, 39};
    /** Where you're released after an arrest. */
    static final float BUSTED_X = 61.5f, BUSTED_Z = -15;

    static final int ASPHALT = 0xFF1A1A1E, CONCRETE = 0xFF4A4743, LINE = 0xFFB89A30;

    /** Ground pieces for the map: x0, z0, x1, z1, color. */
    static final List<float[]> GROUND = new ArrayList<>();

    static boolean onRoad(float x, float z) {
        if (x < X_MIN || x > X_MAX || z < Z_MIN || z > Z_MAX) return false;
        for (float ax : AVE_X) if (Math.abs(x - ax) < HALF) return true;
        for (float sz : ST_Z) if (Math.abs(z - sz) < HALF) return true;
        return false;
    }

    /** The road a point is on, by name, or null. */
    static String roadName(float x, float z) {
        if (x < X_MIN || x > X_MAX || z < Z_MIN || z > Z_MAX) return null;
        for (int i = 0; i < ST_Z.length; i++) {
            if (Math.abs(z - ST_Z[i]) < HALF + WALK) return ST_NAMES[i];
        }
        for (int i = 0; i < AVE_X.length; i++) {
            if (Math.abs(x - AVE_X[i]) < HALF + WALK) return AVE_NAMES[i];
        }
        return null;
    }

    static void build(CasinoWorld w) {
        GROUND.clear();
        CasinoWorld.Mesh m = w.mesh;
        // A base under everything so no gap ever shows the void.
        m.floor(CasinoWorld.WX0, CasinoWorld.WZ0, CasinoWorld.WX1, CasinoWorld.WZ1, -0.15f,
                0xFF242220, 0, 1);
        roads(w);
        Random r = new Random(77);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float x0 = AVE_X[i] + HALF, x1 = AVE_X[i + 1] - HALF;
                float z0 = ST_Z[j] + HALF, z1 = ST_Z[j + 1] - HALF;
                if (i == 1 && j == 1) {
                    casinoBlockEdges(w);
                    continue;
                }
                if (i == 1 && j == 2) {
                    northBlock(w, r);
                    continue;
                }
                ground(m, x0, z0, x1, z1, CONCRETE);
                if (i == 2 && j == 1) {
                    police(w, x0, z0, x1, z1);
                } else if (i == 0 && j == 2) {
                    sprayShop(w, x0, z0, x1, z1, r);
                } else if (i == 2 && j == 2) {
                    park(w, x0, z0, x1, z1);
                } else if (i == 0 && j == 1) {
                    gasStation(w, x0, z0, x1, z1, r);
                } else if (i == 1 && j == 0) {
                    shopRow(w, x0, z0, x1, z1, r);
                } else if (i == 0 && j == 0) {
                    lockUps(w, x0, z0, x1, z1, r);
                } else {
                    buildings(w, r, x0 + WALK, z0 + WALK, x1 - WALK, z1 - WALK);
                }
            }
        }
        skyline(w, r);
        lamps(w);
        streetFurniture(w);
    }

    private static void ground(CasinoWorld.Mesh m, float x0, float z0, float x1, float z1,
                               int color) {
        m.floor(x0, z0, x1, z1, 0, color, 0, 1);
        GROUND.add(new float[]{x0, z0, x1, z1, color});
    }

    // ---------------------------------------------------------------- roads

    private static void roads(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        for (int j = 0; j < ST_Z.length; j++) {
            float z = ST_Z[j];
            GROUND.add(new float[]{X_MIN, z - HALF, X_MAX, z + HALF, ASPHALT});
            if (j == 2) {
                // The Strip in front of the casino is already built from x -48 to 48.
                m.floor(X_MIN, z - HALF, -48, z + HALF, 0, ASPHALT, 0, 1);
                m.floor(48, z - HALF, X_MAX, z + HALF, 0, ASPHALT, 0, 1);
                m.floor(X_MIN, 14, -48, z - HALF, 0, CONCRETE, 0, 1);
                m.floor(48, 14, X_MAX, z - HALF, 0, CONCRETE, 0, 1);
                m.floor(X_MIN, z + HALF, -48, 30, 0, CONCRETE, 0, 1);
                m.floor(48, z + HALF, X_MAX, 30, 0, CONCRETE, 0, 1);
                dashes(m, X_MIN, -48, z, true);
                dashes(m, 48, X_MAX, z, true);
            } else {
                m.floor(X_MIN, z - HALF, X_MAX, z + HALF, 0, ASPHALT, 0, 1);
                dashes(m, X_MIN, X_MAX, z, true);
            }
        }
        for (int i = 0; i < AVE_X.length; i++) {
            float x = AVE_X[i];
            GROUND.add(new float[]{x - HALF, Z_MIN, x + HALF, Z_MAX, ASPHALT});
            for (int j = 0; j + 1 < ST_Z.length; j++) {
                float z0 = ST_Z[j] + HALF, z1 = ST_Z[j + 1] - HALF;
                m.floor(x - HALF, z0, x + HALF, z1, 0, ASPHALT, 0, 1);
                dashes(m, z0, z1, x, false);
            }
        }
        // Crosswalk stripes at every intersection.
        for (float ax : AVE_X) {
            for (float sz : ST_Z) {
                for (int k = -1; k <= 1; k += 2) {
                    for (float t = -HALF + 0.4f; t < HALF - 0.4f; t += 0.9f) {
                        m.floor(ax + t, sz + k * (HALF + 0.4f) - 0.6f, ax + t + 0.45f,
                                sz + k * (HALF + 0.4f) + 0.6f, 0.012f, 0xFFB8B8B0, 0, 1);
                    }
                }
            }
        }
    }

    private static void dashes(CasinoWorld.Mesh m, float a, float b, float c, boolean alongX) {
        for (float t = a + 1; t < b - 1.6f; t += 3.2f) {
            if (alongX) m.floor(t, c - 0.06f, t + 1.6f, c + 0.06f, 0.012f, LINE, 0, 1);
            else m.floor(c - 0.06f, t, c + 0.06f, t + 1.6f, 0.012f, LINE, 0, 1);
        }
    }

    private static void lamps(CasinoWorld w) {
        for (int j = 0; j < ST_Z.length; j++) {
            for (float x = X_MIN + 6; x < X_MAX - 4; x += 16) {
                if (j == 2 && x > -50 && x < 50) continue;
                if (clearOfAvenues(x)) lamp(w, x, ST_Z[j] - HALF - 0.6f, 1);
                if (clearOfAvenues(x + 8)) lamp(w, x + 8, ST_Z[j] + HALF + 0.6f, -1);
            }
        }
        for (float ax : AVE_X) {
            for (int j = 0; j + 1 < ST_Z.length; j++) {
                for (float z = ST_Z[j] + 10; z < ST_Z[j + 1] - 8; z += 16) {
                    lampZ(w, ax - HALF - 0.6f, z, 1);
                    lampZ(w, ax + HALF + 0.6f, z + 8, -1);
                }
            }
        }
    }

    /** Not in an avenue, its crosswalk or the corner where you'd step off the curb. */
    private static boolean clearOfAvenues(float x) {
        for (float ax : AVE_X) {
            if (Math.abs(x - ax) < HALF + WALK + 0.5f) return false;
        }
        return true;
    }

    /** A street lamp whose arm reaches over the road along z. */
    private static void lamp(CasinoWorld w, float x, float z, float dir) {
        CasinoWorld.Mesh m = w.mesh;
        m.cylinder(x, z, 0.08f, 0.08f, 0, 5.4f, 0xFF2A2C30, 0xFF2A2C30, 0, 6);
        m.boxAA(x - 0.04f, 5.3f, Math.min(z, z + dir * 1.2f), x + 0.04f, 5.38f,
                Math.max(z, z + dir * 1.2f), 0xFF2A2C30, 0);
        m.boxAA(x - 0.18f, 5.08f, z + dir * 1.2f - 0.26f, x + 0.18f, 5.12f,
                z + dir * 1.2f + 0.26f, 0xFFFFC27A, 1);
        w.solid(x - 0.12f, z - 0.12f, x + 0.12f, z + 0.12f, 5.4f);
        w.lamps.add(new float[]{x, z + dir * 1.2f, 5.5f, 0.85f, 1.0f, 0.66f, 0.34f});
    }

    private static void lampZ(CasinoWorld w, float x, float z, float dir) {
        CasinoWorld.Mesh m = w.mesh;
        m.cylinder(x, z, 0.08f, 0.08f, 0, 5.4f, 0xFF2A2C30, 0xFF2A2C30, 0, 6);
        m.boxAA(Math.min(x, x + dir * 1.2f), 5.3f, z - 0.04f, Math.max(x, x + dir * 1.2f), 5.38f,
                z + 0.04f, 0xFF2A2C30, 0);
        m.boxAA(x + dir * 1.2f - 0.26f, 5.08f, z - 0.18f, x + dir * 1.2f + 0.26f, 5.12f,
                z + 0.18f, 0xFFFFC27A, 1);
        w.solid(x - 0.12f, z - 0.12f, x + 0.12f, z + 0.12f, 5.4f);
        w.lamps.add(new float[]{x + dir * 1.2f, z, 5.5f, 0.85f, 1.0f, 0.66f, 0.34f});
    }

    // ---------------------------------------------------------------- blocks

    private static final int[] FACADES = {0xFF3A3430, 0xFF2E3338, 0xFF443A30, 0xFF33302E,
            0xFF3B3540, 0xFF2C2A28, 0xFF4A4038, 0xFF2A3230};

    /** Fills a rectangle with a row or two of buildings. */
    static void buildings(CasinoWorld w, Random r, float x0, float z0, float x1, float z1) {
        float depth = z1 - z0;
        boolean twoRows = depth > 22;
        float[][] rows = twoRows ? new float[][]{{z0, (z0 + z1) / 2 - 0.5f},
                {(z0 + z1) / 2 + 0.5f, z1}} : new float[][]{{z0, z1}};
        for (float[] row : rows) {
            float x = x0;
            while (x < x1 - 4) {
                float wd = Math.min(x1 - x, 9 + r.nextFloat() * 12);
                if (x1 - (x + wd) < 5) wd = x1 - x;
                float h = 6 + r.nextFloat() * 16;
                int col = FACADES[r.nextInt(FACADES.length)];
                tower(w, r, x, row[0], x + wd - 0.4f, row[1], h, col);
                x += wd;
            }
        }
    }

    /** One building: a box with lit windows on every side and a roof ledge. */
    static void tower(CasinoWorld w, Random r, float x0, float z0, float x1, float z1, float h,
                      int col) {
        CasinoWorld.Mesh m = w.mesh;
        m.boxAA(x0, 0, z0, x1, h, z1, col, 0);
        m.boxAA(x0 - 0.15f, h, z0 - 0.15f, x1 + 0.15f, h + 0.35f, z1 + 0.15f,
                CasinoWorld.shade(col, 0.8f), 0);
        w.solid(x0, z0, x1, z1, h);
        windows(m, r, x0, x1, z0 - 0.04f, NORTH, h, true);
        windows(m, r, x0, x1, z1 + 0.04f, SOUTH, h, true);
        windows(m, r, z0, z1, x0 - 0.04f, WEST, h, false);
        windows(m, r, z0, z1, x1 + 0.04f, EAST, h, false);
        // A street-level door on the long face.
        m.panel((x0 + x1) / 2, z1 + 0.03f, SOUTH, 1.4f, 0, 2.4f, 0xFF15110E, 0, null);
    }

    private static void windows(CasinoWorld.Mesh m, Random r, float a0, float a1, float c,
                                float yaw, float h, boolean alongX) {
        for (float a = a0 + 1.2f; a < a1 - 1.2f; a += 2.4f) {
            for (float y = 3.0f; y < h - 1.2f; y += 3.0f) {
                float roll = r.nextFloat();
                if (roll < 0.42f) continue;
                int color = roll < 0.75f ? 0xFFFFD08A : (roll < 0.92f ? 0xFF8AA8D8 : 0xFFE8E0C8);
                float cx = alongX ? a + 0.6f : c, cz = alongX ? c : a + 0.6f;
                m.panel(cx, cz, yaw, 1.1f, y, y + 1.4f, color, 0.55f, null);
            }
        }
    }

    /** Sidewalk strips and back lots around the casino block. */
    private static void casinoBlockEdges(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        Random r = new Random(5);
        ground(m, -50, -32, -48, 18, CONCRETE);
        ground(m, 48, -32, 50, 18, CONCRETE);
        ground(m, -48, -32, 48, -28, CONCRETE);
        ground(m, -48, -28, -18 - T / 2, -14, 0xFF2A2826);
        tower(w, r, -47.5f, -27.6f, -20.5f, -16.5f, 9, 0xFF33302E);
        tower(w, r, 26.3f, -27.6f, 47.6f, -6.4f, 11, 0xFF2E3338);
    }

    /** Behind the pawn shop, diner, motel and range: one row of buildings. */
    private static void northBlock(CasinoWorld w, Random r) {
        CasinoWorld.Mesh m = w.mesh;
        ground(m, -50, 30, -48, 56, CONCRETE);
        ground(m, 48, 30, 50, 56, CONCRETE);
        ground(m, -48, 42 + T / 2, 48, 56, CONCRETE);
        buildings(w, r, -47.6f, 42.4f, 47.6f, 53);
    }

    // ---------------------------------------------------------------- landmarks

    /** Founders Park: lawns, paths crossing at a fountain, trees, benches and park lights. */
    private static void park(CasinoWorld w, float x0, float z0, float x1, float z1) {
        CasinoWorld.Mesh m = w.mesh;
        float gx0 = x0 + WALK, gz0 = z0 + WALK, gx1 = x1 - WALK, gz1 = z1 - WALK;
        float cx = (gx0 + gx1) / 2, cz = (gz0 + gz1) / 2;
        m.floor(gx0, gz0, gx1, gz1, 0.012f, 0xFF2E5A2A, 0, 1);
        GROUND.add(new float[]{gx0, gz0, gx1, gz1, 0xFF2E5A2A});
        // Low hedge border with gaps where the paths come in.
        int hedge = 0xFF1E4A22;
        float g = 1.4f;
        m.boxAA(gx0, 0, gz0, cx - g, 0.7f, gz0 + 0.5f, hedge, 0);
        m.boxAA(cx + g, 0, gz0, gx1, 0.7f, gz0 + 0.5f, hedge, 0);
        m.boxAA(gx0, 0, gz1 - 0.5f, cx - g, 0.7f, gz1, hedge, 0);
        m.boxAA(cx + g, 0, gz1 - 0.5f, gx1, 0.7f, gz1, hedge, 0);
        m.boxAA(gx0, 0, gz0 + 0.5f, gx0 + 0.5f, 0.7f, cz - g, hedge, 0);
        m.boxAA(gx0, 0, cz + g, gx0 + 0.5f, 0.7f, gz1 - 0.5f, hedge, 0);
        m.boxAA(gx1 - 0.5f, 0, gz0 + 0.5f, gx1, 0.7f, cz - g, hedge, 0);
        m.boxAA(gx1 - 0.5f, 0, cz + g, gx1, 0.7f, gz1 - 0.5f, hedge, 0);
        w.solid(gx0, gz0, cx - g, gz0 + 0.5f, 0.7f);
        w.solid(cx + g, gz0, gx1, gz0 + 0.5f, 0.7f);
        w.solid(gx0, gz1 - 0.5f, cx - g, gz1, 0.7f);
        w.solid(cx + g, gz1 - 0.5f, gx1, gz1, 0.7f);
        w.solid(gx0, gz0 + 0.5f, gx0 + 0.5f, cz - g, 0.7f);
        w.solid(gx0, cz + g, gx0 + 0.5f, gz1 - 0.5f, 0.7f);
        w.solid(gx1 - 0.5f, gz0 + 0.5f, gx1, cz - g, 0.7f);
        w.solid(gx1 - 0.5f, cz + g, gx1, gz1 - 0.5f, 0.7f);
        // Paths: a cross and a ring round the fountain.
        int path = 0xFF8A7E68;
        m.floor(cx - 1.2f, gz0, cx + 1.2f, gz1, 0.024f, path, 0, 1);
        m.floor(gx0, cz - 1.2f, gx1, cz + 1.2f, 0.024f, path, 0, 1);
        m.floor(cx - 5.5f, cz - 5.5f, cx + 5.5f, cz + 5.5f, 0.036f, path, 0, 1);
        // The fountain: a wide basin, water, and two tiers spilling over.
        m.cylinder(cx, cz, 3.6f, 3.6f, 0, 0.6f, 0xFF8A8478, 0xFF9A9488, 0, 24);
        m.cylinder(cx, cz, 3.25f, 3.25f, 0.6f, 0.62f, 0xFF2A6AA8, 0xFF3A8AD8, 0.35f, 24);
        m.cylinder(cx, cz, 0.35f, 0.35f, 0.6f, 1.9f, 0xFF9A9488, 0xFF9A9488, 0, 10);
        m.cylinder(cx, cz, 1.5f, 1.5f, 1.6f, 1.85f, 0xFF8A8478, 0xFF3A8AD8, 0.3f, 16);
        m.cylinder(cx, cz, 0.18f, 0.18f, 1.85f, 2.8f, 0xFF9A9488, 0xFF9A9488, 0, 8);
        m.cylinder(cx, cz, 0.7f, 0.7f, 2.6f, 2.75f, 0xFF8A8478, 0xFF3A8AD8, 0.3f, 12);
        m.cylinder(cx, cz, 0.12f, 0.06f, 2.75f, 3.4f, 0xFFB8E0FF, 0xFFB8E0FF, 0.6f, 8);
        w.solid(cx - 3.4f, cz - 3.4f, cx + 3.4f, cz + 3.4f, 0.62f);
        w.lamps.add(new float[]{cx, cz, 1.2f, 0.3f, 0.5f, 0.75f, 1f});
        // Trees on the lawns, clear of the paths.
        Random r = new Random(404);
        int planted = 0;
        for (int k = 0; k < 80 && planted < 18; k++) {
            float tx = gx0 + 2 + r.nextFloat() * (gx1 - gx0 - 4);
            float tz = gz0 + 2 + r.nextFloat() * (gz1 - gz0 - 4);
            if (Math.abs(tx - cx) < 3 || Math.abs(tz - cz) < 3) continue;
            if (Math.hypot(tx - cx, tz - cz) < 8) continue;
            tree(w, tx, tz, 0.8f + r.nextFloat() * 0.5f, false);
            planted++;
        }
        // Benches facing the fountain, and lights along the paths.
        bench(w, cx, cz - 6.6f, true);
        bench(w, cx, cz + 6.6f, true);
        bench(w, cx - 6.6f, cz, false);
        bench(w, cx + 6.6f, cz, false);
        for (float k : new float[]{-1, 1}) {
            parkLight(w, cx + 1.8f, cz + k * 9);
            parkLight(w, cx - 1.8f, cz + k * 9);
            parkLight(w, cx + k * 9, cz + 1.8f);
            parkLight(w, cx + k * 9, cz - 1.8f);
        }
        // The sign at the south gate, facing Harbor Road.
        m.boxAA(cx - 2.4f, 0, gz1 + 0.2f, cx + 2.4f, 0.4f, gz1 + 0.6f, 0xFF5A5448, 0);
        m.boxAA(cx - 2.3f, 0.4f, gz1 + 0.35f, cx + 2.3f, 1.4f, gz1 + 0.45f, 0xFF0A0A0E, 0);
        m.panel(cx, gz1 + 0.47f, SOUTH, 4.4f, 0.45f, 1.35f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.PARK));
        w.solid(cx - 2.4f, gz1 + 0.2f, cx + 2.4f, gz1 + 0.6f, 1.4f);
    }

    private static void parkLight(CasinoWorld w, float x, float z) {
        CasinoWorld.Mesh m = w.mesh;
        m.cylinder(x, z, 0.06f, 0.06f, 0, 3.0f, 0xFF1A1A1E, 0xFF1A1A1E, 0, 6);
        m.boxAA(x - 0.2f, 3.0f, z - 0.2f, x + 0.2f, 3.4f, z + 0.2f, 0xFFFFE6B0, 1);
        m.boxAA(x - 0.24f, 3.4f, z - 0.24f, x + 0.24f, 3.48f, z + 0.24f, 0xFF1A1A1E, 0);
        w.lamps.add(new float[]{x, z, 3.2f, 0.45f, 1.0f, 0.85f, 0.6f});
        w.solid(x - 0.08f, z - 0.08f, x + 0.08f, z + 0.08f, 3.4f);
    }

    /** A tree: trunk and a stack of leafy blocks. In a sidewalk planter if asked. */
    static void tree(CasinoWorld w, float x, float z, float size, boolean planter) {
        CasinoWorld.Mesh m = w.mesh;
        if (planter) {
            m.boxAA(x - 0.5f, 0, z - 0.5f, x + 0.5f, 0.38f, z + 0.5f, 0xFF5A5650, 0);
            m.boxAA(x - 0.42f, 0.38f, z - 0.42f, x + 0.42f, 0.4f, z + 0.42f, 0xFF2A1E14, 0);
            w.solid(x - 0.5f, z - 0.5f, x + 0.5f, z + 0.5f, 0.4f);
        } else {
            w.solid(x - 0.15f, z - 0.15f, x + 0.15f, z + 0.15f, 3);
        }
        float s = size;
        m.boxAA(x - 0.11f * s, 0, z - 0.11f * s, x + 0.11f * s, 2.3f * s, z + 0.11f * s,
                0xFF4A3220, 0);
        int[] greens = {0xFF2A5A26, 0xFF336A2C, 0xFF24502A};
        m.boxAA(x - 1.0f * s, 1.9f * s, z - 1.0f * s, x + 1.0f * s, 3.0f * s, z + 1.0f * s,
                greens[0], 0);
        m.boxAA(x - 0.75f * s, 3.0f * s, z - 0.8f * s, x + 0.8f * s, 3.7f * s, z + 0.75f * s,
                greens[1], 0);
        m.boxAA(x - 0.45f * s, 3.7f * s, z - 0.4f * s, x + 0.4f * s, 4.2f * s, z + 0.45f * s,
                greens[2], 0);
    }

    /** A wooden bench; alongX means the seat runs east-west. */
    static void bench(CasinoWorld w, float x, float z, boolean alongX) {
        CasinoWorld.Mesh m = w.mesh;
        float hl = 0.8f, hd = 0.22f;
        float ax = alongX ? hl : hd, az = alongX ? hd : hl;
        int wood = 0xFF6A4628, iron = 0xFF1A1A1E;
        m.boxAA(x - ax, 0.42f, z - az, x + ax, 0.48f, z + az, wood, 0);
        if (alongX) {
            m.boxAA(x - ax, 0.48f, z + az - 0.06f, x + ax, 0.95f, z + az, wood, 0);
            m.boxAA(x - ax + 0.05f, 0, z - az, x - ax + 0.1f, 0.42f, z + az, iron, 0);
            m.boxAA(x + ax - 0.1f, 0, z - az, x + ax - 0.05f, 0.42f, z + az, iron, 0);
        } else {
            m.boxAA(x + ax - 0.06f, 0.48f, z - az, x + ax, 0.95f, z + az, wood, 0);
            m.boxAA(x - ax, 0, z - az + 0.05f, x + ax, 0.42f, z - az + 0.1f, iron, 0);
            m.boxAA(x - ax, 0, z + az - 0.1f, x + ax, 0.42f, z + az - 0.05f, iron, 0);
        }
        w.solid(x - ax, z - az, x + ax, z + az, 0.5f);
    }

    /** Gas 'N Go facing The Strip, with buildings behind it. */
    private static void gasStation(CasinoWorld w, float x0, float z0, float x1, float z1,
                                   Random r) {
        CasinoWorld.Mesh m = w.mesh;
        buildings(w, r, x0 + WALK, z0 + WALK, x1 - WALK, -10);
        float lz0 = -6, lz1 = z1;
        m.floor(x0 + 1, lz0, x1 - 1, lz1, 0.012f, 0xFF232326, 0, 1);
        // The shop.
        float sx0 = x0 + 2, sx1 = x0 + 11, sz0 = lz0 + 0.5f, sz1 = lz0 + 8;
        m.boxAA(sx0, 0, sz0, sx1, 4, sz1, 0xFFD8D4CC, 0);
        m.boxAA(sx0 - 0.2f, 4, sz0 - 0.2f, sx1 + 0.2f, 4.4f, sz1 + 0.2f, 0xFFE03A2A, 0.2f);
        m.panel((sx0 + sx1) / 2, sz1 + 0.03f, SOUTH, sx1 - sx0 - 1.5f, 0.6f, 2.6f,
                0xFFBFE6FF, 0.5f, null);
        m.panel(sx0 + 1.6f, sz1 + 0.05f, SOUTH, 1.2f, 0, 2.3f, 0xFF8AB0C8, 0.4f, null);
        m.panel((sx0 + sx1) / 2, sz1 + 0.25f, SOUTH, 6, 2.9f, 3.9f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.GAS));
        w.solid(sx0, sz0, sx1, sz1, 4.4f);
        // The canopy over two pump islands.
        float cx0 = x0 + 14, cx1 = x1 - 3, cz0 = lz0 + 6, cz1 = lz1 - 4;
        m.boxAA(cx0, 4.6f, cz0, cx1, 5.2f, cz1, 0xFFF2F0EA, 0);
        m.boxAA(cx0 - 0.05f, 4.9f, cz0 - 0.05f, cx1 + 0.05f, 5.2f, cz1 + 0.05f, 0xFFE03A2A,
                0.3f);
        for (float px : new float[]{cx0 + 3, cx1 - 3}) {
            for (float pz : new float[]{cz0 + 2, cz1 - 2}) {
                m.boxAA(px - 0.2f, 0, pz - 0.2f, px + 0.2f, 4.6f, pz + 0.2f, 0xFFE8E6E0, 0);
                w.solid(px - 0.2f, pz - 0.2f, px + 0.2f, pz + 0.2f, 4.6f);
            }
            // Island with two pumps.
            m.boxAA(px - 0.6f, 0, cz0 + 3, px + 0.6f, 0.18f, cz1 - 3, 0xFF8A8680, 0);
            for (float pz : new float[]{cz0 + 4.2f, cz1 - 4.2f}) {
                m.boxAA(px - 0.35f, 0.18f, pz - 0.5f, px + 0.35f, 1.7f, pz + 0.5f, 0xFFE03A2A, 0);
                m.boxAA(px - 0.37f, 1.0f, pz - 0.3f, px + 0.37f, 1.5f, pz + 0.3f, 0xFF1A2A3A,
                        0.6f);
                m.boxAA(px - 0.36f, 1.7f, pz - 0.52f, px + 0.36f, 1.85f, pz + 0.52f,
                        0xFFF2F0EA, 0.3f);
            }
            w.solid(px - 0.6f, cz0 + 3, px + 0.6f, cz1 - 3, 1.85f);
        }
        // Lights under the canopy.
        for (float lx = cx0 + 2; lx < cx1 - 1; lx += 4) {
            for (float lz = cz0 + 2; lz < cz1 - 1; lz += 4) {
                m.boxAA(lx - 0.5f, 4.58f, lz - 0.5f, lx + 0.5f, 4.6f, lz + 0.5f, 0xFFFFFFFF, 1);
            }
        }
        w.lamps.add(new float[]{(cx0 + cx1) / 2, (cz0 + cz1) / 2, 4.5f, 0.9f, 1.0f, 1.0f, 0.95f});
        // The price board by the road.
        float bx = x1 - 2.5f, bz = lz1 - 1.2f;
        m.boxAA(bx - 0.1f, 0, bz - 0.1f, bx + 0.1f, 4, bz + 0.1f, 0xFF3A3A40, 0);
        m.boxAA(bx - 1.3f, 4, bz - 0.15f, bx + 1.3f, 5.4f, bz + 0.15f, 0xFF0A0A0E, 0);
        m.panel(bx, bz + 0.17f, SOUTH, 2.5f, 4.05f, 5.35f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.GAS));
        w.solid(bx - 0.1f, bz - 0.1f, bx + 0.1f, bz + 0.1f, 4);
        CasinoWorld.Spot snack = new CasinoWorld.Spot("Grab a snack (10 FC)", "snack", 10, null,
                sx0 + 1.6f, sz1 + 1.1f, NORTH);
        w.spots.add(snack);
    }

    /** The lock-up garages' bays: x0, z0, x1, z1 (cars parked inside are kept). */
    static final float[][] BAYS = {{-85.4f, -55.2f, -80.6f, -44.2f}, {-79.4f, -55.2f, -74.6f, -44.2f},
            {-73.4f, -55.2f, -68.6f, -44.2f}, {-67.4f, -55.2f, -62.6f, -44.2f}};

    /** Four garage bays facing Back Street, for sale. */
    private static void lockUps(CasinoWorld w, float x0, float z0, float x1, float z1, Random r) {
        CasinoWorld.Mesh m = w.mesh;
        float gx0 = -86, gx1 = -62, gz0 = -56, gz1 = -43.5f, h = 4.6f;
        int brick = 0xFF5A3A2E;
        // Back and end walls, the roof, and the dividers between bays.
        m.boxAA(gx0, 0, gz0, gx1, h, gz0 + 0.4f, brick, 0);
        w.solid(gx0, gz0, gx1, gz0 + 0.4f, h);
        m.boxAA(gx0, 0, gz0, gx0 + 0.4f, h, gz1, brick, 0);
        w.solid(gx0, gz0, gx0 + 0.4f, gz1, h);
        m.boxAA(gx1 - 0.4f, 0, gz0, gx1, h, gz1, brick, 0);
        w.solid(gx1 - 0.4f, gz0, gx1, gz1, h);
        m.boxAA(gx0 - 0.2f, h, gz0 - 0.2f, gx1 + 0.2f, h + 0.4f, gz1 + 0.3f, 0xFF3A2A22, 0);
        for (int k = 1; k < 4; k++) {
            float dx = gx0 + k * 6;
            m.boxAA(dx - 0.2f, 0, gz0, dx + 0.2f, h, gz1, brick, 0);
            w.solid(dx - 0.2f, gz0, dx + 0.2f, gz1, h);
        }
        m.floor(gx0 + 0.4f, gz0 + 0.4f, gx1 - 0.4f, gz1, 0.02f, 0xFF3A3A3E, 0, 1);
        w.regions.add(new float[]{gx0, gz0, gx1, gz1, h});
        for (int k = 0; k < 4; k++) {
            float bx = gx0 + k * 6 + 3;
            // Roll-up door, rolled up, and a number over each bay.
            m.boxAA(bx - 2.6f, 3.7f, gz1 - 0.3f, bx + 2.6f, h, gz1 + 0.05f, 0xFF8A8E94, 0);
            for (float y = 3.75f; y < h; y += 0.15f) {
                m.boxAA(bx - 2.6f, y, gz1 + 0.05f, bx + 2.6f, y + 0.02f, gz1 + 0.07f, 0xFF6A6E74, 0);
            }
            m.boxAA(bx - 0.3f, 3.2f, gz1 - 0.02f, bx + 0.3f, 3.6f, gz1 + 0.02f, 0xFFFFC84A, 0.5f);
            // A strip light inside, a workbench and some tires on the back wall.
            m.boxAA(bx - 1.2f, h - 0.08f, (gz0 + gz1) / 2 - 0.1f, bx + 1.2f, h - 0.04f,
                    (gz0 + gz1) / 2 + 0.1f, 0xFFF2F4FF, 1);
            w.lamps.add(new float[]{bx, (gz0 + gz1) / 2, h - 0.3f, 0.55f, 0.9f, 0.95f, 1f});
            m.boxAA(bx - 2.4f, 0, gz0 + 0.4f, bx - 0.8f, 0.9f, gz0 + 1.0f, 0xFF5A3A22, 0);
            m.cylinder(bx + 1.8f, gz0 + 0.8f, 0.36f, 0.36f, 0, 0.26f, 0xFF121214, 0xFF121214, 0, 10);
            m.cylinder(bx + 1.8f, gz0 + 0.8f, 0.36f, 0.36f, 0.26f, 0.52f, 0xFF121214, 0xFF121214, 0, 10);
        }
        // The sign over the front.
        m.boxAA(-80, h + 0.4f, gz1 + 0.1f, -68, h + 1.6f, gz1 + 0.3f, 0xFF0A0A0E, 0);
        m.panel(-74, gz1 + 0.32f, SOUTH, 11.6f, h + 0.45f, h + 1.55f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.GARAGE));
        // The forecourt.
        m.floor(gx0, gz1, gx1, z1, 0.012f, 0xFF26262A, 0, 1);
        buildings(w, r, gx1 + 1.5f, z0 + WALK, x1 - WALK, z1 - WALK);
        w.spots.add(new CasinoWorld.Spot("Lock-up garages", "garage", 0, null, -74, -41.6f,
                NORTH));
    }

    /** North of Back Street: Burger Barn, Pixel Palace arcade and Suds laundromat. */
    private static void shopRow(CasinoWorld w, float x0, float z0, float x1, float z1,
                                Random r) {
        CasinoWorld.Mesh m = w.mesh;
        float front = z1 - WALK;
        float back = front - 12;
        storefront(w, r, -46, back, -32, front, 0xFF7A2A1A, CasinoArt.BURGER, 0xFFFFD27A);
        storefront(w, r, -30, back, -14, front, 0xFF1A1A3A, CasinoArt.ARCADE, 0xFF8A6AFF);
        storefront(w, r, -12, back, -2, front, 0xFF2A4A5A, CasinoArt.LAUNDRY, 0xFFBFF0FF);
        buildings(w, r, 0, z0 + WALK, x1 - WALK, front);
        w.spots.add(new CasinoWorld.Spot("Eat a burger (25 FC)", "burger", 25, null, -39,
                front + 1.2f, NORTH));
        w.spots.add(new CasinoWorld.Spot("Play Block Drop at the arcade", "arcade", 0, null,
                -22, front + 1.2f, NORTH));
        // Booths and a counter seen through the burger joint's window.
        for (float bx = -44.5f; bx < -40; bx += 1.7f) {
            m.boxAA(bx, 0, front - 1.4f, bx + 1.2f, 0.75f, front - 0.6f, 0xFFC83A2A, 0);
        }
        m.boxAA(-38.5f, 0, front - 3.5f, -33, 1.1f, front - 2.9f, 0xFFE8E0D0, 0);
        // Arcade cabinets glowing in the window.
        int[] glow = {0xFFFF4AD5, 0xFF4AE0FF, 0xFFFFE04A, 0xFF6CFF8A, 0xFFFF6A4A};
        for (int i = 0; i < 5; i++) {
            float ax = -28.6f + i * 2.6f;
            m.boxAA(ax, 0, front - 1.6f, ax + 0.8f, 1.9f, front - 1.0f, 0xFF15151E, 0);
            m.boxAA(ax + 0.1f, 1.0f, front - 1.02f, ax + 0.7f, 1.6f, front - 0.99f, glow[i], 1);
        }
        // Washers in a row.
        for (float lx = -11.2f; lx < -3; lx += 1.1f) {
            m.boxAA(lx, 0, front - 1.2f, lx + 0.9f, 0.95f, front - 0.5f, 0xFFF2F2F2, 0);
            m.boxAA(lx + 0.2f, 0.3f, front - 0.49f, lx + 0.7f, 0.8f, front - 0.47f, 0xFF4A8AC8,
                    0.5f);
        }
    }

    /** A shop with a big lit window you can see into, an awning and a sign. */
    private static void storefront(CasinoWorld w, Random r, float x0, float z0, float x1,
                                   float z1, int col, int sign, int light) {
        CasinoWorld.Mesh m = w.mesh;
        float h = 7;
        // The building, hollow at the front so the window shows the shop.
        m.boxAA(x0, 0, z0, x1, h, z1 - 4, col, 0);
        m.boxAA(x0, 3.4f, z1 - 4, x1, h, z1, col, 0);
        m.boxAA(x0, 0, z1 - 4, x0 + 0.3f, 3.4f, z1, col, 0);
        m.boxAA(x1 - 0.3f, 0, z1 - 4, x1, 3.4f, z1, col, 0);
        m.floor(x0 + 0.3f, z1 - 4, x1 - 0.3f, z1, 0.03f, 0xFF3A3632, 0, 1);
        m.boxAA(x0 - 0.15f, h, z0 - 0.15f, x1 + 0.15f, h + 0.35f, z1 + 0.15f,
                CasinoWorld.shade(col, 0.8f), 0);
        // Window glass (glowing faintly) and a door.
        m.panel((x0 + x1) / 2 + 0.8f, z1 + 0.02f, SOUTH, x1 - x0 - 2.6f, 0.4f, 3.2f,
                CasinoWorld.shade(light, 0.5f), 0.55f, null);
        m.panel(x0 + 1.0f, z1 + 0.02f, SOUTH, 1.2f, 0, 2.5f, 0xFF15110E, 0, null);
        w.lamps.add(new float[]{(x0 + x1) / 2, z1 - 2, 3, 0.55f,
                ((light >> 16) & 255) / 255f, ((light >> 8) & 255) / 255f, (light & 255) / 255f});
        // Awning and sign.
        m.boxAA(x0 + 0.2f, 3.5f, z1, x1 - 0.2f, 3.6f, z1 + 1.2f, CasinoWorld.shade(col, 1.3f),
                0.1f);
        m.boxAA(x0 + 1, 4.2f, z1 - 0.05f, x1 - 1, 5.6f, z1 + 0.05f, 0xFF0A0A0E, 0);
        m.panel((x0 + x1) / 2, z1 + 0.07f, SOUTH, x1 - x0 - 2.2f, 4.25f, 5.55f, 0xFFFFFFFF, 1,
                CasinoArt.sign(sign));
        w.solid(x0, z0, x1, z1 - 0.1f, h);
        windows(m, r, x0, x1, z0 - 0.04f, NORTH, h, true);
    }

    /**
     * Trees, benches, bins, hydrants and newspaper boxes along the curbs, halfway between the
     * street lamps and clear of crossings and The Strip's shopfronts.
     */
    private static void streetFurniture(CasinoWorld w) {
        int n = 0;
        for (int j = 0; j < ST_Z.length; j++) {
            if (j == 2) continue;
            for (float x = X_MIN + 14; x < X_MAX - 4; x += 16) {
                if (clearOfAvenues(x)) furniture(w, x, ST_Z[j] - HALF - 0.55f, true, n++);
                if (clearOfAvenues(x - 8)) furniture(w, x - 8, ST_Z[j] + HALF + 0.55f, true,
                        n++);
            }
        }
        for (float ax : AVE_X) {
            for (int j = 0; j + 1 < ST_Z.length; j++) {
                for (float z = ST_Z[j] + 18; z < ST_Z[j + 1] - 8; z += 16) {
                    furniture(w, ax - HALF - 0.55f, z, false, n++);
                    if (z - 8 > ST_Z[j] + 8) furniture(w, ax + HALF + 0.55f, z - 8, false, n++);
                }
            }
        }
    }

    private static void furniture(CasinoWorld w, float x, float z, boolean alongX, int n) {
        CasinoWorld.Mesh m = w.mesh;
        // Only on real sidewalks, and never in the mouth of the back alley.
        if (x < X_MIN || x > X_MAX || z < Z_MIN || z > Z_MAX) return;
        if (x > 16 && x < 28 && z > -33 && z < -29) return;
        switch (n % 6) {
            case 0:
            case 3:
                tree(w, x, z, 0.75f, true);
                break;
            case 1:
                bench(w, x, z, alongX);
                break;
            case 2: {
                m.cylinder(x, z, 0.28f, 0.28f, 0, 0.9f, 0xFF2A4A32, 0xFF15151A, 0, 10);
                m.cylinder(x, z, 0.3f, 0.3f, 0.9f, 0.98f, 0xFF1A2A1E, 0xFF1A2A1E, 0, 10);
                w.solid(x - 0.28f, z - 0.28f, x + 0.28f, z + 0.28f, 1);
                break;
            }
            case 4: {
                m.cylinder(x, z, 0.15f, 0.15f, 0, 0.6f, 0xFFC8282A, 0xFFC8282A, 0, 8);
                m.cylinder(x, z, 0.11f, 0.05f, 0.6f, 0.75f, 0xFFC8282A, 0xFFC8282A, 0, 8);
                if (alongX) {
                    m.boxAA(x - 0.24f, 0.36f, z - 0.05f, x + 0.24f, 0.46f, z + 0.05f,
                            0xFFE0C04A, 0);
                } else {
                    m.boxAA(x - 0.05f, 0.36f, z - 0.24f, x + 0.05f, 0.46f, z + 0.24f,
                            0xFFE0C04A, 0);
                }
                w.solid(x - 0.15f, z - 0.15f, x + 0.15f, z + 0.15f, 0.75f);
                // A newspaper box beside it.
                float ox = alongX ? 1.0f : 0, oz = alongX ? 0 : 1.0f;
                m.boxAA(x + ox - 0.24f, 0, z + oz - 0.22f, x + ox + 0.24f, 1.0f, z + oz + 0.22f,
                        0xFF2A5AA8, 0);
                m.boxAA(x + ox - 0.2f, 0.55f, z + oz - 0.23f, x + ox + 0.2f, 0.85f,
                        z + oz + 0.23f, 0xFFDDE6F0, 0.2f);
                w.solid(x + ox - 0.24f, z + oz - 0.22f, x + ox + 0.24f, z + oz + 0.22f, 1);
                break;
            }
            default: {
                // A mailbox.
                m.boxAA(x - 0.25f, 0, z - 0.25f, x + 0.25f, 0.15f, z + 0.25f, 0xFF1A2A5A, 0);
                m.boxAA(x - 0.24f, 0.15f, z - 0.24f, x + 0.24f, 1.05f, z + 0.24f, 0xFF1E3A8A, 0);
                m.cylinder(x, z, 0.24f, 0.24f, 1.05f, 1.2f, 0xFF1E3A8A, 0xFF1E3A8A, 0, 10);
                w.solid(x - 0.25f, z - 0.25f, x + 0.25f, z + 0.25f, 1.2f);
                break;
            }
        }
    }

    private static void police(CasinoWorld w, float x0, float z0, float x1, float z1) {
        CasinoWorld.Mesh m = w.mesh;
        Random r = new Random(9);
        // The station: faces East Avenue.
        float bx0 = 64, bx1 = 85, bz0 = -26, bz1 = -5;
        m.boxAA(bx0, 0, bz0, bx1, 8, bz1, 0xFF3A4048, 0);
        m.boxAA(bx0 - 0.2f, 8, bz0 - 0.2f, bx1 + 0.2f, 8.4f, bz1 + 0.2f, 0xFF2A2E34, 0);
        w.solid(bx0, bz0, bx1, bz1, 8);
        windows(m, r, bz0, bz1, bx0 - 0.02f, WEST, 8, false);
        windows(m, r, bx0, bx1, bz0 - 0.02f, NORTH, 8, true);
        windows(m, r, bx0, bx1, bz1 + 0.02f, SOUTH, 8, true);
        m.panel(bx0 - 0.03f, -15.5f, WEST, 2.4f, 0, 2.8f, 0xFF1A2438, 0.35f, null);
        m.boxAA(bx0 - 0.25f, 3.2f, -19.2f, bx0, 4.4f, -11.8f, 0xFF050507, 0);
        m.panel(bx0 - 0.27f, -15.5f, WEST, 7.2f, 3.25f, 4.35f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.POLICE));
        for (float z : new float[]{-18.6f, -12.4f}) {
            m.boxAA(bx0 - 0.3f, 2.9f, z - 0.15f, bx0 - 0.05f, 3.1f, z + 0.15f, 0xFF3A7AFF, 1);
        }
        w.lamps.add(new float[]{bx0 - 3, -15.5f, 5, 0.6f, 0.6f, 0.75f, 1});
        // The lot with parked cruisers.
        ground(m, 60, -2, 86, 15, 0xFF202226);
        for (float x = 62; x < 86; x += 3.4f) {
            m.floor(x - 0.05f, 1, x + 0.05f, 7, 0.012f, 0xFFB8B8B0, 0, 1);
        }
        w.parkedCars.add(new float[]{63.7f, 4, CasinoWorld.PI, 0xFF101014, CasinoTraffic.POLICE});
        w.parkedCars.add(new float[]{67.1f, 4, CasinoWorld.PI, 0xFF101014, CasinoTraffic.POLICE});
        w.parkedCars.add(new float[]{73.9f, 4, CasinoWorld.PI, 0xFF101014, CasinoTraffic.POLICE});
        w.lamps.add(new float[]{70, 4, 6, 0.7f, 1, 0.8f, 0.55f});
        buildings(w, r, x0 + WALK, 9, x1 - WALK, z1 - WALK);
        buildings(w, r, 86.5f, z0 + WALK, x1 - WALK, -2);
    }

    private static void sprayShop(CasinoWorld w, float x0, float z0, float x1, float z1,
                                  Random r) {
        CasinoWorld.Mesh m = w.mesh;
        float sx0 = -76, sx1 = -64, sz0 = 30, sz1 = 41;
        // An open garage: three walls and a roof, open toward The Strip.
        w.wallZ(sx0, sz0, sz1, 4.6f, 0xFF3A3A40);
        w.wallZ(sx1, sz0, sz1, 4.6f, 0xFF3A3A40);
        w.wallX(sz1, sx0, sx1, 4.6f, 0xFF3A3A40);
        m.floor(sx0 - T / 2, sz0, sx1 + T / 2, sz1 + T / 2, 4.6f, 0xFF15110F, 0, 1);
        m.ceiling(sx0, sz0, sx1, sz1, 4.4f, 0xFF1A1A1E, 0);
        m.boxAA(sx0 - T / 2, 3.6f, sz0 - 0.2f, sx1 + T / 2, 4.6f, sz0 + 0.1f, 0xFF2A2A30, 0);
        m.panel((sx0 + sx1) / 2, sz0 - 0.22f, NORTH, 7, 3.65f, 4.55f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.SPRAY));
        m.floor(SPRAY[0], SPRAY[1], SPRAY[2], SPRAY[3], 0.015f, 0xFF2A4A3A, 0, 1);
        w.regions.add(new float[]{sx0, sz0, sx1, sz1, 4.4f});
        w.lamps.add(new float[]{-70, 35, 4, 0.7f, 0.8f, 1, 0.85f});
        for (float z = 32; z < 40; z += 2.5f) {
            m.boxAA(sx0 + 0.15f, 1.0f, z, sx0 + 0.4f, 2.2f, z + 0.8f, 0xFF6A6E78, 0.2f);
        }
        buildings(w, r, x0 + WALK, sz0, sx0 - 2, z1 - WALK);
        buildings(w, r, sx1 + 2, sz0, x1 - WALK, z1 - WALK);
        buildings(w, r, sx0, sz1 + 2, sx1, z1 - WALK);
    }

    /** Tall walls of buildings past the ring road so the city has an edge. */
    private static void skyline(CasinoWorld w, Random r) {
        float wx0 = CasinoWorld.WX0, wx1 = CasinoWorld.WX1, wz0 = CasinoWorld.WZ0,
                wz1 = CasinoWorld.WZ1;
        float g = HALF + WALK;
        CasinoWorld.Mesh m = w.mesh;
        ground(m, wx0, wz0, wx1, Z_MIN, CONCRETE);
        ground(m, wx0, Z_MAX, wx1, wz1, CONCRETE);
        ground(m, wx0, Z_MIN, X_MIN, Z_MAX, CONCRETE);
        ground(m, X_MAX, Z_MIN, wx1, Z_MAX, CONCRETE);
        for (float x = wx0; x < wx1 - 1; x += 14) {
            tower(w, r, x, wz0, Math.min(wx1, x + 13.6f), ST_Z[0] - g, 14 + r.nextFloat() * 20,
                    FACADES[r.nextInt(FACADES.length)]);
            tower(w, r, x, ST_Z[3] + g, Math.min(wx1, x + 13.6f), wz1, 14 + r.nextFloat() * 20,
                    FACADES[r.nextInt(FACADES.length)]);
        }
        for (float z = ST_Z[0] - g + 0.4f; z < ST_Z[3] + g - 1; z += 14) {
            float z1 = Math.min(ST_Z[3] + g - 0.4f, z + 13.6f);
            tower(w, r, wx0, z, AVE_X[0] - g, z1, 14 + r.nextFloat() * 20,
                    FACADES[r.nextInt(FACADES.length)]);
            tower(w, r, AVE_X[3] + g, z, wx1, z1, 14 + r.nextFloat() * 20,
                    FACADES[r.nextInt(FACADES.length)]);
        }
    }
}
