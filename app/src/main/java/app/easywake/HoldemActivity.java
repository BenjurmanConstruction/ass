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
 * Heads-up limit Texas Hold'em against a computer player, with Fun Coupons (play money).
 *
 * Bets are a fixed size: one big blind before and on the flop, two on the turn and river,
 * with at most four bets a round. The computer player sees only its own two cards and
 * the board, and decides by estimating how often its hand would win.
 */
public class HoldemActivity extends Activity {
    private static final int[] STAKES = {2, 10, 20, 50};
    /** The most one hand can cost, in big blinds: four bets on each of four rounds. */
    private static final int MAX_BLINDS = 24;
    private static final int ME = 0;
    private static final int OPP = 1;
    private static final String OPP_NAME = "Rusty";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Stats.Sitting sitting = new Stats.Sitting("Hold'em", "hand");

    private List<Integer> deck = new ArrayList<>();
    private final int[][] hole = new int[2][2];
    private final int[] board = new int[5];
    private int shown;            // board cards face up: 0, 3, 4 or 5
    private int street;           // 0 pre-flop, 1 flop, 2 turn, 3 river
    private int pot;
    private final int[] in = new int[2];      // put in on this round
    private int myTotal;          // everything I have put in this hand
    private int level;            // what each player must match this round
    private int betsMade;         // bets and raises this round
    private int waiting;          // players who still have to act this round
    private int turn;
    private int button = OPP;     // who has the dealer button (and the small blind)
    private int stakeIndex;
    private int blind;
    private boolean live;
    private boolean showdown;
    private boolean postingBlinds;
    private int token;

