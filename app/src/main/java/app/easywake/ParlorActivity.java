package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * The Slot Parlor: one place for every slot machine. A single unlock (75 Knowledge
 * Points, outside the skill tree) opens all the new machines at once. Play money only.
 */
public class ParlorActivity extends Activity {
    static final int COST = 75;
    private static final String ID = "parlor";
    /** Payback for each machine, worked out from millions of test spins (see SlotMath). */
    static final String[] PAYBACK = {"98%", "98%", "99%", "98%", "98%"};
    static final String[] FEEL = {"Wins often", "Free spins about 1 in 86, you pick",
            "Hold & spin about 1 in 51", "Wheel about 1 in 49", "Free spins about 1 in 138"};

    private LinearLayout root;

    static boolean owned(android.content.Context c) {
        return Tree.owned(c, ID);
    }

    /** Spends the KP and records the unlock. Not on the skill tree. */
    private boolean unlock() {
        if (owned(this) || !Scores.spend(this, COST)) return false;
        List<String> have = MarketData.list(this, "treeOwned", "");
        have.add(ID);
        MarketData.saveList(this, "treeOwned", have);
        Stats.log(this, "Unlocked the Slot Parlor");
        return true;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
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
        build();
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Slot Parlor"), Ui.full(this, 0));
        TextView bal = Ui.text(this, String.format(Locale.US, "%,d Fun Coupons  •  %d KP",
                Wallet.coupons(this), Wallet.kp(this)), 15, Ui.ACCENT);
        root.addView(bal, Ui.full(this, 8));

        final boolean open = owned(this);
        if (!open) {
            LinearLayout card = card();
            TextView t = Ui.text(this, "Unlock every machine", 18, Ui.TEXT);
            t.setTypeface(Ui.medium());
            card.addView(t);
            card.addView(Ui.text(this, "One unlock opens every machine below, coupon and penny. "
                    + COST + " Knowledge Points, once.", 14, Ui.SUB), Ui.full(this, 4));
            Button buy = Ui.button(this, "Unlock for " + COST + " KP", true);
            buy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Wallet.kp(ParlorActivity.this) < COST) {
                        new AlertDialog.Builder(ParlorActivity.this)
                                .setTitle("Not enough Knowledge Points")
                                .setMessage("You have " + Wallet.kp(ParlorActivity.this)
                                        + " KP and need " + COST + ". Brain gym tests earn "
                                        + "more.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    new AlertDialog.Builder(ParlorActivity.this)
                            .setTitle("Unlock the Slot Parlor?")
                            .setMessage("Spend " + COST + " Knowledge Points to open all "
                                    + "five machines for good.")
                            .setPositiveButton("Unlock", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    if (unlock()) {
                                        Sfx.play(ParlorActivity.this, Sfx.JACKPOT);
                                        build();
                                    }
                                }
                            })
                            .setNegativeButton("Not now", null)
                            .show();
                }
            });
            card.addView(buy, Ui.full(this, 10));
            root.addView(card, Ui.full(this, 12));
        }

        root.addView(Ui.heading(this, "Coupon machines"), Ui.full(this, 18));
        for (int i = 0; i < SlotMath.COUPON_MACHINES; i++) {
            final int m = i;
            LinearLayout card = card();
            TextView name = Ui.text(this, SlotMath.NAMES[i], 18, open ? Ui.TEXT : Ui.SUB);
            name.setTypeface(Ui.medium());
            card.addView(name);
            card.addView(Ui.text(this, SlotMath.BLURBS[i], 14, Ui.SUB), Ui.full(this, 2));
            card.addView(Ui.text(this, "Pays back about " + PAYBACK[i] + "  •  " + FEEL[i]
                    + (open ? "" : "  •  Locked"), 13, open ? Ui.ACCENT : Ui.SUB),
                    Ui.full(this, 6));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!owned(ParlorActivity.this)) {
                        android.widget.Toast.makeText(ParlorActivity.this,
                                "Unlock the Slot Parlor first (" + COST + " KP).",
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(new Intent(ParlorActivity.this, MachineActivity.class)
                            .putExtra("machine", m));
                }
            });
            root.addView(card, Ui.full(this, 10));
        }

        // The penny machines on the shared jackpot bank.
        root.addView(Ui.heading(this, "Penny Palace"), Ui.full(this, 22));
        JackpotBank bank = PennyActivity.loadBank(this, new java.util.Random());
        LinearLayout jp = card();
        TextView grand = Ui.text(this, String.format(Locale.US, "GRAND  %,.2f",
                bank.meter[JackpotBank.GRAND] / 100.0), 22, 0xFFFFD54F);
        grand.setTypeface(Ui.medium());
        jp.addView(grand);
        jp.addView(Ui.text(this, String.format(Locale.US, "MAJOR %,.2f   •   MINOR %,.2f   •   "
                        + "MINI %,.2f", bank.meter[JackpotBank.MAJOR] / 100.0,
                bank.meter[JackpotBank.MINOR] / 100.0, bank.meter[JackpotBank.MINI] / 100.0),
                13, Ui.TEXT), Ui.full(this, 2));
        jp.addView(Ui.text(this, String.format(Locale.US, "Mystery %,.2f (must hit by 25)   •   "
                        + "Mega mystery %,.2f (must hit by 250)",
                bank.meter[JackpotBank.MYSTERY_SMALL] / 100.0,
                bank.meter[JackpotBank.MYSTERY_BIG] / 100.0), 12, Ui.SUB), Ui.full(this, 2));
        long cr = PennyActivity.credits(this);
        jp.addView(Ui.text(this, "Shared progressive jackpots on every penny machine, in "
                + "coupons. Bets from 25 credits (¼ coupon)."
                + (cr > 0 ? String.format(Locale.US, " You have %,d credits in the machines.",
                cr) : ""), 12, Ui.SUB), Ui.full(this, 6));
        root.addView(jp, Ui.full(this, 8));
        for (int i = SlotMath.COUPON_MACHINES; i < SlotMath.NAMES.length; i++) {
            final int m = i;
            LinearLayout card = card();
            TextView name = Ui.text(this, SlotMath.NAMES[i], 18, open ? Ui.TEXT : Ui.SUB);
            name.setTypeface(Ui.medium());
            card.addView(name);
            card.addView(Ui.text(this, SlotMath.BLURBS[i], 14, Ui.SUB), Ui.full(this, 2));
            card.addView(Ui.text(this, "Penny machine  •  free spins  •  linked jackpots"
                    + (open ? "" : "  •  Locked"), 13, open ? Ui.ACCENT : Ui.SUB),
                    Ui.full(this, 6));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!owned(ParlorActivity.this)) {
                        android.widget.Toast.makeText(ParlorActivity.this,
                                "Unlock the Slot Parlor first (" + COST + " KP).",
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(new Intent(ParlorActivity.this, PennyActivity.class)
                            .putExtra("machine", m));
                }
            });
            root.addView(card, Ui.full(this, 10));
        }

        // The older machines, for anyone who unlocked them on the skill tree.
        boolean s1 = Tree.owned(this, "slot");
        boolean s2 = Tree.owned(this, "slot2");
        if (s1 || s2) {
            root.addView(Ui.heading(this, "Your other machines"), Ui.full(this, 18));
            if (s1) root.addView(link("Lucky Sevens", SlotActivity.class), Ui.full(this, 10));
            if (s2) root.addView(link("Royal Riches", Slot2Activity.class), Ui.full(this, 10));
        }
        root.addView(Ui.text(this, "Play money only. Fun Coupons can't be bought with or "
                + "cashed out for real money. Every machine shows its odds.", 12, Ui.SUB),
                Ui.full(this, 18));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
        card.setBackground(Ui.ripple(this, bg, 18));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        return card;
    }

    private Button link(String label, final Class<?> screen) {
        Button bt = Ui.button(this, label, false);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ParlorActivity.this, screen));
            }
        });
        return bt;
    }
}
