package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * No-limit Texas Hold'em for up to six players: you and a table of characters who play in
 * their own styles and never stop running their mouths. Play money (Fun Coupons) only.
 */
public class HoldemActivity extends Activity {
    private static final int ME = 0;
    private static final int[][] STAKES = {{1, 2}, {5, 10}, {10, 20}, {25, 50}, {50, 100}};
    private static final int[] BUYIN_BB = {50, 100, 200};
    /** Which seats are used for each table size (seat 0 is you, at the bottom). */
    private static final int[][] LAYOUT = {{}, {}, {0, 3}, {0, 2, 4}, {0, 1, 3, 5},
            {0, 1, 2, 4, 5}, {0, 1, 2, 3, 4, 5}};
    private static final int TRIALS = 260;
    private static final String SAVED = "hemStack";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Stats.Sitting sitting = new Stats.Sitting("Hold'em", "hand");
    private HoldemTable table;

    // Choices in the lobby.
    private int stakeIndex = 1;
    private int sizeChoice = 6;
    private int buyinIndex = 1;
    private int buyin;

    private boolean seated;
    private int token;
    private boolean raisedByMe;
    private int myFoldedTo = -1;
    private boolean standAfterHand;

    // Speech bubbles, one per seat.
    private final String[] bubble = new String[HoldemTable.SEATS];
    private final long[] bubbleUntil = new long[HoldemTable.SEATS];
    private final long[] wonAmount = new long[HoldemTable.SEATS];
    private long wonUntil;
    private final List<CharSequence> log = new ArrayList<>();

