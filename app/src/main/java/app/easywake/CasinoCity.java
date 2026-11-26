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
                } else {
                    buildings(w, r, x0 + WALK, z0 + WALK, x1 - WALK, z1 - WALK);
                }
            }
        }
        skyline(w, r);
        lamps(w);
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
                                sz + k * (HALF + 0.4f) + 0.6f, 0.005f, 0xFFB8B8B0, 0, 1);
                    }
                }
            }
        }
    }

    private static void dashes(CasinoWorld.Mesh m, float a, float b, float c, boolean alongX) {
        for (float t = a + 1; t < b - 1.6f; t += 3.2f) {
            if (alongX) m.floor(t, c - 0.06f, t + 1.6f, c + 0.06f, 0.004f, LINE, 0, 1);
            else m.floor(c - 0.06f, t, c + 0.06f, t + 1.6f, 0.004f, LINE, 0, 1);
        }
    }

    private static void lamps(CasinoWorld w) {
        for (int j = 0; j < ST_Z.length; j++) {
            for (float x = X_MIN + 6; x < X_MAX - 4; x += 16) {
                if (j == 2 && x > -50 && x < 50) continue;
                lamp(w, x, ST_Z[j] - HALF - 0.6f, 1);
                lamp(w, x + 8, ST_Z[j] + HALF + 0.6f, -1);
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
        windows(m, r, x0, x1, z0 - 0.02f, NORTH, h, true);
        windows(m, r, x0, x1, z1 + 0.02f, SOUTH, h, true);
        windows(m, r, z0, z1, x0 - 0.02f, WEST, h, false);
        windows(m, r, z0, z1, x1 + 0.02f, EAST, h, false);
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
            m.floor(x - 0.05f, 1, x + 0.05f, 7, 0.004f, 0xFFB8B8B0, 0, 1);
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
        m.floor(SPRAY[0], SPRAY[1], SPRAY[2], SPRAY[3], 0.006f, 0xFF2A4A3A, 0, 1);
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
