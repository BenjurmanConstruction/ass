package app.easywake;

import java.util.Arrays;
import java.util.List;

/**
 * Finding a way around things for the people in the world. A grid of 0.4 m cells over the
 * whole map marks where a body fits (every solid, grown by a body's width); A* finds a route
 * and string-pulling straightens it, so people walk round slot banks and tables instead of
 * into them. Roads cost extra, so people keep to the sidewalks and cross rather than stroll
 * down the middle of the street.
 */
final class CasinoNav {
    static final float CELL = 0.4f;
    /** How far a body keeps from the edge of a solid. */
    static final float BODY = 0.32f;

    final float x0, z0;
    final int nx, nz;
    final boolean[] blocked;
    final byte[] cost;

    // A* work arrays, reused.
    private final float[] g;
    private final int[] from;
    private final int[] stamp;
    private int run;
    private int[] heap = new int[1024];
    private float[] heapF = new float[1024];
    private int heapN;

    CasinoNav(float x0, float z0, float x1, float z1, List<float[]> walls) {
        this.x0 = x0;
        this.z0 = z0;
        nx = (int) Math.ceil((x1 - x0) / CELL);
        nz = (int) Math.ceil((z1 - z0) / CELL);
        blocked = new boolean[nx * nz];
        cost = new byte[nx * nz];
        g = new float[nx * nz];
        from = new int[nx * nz];
        stamp = new int[nx * nz];
        solids = walls;
        for (float[] w : walls) {
            if (w.length > 5) continue;
            mark(w[0] - BODY, w[1] - BODY, w[2] + BODY, w[3] + BODY);
        }
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                float cx = x0 + (i + 0.5f) * CELL, cz = z0 + (j + 0.5f) * CELL;
                cost[j * nx + i] = (byte) (CasinoCity.onRoad(cx, cz) ? 6 : 1);
            }
        }
    }

    /**
     * Opens a doorway: the gap is narrower than two bodies' margins, so the grid would close it.
     * Shut doors still stop people as they walk (they're pushed by the door itself).
     */
    void doorway(float cx, float cz, boolean alongX, float width) {
        float hw = width / 2 - 0.15f, depth = 0.9f;
        float ax = alongX ? cx - hw : cx - depth, bx = alongX ? cx + hw : cx + depth;
        float az = alongX ? cz - depth : cz - hw, bz = alongX ? cz + depth : cz + hw;
        int i0 = Math.max(0, (int) Math.floor((ax - x0) / CELL));
        int i1 = Math.min(nx - 1, (int) Math.floor((bx - x0) / CELL));
        int j0 = Math.max(0, (int) Math.floor((az - z0) / CELL));
        int j1 = Math.min(nz - 1, (int) Math.floor((bz - z0) / CELL));
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                float px = x0 + (i + 0.5f) * CELL, pz = z0 + (j + 0.5f) * CELL;
                // Only where nothing actually solid sits (just the margins).
                if (!inside(px, pz)) blocked[j * nx + i] = false;
            }
        }
    }

    private List<float[]> solids;

    private boolean inside(float x, float z) {
        for (float[] w : solids) {
            if (w.length > 5) continue;
            if (x > w[0] - 0.12f && x < w[2] + 0.12f && z > w[1] - 0.12f && z < w[3] + 0.12f) {
                return true;
            }
        }
        return false;
    }

    /** Blocks every cell whose center falls inside the box. */
    private void mark(float ax, float az, float bx, float bz) {
        int i0 = Math.max(0, (int) Math.ceil((ax - x0) / CELL - 0.5f));
        int i1 = Math.min(nx - 1, (int) Math.floor((bx - x0) / CELL - 0.5f));
        int j0 = Math.max(0, (int) Math.ceil((az - z0) / CELL - 0.5f));
        int j1 = Math.min(nz - 1, (int) Math.floor((bz - z0) / CELL - 0.5f));
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) blocked[j * nx + i] = true;
        }
    }

    int ci(float x) {
        return Math.max(0, Math.min(nx - 1, (int) ((x - x0) / CELL)));
    }

    int cj(float z) {
        return Math.max(0, Math.min(nz - 1, (int) ((z - z0) / CELL)));
    }

    boolean open(float x, float z) {
        return !blocked[cj(z) * nx + ci(x)];
    }

    /** True if a body can walk straight from a to b. */
    boolean clear(float ax, float az, float bx, float bz) {
        float d = (float) Math.hypot(bx - ax, bz - az);
        int n = Math.max(1, (int) (d / (CELL * 0.5f)));
        for (int k = 1; k <= n; k++) {
            float t = k / (float) n;
            if (blocked[cj(az + (bz - az) * t) * nx + ci(ax + (bx - ax) * t)]) return false;
        }
        return true;
    }

    /** The nearest open cell to a point, searching outward a few meters. */
    private int nearestOpen(float x, float z) {
        int i = ci(x), j = cj(z);
        if (!blocked[j * nx + i]) return j * nx + i;
        for (int r = 1; r < 8; r++) {
            int best = -1;
            float bd = 1e9f;
            for (int dj = -r; dj <= r; dj++) {
                for (int di = -r; di <= r; di++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue;
                    int a = i + di, b = j + dj;
                    if (a < 0 || b < 0 || a >= nx || b >= nz || blocked[b * nx + a]) continue;
                    float dd = di * di + dj * dj;
                    if (dd < bd) {
                        bd = dd;
                        best = b * nx + a;
                    }
                }
            }
            if (best >= 0) return best;
        }
        return -1;
    }

    /**
     * A walkable route from (sx, sz) to (tx, tz) as x, z pairs (the start left out), or null.
     * maxNodes caps the search so one long route can't stall a frame.
     */
    float[] find(float sx, float sz, float tx, float tz, int maxNodes) {
        int s = nearestOpen(sx, sz), t = nearestOpen(tx, tz);
        if (s < 0 || t < 0) return null;
        if (s == t) return new float[]{tx, tz};
        run++;
        if (run == Integer.MAX_VALUE) {
            Arrays.fill(stamp, 0);
            run = 1;
        }
        heapN = 0;
        int ti = t % nx, tj = t / nx;
        g[s] = 0;
        from[s] = -1;
        stamp[s] = run;
        push(s, h(s % nx, s / nx, ti, tj));
        int expanded = 0;
        boolean found = false;
        while (heapN > 0) {
            int c = pop();
            if (c == t) {
                found = true;
                break;
            }
            if (++expanded > maxNodes) break;
            int ci = c % nx, cj = c / nx;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    if (di == 0 && dj == 0) continue;
                    int a = ci + di, b = cj + dj;
                    if (a < 0 || b < 0 || a >= nx || b >= nz) continue;
                    int n = b * nx + a;
                    if (blocked[n]) continue;
                    // No cutting corners past a solid.
                    if (di != 0 && dj != 0 && (blocked[cj * nx + a] || blocked[b * nx + ci])) continue;
                    float step = (di != 0 && dj != 0 ? 1.4142f : 1f) * cost[n];
                    float ng = g[c] + step;
                    if (stamp[n] == run && ng >= g[n]) continue;
                    stamp[n] = run;
                    g[n] = ng;
                    from[n] = c;
                    push(n, ng + h(a, b, ti, tj));
                }
            }
        }
        if (!found) return null;
        // Walk back, then keep only the corners a straight walk can't skip.
        int len = 0;
        for (int c = t; c != -1; c = from[c]) len++;
        int[] cells = new int[len];
        int k = len;
        for (int c = t; c != -1; c = from[c]) cells[--k] = c;
        float[] pts = new float[len * 2];
        for (int i = 0; i < len; i++) {
            pts[i * 2] = x0 + (cells[i] % nx + 0.5f) * CELL;
            pts[i * 2 + 1] = z0 + (cells[i] / nx + 0.5f) * CELL;
        }
        pts[len * 2 - 2] = tx;
        pts[len * 2 - 1] = tz;
        float[] out = new float[len * 2];
        int o = 0;
        float ax = sx, az = sz;
        int i = 0;
        while (i < len - 1) {
            int far = i + 1;
            for (int j = Math.min(len - 1, i + 40); j > i + 1; j--) {
                if (clear(ax, az, pts[j * 2], pts[j * 2 + 1])) {
                    far = j;
                    break;
                }
            }
            out[o++] = pts[far * 2];
            out[o++] = pts[far * 2 + 1];
            ax = pts[far * 2];
            az = pts[far * 2 + 1];
            i = far;
        }
        if (o == 0) {
            out[o++] = tx;
            out[o++] = tz;
        }
        return Arrays.copyOf(out, o);
    }

    private static float h(int a, int b, int ti, int tj) {
        int dx = Math.abs(a - ti), dz = Math.abs(b - tj);
        return Math.max(dx, dz) + 0.4142f * Math.min(dx, dz);
    }

    private void push(int n, float f) {
        if (heapN == heap.length) {
            heap = Arrays.copyOf(heap, heapN * 2);
            heapF = Arrays.copyOf(heapF, heapN * 2);
        }
        int i = heapN++;
        while (i > 0) {
            int p = (i - 1) / 2;
            if (heapF[p] <= f) break;
            heap[i] = heap[p];
            heapF[i] = heapF[p];
            i = p;
        }
        heap[i] = n;
        heapF[i] = f;
    }

    private int pop() {
        int top = heap[0];
        int n = heap[--heapN];
        float f = heapF[heapN];
        int i = 0;
        while (true) {
            int l = i * 2 + 1;
            if (l >= heapN) break;
            int r = l + 1;
            int c = r < heapN && heapF[r] < heapF[l] ? r : l;
            if (heapF[c] >= f) break;
            heap[i] = heap[c];
            heapF[i] = heapF[c];
            i = c;
        }
        heap[i] = n;
        heapF[i] = f;
        return top;
    }
}
