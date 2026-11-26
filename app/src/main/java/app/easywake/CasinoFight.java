package app.easywake;

import java.util.Random;

/**
 * Hand-to-hand fighting: your punches, kicks, blocks, parries and dodges, and people who
 * fight back with their own styles. Fists knock people out; they don't kill.
 */
final class CasinoFight {
    // Moves. Attacks first, then the other actions.
    static final int NONE = 0, JAB = 1, CROSS = 2, HOOK = 3, UPPER = 4, KICK = 5, ROUND = 6,
            STOMP = 7, BLOCK = 8, HIT = 9, DODGE = 10, STAGGER = 11;
    static final String[] NAMES = {"", "jab", "cross", "hook", "uppercut", "kick", "roundhouse",
            "stomp"};
    /** Wind-up before the hit lands, recovery after, reach, damage. */
    static final float[] WIND = {0, 0.07f, 0.09f, 0.16f, 0.12f, 0.2f, 0.28f, 0.2f};
    static final float[] REC = {0, 0.17f, 0.2f, 0.3f, 0.35f, 0.32f, 0.45f, 0.4f};
    static final float[] REACH = {0, 1.3f, 1.35f, 1.3f, 1.25f, 1.6f, 1.7f, 1.6f};
    static final float[] DMG = {0, 7, 10, 15, 24, 13, 24, 30};
    /** 0 a flinch, 1 a stagger, 2 a knockdown. */
    static final int[] KNOCK = {0, 0, 0, 1, 2, 1, 2, 0};
    static final boolean[] BREAKS_GUARD = {false, false, false, false, false, true, true, false};
    /** How much longer people wind up than you, so you can see it coming. */
    static final float[] TELL = {0, 0.3f, 0.32f, 0.45f, 0.4f, 0.48f, 0.55f, 0.3f};

    /** Fighting styles. */
    static final int BRAWLER = 0, BOXER = 1, DRUNK = 2, BRUISER = 3;
    static final String[][] TAUNTS = {
            {"Come on then!", "That all you got?", "I'm gonna rearrange your face."},
            {"Keep your hands up, kid.", "Float like a butterfly.", "Too slow."},
            {"I'll fight all of yous!", "Hold my drink.", "Which one of you is real?"},
            {"You picked the wrong guy.", "I eat guys like you for breakfast.", "Big mistake."}};
    static final String[] GIVE_UP = {"Okay, okay! I'm done!", "You win, man!", "Not the face!",
            "I've got a family!"};
    static final String[] CHALLENGE = {"You got a problem?", "What are you looking at?",
            "You bumped me, pal.", "Nice shirt. Wanna fight?", "Step outside. Oh wait."};

    private final CasinoWorld w;
    private final Random rnd;
    /** Your queued input while a move is still playing: 0 none, 1 punch, 2 kick. */
    private int queued;
    private int chain;
    private float chainT, parryT;
    /** Slow motion left (real seconds). */
    float slowmo;
    /** A big word in the middle of the screen. */
    String bigText;
    float bigT;
    private float challengeT = 45;
    private CasinoWorld.Person challenger;

    CasinoFight(CasinoWorld w, long seed) {
        this.w = w;
        rnd = new Random(seed ^ 0x5EED);
    }

    float timeScale() {
        return slowmo > 0 ? 0.35f : 1;
    }

    static boolean attacking(CasinoWorld.Person p) {
        return p.act >= JAB && p.act <= STOMP;
    }

    static boolean busy(CasinoWorld.Person p) {
        return p.act != NONE && p.act != BLOCK;
    }

    private static void start(CasinoWorld.Person p, int move, float extra) {
        p.act = move;
        p.actT = 0;
        p.actWind = (move <= STOMP ? WIND[move] : 0) + extra;
        p.actDur = move <= STOMP ? p.actWind + REC[move] : 0.3f;
        p.actHit = false;
    }

    // ================================================================== you

    void punchPressed() {
        input(1);
    }

    void kickPressed() {
        input(2);
    }

