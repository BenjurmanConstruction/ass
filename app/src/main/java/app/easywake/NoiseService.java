package app.easywake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Plays a sleep sound (fan / noise / your own recording) in the background. */
public class NoiseService extends Service {
    static final String ACTION_START = "app.easywake.NOISE_START";
    static final String ACTION_STOP = "app.easywake.NOISE_STOP";

    /** Sound type meaning "the user's own audio file". */
    static final int TYPE_CUSTOM = 99;

    private static final String CHANNEL = "noise";
    private static final int NOTIF_ID = 2;

    /** Which sound is playing, or -1. Read by the Sleep sounds screen. */
    static volatile int playingType = -1;
    /** When the sleep timer ends (epoch ms), or 0 for no timer. */
    static volatile long endAt = 0;
    /** 0..1 loudness, changed live by the volume slider. */
    static volatile float volume = 0.7f;
    /** 0..100, deep to bright. Changed live by the Tone slider. */
    static volatile int tone = 40;
    /** 0..100 strength of the fan's motor hum. */
    static volatile int hum = 35;
    /** Lets the Sleep sounds screen refresh when playback starts or stops. */
    static Runnable onChange;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile Object token;
    private Thread thread;
    private MediaPlayer filePlayer;
    private PowerManager.WakeLock wakeLock;

    private final Runnable timerDone = new Runnable() {
        @Override
        public void run() {
            stopNoise();
        }
    };

    /** Keeps the own-recording player's loudness in step with the slider. */
    private final Runnable fileVolumeTick = new Runnable() {
        @Override
        public void run() {
            if (filePlayer == null) return;
            try {
                filePlayer.setVolume(volume, volume);
            } catch (IllegalStateException e) {
                return;
            }
            handler.postDelayed(this, 200);
        }
    };

    static String nameOf(Context c, int type) {
        if (type == TYPE_CUSTOM) {
            String n = Prefs.getString(c, "noiseName");
            return n == null || n.isEmpty() ? "My recording" : n;
        }
        if (type < 0 || type >= NoiseGen.NAMES.length) type = 0;
        return NoiseGen.NAMES[type];
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            int type = intent.getIntExtra("type", 0);
            int minutes = intent.getIntExtra("minutes", 0);
            start(type, minutes);
        } else {
            stopNoise();
        }
        return START_NOT_STICKY;
    }

    private void start(int type, int minutes) {
        if (type != TYPE_CUSTOM && (type < 0 || type >= NoiseGen.NAMES.length)) type = 0;

        Notification n = buildNotification(type);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        // Keep the phone's processor awake so the sound doesn't stall with the screen off.
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "easywake:noise");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null) wakeLock.acquire(14 * 60 * 60 * 1000L);

        handler.removeCallbacks(timerDone);
        if (minutes > 0) handler.postDelayed(timerDone, minutes * 60000L);

        stopPlayers();
        boolean ok = true;
        if (type == TYPE_CUSTOM) {
            ok = startFile();
        } else {
            startThread(type);
        }
        if (!ok) {
            stopNoise();
            return;
        }
        playingType = type;
        endAt = minutes > 0 ? System.currentTimeMillis() + minutes * 60000L : 0;
        notifyChange();
    }

    private boolean startFile() {
        String uri = Prefs.getString(this, "noiseUri");
        if (uri == null || uri.isEmpty()) return false;
        MediaPlayer p = new MediaPlayer();
        try {
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            p.setDataSource(this, Uri.parse(uri));
            p.setLooping(true);
            p.prepare();
            p.setVolume(volume, volume);
            p.start();
            filePlayer = p;
            handler.postDelayed(fileVolumeTick, 200);
            return true;
        } catch (Exception e) {
            p.release();
            return false;
        }
    }

    private void startThread(final int type) {
        final Object mine = new Object();
        token = mine;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                AudioTrack track = null;
                try {
                    int sr = NoiseGen.SAMPLE_RATE;
                    int min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sr)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build())
                            .setBufferSizeInBytes(Math.max(min, sr))
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();
                    NoiseGen gen = new NoiseGen(type);
                    short[] buf = new short[2048];
                    float gain = 0f;
                    // Let the loudness leveller settle before anything is heard.
                    gen.setParams(tone, hum);
                    for (int i = 0; i < 12; i++) gen.fill(buf, buf.length, 0f, 0f);
                    track.play();
                    while (token == mine) {
                        gen.setParams(tone, hum);
                        float next = Math.min(1f, gain + 0.05f);
                        gen.fill(buf, buf.length, gain, next);
                        gain = next;
                        track.setVolume(volume);
                        track.write(buf, 0, buf.length);
                    }
                    // Quick fade-out instead of an abrupt cut.
                    while (gain > 0f) {
                        float next = Math.max(0f, gain - 0.2f);
                        gen.fill(buf, buf.length, gain, next);
                        gain = next;
                        track.write(buf, 0, buf.length);
                    }
                } catch (Exception e) {
                    // Audio unavailable; nothing more to do.
                } finally {
                    if (track != null) {
                        try {
                            track.stop();
                        } catch (Exception e) {
                            // Already stopped.
                        }
                        track.release();
                    }
                }
            }
        }, "easywake-noise");
        thread.start();
    }

    private void stopPlayers() {
        token = null;
        handler.removeCallbacks(fileVolumeTick);
        if (filePlayer != null) {
            try {
                filePlayer.stop();
            } catch (Exception e) {
                // Already stopped.
            }
            filePlayer.release();
            filePlayer = null;
        }
    }

    private void stopNoise() {
        handler.removeCallbacks(timerDone);
        stopPlayers();
        playingType = -1;
        endAt = 0;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        notifyChange();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timerDone);
        stopPlayers();
        playingType = -1;
        endAt = 0;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        notifyChange();
        super.onDestroy();
    }

    private void notifyChange() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Runnable r = onChange;
                if (r != null) r.run();
            }
        });
    }

    private Notification buildNotification(int type) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sleep sounds",
                NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 10,
                new Intent(this, SleepActivity.class), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 11,
                new Intent(this, NoiseService.class).setAction(ACTION_STOP), piFlags);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_alarm);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(nameOf(this, type) + " playing")
                .setContentText("Tap to open, or press Stop")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(icon, "Stop", stop).build())
                .build();
    }
}