    private LinearLayout root;
    private TableView tableView;
    private TextView status;
    private TextView chat;
    private LinearLayout actRow;
    private LinearLayout raisePanel;
    private Button foldBtn;
    private Button callBtn;
    private Button raiseBtn;
    private TextView raiseLabel;
    private SeekBar raiseBar;
    private long raiseTo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "holdem")) {
            Toast.makeText(this, "Unlock Texas Hold'em in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Chips left at a table when the app was closed go back to the wallet.
        int left = Prefs.getInt(this, SAVED, 0);
        if (left > 0) {
            Wallet.addCoupons(this, left);
            Prefs.putInt(this, SAVED, 0);
            Toast.makeText(this, "Your " + left + " chips from last time are back in your "
                    + "wallet.", Toast.LENGTH_LONG).show();
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);
        Bg.apply(this, scroll);
        lobby();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) standUp(false);
        sitting.close(this);
    }

    @Override
    protected void onDestroy() {
        token++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void later(long ms, final Runnable r) {
        final int mine = token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token && !isFinishing()) r.run();
            }
        }, ms);
    }

    // ================================================================== lobby

    private void lobby() {
        seated = false;
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Texas Hold'em"), Ui.full(this, 0));
        root.addView(Ui.text(this, String.format(Locale.US, "%,d Fun Coupons",
                Wallet.coupons(this)), 16, Ui.ACCENT), Ui.full(this, 8));

        root.addView(Ui.heading(this, "Blinds"), Ui.full(this, 14));
        String[] st = new String[STAKES.length];
        for (int i = 0; i < st.length; i++) st[i] = STAKES[i][0] + "/" + STAKES[i][1];
        root.addView(choices(st, stakeIndex, new Pick() {
            @Override
            public void pick(int i) {
                stakeIndex = i;
                lobby();
            }
        }), Ui.full(this, 6));

        root.addView(Ui.heading(this, "Players at the table"), Ui.full(this, 14));
        root.addView(choices(new String[]{"2", "3", "4", "5", "6"}, sizeChoice - 2, new Pick() {
            @Override
            public void pick(int i) {
                sizeChoice = i + 2;
                lobby();
            }
        }), Ui.full(this, 6));

        int bb = STAKES[stakeIndex][1];
        root.addView(Ui.heading(this, "Buy in"), Ui.full(this, 14));
        String[] bi = new String[BUYIN_BB.length];
        for (int i = 0; i < bi.length; i++) bi[i] = String.format(Locale.US, "%,d", BUYIN_BB[i] * bb);
        root.addView(choices(bi, buyinIndex, new Pick() {
            @Override
            public void pick(int i) {
                buyinIndex = i;
                lobby();
            }
        }), Ui.full(this, 6));

        Button sit = Ui.button(this, "SIT DOWN", true);
        sit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        sit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sitDown();
            }
        });
        root.addView(sit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 64)));
        ((LinearLayout.LayoutParams) sit.getLayoutParams()).topMargin = Ui.dp(this, 18);

        root.addView(Ui.heading(this, "Who you might play"), Ui.full(this, 20));
        for (int p = 0; p < HoldemBot.NAMES.length; p++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.card(this, 16));
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            TextView n = Ui.text(this, HoldemBot.FACES[p] + "  " + HoldemBot.NAMES[p], 16,
                    Ui.TEXT);
            n.setTypeface(Ui.medium());
            card.addView(n);
            card.addView(Ui.text(this, HoldemBot.STYLES[p], 13, Ui.SUB));
            root.addView(card, Ui.full(this, 8));
        }
        Button rules = Ui.button(this, "How to play and hand ranks", false);
        rules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRules();
            }
        });
        root.addView(rules, Ui.full(this, 14));
        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HoldemActivity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 10));
        root.addView(Ui.text(this, "Play money only. Fun Coupons can't be bought with or "
                + "cashed out for real money.", 12, Ui.SUB), Ui.full(this, 14));
    }

    private interface Pick {
        void pick(int i);
    }

    private LinearLayout choices(String[] labels, int chosen, final Pick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            Button bt = Ui.button(this, labels[i], i == chosen);
            bt.setPadding(0, 0, 0, 0);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.pick(k);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            row.addView(bt, lp);
        }
        return row;
    }

    // ================================================================== sitting down

    private void sitDown() {
        int bb = STAKES[stakeIndex][1];
        buyin = BUYIN_BB[buyinIndex] * bb;
        if (Wallet.coupons(this) < buyin) {
            Toast.makeText(this, "You need " + buyin + " coupons for that buy-in.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Wallet.addCoupons(this, -buyin);
        Prefs.putInt(this, SAVED, buyin);
        table = new HoldemTable(rnd);
        table.sb = STAKES[stakeIndex][0];
        table.bb = bb;
        HoldemTable.Seat me = table.seats[ME];
        me.occupied = true;
        me.human = true;
        me.name = "You";
        me.stack = buyin;
        int[] seats = LAYOUT[sizeChoice];
        List<Integer> cast = new ArrayList<>();
        for (int p = 0; p < HoldemBot.NAMES.length; p++) cast.add(p);
        java.util.Collections.shuffle(cast, rnd);
        int c = 0;
        for (int s : seats) {
            if (s == ME) continue;
            seatBot(s, cast.get(c++));
        }
        seated = true;
        standAfterHand = false;
        log.clear();
        buildTable();
        int greeter = seats[1 + rnd.nextInt(seats.length - 1)];
        say(greeter, HoldemBot.JOIN, null);
        later(1600, new Runnable() {
            @Override
            public void run() {
                startHand();
            }
        });
    }

    private void seatBot(int s, int persona) {
        HoldemTable.Seat st = table.seats[s];
        st.occupied = true;
        st.human = false;
        st.persona = persona;
        st.name = HoldemBot.NAMES[persona];
        st.stack = table.bb * (60 + rnd.nextInt(100));
        st.startStack = st.stack;
        st.tilt = 0;
    }

    private int freePersona() {
        List<Integer> free = new ArrayList<>();
        for (int p = 0; p < HoldemBot.NAMES.length; p++) {
            boolean used = false;
            for (HoldemTable.Seat s : table.seats) {
                if (s.occupied && !s.human && s.persona == p) used = true;
            }
            if (!used) free.add(p);
        }
        return free.isEmpty() ? rnd.nextInt(HoldemBot.NAMES.length)
                : free.get(rnd.nextInt(free.size()));
    }

    /** Puts your chips back in the wallet. If a hand is on, you fold first. */
    private void standUp(boolean toLobby) {
        if (!seated || table == null) return;
        HoldemTable.Seat me = table.seats[ME];
        long back = me.stack;
        if (back > 0) Wallet.addCoupons(this, (int) Math.min(Integer.MAX_VALUE, back));
        me.stack = 0;
        Prefs.putInt(this, SAVED, 0);
        long net = back - buyin;
        Stats.log(this, "Hold'em: left the table with " + back + " chips ("
                + (net >= 0 ? "+" : "") + net + ")");
        seated = false;
        token++;
        handler.removeCallbacksAndMessages(null);
        if (toLobby) {
            Toast.makeText(this, String.format(Locale.US, "Cashed out %,d chips.", back),
                    Toast.LENGTH_LONG).show();
            lobby();
        }
    }

    private void saveStack() {
        if (seated) Prefs.putInt(this, SAVED, (int) Math.min(Integer.MAX_VALUE,
                table.seats[ME].stack));
    }

    // ================================================================== the table screen

    private void buildTable() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Texas Hold'em  " + table.sb + "/" + table.bb),
                Ui.full(this, 0));
        tableView = new TableView(this);
        root.addView(tableView, Ui.full(this, 6));

        status = Ui.text(this, "", 15, Ui.TEXT);
        status.setGravity(Gravity.CENTER);
        status.setMinLines(1);
        root.addView(status, Ui.full(this, 6));

        actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        foldBtn = Ui.button(this, "FOLD", false);
        callBtn = Ui.button(this, "CHECK", true);
        raiseBtn = Ui.button(this, "RAISE", false);
        foldBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                human(HoldemTable.FOLD, 0);
            }
        });
        callBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                human(HoldemTable.CALL, 0);
            }
        });
        raiseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openRaise();
            }
        });
        for (Button bt : new Button[]{foldBtn, callBtn, raiseBtn}) {
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            bt.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f);
            int m = Ui.dp(this, 3);
            lp.setMargins(m, 0, m, 0);
            actRow.addView(bt, lp);
        }
        root.addView(actRow, Ui.full(this, 6));

        // The raise slider and quick sizes, shown when you tap RAISE.
        raisePanel = new LinearLayout(this);
        raisePanel.setOrientation(LinearLayout.VERTICAL);
        raisePanel.setBackground(Ui.card(this, 16));
        raisePanel.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        raiseLabel = Ui.text(this, "", 18, Ui.ACCENT);
        raiseLabel.setGravity(Gravity.CENTER);
        raiseLabel.setTypeface(Ui.medium());
        raisePanel.addView(raiseLabel);
        raiseBar = new SeekBar(this);
        raiseBar.setMax(1000);
        raiseBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int v, boolean fromUser) {
                if (!fromUser || table == null) return;
                long lo = table.minRaiseTo(ME);
                long hi = table.maxRaiseTo(ME);
                long to = lo + Math.round((hi - lo) * Math.pow(v / 1000.0, 2));
                long step = Math.max(1, table.bb / 2);
                if (to < hi) to = Math.max(lo, (to / step) * step);
                setRaise(to);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        raisePanel.addView(raiseBar, Ui.full(this, 6));
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        String[] pl = {"Min", "½ Pot", "¾ Pot", "Pot", "ALL IN"};
        final float[] frac = {0, 0.5f, 0.75f, 1f, -1};
        for (int i = 0; i < pl.length; i++) {
            final int k = i;
            Button bt = Ui.button(this, pl[i], false);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presetRaise(frac[k]);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 4);
            presets.addView(bt, lp);
        }
        raisePanel.addView(presets, Ui.full(this, 6));
        LinearLayout confirmRow = new LinearLayout(this);
        confirmRow.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = Ui.button(this, "Back", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                raisePanel.setVisibility(View.GONE);
                actRow.setVisibility(View.VISIBLE);
            }
        });
        Button go = Ui.button(this, "CONFIRM", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                raisePanel.setVisibility(View.GONE);
                actRow.setVisibility(View.VISIBLE);
                human(HoldemTable.RAISE, raiseTo);
            }
        });
        confirmRow.addView(cancel, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 2f);
        gp.leftMargin = Ui.dp(this, 6);
        confirmRow.addView(go, gp);
        raisePanel.addView(confirmRow, Ui.full(this, 8));
        raisePanel.setVisibility(View.GONE);
        root.addView(raisePanel, Ui.full(this, 6));

        // Talk back.
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        String[] said = {"Nice hand 👏", "You're bluffing 🤨", "Ship it 💰", "Cry about it 😭",
                "Get wrecked 🔥"};
        final int[] replies = {HoldemBot.REPLY_NICE, HoldemBot.REPLY_BLUFF,
                HoldemBot.REPLY_SHIP, HoldemBot.REPLY_CRY, HoldemBot.REPLY_WRECK};
        for (int i = 0; i < said.length; i++) {
            final int k = i;
            final String text = said[i];
            Button bt = Ui.button(this, text, false);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bt.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    talkBack(text, replies[k]);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42));
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            quick.addView(bt, lp);
        }
        hs.addView(quick);
        root.addView(hs, Ui.full(this, 10));

        chat = Ui.text(this, "", 13, Ui.TEXT);
        chat.setBackground(Ui.card(this, 14));
        chat.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        chat.setMinLines(5);
        root.addView(chat, Ui.full(this, 8));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        Button stand = Ui.button(this, "Stand up & cash out", false);
        stand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (table.handLive && table.seats[ME].inHand && !table.seats[ME].folded) {
                    standAfterHand = true;
                    Toast.makeText(HoldemActivity.this, "You'll stand up when this hand is "
                            + "over.", Toast.LENGTH_SHORT).show();
                } else {
                    standUp(true);
                }
            }
        });
        bottom.addView(stand, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        Button rules = Ui.button(this, "Rules", false);
        rules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRules();
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(this, 90),
                Ui.dp(this, 48));
        rp.leftMargin = Ui.dp(this, 6);
        bottom.addView(rules, rp);
        root.addView(bottom, Ui.full(this, 10));
        root.addView(Sfx.toggle(this), Ui.full(this, 8));
        root.addView(Ui.text(this, "Play money only.", 12, Ui.SUB), Ui.full(this, 10));
        paint();
    }

    private void paint() {
        if (!seated || tableView == null) return;
        tableView.invalidate();
        boolean mine = table.handLive && table.toAct == ME;
        long owe = table.handLive ? table.toCall(ME) : 0;
        foldBtn.setEnabled(mine && owe > 0);
        foldBtn.setAlpha(mine && owe > 0 ? 1f : 0.35f);
        callBtn.setEnabled(mine);
        callBtn.setAlpha(mine ? 1f : 0.35f);
        callBtn.setText(owe > 0 ? (owe >= table.seats[ME].stack ? "CALL ALL IN" : "CALL " + owe)
                : "CHECK");
        boolean canRaise = mine && table.canRaise(ME);
        raiseBtn.setEnabled(canRaise);
        raiseBtn.setAlpha(canRaise ? 1f : 0.35f);
        raiseBtn.setText(table.currentBet == 0 ? "BET" : "RAISE");
        if (!mine) {
            raisePanel.setVisibility(View.GONE);
            actRow.setVisibility(View.VISIBLE);
        }
        HoldemTable.Seat me = table.seats[ME];
        String s = String.format(Locale.US, "Your chips: %,d", me.stack);
        if (mine) s = "Your turn.  " + s;
        status.setText(s);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int from = Math.max(0, log.size() - 7);
        for (int i = from; i < log.size(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(log.get(i));
        }
        chat.setText(sb);
    }

    private void openRaise() {
        if (!(table.handLive && table.toAct == ME && table.canRaise(ME))) return;
        actRow.setVisibility(View.GONE);
        raisePanel.setVisibility(View.VISIBLE);
        presetRaise(table.currentBet == 0 ? 0.5f : 0);
    }

    private void presetRaise(float frac) {
        long lo = table.minRaiseTo(ME);
        long hi = table.maxRaiseTo(ME);
        long to;
        if (frac < 0) {
            to = hi;
        } else if (frac == 0) {
            to = lo;
        } else {
            long owe = table.currentBet - table.seats[ME].bet;
            to = table.currentBet + Math.round((table.pot() + owe) * frac);
        }
        to = Math.max(lo, Math.min(hi, to));
        setRaise(to);
        double f = hi > lo ? Math.sqrt((to - lo) / (double) (hi - lo)) : 1;
        raiseBar.setProgress((int) Math.round(f * 1000));
    }

    private void setRaise(long to) {
        raiseTo = to;
        boolean all = to >= table.maxRaiseTo(ME);
        raiseLabel.setText(all ? String.format(Locale.US, "ALL IN  %,d", to)
                : String.format(Locale.US, (table.currentBet == 0 ? "Bet %,d" : "Raise to %,d"),
                to));
    }

    // ================================================================== playing

    private void startHand() {
        if (!seated) return;
        for (int i = 0; i < HoldemTable.SEATS; i++) wonAmount[i] = 0;
        raisedByMe = false;
        myFoldedTo = -1;
        HoldemTable.Seat me = table.seats[ME];
        if (me.stack <= 0) {
            outOfChips();
            return;
        }
        if (!table.startHand()) {
            // Everyone else is gone: bring in fresh players.
            refill();
            if (!table.startHand()) {
                lobby();
                return;
            }
        }
        Sfx.play(this, Sfx.CARD);
        saveStack();
        // Now and then someone runs their mouth before the cards even land.
        if (rnd.nextInt(100) < 30) {
            int s = randomBot();
            if (s >= 0) {
                HoldemTable.Seat b = table.seats[s];
                say(s, b.stack > table.bb * 150 ? HoldemBot.UP_BIG : HoldemBot.IDLE, null);
            }
        }
        step();
    }

    /** Moves the hand along: a character acts, the board runs out, or it waits for you. */
    private void step() {
        paint();
        if (!table.handLive) {
            handOver();
            return;
        }
        if (table.runningOut) {
            later(950, new Runnable() {
                @Override
                public void run() {
                    table.runOutStep();
                    Sfx.play(HoldemActivity.this, Sfx.CARD);
                    step();
                }
            });
            return;
        }
        final int who = table.toAct;
        if (who == ME || who < 0) return;
        long delay = 650 + rnd.nextInt(650);
        later(delay, new Runnable() {
            @Override
            public void run() {
                botAct(who);
            }
        });
    }

    private void botAct(int i) {
        if (!table.handLive || table.toAct != i) return;
        int streetBefore = table.street;
        HoldemBot.Move m = HoldemBot.decide(table, i, rnd, TRIALS);
        HoldemTable.Seat s = table.seats[i];
        // Answer your raise.
        if (raisedByMe && rnd.nextInt(100) < 40) {
            say(i, HoldemBot.YOU_RAISED, null);
            raisedByMe = false;
        }
        table.act(i, m.type, m.to);
        if (m.type != HoldemTable.FOLD && m.type != HoldemTable.CHECK) {
            Sfx.play(this, Sfx.CHIPS);
        }
        if (s.allIn && m.type == HoldemTable.RAISE && rnd.nextInt(100) < 70) {
            say(i, HoldemBot.SHOVE, null);
        }
        addLog(null, s.name + ": " + s.lastAction);
        if (table.handLive && table.street != streetBefore) Sfx.play(this, Sfx.CARD);
        step();
    }

    private void human(int type, long to) {
        if (!table.handLive || table.toAct != ME) return;
        int streetBefore = table.street;
        HoldemTable.Seat me = table.seats[ME];
        if (type == HoldemTable.FOLD) myFoldedTo = table.aggressor;
        table.act(ME, type, to);
        if (type == HoldemTable.RAISE) raisedByMe = true;
        if (type != HoldemTable.FOLD && !me.lastAction.equals("Check")) {
            Sfx.play(this, Sfx.CHIPS);
        }
        addLog(null, "You: " + me.lastAction);
        saveStack();
        if (type == HoldemTable.FOLD && myFoldedTo >= 0 && myFoldedTo != ME
                && rnd.nextInt(100) < 65) {
            final int bully = myFoldedTo;
            later(500, new Runnable() {
                @Override
                public void run() {
                    say(bully, HoldemBot.YOU_FOLDED, null);
                }
            });
        }
        if (table.handLive && table.street != streetBefore) Sfx.play(this, Sfx.CARD);
        step();
    }

    private void handOver() {
        HoldemTable.Seat me = table.seats[ME];
        long won = 0;
        int top = -1;
        long topAmt = 0;
        for (HoldemTable.Win w : table.wins) {
            wonAmount[w.seat] += w.amount;
            if (w.seat == ME) won += w.amount;
            if (w.amount > topAmt) {
                topAmt = w.amount;
                top = w.seat;
            }
        }
        wonUntil = SystemClock.uptimeMillis() + 3500;
        // The result in the log.
        for (HoldemTable.Win w : table.wins) {
            HoldemTable.Seat s = table.seats[w.seat];
            addLog(null, String.format(Locale.US, "%s won %,d%s", s.human ? "You" : s.name,
                    w.amount, w.hand.isEmpty() ? "" : " with " + w.hand.toLowerCase(Locale.US)));
        }
        long wagered = me.total;
        if (me.inHand) {
            Stats.bet(this, "poker", (int) Math.min(Integer.MAX_VALUE, wagered),
                    (int) Math.min(Integer.MAX_VALUE, won));
            sitting.round((int) wagered, (int) won);
            if (won > wagered) Stats.add(this, "pokerWon", 1);
        }
        if (won > 0) Sfx.play(this, topAmt >= table.bb * 40 ? Sfx.BIG_WIN : Sfx.WIN);
        else if (me.inHand && wagered > 0) Sfx.play(this, Sfx.LOSE);
        saveStack();
        talkAfterHand(top, topAmt);
        tilt();
        paint();
        later(table.wentToShowdown ? 4200 : 2800, new Runnable() {
            @Override
            public void run() {
                betweenHands();
            }
        });
    }

    /** Busted characters leave (loudly) and new ones sit down. */
    private void betweenHands() {
        boolean changed = false;
        for (int i = 1; i < HoldemTable.SEATS; i++) {
            HoldemTable.Seat s = table.seats[i];
            if (s.occupied && !s.human && s.stack <= 0) {
                say(i, HoldemBot.BUST, null);
                addLog(null, s.name + " left the table.");
                s.occupied = false;
                changed = true;
            }
        }
        if (standAfterHand) {
            standUp(true);
            return;
        }
        if (changed) {
            later(1800, new Runnable() {
                @Override
                public void run() {
                    refill();
                    startHand();
                }
            });
        } else {
            startHand();
        }
    }

    private void refill() {
        for (int s : LAYOUT[sizeChoice]) {
            if (s == ME || table.seats[s].occupied) continue;
            seatBot(s, freePersona());
            addLog(null, table.seats[s].name + " sat down.");
            say(s, HoldemBot.JOIN, null);
        }
        paint();
    }

    private void outOfChips() {
        Prefs.putInt(this, SAVED, 0);
        new AlertDialog.Builder(this)
                .setTitle("You're out of chips")
                .setMessage("Buy back in for " + buyin + " coupons?")
                .setPositiveButton("Buy back in", new android.content.DialogInterface
                        .OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (Wallet.coupons(HoldemActivity.this) < buyin) {
                            Toast.makeText(HoldemActivity.this, "Not enough coupons.",
                                    Toast.LENGTH_LONG).show();
                            seated = false;
                            lobby();
                            return;
                        }
                        Wallet.addCoupons(HoldemActivity.this, -buyin);
                        table.seats[ME].stack = buyin;
                        saveStack();
                        int s = randomBot();
                        if (s >= 0) say(s, HoldemBot.BEAT_YOU, null);
                        startHand();
                    }
                })
                .setNegativeButton("Leave", new android.content.DialogInterface
                        .OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        seated = false;
                        lobby();
                    }
                })
                .setCancelable(false)
                .show();
        int s = randomBot();
        if (s >= 0) say(s, HoldemBot.BEAT_YOU, null);
    }

    /** Bad losses wind characters up; time calms them down. */
    private void tilt() {
        for (int i = 1; i < HoldemTable.SEATS; i++) {
            HoldemTable.Seat s = table.seats[i];
            if (!s.occupied || s.human) continue;
            s.tilt *= 0.82f;
            long lost = s.startStack - s.stack;
            if (lost >= table.bb * 30) s.tilt += 0.45f;
        }
    }

    // ================================================================== the talking

    private int randomBot() {
        List<Integer> bots = new ArrayList<>();
        for (int i = 1; i < HoldemTable.SEATS; i++) {
            if (table.seats[i].occupied && !table.seats[i].human) bots.add(i);
        }
        return bots.isEmpty() ? -1 : bots.get(rnd.nextInt(bots.size()));
    }

    private void say(int seat, int kind, String them) {
        HoldemTable.Seat s = table.seats[seat];
        if (s.human || !s.occupied) return;
        String line = HoldemBot.line(s.persona, kind, rnd, "you", them, 0);
        if (line == null) return;
        bubble[seat] = line;
        bubbleUntil[seat] = SystemClock.uptimeMillis() + 3600;
        addLog(s.name, line);
        paint();
    }

    /** Who says what when a hand ends. Winners always have something to say. */
    private void talkAfterHand(int top, long amt) {
        if (top < 0) return;
        HoldemTable.Seat w = table.seats[top];
        boolean big = amt >= table.bb * 25;
        // The biggest loser among the others.
        int loser = -1;
        long worst = 0;
        for (int i = 0; i < HoldemTable.SEATS; i++) {
            HoldemTable.Seat s = table.seats[i];
            if (!s.inHand || i == top) continue;
            long lost = s.startStack - s.stack;
            if (lost > worst) {
                worst = lost;
                loser = i;
            }
        }
        if (!w.human) {
            int kind;
            if (!table.wentToShowdown && table.aggressor == top && weakHand(top)
                    && rnd.nextInt(100) < 70) {
                kind = HoldemBot.BLUFF_WIN;
            } else if (loser == ME && worst > 0 && rnd.nextInt(100) < 55) {
                kind = HoldemBot.BEAT_YOU;
            } else if (myFoldedTo == top && rnd.nextInt(100) < 40) {
                kind = HoldemBot.YOU_FOLDED;
            } else {
                kind = big ? HoldemBot.GLOAT_BIG : HoldemBot.GLOAT;
            }
            say(top, kind, null);
            // A character who's way up keeps rubbing it in.
            if (w.stack > table.bb * 180 && rnd.nextInt(100) < 35) {
                final int t = top;
                later(1300, new Runnable() {
                    @Override
                    public void run() {
                        say(t, HoldemBot.UP_BIG, null);
                    }
                });
            } else if (loser > 0 && rnd.nextInt(100) < 45) {
                // Or roasts the character they just beat.
                final int t = top;
                final String name = table.seats[loser].name;
                later(1300, new Runnable() {
                    @Override
                    public void run() {
                        say(t, HoldemBot.ROAST, name);
                    }
                });
            }
        }
        // The loser complains (or gets roasted by someone else).
        if (loser > 0 && worst >= table.bb * 8) {
            final int l = loser;
            final boolean beat = table.wentToShowdown && strongHand(loser);
            final String winnerName = w.human ? "you" : w.name;
            later(w.human ? 300 : 2100, new Runnable() {
                @Override
                public void run() {
                    say(l, beat ? HoldemBot.BAD_BEAT : HoldemBot.SALTY, winnerName);
                }
            });
        }
        // When you win, someone at the table can't stand it.
        if (w.human && loser < 0) {
            final int s = randomBot();
            if (s >= 0) {
                later(400, new Runnable() {
                    @Override
                    public void run() {
                        say(s, HoldemBot.SALTY, "you");
                    }
                });
            }
        }
    }

    /** High card or a small pair with what's on the board: a bluff if it won. */
    private boolean weakHand(int seat) {
        HoldemTable.Seat s = table.seats[seat];
        int n = 2 + Math.max(table.shown, 0);
        if (n < 5) return true;
        int[] cards = new int[n];
        cards[0] = s.c1;
        cards[1] = s.c2;
        for (int i = 0; i < table.shown; i++) cards[2 + i] = table.board[i];
        return category(Cards.score(cards)) == 0;
    }

    /** 0 high card, 1 pair, 2 two pair ... 8 straight flush. */
    private static int category(long score) {
        return (int) (score / (13L * 13 * 13 * 13 * 13));
    }

    /** Two pair or better: losing with that stings. */
    private boolean strongHand(int seat) {
        return category(Cards.score(table.seven(seat))) >= 2;
    }

    private void talkBack(String text, final int reply) {
        if (!seated) return;
        bubble[ME] = text;
        bubbleUntil[ME] = SystemClock.uptimeMillis() + 3000;
        addLog("You", text);
        paint();
        // Whoever you're likely talking to answers, and sometimes someone piles on.
        int target = -1;
        long best = -1;
        for (int i = 1; i < HoldemTable.SEATS; i++) {
            if (wonAmount[i] > best && table.seats[i].occupied) {
                best = wonAmount[i];
                target = i;
            }
        }
        if (target < 0 || best <= 0) target = randomBot();
        if (target < 0) return;
        final int t = target;
        later(900, new Runnable() {
            @Override
            public void run() {
                say(t, reply, null);
            }
        });
        if (rnd.nextInt(100) < 45) {
            final int other = randomBot();
            if (other >= 0 && other != target) {
                later(2200, new Runnable() {
                    @Override
                    public void run() {
                        say(other, rnd.nextBoolean() ? HoldemBot.IDLE : reply, null);
                    }
                });
            }
        }
    }

    private void addLog(String name, String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (name != null) {
            sb.append(name).append(": ");
            sb.setSpan(new ForegroundColorSpan(Ui.ACCENT), 0, name.length() + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, name.length() + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(text);
        } else {
            sb.append(text);
            sb.setSpan(new ForegroundColorSpan(Ui.SUB), 0, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        log.add(sb);
        if (log.size() > 60) log.remove(0);
        if (chat != null) paint();
    }

    private void showRules() {
        new AlertDialog.Builder(this)
                .setTitle("How to play")
                .setMessage("Everyone gets two cards of their own. Five shared cards come out "
                        + "in the middle: three (the flop), then one (the turn), then one (the "
                        + "river). The best five-card hand from your two plus the five shared "
                        + "cards wins the pot.\n\n"
                        + "No limit: on your turn you can FOLD, CHECK or CALL, or BET or RAISE "
                        + "any amount up to all your chips. Use the slider or the quick sizes "
                        + "(half the pot, the whole pot, all in). If you're all in and others "
                        + "keep betting, the extra goes into a side pot you can't win, and "
                        + "you can still win the main pot.\n\n"
                        + "The dealer button (D) moves around the table each hand. The two "
                        + "players after it post the small and big blinds.\n\n"
                        + "Hands, best to worst:\nStraight flush\nFour of a kind\nFull house\n"
                        + "Flush\nStraight\nThree of a kind\nTwo pair\nOne pair\nHigh card\n\n"
                        + "The characters can't see your cards. Each works out its chances "
                        + "from its own two cards and the board, then plays its own way: some "
                        + "tight, some wild, some can't fold. Bad losses put them on tilt. "
                        + "No rake: play better than them and you come out ahead.")
                .setPositiveButton("Close", null)
                .show();
    }

    // ================================================================== the table view

    /** The felt, the seats, the cards, the chips and the speech bubbles. */
    final class TableView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        /** Where each seat sits, as a share of the view's width and height. */
        private final float[][] pos = {{0.5f, 0.88f}, {0.1f, 0.63f}, {0.12f, 0.24f},
                {0.5f, 0.09f}, {0.88f, 0.24f}, {0.9f, 0.63f}};
        private final String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q",
                "K", "A"};
        private final String[] suits = {"♠", "♥", "♦", "♣"};

        TableView(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, (int) (width * 1.12f));
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (table == null) return;
            float w = getWidth();
            float h = getHeight();
            long now = SystemClock.uptimeMillis();
            float d = getResources().getDisplayMetrics().density;

            // Rail and felt.
            rect.set(w * 0.06f, h * 0.13f, w * 0.94f, h * 0.8f);
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0xFF6D4C2F,
                    0xFF3E2A18, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint);
            rect.inset(w * 0.035f, w * 0.035f);
            paint.setShader(new RadialGradient(w / 2, h * 0.42f, w * 0.55f, 0xFF1F7A4D,
                    0xFF0D3B25, Shader.TileMode.CLAMP));
            cv.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * d);
            paint.setColor(0x55E6B869);
            rect.inset(w * 0.02f, w * 0.02f);
            cv.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint);
            paint.setStyle(Paint.Style.FILL);

            // Board and pot.
            float cw = w * 0.12f;
            float cy = h * 0.43f;
            for (int i = 0; i < 5; i++) {
                float x = w / 2 + (i - 2) * cw * 1.08f;
                if (table.handLive || table.street == 4) {
                    if (i < table.shown) drawCard(cv, table.board[i], x, cy, cw, true);
                    else outline(cv, x, cy, cw);
                } else {
                    outline(cv, x, cy, cw);
                }
            }
            long pot = table.handLive ? table.pot() : 0;
            if (pot > 0) {
                text(cv, String.format(Locale.US, "POT  %,d", pot), w / 2, cy - cw * 1.0f,
                        13 * d, 0xFFFFE082, true);
            }

            // Seats.
            for (int i = 0; i < HoldemTable.SEATS; i++) {
                HoldemTable.Seat s = table.seats[i];
                if (!s.occupied) continue;
                drawSeat(cv, i, s, w, h, d, now);
            }
            // Bubbles on top of everything.
            for (int i = 0; i < HoldemTable.SEATS; i++) {
                if (bubble[i] != null && now < bubbleUntil[i]) {
                    drawBubble(cv, bubble[i], pos[i][0] * w, pos[i][1] * h, w, h, d, i);
                }
            }
            boolean anim = false;
            for (int i = 0; i < HoldemTable.SEATS; i++) anim |= now < bubbleUntil[i];
            anim |= now < wonUntil || (table.handLive && table.toAct >= 0);
            if (anim) postInvalidateDelayed(60);
        }

        private void drawSeat(Canvas cv, int i, HoldemTable.Seat s, float w, float h, float d,
                              long now) {
            float x = pos[i][0] * w;
            float y = pos[i][1] * h;
            float r = w * 0.075f;
            boolean out = !s.inHand || s.folded;
            boolean turn = table.handLive && table.toAct == i;

            // Bet in front of the seat, toward the middle.
            if (table.handLive && s.bet > 0) {
                float bx = x + (w / 2 - x) * 0.42f;
                float by = y + (h * 0.43f - y) * 0.42f;
                chip(cv, bx - 9 * d, by, 7 * d);
                text(cv, String.format(Locale.US, "%,d", s.bet), bx + 10 * d, by,
                        11 * d, 0xFFFFFFFF, true);
            }
            // Dealer button.
            if (i == table.button && table.button >= 0) {
                float bx = x + (w / 2 - x) * 0.24f + 18 * d;
                float by = y + (h * 0.43f - y) * 0.24f;
                paint.setColor(0xFFFFFFFF);
                cv.drawCircle(bx, by, 8 * d, paint);
                text(cv, "D", bx, by, 10 * d, 0xFF000000, true);
            }

            // Their cards.
            if (s.inHand && !s.folded && s.c1 >= 0) {
                if (s.human) {
                    float ccw = w * 0.14f;
                    float cyy = y - r - ccw * 0.75f;
                    drawCard(cv, s.c1, x - ccw * 0.55f, cyy, ccw, true);
                    drawCard(cv, s.c2, x + ccw * 0.55f, cyy, ccw, true);
                    if (table.shown >= 3) {
                        int[] have = new int[2 + table.shown];
                        have[0] = s.c1;
                        have[1] = s.c2;
                        for (int k = 0; k < table.shown; k++) have[2 + k] = table.board[k];
                        String name = Cards.handName(Cards.score(have));
                        float hx = x + ccw * 1.3f + w * 0.1f;
                        rect.set(hx - w * 0.11f, cyy - 10 * d, hx + w * 0.11f, cyy + 10 * d);
                        paint.setColor(0xCC10131C);
                        cv.drawRoundRect(rect, 8 * d, 8 * d, paint);
                        text(cv, name, hx, cyy, 11 * d, 0xFFFFE082, true);
                    }
                } else {
                    float ccw = w * 0.075f;
                    boolean up = s.shown && table.street == 4;
                    float cx = x + (x < w / 2 ? r + ccw * 0.2f : -r - ccw * 1.3f);
                    if (Math.abs(x - w / 2) < 1) cx = x + r + ccw * 0.1f;
                    float cyy = y - r * 0.2f;
                    if (up) {
                        // Shown at the showdown: bigger, so you can read them.
                        float big = w * 0.1f;
                        float sx = x + (w / 2 - x) * 0.32f;
                        float sy = y + (h * 0.43f - y) * 0.32f;
                        drawCard(cv, s.c1, sx - big * 0.55f, sy, big, true);
                        drawCard(cv, s.c2, sx + big * 0.55f, sy, big, true);
                    } else {
                        drawCard(cv, -1, cx + ccw * 0.5f, cyy, ccw, false);
                        drawCard(cv, -1, cx + ccw * 0.95f, cyy + 2 * d, ccw, false);
                    }
                }
            }

            // Avatar.
            if (turn) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
                paint.setColor(0xFFFFD54F);
                paint.setAlpha((int) (120 + 135 * pulse));
                cv.drawCircle(x, y, r * 1.22f, paint);
                paint.setAlpha(255);
            }
            if (now < wonUntil && wonAmount[i] > 0) {
                paint.setColor(0xFF66D19E);
                cv.drawCircle(x, y, r * 1.28f, paint);
            }
            paint.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.4f,
                    s.human ? 0xFF4A6FA5 : 0xFF5A4A6A, 0xFF1A1A28, Shader.TileMode.CLAMP));
            cv.drawCircle(x, y, r, paint);
            paint.setShader(null);
            String face = s.human ? "🙂" : HoldemBot.FACES[Math.max(0, s.persona)];
            text(cv, face, x, y - r * 0.05f, r * 1.05f, out ? 0x6EFFFFFF : 0xFFFFFFFF, false);

            // Name and chips.
            float ny = y + r + 9 * d;
            rect.set(x - w * 0.12f, ny - 8 * d, x + w * 0.12f, ny + 22 * d);
            paint.setColor(0xCC10131C);
            cv.drawRoundRect(rect, 8 * d, 8 * d, paint);
            text(cv, s.name, x, ny, 11 * d, out ? 0xFF8A8FA0 : 0xFFFFFFFF, true);
            String chips = s.allIn && s.inHand && !s.folded ? "ALL IN"
                    : String.format(Locale.US, "%,d", s.stack);
            text(cv, chips, x, ny + 13 * d, 11 * d, s.allIn && s.inHand ? 0xFFFF7043
                    : 0xFFFFE082, true);
            // What they last did.
            if (table.handLive && !s.human && !s.lastAction.isEmpty()) {
                text(cv, s.lastAction, x, y - r - 8 * d, 10 * d, s.folded ? 0xFF8A8FA0
                        : 0xFFB3E5FC, true);
            }
            // Winnings float up.
            if (now < wonUntil && wonAmount[i] > 0) {
                float k = 1 - (wonUntil - now) / 3500f;
                text(cv, String.format(Locale.US, "+%,d", wonAmount[i]), x,
                        y - r - 20 * d - k * 18 * d, 15 * d, 0xFF69F0AE, true);
            }
        }

        private void drawBubble(Canvas cv, String text, float x, float y, float w, float h,
                                float d, int seat) {
            paint.setTextSize(12 * d);
            paint.setFakeBoldText(false);
            float maxW = w * 0.46f;
            List<String> lines = wrap(text, maxW - 16 * d);
            float lh = 15 * d;
            float bw = 0;
            for (String l : lines) bw = Math.max(bw, paint.measureText(l));
            bw += 16 * d;
            float bh = lines.size() * lh + 10 * d;
            // Above the seat, or below it for the top seats; kept on screen.
            boolean below = y < h * 0.3f;
            float by = below ? y + w * 0.075f + 34 * d : y - w * 0.075f - bh - (seat == ME
                    ? w * 0.2f : 10 * d);
            float bx = Math.max(4 * d, Math.min(w - bw - 4 * d, x - bw / 2));
            rect.set(bx, by, bx + bw, by + bh);
            paint.setColor(seat == ME ? 0xF04A6FA5 : 0xF7FFFFFF);
            cv.drawRoundRect(rect, 10 * d, 10 * d, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(seat == ME ? 0xFFFFFFFF : 0xFF111111);
            for (int i = 0; i < lines.size(); i++) {
                cv.drawText(lines.get(i), bx + 8 * d, by + 5 * d + lh * (i + 1) - 4 * d, paint);
            }
            paint.setTextAlign(Paint.Align.CENTER);
        }

        private List<String> wrap(String text, float maxW) {
            List<String> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (paint.measureText(test) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) out.add(line.toString());
            return out;
        }

        private void drawCard(Canvas cv, int card, float x, float y, float cw, boolean up) {
            float ch = cw * 1.4f;
            rect.set(x - cw / 2, y - ch / 2, x + cw / 2, y + ch / 2);
            float rr = cw * 0.12f;
            paint.setColor(0x66000000);
            cv.save();
            cv.translate(cw * 0.04f, cw * 0.06f);
            cv.drawRoundRect(rect, rr, rr, paint);
            cv.restore();
            if (!up || card < 0) {
                paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        0xFFB71C1C, 0xFF6A0F0F, Shader.TileMode.CLAMP));
                cv.drawRoundRect(rect, rr, rr, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(cw * 0.05f);
                paint.setColor(0xFFFFFFFF);
                rect.inset(cw * 0.1f, cw * 0.1f);
                cv.drawRoundRect(rect, rr * 0.6f, rr * 0.6f, paint);
                paint.setStyle(Paint.Style.FILL);
                return;
            }
            paint.setColor(0xFFFDFDFD);
            cv.drawRoundRect(rect, rr, rr, paint);
            int suit = Cards.suit(card);
            int color = suit == 1 || suit == 2 ? 0xFFD32F2F : 0xFF111111;
            text(cv, ranks[Cards.rank(card)], x, y - ch * 0.17f, cw * 0.5f, color, true);
            text(cv, suits[suit], x, y + ch * 0.2f, cw * 0.55f, color, false);
        }

        private void outline(Canvas cv, float x, float y, float cw) {
            float ch = cw * 1.4f;
            rect.set(x - cw / 2, y - ch / 2, x + cw / 2, y + ch / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cw * 0.03f);
            paint.setColor(0x33FFFFFF);
            cv.drawRoundRect(rect, cw * 0.12f, cw * 0.12f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void chip(Canvas cv, float x, float y, float r) {
            paint.setColor(0xFFE53935);
            cv.drawCircle(x, y, r, paint);
            paint.setColor(0xFFFFFFFF);
            cv.drawCircle(x, y, r * 0.55f, paint);
            paint.setColor(0xFFE53935);
            cv.drawCircle(x, y, r * 0.4f, paint);
        }

        private void text(Canvas cv, String t, float x, float y, float size, int color,
                          boolean bold) {
            paint.setShader(null);
            paint.setTextSize(size);
            paint.setFakeBoldText(bold);
            paint.setColor(color);
            cv.drawText(t, x, y - (paint.ascent() + paint.descent()) / 2, paint);
            paint.setFakeBoldText(false);
        }
    }
}
