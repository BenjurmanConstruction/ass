package app.easywake;

import java.util.Random;

/** Makes fan and noise sounds live, sample by sample, so they never loop or repeat. */
final class NoiseGen {
    static final String[] NAMES = {"Box fan", "White noise", "Pink noise", "Brown noise"};
    static final String[] HINTS = {
            "Steady whir with a low motor hum",
            "Even hiss, like static or a shower",
            "Softer, like steady rain",
            "Deep rumble, like distant surf"
    };
    static final int SAMPLE_RATE = 44100;

    private static final double TWO_PI = Math.PI * 2;
    private static final double TARGET_RMS = 0.17;

    private final int type;
    private final Random rnd = new Random();

    // Raw noise colors.
    private double b0, b1, b2;
    private double brown;
    // Tone control: two low-pass stages whose cutoff follows the Tone slider.
    private double toneCoef = 1;
    private double t1, t2;
    // Running loudness so every sound and tone setting comes out at a similar level.
    private double meanSquare = 0.01;
    // Fan extras.
    private double humAmp;
    private double ph59, ph118, ph177, ph236, phChop, phWobble, phSlowA, phSlowB;

    NoiseGen(int type) {
        this.type = (type >= 0 && type < NAMES.length) ? type : 0;
        setParams(40, 35);
    }

    /**
     * @param tone 0 = deepest and most muffled, 100 = brightest
     * @param hum  0..100 strength of the fan's motor hum
     */
    void setParams(int tone, int hum) {
        double fc = 180.0 * Math.pow(66.0, Math.max(0, Math.min(100, tone)) / 100.0);
        toneCoef = 1 - Math.exp(-TWO_PI * fc / SAMPLE_RATE);
        humAmp = Math.max(0, Math.min(100, hum)) / 100.0 * 0.07;
    }

    /** Fills the buffer, ramping loudness from gainStart to gainEnd (for fades). */
    void fill(short[] buf, int n, float gainStart, float gainEnd) {
        for (int i = 0; i < n; i++) {
            double g = gainStart + (gainEnd - gainStart) * i / (double) n;
            double v = next() * g;
            if (v > 1) v = 1;
            else if (v < -1) v = -1;
            buf[i] = (short) (v * 32767);
        }
    }

    private double next() {
        double w = rnd.nextDouble() * 2 - 1;
        double raw;
        switch (type) {
            case 1:
                raw = w;
                break;
            case 2:
                raw = pink(w);
                break;
            case 3:
                raw = brown(w);
                break;
            default:
                // Moving air: mostly low rumble with some mid "whoosh" on top.
                raw = pink(w) * 0.5 + brown(w);
                break;
        }

        t1 += toneCoef * (raw - t1);
        t2 += toneCoef * (t1 - t2);
        double y = t2;

        meanSquare += 0.00005 * (y * y - meanSquare);
        double gain = TARGET_RMS / Math.sqrt(meanSquare + 1e-9);
        if (gain > 60) gain = 60;
        y *= gain;

        if (type == 0) {
            // Blades chopping the air, a slow drift, and the motor's hum.
            phChop = wrap(phChop + TWO_PI * 24.0 / SAMPLE_RATE);
            phSlowA = wrap(phSlowA + TWO_PI * 0.13 / SAMPLE_RATE);
            phSlowB = wrap(phSlowB + TWO_PI * 0.31 / SAMPLE_RATE);
            phWobble = wrap(phWobble + TWO_PI * 0.7 / SAMPLE_RATE);
            ph59 = wrap(ph59 + TWO_PI * 59.0 / SAMPLE_RATE);
            ph118 = wrap(ph118 + TWO_PI * 118.0 / SAMPLE_RATE);
            ph177 = wrap(ph177 + TWO_PI * 177.0 / SAMPLE_RATE);
            ph236 = wrap(ph236 + TWO_PI * 236.0 / SAMPLE_RATE);
            y *= 1 + 0.05 * Math.sin(phChop) + 0.04 * Math.sin(phSlowA)
                    + 0.03 * Math.sin(phSlowB);
            double hum = 0.5 * Math.sin(ph59) + Math.sin(ph118) + 0.4 * Math.sin(ph177)
                    + 0.2 * Math.sin(ph236);
            y += humAmp * hum * (1 + 0.1 * Math.sin(phWobble));
        }
        return y;
    }

    private double pink(double w) {
        // Paul Kellet's economy pink filter.
        b0 = 0.99765 * b0 + w * 0.0990460;
        b1 = 0.96300 * b1 + w * 0.2965164;
        b2 = 0.57000 * b2 + w * 1.0526913;
        return (b0 + b1 + b2 + w * 0.1848) * 0.16;
    }

    private double brown(double w) {
        brown = (brown + 0.02 * w) / 1.02;
        return brown * 3.5;
    }

    private static double wrap(double p) {
        return p >= TWO_PI ? p - TWO_PI : p;
    }
}
