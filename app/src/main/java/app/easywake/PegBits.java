package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Peg Pop's pieces, boards and physics, kept apart from the screen so they can be tested
 * on their own. Distances are in board widths: the board is 1 wide and h tall.
 *
 * There are two kinds of piece: round pegs, and bricks. A brick is a short rounded bar;
 * bricks laid end to end along a curve make a smooth ramp that the ball rolls down.
 */
final class PegBits {
    static final float PEG_R = 0.0165f;
    /** Half the thickness of a brick. */
    static final float BRICK_R = 0.0155f;
    static final float BALL_R = 0.0155f;
    static final float GRAVITY = 1.75f;
    /** Length of one brick, end to end. */
    static final float BRICK_LEN = 0.062f;
    /** Center-to-center spacing of packed pegs: leaves just room for the ball. */
    static final float SPACING = 0.074f;

    static final String[] BOARD_NAMES = {"Twin Slides", "The Valley", "Rolling Hills",
            "Switchback", "Gem", "Halo", "Ripples", "Funnel"};

    private PegBits() {
    }

    static final class Peg {
        float x, y;
        /** For a brick, the other end; x, y is the first end. */
        float x2, y2;
        boolean brick;
        /** 0 blue, 1 gold, 2 green, 3 purple. */
        int kind;
        boolean hit;
    }

    static final class Ball {
        float x, y, vx, vy;
        float slowFor;
    }

    /** A ball that touches nothing new for this long pops what it lit and drops on. */
    static final float NOTHING_NEW_SECONDS = 2.5f;

    interface Hits {
        void hit(Peg p);
    }

    // ---------- physics ----------

