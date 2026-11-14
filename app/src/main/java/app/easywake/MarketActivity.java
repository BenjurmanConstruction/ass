package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** S&P 500, Dow and Nasdaq with line charts. Needs an internet connection. */
public class MarketActivity extends Activity {
    private static final String[] NAMES = {"S&P 500", "Dow Jones", "Nasdaq"};
    private static final String[] YAHOO = {"%5EGSPC", "%5EDJI", "%5EIXIC"};
    private static final String[] FRED = {"SP500", "DJIA", "NASDAQCOM"};
    private static final String[] RANGE_LABELS = {"1 day", "1 month", "1 year"};
    private static final String[] RANGE_Q = {"range=1d&interval=5m", "range=1mo&interval=1d",
            "range=1y&interval=1d"};
    private static final int[] FRED_DAYS = {12, 35, 370};
    private static final int UP = 0xFF66D19E;
    private static final int DOWN = 0xFFFF7A7A;
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

    /** Result of loading one index. */
    private static final class Series {
        double[] values = new double[0];
        double last = Double.NaN;
        double prev = Double.NaN;
        boolean live;
        String error;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView[] valueViews = new TextView[3];
    private final TextView[] changeViews = new TextView[3];
    private final ChartView[] charts = new ChartView[3];
    private final Button[] rangeBtns = new Button[3];
    private TextView footer;
    private int range;
    private int generation;
    private boolean visible;

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            load();
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        range = Prefs.getInt(this, "marketRange", 0);
        if (range < 0 || range > 2) range = 0;

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Market"), Ui.full(this, 0));

