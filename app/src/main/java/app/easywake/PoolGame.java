package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Eight-ball pool: the table, the balls, the physics and the rules, kept apart from the screen
 * so it can be tested with thousands of simulated games. Distances are meters on a 9-foot
 * table (2.54 x 1.27 m playing surface), speeds are meters a second.
 *
 * Ball 0 is the cue ball, 1-7 solids, 8 the eight ball, 9-15 stripes.
 */
final class PoolGame {
    static final float W = 2.54f, H = 1.27f, R = 0.028575f;
    /** Pocket centers sit just outside the cushion line. */
    static final float[][] POCKETS = {{-0.014f, -0.014f}, {W / 2, -0.022f},
            {W + 0.014f, -0.014f}, {-0.014f, H + 0.014f}, {W / 2, H + 0.022f},
            {W + 0.014f, H + 0.014f}};
    static final float CORNER_CAP = 0.068f, SIDE_CAP = 0.058f;
    /** How far from a corner the cushions start, and half the side pocket's mouth. */
    static final float CORNER_MOUTH = 0.11f, SIDE_MOUTH = 0.07f;
    /** The break: the cue ball goes behind this line. */
    static final float HEAD = W * 0.25f;
    static final float FOOT_X = W * 0.75f, FOOT_Y = H / 2;

    static final int NONE = 0, SOLIDS = 1, STRIPES = 2;

    /** Hears what happens on the table, for sounds and shakes. */
    interface Listener {
        void clack(float speed);

        void rail(float speed);

        void pocket(int ball);
    }

    final float[] x = new float[16], y = new float[16], vx = new float[16], vy = new float[16];
    final boolean[] on = new boolean[16];
    /** Which way each ball's number faces, for the rolling look (unit vectors). */
    final float[] nx = new float[16], ny = new float[16], nz = new float[16];
    /** The axis through a stripe ball's white caps (always at right angles to the number). */
    final float[] qx = new float[16], qy = new float[16], qz = new float[16];

    // ---- the shot in progress
    boolean moving;
    /** Follow (+) or draw (-) left on the cue ball, applied when it first hits a ball. */
    float spin;
    float shotDx, shotDy;
    int firstHit = -1;
    final List<Integer> potted = new ArrayList<>();
    boolean scratched;
    int railsAfterHit;
    Listener listener;

    // ---- the match
    int turn;
    final int[] group = new int[2];
    boolean breakShot = true;
    /** Ball in hand: the shooter may place the cue ball (only behind the head line on the break). */
    boolean ballInHand = true;
    int winner = -1;
    String lastFoul;
    /** What the last shot did, for the screen to say. */
    String news = "";

    PoolGame(long seed) {
        rack(new Random(seed));
    }

    PoolGame(PoolGame o) {
        System.arraycopy(o.x, 0, x, 0, 16);
        System.arraycopy(o.y, 0, y, 0, 16);
        System.arraycopy(o.on, 0, on, 0, 16);
        turn = o.turn;
        group[0] = o.group[0];
        group[1] = o.group[1];
        breakShot = o.breakShot;
        ballInHand = o.ballInHand;
        winner = o.winner;
        for (int i = 0; i < 16; i++) {
            nz[i] = 1;
            qx[i] = 1;
        }
    }

