package app.easywake;

/**
 * Plays a real fan recording so it never sounds like a loop.
 *
 * The recording is split in two: the motor hum (below about 400 Hz) and the rushing air
 * above it. Each part is played from random places in the recording, in pieces of random
 * length, and every piece slowly blends into the next one, so the order never repeats.
 *
 * The air is like hiss, so each ear gets its own random pieces, which makes the sound wide.
 * The hum is a steady tone, and two tones only blend smoothly if they line up, so before
 * every blend the new piece is nudged by a few milliseconds until its hum matches what is
 * playing; the hum is the same in both ears, the way a motor sounds in a room.
 */
final class FanMixer {
    private static final int SR = NoiseGen.SAMPLE_RATE;
    /** Each blend lasts 3 seconds. */
    private static final int FADE = SR * 3;
    /** Each piece plays 7 to 15 seconds before blending into the next. */
    private static final int MIN_PIECE = SR * 7;
    private static final int MAX_PIECE = SR * 15;
    /** Skip the very start and end of the recording. */
    private static final int EDGE = SR / 2;
    /** How far a hum piece may be nudged to line up (a little over one hum cycle). */
    private static final int SEARCH = 900;
    /** The hum is compared over about three quarters of a second, one full wub. */
    private static final int MATCH_LEN = 32768;
    /** How many random spots are tried for each hum blend; the best match wins. */
    private static final int CANDIDATES = 10;

    private static final double TARGET_RMS = 0.16;

    private final short[] hum;
    private final short[] air;
    private final int firstStart;
    private final int lastStart;
    private long seed;
    private final Reader humReader;
    private final Reader airLeft;
    private final Reader airRight;

    private double toneCoef = 1;
    /** Extra treble from the Tone slider's right half (0 = none). */
    private double bright;
    private static final double BRIGHT_COEF = 1 - Math.exp(-2 * Math.PI * 1500.0 / SR);
    private double bl, br;
    private int lastTone = -1;
    private int lastHum = -1;
    private double humGain = 0.5;
    private double l1, l2, r1, r2;
    private double meanSquare = 0.01;
    private int settling = SR / 2;

    /** Plays one part of the recording from place to place. */
    private final class Reader {
        private final short[] data;
        /** True for the hum: line pieces up and blend them evenly. */
        private final boolean tonal;
        private int pos;
        private int remain;
        private int nextPos = -1;
        private int faded;

        Reader(short[] data, boolean tonal, int start) {
            this.data = data;
            this.tonal = tonal;
            pos = start;
            remain = pieceLength();
        }

        double next() {
            double v;
            if (nextPos >= 0) {
                double m = faded / (double) FADE;
                double out, in;
                if (tonal) {
                    // Lined-up tones add straight, so a straight blend keeps them even.
                    in = 0.5 - 0.5 * Math.cos(m * Math.PI);
                    out = 1 - in;
                } else {
                    // Unrelated hiss adds by power, so an equal-power blend keeps it even.
                    out = Math.cos(m * Math.PI / 2);
                    in = Math.sin(m * Math.PI / 2);
                }
                v = data[pos] * out + data[nextPos] * in;
                pos++;
                nextPos++;
                if (++faded >= FADE) {
                    pos = nextPos;
                    nextPos = -1;
                    remain = pieceLength();
                }
            } else {
                v = data[pos++];
                if (--remain <= 0) beginBlend();
            }
            return v / 32768.0;
        }

        private void beginBlend() {
            // A fresh random spot, well away from what is playing now.
            int q = randomStart();
            for (int tries = 0; tries < 12 && Math.abs(q - pos) < SR * 8; tries++) {
                q = randomStart();
            }
            if (tonal) {
                // Try several spots, nudging each until its hum lines up with what is
                // playing now, and keep the one that matches best.
                double bestScore = -Double.MAX_VALUE;
                int best = q;
                for (int c = 0; c < CANDIDATES; c++) {
                    int base = c == 0 ? q : randomStart();
                    if (c > 0 && Math.abs(base - pos) < SR * 8) continue;
                    // Coarse search first, then fine-tune around the best spot.
                    int coarse = base;
                    double coarseScore = -Double.MAX_VALUE;
                    for (int k = -SEARCH; k <= SEARCH; k += 6) {
                        double sc = match(base + k);
                        if (sc > coarseScore) {
                            coarseScore = sc;
                            coarse = base + k;
                        }
                    }
                    for (int k = coarse - 5; k <= coarse + 5; k++) {
                        double sc = match(k);
                        if (sc > bestScore) {
                            bestScore = sc;
                            best = k;
                        }
                    }
                }
                q = best;
            }
            nextPos = q;
            faded = 0;
        }

        /** How well the hum starting at s lines up with what is playing now. */
        private double match(int s) {
            double score = 0;
            for (int j = 0; j < MATCH_LEN; j += 16) {
                score += data[pos + j] * (double) data[s + j];
            }
            return score;
        }
    }