        LinearLayout ranges = new LinearLayout(this);
        ranges.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button rb = Ui.button(this, RANGE_LABELS[i], false);
            rb.setTextSize(14);
            rb.setMinHeight(Ui.dp(this, 42));
            rb.setMinimumHeight(Ui.dp(this, 42));
            rb.setPadding(0, 0, 0, 0);
            rb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    range = idx;
                    Prefs.putInt(MarketActivity.this, "marketRange", idx);
                    paintRanges();
                    load();
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f);
            int m = Ui.dp(this, 4);
            rp.setMargins(m, 0, m, 0);
            ranges.addView(rb, rp);
            rangeBtns[i] = rb;
        }
        root.addView(ranges, Ui.full(this, 14));

        for (int i = 0; i < 3; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.card(this, 22));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.BOTTOM);
            TextView name = Ui.text(this, NAMES[i], 16, Ui.SUB);
            name.setTypeface(Ui.medium());
            head.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            changeViews[i] = Ui.text(this, "", 15, Ui.SUB);
            head.addView(changeViews[i]);
            card.addView(head);

            valueViews[i] = Ui.text(this, "Loading...", 30, Ui.TEXT);
            valueViews[i].setTypeface(Ui.light());
            card.addView(valueViews[i]);

            charts[i] = new ChartView(this);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 120));
            cp.topMargin = Ui.dp(this, 8);
            card.addView(charts[i], cp);

            root.addView(card, Ui.full(this, 12));
        }

        footer = Ui.text(this, "", 12, Ui.SUB);
        root.addView(footer, Ui.full(this, 14));

        Button refresh = Ui.button(this, "Refresh", false);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        root.addView(refresh, Ui.full(this, 12));

        setContentView(scroll);
        Bg.apply(this, scroll);
        paintRanges();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(autoRefresh);
        handler.post(autoRefresh);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void paintRanges() {
        for (int i = 0; i < 3; i++) {
            boolean on = i == range;
            rangeBtns[i].setBackground(on ? Ui.gold(this, 21)
                    : Ui.ripple(this, Ui.card(this, 21), 21));
            rangeBtns[i].setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
        }
    }

    private void load() {
        final int gen = ++generation;
        final int r = range;
        footer.setText("Loading...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Series[] out = new Series[3];
                for (int i = 0; i < 3; i++) out[i] = fetch(i, r);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen == generation) show(out, r);
                    }
                });
            }
        }).start();
    }

    private void show(Series[] data, int r) {
        DecimalFormat num = new DecimalFormat("#,##0.00");
        boolean anyLive = false;
        boolean anyDaily = false;
        boolean anyFail = false;
        for (int i = 0; i < 3; i++) {
            Series s = data[i];
            if (s.values.length < 2 || Double.isNaN(s.last)) {
                anyFail = true;
                valueViews[i].setText("No data");
                changeViews[i].setText("");
                charts[i].setData(new double[0], Double.NaN, Ui.SUB);
                continue;
            }
            if (s.live) anyLive = true;
            else anyDaily = true;
            valueViews[i].setText(num.format(s.last));
            int color = Ui.SUB;
            if (!Double.isNaN(s.prev) && s.prev != 0) {
                double diff = s.last - s.prev;
                double pct = diff / s.prev * 100;
                color = diff >= 0 ? UP : DOWN;
                changeViews[i].setText((diff >= 0 ? "▲ +" : "▼ ") + num.format(diff)
                        + "  (" + (diff >= 0 ? "+" : "") + num.format(pct) + "%)");
                changeViews[i].setTextColor(color);
            } else {
                changeViews[i].setText("");
            }
            charts[i].setData(s.values, r == 0 && s.live ? s.prev : Double.NaN, color);
        }
        String when = DateFormat.getTimeFormat(this).format(new Date());
        StringBuilder sb = new StringBuilder();
        if (anyFail && !anyLive && !anyDaily) {
            sb.append("Couldn't load market data. Check your internet connection and tap "
                    + "Refresh.");
        } else {
            sb.append("Updated ").append(when).append(". ");
            if (anyLive) {
                sb.append("Data from Yahoo Finance; it can run a few minutes behind. "
                        + "Change is versus the ")
                        .append(r == 0 ? "previous close. " : "start of the period. ");
            }
            if (anyDaily) {
                sb.append("Live data was unavailable, so some charts show daily closing "
                        + "values from FRED (St. Louis Fed), up to a day old. ");
            }
            sb.append("For information only, not investment advice.");
        }
        footer.setText(sb.toString());
    }

    private Series fetch(int index, int r) {
        Series s = fetchYahoo(index, r);
        if (s.values.length >= 2) return s;
        Series f = fetchFred(index, r);
        return f.values.length >= 2 ? f : s;
    }

    private Series fetchYahoo(int index, int r) {
        Series s = new Series();
        s.live = true;
        String[] hosts = {"query1.finance.yahoo.com", "query2.finance.yahoo.com"};
        for (String host : hosts) {
            try {
                String body = http("https://" + host + "/v8/finance/chart/" + YAHOO[index]
                        + "?" + RANGE_Q[r]);
                JSONObject res = new JSONObject(body).getJSONObject("chart")
                        .getJSONArray("result").getJSONObject(0);
                JSONObject meta = res.getJSONObject("meta");
                JSONArray closes = res.getJSONObject("indicators").getJSONArray("quote")
                        .getJSONObject(0).getJSONArray("close");
                List<Double> vals = new ArrayList<>();
                for (int i = 0; i < closes.length(); i++) {
                    if (!closes.isNull(i)) vals.add(closes.getDouble(i));
                }
                if (vals.size() < 2) continue;
                s.values = toArray(vals);
                s.last = meta.optDouble("regularMarketPrice", s.values[s.values.length - 1]);
                s.prev = meta.optDouble("chartPreviousClose",
                        meta.optDouble("previousClose", Double.NaN));
                if (r != 0) s.prev = s.values[0];
                return s;
            } catch (Exception e) {
                s.error = e.getMessage();
            }
        }
        s.values = new double[0];
        return s;
    }

    private Series fetchFred(int index, int r) {
        Series s = new Series();
        s.live = false;
        try {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -FRED_DAYS[r]);
            String start = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            String body = http("https://fred.stlouisfed.org/graph/fredgraph.csv?id="
                    + FRED[index] + "&cosd=" + start);
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
                s.prev = r == 0 ? s.values[s.values.length - 2] : s.values[0];
            }
        } catch (Exception e) {
            s.error = e.getMessage();
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

    /** Simple line chart with a soft fill under the line. */
    static final class ChartView extends View {
        private double[] values = new double[0];
        private double baseline = Double.NaN;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path area = new Path();
        private int color = Ui.SUB;

        ChartView(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Ui.dp(c, 2));
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setStrokeCap(Paint.Cap.ROUND);
            fill.setStyle(Paint.Style.FILL);
            base.setStyle(Paint.Style.STROKE);
            base.setStrokeWidth(Ui.dp(c, 1));
            base.setColor(0x55FFFFFF);
            base.setPathEffect(new DashPathEffect(
                    new float[]{Ui.dp(c, 4), Ui.dp(c, 4)}, 0));
        }

        /** @param baseline value to draw as a dashed reference line, or NaN for none */
        void setData(double[] v, double baseline, int color) {
            this.values = v == null ? new double[0] : v;
            this.baseline = baseline;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int n = values.length;
            float w = getWidth();
            float h = getHeight();
            if (n < 2 || w <= 0 || h <= 0) return;

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (double v : values) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            if (!Double.isNaN(baseline)) {
                if (baseline < min) min = baseline;
                if (baseline > max) max = baseline;
            }
            double span = max - min;
            if (span <= 0) span = 1;
            float padY = Ui.dp(getContext(), 6);
            float usable = h - 2 * padY;

            path.reset();
            area.reset();
            for (int i = 0; i < n; i++) {
                float x = w * i / (float) (n - 1);
                float y = padY + (float) (usable * (1 - (values[i] - min) / span));
                if (i == 0) {
                    path.moveTo(x, y);
                    area.moveTo(x, h);
                    area.lineTo(x, y);
                } else {
                    path.lineTo(x, y);
                    area.lineTo(x, y);
                }
            }
            area.lineTo(w, h);
            area.close();

            fill.setShader(new LinearGradient(0, 0, 0, h, (color & 0x00FFFFFF) | 0x55000000,
                    color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawPath(area, fill);

            if (!Double.isNaN(baseline)) {
                float y = padY + (float) (usable * (1 - (baseline - min) / span));
                Path p = new Path();
                p.moveTo(0, y);
                p.lineTo(w, y);
                cv.drawPath(p, base);
            }

            line.setColor(color);
            cv.drawPath(path, line);
        }
    }
}
