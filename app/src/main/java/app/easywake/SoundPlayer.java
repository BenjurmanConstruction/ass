package app.easywake;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.File;

/** Plays an alarm's sound on a loop, optionally fading the volume in. */
final class SoundPlayer {
    private static final float START_VOLUME = 0.03f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private long fadeStart;
    private int fadeMs;
    private Runnable onFadeDone;
    private long clipStart;
    private long clipEnd;

    /** Keeps playback inside the chosen part of the song. */
    private final Runnable clipTick = new Runnable() {
        @Override
        public void run() {
            if (mp == null) return;
            try {
                int pos = mp.getCurrentPosition();
                if (pos >= clipEnd || pos < clipStart - 1500) {
                    mp.seekTo(clipStart, MediaPlayer.SEEK_CLOSEST);
                }
            } catch (IllegalStateException e) {
                return;
            }
            handler.postDelayed(this, 80);
        }
    };

    private final Runnable fadeTick = new Runnable() {
        @Override
        public void run() {
            if (mp == null) return;
            float x = (SystemClock.elapsedRealtime() - fadeStart) / (float) fadeMs;
            if (x >= 1f) {
                setVolume(1f);
                Runnable done = onFadeDone;
                onFadeDone = null;
                if (done != null) done.run();
                return;
            }
            // Squared curve: volume creeps up slowly at first, which feels gentler.
            setVolume(START_VOLUME + (1f - START_VOLUME) * x * x);
            handler.postDelayed(this, 200);
        }
    };

    /**
     * Starts playing. Falls back to a built-in tone if the music file cannot be played,
     * so the alarm still makes sound.
     */
    void start(Context c, Alarm a, boolean withFade, Runnable fadeDone) {
        stop();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        MediaPlayer p = null;
        boolean fromFile = false;
        if (a.usesFile()) {
            p = tryCreate(c, attrs, Uri.parse(a.uri), null);
            fromFile = p != null;
        }
        if (p == null) {
            try {
                File f = ToneGen.file(c, a.tone);
                p = tryCreate(c, attrs, null, f.getAbsolutePath());
            } catch (Exception e) {
                p = null;
            }
        }
        if (p == null) {
            Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (def != null) p = tryCreate(c, attrs, def, null);
        }
        mp = p;
        onFadeDone = fadeDone;
        if (mp == null) {
            onFadeDone = null;
            if (fadeDone != null) fadeDone.run();
            return;
        }

        if (fromFile && a.hasClip()) {
            try {
                int dur = mp.getDuration();
                clipStart = Math.max(0, a.clipStartMs);
                clipEnd = dur > 0 ? Math.min(a.clipEndMs, dur) : a.clipEndMs;
                if (clipEnd - clipStart >= 500) {
                    mp.seekTo(clipStart, MediaPlayer.SEEK_CLOSEST);
                    handler.postDelayed(clipTick, 80);
                }
            } catch (Exception e) {
                // Play the whole song instead.
            }
        }

        fadeMs = withFade ? a.fadeSec * 1000 : 0;
        if (fadeMs > 0) {
            setVolume(START_VOLUME);
            fadeStart = SystemClock.elapsedRealtime();
            handler.postDelayed(fadeTick, 200);
        } else {
            setVolume(1f);
            onFadeDone = null;
            if (fadeDone != null) fadeDone.run();
        }
        try {
            mp.start();
        } catch (IllegalStateException e) {
            stop();
        }
    }

    private static MediaPlayer tryCreate(Context c, AudioAttributes attrs, Uri uri, String path) {
        MediaPlayer p = new MediaPlayer();
        try {
            p.setAudioAttributes(attrs);
            if (uri != null) {
                p.setDataSource(c, uri);
            } else {
                p.setDataSource(path);
            }
            p.setLooping(true);
            p.setWakeMode(c.getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            p.prepare();
            return p;
        } catch (Exception e) {
            p.release();
            return null;
        }
    }

    private void setVolume(float v) {
        if (mp == null) return;
        try {
            mp.setVolume(v, v);
        } catch (IllegalStateException e) {
            // Player already released.
        }
    }

    boolean isPlaying() {
        return mp != null;
    }

    void stop() {
        handler.removeCallbacks(fadeTick);
        handler.removeCallbacks(clipTick);
        onFadeDone = null;
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception e) {
                // Already stopped.
            }
            mp.release();
            mp = null;
        }
    }
}
