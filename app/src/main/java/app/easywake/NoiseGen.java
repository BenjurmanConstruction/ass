package app.easywake;

/**
 * Makes fan and noise sounds live, sample by sample, in stereo. Nothing is recorded or
 * looped: every sample is new, and the slow changes in the sound follow random drifts
 * rather than fixed cycles, so there is no pattern for the ear to catch.
 */
final class NoiseGen {
    static final String[] NAMES = {"Box fan", "White noise", "Pink noise", "Brown noise",
            "Big floor fan", "Steady rain", "Airplane cabin", "Real fan"};
    static final String[] HINTS = {
            "Steady whir with a soft motor hum",
            "Even hiss, like static or a shower",
            "Softer, like a waterfall far off",
            "Deep rumble, like distant surf",
            "Lower and slower, a big fan across the room",
            "Rain on the roof with scattered drops",
            "Low engine drone and rushing air",
            "A real fan recording, mixed so it never repeats (recording: unfa, Freesound)"
    };
    /** The real recorded fan, played by FanMixer rather than made here. */
    static final int REAL_FAN = 7;
    static final int SAMPLE_RATE = 44100;

    private static final double TWO_PI = Math.PI * 2;
    private static final double TARGET_RMS = 0.16;
    private static final int FAN = 0, WHITE = 1, PINK = 2, BROWN = 3, BIG_FAN = 4, RAIN = 5,
            CABIN = 6;

    /** A resonant filter that turns noise into a soft tone at one pitch. */
    private static final class Resonator {
        private double a1, a2, gain, y1, y2;

        void set(double freq, double q) {
            double r = Math.exp(-Math.PI * (freq / q) / SAMPLE_RATE);
            a1 = 2 * r * Math.cos(TWO_PI * freq / SAMPLE_RATE);
            a2 = -r * r;
            gain = 1 - r;
        }