    /** A tight triangle at the foot spot: 8 in the middle, a solid and a stripe in the back corners. */
    void rack(Random rnd) {
        int[] order = new int[15];
        List<Integer> rest = new ArrayList<>();
        for (int b = 1; b <= 15; b++) if (b != 8 && b != 1 && b != 9 && b != 15) rest.add(b);
        java.util.Collections.shuffle(rest, rnd);
        int k = 0;
        for (int i = 0; i < 15; i++) {
            if (i == 0) order[i] = 1;          // apex
            else if (i == 4) order[i] = 8;     // middle of the third row
            else if (i == 10) order[i] = 9;    // back corners
            else if (i == 14) order[i] = 15;
            else order[i] = rest.get(k++);
        }
        int idx = 0;
        float dx = R * 2 * 0.866f + 0.0004f;
        for (int row = 0; row < 5; row++) {
            for (int j = 0; j <= row; j++) {
                int b = order[idx++];
                x[b] = FOOT_X + row * dx + (rnd.nextFloat() - 0.5f) * 0.0006f;
                y[b] = FOOT_Y + (j - row / 2f) * (R * 2 + 0.0004f)
                        + (rnd.nextFloat() - 0.5f) * 0.0006f;
                on[b] = true;
            }
        }
        x[0] = HEAD - 0.1f;
        y[0] = H / 2;
        on[0] = true;
        for (int i = 0; i < 16; i++) {
            vx[i] = vy[i] = 0;
            nx[i] = ny[i] = 0;
            nz[i] = 1;
            if (i > 0) {
                // Start the numbers facing different ways so the rack looks natural.
                float a = rnd.nextFloat() * 6.283f, t = rnd.nextFloat() * 0.9f;
                nx[i] = (float) (Math.sin(t) * Math.cos(a));
                ny[i] = (float) (Math.sin(t) * Math.sin(a));
                nz[i] = (float) Math.cos(t);
            }
            // Cap axis: any direction at right angles to the number.
            float bx = Math.abs(nz[i]) < 0.9f ? 0 : 1, bz = Math.abs(nz[i]) < 0.9f ? 1 : 0;
            float cx = ny[i] * bz, cy = nz[i] * bx - nx[i] * bz, cz = -ny[i] * bx;
            float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            qx[i] = cx / cl;
            qy[i] = cy / cl;
            qz[i] = cz / cl;
        }
        turn = 0;
        group[0] = group[1] = NONE;
        breakShot = true;
        ballInHand = true;
        winner = -1;
    }

    // ------------------------------------------------------------------ physics

    /** Strikes the cue ball. power 0..1, spin -1 (draw) .. +1 (follow). */
    void shoot(float angle, float power, float spinIn) {
        float sp = speedFor(power);
        shotDx = (float) Math.cos(angle);
        shotDy = (float) Math.sin(angle);
        vx[0] = shotDx * sp;
        vy[0] = shotDy * sp;
        spin = spinIn;
        moving = true;
        firstHit = -1;
        potted.clear();
        scratched = false;
        railsAfterHit = 0;
        lastFoul = null;
    }

    static float speedFor(float power) {
        return 0.25f + (float) Math.pow(Math.max(0, Math.min(1, power)), 1.35) * 7.6f;
    }

    /** Advances the balls by dt seconds in small steps. Returns true while anything moves. */
    boolean step(float dt) {
        if (!moving) return false;
        int n = Math.max(1, (int) Math.ceil(dt / 0.001f));
        float h = dt / n;
        for (int s = 0; s < n && moving; s++) sub(h);
        return moving;
    }

    private void sub(float h) {
        boolean any = false;
        for (int i = 0; i < 16; i++) {
            if (!on[i]) continue;
            float v = (float) Math.hypot(vx[i], vy[i]);
            if (v <= 0) continue;
            // Rolling friction plus cloth drag: a hard break runs a few table lengths.
            float nv = v - (0.3f + 0.35f * v) * h;
            if (nv < 0.004f) {
                vx[i] = vy[i] = 0;
                continue;
            }
            vx[i] *= nv / v;
            vy[i] *= nv / v;
            float dx = vx[i] * h, dy = vy[i] * h;
            x[i] += dx;
            y[i] += dy;
            roll(i, dx, dy);
            any = true;
        }
        for (int i = 0; i < 16; i++) {
            if (!on[i]) continue;
            for (int j = i + 1; j < 16; j++) {
                if (on[j]) collide(i, j);
            }
        }
        for (int i = 0; i < 16; i++) {
            if (on[i]) {
                cushion(i);
                pockets(i);
            }
        }
        if (!any) {
            moving = false;
            for (int i = 0; i < 16; i++) vx[i] = vy[i] = 0;
        }
    }

    /** Turns the ball (its number and stripe axis) as it rolls dx, dy. */
    private void roll(int i, float dx, float dy) {
        float d = (float) Math.hypot(dx, dy);
        if (d <= 0) return;
        float ang = d / R;
        // Rolling forward turns the ball about the horizontal axis at right angles to travel.
        float ax = -dy / d, ay = dx / d;
        float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        float[] v = rot(nx[i], ny[i], nz[i], ax, ay, c, sn, tmp);
        nx[i] = v[0];
        ny[i] = v[1];
        nz[i] = v[2];
        v = rot(qx[i], qy[i], qz[i], ax, ay, c, sn, tmp);
        qx[i] = v[0];
        qy[i] = v[1];
        qz[i] = v[2];
    }

