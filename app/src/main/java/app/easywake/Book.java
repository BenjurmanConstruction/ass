package app.easywake;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of holdings saved on the phone. Used twice: once for the real portfolio you type
 * in, and once for the paper-trading account.
 */
final class Book {
    static final class Pos {
        String sym = "";
        double qty;
        /** Average cost per share. */
        double cost;
    }

    final String key;
    final List<Pos> positions = new ArrayList<>();
    final List<String> history = new ArrayList<>();
    /** Paper cash available. Unused for the real portfolio. */
    double cash;
    /** Total paper cash ever put in, to measure overall return against. */
    double deposited;
    /** Paper account: every trade, deposit and reset, for the Performance page. */
    final List<PaperPerf.Event> events = new ArrayList<>();
    /** True once the old text log has been read into events. */
    boolean eventsReady;
    private static final int MAX_EVENTS = 3000;

    private Book(String key) {
        this.key = key;
    }

    static Book load(Context c, String key) {
        Book b = new Book(key);
        String raw = Prefs.getString(c, key);
        if (raw == null) return b;
        try {
            JSONObject o = new JSONObject(raw);
            b.cash = o.optDouble("cash", 0);
            b.deposited = o.optDouble("dep", 0);
            JSONArray p = o.optJSONArray("p");
            for (int i = 0; p != null && i < p.length(); i++) {
                JSONObject x = p.getJSONObject(i);
                Pos pos = new Pos();
                pos.sym = x.optString("s", "");
                pos.qty = x.optDouble("q", 0);
                pos.cost = x.optDouble("c", 0);
                if (!pos.sym.isEmpty() && pos.qty > 0) b.positions.add(pos);
            }
            JSONArray h = o.optJSONArray("h");
            for (int i = 0; h != null && i < h.length(); i++) b.history.add(h.getString(i));
            b.eventsReady = o.optBoolean("ev", false);
            JSONArray t = o.optJSONArray("t");
            for (int i = 0; t != null && i < t.length(); i++) {
                JSONArray x = t.getJSONArray(i);
                String k = x.optString(1, "");
                if (k.isEmpty()) continue;
                b.events.add(PaperPerf.Event.of(x.optLong(0), k.charAt(0), x.optString(2, ""),
                        x.optDouble(3, 0), x.optDouble(4, 0), x.optDouble(5, 0),
                        x.optDouble(6, 0)));
            }
        } catch (Exception e) {
            // Unreadable data: start empty rather than crash.
        }
        return b;
    }

    void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("cash", cash);
            o.put("dep", deposited);
            JSONArray p = new JSONArray();
            for (Pos pos : positions) {
                JSONObject x = new JSONObject();
                x.put("s", pos.sym);
                x.put("q", pos.qty);
                x.put("c", pos.cost);
                p.put(x);
            }
            o.put("p", p);
            JSONArray h = new JSONArray();
            int from = Math.max(0, history.size() - 60);
            for (int i = from; i < history.size(); i++) h.put(history.get(i));
            o.put("h", h);
            o.put("ev", eventsReady);
            JSONArray t = new JSONArray();
            int start = Math.max(0, events.size() - MAX_EVENTS);
            for (int i = start; i < events.size(); i++) {
                PaperPerf.Event e = events.get(i);
                JSONArray x = new JSONArray();
                x.put(e.ms);
                x.put(String.valueOf(e.kind));
                x.put(e.sym);
                x.put(e.qty);
                x.put(e.price);
                x.put(e.amount);
                x.put(e.gain);
                t.put(x);
            }
            o.put("t", t);
            Prefs.putString(c, key, o.toString());
        } catch (Exception e) {
            // Nothing sensible to do if saving fails.
        }
    }

    /** Records a paper-account event, stamped now. */
    void log(char kind, String sym, double qty, double price, double amount, double gain) {
        events.add(PaperPerf.Event.of(System.currentTimeMillis(), kind, sym, qty, price, amount,
                gain));
    }

    /**
     * The first time through, reads the old text trade log into events, so the
     * Performance page can rebuild the account's past.
     */
    void readOldLog(Context c) {
        if (eventsReady) return;
        events.addAll(0, PaperPerf.parseHistory(history,
                android.text.format.DateFormat.getDateFormat(c),
                android.text.format.DateFormat.getTimeFormat(c)));
        PaperPerf.sort(events);
        eventsReady = true;
    }

    Pos find(String sym) {
        for (Pos p : positions) {
            if (p.sym.equals(sym)) return p;
        }
        return null;
    }

    /** Adds shares, blending the average cost. */
    void add(String sym, double qty, double price) {
        Pos p = find(sym);
        if (p == null) {
            p = new Pos();
            p.sym = sym;
            positions.add(p);
        }
        double total = p.qty * p.cost + qty * price;
        p.qty += qty;
        p.cost = p.qty > 0 ? total / p.qty : 0;
    }

    /** Removes shares. Returns the profit or loss realized on them. */
    double remove(String sym, double qty, double price) {
        Pos p = find(sym);
        if (p == null) return 0;
        double sold = Math.min(qty, p.qty);
        double gain = sold * (price - p.cost);
        p.qty -= sold;
        if (p.qty < 0.000001) positions.remove(p);
        return gain;
    }

    static String money(double v) {
        return (v < 0 ? "-$" : "$") + MarketData.fmt(Math.abs(v));
    }

    static String signed(double v) {
        return (v >= 0 ? "+$" : "-$") + MarketData.fmt(Math.abs(v));
    }

    static String qty(double q) {
        if (Math.abs(q - Math.rint(q)) < 0.0001) return String.valueOf((long) Math.rint(q));
        return String.valueOf(Math.round(q * 1000) / 1000.0);
    }
}