    /**
     * Moves a ball one small step: gravity, walls, then pegs and bricks.
     *
     * @param hits told about each piece touched, or null when only tracing the aim line
     * @return true if the ball touched anything on this step
     */
    static boolean step(Ball b, List<Peg> pegs, float dt, Hits hits) {
        boolean touched = false;
        b.vy += GRAVITY * dt;
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        if (b.x < BALL_R) {
            b.x = BALL_R;
            b.vx = Math.abs(b.vx) * 0.85f;
            touched = true;
        } else if (b.x > 1 - BALL_R) {
            b.x = 1 - BALL_R;
            b.vx = -Math.abs(b.vx) * 0.85f;
            touched = true;
        }
        if (b.y < BALL_R) {
            b.y = BALL_R;
            b.vy = Math.abs(b.vy) * 0.85f;
            touched = true;
        }
        for (int i = 0; i < pegs.size(); i++) {
            Peg p = pegs.get(i);
            float cx, cy, reach;
            if (p.brick) {
                // Quick skip when the ball is nowhere near this brick.
                float m = BALL_R + BRICK_R;
                if (b.x < Math.min(p.x, p.x2) - m || b.x > Math.max(p.x, p.x2) + m
                        || b.y < Math.min(p.y, p.y2) - m || b.y > Math.max(p.y, p.y2) + m) {
                    continue;
                }
                // The nearest point on the brick's middle line.
                float sx = p.x2 - p.x;
                float sy = p.y2 - p.y;
                float t = ((b.x - p.x) * sx + (b.y - p.y) * sy) / (sx * sx + sy * sy);
                t = Math.max(0, Math.min(1, t));
                cx = p.x + sx * t;
                cy = p.y + sy * t;
                reach = m;
            } else {
                cx = p.x;
                cy = p.y;
                reach = BALL_R + PEG_R;
            }
            float dx = b.x - cx;
            float dy = b.y - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 >= reach * reach || d2 == 0) continue;
            float d = (float) Math.sqrt(d2);
            float nx = dx / d;
            float ny = dy / d;
            // Push the ball back out to the surface.
            b.x = cx + nx * reach;
            b.y = cy + ny * reach;
            float along = b.vx * nx + b.vy * ny;
            if (along < 0) {
                // Bricks are softer than pegs. A gentle touch doesn't bounce at all, so
                // the ball stays on a ramp and rolls along it.
                float bounce = p.brick ? 0.45f : 0.8f;
                if (-along < 0.2f) bounce = 0;
                b.vx -= (1 + bounce) * along * nx;
                b.vy -= (1 + bounce) * along * ny;
                if (p.brick) {
                    // Friction along the ramp, more while rolling than when bouncing, so
                    // a ball rocking in a dip settles down instead of swinging for ages.
                    float f = bounce == 0 ? 0.9965f : 0.9985f;
                    b.vx *= f;
                    b.vy *= f;
                }
            }
            touched = true;
            if (hits != null && !p.hit) hits.hit(p);
        }
        return touched;
    }

    // ---------- boards ----------

    /** Builds a board into out (cleared first). h is the board's height in widths. */
    static void build(int lvl, float h, Random rnd, List<Peg> out) {
        out.clear();
        Builder bd = new Builder(out, h);
        switch ((lvl - 1) % BOARD_NAMES.length) {
            case 0:
                bd.twinSlides();
                break;
            case 1:
                bd.valley();
                break;
            case 2:
                bd.hills();
                break;
            case 3:
                bd.switchback();
                break;
            case 4:
                bd.gem();
                break;
            case 5:
                bd.halo();
                break;
            case 6:
                bd.ripples();
                break;
            default:
                bd.funnel();
                break;
        }
        // Colors: gold to clear, two green, and one purple that moves every shot.
        List<Peg> pool = new ArrayList<>(out);
        java.util.Collections.shuffle(pool, rnd);
        int gold = Math.min(pool.size() - 6, Math.min(25, 18 + lvl));
        for (int i = 0; i < gold; i++) pool.get(i).kind = 1;
        int greens = 0;
        for (int i = gold; i < pool.size() && greens < 2; i++) {
            // Green pegs launch a second ball, so keep them round.
            if (!pool.get(i).brick) {
                pool.get(i).kind = 2;
                greens++;
            }
        }
        pickPurple(out, rnd);
    }

    /** Moves the purple bonus to a different blue piece. */
    static void pickPurple(List<Peg> pegs, Random rnd) {
        List<Peg> blue = new ArrayList<>();
        for (Peg p : pegs) {
            if (p.kind == 3) p.kind = 0;
            if (p.kind == 0) blue.add(p);
        }
        if (!blue.isEmpty()) blue.get(rnd.nextInt(blue.size())).kind = 3;
    }

    /** A curve on the board, t from 0 to 1. */
    private interface Curve {
        float x(float t);

        float y(float t);
    }

    /** Lays out bricks and pegs, keeping a clear gap between everything. */
    private static final class Builder {
        final List<Peg> out;
        final float h;
        final float top;
        final float bottom;
        final float span;
        final float mid;

        Builder(List<Peg> out, float h) {
            this.out = out;
            this.h = h;
            top = 0.24f;
            bottom = h - 0.2f;
            span = bottom - top;
            mid = (top + bottom) / 2f;
        }

        /** Distance from (x, y) to the nearest edge of a piece. */
        private float clearance(Peg p, float x, float y) {
            if (!p.brick) {
                return (float) Math.hypot(x - p.x, y - p.y) - PEG_R;
            }
            float sx = p.x2 - p.x;
            float sy = p.y2 - p.y;
            float t = ((x - p.x) * sx + (y - p.y) * sy) / (sx * sx + sy * sy);
            t = Math.max(0, Math.min(1, t));
            return (float) Math.hypot(x - (p.x + sx * t), y - (p.y + sy * t)) - BRICK_R;
        }

        /** Adds a round peg unless it would crowd something already there. */
        void peg(float x, float y) {
            if (x < 0.05f || x > 0.95f || y < top - 0.02f || y > bottom + 0.02f) return;
            for (Peg p : out) {
                float room = clearance(p, x, y) - PEG_R;
                // Either a clear gap the ball fits through, or nothing.
                float need = p.brick ? BALL_R * 2.3f : SPACING - 2 * PEG_R - 0.004f;
                if (room < need) return;
            }
            Peg p = new Peg();
            p.x = x;
            p.y = y;
            out.add(p);
        }

        /** Fills a region with pegs in staggered rows. */
        void fill(float x0, float x1, float y0, float y1, Region keep) {
            float rowGap = SPACING * 0.866f;
            int row = 0;
            for (float y = y0; y <= y1 + 1e-4f; y += rowGap, row++) {
                float off = (row % 2) * SPACING / 2f;
                for (float x = x0 + off; x <= x1 + 1e-4f; x += SPACING) {
                    if (keep == null || keep.in(x, y)) peg(x, y);
                }
            }
        }

        /**
         * Lays bricks end to end along a curve. Each brick is a straight piece between
         * two points on the curve, so neighbours share an end and the ramp is smooth.
         */
        void bricks(Curve c, float from, float to) {
            // Walk the curve finely and cut it into equal lengths.
            int steps = 600;
            float px = c.x(from);
            float py = c.y(from);
            float sx = px;
            float sy = py;
            float run = 0;
            for (int i = 1; i <= steps; i++) {
                float t = from + (to - from) * i / steps;
                float x = c.x(t);
                float y = c.y(t);
                run += (float) Math.hypot(x - px, y - py);
                px = x;
                py = y;
                if (run >= BRICK_LEN || i == steps) {
                    if (run > BRICK_LEN * 0.4f) addBrick(sx, sy, x, y);
                    sx = x;
                    sy = y;
                    run = 0;
                }
            }
        }

        private void addBrick(float x1, float y1, float x2, float y2) {
            Peg p = new Peg();
            p.brick = true;
            p.x = x1;
            p.y = y1;
            p.x2 = x2;
            p.y2 = y2;
            out.add(p);
        }

        // ---------- the eight boards ----------

        /** Two long slides from the walls toward the middle, with pegs packed around. */
        void twinSlides() {
            final float y0 = top + span * 0.3f;
            final float y1 = top + span * 0.5f;
            bricks(new Curve() {
                public float x(float t) {
                    return 0.05f + 0.33f * t;
                }

                public float y(float t) {
                    return y0 + (y1 - y0) * (t * t * 0.4f + t * 0.6f);
                }
            }, 0, 1);
            bricks(new Curve() {
                public float x(float t) {
                    return 0.95f - 0.33f * t;
                }

                public float y(float t) {
                    return y0 + (y1 - y0) * (t * t * 0.4f + t * 0.6f);
                }
            }, 0, 1);
            // A second, shorter pair lower down, sloping the other way.
            final float y2 = top + span * 0.72f;
            final float y3 = top + span * 0.84f;
            bricks(line(0.3f, y2, 0.08f, y3), 0, 1);
            bricks(line(0.7f, y2, 0.92f, y3), 0, 1);
            fill(0.08f, 0.92f, top, top + span * 0.26f, null);
            fill(0.12f, 0.88f, top + span * 0.42f, top + span * 0.68f, null);
            fill(0.3f, 0.7f, top + span * 0.76f, bottom, null);
        }

        /** A big U of bricks with pegs packed inside it and around it. */
        void valley() {
            final float yTop = top + span * 0.3f;
            final float depth = span * 0.5f;
            bricks(new Curve() {
                public float x(float t) {
                    return 0.5f - 0.4f * (float) Math.cos(Math.PI * t);
                }

                public float y(float t) {
                    return yTop + depth * (float) Math.sin(Math.PI * t);
                }
            }, 0, 1);
            fill(0.08f, 0.92f, top, top + span * 0.2f, null);
            // Inside the U.
            fill(0.16f, 0.84f, yTop, yTop + depth - 0.04f, new Region() {
                public boolean in(float x, float y) {
                    float u = (x - 0.1f) / 0.8f;
                    return y < yTop + depth * (float) Math.sin(Math.PI * u) - 0.035f;
                }
            });
            // A few under each arm.
            fill(0.06f, 0.2f, yTop + span * 0.1f, bottom, null);
            fill(0.8f, 0.94f, yTop + span * 0.1f, bottom, null);
        }

        /** Two rounded hills of bricks with pegs packed between and on top. */
        void hills() {
            final float r = 0.19f;
            final float baseY = top + span * 0.62f;
            for (final float cx : new float[]{0.27f, 0.73f}) {
                bricks(new Curve() {
                    public float x(float t) {
                        return cx - r * (float) Math.cos(Math.PI * t);
                    }

                    public float y(float t) {
                        return baseY - r * 1.15f * (float) Math.sin(Math.PI * t);
                    }
                }, 0, 1);
            }
            fill(0.06f, 0.94f, top, baseY - 0.2f, null);
            // Between the hills and below them.
            fill(0.44f, 0.56f, baseY - 0.15f, baseY + 0.02f, null);
            fill(0.12f, 0.88f, baseY + 0.07f, bottom, null);
        }

        /** Ramps that zigzag down the board, left then right, with pegs between. */
        void switchback() {
            int n = 4;
            for (int i = 0; i < n; i++) {
                float y = top + span * (0.16f + 0.22f * i);
                if (i % 2 == 0) {
                    bricks(line(0.06f, y, 0.6f, y + 0.13f), 0, 1);
                } else {
                    bricks(line(0.94f, y, 0.4f, y + 0.13f), 0, 1);
                }
            }
            fill(0.08f, 0.92f, top - 0.02f, bottom, null);
        }

        /** A gem of packed pegs, outlined at the bottom by a V of bricks. */
        void gem() {
            final float half = Math.min(0.43f, span * 0.42f);
            float vy = mid + span * 0.36f + 0.05f;
            bricks(line(0.07f, mid + 0.02f, 0.4f, vy), 0, 1);
            bricks(line(0.93f, mid + 0.02f, 0.6f, vy), 0, 1);
            fill(0.08f, 0.92f, mid - span * 0.42f, mid + span * 0.36f, new Region() {
                public boolean in(float x, float y) {
                    float dy = y < mid ? (mid - y) / (span * 0.42f) : (y - mid) / (span * 0.36f);
                    return Math.abs(x - 0.5f) / half + dy < 1.0f;
                }
            });
            fill(0.08f, 0.2f, top, top + span * 0.25f, null);
            fill(0.8f, 0.92f, top, top + span * 0.25f, null);
        }

        /** A ring of bricks, open at the top and bottom, with pegs inside and out. */
        void halo() {
            final float r = Math.min(0.3f, span * 0.3f);
            bricks(new Curve() {
                public float x(float t) {
                    return 0.5f + r * (float) Math.cos(t);
                }

                public float y(float t) {
                    return mid + r * (float) Math.sin(t);
                }
            }, (float) Math.toRadians(-62), (float) Math.toRadians(62));
            bricks(new Curve() {
                public float x(float t) {
                    return 0.5f + r * (float) Math.cos(t);
                }

                public float y(float t) {
                    return mid + r * (float) Math.sin(t);
                }
            }, (float) Math.toRadians(118), (float) Math.toRadians(242));
            fill(0.08f, 0.92f, top, bottom, new Region() {
                public boolean in(float x, float y) {
                    float d = (float) Math.hypot(x - 0.5f, y - mid);
                    return d < r - 0.06f || d > r + 0.06f;
                }
            });
        }

        /** Three wavy ramps across the board, each with a gap, and rows of pegs between. */
        void ripples() {
            for (int i = 0; i < 3; i++) {
                final float y0 = top + span * (0.22f + 0.3f * i);
                final float phase = i * 2.1f;
                // Each wave also tilts, alternately left and right, so balls drain off it.
                final float tilt = i % 2 == 0 ? 0.16f : -0.16f;
                Curve wave = new Curve() {
                    public float x(float t) {
                        return 0.05f + 0.9f * t;
                    }

                    public float y(float t) {
                        return y0 + tilt * (t - 0.5f)
                                + 0.022f * (float) Math.sin(t * Math.PI * 3 + phase);
                    }
                };
                // Leave a gap in each wave, in a different place each time.
                float gap = 0.2f + 0.3f * i;
                bricks(wave, 0, gap - 0.08f);
                bricks(wave, gap + 0.08f, 1);
            }
            fill(0.08f, 0.92f, top - 0.02f, bottom, null);
        }

        /** Two long ramps meeting near the middle, packed pegs above, more below. */
        void funnel() {
            float y0 = top + span * 0.35f;
            float y1 = top + span * 0.62f;
            bricks(line(0.05f, y0, 0.4f, y1), 0, 1);
            bricks(line(0.95f, y0, 0.6f, y1), 0, 1);
            fill(0.08f, 0.92f, top, top + span * 0.4f, null);
            fill(0.12f, 0.88f, top + span * 0.68f, bottom, null);
        }

        private static Curve line(final float x0, final float y0, final float x1,
                                  final float y1) {
            return new Curve() {
                public float x(float t) {
                    return x0 + (x1 - x0) * t;
                }

                public float y(float t) {
                    return y0 + (y1 - y0) * t;
                }
            };
        }
    }

    /** Which spots in a fill get pegs. */
    private interface Region {
        boolean in(float x, float y);
    }
}
