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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One symbol on its own screen: price scale, touch readout, chart styles and indicators. */
public class ChartActivity extends Activity {
    private static final String[] TYPE_NAMES = {"Fine line", "Bold line", "Area", "Candles",
            "Bars"};
    private static final String[] IND_NAMES = {"MA 20", "MA 50", "Bollinger", "RSI", "MACD",
            "Volume"};
    static final int IND_MA20 = 1;
    static final int IND_MA50 = 2;
    static final int IND_BOLL = 4;
    static final int IND_RSI = 8;
    static final int IND_MACD = 16;
    static final int IND_VOL = 32;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MarketData.Sym sym;
    private MarketData.Series series;
    private int range;
    private int type;
    private int indicators;
    private int generation;
    private boolean visible;

    private TextView price;
    private TextView change;
    private TextView readout;
    private TextView stats;
    private TextView footer;
    private ProChart chart;
    private final Button[] rangeBtns = new Button[MarketData.RANGE_LABELS.length];
    private final Button[] typeBtns = new Button[TYPE_NAMES.length];
    private final Button[] indBtns = new Button[IND_NAMES.length];
    private LinearLayout colorRow;
    private int autoColor = Ui.SUB;

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
        String symbol = getIntent().getStringExtra("symbol");
        String name = getIntent().getStringExtra("name");
        if (symbol == null) {
            finish();
            return;
        }
        sym = new MarketData.Sym(symbol, name == null ? symbol : name,
                getIntent().getStringExtra("fred"));
        range = getIntent().getIntExtra("range", 0);
        if (range < 0 || range >= rangeBtns.length) range = 0;
        type = Prefs.getInt(this, "chartType", 0);
        if (type < 0 || type >= TYPE_NAMES.length) type = 0;
        indicators = Prefs.getInt(this, "chartInd", 0);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, sym.name), Ui.full(this, 0));

        price = Ui.text(this, "Loading...", 38, Ui.TEXT);
        price.setTypeface(Ui.light());
        root.addView(price, Ui.full(this, 10));
        change = Ui.text(this, "", 16, Ui.SUB);
        root.addView(change, Ui.full(this, 0));
        readout = Ui.text(this, "Touch the chart to read the price at any point.", 13,
                Ui.ACCENT);
        readout.setMinLines(2);
        root.addView(readout, Ui.full(this, 10));

        chart = new ProChart(this);
        chart.listener = new ProChart.Listener() {
            @Override
            public void onPoint(int index) {
                paintReadout(index);
            }
        };
        root.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 300)));

        root.addView(pills(MarketData.RANGE_LABELS, rangeBtns, new Pick() {
            @Override
            public void pick(int i) {
                range = i;
                paintPills();
                load();
            }
        }), Ui.full(this, 14));

        stats = Ui.text(this, "", 13, Ui.SUB);
        root.addView(stats, Ui.full(this, 12));

        root.addView(Ui.heading(this, "Chart style"), Ui.full(this, 22));
        root.addView(pills(TYPE_NAMES, typeBtns, new Pick() {
            @Override
            public void pick(int i) {
                type = i;
                Prefs.putInt(ChartActivity.this, "chartType", i);
                paintPills();
                applyToChart();
            }
        }), Ui.full(this, 10));

        root.addView(Ui.heading(this, "Line color"), Ui.full(this, 22));
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorScroll.addView(colorRow);
        root.addView(colorScroll, Ui.full(this, 10));
        paintColors();
        root.addView(Ui.text(this, "Auto is green when up and red when down. Candles and "
                + "bars always use green and red.", 12, Ui.SUB), Ui.full(this, 8));

        root.addView(Ui.heading(this, "Indicators"), Ui.full(this, 22));
        root.addView(pills(IND_NAMES, indBtns, new Pick() {
            @Override
            public void pick(int i) {
                indicators ^= (1 << i);
                Prefs.putInt(ChartActivity.this, "chartInd", indicators);
                paintPills();
                applyToChart();
            }
        }), Ui.full(this, 10));
        root.addView(Ui.text(this, "MA = moving average of the last 20 or 50 points on the "
                + "chart. Bollinger = a band two standard deviations around MA 20. RSI above "
                + "70 is often called overbought, below 30 oversold. MACD compares a fast and "
                + "slow average.", 12, Ui.SUB), Ui.full(this, 10));

        footer = Ui.text(this, "", 12, Ui.SUB);
        root.addView(footer, Ui.full(this, 16));

        setContentView(scroll);
        Bg.apply(this, scroll);
        paintPills();
        applyToChart();
    }

    private void paintColors() {
        colorRow.removeAllViews();
        int current = Prefs.getInt(this, "chartLine", 0);
        for (int i = 0; i < MarketData.LINE_NAMES.length; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(MarketData.lineSwatch(i));
            g.setStroke(Ui.dp(this, i == current ? 3 : 1),
                    i == current ? 0xFFFFFFFF : Ui.STROKE);
            dot.setBackground(g);
            cell.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
            TextView name = Ui.text(this, MarketData.LINE_NAMES[i], 11,
                    i == current ? Ui.TEXT : Ui.SUB);
            name.setGravity(android.view.Gravity.CENTER);
            cell.addView(name, Ui.full(this, 4));
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.putInt(ChartActivity.this, "chartLine", idx);
                    paintColors();
                    chart.setLineColor(MarketData.lineColor(ChartActivity.this, autoColor));
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = Ui.dp(this, 16);
            colorRow.addView(cell, cp);
        }
    }

    private interface Pick {
        void pick(int index);
    }

    private View pills(String[] names, Button[] out, final Pick pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button bt = Ui.button(this, names[i], false);
            bt.setTextSize(14);
            bt.setMinHeight(Ui.dp(this, 40));
            bt.setMinimumHeight(Ui.dp(this, 40));
            bt.setMinWidth(0);
            bt.setMinimumWidth(0);
            bt.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.pick(idx);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
            p.rightMargin = Ui.dp(this, 8);
            row.addView(bt, p);
            out[i] = bt;
        }
        hs.addView(row);
        return hs;
    }

    private void paintPill(Button bt, boolean on) {
        bt.setBackground(on ? Ui.gold(this, 20) : Ui.ripple(this, Ui.card(this, 20), 20));
        bt.setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
    }

    private void paintPills() {
        for (int i = 0; i < rangeBtns.length; i++) paintPill(rangeBtns[i], i == range);
        for (int i = 0; i < typeBtns.length; i++) paintPill(typeBtns[i], i == type);
        for (int i = 0; i < indBtns.length; i++) {
            paintPill(indBtns[i], (indicators & (1 << i)) != 0);
        }
    }

    /** Pushes the style and indicator choices to the chart and resizes it for sub-panels. */
    private void applyToChart() {
        int h = 300;
        if ((indicators & IND_VOL) != 0) h += 70;
        if ((indicators & IND_RSI) != 0) h += 90;
        if ((indicators & IND_MACD) != 0) h += 90;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chart.getLayoutParams();
        lp.height = Ui.dp(this, h);
        chart.setLayoutParams(lp);
        chart.configure(type, indicators);
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

    private void load() {
        final int gen = ++generation;
        final int r = range;
        footer.setText("Loading...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MarketData.Series s = MarketData.fetch(sym, r);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen == generation && !isFinishing()) show(s, r);
                    }
                });
            }
        }).start();
    }

    private void show(MarketData.Series s, int r) {
        if (!s.ok()) {
            series = null;
            price.setText("No data");
            change.setText("");
            stats.setText("");
            chart.setSeries(null, Ui.SUB, Double.NaN, r);
            footer.setText("Couldn't load this chart. Check your internet connection."
                    + (s.error.isEmpty() ? "" : " (Details: " + s.error + ")"));
            return;
        }
        series = s;
        int color = Ui.SUB;
        if (!Double.isNaN(s.prev) && s.prev != 0) {
            color = s.change() >= 0 ? MarketData.UP : MarketData.DOWN;
        }
        price.setText(MarketData.fmt(s.last));
        change.setText(MarketData.changeText(s) + (r == 0 ? "  today" : "  this period"));
        change.setTextColor(color);
        stats.setText("Low " + MarketData.fmt(s.low) + "   •   High "
                + MarketData.fmt(s.high)
                + (Double.isNaN(s.prev) ? "" : "   •   "
                + (r == 0 ? "Prev close " : "Start ") + MarketData.fmt(s.prev)));
        autoColor = color;
        chart.setSeries(s, MarketData.lineColor(this, color),
                r == 0 && s.live ? s.prev : Double.NaN, r);
        readout.setText("Touch the chart to read the price at any point.");
        footer.setText("Updated " + android.text.format.DateFormat.getTimeFormat(this)
                .format(new Date()) + ". " + (s.live
                ? "Data from Yahoo Finance; it can run a few minutes behind. "
                : "Live data was unavailable, so this shows daily closes from the St. Louis "
                + "Fed. Candles and volume need the live source. ")
                + "For information only, not investment advice.");
    }

    private void paintReadout(int i) {
        if (series == null || i < 0 || i >= series.values.length) {
            readout.setText("Touch the chart to read the price at any point.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(MarketData.fmt(series.values[i]));
        if (i < series.times.length && series.times[i] > 0) {
            sb.append("   •   ").append(ProChart.timeLabel(series.times[i], range, true));
        }
        StringBuilder extra = new StringBuilder();
        if ((indicators & IND_MA20) != 0) add(extra, "MA20", chart.ma20, i);
        if ((indicators & IND_MA50) != 0) add(extra, "MA50", chart.ma50, i);
        if ((indicators & IND_RSI) != 0) add(extra, "RSI", chart.rsi, i);
        if ((indicators & IND_MACD) != 0) add(extra, "MACD", chart.macd, i);
        if (extra.length() > 0) sb.append("\n").append(extra);
        readout.setText(sb.toString());
    }

    private static void add(StringBuilder sb, String label, double[] arr, int i) {
        if (arr == null || i >= arr.length || Double.isNaN(arr[i])) return;
        if (sb.length() > 0) sb.append("   •   ");
        sb.append(label).append(' ').append(MarketData.fmt(arr[i]));
    }

    /** The big chart: main price panel plus optional volume, RSI and MACD panels. */
    static final class ProChart extends View {
        interface Listener {
            void onPoint(int index);
        }

        Listener listener;
        double[] ma20;
        double[] ma50;
        double[] bollUp;
        double[] bollDn;
        double[] rsi;
        double[] macd;
        double[] macdSignal;

        private MarketData.Series s;
        private int color = Ui.SUB;
        private double baseline = Double.NaN;
        private int range;
        private int type;
        private int ind;
        private int touched = -1;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float d;

        ProChart(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            txt.setTextSize(11 * d);
            txt.setColor(Ui.SUB);
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(d);
            dash.setColor(0x55FFFFFF);
            dash.setPathEffect(new DashPathEffect(new float[]{4 * d, 4 * d}, 0));
        }

        void setLineColor(int color) {
            this.color = color;
            invalidate();
        }

        void configure(int type, int indicators) {
            this.type = type;
            this.ind = indicators;
            invalidate();
        }

        void setSeries(MarketData.Series series, int color, double baseline, int range) {
            this.s = series;
            this.color = color;
            this.baseline = baseline;
            this.range = range;
            this.touched = -1;
            compute();
            invalidate();
        }

        // ----- indicator math -----

        private static double[] sma(double[] v, int n) {
            double[] out = new double[v.length];
            double sum = 0;
            for (int i = 0; i < v.length; i++) {
                sum += v[i];
                if (i >= n) sum -= v[i - n];
                out[i] = i >= n - 1 ? sum / n : Double.NaN;
            }
            return out;
        }

        private static double[] ema(double[] v, int n) {
            double[] out = new double[v.length];
            if (v.length == 0) return out;
            double k = 2.0 / (n + 1);
            out[0] = v[0];
            for (int i = 1; i < v.length; i++) out[i] = v[i] * k + out[i - 1] * (1 - k);
            return out;
        }

        private void compute() {
            ma20 = ma50 = bollUp = bollDn = rsi = macd = macdSignal = null;
            if (s == null || s.values.length < 2) return;
            double[] v = s.values;
            int n = v.length;
            ma20 = sma(v, 20);
            ma50 = sma(v, 50);

            bollUp = new double[n];
            bollDn = new double[n];
            for (int i = 0; i < n; i++) {
                if (i < 19) {
                    bollUp[i] = bollDn[i] = Double.NaN;
                    continue;
                }
                double mean = ma20[i];
                double sq = 0;
                for (int j = i - 19; j <= i; j++) sq += (v[j] - mean) * (v[j] - mean);
                double sd = Math.sqrt(sq / 20);
                bollUp[i] = mean + 2 * sd;
                bollDn[i] = mean - 2 * sd;
            }

            // RSI 14, Wilder's smoothing.
            rsi = new double[n];
            double gain = 0;
            double loss = 0;
            for (int i = 0; i < n; i++) {
                if (i == 0) {
                    rsi[i] = Double.NaN;
                    continue;
                }
                double ch = v[i] - v[i - 1];
                double g = Math.max(ch, 0);
                double l = Math.max(-ch, 0);
                if (i <= 14) {
                    gain += g / 14;
                    loss += l / 14;
                    rsi[i] = i == 14 ? rsiOf(gain, loss) : Double.NaN;
                } else {
                    gain = (gain * 13 + g) / 14;
                    loss = (loss * 13 + l) / 14;
                    rsi[i] = rsiOf(gain, loss);
                }
            }

            double[] fast = ema(v, 12);
            double[] slow = ema(v, 26);
            macd = new double[n];
            for (int i = 0; i < n; i++) macd[i] = fast[i] - slow[i];
            macdSignal = ema(macd, 9);
        }

        private static double rsiOf(double gain, double loss) {
            if (loss == 0) return 100;
            return 100 - 100 / (1 + gain / loss);
        }

        // ----- labels -----

        static String timeLabel(long t, int range, boolean full) {
            String pattern;
            if (range == 0) pattern = full ? "MMM d, h:mm a" : "h:mm a";
            else if (range == 1) pattern = full ? "EEE MMM d, h:mm a" : "EEE";
            else if (range == 5) pattern = full ? "MMM d, yyyy" : "MMM yyyy";
            else pattern = full ? "MMM d, yyyy" : "MMM d";
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(t));
        }

        // ----- drawing -----

        private float left() {
            return 0;
        }

        private float right() {
            return getWidth() - 58 * d;
        }

        private float xAt(int i, int n) {
            float w = right() - left();
            return n <= 1 ? left() : left() + w * i / (float) (n - 1);
        }

        private void line(Canvas cv, double[] arr, float top, float h, double min, double span,
                          int col, float width) {
            if (arr == null) return;
            path.reset();
            boolean started = false;
            int n = arr.length;
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(arr[i])) continue;
                float x = xAt(i, n);
                float y = top + (float) (h * (1 - (arr[i] - min) / span));
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(col);
            cv.drawPath(path, p);
        }

        private void hline(Canvas cv, float y) {
            path.reset();
            path.moveTo(left(), y);
            path.lineTo(right(), y);
            cv.drawPath(path, dash);
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (s == null || s.values.length < 2 || getWidth() <= 0) return;
            double[] v = s.values;
            int n = v.length;
            boolean ohlc = s.open.length == n && s.hi.length == n && s.lo.length == n;
            boolean candles = (type == 3 || type == 4) && ohlc;

            float xLabels = 18 * d;
            float volH = (ind & IND_VOL) != 0 ? 70 * d : 0;
            float rsiH = (ind & IND_RSI) != 0 ? 90 * d : 0;
            float macdH = (ind & IND_MACD) != 0 ? 90 * d : 0;
            float mainTop = 8 * d;
            float mainH = getHeight() - mainTop - xLabels - volH - rsiH - macdH - 4 * d;
            if (mainH < 40 * d) return;

            // Price range, including whatever overlays are showing.
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double lo = candles ? s.lo[i] : v[i];
                double hi = candles ? s.hi[i] : v[i];
                if (lo < min) min = lo;
                if (hi > max) max = hi;
                if ((ind & IND_BOLL) != 0 && bollUp != null && !Double.isNaN(bollUp[i])) {
                    if (bollUp[i] > max) max = bollUp[i];
                    if (bollDn[i] < min) min = bollDn[i];
                }
            }
            if (!Double.isNaN(baseline)) {
                if (baseline < min) min = baseline;
                if (baseline > max) max = baseline;
            }
            double span = max - min;
            if (span <= 0) span = 1;

            // Grid and price scale.
            txt.setColor(Ui.SUB);
            txt.setTextAlign(Paint.Align.LEFT);
            for (int g = 0; g <= 4; g++) {
                float y = mainTop + mainH * g / 4f;
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d * 0.6f);
                p.setColor(0x22FFFFFF);
                cv.drawLine(left(), y, right(), y, p);
                double val = max - span * g / 4.0;
                cv.drawText(MarketData.fmt(val), right() + 6 * d, y + 4 * d, txt);
            }

            if (!Double.isNaN(baseline)) {
                hline(cv, mainTop + (float) (mainH * (1 - (baseline - min) / span)));
            }

            if (candles) {
                float step = (right() - left()) / (float) Math.max(1, n - 1);
                float body = Math.max(d, Math.min(step * 0.7f, 10 * d));
                for (int i = 0; i < n; i++) {
                    float x = xAt(i, n);
                    boolean up = v[i] >= s.open[i];
                    int col = up ? MarketData.UP : MarketData.DOWN;
                    float yO = mainTop + (float) (mainH * (1 - (s.open[i] - min) / span));
                    float yC = mainTop + (float) (mainH * (1 - (v[i] - min) / span));
                    float yH = mainTop + (float) (mainH * (1 - (s.hi[i] - min) / span));
                    float yL = mainTop + (float) (mainH * (1 - (s.lo[i] - min) / span));
                    p.setShader(null);
                    p.setColor(col);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(d * 0.8f, 1f));
                    cv.drawLine(x, yH, x, yL, p);
                    if (type == 3) {
                        p.setStyle(Paint.Style.FILL);
                        float t = Math.min(yO, yC);
                        float bt = Math.max(yO, yC);
                        if (bt - t < d) bt = t + d;
                        cv.drawRect(x - body / 2, t, x + body / 2, bt, p);
                    } else {
                        cv.drawLine(x - body / 2, yO, x, yO, p);
                        cv.drawLine(x, yC, x + body / 2, yC, p);
                    }
                }
            } else {
                if (type == 2) {
                    path.reset();
                    path.moveTo(xAt(0, n), mainTop + mainH);
                    for (int i = 0; i < n; i++) {
                        path.lineTo(xAt(i, n),
                                mainTop + (float) (mainH * (1 - (v[i] - min) / span)));
                    }
                    path.lineTo(xAt(n - 1, n), mainTop + mainH);
                    path.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setShader(new LinearGradient(0, mainTop, 0, mainTop + mainH,
                            (color & 0x00FFFFFF) | 0x66000000, color & 0x00FFFFFF,
                            Shader.TileMode.CLAMP));
                    cv.drawPath(path, p);
                }
                float width = type == 1 ? 3f * d : (type == 2 ? 1.6f * d : 1.1f * d);
                line(cv, v, mainTop, mainH, min, span, color, width);
            }

            if ((ind & IND_BOLL) != 0) {
                line(cv, bollUp, mainTop, mainH, min, span, 0xAAB39DDB, d);
                line(cv, bollDn, mainTop, mainH, min, span, 0xAAB39DDB, d);
            }
            if ((ind & IND_MA20) != 0) line(cv, ma20, mainTop, mainH, min, span, 0xFFFFC857, 1.3f * d);
            if ((ind & IND_MA50) != 0) line(cv, ma50, mainTop, mainH, min, span, 0xFF7FB8E6, 1.3f * d);

            // Time labels under the main panel.
            float ty = mainTop + mainH + 14 * d;
            if (s.times.length == n && s.times[0] > 0) {
                txt.setTextAlign(Paint.Align.LEFT);
                cv.drawText(timeLabel(s.times[0], range, false), left(), ty, txt);
                txt.setTextAlign(Paint.Align.CENTER);
                cv.drawText(timeLabel(s.times[n / 2], range, false),
                        (left() + right()) / 2, ty, txt);
                txt.setTextAlign(Paint.Align.RIGHT);
                cv.drawText(timeLabel(s.times[n - 1], range, false), right(), ty, txt);
                txt.setTextAlign(Paint.Align.LEFT);
            }

            float top = mainTop + mainH + xLabels + 4 * d;

            if (volH > 0) {
                float h = volH - 16 * d;
                double vmax = 0;
                if (s.volume.length == n) for (double x : s.volume) if (x > vmax) vmax = x;
                cv.drawText(vmax > 0 ? "Volume" : "Volume (not available for this one)",
                        left(), top + 10 * d, txt);
                if (vmax > 0) {
                    float step = (right() - left()) / (float) Math.max(1, n - 1);
                    float bw = Math.max(d, step * 0.6f);
                    p.setShader(null);
                    p.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < n; i++) {
                        boolean up = i == 0 || v[i] >= v[i - 1];
                        p.setColor(((up ? MarketData.UP : MarketData.DOWN) & 0x00FFFFFF)
                                | 0xAA000000);
                        float bh = (float) (h * s.volume[i] / vmax);
                        float x = xAt(i, n);
                        cv.drawRect(x - bw / 2, top + 14 * d + h - bh, x + bw / 2,
                                top + 14 * d + h, p);
                    }
                }
                top += volH;
            }

            if (rsiH > 0 && rsi != null) {
                float h = rsiH - 20 * d;
                float t0 = top + 16 * d;
                cv.drawText("RSI 14", left(), top + 10 * d, txt);
                hline(cv, t0 + h * 0.3f);
                hline(cv, t0 + h * 0.7f);
                cv.drawText("70", right() + 6 * d, t0 + h * 0.3f + 4 * d, txt);
                cv.drawText("30", right() + 6 * d, t0 + h * 0.7f + 4 * d, txt);
                line(cv, rsi, t0, h, 0, 100, 0xFFE08AA0, 1.3f * d);
                top += rsiH;
            }

            if (macdH > 0 && macd != null) {
                float h = macdH - 20 * d;
                float t0 = top + 16 * d;
                cv.drawText("MACD 12, 26, 9", left(), top + 10 * d, txt);
                double m = 1e-9;
                for (int i = 0; i < n; i++) {
                    m = Math.max(m, Math.abs(macd[i]));
                    m = Math.max(m, Math.abs(macdSignal[i]));
                    m = Math.max(m, Math.abs(macd[i] - macdSignal[i]));
                }
                float zero = t0 + h / 2;
                float step = (right() - left()) / (float) Math.max(1, n - 1);
                float bw = Math.max(d, step * 0.6f);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < n; i++) {
                    double hist = macd[i] - macdSignal[i];
                    p.setColor(((hist >= 0 ? MarketData.UP : MarketData.DOWN) & 0x00FFFFFF)
                            | 0x99000000);
                    float y = zero - (float) (h / 2 * hist / m);
                    float x = xAt(i, n);
                    cv.drawRect(x - bw / 2, Math.min(zero, y), x + bw / 2, Math.max(zero, y),
                            p);
                }
                hline(cv, zero);
                line(cv, macd, t0, h, -m, 2 * m, 0xFF7FB8E6, 1.3f * d);
                line(cv, macdSignal, t0, h, -m, 2 * m, 0xFFFFC857, 1.3f * d);
            }

            // Crosshair where the finger is.
            if (touched >= 0 && touched < n) {
                float x = xAt(touched, n);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d);
                p.setColor(0xAAFFFFFF);
                cv.drawLine(x, mainTop, x, getHeight() - 2 * d, p);
                float y = mainTop + (float) (mainH * (1 - (v[touched] - min) / span));
                cv.drawLine(left(), y, right(), y, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.ACCENT);
                cv.drawCircle(x, y, 4.5f * d, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (s == null || s.values.length < 2) return false;
            int n = s.values.length;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // fall through
                case MotionEvent.ACTION_MOVE:
                    float w = right() - left();
                    float frac = w <= 0 ? 0 : (e.getX() - left()) / w;
                    int i = Math.round(frac * (n - 1));
                    touched = Math.max(0, Math.min(n - 1, i));
                    if (listener != null) listener.onPoint(touched);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Keep the crosshair where it was left.
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
