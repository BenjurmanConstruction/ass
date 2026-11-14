package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Indexes, other markets and your own watchlist, each with a line chart. */
public class MarketActivity extends Activity {

    /** The views for one symbol. */
    private static final class Card {
        MarketData.Sym sym;
        TextView value;
        TextView change;
        TextView range;
        ChartView chart;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final List<Card> cards = new ArrayList<>();
    private final Button[] rangeBtns = new Button[MarketData.RANGE_LABELS.length];
    private LinearLayout body;
    private ScrollView scroll;
    private TextView footer;
    private int range;
    private int unlockStamp = -1;
    private int generation;
    private int pending;
    private int okCount;
    private int dailyCount;
    private String firstError = "";
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
        if (range < 0 || range >= MarketData.RANGE_LABELS.length) range = 0;

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Market"), Ui.full(this, 0));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout ranges = new LinearLayout(this);
        ranges.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < rangeBtns.length; i++) {
            final int idx = i;
            Button rb = Ui.button(this, MarketData.RANGE_LABELS[i], false);
            rb.setTextSize(14);
            rb.setMinHeight(Ui.dp(this, 40));
            rb.setMinimumHeight(Ui.dp(this, 40));
            rb.setMinWidth(0);
            rb.setMinimumWidth(0);
            rb.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
            rb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    range = idx;
                    Prefs.putInt(MarketActivity.this, "marketRange", idx);
                    paintRanges();
                    load();
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
            rp.rightMargin = Ui.dp(this, 8);
            ranges.addView(rb, rp);
            rangeBtns[i] = rb;
        }
        hs.addView(ranges);
        root.addView(hs, Ui.full(this, 14));

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, Ui.full(this, 0));

        footer = Ui.text(this, "", 12, Ui.SUB);
        root.addView(footer, Ui.full(this, 16));

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
        rebuild();
    }

    private int unlockCount() {
        return MarketData.list(this, "treeOwned", "").size();
    }

    /** Rebuilds the list of cards from the saved choices. */
    private void rebuild() {
        body.removeAllViews();
        cards.clear();

        body.addView(Ui.heading(this, "Tools"), Ui.full(this, 18));
        body.addView(tool("My portfolio", "m_portfolio"), Ui.full(this, 10));
        body.addView(tool("Paper trading", "m_paper"), Ui.full(this, 10));
        body.addView(tool("Market news", "m_news"), Ui.full(this, 10));

        body.addView(Ui.heading(this, "Indexes"), Ui.full(this, 24));
        for (MarketData.Sym s : MarketData.INDEXES) addCard(s, true, false);

        body.addView(Ui.heading(this, "More markets"), Ui.full(this, 24));
        List<String> extras = MarketData.list(this, "marketExtras", MarketData.DEFAULT_EXTRAS);
        for (MarketData.Sym s : MarketData.EXTRAS) {
            if (extras.contains(s.symbol)) addCard(s, false, false);
        }
        Button choose = Ui.button(this, "Choose markets", false);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseExtras();
            }
        });
        body.addView(choose, Ui.full(this, 10));

        body.addView(Ui.heading(this, "My watchlist"), Ui.full(this, 24));
        List<String> watch = MarketData.list(this, "marketWatch", "");
        if (watch.isEmpty()) {
            body.addView(Ui.text(this, "Add any stock, ETF or crypto ticker, like AAPL, SPY "
                    + "or ETH-USD.", 13, Ui.SUB), Ui.full(this, 8));
        }
        for (String t : watch) addCard(new MarketData.Sym(t, t, null), false, true);
        Button add = Ui.button(this, "+  Add or look up a ticker", false);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addTicker();
            }
        });
        body.addView(add, Ui.full(this, 10));
        if (!watch.isEmpty()) {
            body.addView(Ui.text(this, "Tap any card for the full chart. Hold a watchlist "
                            + "card to remove it.", 12, Ui.SUB),
                    Ui.full(this, 6));
        }

        body.addView(Ui.heading(this, "Show the three indexes"), Ui.full(this, 24));
        body.addView(toggle("On the home screen", "marketHome"), Ui.full(this, 10));
        body.addView(toggle("On the alarm screen", "marketRing"), Ui.full(this, 10));
    }

    /** A row that opens an unlockable tool, or points to the skill tree if it's locked. */
    private View tool(String title, final String nodeId) {
        final boolean owned = Tree.owned(this, nodeId);
        Ui.Row row = Ui.row(this, title, owned ? "Open" : "Locked");
        row.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Tree.owned(MarketActivity.this, nodeId)) {
                    startActivity(new android.content.Intent(MarketActivity.this,
                            Tree.screen(nodeId)));
                } else {
                    Toast.makeText(MarketActivity.this, "Unlock this on the Market branch "
                            + "of the skill tree.", Toast.LENGTH_LONG).show();
                    startActivity(new android.content.Intent(MarketActivity.this,
                            TreeActivity.class));
                }
            }
        });
        return row.view;
    }

    private View toggle(String title, final String key) {
        final Ui.Row row = Ui.row(this, title, Prefs.getInt(this, key, 1) == 1 ? "On" : "Off");
        row.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int now = Prefs.getInt(MarketActivity.this, key, 1) == 1 ? 0 : 1;
                Prefs.putInt(MarketActivity.this, key, now);
                row.value.setText(now == 1 ? "On" : "Off");
            }
        });
        return row.view;
    }

    private void addCard(final MarketData.Sym sym, boolean big, boolean removable) {
        Card c = new Card();
        c.sym = sym;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, 22));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        TextView name = Ui.text(this, sym.name, 16, Ui.SUB);
        name.setTypeface(Ui.medium());
        head.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.change = Ui.text(this, "", 14, Ui.SUB);
        head.addView(c.change);
        card.addView(head);

        c.value = Ui.text(this, "Loading...", big ? 30 : 24, Ui.TEXT);
        c.value.setTypeface(Ui.light());
        card.addView(c.value);

        c.chart = new ChartView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, big ? 120 : 80));
        cp.topMargin = Ui.dp(this, 8);
        card.addView(c.chart, cp);

        c.range = Ui.text(this, "", 12, Ui.SUB);
        card.addView(c.range, Ui.full(this, 6));

        // Tap a card to open that chart on its own screen.
        card.setBackground(Ui.ripple(this, Ui.card(this, 22), 22));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(MarketActivity.this,
                        ChartActivity.class)
                        .putExtra("symbol", sym.symbol)
                        .putExtra("name", sym.name)
                        .putExtra("fred", sym.fred)
                        .putExtra("range", range));
            }
        });
        if (removable) {
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmRemove(sym.symbol);
                    return true;
                }
            });
        }
        body.addView(card, Ui.full(this, 12));
        cards.add(c);
    }

    private void chooseExtras() {
        final List<String> current = MarketData.list(this, "marketExtras",
                MarketData.DEFAULT_EXTRAS);
        final String[] names = new String[MarketData.EXTRAS.length];
        final boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = MarketData.EXTRAS[i].name;
            checked[i] = current.contains(MarketData.EXTRAS[i].symbol);
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose markets")
                .setMultiChoiceItems(names, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which, boolean on) {
                                checked[which] = on;
                            }
                        })
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        List<String> out = new ArrayList<>();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) out.add(MarketData.EXTRAS[i].symbol);
                        }
                        MarketData.saveList(MarketActivity.this, "marketExtras", out);
                        rebuild();
                        load();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addTicker() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("AAPL, SPY, ETH-USD ...");
        LinearLayout wrap = new LinearLayout(this);
        int pad = Ui.dp(this, 20);
        wrap.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Add a ticker")
                .setView(wrap)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String t = MarketData.clean(input.getText().toString());
                        if (t == null) {
                            Toast.makeText(MarketActivity.this,
                                    "That doesn't look like a ticker symbol.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        List<String> watch = MarketData.list(MarketActivity.this,
                                "marketWatch", "");
                        if (!watch.contains(t)) watch.add(t);
                        MarketData.saveList(MarketActivity.this, "marketWatch", watch);
                        rebuild();
                        load();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove(final String symbol) {
        new AlertDialog.Builder(this)
                .setMessage("Remove " + symbol + " from your watchlist?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        List<String> watch = MarketData.list(MarketActivity.this,
                                "marketWatch", "");
                        watch.remove(symbol);
                        MarketData.saveList(MarketActivity.this, "marketWatch", watch);
                        rebuild();
                        load();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        // Coming back from another screen: pick up unlocks and line color changes.
        if (body != null && unlockStamp != unlockCount()) {
            unlockStamp = unlockCount();
            rebuild();
        }
        handler.removeCallbacks(autoRefresh);
        handler.post(autoRefresh);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    private void paintRanges() {
        for (int i = 0; i < rangeBtns.length; i++) {
            boolean on = i == range;
            rangeBtns[i].setBackground(on ? Ui.gold(this, 20)
                    : Ui.ripple(this, Ui.card(this, 20), 20));
            rangeBtns[i].setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
        }
    }

    private void load() {
        final int gen = ++generation;
        final int r = range;
        pending = cards.size();
        okCount = 0;
        dailyCount = 0;
        firstError = "";
        footer.setText("Loading...");
        for (final Card c : new ArrayList<>(cards)) {
            try {
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        final MarketData.Series s = MarketData.fetch(c.sym, r);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != generation) return;
                                show(c, s, r);
                                pending--;
                                if (pending <= 0) paintFooter(r);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                // Screen is closing.
            }
        }
    }

    private void show(Card c, MarketData.Series s, int r) {
        if (!s.ok()) {
            if (firstError.isEmpty()) firstError = s.error;
            c.value.setText("No data");
            c.change.setText("");
            c.range.setText("");
            c.chart.setData(new double[0], Double.NaN, Ui.SUB);
            return;
        }
        okCount++;
        if (!s.live) dailyCount++;
        c.value.setText(MarketData.fmt(s.last));
        int color = Ui.SUB;
        if (!Double.isNaN(s.prev) && s.prev != 0) {
            color = s.change() >= 0 ? MarketData.UP : MarketData.DOWN;
        }
        c.change.setText(MarketData.changeText(s));
        c.change.setTextColor(color);
        c.range.setText("Low " + MarketData.fmt(s.low) + "   •   High "
                + MarketData.fmt(s.high));
        c.chart.setData(s.values, r == 0 && s.live ? s.prev : Double.NaN,
                MarketData.lineColor(this, color));
    }

    private void paintFooter(int r) {
        StringBuilder sb = new StringBuilder();
        if (okCount == 0) {
            sb.append(MarketData.netOff
                    ? "Internet is switched off for this app. Turn it on in Menu, Settings "
                    + "to load market data."
                    : "Couldn't load market data. Check your internet connection and tap "
                    + "Refresh.");
            if (!firstError.isEmpty()) sb.append(" (Details: ").append(firstError).append(")");
        } else {
            sb.append("Updated ").append(DateFormat.getTimeFormat(this).format(new Date()))
                    .append(". Data from Yahoo Finance; it can run a few minutes behind. "
                            + "Change is versus the ")
                    .append(r == 0 ? "previous close. " : "start of the period. ");
            if (dailyCount > 0) {
                sb.append("Live data was unavailable for some indexes, so those show daily "
                        + "closing values from the St. Louis Fed. ");
            }
            sb.append("For information only, not investment advice.");
        }
        footer.setText(sb.toString());
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
            line.setStrokeWidth(Ui.dp(c, 1.5f));
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