    /** Rodrigues' rotation of p about the unit axis (ax, ay, 0). */
    private final float[] tmp = new float[3];

    private static float[] rot(float px, float py, float pz, float ax, float ay, float c,
                               float s, float[] out) {
        float dot = ax * px + ay * py;
        float cx = ay * pz, cy = -ax * pz, cz = ax * py - ay * px;
        float rx = px * c + cx * s + ax * dot * (1 - c);
        float ry = py * c + cy * s + ay * dot * (1 - c);
        float rz = pz * c + cz * s;
        float l = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        out[0] = rx / l;
        out[1] = ry / l;
        out[2] = rz / l;
        return out;
    }

    private void collide(int i, int j) {
        float dx = x[j] - x[i], dy = y[j] - y[i];
        float d2 = dx * dx + dy * dy, min = 2 * R;
        if (d2 >= min * min || d2 == 0) return;
        float d = (float) Math.sqrt(d2);
        float ux = dx / d, uy = dy / d;
        float rel = (vx[i] - vx[j]) * ux + (vy[i] - vy[j]) * uy;
        // Push apart.
        float over = (min - d) / 2;
        x[i] -= ux * over;
        y[i] -= uy * over;
        x[j] += ux * over;
        y[j] += uy * over;
        if (rel <= 0) return;
        float cueSpeed = 0;
        if (i == 0 || j == 0) {
            cueSpeed = (float) Math.hypot(vx[0], vy[0]);
            if (firstHit < 0) firstHit = i == 0 ? j : i;
        }
        float imp = rel * (1 + 0.95f) / 2;
        vx[i] -= ux * imp;
        vy[i] -= uy * imp;
        vx[j] += ux * imp;
        vy[j] += uy * imp;
        if (listener != null) listener.clack(rel);
        // Follow or draw: the cue ball's spin grabs the cloth after it hits.
        if ((i == 0 || j == 0) && spin != 0) {
            float k = spin * cueSpeed * 0.55f * Math.max(0, ux * shotDx + uy * shotDy);
            vx[0] += shotDx * k;
            vy[0] += shotDy * k;
            spin = 0;
        } else if (i == 0 || j == 0) {
            spin = 0;
        }
    }

    private void cushion(int i) {
        boolean leftMouth = y[i] < CORNER_MOUTH || y[i] > H - CORNER_MOUTH;
        boolean topMouth = x[i] < CORNER_MOUTH || x[i] > W - CORNER_MOUTH
                || Math.abs(x[i] - W / 2) < SIDE_MOUTH;
        float e = 0.78f;
        if (!leftMouth) {
            if (x[i] < R && vx[i] < 0) {
                x[i] = R;
                bounce(i, true, e);
            } else if (x[i] > W - R && vx[i] > 0) {
                x[i] = W - R;
                bounce(i, true, e);
            }
        }
        if (!topMouth) {
            if (y[i] < R && vy[i] < 0) {
                y[i] = R;
                bounce(i, false, e);
            } else if (y[i] > H - R && vy[i] > 0) {
                y[i] = H - R;
                bounce(i, false, e);
            }
        }
        // The pocket jaws: the cushion ends act like posts.
        jaw(i, CORNER_MOUTH, 0);
        jaw(i, 0, CORNER_MOUTH);
        jaw(i, W - CORNER_MOUTH, 0);
        jaw(i, W, CORNER_MOUTH);
        jaw(i, CORNER_MOUTH, H);
        jaw(i, 0, H - CORNER_MOUTH);
        jaw(i, W - CORNER_MOUTH, H);
        jaw(i, W, H - CORNER_MOUTH);
        jaw(i, W / 2 - SIDE_MOUTH, 0);
        jaw(i, W / 2 + SIDE_MOUTH, 0);
        jaw(i, W / 2 - SIDE_MOUTH, H);
        jaw(i, W / 2 + SIDE_MOUTH, H);
    }

