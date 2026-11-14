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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paper trading: buy and sell with pretend cash at the app's quoted prices. No real money
 * and no real orders are involved.
 */
public class PaperActivity extends Activity {
    static final double STARTER_CASH = 10000;
    /** Paper dollars bought by one Knowledge Point. */
    static final int DOLLARS_PER_KP = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, MarketData.Series> quotes = new HashMap<>();
    private LinearLayout root;
    private Book book;
    private String status = "";
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
        book = Book.load(this, "paper");
        if (book.deposited <= 0 && book.positions.isEmpty() && book.cash <= 0) {
            // First visit: a starting stake so there's something to trade with.
            book.cash = STARTER_CASH;
            book.deposited = STARTER_CASH;
            book.history.add(stamp() + "  Account opened with " + Book.money(STARTER_CASH));
            book.save(this);
        }
        build();
        refresh();
    }

    private String stamp() {
        Date now = new Date();
        return DateFormat.getDateFormat(this).format(now) + " "
                + DateFormat.getTimeFormat(this).format(now);
    }

    private void refresh() {
        final int gen = ++generation;
        if (book.positions.isEmpty()) return;
        status = "Loading prices...";
        build();
        final List<String> syms = new ArrayList<>();
        for (Book.Pos p : book.positions) syms.add(p.sym);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<String, MarketData.Series> got = new HashMap<>();
                for (String s : syms) {
                    got.put(s, MarketData.fetch(new MarketData.Sym(s, s, null), 0));
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation || isFinishing()) return;
                        quotes.putAll(got);
                        status = "Updated " + DateFormat.getTimeFormat(PaperActivity.this)
                                .format(new Date()) + ".";
                        build();
                    }
                });
            }
        }).start();
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Paper trading"), Ui.full(this, 0));

        double held = 0;
        for (Book.Pos p : book.positions) {
            MarketData.Series s = quotes.get(p.sym);
            held += p.qty * (s != null && s.ok() ? s.last : p.cost);
        }
        double total = book.cash + held;
        double gain = total - book.deposited;
        double pct = book.deposited > 0 ? gain / book.deposited * 100 : 0;

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.card(this, 22));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        hero.addView(Ui.heading(this, "Account value (pretend money)"));
        TextView tv = Ui.text(this, Book.money(total), 36, Ui.TEXT);
        tv.setTypeface(Ui.light());
        hero.addView(tv);
        hero.addView(Ui.text(this, "Overall  " + Book.signed(gain) + "  (" + (pct >= 0 ? "+"
                : "") + MarketData.fmt(pct) + "%)", 15,
                gain >= 0 ? MarketData.UP : MarketData.DOWN), Ui.full(this, 4));
        hero.addView(Ui.text(this, "Cash to trade  " + Book.money(book.cash) + "   •   "
                + "In stocks  " + Book.money(held), 13, Ui.SUB), Ui.full(this, 4));
        hero.addView(Ui.text(this, "Put in so far  " + Book.money(book.deposited), 13,
                Ui.SUB));

        Button trade = Ui.button(this, "Trade", true);
        trade.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askTicker(null);
            }
        });
        hero.addView(trade, Ui.full(this, 14));
        root.addView(hero, Ui.full(this, 14));

        if (book.positions.isEmpty()) {
            TextView empty = Ui.text(this, "No positions. Tap Trade, enter a ticker, and buy "
                    + "your first shares.", 15, Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, Ui.full(this, 26));
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
            left.addView(Ui.text(this, Book.qty(p.qty) + " shares, bought at "
                    + Book.money(p.cost), 13, Ui.SUB));
            card.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            if (s != null && s.ok()) {
                double g = p.qty * (s.last - p.cost);
                double gp = p.cost > 0 ? (s.last - p.cost) / p.cost * 100 : 0;
                TextView vt = Ui.text(this, Book.money(p.qty * s.last), 17, Ui.TEXT);
                vt.setGravity(Gravity.END);
                right.addView(vt);
                TextView gt = Ui.text(this, Book.signed(g) + "  (" + (gp >= 0 ? "+" : "")
                        + MarketData.fmt(gp) + "%)", 13,
                        g >= 0 ? MarketData.UP : MarketData.DOWN);
                gt.setGravity(Gravity.END);
                right.addView(gt);
            } else {
                right.addView(Ui.text(this, "...", 14, Ui.SUB));
            }
            card.addView(right);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String[] items = {"Buy or sell " + p.sym, "Open chart"};
                    new AlertDialog.Builder(PaperActivity.this)
                            .setTitle(p.sym)
                            .setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    if (which == 0) {
                                        quoteThenTrade(p.sym);
                                    } else {
                                        startActivity(new Intent(PaperActivity.this,
                                                ChartActivity.class)
                                                .putExtra("symbol", p.sym)
                                                .putExtra("name", p.sym));
                                    }
                                }
                            })
                            .show();
                }
            });
            root.addView(card, Ui.full(this, 10));
        }

        Button fund = Ui.button(this, "Add cash with Knowledge Points", false);
        fund.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFunds();
            }
        });
        root.addView(fund, Ui.full(this, 16));

        Button hist = Ui.button(this, "Trade history", false);
        hist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (int i = book.history.size() - 1; i >= 0 && i >= book.history.size() - 40;
                     i--) {
                    sb.append(book.history.get(i)).append("\n\n");
                }
                new AlertDialog.Builder(PaperActivity.this)
                        .setTitle("Trade history")
                        .setMessage(sb.length() == 0 ? "No trades yet." : sb.toString().trim())
                        .setPositiveButton("Close", null)
                        .show();
            }
        });
        root.addView(hist, Ui.full(this, 10));

        Button reset = Ui.button(this, "Start over", false);
        reset.setTextColor(Ui.DANGER);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PaperActivity.this)
                        .setMessage("Wipe this paper account and start again with "
                                + Book.money(STARTER_CASH) + "? Cash you added with "
                                + "Knowledge Points is not refunded.")
                        .setPositiveButton("Start over", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                book.positions.clear();
                                book.history.clear();
                                book.cash = STARTER_CASH;
                                book.deposited = STARTER_CASH;
                                book.history.add(stamp() + "  Account reset to "
                                        + Book.money(STARTER_CASH));
                                book.save(PaperActivity.this);
                                quotes.clear();
                                build();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        root.addView(reset, Ui.full(this, 10));

        root.addView(Ui.text(this, (status.isEmpty() ? "" : status + " ")
                + "Practice only: no real money and no real orders. Trades fill instantly "
                + "at the app's last quoted price, which can lag a few minutes and, when "
                + "the market is closed, is the last close. Real trading has delays, "
                + "spreads and fees that this leaves out.", 12, Ui.SUB), Ui.full(this, 14));
    }

    private void addFunds() {
        int have = Wallet.kp(this);
        if (have <= 0) {
            Toast.makeText(this, "No Knowledge Points to spend. Play a test to earn some.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int[] steps = {10, 25, 50, 100, 250};
        final List<Integer> options = new ArrayList<>();
        for (int s : steps) {
            if (s < have) options.add(s);
        }
        options.add(have);
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) {
            int kp = options.get(i);
            labels[i] = kp + " Knowledge Points  →  " + Book.money(kp * DOLLARS_PER_KP);
        }
        new AlertDialog.Builder(this)
                .setTitle("1 Knowledge Point = $" + DOLLARS_PER_KP + " of paper cash")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        int kp = options.get(which);
                        if (!Scores.spend(PaperActivity.this, kp)) return;
                        double add = kp * DOLLARS_PER_KP;
                        book.cash += add;
                        book.deposited += add;
                        book.history.add(stamp() + "  Added " + Book.money(add) + " for " + kp
                                + " Knowledge Points");
                        book.save(PaperActivity.this);
                        build();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askTicker(String preset) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("Ticker, like AAPL or SPY");
        if (preset != null) input.setText(preset);
        LinearLayout wrap = new LinearLayout(this);
        int pad = Ui.dp(this, 20);
        wrap.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("What do you want to trade?")
                .setView(wrap)
                .setPositiveButton("Get price", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String t = MarketData.clean(input.getText().toString());
                        if (t == null) {
                            Toast.makeText(PaperActivity.this,
                                    "That doesn't look like a ticker.", Toast.LENGTH_LONG)
                                    .show();
                        } else {
                            quoteThenTrade(t);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void quoteThenTrade(final String sym) {
        Toast.makeText(this, "Getting the price of " + sym + "...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MarketData.Series s = MarketData.fetch(
                        new MarketData.Sym(sym, sym, null), 0);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (!s.ok()) {
                            Toast.makeText(PaperActivity.this, "Couldn't get a price for "
                                    + sym + ". Check the ticker and your internet.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        quotes.put(sym, s);
                        ticket(sym, s.last);
                    }
                });
            }
        }).start();
    }

    /** The order ticket: how many shares, then Buy or Sell at the quoted price. */
    private void ticket(final String sym, final double price) {
        Book.Pos held = book.find(sym);
        final double have = held == null ? 0 : held.qty;
        int canBuy = (int) Math.floor(book.cash / price);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        TextView info = new TextView(this);
        info.setText("Price: " + Book.money(price) + "\nCash: " + Book.money(book.cash)
                + "  (enough for " + canBuy + " shares)\nYou hold: " + Book.qty(have)
                + " shares");
        info.setTextSize(15);
        box.addView(info);
        final EditText shares = new EditText(this);
        shares.setSingleLine(true);
        shares.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        shares.setHint("Number of shares");
        box.addView(shares, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                .setTitle(sym)
                .setView(box)
                .setPositiveButton("Buy", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        double q = PortfolioActivity.parse(shares.getText().toString());
                        if (q <= 0) {
                            say("Enter how many shares.");
                        } else if (q * price > book.cash + 0.005) {
                            say("Not enough cash. That costs " + Book.money(q * price) + ".");
                        } else {
                            book.cash -= q * price;
                            book.add(sym, q, price);
                            book.history.add(stamp() + "  Bought " + Book.qty(q) + " " + sym
                                    + " at " + Book.money(price) + " = "
                                    + Book.money(q * price));
                            book.save(PaperActivity.this);
                            say("Bought " + Book.qty(q) + " " + sym + ".");
                            build();
                        }
                    }
                })
                .setNeutralButton("Cancel", null);
        if (have > 0) {
            dlg.setNegativeButton("Sell", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    double q = PortfolioActivity.parse(shares.getText().toString());
                    if (q <= 0) {
                        say("Enter how many shares.");
                    } else if (q > have + 0.0001) {
                        say("You only hold " + Book.qty(have) + " shares.");
                    } else {
                        double gain = book.remove(sym, q, price);
                        book.cash += q * price;
                        book.history.add(stamp() + "  Sold " + Book.qty(q) + " " + sym
                                + " at " + Book.money(price) + " = " + Book.money(q * price)
                                + "  (" + Book.signed(gain) + ")");
                        book.save(PaperActivity.this);
                        say("Sold " + Book.qty(q) + " " + sym + " for " + Book.signed(gain)
                                + ".");
                        build();
                    }
                }
            });
        }
        dlg.show();
    }

    private void say(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