    /** @param src the recording, mono, 44,100 samples a second, at least 40 seconds */
    FanMixer(short[] src, long seed) {
        this.seed = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        // Split into hum and air. The air is everything above about 400 Hz, cut very
        // sharply so none of the loud hum leaks into it; the hum part is whatever is left.
        // The filter runs forwards and then backwards so both parts stay exactly in step
        // and add back up to the original.
        int n = src.length;
        float[] high = new float[n];
        for (int i = 0; i < n; i++) high[i] = src[i];
        double c = 1 / (1 + 2 * Math.PI * 400.0 / SR);
        for (int pass = 0; pass < 8; pass++) {
            boolean forward = pass % 2 == 0;
            double prevIn = 0, prevOut = 0;
            for (int k = 0; k < n; k++) {
                int i = forward ? k : n - 1 - k;
                double x = high[i];
                prevOut = c * (prevOut + x - prevIn);
                prevIn = x;
                high[i] = (float) prevOut;
            }
        }
        hum = new short[n];
        air = new short[n];
        for (int i = 0; i < n; i++) {
            air[i] = toShort(high[i]);
            hum[i] = toShort(src[i] - high[i]);
        }

        firstStart = EDGE + SEARCH;
        // Room for a piece, a blend in and a blend out, plus the nudge and the match.
        lastStart = Math.max(firstStart + 1,
                n - EDGE - SEARCH - MATCH_LEN - MAX_PIECE - 2 * FADE);
        humReader = new Reader(hum, true, randomStart());
        airLeft = new Reader(air, false, randomStart());
        int r = randomStart();
        for (int tries = 0; tries < 12 && Math.abs(r - airLeft.pos) < SR * 8; tries++) {
            r = randomStart();
        }
        airRight = new Reader(air, false, r);
    }

    static boolean longEnough(short[] src) {
        return src != null && src.length > SR * 40;
    }

    private static short toShort(double v) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(v)));
    }

    private double random() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return ((seed >>> 11) & ((1L << 53) - 1)) * (1.0 / (1L << 53));
    }

    private int randomStart() {
        return firstStart + (int) (random() * (lastStart - firstStart));
    }

    private int pieceLength() {
        return MIN_PIECE + (int) (random() * (MAX_PIECE - MIN_PIECE));
    }

    /**
     * @param tone 0 = most muffled, 40 = the recording as it is, 100 = brightest
     * @param humAmount 0..100 how much of the motor's low hum is heard
     */
    void setParams(int tone, int humAmount) {
        if (tone == lastTone && humAmount == lastHum) return;
        lastTone = tone;
        lastHum = humAmount;
        // Left of the usual spot (40) muffles it; right of it adds treble, which helps
        // on a phone speaker.
        double t = Math.max(0, Math.min(100, tone)) / 100.0;
        if (t < 0.4) {
            double fc = 200.0 * Math.pow(100.0, t / 0.4);
            toneCoef = 1 - Math.exp(-2 * Math.PI * fc / SR);
            bright = 0;
        } else {
            toneCoef = 1;
            bright = (t - 0.4) / 0.6 * 1.5;
        }
        // The recorded hum is far louder than the air. The slider's usual spot (35)
        // turns it down to a quarter, all the way left leaves mostly the rushing air, and
        // all the way right plays it as recorded.
        double h = Math.max(0, Math.min(100, humAmount)) / 100.0;
        humGain = h <= 0.35 ? 0.03 + h / 0.35 * 0.22 : 0.25 + (h - 0.35) / 0.65 * 0.75;
        settling = SR / 2;
    }

    /**
     * Fills a stereo buffer (left, right, left, right...), ramping loudness from
     * gainStart to gainEnd for fades.
     */
    void fill(short[] buf, int frames, float gainStart, float gainEnd) {
        for (int i = 0; i < frames; i++) {
            double h = humReader.next() * humGain;
            double al = airLeft.next();
            double ar = airRight.next();
            // Share some of each side's air with the other: wide, but still one fan.
            double a = h + al * 0.88 + ar * 0.36;
            double b = h + ar * 0.88 + al * 0.36;
            // Tone: two gentle low-pass stages per ear.
            l1 += toneCoef * (a - l1);
            l2 += toneCoef * (l1 - l2);
            r1 += toneCoef * (b - r1);
            r2 += toneCoef * (r1 - r2);
            double l = l2, r = r2;
            if (bright > 0) {
                bl += BRIGHT_COEF * (l - bl);
                br += BRIGHT_COEF * (r - br);
                l += (l - bl) * bright;
                r += (r - br) * bright;
            }

            double power = (l * l + r * r) * 0.5;
            if (settling > 0) {
                settling--;
                meanSquare += 0.0004 * (power - meanSquare);
            } else {
                meanSquare += 0.000002 * (power - meanSquare);
            }
            double level = TARGET_RMS / Math.sqrt(meanSquare + 1e-9);
            // The recorded air is faint next to the hum, so allow a big boost when the
            // hum is turned right down.
            if (level > 60) level = 60;
            double g = (gainStart + (gainEnd - gainStart) * i / (double) frames) * level;
            buf[2 * i] = clip(l * g);
            buf[2 * i + 1] = clip(r * g);
        }
    }

    private static short clip(double v) {
        if (v > 0.9) v = 0.9 + 0.1 * Math.tanh((v - 0.9) * 10);
        else if (v < -0.9) v = -0.9 + 0.1 * Math.tanh((v + 0.9) * 10);
        return (short) (v * 32767);
    }
}
