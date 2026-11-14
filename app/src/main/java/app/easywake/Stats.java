package app.easywake;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps count of what gets done in the app, for the profile screen: running totals,
 * totals per day, and a list of recent activity. Everything stays on the phone.
 */
final class Stats {
    private static final int LOG_MAX = 150;
    /** Day counters: brain work, gambling bets, new personal bests, points from tests. */
    static final String BRAIN = "b";
    static final String GAMBLE = "g";
    static final String BESTS = "p";
    static final String POINTS = "k";

    private Stats() {}

    static int get(Context c, String key) {
        return Prefs.getInt(c, "st_" + key, 0);
    }

    static void add(Context c, String key, int n) {
        if (n != 0) Prefs.putInt(c, "st_" + key, get(c, key) + n);
    }

    /** Remembers the largest value seen under this name. */
    static void max(Context c, String key, int value) {
        if (value > get(c, key)) Prefs.putInt(c, "st_" + key, value);
    }

    static int day(Context c, String kind, int day) {
        return Prefs.getInt(c, "st_d" + day + kind, 0);
    }

    static void addDay(Context c, String kind, int n) {
        if (n == 0) return;
        String key = "st_d" + Progress.today() + kind;
        Prefs.putInt(c, key, Prefs.getInt(c, key, 0) + n);
    }

    /** Total of a day counter over a run of days ending the given number of days ago. */
    static int span(Context c, String kind, int daysAgoStart, int daysAgoEnd) {
        int today = Progress.today();
        int sum = 0;
        for (int d = daysAgoStart; d <= daysAgoEnd; d++) sum += day(c, kind, today - d);
        return sum;
    }

    /** Adds a line to the recent activity list. */
    static void log(Context c, String text) {
        try {
            String raw = Prefs.getString(c, "st_log");
            JSONArray old = raw == null || raw.isEmpty() ? new JSONArray() : new JSONArray(raw);
            JSONArray out = new JSONArray();
            int from = Math.max(0, old.length() - (LOG_MAX - 1));
            for (int i = from; i < old.length(); i++) out.put(old.get(i));
            JSONArray entry = new JSONArray();
            entry.put(System.currentTimeMillis());
            entry.put(text);
            out.put(entry);
            Prefs.putString(c, "st_log", out.toString());
        } catch (Exception ignored) {
        }
    }

    /** Recent activity, newest first. Each item is {time in ms as text, what happened}. */
    static List<String[]> recent(Context c, int limit) {
        List<String[]> out = new ArrayList<>();
        try {
            String raw = Prefs.getString(c, "st_log");
            if (raw == null || raw.isEmpty()) return out;
            JSONArray arr = new JSONArray(raw);
            for (int i = arr.length() - 1; i >= 0 && out.size() < limit; i--) {
                JSONArray e = arr.getJSONArray(i);
                out.add(new String[]{String.valueOf(e.getLong(0)), e.getString(1)});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** A finished Brain gym test. */
    static void test(Context c, int mode, String result, int points, boolean newBest) {
        add(c, "tests", 1);
        add(c, "test" + mode, 1);
        if (mode >= 0 && mode < Games.CATEGORY.length) add(c, "cat" + Games.CATEGORY[mode], 1);
        add(c, "testPoints", points);
        addDay(c, BRAIN, 1);
        addDay(c, POINTS, points);
        if (newBest) {
            add(c, "bests", 1);
            addDay(c, BESTS, 1);
        }
        String name = mode >= 0 && mode < Games.TITLES.length ? Games.TITLES[mode] : "Test";
        log(c, name + ": " + result + (newBest ? " (new best)" : "") + ", +" + points
                + " points");
    }

    /** One bet at a casino game. Counts it without adding a line to the activity list. */
    static void bet(Context c, String game, int wagered, int won) {
        add(c, game + "Bets", 1);
        add(c, game + "In", wagered);
        add(c, game + "Out", won);
        add(c, "bets", 1);
        add(c, "betIn", wagered);
        add(c, "betOut", won);
        max(c, "bigWin", won);
        addDay(c, GAMBLE, 1);
    }

    /** Tallies one sitting at a casino game so it can be logged as a single line. */
    static final class Sitting {
        private final String label;
        private final String unit;
        private int rounds;
        private int in;
        private int out;

        Sitting(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }

        void round(int wagered, int won) {
            rounds++;
            in += wagered;
            out += won;
        }

        /** Writes the sitting to the activity list and starts a new one. */
        void close(Context c) {
            if (rounds == 0) return;
            int net = out - in;
            log(c, label + ": " + rounds + " " + unit + (rounds == 1 ? "" : "s") + ", "
                    + (net == 0 ? "broke even" : (net > 0 ? "up " : "down ") + Math.abs(net)
                    + " coupons"));
            rounds = 0;
            in = 0;
            out = 0;
        }
    }
}