    private void bounce(int i, boolean xAxis, float e) {
        float v = xAxis ? Math.abs(vx[i]) : Math.abs(vy[i]);
        if (xAxis) {
            vx[i] = -vx[i] * e;
            vy[i] *= 0.95f;
        } else {
            vy[i] = -vy[i] * e;
            vx[i] *= 0.95f;
        }
        if (firstHit >= 0) railsAfterHit++;
        if (listener != null && v > 0.05f) listener.rail(v);
    }

    private void jaw(int i, float px, float py) {
        float dx = x[i] - px, dy = y[i] - py;
        float d2 = dx * dx + dy * dy;
        if (d2 >= R * R || d2 == 0) return;
        float d = (float) Math.sqrt(d2);
        float ux = dx / d, uy = dy / d;
        x[i] = px + ux * R;
        y[i] = py + uy * R;
        float vn = vx[i] * ux + vy[i] * uy;
        if (vn < 0) {
            vx[i] -= (1 + 0.6f) * vn * ux;
            vy[i] -= (1 + 0.6f) * vn * uy;
            if (listener != null && -vn > 0.05f) listener.rail(-vn);
        }
    }

    private void pockets(int i) {
        for (int p = 0; p < 6; p++) {
            float cap = p == 1 || p == 4 ? SIDE_CAP : CORNER_CAP;
            float dx = x[i] - POCKETS[p][0], dy = y[i] - POCKETS[p][1];
            if (dx * dx + dy * dy < cap * cap) {
                drop(i);
                return;
            }
        }
        // Anything that somehow gets past the cushions goes down the nearest hole.
        if (x[i] < -R * 2 || x[i] > W + R * 2 || y[i] < -R * 2 || y[i] > H + R * 2) drop(i);
    }

    private void drop(int i) {
        on[i] = false;
        vx[i] = vy[i] = 0;
        if (i == 0) scratched = true;
        else potted.add(i);
        if (listener != null) listener.pocket(i);
    }

    // ------------------------------------------------------------------ rules

    static int groupOf(int b) {
        return b >= 1 && b <= 7 ? SOLIDS : (b >= 9 ? STRIPES : NONE);
    }

    int left(int grp) {
        int n = 0;
        for (int b = 1; b <= 15; b++) if (on[b] && groupOf(b) == grp) n++;
        return n;
    }

    /** True if this player may (and must) shoot at the eight now. */
    boolean onEight(int player) {
        return group[player] != NONE && left(group[player]) == 0;
    }

    /** Whether hitting ball b first is legal for the shooter. */
    boolean legalFirst(int b) {
        if (b <= 0) return false;
        if (breakShot) return true;
        int g = group[turn];
        if (g == NONE) return b != 8;
        if (onEight(turn)) return b == 8;
        return groupOf(b) == g;
    }

    /**
     * Applies the rules once the balls have stopped. The group cleared check uses the table
     * before the shot: potting your last ball and the eight together still loses.
     */
    void endShot(boolean ownGroupWasClear) {
        int me = turn, them = 1 - turn;
        boolean foul = false;
        String why = null;
        if (scratched) {
            foul = true;
            why = "Scratch";
        } else if (firstHit < 0) {
            foul = true;
            why = "Missed everything";
        } else if (!legalFirst(firstHit)) {
            foul = true;
            why = "Hit the wrong ball first";
        }
        boolean eight = potted.contains(8);
        news = "";
        if (eight) {
            if (breakShot) {
                spotEight();
                news = "Eight on the break: it's spotted.";
            } else {
                if (ownGroupWasClear && !foul) {
                    winner = me;
                    news = "Eight ball down. Game!";
                } else {
                    winner = them;
                    news = foul ? "Eight went in on a foul. Game over."
                            : "Eight went in early. Game over.";
                }
                moving = false;
                return;
            }
        }
        // Groups are decided by the first legal pot after the break.
        if (!breakShot && !foul && group[me] == NONE) {
            for (int b : potted) {
                int g = groupOf(b);
                if (g != NONE) {
                    group[me] = g;
                    group[them] = g == SOLIDS ? STRIPES : SOLIDS;
                    news = (g == SOLIDS ? "Solids" : "Stripes") + " for "
                            + (me == 0 ? "you" : "them") + ".";
                    break;
                }
            }
        }
        boolean keep = false;
        if (!foul) {
            for (int b : potted) {
                if (b == 8) continue;
                if (breakShot || group[me] == NONE || groupOf(b) == group[me]) keep = true;
            }
        }
        breakShot = false;
        lastFoul = foul ? why : null;
        if (foul) {
            turn = them;
            ballInHand = true;
            if (!on[0]) {
                on[0] = true;
                place(HEAD - 0.1f, H / 2);
            }
            news = why + ". Ball in hand.";
        } else if (keep) {
            ballInHand = false;
            if (news.isEmpty()) news = potted.size() > 1 ? "Two down!" : "Down it goes.";
        } else {
            turn = them;
            ballInHand = false;
        }
    }

