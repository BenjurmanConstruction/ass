package app.easywake;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Saves the alarm list on the phone. */
final class Store {
    private static final String PREFS = "easywake";

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<>();
        String raw = prefs(c).getString("alarms", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data: start with an empty list rather than crash.
        }
        return out;
    }

    static synchronized void save(Context c, List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        for (Alarm a : alarms) {
            try {
                arr.put(a.toJson());
            } catch (JSONException e) {
                // Skip an alarm that cannot be written.
            }
        }
        prefs(c).edit().putString("alarms", arr.toString()).commit();
    }

    static synchronized Alarm get(Context c, int id) {
        for (Alarm a : load(c)) {
            if (a.id == id) return a;
        }
        return null;
    }

    /** Adds the alarm, or replaces the saved one with the same id. */
    static synchronized void put(Context c, Alarm alarm) {
        List<Alarm> all = load(c);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == alarm.id) {
                all.set(i, alarm);
                found = true;
                break;
            }
        }
        if (!found) all.add(alarm);
        save(c, all);
    }

    static synchronized void delete(Context c, int id) {
        List<Alarm> all = load(c);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id == id) all.remove(i);
        }
        save(c, all);
    }

    static synchronized int newId(Context c) {
        SharedPreferences p = prefs(c);
        int id = p.getInt("nextId", 1);
        p.edit().putInt("nextId", id + 1).commit();
        return id;
    }
}
