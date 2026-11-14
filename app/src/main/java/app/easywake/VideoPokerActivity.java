package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Video poker, Jacks or Better, played with Fun Coupons (play money only). You are dealt
 * five cards, choose which to hold, and draw once. A pair of jacks or better wins.
 */
public class VideoPokerActivity extends Activity {
    private static final int[] BETS = {1, 2, 5, 10, 25, 50};
    private static final String[] HANDS = {"Royal flush", "Straight flush", "Four of a kind",
            "Full house", "Flush", "Straight", "Three of a kind", "Two pair",
            "Jacks or better"};
    private static final int[] PAYS = {800, 50, 25, 9, 6, 4, 3, 2, 1};

    private final Random rnd = new Random();
    private final Stats.Sitting sitting = new Stats.Sitting("Video poker", "hand");
    private final int[] hand = new int[5];
    private final boolean[] held = new boolean[5];
    private List<Integer> deck;
    private boolean dealt;
    private int betIndex = 1;
    private int bet;

    private LinearLayout cardRow;
    private TextView message;
    private TextView balance;
    private TextView betText;
    private TextView payText;
    private Button dealBtn;
    private LinearLayout betRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "vpoker")) {
            Toast.makeText(this, "Unlock Video poker in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.topBar(this, "Video poker"), Ui.full(this, 0));

        payText = Ui.text(this, "", 13, Ui.TEXT);
        payText.setBackground(Ui.card(this, 16));
        payText.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        payText.setLineSpacing(Ui.dp(this, 2), 1f);
        root.addView(payText, Ui.full(this, 12));

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable felt = Ui.round(this, 0xCC14432F, 22);
        felt.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        table.setBackground(felt);
        table.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14));
        cardRow = new LinearLayout(this);
        cardRow.setGravity(Gravity.CENTER);
        table.addView(cardRow);
        message = Ui.text(this, "Choose your bet and deal.", 17, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        table.addView(message, Ui.full(this, 10));
        root.addView(table, Ui.full(this, 12));

        balance = Ui.text(this, "", 16, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 10));

        betRow = new LinearLayout(this);
        betRow.setOrientation(LinearLayout.HORIZONTAL);
        betRow.setGravity(Gravity.CENTER_VERTICAL);
        Button less = step("−", -1);
        Button more = step("+", 1);
        betText = Ui.text(this, "", 16, Ui.TEXT);
        betText.setGravity(Gravity.CENTER);
        betRow.addView(less, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 54)));
        betRow.addView(betText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        betRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 54)));
        root.addView(betRow, Ui.full(this, 8));

        dealBtn = Ui.button(this, "DEAL", true);
        dealBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        dealBtn.setMinHeight(Ui.dp(this, 68));
        dealBtn.setMinimumHeight(Ui.dp(this, 68));
        dealBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dealt) draw();
                else deal();
            }
        });
        root.addView(dealBtn, Ui.full(this, 10));

        root.addView(Sfx.toggle(this), Ui.full(this, 10));
        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(VideoPokerActivity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 10));
        Button rules = Ui.button(this, "How to play and odds", false);
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
        java.util.Arrays.fill(hand, -2);
        paint();
    }

    private Button step(String label, final int dir) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bt.setPadding(0, 0, 0, 0);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dealt) return;
                betIndex = Math.max(0, Math.min(BETS.length - 1, betIndex + dir));
                paint();
            }
        });
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

    private void deal() {
        bet = BETS[betIndex];
        if (Wallet.coupons(this) < bet) {
            message.setText("Not enough coupons for that bet.\nLower it or visit the Wallet.");
            return;
        }
        Wallet.addCoupons(this, -bet);
        deck = Cards.deck(rnd);
        for (int i = 0; i < 5; i++) {
            hand[i] = deck.remove(deck.size() - 1);
            held[i] = false;
        }
        dealt = true;
        Sfx.play(this, Sfx.CARD);
        int now = rank(hand);
        message.setText((now >= 0 ? "You have " + HANDS[now].toLowerCase(Locale.US) + ". "
                : "") + "Tap the cards you want to keep,\nthen draw.");
        paint();
    }

    private void draw() {
        for (int i = 0; i < 5; i++) {
            if (!held[i]) hand[i] = deck.remove(deck.size() - 1);
        }
        dealt = false;
        Sfx.play(this, Sfx.CARD);
        int result = rank(hand);
        int back = result >= 0 ? PAYS[result] * bet : 0;
        if (back > 0) Wallet.addCoupons(this, back);
        message.setText(result >= 0 ? HANDS[result] + "!\n+" + String.format(Locale.US, "%,d",
                back) + " coupons" : "Nothing this time.\nA pair of jacks or better wins.");
        Sfx.play(this, result < 0 ? Sfx.LOSE : (result <= 3 ? Sfx.BIG_WIN : Sfx.WIN));
        Stats.bet(this, "vpoker", bet, back);
        sitting.round(bet, back);
        java.util.Arrays.fill(held, false);
        paint();
    }

    /** Which paying hand these five cards make: an index into HANDS, or -1 for none. */
    static int rank(int[] cards) {
        for (int c : cards) {
            if (c < 0) return -1;
        }
        long score = Cards.score(cards);
        long rest = score;
        int[] kick = new int[5];
        for (int i = 4; i >= 0; i--) {
            kick[i] = (int) (rest % 13);
            rest /= 13;
        }
        switch ((int) rest) {
            case 8:
                return kick[0] == 12 ? 0 : 1;
            case 7:
                return 2;
            case 6:
                return 3;
            case 5:
                return 4;
            case 4:
                return 5;
            case 3:
                return 6;
            case 2:
                return 7;
            case 1:
                // Only a pair of jacks, queens, kings or aces pays.
                return kick[0] >= 9 ? 8 : -1;
            default:
                return -1;
        }
    }

    private void paint() {
        balance.setText(String.format(Locale.US, "%,d Fun Coupons", Wallet.coupons(this)));
        int shownBet = dealt ? bet : BETS[betIndex];
        betText.setText("Bet " + shownBet);
        betRow.setAlpha(dealt ? 0.4f : 1f);
        dealBtn.setText(dealt ? "DRAW" : "DEAL");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HANDS.length; i++) {
            sb.append(HANDS[i]).append("   ").append(String.format(Locale.US, "%,d",
                    PAYS[i] * shownBet)).append(i < HANDS.length - 1 ? "\n" : "");
        }
        payText.setText(sb.toString());

        cardRow.removeAllViews();
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout slot = new LinearLayout(this);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView card = Cards.view(this, hand[i], 54);
            slot.addView(card, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 78)));
            TextView tag = Ui.text(this, held[i] ? "HELD" : " ", 11, Ui.ACCENT);
            tag.setTypeface(Ui.medium());
            tag.setGravity(Gravity.CENTER);
            slot.addView(tag);
            if (held[i]) card.setAlpha(1f);
            else if (dealt) card.setAlpha(0.82f);
            slot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!dealt) return;
                    held[idx] = !held[idx];
                    paint();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cardRow.addView(slot, lp);
        }
    }

    private void showRules() {
        new AlertDialog.Builder(this)
                .setTitle("How to play")
                .setMessage("Press DEAL to get five cards. Tap the ones you want to keep; "
                        + "they are marked HELD. Press DRAW and the others are replaced, "
                        + "once. Your final five cards are paid from the table at the "
                        + "top.\n\n"
                        + "The lowest hand that pays is a pair of jacks, queens, kings or "
                        + "aces, which gives your bet back. Lower pairs pay nothing.\n\n"
                        + "A few rules of thumb:\n"
                        + "• Always keep a paying hand, except break up a pair or "
                        + "better only for four cards to a royal flush.\n"
                        + "• Keep any pair over single high cards.\n"
                        + "• Keep four cards to a flush.\n"
                        + "• With nothing, keep jacks or higher and draw the rest.\n\n"
                        + "This is the full-pay version of the game: a full house pays 9 "
                        + "and a flush pays 6. Played perfectly it returns about 99.5 "
                        + "coupons for every 100 bet, the best odds in this casino. "
                        + "Played by feel it returns less.")
                .setPositiveButton("Close", null)
                .show();
    }
}
