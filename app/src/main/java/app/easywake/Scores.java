package app.easywake;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Top-10 boards for the Brain gym tests, plus the points balance. */
final class Scores {
    static final int MAX = 10;

    static final class Entry {
        String name = "";
        /** Higher is always better, whatever the test measures. */
        int rank;
        /** What to show, for example "245 ms". */
        String text = "";
    }

    private Scores() {}

    static List<Entry> load(Context c, int mode) {
        List<Entry> out = new ArrayList<>();
        String raw = Prefs.getString(c, "hs" + mode);
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.name = o.optString("n", "");
                e.rank = o.optInt("r", 0);
                e.text = o.optString("t", "");
                out.add(e);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    private static void save(Context c, int mode, List<Entry> list) {
        JSONArray arr = new JSONArray();
        for (Entry e : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("n", e.name);
                o.put("r", e.rank);
                o.put("t", e.text);
                arr.put(o);
            } catch (Exception ex) {
                // Skip an entry that can't be written.
            }
        }
        Prefs.putString(c, "hs" + mode, arr.toString());
    }

    /** Would this result make the top 10? */
    static boolean qualifies(Context c, int mode, int rank) {
        List<Entry> list = load(c, mode);
        return list.size() < MAX || rank > list.get(list.size() - 1).rank;
    }

    /** Would this result be the new number one? */
    static boolean isBest(Context c, int mode, int rank) {
        List<Entry> list = load(c, mode);
        return list.isEmpty() || rank > list.get(0).rank;
    }

    /** Adds a result and returns its position on the board, or -1 if it didn't make it. */
    static int add(Context c, int mode, String name, int rank, String text) {
        List<Entry> list = load(c, mode);
        Entry e = new Entry();
        e.name = name == null || name.trim().isEmpty() ? "Player" : name.trim();
        e.rank = rank;
        e.text = text;
        int pos = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (rank > list.get(i).rank) {
                pos = i;
                break;
            }
        }
        if (pos >= MAX) return -1;
        list.add(pos, e);
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(c, mode, list);
        return pos;
    }

    static String bestText(Context c, int mode) {
        List<Entry> list = load(c, mode);
        if (list.isEmpty()) return "Not tried yet";
        return "#1: " + list.get(0).name + ", " + list.get(0).text;
    }

    static int points(Context c) {
        return Prefs.getInt(c, "points", 0);
    }

    static void addPoints(Context c, int n) {
        Prefs.putInt(c, "points", Math.max(0, points(c) + n));
    }

    /** Takes points if there are enough. */
    static boolean spend(Context c, int n) {
        int have = points(c);
        if (have < n) return false;
        Prefs.putInt(c, "points", have - n);
        return true;
    }
}
