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
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Runs while an alarm is ringing: plays the sound and shows the Snooze / Stop controls. */
public class AlarmService extends Service {
    static final String ACTION_START = "app.easywake.START";
    static final String ACTION_SNOOZE = "app.easywake.SNOOZE";
    static final String ACTION_DISMISS = "app.easywake.DISMISS";

    private static final String CHANNEL = "ring";
    private static final int NOTIF_ID = 1;
    private static final long MAX_RING_MS = 20 * 60 * 1000L;

    /** Id of the alarm ringing right now, or -1. Read by the ringing screen. */
    static volatile int ringingId = -1;
    static volatile String ringingLabel = "";
    static volatile int ringingSnoozeMin = 10;
    /** Set by the ringing screen so it can close itself when the alarm stops. */
    static Runnable onStopped;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SoundPlayer player = new SoundPlayer();
    private Alarm current;
    private Vibrator vibrator;

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            stopRinging();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            int id = intent.getIntExtra("id", -1);
            boolean snooze = intent.getBooleanExtra("snooze", false);
            Alarm a = Store.get(this, id);
            if (a == null) {
                // Alarm was deleted. Android still requires the notification briefly.
                Alarm blank = new Alarm();
                goForeground(blank);
                stopRinging();
            } else {
                startRinging(a, snooze);
            }
        } else if (ACTION_SNOOZE.equals(action)) {
            snooze();
        } else if (ACTION_DISMISS.equals(action)) {
            stopRinging();
        } else if (current == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startRinging(Alarm a, boolean wasSnooze) {
        player.stop();
        stopVibration();

        current = a;
        ringingId = a.id;
        ringingLabel = a.label == null ? "" : a.label;
        ringingSnoozeMin = a.snoozeMin;

        // A one-time alarm switches itself off once it has rung.
        a.snoozeUntil = 0;
        if (!wasSnooze && a.days == 0) a.enabled = false;
        Store.put(this, a);
        Scheduler.rescheduleAll(this);

        // Sleep sounds give way to the alarm.
        try {
            stopService(new Intent(this, NoiseService.class));
        } catch (Exception e) {
            // Not running.
        }

        goForeground(a);
        ensureAudible();
        if (a.vibrateMode == 2) startVibration();
        player.start(this, a, true, new Runnable() {
            @Override
            public void run() {
                startVibration();
            }
        });

        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_RING_MS);
    }

    private void snooze() {
        if (current != null) {
            Alarm a = Store.get(this, current.id);
            if (a != null) {
                a.snoozeUntil = System.currentTimeMillis() + a.snoozeMin * 60000L;
                Store.put(this, a);
                Scheduler.rescheduleAll(this);
            }
        }
        stopRinging();
    }

    private void stopRinging() {
        handler.removeCallbacks(timeout);
        player.stop();
        stopVibration();
        current = null;
        ringingId = -1;
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        Runnable r = onStopped;
        if (r != null) r.run();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        player.stop();
        stopVibration();
        ringingId = -1;
        super.onDestroy();
    }

    /** If the phone's alarm volume is at zero, raise it so the alarm can be heard. */
    private void ensureAudible() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, (int) (max * 0.6f)), 0);
            }
        } catch (Exception e) {
            // Volume is managed by the system (for example Do Not Disturb); leave it.
        }
    }

    private void startVibration() {
        if (current == null || current.vibrateMode == 0 || vibrator != null) return;
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            vibrator = v;
            long[] pattern = {0, 400, 1200};
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
            } else {
                vibrator.vibrate(effect, attrs);
            }
        } catch (Exception e) {
            // Vibration is optional.
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception e) {
                // Nothing to cancel.
            }
            vibrator = null;
        }
    }

    private void goForeground(Alarm a) {
        Notification n = buildNotification(a);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification(Alarm a) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Ringing alarm",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent ring = new Intent(this, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent ringPi = PendingIntent.getActivity(this, 1, ring, piFlags);
        PendingIntent snoozePi = PendingIntent.getService(this, 2,
                new Intent(this, AlarmService.class).setAction(ACTION_SNOOZE), piFlags);
        PendingIntent stopPi = PendingIntent.getService(this, 3,
                new Intent(this, AlarmService.class).setAction(ACTION_DISMISS), piFlags);

        Icon icon = Icon.createWithResource(this, R.drawable.ic_alarm);
        String title = (a.label != null && !a.label.isEmpty()) ? a.label : "Alarm";

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(Ui.timeText(this, a.hour, a.minute))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(ringPi)
                .setFullScreenIntent(ringPi, true)
                .addAction(new Notification.Action.Builder(icon, "Snooze", snoozePi).build())
                .addAction(new Notification.Action.Builder(icon, "Stop", stopPi).build())
                .build();
    }
}
