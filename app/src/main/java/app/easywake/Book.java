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
            Prefs.putString(c, key, o.toString());
        } catch (Exception e) {
            // Nothing sensible to do if saving fails.
        }
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
