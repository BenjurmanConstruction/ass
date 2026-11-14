package app.easywake;

import java.util.Random;

/** Makes fan and noise sounds live, sample by sample, so they never loop or repeat. */
final class NoiseGen {
    static final String[] NAMES = {"Box fan", "White noise", "Pink noise", "Brown noise"};
    static final String[] HINTS = {
            "Steady whir with a low hum",
            "Bright, even hiss",
            "Softer, like steady rain",
            "Deep rumble, like distant surf"
    };
    static final int SAMPLE_RATE = 44100;

    private static final double TWO_PI = Math.PI * 2;

    private final int type;
    private final Random rnd = new Random();

    private double white;
    private double b0, b1, b2;
    private double brown;
    private double lp1, lp2;
    private double phHum1, phHum2, phWobble, phSlow;

    NoiseGen(int type) {
        this.type = (type >= 0 && type < NAMES.length) ? type : 0;
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
        switch (type) {
            case 1: // white, with the harshest top end taken off
                white += 0.55 * (w - white);
                return white * 0.5;
            case 2: // pink (Paul Kellet's economy filter)
                b0 = 0.99765 * b0 + w * 0.0990460;
                b1 = 0.96300 * b1 + w * 0.2965164;
                b2 = 0.57000 * b2 + w * 1.0526913;
                return (b0 + b1 + b2 + w * 0.1848) * 0.16;
            case 3: // brown
                brown = (brown + 0.02 * w) / 1.02;
                return brown * 3.5;
            default: // fan: muffled air rush plus a faint motor hum that wobbles slightly
                lp1 += 0.06 * (w - lp1);
                lp2 += 0.06 * (lp1 - lp2);
                brown = (brown + 0.02 * w) / 1.02;
                phHum1 = wrap(phHum1 + TWO_PI * 118.0 / SAMPLE_RATE);
                phHum2 = wrap(phHum2 + TWO_PI * 236.0 / SAMPLE_RATE);
                phWobble = wrap(phWobble + TWO_PI * 0.7 / SAMPLE_RATE);
                phSlow = wrap(phSlow + TWO_PI * 0.13 / SAMPLE_RATE);
                double hum = (0.030 * Math.sin(phHum1) + 0.015 * Math.sin(phHum2))
                        * (1 + 0.15 * Math.sin(phWobble));
                double air = lp2 * 3.0 + brown * 1.2;
                return (air + hum) * (1 + 0.05 * Math.sin(phSlow));
        }
    }

    private static double wrap(double p) {
        return p >= TWO_PI ? p - TWO_PI : p;
    }
}
