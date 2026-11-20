package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How the paper account is doing: its value over time against the same money in the
 * S&P 500, a scoreboard of trades, and each holding. Practice only; information, not advice.
 */
public class PerformanceActivity extends Activity {
    /** The S&P 500 fund the account is measured against. */
    private static final String BENCH = "SPY";
    private static final String[] RANGES = {"1W", "1M", "3M", "All"};
    private static final long[] RANGE_MS = {7L * 86400000, 31L * 86400000, 92L * 86400000, 0};
    private static final int[] SLICE = {0xFF4FC3F7, 0xFFE6B869, 0xFF81C784, 0xFFBA68C8,
            0xFFFF8A65, 0xFF4DB6AC, 0xFFF06292, 0xFFAED581, 0xFF9575CD, 0xFFFFD54F};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private Book book;
    private Map<String, PaperPerf.Prices> prices;
    private PaperPerf.Prices bench;
    private PaperPerf.History history;
    private String error;
    private int range = 3;
    private int generation;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "m_paper")) {
            Toast.makeText(this, "Unlock paper trading in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root == null) return;
        book = Book.load(this, "paper");
        if (!book.eventsReady) {
            book.readOldLog(this);
            book.save(this);
        }
        load();
    }

    private Map<String, Double> holdings() {
        Map<String, Double> h = new HashMap<>();
        for (Book.Pos p : book.positions) h.put(p.sym, p.qty);
        return h;
    }

    private void load() {
        final int gen = ++generation;
        prices = null;
        error = null;
        build();
        final Map<String, Double> held = holdings();
        final List<PaperPerf.Event> events = new ArrayList<>(book.events);
        final Set<String> syms = PaperPerf.symbols(events, held);
        List<PaperPerf.Event> since = PaperPerf.sinceStart(events);
        long first = since.isEmpty() ? System.currentTimeMillis() : since.get(0).ms;
        // A year of daily closes covers most accounts; older ones get five years, weekly.
        final int r = System.currentTimeMillis() - first > 355L * 86400000 ? 5 : 4;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, PaperPerf.Prices> got = new HashMap<>();
                for (String s : syms) got.put(s, fetch(s, r));
                final PaperPerf.Prices spx = fetch(BENCH, r);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation || isFinishing()) return;
                        if (spx.closes.length < 2) {
                            error = "Couldn't load prices. Check your internet and try again.";
                            build();
                            return;
                        }
                        prices = got;
                        bench = spx;
                        history = PaperPerf.rebuild(book.cash, book.deposited, held, events,
                                got, spx, System.currentTimeMillis());
                        build();
                    }
                });
            }
        }).start();
    }

    private static PaperPerf.Prices fetch(String sym, int r) {
        PaperPerf.Prices p = new PaperPerf.Prices();
        MarketData.Sym s = new MarketData.Sym(sym, sym, null);
        MarketData.Series hist = MarketData.fetch(s, r);
        if (hist.ok()) {
            p.times = hist.times;
            p.closes = hist.values;
            p.last = hist.last;
        }
        MarketData.Series day = MarketData.fetch(s, 0);
        if (day.ok()) {
            p.last = day.last;
            p.prevClose = day.prev;
        }
        return p;
    }

    // ------------------------------------------------------------------ screen

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Performance"), Ui.full(this, 0));
        if (error != null) {
            root.addView(Ui.text(this, error, 15, Ui.SUB), Ui.full(this, 20));
            Button again = Ui.button(this, "Try again", true);
            again.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    load();
                }
            });
            root.addView(again, Ui.full(this, 12));
            return;
        }
        if (prices == null) {
            TextView t = Ui.text(this, "Rebuilding your account's history from your trades "
                    + "and real closing prices...", 15, Ui.SUB);
            t.setGravity(Gravity.CENTER);
            root.addView(t, Ui.full(this, 40));
            return;
        }
        if (history.value.length < 1) {
            root.addView(Ui.text(this, "Couldn't work out the account's value.", 15,
                    Ui.SUB), Ui.full(this, 20));
            return;
        }

        // Numbers for now.
        double held = 0;
        double today = 0;
        double unrealized = 0;
        double yesterdayHeld = 0;
        for (Book.Pos p : book.positions) {
            PaperPerf.Prices px = prices.get(p.sym);
            double last = px != null && !Double.isNaN(px.last) ? px.last : p.cost;
            held += p.qty * last;
            unrealized += p.qty * (last - p.cost);
            if (px != null && !Double.isNaN(px.prevClose)) {
                today += p.qty * (last - px.prevClose);
                yesterdayHeld += p.qty * px.prevClose;
            }
        }
        double total = book.cash + held;
        double gain = total - book.deposited;
        double gainPct = book.deposited > 0 ? gain / book.deposited * 100 : 0;
        int n = history.value.length;
        double benchNow = history.bench[n - 1];
        double benchDep = history.deposited[n - 1];
        double benchGain = benchNow - benchDep;
        double benchPct = benchDep > 0 ? benchGain / benchDep * 100 : 0;
        double ahead = history.value[n - 1] - benchNow;
        PaperPerf.Stats st = PaperPerf.stats(book.events, history);

        // ---- hero
        LinearLayout hero = card();
        hero.addView(Ui.heading(this, "Account value (pretend money)"));
        TextView big = Ui.text(this, Book.money(total), 36, Ui.TEXT);
        big.setTypeface(Ui.light());
        hero.addView(big);
        double todayPct = yesterdayHeld + book.cash > 0 ? today / (yesterdayHeld + book.cash)
                * 100 : 0;
        hero.addView(Ui.text(this, "Today  " + Book.signed(today) + "  (" + pct(todayPct) + ")",
                15, today >= 0 ? MarketData.UP : MarketData.DOWN), Ui.full(this, 4));
        hero.addView(Ui.text(this, "All time  " + Book.signed(gain) + "  (" + pct(gainPct)
                + ") on " + Book.money(book.deposited) + " put in", 15,
                gain >= 0 ? MarketData.UP : MarketData.DOWN), Ui.full(this, 2));
        TextView vs = Ui.text(this, (ahead >= 0 ? "Beating" : "Trailing") + " the S&P 500 by "
                + Book.money(Math.abs(ahead)), 17, ahead >= 0 ? MarketData.UP : MarketData.DOWN);
        vs.setTypeface(Ui.medium());
        hero.addView(vs, Ui.full(this, 10));
        hero.addView(Ui.text(this, "The same money in the S&P 500 (SPY), put in on the same "
                + "days, would be " + Book.money(benchNow) + "  (" + pct(benchPct) + ").", 13,
                Ui.SUB), Ui.full(this, 2));
        root.addView(hero, Ui.full(this, 12));

        // ---- chart
        LinearLayout chartCard = card();
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < RANGES.length; i++) {
            final int k = i;
            Button bt = Ui.button(this, RANGES[i], i == range);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    range = k;
                    build();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            pills.addView(bt, lp);
        }
        chartCard.addView(pills);
        int from = 0;
        if (RANGE_MS[range] > 0) {
            long cut = history.times[n - 1] - RANGE_MS[range];
            while (from < n - 2 && history.times[from] < cut) from++;
        }
        double v0 = history.value[from];
        double b0 = history.bench[from];
        double addedDuring = history.deposited[n - 1] - history.deposited[from];
        double myP = v0 > 0 ? (history.value[n - 1] - v0 - addedDuring) / v0 * 100 : 0;
        double spP = b0 > 0 ? (history.bench[n - 1] - b0 - addedDuring) / b0 * 100 : 0;
        chartCard.addView(Ui.text(this, "This period:  You " + pct(myP) + "   •   S&P 500 "
                + pct(spP), 14, myP >= spP ? MarketData.UP : MarketData.DOWN), Ui.full(this, 10));
        final Chart chart = new Chart(this, history, from);
        chartCard.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 230)));
        TextView legend = Ui.text(this, "━ Your account     ┅ Same money in the S&P 500\n"
                + "Drag across the chart to see any day.", 12, Ui.SUB);
        chartCard.addView(legend, Ui.full(this, 6));
        root.addView(chartCard, Ui.full(this, 12));

        // ---- scoreboard
        root.addView(Ui.heading(this, "Scoreboard"), Ui.full(this, 14));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        String winRate = st.sells > 0 ? Math.round(100.0 * st.wins / st.sells) + "%" : "—";
        tiles(grid,
                new String[]{"Realized", Book.signed(st.realized), "from " + st.sells + " sale"
                        + (st.sells == 1 ? "" : "s")},
                new String[]{"Unrealized", Book.signed(unrealized), "on what you hold now"});
        tiles(grid,
                new String[]{"Win rate", winRate, st.wins + " of " + st.sells + " sales made "
                        + "money"},
                new String[]{"Worst dip", st.maxDip > 0 ? "-" + MarketData.fmt(st.maxDip) + "%"
                        : "0%", "biggest drop from a high"});
        tiles(grid,
                new String[]{"Average win", st.wins > 0 ? Book.signed(st.avgWin) : "—",
                        "per winning sale"},
                new String[]{"Average loss", st.sells - st.wins > 0 && st.avgLoss != 0
                        ? Book.signed(st.avgLoss) : "—", "per losing sale"});
        tiles(grid,
                new String[]{"Best sale", Double.isNaN(st.best) ? "—" : Book.signed(st.best),
                        st.bestSym},
                new String[]{"Worst sale", Double.isNaN(st.worst) ? "—" : Book.signed(st.worst),
                        st.worstSym});
        tiles(grid,
                new String[]{"Trades", String.valueOf(st.buys + st.sells), st.buys + " buys, "
                        + st.sells + " sells"},
                new String[]{"Cash", Book.money(book.cash), total > 0 ? Math.round(book.cash
                        / total * 100) + "% of the account" : ""});
        root.addView(grid, Ui.full(this, 6));

        // ---- holdings
        root.addView(Ui.heading(this, "Holdings"), Ui.full(this, 18));
        List<Book.Pos> sorted = new ArrayList<>(book.positions);
        final Map<String, PaperPerf.Prices> px = prices;
        Collections.sort(sorted, new Comparator<Book.Pos>() {
            @Override
            public int compare(Book.Pos a, Book.Pos b) {
                return Double.compare(value(b, px), value(a, px));
            }
        });
        if (total > 0) root.addView(new Allocation(this, sorted, px, book.cash, total),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 22)));
        int idx = 0;
        StringBuilder key = new StringBuilder();
        for (Book.Pos p : sorted) {
            key.append(idx > 0 ? "   " : "").append("● ").append(p.sym);
            idx++;
        }
        if (book.cash > 0) key.append(idx > 0 ? "   " : "").append("○ Cash");
        if (key.length() > 0) root.addView(Ui.text(this, key.toString(), 12, Ui.SUB),
                Ui.full(this, 4));
        for (final Book.Pos p : sorted) {
            PaperPerf.Prices pp = prices.get(p.sym);
            double last = pp != null && !Double.isNaN(pp.last) ? pp.last : p.cost;
            double val = p.qty * last;
            double g = p.qty * (last - p.cost);
            double gp = p.cost > 0 ? (last - p.cost) / p.cost * 100 : 0;
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView sym = Ui.text(this, p.sym, 18, Ui.TEXT);
            sym.setTypeface(Ui.medium());
            left.addView(sym);
            left.addView(Ui.text(this, Book.qty(p.qty) + " sh  •  avg " + Book.money(p.cost)
                    + "\n" + (total > 0 ? Math.round(val / total * 100) : 0) + "% of account",
                    12, Ui.SUB));
            row.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            TextView vt = Ui.text(this, Book.money(val), 17, Ui.TEXT);
            vt.setGravity(Gravity.END);
            right.addView(vt);
            TextView gt = Ui.text(this, Book.signed(g) + " (" + pct(gp) + ")", 13,
                    g >= 0 ? MarketData.UP : MarketData.DOWN);
            gt.setGravity(Gravity.END);
            right.addView(gt);
            if (pp != null && !Double.isNaN(pp.prevClose) && pp.prevClose > 0) {
                double d = p.qty * (last - pp.prevClose);
                double dp = (last - pp.prevClose) / pp.prevClose * 100;
                TextView dt = Ui.text(this, "today " + Book.signed(d) + " (" + pct(dp) + ")", 12,
                        d >= 0 ? MarketData.UP : MarketData.DOWN);
                dt.setGravity(Gravity.END);
                right.addView(dt);
            }
            row.addView(right);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(PerformanceActivity.this, ChartActivity.class)
                            .putExtra("symbol", p.sym).putExtra("name", p.sym));
                }
            });
            root.addView(row, Ui.full(this, 8));
        }
        if (sorted.isEmpty()) {
            root.addView(Ui.text(this, "Nothing held right now: it's all cash.", 14, Ui.SUB),
                    Ui.full(this, 8));
        }

        String note = "How this works: your account's past is rebuilt from your trade log and "
                + "each day's closing prices, so past days are valued at the close and today "
                + "uses the latest prices.";
        if (!history.complete) {
            note += " Your trade log only reaches back to "
                    + new SimpleDateFormat("MMM d, yyyy", Locale.US).format(
                    new Date(history.times[0])) + ", so the chart starts there.";
        }
        note += " Practice only: information, not financial advice.";
        root.addView(Ui.text(this, note, 12, Ui.SUB), Ui.full(this, 16));
        Button refresh = Ui.button(this, "Refresh prices", false);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        root.addView(refresh, Ui.full(this, 10));
    }

    private static double value(Book.Pos p, Map<String, PaperPerf.Prices> px) {
        PaperPerf.Prices pp = px.get(p.sym);
        double last = pp != null && !Double.isNaN(pp.last) ? pp.last : p.cost;
        return p.qty * last;
    }

    private static String pct(double v) {
        return (v >= 0 ? "+" : "") + MarketData.fmt(v) + "%";
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.card(this, 18));
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        return c;
    }

    /** Two scoreboard tiles side by side. Each is {label, big number, small note}. */
    private void tiles(LinearLayout grid, String[] a, String[] b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tile(a), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = Ui.dp(this, 8);
        row.addView(tile(b), lp);
        grid.addView(row, Ui.full(this, 8));
    }

    private View tile(String[] t) {
        LinearLayout c = card();
        c.addView(Ui.text(this, t[0], 12, Ui.SUB));
        int color = Ui.TEXT;
        if (t[1].startsWith("+")) color = MarketData.UP;
        else if (t[1].startsWith("-")) color = MarketData.DOWN;
        TextView big = Ui.text(this, t[1], 20, color);
        big.setTypeface(Ui.medium());
        c.addView(big);
        if (t[2] != null && !t[2].isEmpty()) c.addView(Ui.text(this, t[2], 11, Ui.SUB));
        return c;
    }

    // ------------------------------------------------------------------ views

    /** The account line against the S&P line, with a finger to read any day. */
    static final class Chart extends View {
        private final PaperPerf.History h;
        private final int from;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final SimpleDateFormat day = new SimpleDateFormat("MMM d", Locale.US);
        private float touchX = -1;

        Chart(Context c, PaperPerf.History h, int from) {
            super(c);
            this.h = h;
            this.from = from;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction()
                    == MotionEvent.ACTION_CANCEL) {
                touchX = -1;
            } else {
                touchX = e.getX();
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas cv) {
            int n = h.value.length - from;
            if (n < 1) return;
            float w = getWidth();
            float ht = getHeight();
            float d = getResources().getDisplayMetrics().density;
            float left = 4 * d;
            float right = w - 54 * d;
            float top = 10 * d;
            float bottom = ht - 22 * d;
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            for (int i = from; i < h.value.length; i++) {
                lo = Math.min(lo, Math.min(h.value[i], h.bench[i]));
                hi = Math.max(hi, Math.max(h.value[i], h.bench[i]));
            }
            if (hi - lo < 1) {
                hi += 1;
                lo -= 1;
            }
            double padY = (hi - lo) * 0.08;
            hi += padY;
            lo -= padY;

            // Grid and price labels.
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(11 * d);
            paint.setTextAlign(Paint.Align.LEFT);
            for (int g = 0; g <= 3; g++) {
                double v = lo + (hi - lo) * g / 3;
                float y = (float) (bottom - (v - lo) / (hi - lo) * (bottom - top));
                paint.setColor(0x22FFFFFF);
                cv.drawRect(left, y, right, y + d * 0.7f, paint);
                paint.setColor(Ui.SUB);
                cv.drawText(short$(v), right + 6 * d, y + 4 * d, paint);
            }
            paint.setColor(Ui.SUB);
            cv.drawText(day.format(new Date(h.times[from])), left, ht - 6 * d, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            cv.drawText("now", right, ht - 6 * d, paint);

            // The S&P line, dashed.
            line(cv, h.bench, left, right, top, bottom, lo, hi, n);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * d);
            paint.setColor(0xAAA7ADBB);
            paint.setPathEffect(new DashPathEffect(new float[]{6 * d, 5 * d}, 0));
            cv.drawPath(path, paint);
            paint.setPathEffect(null);

            // The account line, with a soft fill under it.
            boolean up = h.value[h.value.length - 1] >= h.value[from];
            int color = up ? MarketData.UP : MarketData.DOWN;
            line(cv, h.value, left, right, top, bottom, lo, hi, n);
            Path fill = new Path(path);
            fill.lineTo(right, bottom);
            fill.lineTo(left, bottom);
            fill.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, top, 0, bottom, (color & 0x00FFFFFF)
                    | 0x55000000, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawPath(fill, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f * d);
            paint.setColor(color);
            cv.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);

            // Reading a day with a finger.
            if (touchX >= 0 && n >= 1) {
                float f = n == 1 ? 0 : (touchX - left) / (right - left);
                int i = from + Math.max(0, Math.min(n - 1, Math.round(f * (n - 1))));
                float x = n == 1 ? right : left + (right - left) * (i - from) / (float) (n - 1);
                paint.setColor(0x88FFFFFF);
                cv.drawRect(x - d * 0.6f, top, x + d * 0.6f, bottom, paint);
                float yv = (float) (bottom - (h.value[i] - lo) / (hi - lo) * (bottom - top));
                paint.setColor(color);
                cv.drawCircle(x, yv, 4.5f * d, paint);
                String a = (i == h.value.length - 1 ? "Now" : day.format(new Date(h.times[i])))
                        + ":  You " + Book.money(h.value[i]);
                String b = "S&P " + Book.money(h.bench[i]) + "   (" + (h.value[i] >= h.bench[i]
                        ? "+" : "-") + Book.money(Math.abs(h.value[i] - h.bench[i])).replace("-",
                        "") + ")";
                paint.setTextSize(12 * d);
                float tw = Math.max(paint.measureText(a), paint.measureText(b)) + 16 * d;
                float bx = Math.max(0, Math.min(w - tw, x - tw / 2));
                paint.setColor(0xEE10131C);
                cv.drawRoundRect(bx, top, bx + tw, top + 40 * d, 8 * d, 8 * d, paint);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor(Ui.TEXT);
                cv.drawText(a, bx + 8 * d, top + 16 * d, paint);
                paint.setColor(Ui.SUB);
                cv.drawText(b, bx + 8 * d, top + 33 * d, paint);
            }
        }

        private void line(Canvas cv, double[] v, float left, float right, float top,
                          float bottom, double lo, double hi, int n) {
            path.reset();
            for (int i = 0; i < n; i++) {
                float x = n == 1 ? right : left + (right - left) * i / (float) (n - 1);
                float y = (float) (bottom - (v[from + i] - lo) / (hi - lo) * (bottom - top));
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
        }

        private static String short$(double v) {
            if (Math.abs(v) >= 1000000) return "$" + MarketData.fmt(v / 1000000) + "M";
            if (Math.abs(v) >= 10000) return "$" + Math.round(v / 1000) + "k";
            return "$" + Math.round(v);
        }
    }

    /** One bar split by how much of the account each holding (and cash) makes up. */
    static final class Allocation extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final double[] parts;

        Allocation(Context c, List<Book.Pos> sorted, Map<String, PaperPerf.Prices> px,
                   double cash, double total) {
            super(c);
            parts = new double[sorted.size() + 1];
            for (int i = 0; i < sorted.size(); i++) parts[i] = value(sorted.get(i), px) / total;
            parts[sorted.size()] = cash / total;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float r = h / 2;
            float x = 0;
            cv.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(0, 0, w, h, r, r, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
            for (int i = 0; i < parts.length; i++) {
                float pw = (float) (parts[i] * w);
                boolean cashPart = i == parts.length - 1;
                paint.setColor(cashPart ? 0xFF3A4052 : SLICE[i % SLICE.length]);
                cv.drawRect(x, 0, x + pw, h, paint);
                x += pw;
            }
            cv.restore();
        }
    }
}