    private LinearLayout oppRow;
    private LinearLayout boardRow;
    private LinearLayout myRow;
    private TextView oppText;
    private TextView potText;
    private TextView myText;
    private TextView message;
    private TextView balance;
    private TextView stakeText;
    private LinearLayout stakeRow;
    private LinearLayout actRow;
    private Button foldBtn;
    private Button callBtn;
    private Button raiseBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "holdem")) {
            Toast.makeText(this, "Unlock Texas Hold'em in the skill tree first.",
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
        root.addView(Ui.topBar(this, "Texas Hold'em"), Ui.full(this, 0));

        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable felt = Ui.round(this, 0xCC14432F, 24);
        felt.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        table.setBackground(felt);
        table.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));

        oppText = Ui.text(this, OPP_NAME, 14, Ui.SUB);
        oppText.setGravity(Gravity.CENTER);
        table.addView(oppText);
        oppRow = new LinearLayout(this);
        table.addView(oppRow, Ui.full(this, 6));

        potText = Ui.text(this, "", 16, Ui.ACCENT);
        potText.setGravity(Gravity.CENTER);
        potText.setTypeface(Ui.medium());
        table.addView(potText, Ui.full(this, 12));
        boardRow = new LinearLayout(this);
        table.addView(boardRow, Ui.full(this, 6));

        message = Ui.text(this, "", 15, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        table.addView(message, Ui.full(this, 10));

        myRow = new LinearLayout(this);
        table.addView(myRow, Ui.full(this, 6));
        myText = Ui.text(this, "You", 14, Ui.SUB);
        myText.setGravity(Gravity.CENTER);
        table.addView(myText, Ui.full(this, 6));
        root.addView(table, Ui.full(this, 14));

        balance = Ui.text(this, "", 16, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 12));

        // Stakes and Deal, between hands.
        stakeRow = new LinearLayout(this);
        stakeRow.setOrientation(LinearLayout.HORIZONTAL);
        stakeRow.setGravity(Gravity.CENTER_VERTICAL);
        Button less = small("−");
        less.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (stakeIndex > 0) stakeIndex--;
                paint();
            }
        });
        Button more = small("+");
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (stakeIndex < STAKES.length - 1) stakeIndex++;
                paint();
            }
        });
        stakeText = Ui.text(this, "", 14, Ui.TEXT);
        stakeText.setGravity(Gravity.CENTER);
        Button deal = Ui.button(this, "DEAL", true);
        deal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deal();
            }
        });
        stakeRow.addView(less, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 56)));
        stakeRow.addView(stakeText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        stakeRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 56)));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1.1f);
        dp.leftMargin = Ui.dp(this, 10);
        stakeRow.addView(deal, dp);
        root.addView(stakeRow, Ui.full(this, 10));

        // Fold, Check or Call, Bet or Raise.
        actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        foldBtn = Ui.button(this, "FOLD", false);
        foldBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myTurn()) fold(ME);
            }
        });
        callBtn = Ui.button(this, "CHECK", true);
        callBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myTurn()) call(ME);
            }
        });
        raiseBtn = Ui.button(this, "BET", false);
        raiseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myTurn() && betsMade < 4) raise(ME);
            }
        });
        for (Button bt : new Button[]{foldBtn, callBtn, raiseBtn}) {
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            bt.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 60), 1f);
            int m = Ui.dp(this, 4);
            lp.setMargins(m, 0, m, 0);
            actRow.addView(bt, lp);
        }
        root.addView(actRow, Ui.full(this, 10));

        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HoldemActivity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 14));
        root.addView(Sfx.toggle(this), Ui.full(this, 10));
        Button rules = Ui.button(this, "How to play and hand ranks", false);
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
        message.setText("Pick your stakes and deal.");
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

    private boolean myTurn() {
        return live && turn == ME;
    }

    private int betSize() {
        return street >= 2 ? blind * 2 : blind;
    }

    private String who(int p) {
        return p == ME ? "You" : OPP_NAME;
    }

    /** Moves coupons from a player into the pot. The computer has no wallet to drain. */
    private void pay(int p, int amount) {
        if (amount <= 0) return;
        if (!postingBlinds) Sfx.play(this, Sfx.CHIPS);
        if (p == ME) {
            Wallet.addCoupons(this, -amount);
            myTotal += amount;
        }
        in[p] += amount;
        pot += amount;
    }

    private void deal() {
        if (live) return;
        blind = STAKES[stakeIndex];
        if (Wallet.coupons(this) < blind * MAX_BLINDS) {
            message.setText("You need at least " + blind * MAX_BLINDS + " coupons to sit at "
                    + "these stakes, enough to see a hand through.");
            return;
        }
        token++;
        deck = Cards.deck(rnd);
        for (int p = 0; p < 2; p++) {
            hole[p][0] = deck.remove(deck.size() - 1);
            hole[p][1] = deck.remove(deck.size() - 1);
        }
        for (int i = 0; i < 5; i++) board[i] = deck.remove(deck.size() - 1);
        shown = 0;
        street = 0;
        pot = 0;
        myTotal = 0;
        in[ME] = 0;
        in[OPP] = 0;
        showdown = false;
        live = true;
        button = 1 - button;
        Sfx.play(this, Sfx.CARD);
        // The button posts the small blind and acts first before the flop.
        postingBlinds = true;
        pay(button, blind / 2);
        pay(1 - button, blind);
        postingBlinds = false;
        level = blind;
        betsMade = 1;
        waiting = 2;
        turn = button;
        message.setText(who(button) + (button == ME ? " have" : " has") + " the button and "
                + "the small blind.");
        next();
    }

    /** Hands the action to whoever is next, or moves on when the round is finished. */
    private void next() {
        paint();
        if (!live) return;
        if (waiting <= 0) {
            nextStreet();
            return;
        }
        if (turn == OPP) {
            final int run = ++token;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (run == token && live) oppAct();
                }
            }, 900);
        }
    }

    private void fold(int p) {
        message.setText(who(p) + (p == ME ? " fold. " : " folds. ") + who(1 - p)
                + (p == ME ? " takes" : " take") + " the pot.");
        end(1 - p, false);
    }

    private void call(int p) {
        int owe = level - in[p];
        if (owe > 0) {
            pay(p, owe);
            message.setText(who(p) + (p == ME ? " call " : " calls ") + owe + ".");
        } else {
            message.setText(who(p) + (p == ME ? " check." : " checks."));
        }
        waiting--;
        turn = 1 - p;
        next();
    }

    private void raise(int p) {
        boolean opening = level == 0;
        level += betSize();
        pay(p, level - in[p]);
        betsMade++;
        waiting = 1;
        turn = 1 - p;
        message.setText(who(p) + (opening ? (p == ME ? " bet " : " bets ")
                : (p == ME ? " raise to " : " raises to ")) + level + ".");
        next();
    }

    private void nextStreet() {
        if (street == 3) {
            showdown();
            return;
        }
        street++;
        shown = street == 1 ? 3 : shown + 1;
        Sfx.play(this, Sfx.CARD);
        in[ME] = 0;
        in[OPP] = 0;
        level = 0;
        betsMade = 0;
        waiting = 2;
        // After the flop the player without the button acts first.
        turn = 1 - button;
        String[] names = {"", "The flop.", "The turn.", "The river."};
        message.setText(names[street] + " " + who(turn) + (turn == ME ? " act" : " acts")
                + " first.");
        next();
    }

    private int[] seven(int p) {
        return new int[]{hole[p][0], hole[p][1], board[0], board[1], board[2], board[3],
                board[4]};
    }

    private void showdown() {
        showdown = true;
        long mine = Cards.score(seven(ME));
        long theirs = Cards.score(seven(OPP));
        String hands = "You: " + Cards.handName(mine).toLowerCase(Locale.US) + ". "
                + OPP_NAME + ": " + Cards.handName(theirs).toLowerCase(Locale.US) + ".";
        if (mine == theirs) {
            message.setText(hands + "\nSplit pot.");
            end(-1, true);
        } else {
            int winner = mine > theirs ? ME : OPP;
            message.setText(hands + "\n" + who(winner) + (winner == ME ? " win." : " wins."));
            end(winner, true);
        }
    }

    /** Pays out the pot. @param winner ME, OPP, or -1 for a split */
    private void end(int winner, boolean shownDown) {
        live = false;
        token++;
        int back = winner == ME ? pot : (winner == -1 ? pot / 2 : 0);
        if (back > 0) Wallet.addCoupons(this, back);
        int net = back - myTotal;
        message.setText(message.getText() + "\n" + (net > 0 ? "+" + net + " coupons"
                : (net < 0 ? "−" + (-net) + " coupons" : "No change")));
        Stats.bet(this, "poker", myTotal, back);
        if (winner == ME) Stats.add(this, "pokerWon", 1);
        sitting.round(myTotal, back);
        Sfx.play(this, winner == ME ? Sfx.WIN : (winner == OPP ? Sfx.LOSE : Sfx.CHIPS));
        if (shownDown) shown = 5;
        paint();
    }

    // ---------- the computer player ----------

    /**
     * Estimates how often the computer's hand wins by dealing out the unseen cards many
     * times at random. It never looks at your cards.
     */
    private float equity() {
        List<Integer> unseen = new ArrayList<>();
        for (int c = 0; c < 52; c++) {
            boolean known = c == hole[OPP][0] || c == hole[OPP][1];
            for (int i = 0; i < shown; i++) known |= c == board[i];
            if (!known) unseen.add(c);
        }
        int trials = 220;
        float wins = 0;
        int[] a = new int[7];
        int[] b2 = new int[7];
        for (int t = 0; t < trials; t++) {
            // Pick the cards this trial needs without shuffling the whole list.
            int need = 2 + (5 - shown);
            for (int i = 0; i < need; i++) {
                int j = i + rnd.nextInt(unseen.size() - i);
                Integer tmp = unseen.get(i);
                unseen.set(i, unseen.get(j));
                unseen.set(j, tmp);
            }
            a[0] = hole[OPP][0];
            a[1] = hole[OPP][1];
            b2[0] = unseen.get(0);
            b2[1] = unseen.get(1);
            for (int i = 0; i < 5; i++) {
                int card = i < shown ? board[i] : unseen.get(2 + i - shown);
                a[2 + i] = card;
                b2[2 + i] = card;
            }
            long x = Cards.score(a);
            long y = Cards.score(b2);
            wins += x > y ? 1f : (x == y ? 0.5f : 0f);
        }
        return wins / trials;
    }

    private void oppAct() {
        float eq = equity();
        int owe = level - in[OPP];
        boolean canRaise = betsMade < 4;
        int roll = rnd.nextInt(100);
        if (owe > 0) {
            float price = owe / (float) (pot + owe);
            if (canRaise && eq > 0.72f && roll < 80) {
                raise(OPP);
            } else if (canRaise && roll < 4) {
                raise(OPP);
            } else if (eq >= price * 0.92f || (street == 0 && eq > 0.3f)) {
                call(OPP);
            } else {
                fold(OPP);
            }
        } else if (canRaise && ((eq > 0.6f && roll < 85) || roll < 13)) {
            raise(OPP);
        } else {
            call(OPP);
        }
    }

    private void paint() {
        balance.setText(String.format(Locale.US, "%,d Fun Coupons", Wallet.coupons(this)));
        int st = STAKES[stakeIndex];
        stakeText.setText("Blinds " + st / 2 + " / " + st + "\nBets " + st + " and " + st * 2);
        stakeRow.setVisibility(live ? View.GONE : View.VISIBLE);
        actRow.setVisibility(live ? View.VISIBLE : View.GONE);

        boolean dealt = pot > 0 || live;
        int[] b = new int[5];
        for (int i = 0; i < 5; i++) b[i] = dealt && i < shown ? board[i] : -2;
        Cards.show(this, boardRow, b, 46);
        Cards.show(this, oppRow, !dealt ? new int[]{-2, -2}
                : (showdown ? hole[OPP] : new int[]{-1, -1}), 46);
        Cards.show(this, myRow, dealt ? hole[ME] : new int[]{-2, -2}, 54);
        potText.setText(dealt ? "Pot " + pot : "");
        oppText.setText(OPP_NAME + (dealt && button == OPP ? "  •  button" : ""));

        String mine = "You" + (dealt && button == ME ? "  •  button" : "");
        if (dealt && shown >= 3) {
            int[] have = new int[2 + shown];
            have[0] = hole[ME][0];
            have[1] = hole[ME][1];
            for (int i = 0; i < shown; i++) have[2 + i] = board[i];
            mine += "  •  " + Cards.handName(Cards.score(have)).toLowerCase(Locale.US);
        }
        myText.setText(mine);

        if (live) {
            boolean me = turn == ME;
            int owe = level - in[ME];
            foldBtn.setEnabled(me && owe > 0);
            foldBtn.setAlpha(me && owe > 0 ? 1f : 0.35f);
            callBtn.setEnabled(me);
            callBtn.setAlpha(me ? 1f : 0.35f);
            callBtn.setText(owe > 0 ? "CALL " + owe : "CHECK");
            boolean canRaise = me && betsMade < 4;
            raiseBtn.setEnabled(canRaise);
            raiseBtn.setAlpha(canRaise ? 1f : 0.35f);
            raiseBtn.setText(betsMade >= 4 ? "CAPPED" : (level == 0 ? "BET " + betSize()
                    : "RAISE TO " + (level + betSize())));
        }
    }

    private void showRules() {
        new AlertDialog.Builder(this)
                .setTitle("How to play")
                .setMessage("You and " + OPP_NAME + " each get two cards of your own. Five "
                        + "shared cards come out in the middle: three (the flop), then "
                        + "one (the turn), then one (the river). The best five-card hand "
                        + "from your two plus the five shared cards wins the pot.\n\n"
                        + "There is a round of betting before the flop and after each "
                        + "deal. On your turn you can FOLD (give up the hand), CHECK or "
                        + "CALL (match the bet), or BET or RAISE. Bets are a fixed size: "
                        + "one big blind before and on the flop, two on the turn and "
                        + "river, with at most four bets a round.\n\n"
                        + "The button swaps every hand. The button posts the small blind "
                        + "and acts first before the flop, then last on later rounds.\n\n"
                        + "Hands, best to worst:\n"
                        + "Straight flush: five in a row, same suit\n"
                        + "Four of a kind\n"
                        + "Full house: three of one, two of another\n"
                        + "Flush: five of the same suit\n"
                        + "Straight: five in a row\n"
                        + "Three of a kind\n"
                        + "Two pair\n"
                        + "One pair\n"
                        + "High card\n\n"
                        + OPP_NAME + " can't see your cards. He works out his chances "
                        + "from his own two cards and the board, and bluffs now and then. "
                        + "There's no rake, so against him it's an even game: whoever "
                        + "plays better comes out ahead.")
                .setPositiveButton("Close", null)
                .show();
    }
}
