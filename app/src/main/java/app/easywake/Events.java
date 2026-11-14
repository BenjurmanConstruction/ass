package app.easywake;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

/** Calendar events you add yourself. */
final class Events {
    static final class Event {
        /** Days since 1970 in the phone's time zone. */
        int day;
        String title = "";
        /** Minutes after midnight, or -1 for an all-day event. */
        int minutes = -1;
    }

    private Events() {}

    static int dayOf(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        long t = c.getTimeInMillis();
        return (int) ((t + TimeZone.getDefault().getOffset(t)) / 86400000L);
    }

    static List<Event> load(Context c) {
        List<Event> out = new ArrayList<>();
        String raw = Prefs.getString(c, "events");
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Event e = new Event();
                e.day = o.optInt("d", 0);
                e.title = o.optString("t", "");
                e.minutes = o.optInt("m", -1);
                out.add(e);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    static void save(Context c, List<Event> events) {
        JSONArray arr = new JSONArray();
        for (Event e : events) {
            try {
                JSONObject o = new JSONObject();
                o.put("d", e.day);
                o.put("t", e.title);
                o.put("m", e.minutes);
                arr.put(o);
            } catch (Exception ex) {
                // Skip one that can't be written.
            }
        }
        Prefs.putString(c, "events", arr.toString());
    }

    /** Events on one day, earliest first. */
    static List<Event> on(List<Event> all, int day) {
        List<Event> out = new ArrayList<>();
        for (Event e : all) {
            if (e.day == day) out.add(e);
        }
        Collections.sort(out, new Comparator<Event>() {
            @Override
            public int compare(Event x, Event y) {
                return x.minutes - y.minutes;
            }
        });
        return out;
    }

    static String line(Context c, Event e) {
        return (e.minutes >= 0 ? Ui.timeText(c, e.minutes / 60, e.minutes % 60) + "  " : "")
                + e.title;
    }

    /** Today's events as bullet lines, for the alarm screen and the helper. */
    static String todayText(Context c) {
        StringBuilder sb = new StringBuilder();
        for (Event e : on(load(c), Progress.today())) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("• ").append(line(c, e));
        }
        return sb.toString();
    }
}
