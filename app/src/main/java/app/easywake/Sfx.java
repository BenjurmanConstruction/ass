package app.easywake;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Sound effects for the games. Every sound is built from scratch in code the first time
 * it is needed (no sound files), then kept ready to replay.
 */
final class Sfx {
    static final int SPIN = 0;
    static final int WIN = 1;
    static final int BIG_WIN = 2;
    static final int JACKPOT = 3;
    static final int CARD = 4;
    static final int CHIPS = 5;
    static final int LOSE = 6;
    private static final int COUNT = 7;

    private static final int SR = 22050;
    private static final AudioTrack[] TRACKS = new AudioTrack[COUNT];
    /** How long each slot reel spins, in milliseconds. Must match SlotActivity. */
    static final long[] REEL_MS = {1900, 2700, 3600};

    private Sfx() {}

    static boolean on(Context c) {
        return Prefs.getInt(c, "sfxOn", 1) == 1;
    }

    static void setOn(Context c, boolean on) {
        Prefs.putInt(c, "sfxOn", on ? 1 : 0);
        if (!on) stopAll();
    }

    /** Plays a sound from its start, if game sounds are switched on. */
    static synchronized void play(Context c, int id) {
        if (!on(c) || id < 0 || id >= COUNT) return;
        try {
            AudioTrack t = TRACKS[id];
            if (t == null) {
                short[] data = build(id);
                t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SR)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(data.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                t.write(data, 0, data.length);
                TRACKS[id] = t;
            } else {
                if (t.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) t.stop();
                t.reloadStaticData();
            }
            t.play();
        } catch (Exception e) {
            // No sound available right now; the game carries on silently.
            TRACKS[id] = null;
        }
    }

