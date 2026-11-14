package app.easywake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Price alerts: saved targets, the background check, and the notification. */
final class Alerts {
    private static final String CHANNEL = "alerts";
    private static final int JOB_ID = 7301;
    private static final int CODE_BASE = 700000;
    static final int MAX = 12;

    static final class Item {
        int id;
        String symbol = "";
        /** True: alert when the price rises to the target. False: when it falls to it. */
        boolean above = true;
        double price;
        boolean on = true;
        /** What happened last, such as "Hit 251.20 at 3:42 PM". */
        String note = "";
    }

    private Alerts() {}

    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            String raw = Prefs.getString(c, "alerts");
            if (raw == null || raw.isEmpty()) return out;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.optInt("id");
                it.symbol = o.optString("symbol");
                it.above = o.optBoolean("above", true);
                it.price = o.optDouble("price", 0);
                it.on = o.optBoolean("on", true);
                it.note = o.optString("note");
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("symbol", it.symbol);
                o.put("above", it.above);
                o.put("price", it.price);
                o.put("on", it.on);
                o.put("note", it.note);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        Prefs.putString(c, "alerts", arr.toString());
        schedule(c);
    }

    static int newId(Context c) {
        int id = Prefs.getInt(c, "alertNextId", 1);
        Prefs.putInt(c, "alertNextId", id + 1);
        return id;
    }

    static String describe(Item it) {
        return it.symbol + (it.above ? " rises to " : " falls to ") + MarketData.fmt(it.price);
    }

    /** Asks Android to run the check about every 15 minutes while any alert is waiting. */
    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            boolean any = false;
            for (Item it : load(c)) any |= it.on;
            if (!any) {
                js.cancel(JOB_ID);
                return;
            }
            // Already set up: leave it alone, since scheduling again would restart it.
            if (js.getPendingJob(JOB_ID) != null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, AlertJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } catch (Exception ignored) {
        }
    }

    /**
     * Looks up the price of every waiting alert and notifies for any that were reached.
     * Uses the internet, so call it off the main thread. Returns how many were reached,
     * or -1 if no price could be loaded at all.
     */
    static int check(Context c) {
        List<Item> items = load(c);
        Map<String, Double> prices = new HashMap<>();
        boolean anyWaiting = false;
        boolean anyPrice = false;
        for (Item it : items) {
            if (!it.on) continue;
            anyWaiting = true;
            if (prices.containsKey(it.symbol)) continue;
            MarketData.Series s = MarketData.fetch(
                    new MarketData.Sym(it.symbol, it.symbol, null), 0);
            prices.put(it.symbol, s.ok() ? s.last : Double.NaN);
            anyPrice |= s.ok();
        }
        if (!anyWaiting) return 0;
        if (!anyPrice) return -1;

        // Load again in case the list was edited while the prices were downloading.
        items = load(c);
        int hits = 0;
        String now = new SimpleDateFormat("EEE h:mm a", Locale.US).format(new Date());
        for (Item it : items) {
            if (!it.on) continue;
            Double p = prices.get(it.symbol);
            if (p == null || Double.isNaN(p)) continue;
            boolean reached = it.above ? p >= it.price : p <= it.price;
            if (reached) {
                it.on = false;
                it.note = "Reached " + MarketData.fmt(p) + " on " + now;
                notify(c, it, p);
                hits++;
            } else {
                it.note = "Last check " + now + ": " + MarketData.fmt(p);
            }
        }
        save(c, items);
        return hits;
    }

    private static void notify(Context c, Item it, double price) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Price alerts",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(c, CODE_BASE + it.id,
                new Intent(c, ChartActivity.class)
                        .putExtra("symbol", it.symbol).putExtra("name", it.symbol),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = it.symbol + " is at " + MarketData.fmt(price) + ". Your alert was "
                + (it.above ? "at or above " : "at or below ") + MarketData.fmt(it.price) + ".";
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("Price alert: " + it.symbol)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        nm.notify(CODE_BASE + it.id, n);
    }
}
