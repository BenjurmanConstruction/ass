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
    static final int PEG = 7;
    /** The spin clip for the five-reel slot. */
    static final int SPIN5 = 8;
    private static final int COUNT = 9;

    private static final int SR = 22050;
    private static final AudioTrack[] TRACKS = new AudioTrack[COUNT];
    /** How long each slot reel spins, in milliseconds. Must match SlotActivity. */
    static final long[] REEL_MS = {1900, 2700, 3600};
    /** The same for the five-reel slot. Must match Slot2Activity. */
    static final long[] REEL5_MS = {1300, 1700, 2100, 2500, 2900};

    private Sfx() {}

    /** Sound setting: everything, only the win and lose sounds, or nothing. */
    static final int ALL = 1, WINS_ONLY = 2, OFF = 0;
    private static final String[] MODE_NAMES = {"Sound: off", "Sound: on",
            "Sound: wins only"};

    static int mode(Context c) {
        int m = Prefs.getInt(c, "sfxOn", ALL);
        return m < 0 || m > 2 ? ALL : m;
    }

    static String modeName(Context c) {
        return MODE_NAMES[mode(c)];
    }

    /** Steps to the next setting: on, wins only, off. */
    static void nextMode(Context c) {
        int m = mode(c);
        Prefs.putInt(c, "sfxOn", m == ALL ? WINS_ONLY : (m == WINS_ONLY ? OFF : ALL));
        stopAll();
    }

    private static boolean allowed(Context c, int id) {
        int m = mode(c);
        if (m == OFF) return false;
        return m == ALL || id == WIN || id == BIG_WIN || id == JACKPOT || id == LOSE;
    }

    /** Plays a sound from its start, if game sounds are switched on. */
    static synchronized void play(Context c, int id) {
        if (id < 0 || id >= COUNT || !allowed(c, id)) return;
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
        // Stops whatever is still ringing, on every sound.
        for (int i = 0; i < COUNT; i++) stop(i);
    }

    /** A button that switches game sounds on and off. */
    static android.widget.Button toggle(final Context c) {
        final android.widget.Button bt = Ui.button(c, modeName(c), false);
        bt.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                nextMode(c);
                bt.setText(modeName(c));
                play(c, WIN);
            }
        });
        return bt;
    }

    // ---------- building the sounds ----------

    private static short[] build(int id) {
        float[] b;
        switch (id) {
            case SPIN:
                b = spin(REEL_MS);
                break;
            case SPIN5:
                b = spin(REEL5_MS);
                break;
            case WIN:
                b = notes(new double[]{784, 1047}, 0.12, 0.5);
                break;
            case BIG_WIN:
                b = notes(new double[]{523, 659, 784, 1047, 784, 1047, 1319}, 0.11, 0.6);
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
            case PEG:
                b = new float[(int) (0.12 * SR)];
                bell(b, 0, 988, 0.1, 0.5);
                break;
            default:
                b = lose();
                break;
        }
        float peak = 0.001f;
        for (float v : b) peak = Math.max(peak, Math.abs(v));
        // Kept well under full volume: these sit behind the game, not on top of it.
        float[] loudness = {0.3f, 0.45f, 0.5f, 0.55f, 0.25f, 0.3f, 0.35f, 0.3f, 0.3f};
        float scale = loudness[id] / peak;
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
            // A soft mallet tone: mostly the note itself, with a little brightness
            // right at the strike that fades fast.
            double v = Math.sin(2 * Math.PI * freq * t)
                    + 0.25 * Math.sin(2 * Math.PI * freq * 4 * t) * Math.exp(-t * 60);
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
    private static float[] spin(long[] ms) {
        Random r = new Random(7);
        float[] b = new float[(int) ((ms[ms.length - 1] / 1000.0 + 0.35) * SR)];
        for (int reel = 0; reel < ms.length; reel++) {
            double dur = ms[reel] / 1000.0;
            int symbols = 7 + (int) (ms[reel] * 14 / 1000) - 3;
            int last = 0;
            for (int at1 = 0; at1 < ms[reel]; at1++) {
                int passed = (int) (symbols * ease(at1 / (double) ms[reel]));
                if (passed != last) {
                    last = passed;
                    double at = at1 / 1000.0;
                    // A soft, dull click, like a wooden ratchet. Later reels are
                    // quieter so three together don't turn into a buzz.
                    burst(b, r, at, 0.014, Math.max(0.05, 0.16 - reel * 0.03), 0.82);
                }
            }
            thump(b, r, dur, 0.5);
        }
        return b;
    }

    private static float[] jackpot() {
        float[] b = new float[(int) (3.4 * SR)];
        double[] run = {1047, 1319, 1568, 2093};
        double at = 0;
        for (int round = 0; round < 5; round++) {
            for (double f : run) {
                bell(b, at, f * 0.5 * (round % 2 == 0 ? 1 : 1.122), 0.4, 0.6);
                at += 0.085;
            }
        }
        // A long bright chord to finish, with coins falling over it.
        for (double f : new double[]{523, 659, 784, 1047, 1319}) bell(b, at, f, 1.5, 0.5);
        Random r = new Random(3);
        for (int i = 0; i < 14; i++) {
            bell(b, 0.3 + r.nextDouble() * 2.6, 1800 + r.nextInt(900), 0.09, 0.14);
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
            bell(b, at, 1700 + r.nextInt(500), 0.05, 0.3);
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
