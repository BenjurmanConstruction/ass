package app.easywake;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Daily and weekly reminders that arrive as ordinary notifications (not ringing alarms). */
final class Reminders {
    private static final int CODE_BASE = 500000;
    private static final String CHANNEL = "reminders";

    static final class Item {
        int id;
        String text = "";
        int hour = 9;
        int minute = 0;
        /** Same layout as alarms: bit 0 = Sunday ... bit 6 = Saturday; 0 = once. */
        int days = 127;
        boolean on = true;
    }

    private Reminders() {}

    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        String raw = Prefs.getString(c, "reminders");
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.optInt("id", i + 1);
                it.text = o.optString("t", "");
                it.hour = o.optInt("h", 9);
                it.minute = o.optInt("m", 0);
                it.days = o.optInt("d", 127);
                it.on = o.optBoolean("on", true);
                out.add(it);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    static void save(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        for (Item it : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("t", it.text);
                o.put("h", it.hour);
                o.put("m", it.minute);
                o.put("d", it.days);
                o.put("on", it.on);
                arr.put(o);
            } catch (Exception e) {
                // Skip one that can't be written.
            }
        }
        Prefs.putString(c, "reminders", arr.toString());
    }

    static int newId(Context c) {
        int id = Prefs.getInt(c, "reminderNextId", 1);
        Prefs.putInt(c, "reminderNextId", id + 1);
        return id;
    }

    /** The next time this reminder is due. */
    static long next(Item it, long now) {
        Alarm a = new Alarm();
        a.hour = it.hour;
        a.minute = it.minute;
        a.days = it.days;
        return a.nextTrigger(now);
    }

    private static PendingIntent pending(Context c, int id) {
        Intent i = new Intent(c, ReminderReceiver.class)
                .setAction("app.easywake.REMIND")
                .putExtra("id", id);
        return PendingIntent.getBroadcast(c, CODE_BASE + id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancel(Context c, int id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pending(c, id));
    }

    /** Clears and re-registers every reminder. Safe to call any time. */
    static void scheduleAll(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = app.getSystemService(AlarmManager.class);
        if (am == null) return;
        long now = System.currentTimeMillis();
        for (Item it : load(app)) {
            PendingIntent pi = pending(app, it.id);
            am.cancel(pi);
            if (!it.on) continue;
            long when = next(it, now);
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        }
    }

    /** Shows the notification for a reminder that has come due. */
    static void fire(Context c, int id) {
        List<Item> items = load(c);
        Item hit = null;
        for (Item it : items) {
            if (it.id == id) hit = it;
        }
        if (hit == null || !hit.on) return;

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
            PendingIntent open = PendingIntent.getActivity(c, CODE_BASE + id,
                    new Intent(c, RemindersActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle("Reminder")
                    .setContentText(hit.text)
                    .setStyle(new Notification.BigTextStyle().bigText(hit.text))
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build();
            nm.notify(CODE_BASE + id, n);
        }

        // A one-time reminder switches itself off once it has fired.
        if (hit.days == 0) {
            hit.on = false;
            save(c, items);
        }
        scheduleAll(c);
    }

    static String repeatText(int days) {
        return days == 0 ? "Once" : Ui.daysText(days);
    }
}
