package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Shows the three currencies and lets you move value between them. */
public class WalletActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        build();
    }

    private View balance(String label, int amount, String note) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, 20));
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        card.addView(Ui.heading(this, label));
        TextView v = Ui.text(this, String.format(java.util.Locale.US, "%,d", amount), 34,
                Ui.ACCENT);
        v.setTypeface(Ui.light());
        card.addView(v);
        card.addView(Ui.text(this, note, 13, Ui.SUB));
        return card;
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Wallet"), Ui.full(this, 0));

        root.addView(balance("Knowledge Points", Wallet.kp(this),
                "Earned from Brain gym tests. Spend them on the skill tree, lessons and "
                        + "vault facts."), Ui.full(this, 14));
        root.addView(balance("Gold", Wallet.gold(this),
                "Your savings. Earns " + Wallet.interestPercent(this) + "% on each day you "
                        + "play a test (up to 60 a day)."), Ui.full(this, 10));
        root.addView(balance("Fun Coupons", Wallet.coupons(this),
                "Play money for the casino. One coupon is one penny bet."), Ui.full(this, 10));

        root.addView(Ui.heading(this, "Exchange"), Ui.full(this, 26));
        final int rate = Wallet.couponRate(this);

        root.addView(action("Buy Fun Coupons", "1 Knowledge Point = " + rate + " coupons",
                new Runnable() {
                    @Override
                    public void run() {
                        pick("Buy Fun Coupons with Knowledge Points", Wallet.kp(WalletActivity
                                .this), " KP", new Amount() {
                            @Override
                            public void chosen(int n) {
                                if (Wallet.kpToCoupons(WalletActivity.this, n)) {
                                    done("You got " + (n * rate) + " Fun Coupons.");
                                }
                            }
                        });
                    }
                }), Ui.full(this, 10));

        root.addView(action("Save Knowledge Points as Gold", "1 Knowledge Point = 1 Gold",
                new Runnable() {
                    @Override
                    public void run() {
                        pick("Move Knowledge Points into Gold", Wallet.kp(WalletActivity.this),
                                " KP", new Amount() {
                                    @Override
                                    public void chosen(int n) {
                                        if (Wallet.kpToGold(WalletActivity.this, n)) {
                                            done(n + " Gold saved.");
                                        }
                                    }
                                });
                    }
                }), Ui.full(this, 10));

        root.addView(action("Take Gold out", "1 Gold = 1 Knowledge Point",
                new Runnable() {
                    @Override
                    public void run() {
                        pick("Turn Gold back into Knowledge Points",
                                Wallet.gold(WalletActivity.this), " Gold", new Amount() {
                                    @Override
                                    public void chosen(int n) {
                                        if (Wallet.goldToKp(WalletActivity.this, n)) {
                                            done(n + " Knowledge Points added.");
                                        }
                                    }
                                });
                    }
                }), Ui.full(this, 10));

        root.addView(action("Cash coupons out into Gold",
                Wallet.COUPONS_PER_GOLD + " coupons = 1 Gold",
                new Runnable() {
                    @Override
                    public void run() {
                        pick("Turn casino winnings into Gold",
                                Wallet.coupons(WalletActivity.this) / Wallet.COUPONS_PER_GOLD,
                                " Gold", new Amount() {
                                    @Override
                                    public void chosen(int n) {
                                        if (Wallet.couponsToGold(WalletActivity.this, n) > 0) {
                                            done(n + " Gold added from "
                                                    + (n * Wallet.COUPONS_PER_GOLD)
                                                    + " coupons.");
                                        }
                                    }
                                });
                    }
                }), Ui.full(this, 10));

        root.addView(Ui.text(this, "None of this is real money. Nothing here can be bought "
                + "with money or cashed out for it.", 12, Ui.SUB), Ui.full(this, 18));
    }

    private View action(String title, String sub, final Runnable run) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        TextView t = Ui.text(this, title, 16, Ui.TEXT);
        t.setTypeface(Ui.medium());
        card.addView(t);
        card.addView(Ui.text(this, sub, 13, Ui.ACCENT));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        return card;
    }

    private interface Amount {
        void chosen(int n);
    }

    /** Asks how much to move, offering sensible amounts up to what's available. */
    private void pick(String title, final int max, String unit, final Amount amount) {
        if (max <= 0) {
            Toast.makeText(this, "You don't have enough for that yet.", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        int[] steps = {5, 10, 25, 50, 100, 250, 500};
        final java.util.List<Integer> options = new java.util.ArrayList<>();
        for (int s : steps) {
            if (s < max) options.add(s);
        }
        options.add(max);
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = options.get(i) + unit + (options.get(i) == max ? "  (all of it)" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        amount.chosen(options.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void done(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        build();
    }
}