        double run(double x) {
            double y = gain * x + a1 * y1 + a2 * y2;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    /** Everything one ear hears. Left and right get their own noise so the sound is wide. */
    private final class Channel {
        private long seed;
        private double b0, b1, b2, brown, t1, t2, hpIn, hpOut, dropEnv, dropLp, sub1, sub2;
        private final Resonator[] tones = {new Resonator(), new Resonator(), new Resonator()};

        Channel(long seed) {
            this.seed = seed;
        }

        /** Fast random number from -1 to 1. */
        double white() {
            seed ^= seed << 13;
            seed ^= seed >>> 7;
            seed ^= seed << 17;
            return (seed >> 11) * (1.0 / (1L << 52));
        }

        double pink(double w) {
            // Paul Kellet's economy pink filter.
            b0 = 0.99765 * b0 + w * 0.0990460;
            b1 = 0.96300 * b1 + w * 0.2965164;
            b2 = 0.57000 * b2 + w * 1.0526913;
            return (b0 + b1 + b2 + w * 0.1848) * 0.16;
        }

        double brown(double w) {
            brown = (brown + 0.02 * w) / 1.02;
            return brown * 3.5;
        }

        double next() {
            double w = white();
            double raw;
            switch (type) {
                case WHITE:
                    raw = w;
                    break;
                case PINK:
                    raw = pink(w);
                    break;
                case BROWN:
                    raw = brown(w);
                    break;
                case RAIN:
                    raw = pink(w) * 0.8 + w * 0.12;
                    break;
                case CABIN:
                    raw = brown(w) + pink(w) * 0.25;
                    break;
                default:
                    // Moving air: mostly low rumble with some mid "whoosh" on top.
                    raw = pink(w) * 0.5 + brown(w);
                    break;
            }

            // Tone control: two low-pass stages.
            t1 += toneCoef * (raw - t1);
            t2 += toneCoef * (t1 - t2);
            double y = t2;

            // Remove rumble too low to hear. It only wastes loudness and thumps speakers.
            double hp = HP_COEF * (hpOut + y - hpIn);
            hpIn = y;
            hpOut = hp;
            y = hp;

            if (rumble > 0) {
                // Rumble: a layer of very deep noise under the sound, then a soft
                // overdrive that thickens it, the way a big motor growls.
                sub1 += 0.01 * (w - sub1);
                sub2 += 0.01 * (sub1 - sub2);
                y += sub2 * 10 * rumble;
                double drive = 1 + 9 * rumble;
                y = Math.tanh(y * drive) / drive;
            }
            if (type == FAN || type == BIG_FAN || type == CABIN) {
                // The motor and blades: soft tones made by filtering noise, so they
                // waver naturally the way a real motor does instead of sounding like a
                // test tone.
                double tone = tones[0].run(w) + tones[1].run(w) * 0.6 + tones[2].run(w) * 0.3;
                y += tone * humAmp;
            }
            if (type == RAIN) {
                // Scattered single drops on top of the steady rain.
                if (white() > 0.99965) dropEnv = 0.25 + 0.75 * Math.abs(white());
                dropEnv *= 0.9965;
                dropLp += 0.35 * (w - dropLp);
                y += (w - dropLp) * dropEnv * 0.05;
            }
            return y;
        }
    }

    /** High-pass at about 28 Hz. */
    private static final double HP_COEF = 1 / (1 + TWO_PI * 28.0 / SAMPLE_RATE);

    private final int type;
    private final Channel left = new Channel(0x9E3779B97F4A7C15L);
    private final Channel right = new Channel(0xD1B54A32D192ED03L);
    private final Channel driftSource = new Channel(0x2545F4914F6CDD1DL);

    private double toneCoef = 1;
    private double humAmp;
    private int lastTone = -1;
    private int lastHum = -1;
    private int lastRumble = -1;
    private double rumble;
    // Loudness levelling: fast while settling after a change, then very slow, so the
    // volume never pumps up and down.
    private double meanSquare = 0.01;
    private int settling;
    // A slow random wander in loudness, like air moving around a room.
    private double drift1, drift2;

    NoiseGen(int type) {
        this.type = (type >= 0 && type < NAMES.length) ? type : 0;
        setParams(40, 35, 0);
    }

    /**
     * @param tone 0 = deepest and most muffled, 100 = brightest
     * @param hum  0..100 strength of the motor hum (fans and the cabin only)
     * @param rumbleAmount 0..100 how much deep growl is added underneath
     */
    void setParams(int tone, int hum, int rumbleAmount) {
        if (tone == lastTone && hum == lastHum && rumbleAmount == lastRumble) return;
        lastTone = tone;
        lastHum = hum;
        lastRumble = rumbleAmount;
        rumble = Math.max(0, Math.min(100, rumbleAmount)) / 100.0;
        double t = Math.max(0, Math.min(100, tone)) / 100.0;
        double base = type == BIG_FAN ? 110.0 : (type == CABIN ? 140.0 : 180.0);
        double fc = base * Math.pow(66.0, t);
        if (type == RAIN) fc = 900.0 * Math.pow(12.0, t);
        toneCoef = 1 - Math.exp(-TWO_PI * fc / SAMPLE_RATE);
        humAmp = Math.max(0, Math.min(100, hum)) / 100.0
                * (type == CABIN ? 0.64 : (type == BIG_FAN ? 0.26 : 0.33));

        // Blade and motor pitches. The two ears are tuned a hair apart so the hum
        // sounds like it fills the room.
        double f = type == BIG_FAN ? 43.0 : (type == CABIN ? 96.0 : 59.0);
        for (int i = 0; i < 3; i++) {
            left.tones[i].set(f * (i + 1), 28 + i * 8);
            right.tones[i].set(f * (i + 1) * 1.004, 28 + i * 8);
        }
        settling = SAMPLE_RATE / 2;
    }

    /**
     * Fills a stereo buffer (left, right, left, right...), ramping loudness from
     * gainStart to gainEnd for fades.
     *
     * @param frames number of left-right pairs to write
     */
    void fill(short[] buf, int frames, float gainStart, float gainEnd) {
        for (int i = 0; i < frames; i++) {
            double a = left.next();
            double b = right.next();
            // Share some of each side with the other: wide, but still one sound.
            double l = a * 0.88 + b * 0.36;
            double r = b * 0.88 + a * 0.36;

            double power = (l * l + r * r) * 0.5;
            if (settling > 0) {
                settling--;
                meanSquare += 0.0004 * (power - meanSquare);
            } else {
                meanSquare += 0.000002 * (power - meanSquare);
            }
            double level = TARGET_RMS / Math.sqrt(meanSquare + 1e-9);
            if (level > 80) level = 80;

            // Very slow, very gentle random swell: about one decibel either way.
            drift1 += 0.000005 * (driftSource.white() - drift1);
            drift2 += 0.000005 * (drift1 - drift2);
            double swell = 1 + Math.max(-0.1, Math.min(0.1, drift2 * 65));

            double g = (gainStart + (gainEnd - gainStart) * i / (double) frames) * level * swell;
            buf[2 * i] = clip(l * g);
            buf[2 * i + 1] = clip(r * g);
        }
    }

    private static short clip(double v) {
        // Soft limit so a rare loud moment rounds off instead of crackling.
        if (v > 0.9) v = 0.9 + 0.1 * Math.tanh((v - 0.9) * 10);
        else if (v < -0.9) v = -0.9 + 0.1 * Math.tanh((v + 0.9) * 10);
        return (short) (v * 32767);
    }
}
