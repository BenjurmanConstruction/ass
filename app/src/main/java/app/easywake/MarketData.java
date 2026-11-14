package app.easywake;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Downloads prices for the Market screens. Runs on background threads only. */
final class MarketData {
    static final String[] RANGE_LABELS = {"1D", "5D", "1M", "6M", "1Y", "5Y"};
    private static final String[] RANGE_Q = {
            "range=1d&interval=5m", "range=5d&interval=30m", "range=1mo&interval=1d",
            "range=6mo&interval=1d", "range=1y&interval=1d", "range=5y&interval=1wk"
    };
    private static final int[] FRED_DAYS = {12, 12, 35, 190, 370, 1830};
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

    static final int UP = 0xFF66D19E;
    static final int DOWN = 0xFFFF7A7A;

    /** Something that can be charted. */
    static final class Sym {
        final String symbol;
        final String name;
        /** Matching series at the St. Louis Fed, used when the live source fails. */
        final String fred;

        Sym(String symbol, String name, String fred) {
            this.symbol = symbol;
            this.name = name;
            this.fred = fred;
        }
    }

    static final Sym[] INDEXES = {
            new Sym("^GSPC", "S&P 500", "SP500"),
            new Sym("^DJI", "Dow Jones", "DJIA"),
            new Sym("^IXIC", "Nasdaq", "NASDAQCOM"),
    };

    static final Sym[] EXTRAS = {
            new Sym("GC=F", "Gold", null),
            new Sym("CL=F", "Crude oil", null),
            new Sym("BTC-USD", "Bitcoin", null),
            new Sym("^TNX", "10-year Treasury yield", null),
            new Sym("^VIX", "VIX (fear gauge)", null),
            new Sym("DX-Y.NYB", "US dollar index", null),
            new Sym("^RUT", "Russell 2000", null),
            new Sym("ES=F", "S&P 500 futures", null),
            new Sym("NQ=F", "Nasdaq futures", null),
    };

    static final String DEFAULT_EXTRAS = "GC=F,CL=F,BTC-USD";

    /** Result of loading one symbol. */
    static final class Series {
        double[] values = new double[0];
        double last = Double.NaN;
        double prev = Double.NaN;
        double high = Double.NaN;
        double low = Double.NaN;
        /** True when from the live source; false when it is daily closes from the Fed. */
        boolean live;
        String error = "";

        boolean ok() {
            return values.length >= 2 && !Double.isNaN(last);
        }

        double change() {
            return last - prev;
        }

        double pct() {
            return prev == 0 || Double.isNaN(prev) ? Double.NaN : (last - prev) / prev * 100;
        }
    }

    private MarketData() {}

    static String fmt(double v) {
        return new DecimalFormat("#,##0.00").format(v);
    }

    /** "+12.30 (+0.45%)" with an arrow. */
    static String changeText(Series s) {
        if (Double.isNaN(s.prev) || s.prev == 0) return "";
        double d = s.change();
        return (d >= 0 ? "▲ +" : "▼ ") + fmt(d) + "  (" + (d >= 0 ? "+" : "")
                + fmt(s.pct()) + "%)";
    }

    /** Cleans up a typed ticker, or returns null if it can't be one. */
    static String clean(String typed) {
        if (typed == null) return null;
        String t = typed.trim().toUpperCase(Locale.US);
        if (t.isEmpty() || t.length() > 12) return null;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            boolean ok = (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '-' || ch == '=' || ch == '^';
            if (!ok) return null;
        }
        return t;
    }

    static List<String> list(Context c, String key, String def) {
        String raw = Prefs.getString(c, key);
        if (raw == null) raw = def;
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    static void saveList(Context c, String key, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        Prefs.putString(c, key, sb.toString());
    }

    static Series fetch(Sym sym, int range) {
        if (range < 0 || range >= RANGE_Q.length) range = 0;
        Series s = fetchYahoo(sym, range);
        if (s.ok() || sym.fred == null) return s;
        Series f = fetchFred(sym, range);
        if (f.ok()) return f;
        s.error = "Yahoo: " + s.error + "; Fed: " + f.error;
        return s;
    }

    private static void finish(Series s) {
        double hi = -Double.MAX_VALUE;
        double lo = Double.MAX_VALUE;
        for (double v : s.values) {
            if (v > hi) hi = v;
            if (v < lo) lo = v;
        }
        if (!Double.isNaN(s.last)) {
            if (s.last > hi) hi = s.last;
            if (s.last < lo) lo = s.last;
        }
        s.high = hi;
        s.low = lo;
    }

    private static Series fetchYahoo(Sym sym, int r) {
        Series s = new Series();
        s.live = true;
        String[] hosts = {"query1.finance.yahoo.com", "query2.finance.yahoo.com"};
        for (String host : hosts) {
            try {
                String body = http("https://" + host + "/v8/finance/chart/"
                        + URLEncoder.encode(sym.symbol, "UTF-8") + "?" + RANGE_Q[r]);
                JSONObject res = new JSONObject(body).getJSONObject("chart")
                        .getJSONArray("result").getJSONObject(0);
                JSONObject meta = res.getJSONObject("meta");
                JSONArray closes = res.getJSONObject("indicators").getJSONArray("quote")
                        .getJSONObject(0).getJSONArray("close");
                List<Double> vals = new ArrayList<>();
                for (int i = 0; i < closes.length(); i++) {
                    if (!closes.isNull(i)) vals.add(closes.getDouble(i));
                }
                if (vals.size() < 2) {
                    s.error = "no prices returned";
                    continue;
                }
                s.values = toArray(vals);
                s.last = meta.optDouble("regularMarketPrice", s.values[s.values.length - 1]);
                s.prev = meta.optDouble("chartPreviousClose",
                        meta.optDouble("previousClose", Double.NaN));
                if (r != 0 || Double.isNaN(s.prev)) s.prev = s.values[0];
                finish(s);
                s.error = "";
                return s;
            } catch (Exception e) {
                s.error = String.valueOf(e.getMessage());
            }
        }
        s.values = new double[0];
        return s;
    }

    private static Series fetchFred(Sym sym, int r) {
        Series s = new Series();
        s.live = false;
        try {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -FRED_DAYS[r]);
            String start = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            String body = http("https://fred.stlouisfed.org/graph/fredgraph.csv?id="
                    + sym.fred + "&cosd=" + start);
            List<Double> vals = new ArrayList<>();
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].trim().split(",");
                if (parts.length < 2) continue;
                try {
                    vals.add(Double.parseDouble(parts[1]));
                } catch (NumberFormatException e) {
                    // Holidays are listed with a "." instead of a number.
                }
            }
            if (vals.size() >= 2) {
                s.values = toArray(vals);
                s.last = s.values[s.values.length - 1];
                s.prev = r <= 1 ? s.values[s.values.length - 2] : s.values[0];
                finish(s);
            } else {
                s.error = "no prices returned";
            }
        } catch (Exception e) {
            s.error = String.valueOf(e.getMessage());
        }
        return s;
    }

    private static double[] toArray(List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static String http(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(12000);
            con.setRequestProperty("User-Agent", AGENT);
            con.setRequestProperty("Accept", "application/json,text/csv,*/*");
            int code = con.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 4000000) break;
                }
                return out.toString("UTF-8");
            }
        } finally {
            con.disconnect();
        }
    }
}