    static synchronized void stop(int id) {
        try {
            AudioTrack t = TRACKS[id];
            if (t != null && t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.pause();
        } catch (Exception ignored) {
        }
    }

    static synchronized void stopAll() {
        for (int i = 0; i < COUNT; i++) stop(i);
    }

    /** A button that switches game sounds on and off. */
    static android.widget.Button toggle(final Context c) {
        final android.widget.Button bt = Ui.button(c, on(c) ? "Sound: on" : "Sound: off",
                false);
        bt.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                setOn(c, !on(c));
                bt.setText(on(c) ? "Sound: on" : "Sound: off");
                if (on(c)) play(c, CHIPS);
            }
        });
        return bt;
    }

    // ---------- building the sounds ----------

    private static short[] build(int id) {
        float[] b;
        switch (id) {
            case SPIN:
                b = spin();
                break;
            case WIN:
                b = notes(new double[]{1319, 1760}, 0.11, 0.5);
                break;
            case BIG_WIN:
                b = notes(new double[]{1047, 1319, 1568, 2093, 1568, 2093, 2637}, 0.1, 0.7);
                break;
            case JACKPOT:
                b = jackpot();
                break;
            case CARD:
                b = card();
                break;
            case CHIPS:
                b = chips();
                break;
            default:
                b = lose();
                break;
        }
        float peak = 0.001f;
        for (float v : b) peak = Math.max(peak, Math.abs(v));
        float scale = Math.min(1f, 0.85f / peak);
        short[] out = new short[b.length];
        for (int i = 0; i < b.length; i++) out[i] = (short) (b[i] * scale * 32767);
        return out;
    }

    /** Adds a bell-like note: a tone with a couple of overtones that dies away. */
    private static void bell(float[] b, double start, double freq, double dur, double amp) {
        int from = (int) (start * SR);
        int n = (int) (dur * SR);
        for (int i = 0; i < n && from + i < b.length; i++) {
            double t = i / (double) SR;
            double env = Math.exp(-t * 5.5 / dur) * Math.min(1, i / 40.0);
            double v = Math.sin(2 * Math.PI * freq * t)
                    + 0.4 * Math.sin(2 * Math.PI * freq * 2.01 * t)
                    + 0.2 * Math.sin(2 * Math.PI * freq * 3.02 * t);
            b[from + i] += (float) (v * env * amp);
        }
    }

    /** Adds a short burst of noise, bright or dull, that dies away quickly. */
    private static void burst(float[] b, Random r, double start, double dur, double amp,
                              double dullness) {
        int from = (int) (start * SR);
        int n = (int) (dur * SR);
        double lp = 0;
        for (int i = 0; i < n && from + i < b.length; i++) {
            double env = Math.exp(-i * 6.0 / n);
            double w = r.nextDouble() * 2 - 1;
            lp += (1 - dullness) * (w - lp);
            b[from + i] += (float) (lp * env * amp);
        }
    }

    /** Adds a low thump, like a reel locking into place. */
    private static void thump(float[] b, Random r, double start, double amp) {
        int from = (int) (start * SR);
        int n = (int) (0.16 * SR);
        for (int i = 0; i < n && from + i < b.length; i++) {
            double t = i / (double) SR;
            double env = Math.exp(-t * 28);
            // The pitch drops as it rings, which is what makes it sound heavy.
            double f = 70 + 90 * Math.exp(-t * 40);
            b[from + i] += (float) (Math.sin(2 * Math.PI * f * t) * env * amp);
        }
        burst(b, r, start, 0.03, amp * 0.6, 0.5);
    }

    private static float[] notes(double[] freqs, double step, double amp) {
        float[] b = new float[(int) ((freqs.length * step + 0.6) * SR)];
        for (int i = 0; i < freqs.length; i++) bell(b, i * step, freqs[i], 0.55, amp);
        return b;
    }

    /** Same easing the reels use: steady speed, then braking to a stop. */
    private static double ease(double t) {
        double brakeAt = 0.62;
        double full = brakeAt + (1 - brakeAt) / 2;
        if (t < brakeAt) return t / full;
        double u = (t - brakeAt) / (1 - brakeAt);
        return (brakeAt + (1 - brakeAt) * (u - u * u / 2)) / full;
    }

    /**
     * The whole spin as one clip: each reel ticks as its symbols pass, slowing as it
     * brakes, and thumps when it stops. Timed to match what the reels do on screen.
     */
    private static float[] spin() {
        Random r = new Random(7);
        float[] b = new float[(int) ((REEL_MS[2] / 1000.0 + 0.35) * SR)];
        for (int reel = 0; reel < 3; reel++) {
            double dur = REEL_MS[reel] / 1000.0;
            int symbols = 7 + (int) (REEL_MS[reel] * 14 / 1000) - 3;
            int last = 0;
            for (int ms = 0; ms < REEL_MS[reel]; ms++) {
                int passed = (int) (symbols * ease(ms / (double) REEL_MS[reel]));
                if (passed != last) {
                    last = passed;
                    double at = ms / 1000.0;
                    // Each reel clicks at its own pitch so three together sound busy.
                    int from = (int) (at * SR);
                    int n = (int) (0.012 * SR);
                    double f = 1500 + reel * 350;
                    for (int i = 0; i < n && from + i < b.length; i++) {
                        double t = i / (double) SR;
                        b[from + i] += (float) (Math.sin(2 * Math.PI * f * t)
                                * Math.exp(-t * 420) * 0.16);
                    }
                    burst(b, r, at, 0.008, 0.1, 0.2);
                }
            }
            thump(b, r, dur, 0.9);
        }
        return b;
    }

    private static float[] jackpot() {
        float[] b = new float[(int) (3.4 * SR)];
        double[] run = {1047, 1319, 1568, 2093};
        double at = 0;
        for (int round = 0; round < 5; round++) {
            for (double f : run) {
                bell(b, at, f * (round % 2 == 0 ? 1 : 1.122), 0.4, 0.6);
                at += 0.085;
            }
        }
        // A long bright chord to finish, with coins falling over it.
        for (double f : new double[]{1047, 1319, 1568, 2093, 2637}) bell(b, at, f, 1.5, 0.5);
        Random r = new Random(3);
        for (int i = 0; i < 26; i++) {
            bell(b, 0.3 + r.nextDouble() * 2.6, 3200 + r.nextInt(1800), 0.09, 0.22);
        }
        return b;
    }

    /** A card sliding off the deck: a quick soft swish. */
    private static float[] card() {
        Random r = new Random(5);
        float[] b = new float[(int) (0.12 * SR)];
        int n = b.length;
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            double env = Math.sin(Math.PI * Math.min(1, x * 1.6)) * Math.exp(-x * 3.5);
            double w = r.nextDouble() * 2 - 1;
            lp += 0.55 * (w - lp);
            b[i] = (float) ((w - lp) * env * 0.6);
        }
        burst(b, r, 0.07, 0.02, 0.5, 0.6);
        return b;
    }

    /** Chips dropping onto a pile. */
    private static float[] chips() {
        Random r = new Random(11);
        float[] b = new float[(int) (0.5 * SR)];
        for (int i = 0; i < 6; i++) {
            double at = i * 0.05 + r.nextDouble() * 0.02;
            bell(b, at, 2600 + r.nextInt(900), 0.06, 0.5);
            burst(b, r, at, 0.012, 0.35, 0.1);
        }
        return b;
    }

    /** Two falling notes. */
    private static float[] lose() {
        float[] b = new float[(int) (0.6 * SR)];
        bell(b, 0, 392, 0.3, 0.4);
        bell(b, 0.16, 311, 0.4, 0.4);
        return b;
    }
}