    private void spotEight() {
        float px = FOOT_X;
        while (!free(px, FOOT_Y, 8)) px += 2 * R + 0.002f;
        x[8] = Math.min(W - R, px);
        y[8] = FOOT_Y;
        on[8] = true;
    }

    /** Puts the cue ball at x, y (nudged clear of other balls). */
    void place(float px, float py) {
        float minX = breakShot ? R : R, maxX = breakShot ? HEAD : W - R;
        px = Math.max(minX, Math.min(maxX, px));
        py = Math.max(R, Math.min(H - R, py));
        if (!free(px, py, 0)) {
            // Spiral out to the nearest clear spot.
            for (int k = 1; k < 60; k++) {
                float a = k * 0.9f, r = k * R * 0.3f;
                float qx = px + (float) Math.cos(a) * r, qy = py + (float) Math.sin(a) * r;
                if (qx > minX && qx < maxX && qy > R && qy < H - R && free(qx, qy, 0)) {
                    px = qx;
                    py = qy;
                    break;
                }
            }
        }
        x[0] = px;
        y[0] = py;
        on[0] = true;
    }

    boolean free(float px, float py, int except) {
        for (int b = 0; b < 16; b++) {
            if (b == except || !on[b]) continue;
            float dx = x[b] - px, dy = y[b] - py;
            if (dx * dx + dy * dy < 4 * R * R * 1.02f) return false;
        }
        return true;
    }

    /** Plays a shot to the end without drawing: for the AI and tests. */
    void playOut(float angle, float power, float spinIn) {
        boolean clear = onEight(turn);
        shoot(angle, power, spinIn);
        int guard = 0;
        while (step(1 / 60f) && guard++ < 60 * 40) {
            // run
        }
        moving = false;
        endShot(clear);
    }

    // ------------------------------------------------------------------ aiming help

