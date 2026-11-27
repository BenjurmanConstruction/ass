package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Every car in town (traffic, parked, police, and whatever you're driving), and the police:
 * wanted stars, cop cars, cops on foot, getting busted.
 *
 * A heading works like a person's: 0 faces north, forward is (sin h, -cos h).
 */
final class CasinoTraffic {
    static final int SEDAN = 0, SUV = 1, TAXI = 2, PICKUP = 3, POLICE = 4;
    static final int TRAFFIC = 0, PARKED = 1, COP = 2;

    static final class Car {
        float x, z, h, speed, spin;
        int color, type, role;
        /** Someone's at the wheel (AI), apart from you. */
        boolean driver;
        float health = 100;
        /** Burning: seconds until it blows. Wrecked: a burnt shell. */
        float fire = -1;
        boolean wrecked;
        /** Lane following: the intersection behind and the one ahead. */
        int fi, fj, ti, tj;
        float stuck;
        boolean siren;
        /** You broke it: explosions count as yours. */
        boolean playerDamaged;
        List<CasinoWorld.Person> crew;
        float age;
        final int id = nextId++;
        /** Following its lane exactly (AI traffic): s along the lane, or u through a turn. */
        boolean rail;
        boolean turning;
        float s, u;
        int ni, nj;
        float[] bez;
        float knocked;
        /** A cruiser that lost you and is checking the area. */
        boolean searching;

        // ---- looking alive
        /** Front wheels' steering angle as drawn, body roll (lean), pitch (dive) and bounce. */
        float steerVis, roll, pitch, bounce, bounceV;
        float prevSpeed, prevH;
        /** Brake lights, reversing, indicator (-1 left, 1 right) and the engine shake. */
        float brake;
        boolean reversing;
        int blink;
        /** Sliding sideways (m/s) and the handbrake. */
        float slip;
        boolean handbrake;
        // ---- damage you can see, 0..1 per side: front, back, left, right
        final float[] dent = new float[4];
        boolean bumperF, bumperB = false, lightL, lightR, glassF, glassB, glassL, glassR;
        /** Seconds since last scraped (for sparks). */
        float scrapeT;
        // ---- Spray Shop Customs
        /** Rim style (RIM_*), neon color (0 none), tint 0..2, stripe color (0 none). */
        int rim, neon, tint, stripes;
        boolean spoiler, lowered;
        /** Engine and armor upgrades, 0..3. */
        int engine, armor;

        /** The car as a line of text, for the garage save. */
        String save() {
            return type + "," + color + "," + rim + "," + neon + "," + tint + "," + stripes + ","
                    + (spoiler ? 1 : 0) + "," + (lowered ? 1 : 0) + "," + engine + "," + armor;
        }

        static Car load(String s) {
            String[] f = s.split(",");
            if (f.length < 10) return null;
            try {
                Car c = new Car();
                c.type = Integer.parseInt(f[0]);
                c.color = Integer.parseInt(f[1]);
                c.rim = Integer.parseInt(f[2]);
                c.neon = Integer.parseInt(f[3]);
                c.tint = Integer.parseInt(f[4]);
                c.stripes = Integer.parseInt(f[5]);
                c.spoiler = f[6].equals("1");
                c.lowered = f[7].equals("1");
                c.engine = Integer.parseInt(f[8]);
                c.armor = Integer.parseInt(f[9]);
                if (c.type == POLICE) c.type = SEDAN;
                return c;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    static final int RIM_STEEL = 0, RIM_ALLOY = 1, RIM_CHROME = 2, RIM_GOLD = 3, RIM_BLACK = 4,
            RIM_SPINNER = 5;

    static final int FRONT = 0, BACK = 1, LEFT = 2, RIGHT = 3;
    private static final int TRIM_COLOR = 0xFF2A2A2E;

    /** Bits flying off cars: x, y, z, vx, vy, vz, spin, spinV, w, h, d, color, life. */
    final List<float[]> debris = new ArrayList<>();
    /** Skid marks on the road: x0, z0, x1, z1, age. */
    final List<float[]> skids = new ArrayList<>();

    private static int nextId;

    final List<Car> cars = new ArrayList<>();
    final CasinoWorld w;
    private final Random rnd;
    /** What you're driving, or null. */
    Car driving;

    // Wanted level.
    float heat;
    /** Seconds since a cop last saw you. */
    float unseen;
    boolean busted;
    int fineDue;
    String flash;
    /** Parked in the spray booth and it would help: the phone charges and calls respray(). */
    boolean sprayReady;
    private boolean sprayUsed;
    static final int SPRAY_COST = 300;

    private static final float PI_F = (float) Math.PI;
    static final float CAR_LEN = 4.5f, CAR_R = 1.0f, MAX_SPEED = 24, REVERSE = 7;
    static final int TRAFFIC_CARS = 16;

    CasinoTraffic(CasinoWorld w, long seed) {
        this.w = w;
        rnd = new Random(seed);
    }

    int stars() {
        return heat <= 0.05f ? 0 : Math.min(5, (int) Math.ceil(heat));
    }

    // ================================================================== setup

    void spawnAll() {
        for (float[] p : w.parkedCars) {
            Car c = new Car();
            c.x = p[0];
            c.z = p[1];
            c.h = p[2];
            c.color = (int) p[3];
            c.type = (int) p[4];
            c.role = PARKED;
            cars.add(c);
        }
        int[] colors = {0xFF9A1E1E, CasinoWorld.TAXI_YELLOW, 0xFFC8CCD2, 0xFF2A4E8A, 0xFF1C1C20,
                0xFF2E6A44, 0xFFE8E8EA, 0xFF5A2A5A, 0xFF8A6A3A, 0xFF3A3A44};
        int[] types = {SEDAN, TAXI, SUV, SEDAN, PICKUP, SUV, SEDAN, TAXI};
        for (int k = 0; k < TRAFFIC_CARS; k++) {
            Car c = new Car();
            c.role = TRAFFIC;
            c.driver = true;
            c.type = types[k % types.length];
            c.color = c.type == TAXI ? CasinoWorld.TAXI_YELLOW : colors[rnd.nextInt(colors.length)];
            placeOnRoad(c, k);
            cars.add(c);
        }
    }

    /** Puts a car somewhere along a random road segment, in its lane. */
    private void placeOnRoad(Car c, int k) {
        int n = CasinoCity.AVE_X.length;
        for (int tries = 0; tries < 30; tries++) {
            int i = rnd.nextInt(n), j = rnd.nextInt(n);
            int[] nb = neighbor(i, j, -1, -1);
            float t = 0.2f + rnd.nextFloat() * 0.6f;
            c.fi = i;
            c.fj = j;
            c.ti = nb[0];
            c.tj = nb[1];
            c.rail = true;
            c.turning = false;
            c.s = t * laneLen(c);
            railPos(c);
            // Not on top of the player or another car.
            if (CasinoWorld.dist2(c.x, c.z, w.player.x, w.player.z) < 400) continue;
            boolean clash = false;
            for (Car o : cars) {
                if (CasinoWorld.dist2(c.x, c.z, o.x, o.z) < 64) clash = true;
            }
            if (!clash) return;
        }
    }

    static float ix(int i) {
        return CasinoCity.AVE_X[i];
    }

    static float iz(int j) {
        return CasinoCity.ST_Z[j];
    }

    /** A random neighbouring intersection, avoiding going straight back if possible. */
    private int[] neighbor(int i, int j, int pi, int pj) {
        int n = CasinoCity.AVE_X.length;
        int[][] opts = {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}};
        List<int[]> ok = new ArrayList<>();
        for (int[] o : opts) {
            if (o[0] < 0 || o[1] < 0 || o[0] >= n || o[1] >= n) continue;
            if (o[0] == pi && o[1] == pj) continue;
            ok.add(o);
        }
        if (ok.isEmpty()) return new int[]{pi, pj};
        return ok.get(rnd.nextInt(ok.size()));
    }

    // ================================================================== every frame

    void update(float dt, float sx, float sy) {
        for (int k = cars.size() - 1; k >= 0; k--) {
            Car c = cars.get(k);
            c.age += dt;
            if (c == driving) {
                drive(c, dt, sx, sy);
            } else if (!c.wrecked && c.fire < 0 && c.driver) {
                if (c.role == COP) copDrive(c, dt);
                else trafficDrive(c, dt);
            } else {
                c.speed *= (float) Math.pow(0.05, dt);
                move(c, dt);
            }
            burn(c, dt);
            alive(c, dt);
        }
        updateDebris(dt);
        collideCars();
        hitPeople();
        police(dt);
        // The spray shop: stop inside the booth.
        Car d = driving;
        float[] b = CasinoCity.SPRAY;
        boolean inBooth = d != null && d.x > b[0] && d.x < b[2] && d.z > b[1] && d.z < b[3];
        if (!inBooth) sprayUsed = false;
        sprayReady = inBooth && !sprayUsed && Math.abs(d.speed) < 1.5f
                && d.fire < 0;
        // Keep the streets busy: replace wrecks and stolen cars with fresh traffic.
        int live = 0;
        for (Car c : cars) if (c.role == TRAFFIC && c.driver && !c.wrecked) live++;
        if (live < TRAFFIC_CARS) {
            Car c = new Car();
            c.role = TRAFFIC;
            c.driver = true;
            c.type = rnd.nextInt(4);
            c.color = c.type == TAXI ? CasinoWorld.TAXI_YELLOW : 0xFF000000
                    | (0x202020 + rnd.nextInt(0x9F9F9F));
            placeOnRoad(c, 0);
            if (!w.far(c.x, c.z) && CasinoWorld.dist2(c.x, c.z, w.player.x, w.player.z) < 30 * 30) {
                return;
            }
            cars.add(c);
        }
        // Clear out old wrecks you've left far behind.
        for (int k = cars.size() - 1; k >= 0; k--) {
            Car c = cars.get(k);
            if (c.wrecked && c.age > 25 && CasinoWorld.dist2(c.x, c.z, w.player.x, w.player.z) > 35 * 35) {
                cars.remove(k);
            }
        }
    }

    static float fx(float h) {
        return (float) Math.sin(h);
    }

    static float fz(float h) {
        return (float) -Math.cos(h);
    }

    private void move(Car c, float dt) {
        c.x += fx(c.h) * c.speed * dt + fx(c.h + 1.5708f) * c.slip * dt;
        c.z += fz(c.h) * c.speed * dt + fz(c.h + 1.5708f) * c.slip * dt;
        c.spin -= c.speed * dt / 0.34f;
        hitWalls(c);
    }

    /** You at the wheel. sy < 0 is the gas, sx steers. */
    private void drive(Car c, float dt, float sx, float sy) {
        if (c.wrecked || c.fire >= 0) {
            c.speed *= (float) Math.pow(0.05, dt);
            move(c, dt);
            return;
        }
        float throttle = -sy, steer = sx;
        if (Math.abs(throttle) < 0.12f) throttle = 0;
        steer = Math.abs(steer) < 0.1f ? 0 : (steer - Math.signum(steer) * 0.1f) / 0.9f;
        if (throttle > 0) {
            if (c.speed < -0.5f) c.speed += 18 * dt;
            else c.speed += 9 * (1 + 0.18f * c.engine) * throttle * dt
                    * (1 - c.speed / (MAX_SPEED * (1 + 0.1f * c.engine)) * 0.6f);
        } else if (throttle < 0) {
            if (c.speed > 0.5f) c.speed -= 20 * -throttle * dt;
            else c.speed -= 6 * -throttle * dt;
        } else {
            float d = Math.min(Math.abs(c.speed), 2.5f * dt);
            c.speed -= Math.signum(c.speed) * d;
        }
        c.speed = Math.max(-REVERSE, Math.min(MAX_SPEED * (1 + 0.1f * c.engine), c.speed));
        float v = Math.abs(c.speed);
        // Turns sharp at city speeds, a bit wider when you're flying.
        float rate = 2.7f * Math.min(1, v / 2.5f) / (1 + v * 0.028f);
        steerIn = steer;
        // Handbrake: the back end lets go, so it swings round and slides.
        if (c.handbrake && v > 3) {
            rate *= 1.7f;
            c.speed -= Math.signum(c.speed) * 5 * dt;
        }
        float yaw = steer * Math.signum(c.speed) * rate;
        c.h += yaw * dt;
        // More turn than the tires can hold turns into a slide (the body keeps going sideways).
        float grip = c.handbrake ? 3.5f : 11f;
        float demand = Math.abs(c.speed * yaw);
        if (demand > grip) c.slip -= Math.signum(c.speed * yaw) * (demand - grip) * dt * 0.8f;
        c.slip -= c.slip * Math.min(1, dt * (c.handbrake ? 0.8f : 2.6f));
        // Cranking the wheel at speed scrubs speed off, so corners actually make it round.
        if (Math.abs(steer) > 0.45f && v > 10) {
            c.speed -= Math.signum(c.speed) * 8 * Math.abs(steer) * dt;
        }
        // Let go of the wheel near a road's direction and the car straightens up onto it.
        if (steer == 0 && v > 2 && !c.handbrake && Math.abs(c.slip) < 1) {
            float axis = Math.round(c.h / (PI_F / 2)) * (PI_F / 2);
            float d = CasinoWorld.angleDiff(axis, c.h);
            if (Math.abs(d) < 0.35f) c.h += d * Math.min(1, dt * 3.5f);
        }
        move(c, dt);
    }

    // ---------------------------------------------------------------- lanes

    static final float STOP = CasinoCity.HALF + 1.5f;

    /** The lane from the intersection behind to the one ahead: start x, z, dir x, z. */
    private float[] lane(int fi, int fj, int ti, int tj) {
        float ax = ix(fi), az = iz(fj), bx = ix(ti), bz = iz(tj);
        float l = (float) Math.hypot(bx - ax, bz - az);
        float dx = (bx - ax) / l, dz = (bz - az) / l;
        float rx = -dz, rz = dx;
        return new float[]{ax + dx * STOP + rx * CasinoCity.LANE,
                az + dz * STOP + rz * CasinoCity.LANE, dx, dz, l - 2 * STOP};
    }

    private float laneLen(Car c) {
        return lane(c.fi, c.fj, c.ti, c.tj)[4];
    }

    /** Puts a lane car exactly where its lane (or turn) says. */
    private void railPos(Car c) {
        if (!c.turning) {
            float[] L = lane(c.fi, c.fj, c.ti, c.tj);
            c.x = L[0] + L[2] * c.s;
            c.z = L[1] + L[3] * c.s;
            c.h = (float) Math.atan2(L[2], -L[3]);
            return;
        }
        float[] b = c.bez;
        float u = c.u, v = 1 - u;
        c.x = v * v * b[0] + 2 * v * u * b[2] + u * u * b[4];
        c.z = v * v * b[1] + 2 * v * u * b[3] + u * u * b[5];
        float tx = 2 * v * (b[2] - b[0]) + 2 * u * (b[4] - b[2]);
        float tz = 2 * v * (b[3] - b[1]) + 2 * u * (b[5] - b[3]);
        if (Math.abs(tx) + Math.abs(tz) > 1e-4f) c.h = (float) Math.atan2(tx, -tz);
    }

    /** Sets up the curve through the intersection ahead into the next lane. */
    private void startTurn(Car c) {
        float[] in = lane(c.fi, c.fj, c.ti, c.tj);
        float[] out = lane(c.ti, c.tj, c.ni, c.nj);
        float p0x = in[0] + in[2] * in[4], p0z = in[1] + in[3] * in[4];
        float p2x = out[0], p2z = out[1];
        float p1x, p1z;
        if (Math.abs(in[2] * out[2] + in[3] * out[3]) > 0.9f) {
            p1x = (p0x + p2x) / 2;
            p1z = (p0z + p2z) / 2;
        } else if (Math.abs(in[2]) > 0.5f) {
            p1x = p2x;
            p1z = p0z;
        } else {
            p1x = p0x;
            p1z = p2z;
        }
        c.bez = new float[]{p0x, p0z, p1x, p1z, p2x, p2z,
                ((float) Math.hypot(p1x - p0x, p1z - p0z) + (float) Math.hypot(p2x - p1x, p2z - p1z)) * 0.9f};
        c.turning = true;
        c.u = 0;
    }

    /** Someone else is crossing this intersection the other way right now. */
    private boolean junctionBusy(Car c) {
        float[] in = lane(c.fi, c.fj, c.ti, c.tj);
        for (Car o : cars) {
            if (o == c || !o.rail || !o.turning || o.ti != c.ti || o.tj != c.tj) continue;
            float[] oi = lane(o.fi, o.fj, o.ti, o.tj);
            if (Math.abs(in[2] * oi[2] + in[3] * oi[3]) < 0.5f) return true;
        }
        return false;
    }

    /** Lane traffic: drive along the lane, wait at busy junctions, brake for what's ahead. */
    private void trafficDrive(Car c, float dt) {
        if (!c.rail) {
            recover(c, dt);
            return;
        }
        float target = c.turning ? 6.5f : 11;
        float len = laneLen(c);
        if (!c.turning) {
            float toEnd = len - c.s;
            if (toEnd < 14) target = Math.min(target, 4 + toEnd * 0.6f);
            if (c.ni == c.fi && c.nj == c.fj || c.ni == c.ti && c.nj == c.tj || (c.ni == 0 && c.nj == 0 && c.age < 0.1f)) {
                int[] nb = neighbor(c.ti, c.tj, c.fi, c.fj);
                c.ni = nb[0];
                c.nj = nb[1];
            }
            if (toEnd < 3 && junctionBusy(c)) target = 0;
        }
        float gap = clearAhead(c, 13);
        if (gap < 13) target = Math.min(target, Math.max(0, (gap - 4.2f) * 1.7f));
        float accel = target > c.speed ? 5 : 16;
        c.speed += Math.max(-accel * dt, Math.min(accel * dt, target - c.speed));
        c.speed = Math.max(0, c.speed);
        float ds = c.speed * dt;
        if (!c.turning) {
            c.s += ds;
            if (c.s >= len) {
                if (junctionBusy(c)) {
                    c.s = len;
                    c.speed = 0;
                } else {
                    startTurn(c);
                }
            }
        } else {
            c.u += ds / Math.max(1, c.bez[6]);
            if (c.u >= 1) {
                c.fi = c.ti;
                c.fj = c.tj;
                c.ti = c.ni;
                c.tj = c.nj;
                int[] nb = neighbor(c.ti, c.tj, c.fi, c.fj);
                c.ni = nb[0];
                c.nj = nb[1];
                c.turning = false;
                c.s = 0;
            }
        }
        railPos(c);
        c.spin -= ds / 0.34f;
    }

    /** Knocked off its lane: steer back onto it, then latch on again. */
    private void recover(Car c, float dt) {
        c.knocked -= dt;
        if (c.knocked > 0) {
            c.speed *= (float) Math.pow(0.1, dt);
            move(c, dt);
            return;
        }
        float[] L = lane(c.fi, c.fj, c.ti, c.tj);
        float proj = (c.x - L[0]) * L[2] + (c.z - L[1]) * L[3];
        float px = L[0] + L[2] * proj, pz = L[1] + L[3] * proj;
        float side = (float) Math.hypot(c.x - px, c.z - pz);
        float laneH = (float) Math.atan2(L[2], -L[3]);
        if (side < 0.8f && Math.abs(angle(c.h - laneH)) < 0.35f && proj > -2 && proj < L[4] + 2) {
            c.rail = true;
            c.turning = false;
            c.s = Math.max(0, Math.min(L[4], proj));
            return;
        }
        float ahead = Math.max(0, Math.min(L[4], proj + 7));
        float tx = L[0] + L[2] * ahead, tz = L[1] + L[3] * ahead;
        float want = (float) Math.atan2(tx - c.x, -(tz - c.z));
        steerTo(c, angle(want - c.h), 5, dt, 2.2f);
        c.stuck += dt;
        if (c.stuck > 12 || proj > L[4] + 6) {
            // Give up and snap back on: it happens off-camera often enough.
            c.rail = true;
            c.turning = false;
            c.s = Math.max(0, Math.min(L[4], proj));
            c.stuck = 0;
            railPos(c);
        }
    }

    private void steerTo(Car c, float diff, float target, float dt, float turn) {
        float accel = target > c.speed ? 6 : 14;
        c.speed += Math.max(-accel * dt, Math.min(accel * dt, target - c.speed));
        float grip = Math.max(0.3f, Math.min(1, c.speed / 4));
        c.h += Math.max(-1, Math.min(1, diff * 2.5f)) * turn * grip * dt;
        move(c, dt);
    }

    /** Distance to the nearest car or person in the lane ahead, or far if none. */
    private float clearAhead(Car c, float look) {
        float best = 99;
        float fx = fx(c.h), fz = fz(c.h);
        for (Car o : cars) {
            if (o == c) continue;
            float dx = o.x - c.x, dz = o.z - c.z;
            float ahead = dx * fx + dz * fz;
            if (ahead <= 0 || ahead > look) continue;
            float side = Math.abs(dx * fz - dz * fx);
            if (side >= 2.2f) continue;
            // Lane cars only queue behind cars going their way; junctions are handled apart.
            if (c.rail && o.rail && Math.abs(angle(o.h - c.h)) > 0.9f) continue;
            best = Math.min(best, ahead);
        }
        for (CasinoWorld.Person p : w.allPeople()) {
            if (!p.active || p.fall > 0.5f || (p == w.player && driving != null)) continue;
            float dx = p.x - c.x, dz = p.z - c.z;
            float ahead = dx * fx + dz * fz;
            if (ahead <= 0 || ahead > look) continue;
            float side = Math.abs(dx * fz - dz * fx);
            if (side < 1.6f) best = Math.min(best, ahead - 1);
        }
        return best;
    }

    static float angle(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    // ================================================================== crashes

    private final float[] probe = new float[2];

    /** The car's three body circles. */
    private float cx(Car c, int k) {
        return c.x + fx(c.h) * (k - 1) * 1.5f;
    }

    private float cz(Car c, int k) {
        return c.z + fz(c.h) * (k - 1) * 1.5f;
    }

    private void hitWalls(Car c) {
        float pushX = 0, pushZ = 0;
        for (int k = 0; k < 3; k++) {
            float px = cx(c, k), pz = cz(c, k);
            for (float[] wl : w.walls) {
                if (wl.length > 5 || wl[4] < 0.5f) continue;
                if (px < wl[0] - CAR_R || px > wl[2] + CAR_R || pz < wl[1] - CAR_R
                        || pz > wl[3] + CAR_R) {
                    continue;
                }
                float qx = Math.max(wl[0], Math.min(px, wl[2]));
                float qz = Math.max(wl[1], Math.min(pz, wl[3]));
                float dx = px - qx, dz = pz - qz, d = (float) Math.hypot(dx, dz);
                if (d >= CAR_R) continue;
                if (d < 1e-4f) {
                    dx = -fx(c.h);
                    dz = -fz(c.h);
                    d = 0;
                } else {
                    dx /= d;
                    dz /= d;
                }
                float in = CAR_R - d;
                pushX += dx * in;
                pushZ += dz * in;
                px += dx * in;
                pz += dz * in;
            }
            for (CasinoWorld.Door dr : w.doors) {
                if (!dr.shut()) continue;
                float[] b = dr.box();
                float qx = Math.max(b[0], Math.min(px, b[2])), qz = Math.max(b[1], Math.min(pz, b[3]));
                float dx = px - qx, dz = pz - qz, d = (float) Math.hypot(dx, dz);
                if (d >= CAR_R || d < 1e-4f) continue;
                pushX += dx / d * (CAR_R - d);
                pushZ += dz / d * (CAR_R - d);
            }
        }
        float wx0 = CasinoWorld.WX0 + 2, wx1 = CasinoWorld.WX1 - 2, wz0 = CasinoWorld.WZ0 + 2,
                wz1 = CasinoWorld.WZ1 - 2;
        if (c.x < wx0) pushX += wx0 - c.x;
        if (c.x > wx1) pushX += wx1 - c.x;
        if (c.z < wz0) pushZ += wz0 - c.z;
        if (c.z > wz1) pushZ += wz1 - c.z;
        float pl = (float) Math.hypot(pushX, pushZ);
        if (pl < 1e-4f) return;
        c.x += pushX;
        c.z += pushZ;
        float into = -(pushX * fx(c.h) + pushZ * fz(c.h)) / pl * Math.signum(c.speed);
        float v = Math.abs(c.speed);
        if (into > 0.5f) {
            // Head-on: bounce back and take damage.
            if (v > 8) {
                impact(c, (v - 8) * (c == driving ? 0.8f : 0.6f), c == driving, -pushX / pl,
                        -pushZ / pl, v);
            } else if (v > 3) {
                kick(c, v * 0.02f);
            }
            if (c == driving && v > 6) {
                w.player.shake = Math.min(0.6f, v * 0.03f);
                w.sounds |= 1;
            }
            c.speed *= -0.25f;
        } else {
            // A scrape: keep sliding along it.
            c.speed *= 0.93f;
            if (v > 14 && c == driving) damage(c, v * 0.004f, true);
            if (v > 5) scrape(c, -pushX / pl, -pushZ / pl, v);
        }
    }

    private void collideCars() {
        for (int a = 0; a < cars.size(); a++) {
            Car p = cars.get(a);
            for (int b = a + 1; b < cars.size(); b++) {
                Car q = cars.get(b);
                if (CasinoWorld.dist2(p.x, p.z, q.x, q.z) > 36) continue;
                if (p.rail && q.rail) continue;
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        float dx = cx(q, j) - cx(p, i), dz = cz(q, j) - cz(p, i);
                        float d = (float) Math.hypot(dx, dz);
                        if (d >= 2 * CAR_R || d < 1e-4f) continue;
                        float in = (2 * CAR_R - d) / 2;
                        dx /= d;
                        dz /= d;
                        p.x -= dx * in;
                        p.z -= dz * in;
                        q.x += dx * in;
                        q.z += dz * in;
                        // How fast they're closing along the contact line.
                        float vpx = fx(p.h) * p.speed, vpz = fz(p.h) * p.speed;
                        float vqx = fx(q.h) * q.speed, vqz = fz(q.h) * q.speed;
                        float rel = -((vqx - vpx) * dx + (vqz - vpz) * dz);
                        boolean mineHit = p == driving || q == driving;
                        if (rel > (mineHit ? 6 : 8)) {
                            boolean mine = p == driving || q == driving;
                            float k = mine ? 0.55f : 0.4f;
                            impact(p, rel * k, mine, dx, dz, rel);
                            impact(q, rel * k, mine, -dx, -dz, rel);
                            if (mine) {
                                w.player.shake = Math.min(0.5f, rel * 0.03f);
                                w.sounds |= 1;
                                if (q.role == COP || p.role == COP) addHeat(0.3f, "Hit a police car");
                            }
                        }
                        if (p.rail && (rel > 3 || q == driving)) knock(p);
                        if (q.rail && (rel > 3 || p == driving)) knock(q);
                        float avg = (p.speed + q.speed) / 2;
                        p.speed = p.speed * 0.6f + avg * 0.2f;
                        q.speed = q.speed * 0.6f + avg * 0.2f;
                    }
                }
            }
        }
    }

    /** Cars knock people down; fast ones kill. */
    private void hitPeople() {
        for (Car c : cars) {
            float v = Math.abs(c.speed);
            if (v < 2.5f) continue;
            for (CasinoWorld.Person p : w.allPeople()) {
                if (!p.active || p.dead || p.hidden) continue;
                if (p == w.player && driving != null) continue;
                boolean hit = false;
                for (int k = 0; k < 3 && !hit; k++) {
                    if (CasinoWorld.dist2(p.x, p.z, cx(c, k), cz(c, k)) < (CAR_R + 0.3f) * (CAR_R + 0.3f)) {
                        hit = true;
                    }
                }
                if (!hit) continue;
                float dir = Math.signum(c.speed);
                float kx = fx(c.h) * dir, kz = fz(c.h) * dir;
                if (p == w.player) {
                    w.player.pushX = kx * 9;
                    w.player.pushZ = kz * 9;
                    w.player.shake = 0.6f;
                    w.player.bleed = 8;
                    w.hurtPlayer(v * 2.2f);
                    w.spray(p.x, 1.3f, p.z, kx, kz, 12);
                    w.sounds |= 1;
                    w.toast = "Watch the road.";
                    c.speed *= 0.5f;
                    continue;
                }
                p.kx = kx * (v * 0.6f);
                p.kz = kz * (v * 0.6f);
                w.spray(p.x, 1.2f, p.z, kx, kz, 14);
                w.sounds |= 1;
                c.speed *= 0.85f;
                if (c == driving) {
                    if (v > 7) w.killBy(p, CasinoWorld.KILL_RUNOVER);
                    else {
                        p.down = CasinoWorld.DOWN_TIME;
                        p.health -= 40;
                        if (p.health <= 0) w.killBy(p, CasinoWorld.KILL_RUNOVER);
                        else w.crimeSeen(0.5f, "Hit a pedestrian");
                    }
                } else {
                    boolean blame = w.time - p.hitAt < 4;
                    if (v > 6) w.killBy(p, blame ? CasinoWorld.KILL_PUSH : CasinoWorld.KILL_CAR);
                    else p.down = CasinoWorld.DOWN_TIME;
                }
            }
        }
    }

    private void knock(Car c) {
        c.rail = false;
        c.turning = false;
        c.knocked = 1.5f;
        c.stuck = 0;
    }

    // ================================================================== looking alive

    /** Suspension, steering, lights and exhaust, every frame for every car. */
    private void alive(Car c, float dt) {
        if (dt <= 0) return;
        float acc = (c.speed - c.prevSpeed) / dt;
        float yawRate = CasinoWorld.angleDiff(c.h, c.prevH) / dt;
        c.prevSpeed = c.speed;
        c.prevH = c.h;
        // Nose dips when braking, squats when you floor it; leans out of turns.
        float tp = Math.max(-0.07f, Math.min(0.07f, acc * 0.009f));
        float tr = Math.max(-0.1f, Math.min(0.1f, c.speed * yawRate * 0.011f));
        if (c.wrecked) {
            tp = 0;
            tr = 0;
        }
        c.pitch += (tp - c.pitch) * Math.min(1, dt * 7);
        c.roll += (tr - c.roll) * Math.min(1, dt * 6);
        // A springy bounce after bumps.
        c.bounceV += (-c.bounce * 160 - c.bounceV * 9) * dt;
        c.bounce += c.bounceV * dt;
        // Front wheels follow the turn (worked out from how it's turning).
        float wantSteer = Math.abs(c.speed) > 0.3f
                ? Math.max(-0.55f, Math.min(0.55f, yawRate / Math.max(1, Math.abs(c.speed)) * 2.6f
                * Math.signum(c.speed))) : c.steerVis;
        if (c == driving && Math.abs(c.speed) < 0.3f) wantSteer = steerIn * 0.55f;
        c.steerVis += (wantSteer - c.steerVis) * Math.min(1, dt * 10);
        c.brake = Math.max(0, c.brake - dt * 3);
        if (acc < -3 && c.speed > 0.5f || c.handbrake) c.brake = 1;
        c.reversing = c.speed < -0.3f;
        // Turn signals: AI cars blink into their turns.
        c.blink = c.turning && c.bez != null ? (yawRate > 0.05f ? 1 : yawRate < -0.05f ? -1 : c.blink)
                : 0;
        // Exhaust: a puff now and then, more when the gas goes down.
        if ((c.driver || c == driving) && !c.wrecked && rnd.nextFloat() < dt * (acc > 2 ? 9 : 1.2f)
                && !w.far(c.x, c.z)) {
            float bx = c.x - fx(c.h) * 2.3f, bz = c.z - fz(c.h) * 2.3f;
            w.exhaust(bx, bz);
        }
        if (c.scrapeT > 0) c.scrapeT -= dt;
        // Skid marks when sliding or locking up.
        if (c == driving && (Math.abs(c.slip) > 1.6f || c.handbrake && Math.abs(c.speed) > 4
                || acc < -14 && Math.abs(c.speed) > 6)) {
            for (int side = -1; side <= 1; side += 2) {
                float rx = c.x - fx(c.h) * 1.4f + fx(c.h + 1.5708f) * 0.8f * side;
                float rz = c.z - fz(c.h) * 1.4f + fz(c.h + 1.5708f) * 0.8f * side;
                skids.add(new float[]{rx, rz, rx - fx(c.h) * 0.5f, rz - fz(c.h) * 0.5f, 0});
            }
            while (skids.size() > 600) skids.remove(0);
            w.sounds |= 512;
        }
    }

    /** The steering you're holding (for the wheels when parked). */
    float steerIn;

    /** A knock to the suspension. */
    private static void kick(Car c, float amount) {
        c.bounceV -= amount * 6;
    }

    /**
     * Damage with a direction (toward what was hit, world space): the side that hit dents,
     * lights and glass break, bumpers come off, sparks and bits fly.
     */
    void impact(Car c, float amount, boolean byPlayer, float dirX, float dirZ, float speed) {
        if (c.wrecked) {
            kick(c, 0.2f);
            return;
        }
        damage(c, amount, byPlayer);
        float l = (float) Math.hypot(dirX, dirZ);
        if (l < 1e-4f) return;
        dirX /= l;
        dirZ /= l;
        float along = dirX * fx(c.h) + dirZ * fz(c.h);
        float side = dirX * fx(c.h + 1.5708f) + dirZ * fz(c.h + 1.5708f);
        int where = Math.abs(along) > Math.abs(side) * 1.2f ? (along > 0 ? FRONT : BACK)
                : (side > 0 ? RIGHT : LEFT);
        float add = amount * (1 - 0.22f * c.armor) / 55f;
        c.dent[where] = Math.min(1, c.dent[where] + add);
        if (where == FRONT || where == BACK) {
            // Corners take some of it too.
            int s2 = side > 0 ? RIGHT : LEFT;
            c.dent[s2] = Math.min(1, c.dent[s2] + add * 0.3f);
        }
        kick(c, Math.min(1.2f, speed * 0.05f));
        float hx = c.x + dirX * (where < 2 ? 2.2f : 0.9f), hz = c.z + dirZ * (where < 2 ? 2.2f : 0.9f);
        // Sparks at the point of impact.
        int n = (int) Math.min(18, 4 + speed);
        for (int i = 0; i < n; i++) {
            w.sparks.add(new float[]{hx, 0.6f + rnd.nextFloat() * 0.4f, hz,
                    -dirX * 4 + (rnd.nextFloat() - 0.5f) * 6, 1 + rnd.nextFloat() * 3,
                    -dirZ * 4 + (rnd.nextFloat() - 0.5f) * 6, 0.35f + rnd.nextFloat() * 0.3f});
        }
        float dmg = c.dent[where];
        int col = c.color;
        if (where == FRONT) {
            if (dmg > 0.3f && !c.lightL && rnd.nextBoolean()) {
                c.lightL = true;
                glassBits(hx, hz, 0xFFE8EEF0, 4);
            }
            if (dmg > 0.3f && !c.lightR && rnd.nextBoolean()) {
                c.lightR = true;
                glassBits(hx, hz, 0xFFE8EEF0, 4);
            }
            if (dmg > 0.5f && !c.glassF) {
                c.glassF = true;
                glassBits(hx, hz, 0xFF9AB8C8, 10);
            }
            if (dmg > 0.6f && !c.bumperF) {
                c.bumperF = true;
                flyOff(c, hx, hz, dirX, dirZ, 1.8f, 0.22f, 0.24f, 0xFF2A2A2E, speed);
            }
        } else if (where == BACK) {
            if (dmg > 0.5f && !c.glassB) {
                c.glassB = true;
                glassBits(hx, hz, 0xFF9AB8C8, 8);
            }
            if (dmg > 0.55f && !c.bumperB) {
                c.bumperB = true;
                flyOff(c, hx, hz, dirX, dirZ, 1.8f, 0.22f, 0.24f, 0xFF2A2A2E, speed);
            }
        } else {
            boolean left = where == LEFT;
            if (dmg > 0.45f && !(left ? c.glassL : c.glassR)) {
                if (left) c.glassL = true;
                else c.glassR = true;
                glassBits(hx, hz, 0xFF9AB8C8, 8);
            }
            if (dmg > 0.7f && rnd.nextFloat() < 0.5f) {
                // A strip of trim or a mirror.
                flyOff(c, hx, hz, dirX, dirZ, 0.25f, 0.12f, 0.2f, col, speed);
            }
        }
        // A few flakes of paint on any decent hit.
        if (speed > 6) {
            for (int i = 0; i < 3; i++) {
                flyOff(c, hx, hz, dirX, dirZ, 0.12f + rnd.nextFloat() * 0.15f, 0.03f,
                        0.1f + rnd.nextFloat() * 0.1f, col, speed * 0.6f);
            }
        }
    }

    /** Scraping along a wall: a trail of sparks. */
    private void scrape(Car c, float dirX, float dirZ, float v) {
        if (c.scrapeT > 0) return;
        c.scrapeT = 0.05f;
        float hx = c.x + dirX * 0.95f, hz = c.z + dirZ * 0.95f;
        for (int i = 0; i < 3; i++) {
            w.sparks.add(new float[]{hx, 0.45f, hz, -fx(c.h) * v * 0.3f + (rnd.nextFloat() - 0.5f) * 2,
                    1 + rnd.nextFloat() * 2, -fz(c.h) * v * 0.3f + (rnd.nextFloat() - 0.5f) * 2,
                    0.3f});
        }
        int s = Math.abs(dirX * fx(c.h + 1.5708f) + dirZ * fz(c.h + 1.5708f)) > 0
                && dirX * fx(c.h + 1.5708f) + dirZ * fz(c.h + 1.5708f) > 0 ? RIGHT : LEFT;
        c.dent[s] = Math.min(1, c.dent[s] + 0.004f);
    }

    private void flyOff(Car c, float x, float z, float dirX, float dirZ, float sw, float sh,
                        float sd, int color, float speed) {
        float v = Math.min(9, 2 + speed * 0.35f);
        debris.add(new float[]{x, 0.6f, z, -dirX * v * 0.6f + (rnd.nextFloat() - 0.5f) * 3,
                2 + rnd.nextFloat() * 3, -dirZ * v * 0.6f + (rnd.nextFloat() - 0.5f) * 3,
                rnd.nextFloat() * 6, (rnd.nextFloat() - 0.5f) * 14, sw, sh, sd, color, 0,
                c.h});
        while (debris.size() > 80) debris.remove(0);
    }

    private void glassBits(float x, float z, int color, int n) {
        for (int i = 0; i < n; i++) {
            debris.add(new float[]{x, 0.9f, z, (rnd.nextFloat() - 0.5f) * 4,
                    1 + rnd.nextFloat() * 2.5f, (rnd.nextFloat() - 0.5f) * 4,
                    rnd.nextFloat() * 6, (rnd.nextFloat() - 0.5f) * 20, 0.06f, 0.015f, 0.05f,
                    color, 0, rnd.nextFloat() * 6});
        }
        while (debris.size() > 80) debris.remove(0);
    }

    /** Bits fly, bounce and come to rest; they stay a while, then go. */
    private void updateDebris(float dt) {
        for (int i = debris.size() - 1; i >= 0; i--) {
            float[] b = debris.get(i);
            b[12] += dt;
            if (b[12] > 45) {
                debris.remove(i);
                continue;
            }
            if (b[1] <= b[9] / 2 + 0.005f && Math.abs(b[4]) < 0.3f && Math.hypot(b[3], b[5]) < 0.3f) {
                b[3] = b[4] = b[5] = 0;
                b[7] = 0;
                continue;
            }
            b[4] -= 9.8f * dt;
            b[0] += b[3] * dt;
            b[1] += b[4] * dt;
            b[2] += b[5] * dt;
            b[6] += b[7] * dt;
            float floor = b[9] / 2 + 0.005f;
            if (b[1] < floor) {
                b[1] = floor;
                b[4] = -b[4] * 0.3f;
                b[3] *= 0.6f;
                b[5] *= 0.6f;
                b[7] *= 0.5f;
                // Settle flat once it stops hopping.
                if (Math.abs(b[4]) < 0.6f) b[6] = Math.round(b[6] / 3.1416f) * 3.1416f;
            }
        }
        for (float[] k : skids) k[4] += dt;
    }

    /** A repair job at the shop: dents out, glass and lights replaced, bumpers back on. */
    void fix(Car c) {
        repair(c);
        c.fire = -1;
    }

    /** The spray shop fixes the body too. */
    private static void repair(Car c) {
        java.util.Arrays.fill(c.dent, 0);
        c.bumperF = c.bumperB = c.lightL = c.lightR = false;
        c.glassF = c.glassB = c.glassL = c.glassR = false;
    }

    void damage(Car c, float amount, boolean byPlayer) {
        if (c.wrecked || amount <= 0) return;
        amount *= 1 - 0.22f * c.armor;
        c.health -= amount;
        if (byPlayer) c.playerDamaged = true;
        if (c.health <= 0 && c.fire < 0) {
            c.fire = 9f;
            c.health = 0;
            if (c == driving) w.toast = "Your car's on fire! You've got a few seconds. Get out!";
        }
    }

    private void burn(Car c, float dt) {
        if (c.fire < 0 || c.wrecked) {
            // Steam from the radiator once it's hurt, thick smoke when it's badly hurt.
            float rate = c.health < 35 ? 7 : (c.health < 65 ? 2 : 0);
            if (!c.wrecked && rate > 0 && rnd.nextFloat() < dt * rate) {
                w.smoke(c.x + fx(c.h) * 1.6f, 1.0f, c.z + fz(c.h) * 1.6f, false);
            }
            return;
        }
        c.fire -= dt;
        if (rnd.nextFloat() < dt * 25) w.smoke(c.x + fx(c.h) * 1.5f, 1.0f, c.z + fz(c.h) * 1.5f, true);
        if (c.fire <= 0) explode(c);
    }

    private void explode(Car c) {
        c.wrecked = true;
        c.fire = -1;
        c.driver = false;
        c.siren = false;
        c.speed = 0;
        c.age = 0;
        w.sounds |= 128;
        w.blast(c.x, c.z, c.playerDamaged);
        // The shell jumps, everything that can come off does.
        c.bounceV = 7;
        java.util.Arrays.fill(c.dent, 0.85f);
        c.glassF = c.glassB = c.glassL = c.glassR = c.lightL = c.lightR = true;
        int col = c.color;
        for (int i = 0; i < 4; i++) {
            float a = rnd.nextFloat() * 6.283f;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            flyOff(c, c.x, c.z, -dx, -dz, i == 0 ? 1.4f : 1.1f, 0.06f, i == 0 ? 1.3f : 0.9f,
                    i < 2 ? col : 0xFF1E1A18, 24);
        }
        if (!c.bumperF) {
            c.bumperF = true;
            flyOff(c, c.x + fx(c.h) * 2.2f, c.z + fz(c.h) * 2.2f, -fx(c.h), -fz(c.h), 1.8f,
                    0.22f, 0.24f, TRIM_COLOR, 20);
        }
        glassBits(c.x, c.z, 0xFF9AB8C8, 14);
        for (Car o : cars) {
            if (o == c) continue;
            float d = (float) Math.hypot(o.x - c.x, o.z - c.z);
            if (d < 5) impact(o, (5 - d) * 9, c.playerDamaged, c.x - o.x, c.z - o.z, 12);
        }
        if (c == driving) w.exitCar(true);
    }

    // ================================================================== getting in and out

    /** The car you could get into from here, or null. */
    Car carNear() {
        if (driving != null) return null;
        Car best = null;
        float bestD = 3.0f * 3.0f;
        for (Car c : cars) {
            if (c.wrecked || c.fire >= 0 || Math.abs(c.speed) > 3) continue;
            float d = CasinoWorld.dist2(c.x, c.z, w.player.x, w.player.z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    String enterLabel(Car c) {
        if (c.role == COP) return "Steal police car";
        if (c.driver) return "Carjack";
        return "Get in";
    }

    void enter(Car c) {
        if (c.driver && c.role == TRAFFIC) {
            // Drag the driver out; they run (or swing at you).
            CasinoWorld.Person p = w.spawnPedestrian(c.x - (float) Math.cos(c.h) * 1.6f,
                    c.z - (float) Math.sin(c.h) * 1.6f);
            p.flee = 6;
            p.bubble = "My car!";
            p.bubbleT = 3;
            w.crimeSeen(0.6f, "Carjacking");
        }
        if (c.role == COP) addHeat(1.0f, "Stole a police car");
        if (c.crew != null) c.crew = null;
        c.driver = false;
        c.rail = false;
        c.role = TRAFFIC;
        driving = c;
        c.speed = 0;
    }

    /** Where you get out: the driver's side, or the other side if that's blocked. */
    float[] exitSpot(Car c) {
        float lx = (float) -Math.cos(c.h), lz = (float) -Math.sin(c.h);
        for (int side = 1; side >= -1; side -= 2) {
            float x = c.x + lx * 1.6f * side, z = c.z + lz * 1.6f * side;
            if (w.isFree(x, z)) return new float[]{x, z};
        }
        return new float[]{c.x + fx(c.h) * -3, c.z + fz(c.h) * -3};
    }

    /** New paint, fixed engine, and the cops lose track of you. */
    void respray() {
        Car d = driving;
        if (d == null) return;
        sprayUsed = true;
        sprayReady = false;
        int[] colors = {0xFF9A1E1E, 0xFF2A4E8A, 0xFFC8CCD2, 0xFF1C1C20, 0xFF2E6A44, 0xFFE8E8EA,
                0xFF5A2A5A, 0xFFB86A1A};
        int c = d.color;
        while (c == d.color) c = colors[rnd.nextInt(colors.length)];
        d.color = c;
        if (d.type == POLICE) d.type = SEDAN;
        d.health = 100;
        repair(d);
        clearHeat();
        reset();
    }

    void skipSpray() {
        sprayUsed = true;
        sprayReady = false;
    }

    // ================================================================== police

    final List<CasinoWorld.Person> cops = new ArrayList<>();
    private float spawnCool;

    /** Where the cops think you are. */
    float lastX, lastZ;
    private float shotsCool;

    /**
     * A crime. Cops who see it raise your stars freely; a civilian calling it in can only
     * get you up to two.
     */
    void report(float amount, String why, boolean copWitness) {
        int before = stars();
        if (copWitness) {
            heat = Math.min(5, heat + amount);
            unseen = 0;
        } else {
            heat = Math.max(heat, Math.min(2, heat + amount * 0.6f));
        }
        if (heat > 0 && (copWitness || before == 0)) {
            lastX = w.player.x;
            lastZ = w.player.z;
        }
        if (stars() > before) flash = why + ": wanted " + stars() + (stars() == 1 ? " star" : " stars");
    }

    void addHeat(float amount, String why) {
        report(amount, why, copSees());
    }

    /** Gunfire near the police, counted once every few seconds rather than per bullet. */
    void shotsFired() {
        if (shotsCool > 0) return;
        shotsCool = 6;
        boolean seen = copSees();
        report(seen ? 0.5f : 1f, "Shots fired", seen);
    }

    void clearHeat() {
        heat = 0;
        unseen = 0;
        for (Car c : cars) {
            if (c.role == COP) {
                c.siren = false;
            }
        }
        for (CasinoWorld.Person p : cops) p.target = false;
    }

    /** Whether any cop can see you right now. */
    boolean copSees() {
        float px = w.player.x, pz = w.player.z;
        for (CasinoWorld.Person p : cops) {
            if (!p.active || p.dead || p.ko || p.down > 0) continue;
            if (w.canSee(p, px, pz, 22)) return true;
        }
        for (Car c : cars) {
            if (c.role != COP || c.wrecked || (!c.driver && c.crew == null)) continue;
            if (CasinoWorld.dist2(c.x, c.z, px, pz) > 26 * 26 || !w.clear(c.x, c.z, px, pz)) continue;
            // Cruisers look ahead and to the sides, not straight behind.
            float dx = px - c.x, dz = pz - c.z, d = Math.max(0.1f, (float) Math.hypot(dx, dz));
            if ((dx * fx(c.h) + dz * fz(c.h)) / d > -0.3f || d < 8) return true;
        }
        return false;
    }

    private void police(float dt) {
        int s = stars();
        if (s == 0) {
            // Cops go back to patrol and disappear once you can't see them.
            for (int k = cars.size() - 1; k >= 0; k--) {
                Car c = cars.get(k);
                if (c.role == COP && c != driving && c.driver
                        && CasinoWorld.dist2(c.x, c.z, w.player.x, w.player.z) > 45 * 45) {
                    cars.remove(k);
                }
            }
            for (int k = cops.size() - 1; k >= 0; k--) {
                CasinoWorld.Person p = cops.get(k);
                if (CasinoWorld.dist2(p.x, p.z, w.player.x, w.player.z) > 40 * 40 || !p.active) {
                    w.people.remove(p);
                    cops.remove(k);
                }
            }
            return;
        }
        if (shotsCool > 0) shotsCool -= dt;
        if (copSees()) {
            unseen = 0;
            lastX = w.player.x;
            lastZ = w.player.z;
        } else {
            unseen += dt;
            if (unseen > 6 + 3 * s) {
                clearHeat();
                flash = "You lost the cops.";
                return;
            }
        }
        // Enough cruisers for your stars.
        int[] perStar = {0, 1, 1, 2, 3, 4};
        int want = perStar[s];
        int have = 0;
        for (Car c : cars) if (c.role == COP && c.driver && !c.wrecked) have++;
        spawnCool -= dt;
        if (have < want && spawnCool <= 0) {
            spawnCool = 4;
            spawnCruiser();
        }
    }

    private void spawnCruiser() {
        // An intersection 35-80 m away, preferably out of sight.
        int n = CasinoCity.AVE_X.length;
        int bi = -1, bj = -1;
        float bestScore = -1;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float d = (float) Math.hypot(ix(i) - w.player.x, iz(j) - w.player.z);
                if (d < 35 || d > 110) continue;
                float dl = (float) Math.hypot(ix(i) - lastX, iz(j) - lastZ);
                float score = 100 - Math.abs(dl - 45) + (w.clear(ix(i), iz(j), w.player.x, w.player.z) ? 0 : 30)
                        + rnd.nextFloat() * 20;
                if (score > bestScore) {
                    bestScore = score;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (bi < 0) return;
        Car c = new Car();
        c.role = COP;
        c.type = POLICE;
        c.color = 0xFF101014;
        c.driver = true;
        c.siren = true;
        c.health = 140;
        c.x = ix(bi);
        c.z = iz(bj);
        c.fi = c.ti = bi;
        c.fj = c.tj = bj;
        c.h = (float) Math.atan2(lastX - c.x, -(lastZ - c.z));
        cars.add(c);
    }

    /** A cruiser: heads for you, rams your car, or stops and lets the cops out. */
    private void copDrive(Car c, float dt) {
        if (stars() == 0) {
            c.siren = false;
            trafficFallback(c, dt);
            return;
        }
        c.siren = true;
        boolean seen = unseen < 1.5f;
        float tx = lastX, tz = lastZ;
        if (seen) {
            tx = w.player.x;
            tz = w.player.z;
            c.searching = false;
            if (driving != null) {
                tx += fx(driving.h) * driving.speed * 0.6f;
                tz += fz(driving.h) * driving.speed * 0.6f;
            }
        } else if (c.searching || Math.hypot(lastX - c.x, lastZ - c.z) < 12) {
            // Got to where you were last seen: drive the nearby blocks looking for you.
            if (!c.searching) {
                c.searching = true;
                c.ti = nearestI(c.x);
                c.tj = nearestJ(c.z);
            }
            if (Math.hypot(ix(c.ti) - c.x, iz(c.tj) - c.z) < 7) {
                int[] nb = neighbor(c.ti, c.tj, c.fi, c.fj);
                c.fi = c.ti;
                c.fj = c.tj;
                c.ti = nb[0];
                c.tj = nb[1];
            }
            tx = ix(c.ti);
            tz = iz(c.tj);
            float want = (float) Math.atan2(tx - c.x, -(tz - c.z));
            steerTo(c, angle(want - c.h), 9, dt, 2.4f);
            return;
        }
        float d = (float) Math.hypot(tx - c.x, tz - c.z);
        boolean direct = d < 32 && w.clear(c.x, c.z, tx, tz);
        if (!direct) {
            // Head for the next intersection along the roads, toward you.
            float wx = ix(c.ti), wz = iz(c.tj);
            if (Math.hypot(wx - c.x, wz - c.z) < 6) {
                int n = CasinoCity.AVE_X.length;
                int[][] opts = {{c.ti - 1, c.tj}, {c.ti + 1, c.tj}, {c.ti, c.tj - 1},
                        {c.ti, c.tj + 1}};
                float bestD = 1e9f;
                int bi = c.ti, bj = c.tj;
                for (int[] o : opts) {
                    if (o[0] < 0 || o[1] < 0 || o[0] >= n || o[1] >= n) continue;
                    if (o[0] == c.fi && o[1] == c.fj && rnd.nextFloat() < 0.7f) continue;
                    float od = (float) Math.hypot(ix(o[0]) - tx, iz(o[1]) - tz);
                    if (od < bestD) {
                        bestD = od;
                        bi = o[0];
                        bj = o[1];
                    }
                }
                c.fi = c.ti;
                c.fj = c.tj;
                c.ti = bi;
                c.tj = bj;
                wx = ix(c.ti);
                wz = iz(c.tj);
            }
            // Already on the road you're next to: just drive at you along it.
            boolean sameRoad = (c.fi == c.ti && Math.abs(tx - ix(c.ti)) < 9
                    && Math.min(iz(c.fj), iz(c.tj)) - 4 < tz && tz < Math.max(iz(c.fj), iz(c.tj)) + 4)
                    || (c.fj == c.tj && Math.abs(tz - iz(c.tj)) < 9
                    && Math.min(ix(c.fi), ix(c.ti)) - 4 < tx && tx < Math.max(ix(c.fi), ix(c.ti)) + 4);
            if (!sameRoad || d > 60) {
                tx = wx;
                tz = wz;
            }
        }
        float want = (float) Math.atan2(tx - c.x, -(tz - c.z));
        float diff = angle(want - c.h);
        float target = driving != null ? 21 : (d > 18 ? 16 : Math.max(0, (d - 7) * 1.5f));
        if (Math.abs(diff) > 1.2f) target = Math.min(target, 6);
        // Reverse out when stuck against a wall.
        if (c.stuck > 1.5f) {
            c.speed = Math.max(c.speed - 12 * dt, -5);
            c.h -= Math.signum(diff) * 1.2f * dt;
            move(c, dt);
            c.stuck -= dt * 0.6f;
            if (c.stuck < 0.6f) c.stuck = 0;
            return;
        }
        steerTo(c, diff, target, dt, 2.6f);
        if (Math.abs(c.speed) < 1 && target > 3) c.stuck += dt;
        else if (c.stuck < 1.5f) c.stuck = 0;
        // You're on foot and close: out they come.
        if (driving == null && seen && d < 14 && Math.abs(c.speed) < 2 && c.crew == null) {
            c.crew = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                float side = k == 0 ? 1 : -1;
                CasinoWorld.Person p = w.spawnCop(c.x - (float) Math.cos(c.h) * 1.5f * side,
                        c.z - (float) Math.sin(c.h) * 1.5f * side);
                c.crew.add(p);
                cops.add(p);
            }
            c.driver = false;
        }
    }

    private static int nearestI(float x) {
        int best = 0;
        for (int i = 1; i < CasinoCity.AVE_X.length; i++) {
            if (Math.abs(CasinoCity.AVE_X[i] - x) < Math.abs(CasinoCity.AVE_X[best] - x)) best = i;
        }
        return best;
    }

    private static int nearestJ(float z) {
        int best = 0;
        for (int j = 1; j < CasinoCity.ST_Z.length; j++) {
            if (Math.abs(CasinoCity.ST_Z[j] - z) < Math.abs(CasinoCity.ST_Z[best] - z)) best = j;
        }
        return best;
    }

    private void trafficFallback(Car c, float dt) {
        c.speed *= (float) Math.pow(0.3, dt);
        move(c, dt);
    }

    /** Called when an arrest happens or you go down. */
    void reset() {
        clearHeat();
        for (int k = cars.size() - 1; k >= 0; k--) {
            if (cars.get(k).role == COP && cars.get(k) != driving) cars.remove(k);
        }
        for (CasinoWorld.Person p : cops) w.people.remove(p);
        cops.clear();
    }

    // ================================================================== drawing

    void draw(CasinoWorld.PartSink s) {
        for (Car c : cars) {
            if (w.far(c.x, c.z)) continue;
            int col = c.wrecked ? 0xFF1E1A18 : c.color;
            w.drawCar(s, c, col, c.type == POLICE ? SEDAN : c.type,
                    !c.wrecked && (c.driver || c == driving));
            if (c.type == POLICE && !c.wrecked) w.drawPoliceBits(s, c.siren);
        }
        float[] m = new float[16];
        for (float[] b : debris) {
            if (w.far(b[0], b[2])) continue;
            CasinoWorld.Mat.identity(m);
            CasinoWorld.Mat.translate(m, b[0], b[1], b[2]);
            CasinoWorld.Mat.rotY(m, b[13]);
            CasinoWorld.Mat.rotX(m, b[6]);
            CasinoWorld.Mat.scale(m, b[8], b[9], b[10]);
            s.part(m, (int) b[11], 0);
        }
        for (float[] k : skids) {
            if (w.far(k[0], k[1])) continue;
            float dx = k[2] - k[0], dz = k[3] - k[1];
            CasinoWorld.Mat.identity(m);
            CasinoWorld.Mat.translate(m, (k[0] + k[2]) / 2, 0.012f, (k[1] + k[3]) / 2);
            CasinoWorld.Mat.rotY(m, (float) Math.atan2(dx, dz));
            CasinoWorld.Mat.scale(m, 0.22f, 0.004f, (float) Math.hypot(dx, dz) + 0.05f);
            s.part(m, k[4] < 20 ? 0xFF0A0A0C : 0xFF141416, 0);
        }
    }
}
