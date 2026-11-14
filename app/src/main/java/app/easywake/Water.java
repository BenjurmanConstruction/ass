package app.easywake;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** The drink-water tracker: today's count, the small reward, and the reminders. */
final class Water {
    static final int GOAL = 8;
    static final int[] EVERY_HOURS = {1, 2, 3};
    private static final String CHANNEL = "water";
    private static final int CODE = 810000;
    /** Reminders only arrive between these hours of the day. */
    private static final int FIRST_HOUR = 8;
    private static final int LAST_HOUR = 21;

    private Water() {}

    static int count(Context c) {
        return Prefs.getInt(c, "waterDay", 0) == Progress.today()
                ? Prefs.getInt(c, "waterCount", 0) : 0;
    }

    static boolean on(Context c) {
        return Prefs.getInt(c, "waterOn", 0) == 1;
    }

    static int everyHours(Context c) {
        int i = Prefs.getInt(c, "waterEvery", 1);
        return EVERY_HOURS[Math.max(0, Math.min(EVERY_HOURS.length - 1, i))];
    }

    /**
     * Records a glass. The first eight each day earn a Knowledge Point apiece.
     * Returns the points earned (0 or 1).
     */
    static int drink(Context c) {
        int n = count(c) + 1;
        Prefs.putInt(c, "waterDay", Progress.today());
        Prefs.putInt(c, "waterCount", n);
        Stats.add(c, "water", 1);
        int earned = n <= GOAL ? 1 : 0;
        if (earned > 0) Scores.addPoints(c, earned);
        if (n == GOAL) Stats.log(c, "Drank " + GOAL + " glasses of water today");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(CODE);
        schedule(c);
        return earned;
    }

    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c, CODE, new Intent(c, WaterReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets (or clears) the next reminder. */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(pending(c));
        if (!on(c) || !Tree.owned(c, "l_water")) return;
        Calendar next = Calendar.getInstance();
        next.add(Calendar.HOUR_OF_DAY, everyHours(c));
        next.set(Calendar.SECOND, 0);
        int hour = next.get(Calendar.HOUR_OF_DAY);
        // Outside waking hours, or once today's eight are done, wait for the morning.
        boolean doneToday = count(c) >= GOAL
                && next.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance()
                .get(Calendar.DAY_OF_YEAR);
        if (hour > LAST_HOUR || doneToday) {
            next.add(Calendar.DAY_OF_YEAR, 1);
            next.set(Calendar.HOUR_OF_DAY, FIRST_HOUR);
            next.set(Calendar.MINUTE, 0);
        } else if (hour < FIRST_HOUR) {
            next.set(Calendar.HOUR_OF_DAY, FIRST_HOUR);
            next.set(Calendar.MINUTE, 0);
        }
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                    pending(c));
        } catch (Exception ignored) {
        }
    }

    /** Shows the reminder and lines up the next one. */
    static void fire(Context c) {
        if (on(c) && Tree.owned(c, "l_water") && count(c) < GOAL) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Water reminders",
                        NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
                PendingIntent open = PendingIntent.getActivity(c, CODE,
                        new Intent(c, WaterActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                String text = "Glass " + (count(c) + 1) + " of " + GOAL
                        + ". Tap when you've had it.";
                Notification n = new Notification.Builder(c, CHANNEL)
                        .setSmallIcon(R.drawable.ic_alarm)
                        .setContentTitle("Time for a glass of water")
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build();
                nm.notify(CODE, n);
            }
        }
        schedule(c);
    }
}