    private void input(int kind) {
        CasinoWorld.Person me = w.player;
        if (me.down > 0 || w.escort > 0 || w.traffic.driving != null) return;
        if (busy(me) && me.act != HIT) {
            // Buffer it for when this move's nearly done.
            queued = kind;
            return;
        }
        begin(kind);
    }

    private void begin(int kind) {
        CasinoWorld.Person me = w.player;
        queued = 0;
        CasinoWorld.Person t = focus(2.2f);
        if (t != null) me.yaw = (float) Math.atan2(t.x - me.x, -(t.z - me.z));
        int move;
        if (t != null && (t.down > 0 || t.fall > 0.5f) && !t.dead) {
            move = STOMP;
        } else if (kind == 1) {
            if (parryT > 0) {
                move = UPPER;
                parryT = 0;
            } else {
                move = chainT > 0 ? (chain == 0 ? CROSS : (chain == 1 ? HOOK : JAB)) : JAB;
            }
        } else {
            move = chainT > 0 && chain >= 1 ? ROUND : KICK;
        }
        chain = move == JAB ? 0 : (move == CROSS ? 1 : (move == HOOK ? 2 : 3));
        chainT = 0;
        start(me, move, 0);
    }

    void block(boolean on) {
        CasinoWorld.Person me = w.player;
        if (on) {
            if (busy(me) || me.down > 0) return;
            me.act = BLOCK;
            me.actT = 0;
            me.blockAt = w.time;
        } else if (me.act == BLOCK) {
            me.act = NONE;
        }
    }

    void dodge(float sx, float sy) {
        CasinoWorld.Person me = w.player;
        if (me.down > 0 || (busy(me) && me.act != BLOCK && me.act != HIT)) return;
        // Dodge the way you're pushing the stick, or straight back.
        float dx, dz;
        if (Math.hypot(sx, sy) > 0.3f) {
            float fx = (float) Math.sin(w.camYaw), fz = (float) -Math.cos(w.camYaw);
            float rx = (float) Math.cos(w.camYaw), rz = (float) Math.sin(w.camYaw);
            dx = rx * sx - fx * sy;
            dz = rz * sx - fz * sy;
        } else {
            dx = -(float) Math.sin(me.yaw);
            dz = (float) Math.cos(me.yaw);
        }
        float l = (float) Math.max(0.01, Math.hypot(dx, dz));
        me.dodgeX = dx / l;
        me.dodgeZ = dz / l;
        start(me, DODGE, 0);
        me.actDur = 0.32f;
    }

    /** The closest awake person in front of you within range, preferring fighters. */
    CasinoWorld.Person focus(float range) {
        CasinoWorld.Person me = w.player, best = null;
        float bestS = 1e9f;
        for (CasinoWorld.Person p : w.people) {
            if (!p.active || p.dead || p.hidden || (p.ko && p.fall > 0.9f)) continue;
            float d = CasinoWorld.dist(p, me);
            if (d > range) continue;
            float ang = Math.abs(CasinoTraffic.angle((float) Math.atan2(p.x - me.x, -(p.z - me.z)) - me.yaw));
            float s = d + ang * 0.8f - (p.fighter ? 0.8f : 0);
            if (s < bestS) {
                bestS = s;
                best = p;
            }
        }
        return best;
    }

    // ================================================================== every frame

    void update(float dt) {
        if (slowmo > 0) slowmo -= dt / 0.35f;
        if (bigT > 0) bigT -= dt;
        if (chainT > 0) chainT -= dt;
        if (parryT > 0) parryT -= dt;
        CasinoWorld.Person me = w.player;
        animate(me, dt);
        // Face whoever you're fighting while you square up.
        CasinoWorld.Person f = nearestFighter(5);
        if (f != null && me.act != DODGE && me.walk < 0.9f && w.traffic.driving == null
                && w.weapon == null) {
            me.yaw = CasinoWorld.turn(me.yaw, (float) Math.atan2(f.x - me.x, -(f.z - me.z)), dt * 8);
        }
        me.guard = f != null && w.weapon == null;
        if (me.act == DODGE) {
            float k = (float) Math.sin(Math.min(1, me.actT / me.actDur) * Math.PI);
            me.x += me.dodgeX * 7.5f * k * dt;
            me.z += me.dodgeZ * 7.5f * k * dt;
        }
        if (me.down > 0) {
            me.down -= dt;
            float t = 1.0f - me.down;
            me.fall = me.down > 0.35f ? Math.min(1, t / 0.25f) : Math.max(0, me.down / 0.35f);
            if (me.down <= 0) me.fall = 0;
        }
        challenges(dt);
    }

