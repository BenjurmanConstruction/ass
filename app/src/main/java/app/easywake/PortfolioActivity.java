package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Your real positions, typed in by hand, with live-ish value and gain. */
public class PortfolioActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, MarketData.Series> quotes = new HashMap<>();
    private LinearLayout root;
    private Book book;
    private String status = "";
    private int generation;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "m_portfolio")) {
            Toast.makeText(this, "Unlock the portfolio tracker in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root == null) return;
        book = Book.load(this, "portfolio");
        build();
        refresh();
    }

    private void refresh() {
        final int gen = ++generation;
        if (book.positions.isEmpty()) return;
        status = "Loading prices...";
        build();
        final Book snapshot = book;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, MarketData.Series> got = new HashMap<>();
                for (Book.Pos p : snapshot.positions) {
                    got.put(p.sym, MarketData.fetch(new MarketData.Sym(p.sym, p.sym, null), 0));
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation || isFinishing()) return;
                        quotes.putAll(got);
                        status = "Updated " + DateFormat.getTimeFormat(PortfolioActivity.this)
                                .format(new Date()) + ". Prices can run a few minutes behind.";
                        build();
                    }
                });
            }
        }).start();
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "My portfolio"), Ui.full(this, 0));

        double value = 0;
        double cost = 0;
        double day = 0;
        boolean missing = false;
        for (Book.Pos p : book.positions) {
            MarketData.Series s = quotes.get(p.sym);
            cost += p.qty * p.cost;
            if (s != null && s.ok()) {
                value += p.qty * s.last;
                if (!Double.isNaN(s.prev)) day += p.qty * (s.last - s.prev);
            } else {
                // No price yet: count it at cost so the total isn't misleading.
                value += p.qty * p.cost;
                missing = true;
            }
        }

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.card(this, 22));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        hero.addView(Ui.heading(this, "Total value"));
        TextView total = Ui.text(this, Book.money(value), 36, Ui.TEXT);
        total.setTypeface(Ui.light());
        hero.addView(total);
        double gain = value - cost;
        double pct = cost > 0 ? gain / cost * 100 : 0;
        hero.addView(Ui.text(this, "Total gain  " + Book.signed(gain) + "  ("
                + (pct >= 0 ? "+" : "") + MarketData.fmt(pct) + "%)", 15,
                gain >= 0 ? MarketData.UP : MarketData.DOWN), Ui.full(this, 4));
        hero.addView(Ui.text(this, "Today  " + Book.signed(day), 15,
                day >= 0 ? MarketData.UP : MarketData.DOWN));
        hero.addView(Ui.text(this, "Cost basis  " + Book.money(cost), 13, Ui.SUB),
                Ui.full(this, 4));
        if (missing && quotes.size() > 0) {
            hero.addView(Ui.text(this, "Some prices didn't load; those are counted at cost.",
                    12, Ui.SUB));
        }
        root.addView(hero, Ui.full(this, 14));

        if (book.positions.isEmpty()) {
            TextView empty = Ui.text(this, "No positions yet.\nAdd what you own and what you "
                    + "paid, and this screen tracks it.", 15, Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, Ui.full(this, 30));
        }

        for (final Book.Pos p : book.positions) {
            MarketData.Series s = quotes.get(p.sym);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView sym = Ui.text(this, p.sym, 18, Ui.TEXT);
            sym.setTypeface(Ui.medium());
            left.addView(sym);
            left.addView(Ui.text(this, Book.qty(p.qty) + " shares at " + Book.money(p.cost),
                    13, Ui.SUB));
            card.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            if (s != null && s.ok()) {
                double v = p.qty * s.last;
                double g = p.qty * (s.last - p.cost);
                double gp = p.cost > 0 ? (s.last - p.cost) / p.cost * 100 : 0;
                TextView vt = Ui.text(this, Book.money(v), 17, Ui.TEXT);
                vt.setGravity(Gravity.END);
                right.addView(vt);
                TextView gt = Ui.text(this, Book.signed(g) + "  (" + (gp >= 0 ? "+" : "")
                        + MarketData.fmt(gp) + "%)", 13,
                        g >= 0 ? MarketData.UP : MarketData.DOWN);
                gt.setGravity(Gravity.END);
                right.addView(gt);
                TextView pt = Ui.text(this, "Now " + Book.money(s.last), 12, Ui.SUB);
                pt.setGravity(Gravity.END);
                right.addView(pt);
            } else {
                right.addView(Ui.text(this, quotes.containsKey(p.sym) ? "No price" : "...",
                        14, Ui.SUB));
            }
            card.addView(right);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    options(p);
                }
            });
            root.addView(card, Ui.full(this, 10));
        }

        Button add = Ui.button(this, "+  Add a position", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(null);
            }
        });
        root.addView(add, Ui.full(this, 16));

        if (!book.positions.isEmpty()) {
            Button again = Ui.button(this, "Refresh prices", false);
            again.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refresh();
                }
            });
            root.addView(again, Ui.full(this, 10));
        }

        root.addView(Ui.text(this, (status.isEmpty() ? "" : status + " ")
                + "You type these in yourself; the app can't see or connect to a brokerage "
                + "account, and it stays on this phone. For information only, not "
                + "investment advice.", 12, Ui.SUB), Ui.full(this, 14));
    }

    private void options(final Book.Pos p) {
        String[] items = {"Open chart", "Edit shares or cost", "Remove from portfolio"};
        new AlertDialog.Builder(this)
                .setTitle(p.sym)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            startActivity(new Intent(PortfolioActivity.this,
                                    ChartActivity.class)
                                    .putExtra("symbol", p.sym)
                                    .putExtra("name", p.sym));
                        } else if (which == 1) {
                            edit(p);
                        } else {
                            book.positions.remove(p);
                            book.save(PortfolioActivity.this);
                            build();
                        }
                    }
                })
                .show();
    }

    private EditText field(LinearLayout box, String hint, int type, String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setHint(hint);
        if (value != null) e.setText(value);
        box.addView(e, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    /** Add a new position, or edit an existing one when {@code existing} is given. */
    private void edit(final Book.Pos existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        int decimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        final EditText sym = field(box, "Ticker, like AAPL",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
                existing == null ? null : existing.sym);
        if (existing != null) sym.setEnabled(false);
        final EditText shares = field(box, "Number of shares", decimal,
                existing == null ? null : Book.qty(existing.qty));
        final EditText cost = field(box, "Average cost per share, in dollars", decimal,
                existing == null ? null : String.valueOf(existing.cost));

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add a position" : "Edit " + existing.sym)
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String t = MarketData.clean(sym.getText().toString());
                        double q = parse(shares.getText().toString());
                        double c = parse(cost.getText().toString());
                        if (t == null || q <= 0 || c < 0) {
                            Toast.makeText(PortfolioActivity.this,
                                    "Check the ticker, shares and cost.", Toast.LENGTH_LONG)
                                    .show();
                            return;
                        }
                        if (existing != null) {
                            existing.qty = q;
                            existing.cost = c;
                        } else if (book.find(t) != null) {
                            // Already held: blend the new shares into the average cost.
                            book.add(t, q, c);
                        } else {
                            book.add(t, q, c);
                        }
                        book.save(PortfolioActivity.this);
                        build();
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(",", "").replace("$", ""));
        } catch (Exception e) {
            return -1;
        }
    }
}
