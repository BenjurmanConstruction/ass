package app.easywake;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Builds the built-in wake-up sounds from scratch, so no sound files ship with the app. */
final class ToneGen {
    static final String[] NAMES = {
            "Soft chimes", "Slow bells", "Gentle waves", "Morning birds", "Classic beep"
    };

    private static final int SR = 44100;
    private static final double TWO_PI = Math.PI * 2;

    private ToneGen() {}

    /** Returns a looping WAV file for the tone, creating it the first time. */
    static synchronized File file(Context c, int tone) throws IOException {
        if (tone < 0 || tone >= NAMES.length) tone = 0;
        File f = new File(c.getCacheDir(), "tone_v1_" + tone + ".wav");
        if (f.exists() && f.length() > 1000) return f;
        float[] b = render(tone);
        normalize(b);
        writeWav(f, b);
        return f;
    }

    private static float[] render(int tone) {
        switch (tone) {
            case 1:
                return bells();
            case 2:
                return waves();
            case 3:
                return birds();
            case 4:
                return beep();
            default:
                return chimes();
        }
    }

    private static void note(float[] b, double start, double freq, double dur, double amp,
                             double decay, double[] ratios, double[] gains) {
        int n = (int) (dur * SR);
        int s0 = (int) (start * SR);
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double env = (1 - Math.exp(-t / 0.008)) * Math.exp(-t / decay);
            double v = 0;
            for (int k = 0; k < ratios.length; k++) {
                v += gains[k] * Math.sin(TWO_PI * freq * ratios[k] * t);
            }
            // Wrap around the end so the loop repeats without a click.
            b[(s0 + i) % b.length] += (float) (amp * env * v);
        }
    }

    private static float[] chimes() {
        float[] b = new float[8 * SR];
        double[] ratios = {1, 2, 3};
        double[] gains = {1, 0.3, 0.1};
        double[] freqs = {523.25, 659.25, 783.99, 1046.50, 783.99, 659.25};
        double[] starts = {0, 0.7, 1.4, 2.1, 4.0, 4.7};
        for (int i = 0; i < freqs.length; i++) {
            note(b, starts[i], freqs[i], 4.0, 0.5, 0.9, ratios, gains);
        }
        return b;
    }

    private static float[] bells() {
        float[] b = new float[10 * SR];
        double[] ratios = {1, 2.0, 2.76, 5.4};
        double[] gains = {1, 0.5, 0.25, 0.1};
        double[] freqs = {392.00, 523.25, 440.00, 587.33};
        double[] starts = {0, 2.5, 5.0, 7.5};
        for (int i = 0; i < freqs.length; i++) {
            note(b, starts[i], freqs[i], 7.0, 0.5, 1.8, ratios, gains);
        }
        return b;
    }

    private static float[] waves() {
        int len = 8 * SR;
        float[] b = new float[len];
        double[] freqs = {220.00, 277.18, 329.63, 440.00};
        double[] gains = {1, 0.7, 0.7, 0.4};
        for (int i = 0; i < len; i++) {
            double t = i / (double) SR;
            double swell = Math.sin(Math.PI * i / (double) len);
            double env = swell * swell;
            double v = 0;
            for (int k = 0; k < freqs.length; k++) {
                v += gains[k] * Math.sin(TWO_PI * freqs[k] * t);
            }
            b[i] = (float) (env * v);
        }
        return b;
    }

    private static float[] birds() {
        int len = 6 * SR;
        float[] b = new float[len];
        Random r = new Random(7);
        double t = 0.2;
        while (t < 5.3) {
            int chirps = 2 + r.nextInt(4);
            for (int c = 0; c < chirps && t < 5.6; c++) {
                double d = 0.06 + 0.08 * r.nextDouble();
                double f0 = 2200 + 1500 * r.nextDouble();
                double f1 = f0 + (r.nextDouble() * 1600 - 400);
                int n = (int) (d * SR);
                int s0 = (int) (t * SR);
                double phase = 0;
                for (int i = 0; i < n && s0 + i < len; i++) {
                    double x = i / (double) n;
                    double f = f0 + (f1 - f0) * x;
                    phase += TWO_PI * f / SR;
                    double env = 0.5 - 0.5 * Math.cos(TWO_PI * x);
                    b[s0 + i] += (float) (0.5 * env * Math.sin(phase));
                }
                t += d + 0.04;
            }
            t += 0.5 + 0.9 * r.nextDouble();
        }
        return b;
    }

    private static float[] beep() {
        int len = 2 * SR;
        float[] b = new float[len];
        double[] starts = {0, 0.25, 0.5};
        int n = (int) (0.15 * SR);
        int ramp = (int) (0.005 * SR);
        for (double start : starts) {
            int s0 = (int) (start * SR);
            for (int i = 0; i < n; i++) {
                double env = 1;
                if (i < ramp) env = i / (double) ramp;
                else if (i > n - ramp) env = (n - i) / (double) ramp;
                b[s0 + i] += (float) (env * Math.sin(TWO_PI * 880 * i / SR));
            }
        }
        return b;
    }

    private static void normalize(float[] b) {
        float peak = 0;
        for (float v : b) {
            float a = Math.abs(v);
            if (a > peak) peak = a;
        }
        if (peak <= 0) return;
        float g = 0.85f / peak;
        for (int i = 0; i < b.length; i++) b[i] *= g;
    }

    private static void writeWav(File f, float[] b) throws IOException {
        int dataLen = b.length * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[]{'R', 'I', 'F', 'F'});
        buf.putInt(36 + dataLen);
        buf.put(new byte[]{'W', 'A', 'V', 'E'});
        buf.put(new byte[]{'f', 'm', 't', ' '});
        buf.putInt(16);
        buf.putShort((short) 1);          // PCM
        buf.putShort((short) 1);          // mono
        buf.putInt(SR);
        buf.putInt(SR * 2);               // bytes per second
        buf.putShort((short) 2);          // bytes per sample frame
        buf.putShort((short) 16);         // bits per sample
        buf.put(new byte[]{'d', 'a', 't', 'a'});
        buf.putInt(dataLen);
        for (float v : b) {
            float cl = Math.max(-1f, Math.min(1f, v));
            buf.putShort((short) Math.round(cl * 32767));
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(buf.array());
        }
    }
}