    CasinoWorld.Person nearestFighter(float range) {
        CasinoWorld.Person best = null;
        float bd = range;
        for (CasinoWorld.Person p : w.people) {
            if (!p.fighter || !p.active || p.dead || p.ko) continue;
            float d = CasinoWorld.dist(p, w.player);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Moves a person's current action along; lands the hit at the end of the wind-up. */
    void animate(CasinoWorld.Person p, float dt) {
        if (p.act == NONE) return;
        p.actT += dt;
        if (p.act == BLOCK) return;
        if (attacking(p) && !p.actHit && p.actT >= p.actWind) {
            p.actHit = true;
            land(p);
        }
        if (p == w.player && attacking(p) && queued != 0 && p.actT > p.actWind + REC[p.act] * 0.35f) {
            chainT = 0.5f;
            int q = queued;
            p.act = NONE;
            begin(q);
            return;
        }
        if (p.actT >= p.actDur) {
            if (p == w.player && attacking(p)) chainT = 0.45f;
            p.act = NONE;
        }
    }

    /** The moment a punch or kick connects (or whiffs). */
    private void land(CasinoWorld.Person a) {
        int m = a.act;
        float fx = (float) Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        CasinoWorld.Person t;
        if (a == w.player) {
            t = null;
            float best = REACH[m] * a.scale + 0.25f;
            for (CasinoWorld.Person p : w.people) {
                if (!p.active || p.dead || p.hidden) continue;
                boolean downed = p.down > 0 || p.fall > 0.5f;
                if ((m == STOMP) != downed) continue;
                float dx = p.x - a.x, dz = p.z - a.z, d = (float) Math.hypot(dx, dz);
                if (d > best || d < 0.01f) continue;
                if ((dx * fx + dz * fz) / d < 0.5f) continue;
                best = d;
                t = p;
            }
        } else {
            t = w.player;
            float dx = t.x - a.x, dz = t.z - a.z, d = (float) Math.hypot(dx, dz);
            if (d > REACH[m] * a.scale + 0.25f || (dx * fx + dz * fz) / Math.max(0.01f, d) < 0.45f
                    || w.traffic.driving != null || w.escort > 0) {
                t = null;
            }
        }
        if (t == null) {
            w.sounds |= 2;
            return;
        }
        if (t == w.player && t.act == DODGE) {
            w.sounds |= 2;
            return;
        }
        hit(a, t, m, fx, fz);
    }

    private void hit(CasinoWorld.Person a, CasinoWorld.Person t, int m, float fx, float fz) {
        boolean facing = Math.cos(t.yaw - a.yaw) < -0.3f;
        float dmg = DMG[m] * (a == w.player ? 1 : a.power);
        float hy = (m == STOMP ? 0.3f : (m == KICK || m == ROUND ? 1.05f : 1.6f)) * t.scale;
        if (t.act == BLOCK && facing && m != STOMP) {
            boolean parry = w.time - t.blockAt < 0.22f;
            if (parry && a != w.player) {
                // A perfect block: they're wide open.
                start(a, STAGGER, 0);
                a.actDur = 1.2f;
                parryT = 1.4f;
                slowmo = 0.5f;
                big("COUNTER!");
                w.sounds |= 256;
                sparks(t, hy, fx, fz, 0xFF9AD0FF);
                return;
            }
            if (BREAKS_GUARD[m]) {
                start(t, STAGGER, 0);
                t.actDur = 0.7f;
                dmg *= 0.35f;
            } else {
                dmg *= 0.12f;
            }
            w.sounds |= 256;
            sparks(t, hy, fx, fz, 0xFF9AD0FF);
            hurt(a, t, dmg, 0, fx, fz, m);
            return;
        }
        w.sounds |= 1;
        sparks(t, hy, fx, fz, 0xFFFFF0C0);
        hurt(a, t, dmg, KNOCK[m], fx, fz, m);
    }

    private void hurt(CasinoWorld.Person a, CasinoWorld.Person t, float dmg, int knock,
                      float fx, float fz, int m) {
        if (t == w.player) {
            w.hurtPlayer(dmg);
            t.pushX = fx * (2 + knock * 2.5f);
            t.pushZ = fz * (2 + knock * 2.5f);
            t.shake = 0.18f + knock * 0.12f;
            if (w.health > 0) {
                if (knock >= 2) {
                    t.down = 1.0f;
                    t.act = NONE;
                } else if (knock == 1 || dmg > 4) {
                    start(t, knock == 1 ? STAGGER : HIT, 0);
                    t.actDur = knock == 1 ? 0.5f : 0.22f;
                }
            }
            return;
        }
        // An NPC takes it.
        boolean first = !t.fighter && !t.ko;
        t.health -= dmg;
        t.hitAt = w.time;
        t.bleed = Math.max(t.bleed, dmg > 10 ? 10 : 4);
        if (dmg > 6) w.spray(t.x, 1.6f * t.scale, t.z, fx, fz, (int) (dmg / 2));
        t.kx = fx * (1.5f + knock * 2.5f);
        t.kz = fz * (1.5f + knock * 2.5f);
        if (a == w.player) {
            w.player.shake = Math.max(w.player.shake, 0.08f + knock * 0.07f);
            if (!t.reported && !t.startedIt) {
                t.reported = true;
                if (t.kind == CasinoWorld.COP) w.traffic.addHeat(0.6f, "Assaulted an officer");
                else w.crimeSeen(0.5f, "Assault");
            }
        }
        if (t.health <= 0) {
            knockOut(t, fx, fz);
            return;
        }
        if (t.kind == CasinoWorld.GUARD) {
            w.raiseAlarmPublic("You hit a guard.");
        }
        // Nobody stands there and eats a whole combo: after a few clean hits a
        // fighter covers up and comes straight back at you.
        t.combo = w.time - t.comboAt < 1.3f ? t.combo + 1 : 1;
        t.comboAt = w.time;
        int poise = t.style == BRUISER ? 2 : (t.style == DRUNK ? 4 : 3);
        // A swing that's already coming still lands unless you knock them down.
        boolean armor = attacking(t) && knock < 2 && t != w.player;
        if (armor) {
            // Bruisers walk through small stuff mid-swing.
        } else if (t.fighter && knock < 2 && t.combo >= poise) {
            t.combo = 0;
            t.act = BLOCK;
            t.actT = 0;
            t.actDur = 0.5f;
            t.blockAt = w.time - 1;
            t.fightCool = 0;
            t.blockRoll = -2;
            if (rnd.nextFloat() < 0.4f) say(t, TAUNTS[t.style]);
        } else if (knock >= 2) {
            t.down = CasinoWorld.DOWN_TIME * 0.8f;
            t.act = NONE;
        } else if (knock == 1) {
            start(t, STAGGER, 0);
            t.actDur = 0.6f;
        } else if (!busy(t)) {
            start(t, HIT, 0);
            t.actDur = 0.25f;
        }
        if (first) provoke(t);
    }

    /** Out cold. Fists don't kill. */
    private void knockOut(CasinoWorld.Person t, float fx, float fz) {
        t.ko = true;
        t.fighter = false;
        t.fallRaw = 0;
        t.act = NONE;
        t.kx = fx * 4;
        t.kz = fz * 4;
        t.bubble = null;
        slowmo = 0.6f;
        big("K.O.!");
        w.sounds |= 1;
        if (t.target) w.knockedOutTarget(t);
    }

    /** Somebody just got hit: fight back, run, or cower. */
    void provoke(CasinoWorld.Person t) {
        if (t.kind == CasinoWorld.COP || t.kind == CasinoWorld.GUARD || t.kind == CasinoWorld.PLAYER) return;
        if (t.target && !t.tough) {
            t.flee = 8;
            return;
        }
        float roll = rnd.nextFloat();
        if (t.tough || roll < 0.45f) {
            makeFighter(t, t.tough ? (t.scale > 1.05f ? BRUISER : BRAWLER) : rnd.nextInt(3));
        } else if (roll < 0.85f) {
            t.flee = 6 + rnd.nextFloat() * 3;
            t.sitting = false;
            say(t, new String[]{"Help!", "Psycho!", "Somebody call the cops!"});
        } else {
            t.cower = 4;
            say(t, GIVE_UP);
        }
    }

    void makeFighter(CasinoWorld.Person t, int style) {
        t.fighter = true;
        t.style = style;
        t.sitting = false;
        t.flee = 0;
        t.idle = 0;
        t.power = style == BRUISER ? 1.4f : (style == DRUNK ? 0.8f : 1);
        t.fightCool = 0.6f + rnd.nextFloat() * 0.6f;
        if (t.health < 60) t.health = 60;
        if (style == BRUISER) t.health = Math.max(t.health, 150);
        say(t, TAUNTS[style]);
    }

    private void say(CasinoWorld.Person p, String[] lines) {
        p.bubble = lines[rnd.nextInt(lines.length)];
        p.bubbleT = 2.6f;
    }

    private void big(String s) {
        bigText = s;
        bigT = 0.9f;
    }

    private void sparks(CasinoWorld.Person t, float y, float fx, float fz, int color) {
        for (int i = 0; i < 6; i++) {
            w.sparks.add(new float[]{t.x - fx * 0.15f, y, t.z - fz * 0.15f,
                    fx * 2 + (rnd.nextFloat() - 0.5f) * 3, rnd.nextFloat() * 2.5f,
                    fz * 2 + (rnd.nextFloat() - 0.5f) * 3, 0.22f});
        }
    }

    // ================================================================== people who fight

    /** How many fighters are mid-swing at you right now. */
    private int swinging() {
        int n = 0;
        for (CasinoWorld.Person p : w.people) {
            if (p.fighter && attacking(p)) n++;
        }
        return n;
    }

    /** One fighter's brain for a frame. Returns false if they've stopped fighting. */
    boolean think(CasinoWorld.Person p, float dt) {
        animate(p, dt);
        CasinoWorld.Person me = w.player;
        float d = CasinoWorld.dist(p, me);
        if (p.ko || p.dead) {
            p.fighter = false;
            return false;
        }
        if (d > 25 || w.escort > 0 || w.traffic.driving != null && d > 6) {
            p.fighter = false;
            return false;
        }
        if (p.health < 22 && p.style != BRUISER && p.blockRoll != -5) {
            // Hurt bad: some bail, the rest fight to the end.
            p.blockRoll = -5;
            if (rnd.nextFloat() < 0.6f) {
                if (rnd.nextFloat() < 0.5f) say(p, TAUNTS[p.style]);
            } else {
            p.fighter = false;
            p.flee = 8;
            say(p, GIVE_UP);
            return false;
            }
        }
        if (busy(p)) {
            p.walk = Math.max(0, p.walk - dt * 6);
            return true;
        }
        float want = (float) Math.atan2(me.x - p.x, -(me.z - p.z));
        p.yaw = CasinoWorld.turn(p.yaw, want, dt * 7);
        // React to your swing: boxers block a lot, drunks rarely.
        if (attacking(me) && !me.actHit && d < 2.2f && p.act == NONE) {
            float chance = p.style == BOXER ? 0.55f : (p.style == DRUNK ? 0.1f : 0.28f);
            if (p.blockRoll != me.actT && me.actT < 0.05f) {
                p.blockRoll = me.actT;
                if (rnd.nextFloat() < chance) {
                    p.act = BLOCK;
                    p.actT = 0;
                    p.actDur = 0.5f;
                    p.blockAt = w.time;
                    return true;
                }
            }
        }
        if (p.act == BLOCK) {
            p.actT += 0;
            if (w.time - p.blockAt > 0.55f) p.act = NONE;
            return true;
        }
        float reach = 1.45f;
        if (d > reach) {
            float sp = p.speed;
            p.speed = p.style == DRUNK ? 1.7f : 2.6f;
            // If a buddy's already in your face, come around the side.
            float gx = me.x, gz = me.z;
            for (CasinoWorld.Person o : w.people) {
                if (o == p || !o.fighter || o.ko || !o.active) continue;
                if (CasinoWorld.dist(o, me) < d && CasinoWorld.dist(o, p) < 1.6f) {
                    float ang = (float) Math.atan2(p.x - me.x, p.z - me.z)
                            + (p.id % 2 == 0 ? 1.3f : -1.3f);
                    gx = me.x + (float) Math.sin(ang) * 1.2f;
                    gz = me.z + (float) Math.cos(ang) * 1.2f;
                    break;
                }
            }
            w.stepPerson(p, gx, gz, dt);
            p.speed = sp;
            p.yaw = CasinoWorld.turn(p.yaw, want, dt * 7);
            return true;
        }
        if (d < 0.8f) {
            p.x -= (float) Math.sin(want) * 1.5f * dt;
            p.z += (float) Math.cos(want) * 1.5f * dt;
        }
        // Circle a bit, then swing when it's your turn to get hit.
        p.fightCool -= dt;
        if (p.fightCool <= 0 && swinging() < 2) {
            int move;
            float r = rnd.nextFloat();
            switch (p.style) {
                case BOXER:
                    move = r < 0.55f ? JAB : (r < 0.85f ? CROSS : HOOK);
                    break;
                case DRUNK:
                    move = r < 0.6f ? HOOK : (r < 0.85f ? KICK : CROSS);
                    break;
                case BRUISER:
                    move = r < 0.45f ? HOOK : (r < 0.7f ? UPPER : (r < 0.9f ? ROUND : CROSS));
                    break;
                default:
                    move = r < 0.35f ? CROSS : (r < 0.7f ? HOOK : (r < 0.9f ? KICK : JAB));
                    break;
            }
            start(p, move, TELL[move] * (p.style == BOXER ? 0.75f : 1));
            float aggro = p.style == BOXER ? 1.3f : (p.style == DRUNK ? 0.7f : 1);
            p.fightCool = (0.9f + rnd.nextFloat() * 1.1f) / aggro;
            if (rnd.nextFloat() < 0.15f) say(p, TAUNTS[p.style]);
        } else {
            float side = (p.id % 2 == 0 ? 1 : -1);
            float sx = (float) Math.cos(want) * side, sz = (float) Math.sin(want) * side;
            p.x += sx * 0.8f * dt;
            p.z += sz * 0.8f * dt;
            p.walk = 0.3f;
            p.phase += dt * 4;
        }
        return true;
    }

    // ================================================================== street toughs

    private void challenges(float dt) {
        CasinoWorld.Person me = w.player;
        if (challenger != null) {
            CasinoWorld.Person c = challenger;
            if (!c.active || c.dead || c.ko || c.fighter || c.flee > 0) {
                challenger = null;
                return;
            }
            float d = CasinoWorld.dist(c, me);
            if (d > 14) {
                challenger = null;
                c.challengeT = 0;
                return;
            }
            if (c.challengeT > 0) {
                c.challengeT -= dt;
                if (d < 2.2f && c.challengeT <= 0) {
                    c.startedIt = true;
                    makeFighter(c, rnd.nextFloat() < 0.4f ? DRUNK : BRAWLER);
                    challenger = null;
                }
            }
            return;
        }
        if (w.traffic.driving != null || w.alarm || w.traffic.stars() > 0) return;
        challengeT -= dt;
        if (challengeT > 0) return;
        challengeT = 70 + rnd.nextFloat() * 70;
        for (CasinoWorld.Person p : w.people) {
            if (p.kind != CasinoWorld.WANDER || !p.active || p.ko || p.dead || p.fighter) continue;
            float d = CasinoWorld.dist(p, me);
            if (d < 4 || d > 12) continue;
            challenger = p;
            p.challengeT = 3.5f;
            p.tx = me.x;
            p.tz = me.z;
            p.idle = 0;
            p.speed = 1.6f;
            say(p, CHALLENGE);
            return;
        }
    }
}
