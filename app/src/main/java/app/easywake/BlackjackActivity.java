package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Blackjack against the dealer, played with Fun Coupons (play money only). The dealer
 * stands on every 17, blackjack pays 3 to 2, and you can double on your first two cards.
 */
public class BlackjackActivity extends Activity {
    private static final int[] BETS = {2, 10, 20, 50, 100, 200};
    private static final int BETTING = 0;
    private static final int PLAYING = 1;
    private static final int DEALER = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Stats.Sitting sitting = new Stats.Sitting("Blackjack", "hand");
    private final List<Integer> mine = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private List<Integer> deck = new ArrayList<>();
    private int state = BETTING;
    private int betIndex = 1;
    private int bet;
    private boolean holeHidden;
    private int token;

    private LinearLayout dealerRow;
    private LinearLayout myRow;
    private TextView dealerText;
    private TextView myText;
    private TextView message;
    private TextView balance;
    private TextView betText;
    private Button dealBtn;
    private Button hitBtn;
    private Button standBtn;
    private Button doubleBtn;
    private LinearLayout betRow;
    private LinearLayout playRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "blackjack")) {
            Toast.makeText(this, "Unlock Blackjack in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.topBar(this, "Blackjack"), Ui.full(this, 0));

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable felt = Ui.round(this, 0xCC14432F, 24);
        felt.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        table.setBackground(felt);
        table.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));

        dealerText = Ui.text(this, "Dealer", 14, Ui.SUB);
        dealerText.setGravity(Gravity.CENTER);
        table.addView(dealerText);
        dealerRow = new LinearLayout(this);
        dealerRow.setMinimumHeight(Ui.dp(this, 78));
        table.addView(dealerRow, Ui.full(this, 6));

        message = Ui.text(this, "Place your bet.", 18, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        table.addView(message, Ui.full(this, 12));

        myRow = new LinearLayout(this);
        myRow.setMinimumHeight(Ui.dp(this, 78));
        table.addView(myRow, Ui.full(this, 8));
        myText = Ui.text(this, "You", 14, Ui.SUB);
        myText.setGravity(Gravity.CENTER);
        table.addView(myText, Ui.full(this, 6));
        root.addView(table, Ui.full(this, 14));

        balance = Ui.text(this, "", 16, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 12));

        // Bet chooser and Deal.
        betRow = new LinearLayout(this);
        betRow.setOrientation(LinearLayout.HORIZONTAL);
        betRow.setGravity(Gravity.CENTER_VERTICAL);
        Button less = small("−");
        less.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (betIndex > 0) betIndex--;
                paint();
            }
        });
        Button more = small("+");
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (betIndex < BETS.length - 1) betIndex++;
                paint();
            }
        });
        betText = Ui.text(this, "", 16, Ui.TEXT);
        betText.setGravity(Gravity.CENTER);
        dealBtn = Ui.button(this, "DEAL", true);
        dealBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deal();
            }
        });
        betRow.addView(less, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 56)));
        betRow.addView(betText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        betRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 56)));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1.3f);
        dp.leftMargin = Ui.dp(this, 10);
        betRow.addView(dealBtn, dp);
        root.addView(betRow, Ui.full(this, 10));

        // Hit, Stand, Double.
        playRow = new LinearLayout(this);
        playRow.setOrientation(LinearLayout.HORIZONTAL);
        hitBtn = Ui.button(this, "HIT", true);
        hitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hit(false);
            }
        });
        standBtn = Ui.button(this, "STAND", false);
        standBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state == PLAYING) dealerTurn();
            }
        });
        doubleBtn = Ui.button(this, "DOUBLE", false);
        doubleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doubleDown();
            }
        });
        for (Button bt : new Button[]{hitBtn, standBtn, doubleBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 60), 1f);
            int m = Ui.dp(this, 4);
            lp.setMargins(m, 0, m, 0);
            playRow.addView(bt, lp);
        }
        root.addView(playRow, Ui.full(this, 10));

        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BlackjackActivity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 14));
        root.addView(Sfx.toggle(this), Ui.full(this, 10));
        Button rules = Ui.button(this, "Rules and odds", false);
        rules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRules();
            }
        });
        root.addView(rules, Ui.full(this, 10));
        root.addView(Ui.text(this, "Play money only. Fun Coupons can't be bought with or "
                + "cashed out for real money.", 12, Ui.SUB), Ui.full(this, 14));

        setContentView(scroll);
        Bg.apply(this, scroll);
        paint();
    }

    private Button small(String label) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bt.setPadding(0, 0, 0, 0);
        return bt;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (balance != null) paint();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sitting.close(this);
    }

    @Override
    protected void onDestroy() {
        token++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Best total for a hand, counting aces as 11 where that doesn't bust. */
    private static int total(List<Integer> hand) {
        int sum = 0;
        int aces = 0;
        for (int card : hand) {
            int r = Cards.rank(card);
            if (r == 12) {
                aces++;
                sum += 11;
            } else {
                sum += r >= 8 ? 10 : r + 2;
            }
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    private static boolean soft(List<Integer> hand) {
        int hard = 0;
        boolean ace = false;
        for (int card : hand) {
            int r = Cards.rank(card);
            if (r == 12) {
                ace = true;
                hard += 1;
            } else {
                hard += r >= 8 ? 10 : r + 2;
            }
        }
        return ace && hard + 10 <= 21;
    }

    private static boolean natural(List<Integer> hand) {
        return hand.size() == 2 && total(hand) == 21;
    }

    private int draw() {
        return deck.remove(deck.size() - 1);
    }

    private void deal() {
        if (state != BETTING) return;
        bet = BETS[betIndex];
        if (Wallet.coupons(this) < bet) {
            message.setText("Not enough coupons for that bet.\nLower it or visit the Wallet.");
            return;
        }
        Wallet.addCoupons(this, -bet);
        // A fresh six-deck shoe every hand, so counting cards won't help.
        deck = new ArrayList<>();
        for (int i = 0; i < 6; i++) deck.addAll(Cards.deck(rnd));
        java.util.Collections.shuffle(deck, rnd);
        mine.clear();
        dealer.clear();
        mine.add(draw());
        dealer.add(draw());
        mine.add(draw());
        dealer.add(draw());
        holeHidden = true;
        state = PLAYING;
        Sfx.play(this, Sfx.CARD);
        message.setText("");
        if (natural(mine) || natural(dealer)) {
            holeHidden = false;
            if (natural(mine) && natural(dealer)) {
                settle("Both have blackjack. Push.", bet, 0);
            } else if (natural(mine)) {
                settle("Blackjack! Pays 3 to 2.", bet + bet * 3 / 2, 1);
            } else {
                settle("Dealer has blackjack.", 0, -1);
            }
            return;
        }
        paint();
    }

    private void hit(boolean lastCard) {
        if (state != PLAYING) return;
        mine.add(draw());
        Sfx.play(this, Sfx.CARD);
        int t = total(mine);
        if (t > 21) {
            holeHidden = false;
            settle("Bust with " + t + ".", 0, -1);
        } else if (t == 21 || lastCard) {
            dealerTurn();
        } else {
            paint();
        }
    }

    private void doubleDown() {
        if (state != PLAYING || mine.size() != 2) return;
        if (Wallet.coupons(this) < bet) {
            message.setText("Not enough coupons to double.");
            return;
        }
        Wallet.addCoupons(this, -bet);
        bet *= 2;
        hit(true);
    }

    /** The dealer turns over the hidden card and draws to 17, one card at a time. */
    private void dealerTurn() {
        state = DEALER;
        holeHidden = false;
        paint();
        final int run = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (run != token) return;
                if (total(dealer) < 17) {
                    dealer.add(draw());
                    Sfx.play(BlackjackActivity.this, Sfx.CARD);
                    paint();
                    handler.postDelayed(this, 650);
                    return;
                }
                int d = total(dealer);
                int p = total(mine);
                if (d > 21) {
                    settle("Dealer busts with " + d + ". You win.", bet * 2, 1);
                } else if (p > d) {
                    settle("You win, " + p + " to " + d + ".", bet * 2, 1);
                } else if (p == d) {
                    settle("Push at " + p + ". Bet returned.", bet, 0);
                } else {
                    settle("Dealer wins, " + d + " to " + p + ".", 0, -1);
                }
            }
        }, 650);
    }

    /** Ends the hand. @param back coupons returned, including the bet itself */
    private void settle(String text, int back, int outcome) {
        state = BETTING;
        if (back > 0) Wallet.addCoupons(this, back);
        int net = back - bet;
        message.setText(text + "\n" + (net > 0 ? "+" + net + " coupons" : (net < 0
                ? "−" + (-net) + " coupons" : "No change")));
        Stats.bet(this, "bj", bet, back);
        Stats.add(this, outcome > 0 ? "bjWon" : (outcome < 0 ? "bjLost" : "bjPush"), 1);
        sitting.round(bet, back);
        Sfx.play(this, outcome > 0 ? Sfx.WIN : (outcome < 0 ? Sfx.LOSE : Sfx.CHIPS));
        paint();
    }

    private static int[] toArray(List<Integer> hand, boolean hideSecond) {
        int[] out = new int[hand.size()];
        for (int i = 0; i < out.length; i++) out[i] = hideSecond && i == 1 ? -1 : hand.get(i);
        return out;
    }

    private void paint() {
        balance.setText(String.format(Locale.US, "%,d Fun Coupons", Wallet.coupons(this)));
        betText.setText("Bet " + BETS[betIndex]);
        betRow.setVisibility(state == BETTING ? View.VISIBLE : View.GONE);
        playRow.setVisibility(state == PLAYING ? View.VISIBLE : View.GONE);
        doubleBtn.setEnabled(mine.size() == 2);
        doubleBtn.setAlpha(mine.size() == 2 ? 1f : 0.4f);
        if (mine.isEmpty()) {
            Cards.show(this, dealerRow, new int[]{-2, -2}, 50);
            Cards.show(this, myRow, new int[]{-2, -2}, 50);
            dealerText.setText("Dealer");
            myText.setText("You");
            return;
        }
        Cards.show(this, dealerRow, toArray(dealer, holeHidden), dealer.size() > 5 ? 40 : 50);
        Cards.show(this, myRow, toArray(mine, false), mine.size() > 5 ? 40 : 50);
        dealerText.setText(holeHidden ? "Dealer shows " + Cards.label(dealer.get(0))
                : "Dealer: " + total(dealer));
        myText.setText("You: " + (soft(mine) && total(mine) < 21 ? "soft " : "") + total(mine)
                + (state == PLAYING || state == DEALER ? "   •   bet " + bet : ""));
    }

    private void showRules() {
        new AlertDialog.Builder(this)
                .setTitle("Rules and odds")
                .setMessage("Get closer to 21 than the dealer without going over.\n\n"
                        + "Number cards count their number, face cards count 10, and an "
                        + "ace counts 11 or 1, whichever helps.\n\n"
                        + "HIT takes another card. STAND keeps what you have. DOUBLE "
                        + "doubles your bet for exactly one more card, and is only "
                        + "offered on your first two cards.\n\n"
                        + "The dealer must draw to 16 and stand on any 17. A tie is a "
                        + "push and you get your bet back. A two-card 21 is a blackjack "
                        + "and pays 3 to 2. There is no splitting or insurance here.\n\n"
                        + "Every hand is dealt from a newly shuffled six decks. Played "
                        + "well, the house edge in a game like this is small, well under "
                        + "the slot's, but the house still wins over time. A simple guide: "
                        + "stand on 17 or more, hit 11 or less, and on 12 to 16 stand if "
                        + "the dealer shows 2 to 6, otherwise hit.")
                .setPositiveButton("Close", null)
                .show();
    }
}
