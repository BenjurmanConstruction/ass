package app.easywake;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Tells Android when to wake the app up for each alarm. */
final class Scheduler {
    private static final int SNOOZE_OFFSET = 100000;

    private Scheduler() {}

    private static PendingIntent firePi(Context c, int id, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction("app.easywake.FIRE")
                .putExtra("id", id)
                .putExtra("snooze", snooze);
        return PendingIntent.getBroadcast(c, snooze ? id + SNOOZE_OFFSET : id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    static void cancel(Context c, int id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(firePi(c, id, false));
        am.cancel(firePi(c, id, true));
    }

    /** Clears and re-registers every alarm. Safe to call at any time. */
    static void rescheduleAll(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = app.getSystemService(AlarmManager.class);
        if (am == null) return;
        long now = System.currentTimeMillis();
        for (Alarm a : Store.load(app)) {
            cancel(app, a.id);
            if (a.enabled) {
                set(app, am, a.nextTrigger(now), firePi(app, a.id, false));
            }
            if (a.snoozeUntil > now) {
                set(app, am, a.snoozeUntil, firePi(app, a.id, true));
            }
        }
    }

    private static void set(Context c, AlarmManager am, long when, PendingIntent pi) {
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
        } catch (SecurityException e) {
            // Exact alarms not allowed: fall back to the closest thing Android permits.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}