    /** Where the aim line meets the first ball: {ballIndex or -1, contactX, contactY}. */
    float[] aimHit(float angle) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float best = Float.MAX_VALUE;
        int hit = -1;
        for (int b = 1; b < 16; b++) {
            if (!on[b]) continue;
            float ox = x[b] - x[0], oy = y[b] - y[0];
            float along = ox * dx + oy * dy;
            if (along <= 0) continue;
            float perp2 = ox * ox + oy * oy - along * along;
            float rr = 4 * R * R;
            if (perp2 >= rr) continue;
            float t = along - (float) Math.sqrt(rr - perp2);
            if (t < best) {
                best = t;
                hit = b;
            }
        }
        // Cushion (the inner rectangle the cue ball's center can reach).
        float tc = Float.MAX_VALUE;
        if (dx > 0) tc = Math.min(tc, (W - R - x[0]) / dx);
        if (dx < 0) tc = Math.min(tc, (R - x[0]) / dx);
        if (dy > 0) tc = Math.min(tc, (H - R - y[0]) / dy);
        if (dy < 0) tc = Math.min(tc, (R - y[0]) / dy);
        if (hit >= 0 && best < tc) {
            return new float[]{hit, x[0] + dx * best, y[0] + dy * best};
        }
        return new float[]{-1, x[0] + dx * tc, y[0] + dy * tc};
    }

    // ------------------------------------------------------------------ the computer player

    /** A planned shot: angle, power, spin, and how good the plan looked. */
    static final class Plan {
        float angle, power, spin, score = -1e9f;
        float placeX = -1, placeY;
    }

    /**
     * Picks a shot by trying likely ones on copies of the table, then shakes the aim by the
     * player's skill (0..1). Better players also think about where the cue ball ends up.
     */
    static Plan plan(PoolGame g, float skill, Random rnd) {
        Plan best = new Plan();
        PoolGame base = new PoolGame(g);
        if (g.ballInHand) placeForShot(base, rnd);
        float cx = base.x[0], cy = base.y[0];
        List<float[]> cands = new ArrayList<>();
        for (int b = 1; b < 16; b++) {
            if (!base.on[b] || !base.legalFirst(b)) continue;
            for (int p = 0; p < 6; p++) {
                float px = POCKETS[p][0], py = POCKETS[p][1];
                float tx = px - base.x[b], ty = py - base.y[b];
                float tl = (float) Math.hypot(tx, ty);
                // Side pockets won't take a ball that comes in flat along the rail.
                if ((p == 1 || p == 4) && Math.abs(ty) / tl < 0.55f) continue;
                float gx = base.x[b] - tx / tl * 2 * R, gy = base.y[b] - ty / tl * 2 * R;
                float ax = gx - cx, ay = gy - cy;
                float al = (float) Math.hypot(ax, ay);
                if (al < 1e-4f) continue;
                float cut = (ax * tx + ay * ty) / (al * tl);
                if (cut < 0.2f) continue;
                float h = cut * cut / (0.4f + al + tl * 1.3f);
                // Blocked shots go to the back of the line.
                if (!clearPath(base, cx, cy, gx, gy, b)
                        || !clearPath(base, base.x[b], base.y[b], px, py, b)) h *= 0.05f;
                cands.add(new float[]{(float) Math.atan2(ay, ax), h, al + tl, b});
            }
        }
        cands.sort((a, b) -> Float.compare(b[1], a[1]));
        int tries = 0;
        for (float[] c : cands) {
            if (tries++ >= 12) break;
            float dist = c[2];
            float basePow = Math.min(0.9f, 0.14f + dist * 0.1f);
            for (float pm : new float[]{0.8f, 1.0f, 1.35f}) {
                for (float sp : new float[]{0f, -0.6f, 0.5f}) {
                    PoolGame t = new PoolGame(base);
                    int me = t.turn;
                    t.playOut(c[0], basePow * pm, sp);
                    float score = 0;
                    if (t.winner == me) score = 1000;
                    else if (t.winner >= 0) score = -1000;
                    else if (t.turn == me && t.lastFoul == null) {
                        score = 100 + nextShotEase(t) * 40 * skill;
                    } else {
                        score = t.lastFoul != null ? -50 : 0;
                    }
                    score -= Math.abs(sp) * 2 + pm * 1;
                    if (score > best.score) {
                        best.score = score;
                        best.angle = c[0];
                        best.power = basePow * pm;
                        best.spin = sp;
                    }
                }
            }
        }
        if (best.score < 50) {
            // Nothing goes: roll softly into a legal ball and leave it tough.
            // Center, both edges, and fuller/thinner hits, soft and medium; then off a cushion.
            List<float[]> safe = new ArrayList<>();
            for (int b = 1; b < 16; b++) {
                if (!base.on[b] || !base.legalFirst(b)) continue;
                float dx = base.x[b] - cx, dy = base.y[b] - cy;
                float d = (float) Math.hypot(dx, dy);
                float a0 = (float) Math.atan2(dy, dx);
                float edge = (float) Math.asin(Math.min(1, 1.9f * R / d));
                for (float k : new float[]{0, -0.5f, 0.5f, -0.9f, 0.9f}) {
                    safe.add(new float[]{a0 + edge * k, b});
                }
                // Mirror the ball in each cushion for a one-rail kick.
                float[][] mirrors = {{base.x[b], -base.y[b] + 2 * R}, {base.x[b], 2 * (H - R) - base.y[b]},
                        {-base.x[b] + 2 * R, base.y[b]}, {2 * (W - R) - base.x[b], base.y[b]}};
                for (float[] m : mirrors) {
                    safe.add(new float[]{(float) Math.atan2(m[1] - cy, m[0] - cx), b});
                }
            }
            for (float[] sc : safe) {
                for (float pw : new float[]{0.3f, 0.45f}) {
                    PoolGame t = new PoolGame(base);
                    t.playOut(sc[0], pw, 0);
                    float score = t.lastFoul == null ? 10 - nextShotEase(t) * 5 : -40;
                    if (t.turn == base.turn && t.lastFoul == null) score = 60;
                    if (t.winner >= 0) score = t.winner == base.turn ? 1000 : -1000;
                    if (score > best.score) {
                        best.score = score;
                        best.angle = sc[0];
                        best.power = pw;
                        best.spin = 0;
                    }
                }
            }
        }
        if (best.score == -1e9f) {
            best.angle = (float) Math.atan2(H / 2 - cy, W / 2 - cx);
            best.power = 0.3f;
        }
        if (g.breakShot) {
            best.angle = (float) Math.atan2(FOOT_Y - cy, FOOT_X - cx);
            best.power = 0.95f;
            best.spin = 0;
        }
        // Human hands: the worse the player, the shakier the aim and touch.
        float wobble = (1 - skill) * 0.03f + 0.0015f;
        best.angle += (float) rnd.nextGaussian() * wobble;
        best.power = Math.max(0.05f, Math.min(1, best.power
                * (1 + (float) rnd.nextGaussian() * (1 - skill) * 0.12f)));
        if (g.ballInHand) {
            best.placeX = base.x[0];
            best.placeY = base.y[0];
        }
        return best;
    }

    /** 0..1: how easy the best pot is from here for whoever's turn it is. */
    static float nextShotEase(PoolGame t) {
        float best = 0;
        for (int b = 1; b < 16; b++) {
            if (!t.on[b] || !t.legalFirst(b)) continue;
            for (int p = 0; p < 6; p++) {
                float tx = POCKETS[p][0] - t.x[b], ty = POCKETS[p][1] - t.y[b];
                float tl = (float) Math.hypot(tx, ty);
                float gx = t.x[b] - tx / tl * 2 * R, gy = t.y[b] - ty / tl * 2 * R;
                float ax = gx - t.x[0], ay = gy - t.y[0];
                float al = (float) Math.hypot(ax, ay);
                if (al < 1e-4f) continue;
                float cut = (ax * tx + ay * ty) / (al * tl);
                if (cut < 0.3f) continue;
                if (!clearPath(t, t.x[0], t.y[0], gx, gy, b)) continue;
                if (!clearPath(t, t.x[b], t.y[b], POCKETS[p][0], POCKETS[p][1], b)) continue;
                best = Math.max(best, cut * cut / (1 + (al + tl) * 0.6f));
            }
        }
        return best;
    }

    static boolean clearPath(PoolGame t, float ax, float ay, float bx, float by, int skip) {
        float dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        for (int k = 0; k < 16; k++) {
            if (k == skip || k == 0 || !t.on[k]) continue;
            float u = ((t.x[k] - ax) * dx + (t.y[k] - ay) * dy) / l2;
            if (u <= 0 || u >= 1) continue;
            float px = ax + dx * u - t.x[k], py = ay + dy * u - t.y[k];
            if (px * px + py * py < 4 * R * R) return false;
        }
        return true;
    }

    /** Ball in hand for the computer: set up straight-ish on the easiest pot. */
    private static void placeForShot(PoolGame t, Random rnd) {
        if (t.breakShot) {
            t.place(HEAD - 0.05f, H / 2 + (rnd.nextFloat() - 0.5f) * 0.3f);
            return;
        }
        float bestScore = -1;
        float bx = HEAD, by = H / 2;
        for (int b = 1; b < 16; b++) {
            if (!t.on[b] || !t.legalFirst(b)) continue;
            for (int p = 0; p < 6; p++) {
                float tx = POCKETS[p][0] - t.x[b], ty = POCKETS[p][1] - t.y[b];
                float tl = (float) Math.hypot(tx, ty);
                if (!clearPath(t, t.x[b], t.y[b], POCKETS[p][0], POCKETS[p][1], b)) continue;
                for (float back : new float[]{0.25f, 0.4f, 0.15f}) {
                    float px = t.x[b] - tx / tl * (2 * R + back);
                    float py = t.y[b] - ty / tl * (2 * R + back);
                    if (px < R || px > W - R || py < R || py > H - R) continue;
                    if (!t.free(px, py, 0)) continue;
                    float s = 1 / (1 + tl) + back * 0.1f;
                    if (s > bestScore) {
                        bestScore = s;
                        bx = px;
                        by = py;
                    }
                    break;
                }
            }
        }
        t.place(bx, by);
    }
}
