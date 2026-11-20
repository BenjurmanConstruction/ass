package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Random;

/**
 * One Slot Parlor machine. The math comes from SlotMath; this screen takes your bet,
 * plays the outcome back with reels, tumbles, coins or the wheel, and pays the win.
 * Play money only.
 */
public class MachineActivity extends Activity {
    private static final int[] BETS = {10, 20, 50, 100, 200};
    private static final int[] BETS_HIGH = {10, 20, 50, 100, 200, 500};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private int m;
    private Stats.Sitting sitting;
    private MachineView view;
    private TextView message;
    private TextView balance;
    private TextView betText;
    private TextView winMeter;
    private Button spinBtn;
    private Button autoBtn;
    private int betIndex = 0;
    private boolean busy;
    private int autoLeft;
    private long shownWin;
    private long rollTarget;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!ParlorActivity.owned(this)) {
            Toast.makeText(this, "Unlock the Slot Parlor first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        m = Math.max(0, Math.min(SlotMath.NAMES.length - 1,
                getIntent().getIntExtra("machine", 0)));
        sitting = new Stats.Sitting(SlotMath.NAMES[m], "spin");

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        root.addView(Ui.topBar(this, SlotMath.NAMES[m]), Ui.full(this, 0));

        view = new MachineView(this, m);
        android.graphics.drawable.GradientDrawable fg = Ui.round(this, CABINET[m], 22);
        fg.setStroke(Ui.dp(this, 3), TRIM[m]);
        view.setBackground(fg);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!busy) {
                    autoLeft = 0;
                    spin();
                }
            }
        });
        root.addView(view, Ui.full(this, 10));

        winMeter = Ui.text(this, "", 26, Ui.ACCENT);
        winMeter.setTypeface(Ui.medium());
        winMeter.setGravity(Gravity.CENTER);
        root.addView(winMeter, Ui.full(this, 6));
        message = Ui.text(this, SlotMath.BLURBS[m] + "\nTap the machine or SPIN. Tap again to stop "
                + "the reels.", 15, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        root.addView(message, Ui.full(this, 2));
        balance = Ui.text(this, "", 15, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 4));

        LinearLayout betRow = new LinearLayout(this);
        betRow.setOrientation(LinearLayout.HORIZONTAL);
        betRow.setGravity(Gravity.CENTER_VERTICAL);
        betText = Ui.text(this, "", 16, Ui.TEXT);
        betText.setGravity(Gravity.CENTER);
        betRow.addView(step("−", -1), new LinearLayout.LayoutParams(Ui.dp(this, 56),
                Ui.dp(this, 54)));
        betRow.addView(betText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        betRow.addView(step("+", 1), new LinearLayout.LayoutParams(Ui.dp(this, 56),
                Ui.dp(this, 54)));
        root.addView(betRow, Ui.full(this, 8));

        LinearLayout spinRow = new LinearLayout(this);
        spinRow.setOrientation(LinearLayout.HORIZONTAL);
        autoBtn = Ui.button(this, "AUTO\n10", false);
        autoBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        autoBtn.setPadding(0, 0, 0, 0);
        autoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (autoLeft > 0) {
                    autoLeft = 0;
                    paint();
                    return;
                }
                autoLeft = 10;
                paint();
                if (!busy) spin();
            }
        });
        spinRow.addView(autoBtn, new LinearLayout.LayoutParams(Ui.dp(this, 76),
                Ui.dp(this, 72)));
        spinBtn = Ui.button(this, "SPIN", true);
        spinBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        spinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoLeft = 0;
                // While the reels are spinning, SPIN stops them.
                if (busy) view.slam();
                else spin();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f);
        sp.leftMargin = Ui.dp(this, 8);
        spinRow.addView(spinBtn, sp);
        root.addView(spinRow, Ui.full(this, 8));

        root.addView(Sfx.toggle(this), Ui.full(this, 10));
        Button table = Ui.button(this, "Pay table and odds", false);
        table.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTable();
            }
        });
        root.addView(table, Ui.full(this, 10));
        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MachineActivity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 10));
        root.addView(Ui.text(this, "Play money only. Fun Coupons can't be bought with or "
                + "cashed out for real money.", 12, Ui.SUB), Ui.full(this, 14));

        setContentView(scroll);
        Bg.apply(this, scroll);
        view.showIdle(rnd);
        paint();
    }

    private Button step(String label, final int dir) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bt.setPadding(0, 0, 0, 0);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) return;
                betIndex = Math.max(0, Math.min(bets().length - 1, betIndex + dir));
                paint();
            }
        });
        return bt;
    }

    private int[] bets() {
        return Tree.owned(this, "highroller") ? BETS_HIGH : BETS;
    }

    private int bet() {
        if (betIndex >= bets().length) betIndex = bets().length - 1;
        return bets()[betIndex];
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (balance != null) paint();
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoLeft = 0;
        if (sitting != null) sitting.close(this);
        Sfx.stop(Sfx.SPIN5);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (view != null) view.cancel();
        super.onDestroy();
    }

    private void paint() {
        balance.setText(String.format(Locale.US, "%,d Fun Coupons", Wallet.coupons(this)));
        betText.setText(String.format(Locale.US, "Bet %,d", bet()));
        autoBtn.setText(autoLeft > 0 ? "STOP\n" + autoLeft : "AUTO\n10");
        spinBtn.setAlpha(busy ? 0.5f : 1f);
    }

    private long coupons(long units) {
        return Math.round(units * (double) bet() / SlotMath.UNITS[m]);
    }

    private void spin() {
        if (busy) return;
        final int bet = bet();
        if (Wallet.coupons(this) < bet) {
            autoLeft = 0;
            message.setText("Not enough coupons for a bet of " + bet
                    + ".\nLower the bet or get more in the Wallet.");
            paint();
            return;
        }
        Wallet.addCoupons(this, -bet);
        if (autoLeft > 0) autoLeft--;
        busy = true;
        shownWin = 0;
        rollTarget = 0;
        winMeter.setText("");
        message.setText("Good luck!");
        paint();
        final SlotMath.Outcome o = SlotMath.play(m, rnd, true);
        view.setBet(bet);
        view.play(o, new MachineView.Listener() {
            long units;

            @Override
            public void frameWon(long u) {
                units += u;
                rollTo(coupons(units));
            }

            @Override
            public void featureStarted() {
                autoLeft = 0;
                message.setText("BONUS! Tap the machine to play it.");
                Sfx.play(MachineActivity.this, Sfx.JACKPOT);
                paint();
            }

            @Override
            public void done() {
                settle(o, bet);
            }
        });
    }

    /** Counts the win meter up to a new total, like a real machine. */
    private void rollTo(long target) {
        rollTarget = target;
        handler.removeCallbacks(roller);
        handler.post(roller);
    }

    private final Runnable roller = new Runnable() {
        @Override
        public void run() {
            if (shownWin >= rollTarget) {
                shownWin = rollTarget;
                winMeter.setText(shownWin > 0 ? String.format(Locale.US, "WIN %,d", shownWin)
                        : "");
                return;
            }
            long step = Math.max(1, (rollTarget - shownWin) / 12);
            shownWin = Math.min(rollTarget, shownWin + step);
            winMeter.setText(String.format(Locale.US, "WIN %,d", shownWin));
            handler.postDelayed(this, 35);
        }
    };

    private void settle(SlotMath.Outcome o, int bet) {
        long won = coupons(o.total);
        if (won > 0) Wallet.addCoupons(this, (int) Math.min(Integer.MAX_VALUE, won));
        rollTo(won);
        double x = won / (double) bet;
        if (won == 0) {
            message.setText("No win.");
        } else if (won < bet) {
            message.setText(String.format(Locale.US, "Won %,d back.", won));
        } else {
            message.setText(String.format(Locale.US, "Winner! %,d coupons (%.1fx your bet)",
                    won, x));
        }
        // Wins of 10x and up already had their celebration on the machine.
        if (x >= 10) {
            // Nothing more to play.
        } else if (x >= 3) {
            Sfx.play(this, Sfx.BIG_WIN);
        } else if (won > 0) {
            Sfx.play(this, Sfx.WIN);
        }
        Stats.bet(this, "parlor", bet, (int) Math.min(Integer.MAX_VALUE, won));
        sitting.round(bet, (int) Math.min(Integer.MAX_VALUE, won));
        busy = false;
        // A big feature stops auto play so you get to enjoy it.
        if (o.feature) autoLeft = 0;
        paint();
        if (autoLeft > 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing() && autoLeft > 0 && !busy) spin();
                }
            }, won > 0 ? 1100 : 450);
        }
    }

    // ------------------------------------------------------------------ pay table

    private void showTable() {
        StringBuilder sb = new StringBuilder();
        switch (m) {
            case SlotMath.CLASSIC:
                sb.append("Five lines: the three rows and both diagonals. Your bet is split "
                        + "evenly across them, so each line is a fifth of your bet. Pays are "
                        + "times that line bet.\n\n");
                sb.append("🔥🔥🔥 three wilds  ").append(SlotMath.CLASSIC_WILDS).append('\n');
                String[] n = {"🍒🍒🍒 cherries", "BAR BAR BAR", "2BAR x3", "3BAR x3",
                        "7 7 7"};
                for (int i = 4; i >= 0; i--) {
                    sb.append(n[i]).append("  ").append(SlotMath.CLASSIC_PAY[i]).append('\n');
                }
                sb.append("Any three bars  ").append(SlotMath.CLASSIC_ANY_BAR).append('\n');
                sb.append("Two cherries  ").append(SlotMath.CLASSIC_CHERRY[2])
                        .append("   One cherry  ").append(SlotMath.CLASSIC_CHERRY[1])
                        .append("\n\n🔥 WILD stands in for any symbol and doubles the win; "
                                + "two wilds make it 4x.\n\n"
                                + "Odds: something pays about 1 spin in 2.3, and about 1 spin "
                                + "in 3.4 pays more than your bet. Pays back 98.3% over the "
                                + "long run, worked out exactly from all 74,088 ways the "
                                + "reels can stop.");
                break;
            case SlotMath.WAYS:
                sb.append("1,024 ways: any symbol on each reel next to the one before it, "
                        + "starting from the left, wins. More of the same symbol on a reel "
                        + "multiplies the win. Pays below are for a 40-coupon bet per way "
                        + "and scale with your bet.\n\n");
                String[] w = {"🦅", "🐺", "🐻", "🦌", "A", "K", "Q", "J", "10", "9"};
                for (int i = 0; i < 10; i++) {
                    sb.append(w[i]).append("   ").append(SlotMath.WAYS_PAY[i][0]).append(" / ")
                            .append(SlotMath.WAYS_PAY[i][1]).append(" / ")
                            .append(SlotMath.WAYS_PAY[i][2]).append('\n');
                }
                sb.append("\n🌅 WILD appears on reels 2, 3 and 4 only.\n🌕 Three, four or "
                        + "five gold moons anywhere pay 4x, 10x or 50x your bet and win 8, "
                        + "15 or 20 free spins. Then you pick: all the spins with wins x1, "
                        + "half of them with wins x2, or a third of them with wins x3. In "
                        + "free spins the reels carry more wilds, each wild is 2x or 3x, and "
                        + "wins through several wilds multiply together.\n\nOdds: something "
                        + "pays about 1 spin in 1.7, and about 1 in 5.7 pays more than your "
                        + "bet. Free spins about 1 in 86 spins, worth about 26x your bet on "
                        + "average (the three picks come out about the same). Pays back "
                        + "about 98.1% (4 million test spins).");
                break;
            case SlotMath.COINS:
                sb.append("243 ways from the left. Pays for a 20-coupon bet per way, scaled "
                        + "to your bet:\n\n");
                String[] v = {"🐉", "🐯", "🐟", "🏮", "A", "K", "Q", "J", "10"};
                for (int i = 0; i < v.length; i++) {
                    sb.append(v[i]).append("   ").append(SlotMath.VAULT_PAY[i][0])
                            .append(" / ").append(SlotMath.VAULT_PAY[i][1]).append(" / ")
                            .append(SlotMath.VAULT_PAY[i][2]).append('\n');
                }
                sb.append("\n🪙 HOLD & SPIN: land 6 or more coins. They lock in place and "
                        + "you get 3 respins; every new coin resets the respins to 3. When "
                        + "they run out, every coin pays its value. Coins are worth half to "
                        + "12.5x your bet, or a jackpot:\nMINI 10x  •  MINOR 25x  •  MAJOR "
                        + "100x  •  GRAND 1,000x (fill all 15 spots)\n\nOdds: about 1 spin "
                        + "in 4.2 pays more than your bet. Hold & spin about 1 in 51 spins, "
                        + "worth about 22x on average. Jackpots, per spin: Mini about 1 in "
                        + "270, Minor 1 in 600, Major 1 in 6,200, Grand about 1 in 110,000. "
                        + "Pays back about 99% (4 million test spins).");
                break;
            case SlotMath.WHEEL:
                sb.append("One line, through the middle. Pays are times your bet.\n\n");
                sb.append("2X 2X 2X three wilds  ").append(SlotMath.WHEEL_WILDS).append('\n');
                String[] k = {"BAR x3", "2BAR x3", "3BAR x3", "7 7 7"};
                for (int i = 3; i >= 0; i--) {
                    sb.append(k[i]).append("  ").append(SlotMath.WHEEL_PAY[i]).append('\n');
                }
                sb.append("Any three bars  ").append(SlotMath.WHEEL_ANY_BAR)
                        .append("\n\n2X WILD stands in for any symbol and doubles the win.\n"
                                + "🎡 A WHEEL symbol on the line (third reel) spins the "
                                + "bonus wheel:\n");
                int tw = 0;
                for (int x : SlotMath.WHEEL_WEIGHTS) tw += x;
                for (int val : new int[]{1000, 250, 100, 75, 50, 40, 30, 25, 20, 15, 10}) {
                    int wsum = 0;
                    for (int j = 0; j < SlotMath.WHEEL_SEGMENTS.length; j++) {
                        if (SlotMath.WHEEL_SEGMENTS[j] == val) wsum += SlotMath.WHEEL_WEIGHTS[j];
                    }
                    if (wsum > 0) {
                        sb.append(String.format(Locale.US, "%,dx  •  %.1f%% of wheel spins%n",
                                val, 100.0 * wsum / tw));
                    }
                }
                sb.append("\nOdds: the wheel comes up about 1 spin in 49 and pays about 26x "
                        + "your bet on average. Something pays about 1 spin in 10. Pays "
                        + "back 98.3%, worked out exactly from every reel stop.");
                break;
            default:
                sb.append("Gems pay anywhere: 8 or more of the same gem on the board wins, "
                        + "wherever they are. Winning gems burst, the rest fall, new ones "
                        + "drop in, and it can win again. Pays for a 20-coupon bet, scaled "
                        + "to your bet (8-9 / 10-11 / 12+ gems):\n\n");
                String[] g = {"Ruby", "Emerald", "Sapphire", "Amethyst", "Topaz", "💗",
                        "⭐", "🌙"};
                for (int i = 0; i < g.length; i++) {
                    sb.append(g[i]).append("   ").append(SlotMath.GEM_PAY[i][0])
                            .append(" / ").append(SlotMath.GEM_PAY[i][1]).append(" / ")
                            .append(SlotMath.GEM_PAY[i][2]).append('\n');
                }
                sb.append("\n💫 Four or more shooting stars pay 3x, 5x or 100x your bet and "
                        + "give ").append(SlotMath.GEM_FREE).append(" free spins. The free "
                        + "spin multiplier starts at x2 and goes up by one with every "
                        + "winning tumble, and it never resets during the round. Four "
                        + "stars in free spins add 5 more.\n\nOdds: something pays about 1 "
                        + "spin in 2.2, and about 1 in 4.9 pays more than your bet. Free "
                        + "spins about 1 in 138 spins, worth about 49x your bet on average. "
                        + "Pays back about 98.1% (5 million test spins).");
                break;
        }
        sb.append("\n\nPlay money only. It's for fun.");
        new AlertDialog.Builder(this)
                .setTitle(SlotMath.NAMES[m] + ": pays and odds")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    static final int[] CABINET = {0xEE3A0D0D, 0xEE1E1408, 0xEE3A0A12, 0xEE0D1F3A, 0xEE1A0D33};
    static final int[] TRIM = {0xFFFF7043, 0xFFFFB300, 0xFFFFD54F, 0xFF4FC3F7, 0xFFE040FB};

    // ================================================================== the machine view

    /** Draws a machine and plays an outcome back, frame by frame. */
    static final class MachineView extends View {
        interface Listener {
            void frameWon(long units);

            /** A bonus has started: stop auto play, the player takes over. */
            void featureStarted();

            void done();
        }

        private static final int IDLE = 0, SPIN = 1, DROP = 2, CASCADE = 3, HOLD = 4,
                WHEEL = 5, SHOW = 6, WAIT = 7, CHOOSE = 8, PARTY = 9;
        private static final int COINS_FALLING = 46;

        private final int machine;
        private final int rows;
        private final int cols;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random fx = new Random();

        private char[][] shown;
        private boolean[][] lit;
        private long[][] coins;
        private int freeLeft = -1;
        private int mult = 1;
        private int respins;
        private String banner;
        private long bannerAt;
        private int bet = 20;

        private SlotMath.Outcome outcome;
        private Listener listener;
        private int index;
        private int phase = IDLE;
        private long phaseStart;
        private long phaseLength;
        private SlotMath.Frame frame;
        private long[] stopAt;
        private char[][] before;
        private boolean[][] beforeLit;
        private long[][] coinsBefore;
        /** For tumbles: where each new cell falls from (row, can be negative). */
        private float[][] fromRow;
        private float wheelFrom;
        private float wheelTo;
        private float wheelAngle;
        private int token;
        /** Bumped whenever a frame's landing is rescheduled (tap to stop). */
        private int phaseId;
        private String waitText;
        private final RectF[] choiceBox = {new RectF(), new RectF(), new RectF()};
        private String partyTitle;
        private long partyCoupons;
        private final float[] px = new float[COINS_FALLING];
        private final float[] py = new float[COINS_FALLING];
        private final float[] pv = new float[COINS_FALLING];
        private final float[] pr = new float[COINS_FALLING];

        MachineView(Context c, int machine) {
            super(c);
            this.machine = machine;
            rows = SlotMath.ROWS[machine];
            cols = SlotMath.COLS[machine];
            shown = new char[rows][cols];
            lit = new boolean[rows][cols];
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void setBet(int b) {
            bet = b;
        }

        void cancel() {
            token++;
            handler.removeCallbacksAndMessages(null);
        }

        /** Fills the screen with a random-looking resting board. */
        void showIdle(Random rnd) {
            SlotMath.Outcome o = SlotMath.play(machine, rnd);
            SlotMath.Frame f = o.frames.get(0);
            shown = f.grid;
            coins = machine == SlotMath.COINS ? f.coins : null;
            lit = new boolean[rows][cols];
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            float cell = (w - pad() * 2) / (float) cols;
            float cellH = machine == SlotMath.GEMS ? cell : cell * (cols == 3 ? 0.8f : 0.95f);
            int h = (int) (band(w) + cellH * rows + pad() * 2);
            setMeasuredDimension(w, h);
        }

        private float pad() {
            return getResources().getDisplayMetrics().density * 10;
        }

        private float band(float w) {
            return w * 0.13f;
        }

        // ---------------------------------------------------------- playback

        void play(SlotMath.Outcome o, Listener l) {
            final int mine = ++token;
            outcome = o;
            listener = l;
            index = -1;
            freeLeft = -1;
            mult = 1;
            respins = 0;
            banner = null;
            nextFrame(mine);
        }

        private void after(long ms, final int mine, final Runnable r) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mine == token) r.run();
                }
            }, ms);
        }

        private void nextFrame(final int mine) {
            index++;
            if (index >= outcome.frames.size()) {
                double x = outcome.total / (double) SlotMath.UNITS[machine];
                if (x >= 10) {
                    startParty(mine, x);
                    return;
                }
                finish();
                return;
            }
            frame = outcome.frames.get(index);
            before = shown;
            beforeLit = lit;
            coinsBefore = coins;
            lit = new boolean[rows][cols];
            phaseStart = SystemClock.uptimeMillis();
            boolean freeSpin = frame.freeLeft >= 0;
            switch (frame.kind) {
                case SlotMath.REELS:
                    if (machine == SlotMath.GEMS) {
                        phase = DROP;
                        phaseLength = 520 + cols * 70L;
                        shown = frame.grid;
                        coins = null;
                    } else {
                        phase = SPIN;
                        stopAt = new long[cols];
                        long first = freeSpin ? 450 : (cols == 3 ? 650 : 550);
                        long gap = freeSpin ? 140 : (cols == 3 ? 300 : 210);
                        for (int c = 0; c < cols; c++) {
                            stopAt[c] = first + gap * c;
                            // Suspense: the last reels keep spinning a while longer.
                            if (frame.tease && c >= cols - 2) {
                                stopAt[c] += 1100 + (c - (cols - 2)) * 500L;
                            }
                        }
                        phaseLength = stopAt[cols - 1] + 200;
                        Sfx.play(getContext(), Sfx.SPIN5);
                        for (int c = 0; c < cols; c++) {
                            after(stopAt[c], mine, new Runnable() {
                                @Override
                                public void run() {
                                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                }
                            });
                        }
                    }
                    break;
                case SlotMath.CASCADE:
                    phase = CASCADE;
                    phaseLength = 760;
                    fromRow = new float[rows][cols];
                    for (int c = 0; c < cols; c++) {
                        int write = rows - 1;
                        for (int r = rows - 1; r >= 0; r--) {
                            if (beforeLit == null || !beforeLit[r][c]) fromRow[write--][c] = r;
                        }
                        int above = 1;
                        while (write >= 0) fromRow[write--][c] = -(above++);
                    }
                    shown = frame.grid;
                    break;
                case SlotMath.HOLD:
                    phase = HOLD;
                    boolean firstHold = coinsBefore == null || before == null
                            || index > 0 && outcome.frames.get(index - 1).kind != SlotMath.HOLD;
                    phaseLength = firstHold ? 1500 : (frame.text != null ? 1300 : 950);
                    break;
                case SlotMath.WHEEL_SPIN:
                    phase = WHEEL;
                    phaseLength = 5200;
                    int n = SlotMath.WHEEL_SEGMENTS.length;
                    float segDeg = 360f / n;
                    wheelFrom = wheelAngle % 360f;
                    // The pointer is at the top; land the chosen segment under it, a little
                    // off center so it doesn't look mechanical.
                    float target = -(frame.segment * segDeg) + (fx.nextFloat() - 0.5f)
                            * segDeg * 0.6f;
                    float turns = 360f * 6;
                    float delta = ((target - wheelFrom) % 360f + 360f) % 360f;
                    wheelTo = wheelFrom + turns + delta;
                    Sfx.play(getContext(), Sfx.SPIN);
                    break;
                case SlotMath.CHOICE:
                    // Thunder Plains: the player picks how to take their free spins.
                    phase = CHOOSE;
                    if (listener != null) listener.featureStarted();
                    invalidate();
                    return;
                default:
                    phase = SHOW;
                    phaseLength = 600;
                    break;
            }
            invalidate();
            scheduleLand(mine);
        }

        private void scheduleLand(final int mine) {
            final int pid = ++phaseId;
            after(phaseLength, mine, new Runnable() {
                @Override
                public void run() {
                    if (pid == phaseId) landFrame(mine);
                }
            });
        }

        private void finish() {
            phase = IDLE;
            invalidate();
            Listener l = listener;
            if (l != null) l.done();
        }

        /** Tap to stop: the reels still spinning stop now, one after another. */
        void slam() {
            if (phase != SPIN || stopAt == null) return;
            long t = SystemClock.uptimeMillis() - phaseStart;
            int k = 0;
            for (int c = 0; c < cols; c++) {
                if (stopAt[c] > t + 60) stopAt[c] = t + 60 + 80L * k++;
            }
            if (k == 0) return;
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            phaseLength = stopAt[cols - 1] + 150;
            scheduleLand(token);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            final int mine = token;
            switch (phase) {
                case SPIN:
                    slam();
                    break;
                case WAIT:
                    phase = SHOW;
                    waitText = null;
                    nextFrame(mine);
                    break;
                case CHOOSE:
                    for (int i = 0; i < choiceBox.length; i++) {
                        if (choiceBox[i].contains(e.getX(), e.getY())) {
                            int spins = SlotMath.choiceSpins(outcome.pendingFree, i);
                            SlotMath.waysFree(outcome, fx, i);
                            banner = spins + " FREE SPINS, ALL WINS x" + SlotMath.FREE_CHOICES[i][1];
                            bannerAt = SystemClock.uptimeMillis();
                            Sfx.play(getContext(), Sfx.CHIPS);
                            phase = SHOW;
                            nextFrame(mine);
                            break;
                        }
                    }
                    break;
                case PARTY:
                    // Skip ahead: show the full amount, then finish.
                    phaseStart = SystemClock.uptimeMillis() - 3000;
                    phaseLength = 3600;
                    final int pid = ++phaseId;
                    after(600, mine, new Runnable() {
                        @Override
                        public void run() {
                            if (pid == phaseId) finish();
                        }
                    });
                    break;
                case IDLE:
                    // Tapping the resting machine spins it.
                    performClick();
                    break;
                default:
                    break;
            }
            return true;
        }

        /** BIG WIN, MEGA WIN or EPIC WIN: a shower of coins and a counter. */
        private void startParty(final int mine, double x) {
            partyTitle = x >= 50 ? "EPIC WIN!" : (x >= 25 ? "MEGA WIN!" : "BIG WIN!");
            partyCoupons = Math.round(outcome.total * (double) bet / SlotMath.UNITS[machine]);
            for (int i = 0; i < COINS_FALLING; i++) {
                px[i] = fx.nextFloat();
                py[i] = -fx.nextFloat() * 1.2f;
                pv[i] = 0.25f + fx.nextFloat() * 0.35f;
                pr[i] = 0.6f + fx.nextFloat() * 0.6f;
            }
            phase = PARTY;
            phaseStart = SystemClock.uptimeMillis();
            phaseLength = x >= 50 ? 5200 : (x >= 25 ? 4400 : 3600);
            Sfx.play(getContext(), x >= 25 ? Sfx.JACKPOT : Sfx.BIG_WIN);
            invalidate();
            final int pid = ++phaseId;
            after(phaseLength, mine, new Runnable() {
                @Override
                public void run() {
                    if (pid == phaseId) finish();
                }
            });
        }

        /** The frame's motion is over: show its result and any win, then move on. */
        private void landFrame(final int mine) {
            Sfx.stop(Sfx.SPIN5);
            shown = frame.grid != null ? frame.grid : shown;
            lit = frame.lit != null ? frame.lit : new boolean[rows][cols];
            if (frame.kind == SlotMath.HOLD || machine == SlotMath.COINS
                    || machine == SlotMath.WAYS) {
                coins = frame.coins;
            }
            if (frame.kind == SlotMath.HOLD) {
                respins = frame.respins;
                if (coinsBefore != null && frame.coins != null && newCoins()) {
                    Sfx.play(getContext(), Sfx.PEG);
                }
            }
            if (frame.kind == SlotMath.WHEEL_SPIN) wheelAngle = wheelTo;
            freeLeft = frame.freeLeft;
            mult = frame.mult;
            if (frame.text != null) {
                banner = frame.text;
                bannerAt = SystemClock.uptimeMillis();
            }
            phase = SHOW;
            phaseStart = SystemClock.uptimeMillis();
            boolean anyLit = false;
            for (boolean[] row : lit) {
                for (boolean b : row) anyLit |= b;
            }
            if (frame.win > 0 && listener != null) listener.frameWon(frame.win);
            if (frame.win > 0 && frame.kind != SlotMath.HOLD) Sfx.play(getContext(), Sfx.WIN);
            long hold = frame.text != null ? 1400 : (frame.win > 0 || anyLit ? 900
                    : (frame.kind == SlotMath.HOLD ? 150 : 120));
            if (frame.freeLeft >= 0 && frame.win == 0 && frame.text == null) hold = 250;
            phaseLength = hold;
            invalidate();
            final boolean tap = frame.waitTap;
            if (tap && listener != null) listener.featureStarted();
            final String tapText = machine == SlotMath.COINS ? "TAP TO START HOLD & SPIN"
                    : (machine == SlotMath.WHEEL ? "TAP TO SPIN THE WHEEL"
                    : "TAP TO START FREE SPINS");
            after(hold, mine, new Runnable() {
                @Override
                public void run() {
                    if (tap) {
                        phase = WAIT;
                        waitText = tapText;
                        invalidate();
                    } else {
                        nextFrame(mine);
                    }
                }
            });
        }

        private boolean newCoins() {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (frame.coins[r][c] != 0 && coinsBefore[r][c] == 0) return true;
                }
            }
            return false;
        }

        // ---------------------------------------------------------- drawing

        private static float easeOut(float t) {
            t = Math.max(0, Math.min(1, t));
            return 1 - (1 - t) * (1 - t) * (1 - t);
        }

        /** A stand-in symbol for a spinning reel, steady for each position so it doesn't flicker. */
        private char filler(int c, int k) {
            String set;
            switch (machine) {
                case SlotMath.CLASSIC:
                    set = "C123-7-W-1-2";
                    break;
                case SlotMath.WAYS:
                    set = "EFBDAKQJTNWS";
                    break;
                case SlotMath.COINS:
                    set = "RGXLAYQJTO";
                    break;
                case SlotMath.WHEEL:
                    set = "123-7-D-S-1-2";
                    break;
                default:
                    set = "RMPATHOC";
                    break;
            }
            int h = (k * 7919 + c * 104729) & 0x7FFFFFFF;
            return set.charAt(h % set.length());
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float p = pad();
            float top = p + band(w);
            float gw = w - 2 * p;
            float gh = getHeight() - top - p;
            float cw = gw / cols;
            float ch = gh / rows;
            long now = SystemClock.uptimeMillis();
            long t = now - phaseStart;

            drawLights(cv, w, p, now);
            drawBand(cv, w, p, now);

            // The glass behind the reels.
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF0B0E16);
            rect.set(p, top, p + gw, top + gh);
            cv.drawRoundRect(rect, 14, 14, paint);
            if (machine != SlotMath.GEMS) {
                paint.setColor(0x14FFFFFF);
                for (int c = 1; c < cols; c++) cv.drawRect(p + c * cw - 1, top, p + c * cw + 1,
                        top + gh, paint);
            }

            cv.save();
            cv.clipRect(p, top, p + gw, top + gh);
            if (phase == SPIN) {
                for (int c = 0; c < cols; c++) {
                    float x = p + (c + 0.5f) * cw;
                    if (t < stopAt[c]) {
                        // Spinning: symbols stream down, a bit stretched and faded.
                        float scroll = t * 0.024f;
                        int base = (int) scroll;
                        float off = scroll - base;
                        boolean teasing = frame.tease && c >= cols - 2 && c > 0
                                && t > stopAt[cols - 3 < 0 ? 0 : cols - 3];
                        if (teasing) {
                            paint.setShader(null);
                            paint.setColor(0x55FFD54F);
                            float glow = 0.5f + 0.5f * (float) Math.sin(t / 90.0);
                            paint.setAlpha((int) (60 + 70 * glow));
                            cv.drawRect(p + c * cw, top, p + (c + 1) * cw, top + gh, paint);
                        }
                        for (int k = -1; k <= rows; k++) {
                            float y = top + (k + off + 0.5f) * ch;
                            char s = filler(c, base - k);
                            cv.save();
                            cv.scale(1f, 1.3f, x, y);
                            drawSymbol(cv, s, x, y, Math.min(cw, ch), 150, 0, 0);
                            cv.restore();
                        }
                    } else {
                        float since = t - stopAt[c];
                        float bounce = since < 180 ? -0.12f * (float) Math.sin(Math.PI * since
                                / 180f) : 0;
                        for (int r = 0; r < rows; r++) {
                            float y = top + (r + 0.5f + bounce) * ch;
                            drawCell(cv, r, c, x, y, Math.min(cw, ch), frame.grid, frame.coins,
                                    false, now);
                        }
                    }
                }
            } else if (phase == DROP) {
                for (int c = 0; c < cols; c++) {
                    float x = p + (c + 0.5f) * cw;
                    for (int r = 0; r < rows; r++) {
                        float local = (t - c * 70f - (rows - 1 - r) * 25f) / 420f;
                        float e = easeOut(local);
                        float y = top + (r + 0.5f) * ch - (1 - e) * (gh + ch);
                        if (local > 0) {
                            drawCell(cv, r, c, x, y, Math.min(cw, ch), shown, null, false, now);
                        }
                    }
                }
            } else if (phase == CASCADE) {
                float popT = Math.min(1f, t / 300f);
                float fallT = easeOut((t - 300f) / 460f);
                if (t < 300 && before != null) {
                    // Winning gems burst.
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            float x = p + (c + 0.5f) * cw;
                            float y = top + (r + 0.5f) * ch;
                            if (beforeLit != null && beforeLit[r][c]) {
                                float s = 1 + popT * 0.4f;
                                cv.save();
                                cv.scale(s, s, x, y);
                                drawSymbol(cv, before[r][c], x, y, Math.min(cw, ch),
                                        (int) (255 * (1 - popT)), 0, 0);
                                cv.restore();
                                paint.setShader(null);
                                paint.setColor(0xFFFFF59D);
                                paint.setAlpha((int) (200 * (1 - popT)));
                                for (int k = 0; k < 6; k++) {
                                    double a = k * Math.PI / 3 + r + c;
                                    float d = popT * cw * 0.6f;
                                    cv.drawCircle(x + (float) Math.cos(a) * d,
                                            y + (float) Math.sin(a) * d, cw * 0.05f, paint);
                                }
                            } else {
                                drawSymbol(cv, before[r][c], x, y, Math.min(cw, ch), 255, 0, 0);
                            }
                        }
                    }
                } else {
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            float x = p + (c + 0.5f) * cw;
                            float from = fromRow[r][c];
                            float row = from + (r - from) * fallT;
                            float y = top + (row + 0.5f) * ch;
                            drawSymbol(cv, shown[r][c], x, y, Math.min(cw, ch), 255, 0, 0);
                        }
                    }
                }
            } else if (phase == HOLD || ((phase == SHOW || phase == IDLE) && frame != null
                    && frame.kind == SlotMath.HOLD)) {
                drawHold(cv, p, top, cw, ch, t, now);
            } else {
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        float x = p + (c + 0.5f) * cw;
                        float y = top + (r + 0.5f) * ch;
                        drawCell(cv, r, c, x, y, Math.min(cw, ch), shown, coins,
                                lit != null && lit[r][c], now);
                    }
                }
            }
            cv.restore();

            // Lines through the middle for the one-line wheel machine.
            if (machine == SlotMath.WHEEL) {
                paint.setShader(null);
                paint.setColor(0x88FF5252);
                cv.drawRect(p, top + gh / 2 - 1.5f, p + gw, top + gh / 2 + 1.5f, paint);
            }

            if (phase == WHEEL || ((phase == SHOW || phase == IDLE) && frame != null
                    && frame.kind == SlotMath.WHEEL_SPIN)) {
                float a = phase == WHEEL ? wheelFrom + (wheelTo - wheelFrom)
                        * wheelEase(t / (float) phaseLength) : wheelTo;
                drawWheel(cv, w / 2f, top + gh / 2f, Math.min(gw, gh) * 0.48f, a);
            }

            if (phase == WAIT) drawWait(cv, w, top, gh, now);
            if (phase == CHOOSE) drawChoice(cv, p, top, gw, gh, now);
            if (phase == PARTY) drawParty(cv, w, top, gh, t);

            if (phase != IDLE) postInvalidateOnAnimation();
            else postInvalidateDelayed(110);
        }

        /** Chasing bulbs around the cabinet; they go wild on a win or a bonus. */
        private void drawLights(Canvas cv, float w, float p, long now) {
            float h = getHeight();
            float in = p * 0.5f;
            float step = p * 1.6f;
            float r = p * 0.22f;
            boolean wild = phase == PARTY || phase == WAIT || phase == CHOOSE
                    || (phase == SHOW && frame != null && frame.win > 0);
            long tick = now / (wild ? 70 : 140);
            int i = 0;
            paint.setShader(null);
            float perim = 2 * (w - 2 * in) + 2 * (h - 2 * in);
            for (float d = 0; d < perim; d += step, i++) {
                float x, y;
                float a = w - 2 * in;
                float b = h - 2 * in;
                if (d < a) {
                    x = in + d;
                    y = in;
                } else if (d < a + b) {
                    x = w - in;
                    y = in + d - a;
                } else if (d < 2 * a + b) {
                    x = w - in - (d - a - b);
                    y = h - in;
                } else {
                    x = in;
                    y = h - in - (d - 2 * a - b);
                }
                boolean on = wild ? ((i + tick) % 2 == 0) : ((i + tick) % 4 == 0);
                paint.setColor(on ? (wild && (tick % 6 < 3) ? 0xFFFFFFFF : TRIM[machine])
                        : 0x44FFFFFF);
                cv.drawCircle(x, y, on ? r * 1.25f : r, paint);
            }
        }

        private void drawWait(Canvas cv, float w, float top, float gh, long now) {
            paint.setShader(null);
            paint.setColor(0x99000000);
            cv.drawRect(0, top, w, top + gh, paint);
            float pulse = 1 + 0.08f * (float) Math.sin(now / 150.0);
            float cy = top + gh / 2;
            cv.save();
            cv.scale(pulse, pulse, w / 2, cy);
            label(cv, waitText == null ? "TAP TO START" : waitText, w / 2, cy - gh * 0.08f,
                    fitText(waitText, w * 0.86f, gh * 0.11f), 0xFFFFEB3B, 255);
            cv.restore();
            emoji(cv, "👆", w / 2, cy + gh * 0.18f + (float) Math.sin(now / 120.0) * gh * 0.03f,
                    gh * 0.16f, 255);
        }

        private float fitText(String text, float maxW, float size) {
            if (text == null) return size;
            paint.setFakeBoldText(true);
            paint.setTextSize(size);
            while (paint.measureText(text) > maxW && size > 8) {
                size -= 1;
                paint.setTextSize(size);
            }
            paint.setFakeBoldText(false);
            return size;
        }

        private void drawChoice(Canvas cv, float p, float top, float gw, float gh, long now) {
            paint.setShader(null);
            paint.setColor(0xCC000000);
            cv.drawRect(0, top, getWidth(), top + gh, paint);
            float w = getWidth();
            label(cv, "PICK YOUR FREE SPINS", w / 2, top + gh * 0.12f,
                    fitText("PICK YOUR FREE SPINS", gw * 0.9f, gh * 0.09f), 0xFFFFD54F, 255);
            int[] fill = {0xFF1565C0, 0xFF6A1B9A, 0xFFC62828};
            float bw = gw / 3f;
            for (int i = 0; i < 3; i++) {
                float x0 = p + i * bw + bw * 0.06f;
                float x1 = p + (i + 1) * bw - bw * 0.06f;
                float y0 = top + gh * 0.24f;
                float y1 = top + gh * 0.94f;
                choiceBox[i].set(x0, y0, x1, y1);
                float bob = (float) Math.sin(now / 200.0 + i) * gh * 0.012f;
                rect.set(x0, y0 + bob, x1, y1 + bob);
                paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        0xFFFFFFFF & (fill[i] | 0xFF404040), fill[i], Shader.TileMode.CLAMP));
                cv.drawRoundRect(rect, bw * 0.12f, bw * 0.12f, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(bw * 0.03f);
                paint.setColor(0xFFFFD54F);
                cv.drawRoundRect(rect, bw * 0.12f, bw * 0.12f, paint);
                paint.setStyle(Paint.Style.FILL);
                int spins = SlotMath.choiceSpins(outcome.pendingFree, i);
                float cx = rect.centerX();
                label(cv, String.valueOf(spins), cx, rect.top + rect.height() * 0.3f,
                        rect.width() * 0.42f, 0xFFFFFFFF, 255);
                label(cv, "SPINS", cx, rect.top + rect.height() * 0.52f, rect.width() * 0.16f,
                        0xFFFFFFFF, 255);
                label(cv, "WINS x" + SlotMath.FREE_CHOICES[i][1], cx,
                        rect.top + rect.height() * 0.78f, rect.width() * 0.15f, 0xFFFFEB3B, 255);
            }
        }

        private void drawParty(Canvas cv, float w, float top, float gh, long t) {
            float h = getHeight();
            paint.setShader(null);
            paint.setColor(0xAA000000);
            cv.drawRect(0, 0, w, h, paint);
            // Falling coins.
            float sec = t / 1000f;
            for (int i = 0; i < COINS_FALLING; i++) {
                float y = (py[i] + pv[i] * sec * 1.6f) % 1.3f;
                float x = px[i] * w + (float) Math.sin(sec * 3 + i) * w * 0.02f;
                float r = w * 0.025f * pr[i];
                float squash = Math.abs((float) Math.cos(sec * 6 + i));
                rect.set(x - r * squash, y * h - r, x + r * squash, y * h + r);
                paint.setShader(new RadialGradient(x - r * 0.3f, y * h - r * 0.3f, r * 1.4f,
                        0xFFFFF8E1, 0xFFFFA000, Shader.TileMode.CLAMP));
                cv.drawOval(rect, paint);
                paint.setShader(null);
            }
            float pulse = 1 + 0.07f * (float) Math.sin(t / 110.0);
            int[] cycle = {0xFFFFEB3B, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB};
            int color = cycle[(int) ((t / 180) % cycle.length)];
            float cy = h * 0.42f;
            cv.save();
            cv.scale(pulse, pulse, w / 2, cy);
            label(cv, partyTitle, w / 2, cy, fitText(partyTitle, w * 0.9f, h * 0.14f), color, 255);
            cv.restore();
            float k = easeOut(t / 2600f);
            long shownAmount = Math.round(partyCoupons * (double) k);
            label(cv, String.format(Locale.US, "%,d", shownAmount), w / 2, h * 0.62f,
                    h * 0.11f, 0xFFFFFFFF, 255);
            label(cv, "tap to skip", w / 2, h * 0.78f, h * 0.035f, 0xAAFFFFFF, 255);
        }

        private static float wheelEase(float t) {
            t = Math.max(0, Math.min(1, t));
            // Fast start, long slow finish, like a heavy wheel.
            return 1 - (float) Math.pow(1 - t, 4);
        }

        /** The strip above the reels: free spins, respins, multipliers and big news. */
        private void drawBand(Canvas cv, float w, float p, long now) {
            float bh = band(w);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            String main;
            int color = TRIM[machine];
            if (frame != null && (phase == HOLD || frame.kind == SlotMath.HOLD
                    && (phase == SHOW || phase == IDLE))) {
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < 3; i++) dots.append(i < respins ? "●" : "○");
                main = "HOLD & SPIN   RESPINS " + dots;
            } else if (freeLeft >= 0) {
                main = "FREE SPINS: " + freeLeft + " LEFT"
                        + (machine == SlotMath.GEMS ? "   MULTIPLIER x" + mult : "");
            } else {
                main = SlotMath.NAMES[machine].toUpperCase(Locale.US);
                if (machine == SlotMath.COINS) {
                    main = "GRAND " + fmt(SlotMath.VAULT_JACKPOTS[3]) + "   MAJOR "
                            + fmt(SlotMath.VAULT_JACKPOTS[2]);
                }
            }
            boolean flash = banner != null && now - bannerAt < 2200;
            if (flash) {
                main = banner;
                color = (now / 160) % 2 == 0 ? 0xFFFFEB3B : 0xFFFFFFFF;
            }
            paint.setColor(0x66000000);
            rect.set(p, p * 0.6f, w - p, p * 0.6f + bh * 0.8f);
            cv.drawRoundRect(rect, bh * 0.3f, bh * 0.3f, paint);
            paint.setColor(color);
            paint.setFakeBoldText(true);
            float size = bh * 0.42f;
            paint.setTextSize(size);
            while (paint.measureText(main) > w - 3 * p && size > 8) {
                size -= 1;
                paint.setTextSize(size);
            }
            cv.drawText(main, w / 2f, rect.centerY() - (paint.ascent() + paint.descent()) / 2f,
                    paint);
            paint.setFakeBoldText(false);
        }

        private String fmt(long units) {
            long cps = Math.round(units * (double) bet / SlotMath.UNITS[machine]);
            return String.format(Locale.US, "%,d", cps);
        }

        private void drawCell(Canvas cv, int r, int c, float x, float y, float size,
                              char[][] grid, long[][] cvals, boolean on, long now) {
            if (grid == null) return;
            if (on) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(now / 110.0);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFD54F);
                paint.setAlpha((int) (40 + 50 * pulse));
                rect.set(x - size * 0.47f, y - size * 0.47f, x + size * 0.47f, y + size * 0.47f);
                cv.drawRoundRect(rect, size * 0.15f, size * 0.15f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(size * 0.05f);
                paint.setAlpha((int) (160 + 95 * pulse));
                cv.drawRoundRect(rect, size * 0.15f, size * 0.15f, paint);
                paint.setStyle(Paint.Style.FILL);
                cv.save();
                float s = 1 + 0.06f * pulse;
                cv.scale(s, s, x, y);
            }
            long cvv = cvals != null && r < cvals.length && c < cvals[r].length ? cvals[r][c] : 0;
            drawSymbol(cv, grid[r][c], x, y, size, 255, cvv,
                    machine == SlotMath.WAYS ? (int) cvv : 0);
            if (on) cv.restore();
        }

        // ---------------------------------------------------------- symbols

        private void drawSymbol(Canvas cv, char s, float x, float y, float size, int alpha,
                                long coinVal, int wildMult) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.CENTER);
            switch (machine) {
                case SlotMath.CLASSIC:
                case SlotMath.WHEEL:
                    drawClassic(cv, s, x, y, size, alpha);
                    break;
                case SlotMath.WAYS:
                    drawWays(cv, s, x, y, size, alpha, wildMult);
                    break;
                case SlotMath.COINS:
                    if (s == 'O') drawCoin(cv, x, y, size, alpha, coinVal);
                    else drawVault(cv, s, x, y, size, alpha);
                    break;
                default:
                    drawGem(cv, s, x, y, size, alpha);
                    break;
            }
            paint.setAlpha(255);
        }

        private void emoji(Canvas cv, String e, float x, float y, float size, int alpha) {
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(alpha);
            paint.setTextSize(size);
            cv.drawText(e, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
        }

        private void label(Canvas cv, String text, float x, float y, float size, int color,
                           int alpha) {
            paint.setShader(null);
            paint.setFakeBoldText(true);
            paint.setTextSize(size);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.18f);
            paint.setColor(0xFF000000);
            paint.setAlpha(alpha);
            cv.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setAlpha(alpha);
            cv.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
            paint.setFakeBoldText(false);
        }

        private void drawClassic(Canvas cv, char s, float x, float y, float size, int alpha) {
            switch (s) {
                case '-':
                    return;
                case 'C':
                    emoji(cv, "🍒", x, y, size * 0.55f, alpha);
                    return;
                case '1':
                case '2':
                case '3': {
                    int n = s - '0';
                    float ph = size * 0.17f;
                    float pw = size * 0.7f;
                    float gap = size * 0.03f;
                    float total = n * ph + (n - 1) * gap;
                    for (int i = 0; i < n; i++) {
                        float py = y - total / 2 + i * (ph + gap);
                        rect.set(x - pw / 2, py, x + pw / 2, py + ph);
                        paint.setShader(new LinearGradient(0, py, 0, py + ph, 0xFF4A4A4A,
                                0xFF0A0A0A, Shader.TileMode.CLAMP));
                        paint.setAlpha(alpha);
                        cv.drawRoundRect(rect, ph * 0.3f, ph * 0.3f, paint);
                        paint.setShader(null);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(size * 0.012f);
                        paint.setColor(0xFFFFD54F);
                        paint.setAlpha(alpha);
                        cv.drawRoundRect(rect, ph * 0.3f, ph * 0.3f, paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(0xFFFFFFFF);
                        paint.setAlpha(alpha);
                        paint.setFakeBoldText(true);
                        paint.setTextSize(ph * 0.75f);
                        cv.drawText("BAR", x, py + ph / 2 - (paint.ascent() + paint.descent()) / 2f,
                                paint);
                        paint.setFakeBoldText(false);
                    }
                    return;
                }
                case '7': {
                    paint.setTextSize(size * 0.75f);
                    paint.setFakeBoldText(true);
                    float by = y - (paint.ascent() + paint.descent()) / 2f;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(size * 0.06f);
                    paint.setColor(0xFFFFFFFF);
                    paint.setAlpha(alpha);
                    cv.drawText("7", x, by, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setShader(new LinearGradient(0, y - size * 0.35f, 0, y + size * 0.35f,
                            0xFFFF5252, 0xFF8B0000, Shader.TileMode.CLAMP));
                    paint.setAlpha(alpha);
                    cv.drawText("7", x, by, paint);
                    paint.setShader(null);
                    paint.setFakeBoldText(false);
                    return;
                }
                case 'W':
                    emoji(cv, "🔥", x, y - size * 0.08f, size * 0.45f, alpha);
                    label(cv, "WILD 2X", x, y + size * 0.3f, size * 0.16f, 0xFFFFEB3B, alpha);
                    return;
                case 'D': {
                    rect.set(x - size * 0.34f, y - size * 0.3f, x + size * 0.34f, y + size * 0.3f);
                    paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0xFFCE93D8,
                            0xFF6A1B9A, Shader.TileMode.CLAMP));
                    paint.setAlpha(alpha);
                    cv.drawRoundRect(rect, size * 0.12f, size * 0.12f, paint);
                    paint.setShader(null);
                    label(cv, "2X", x, y - size * 0.03f, size * 0.36f, 0xFFFFFFFF, alpha);
                    label(cv, "WILD", x, y + size * 0.21f, size * 0.12f, 0xFFFFEB3B, alpha);
                    return;
                }
                case 'S':
                    emoji(cv, "🎡", x, y - size * 0.08f, size * 0.45f, alpha);
                    label(cv, "WHEEL", x, y + size * 0.3f, size * 0.15f, 0xFF4FC3F7, alpha);
                    return;
                default:
            }
        }

        private void drawWays(Canvas cv, char s, float x, float y, float size, int alpha,
                              int wildMult) {
            switch (s) {
                case 'E':
                    emoji(cv, "🦅", x, y, size * 0.55f, alpha);
                    return;
                case 'F':
                    emoji(cv, "🐺", x, y, size * 0.55f, alpha);
                    return;
                case 'B':
                    emoji(cv, "🐻", x, y, size * 0.55f, alpha);
                    return;
                case 'D':
                    emoji(cv, "🦌", x, y, size * 0.55f, alpha);
                    return;
                case 'W':
                    emoji(cv, "🌅", x, y - size * 0.06f, size * 0.48f, alpha);
                    label(cv, wildMult > 1 ? "WILD x" + wildMult : "WILD", x, y + size * 0.32f,
                            size * 0.15f, wildMult > 1 ? 0xFFFF5252 : 0xFFFFEB3B, alpha);
                    return;
                case 'S':
                    emoji(cv, "🌕", x, y - size * 0.06f, size * 0.45f, alpha);
                    label(cv, "BONUS", x, y + size * 0.32f, size * 0.15f, 0xFFFFD54F, alpha);
                    return;
                default:
                    String t = s == 'T' ? "10" : (s == 'N' ? "9" : String.valueOf(s));
                    int[] colors = {0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFAB47BC, 0xFFFFA726,
                            0xFF26C6DA};
                    int idx = "AKQJTN".indexOf(s);
                    label(cv, t, x, y, size * 0.48f, colors[Math.max(0, idx)], alpha);
            }
        }

        private void drawVault(Canvas cv, char s, float x, float y, float size, int alpha) {
            switch (s) {
                case 'R':
                    emoji(cv, "🐉", x, y, size * 0.55f, alpha);
                    return;
                case 'G':
                    emoji(cv, "🐯", x, y, size * 0.55f, alpha);
                    return;
                case 'X':
                    emoji(cv, "🐟", x, y, size * 0.55f, alpha);
                    return;
                case 'L':
                    emoji(cv, "🏮", x, y, size * 0.55f, alpha);
                    return;
                default:
                    String t = s == 'T' ? "10" : (s == 'Y' ? "K" : String.valueOf(s));
                    int[] colors = {0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFAB47BC, 0xFFFFA726};
                    int idx = "AYQJT".indexOf(s);
                    label(cv, t, x, y, size * 0.48f, colors[Math.max(0, idx)], alpha);
            }
        }

        /** A gold coin showing what it pays: coupons, or a jackpot name. */
        private void drawCoin(Canvas cv, float x, float y, float size, int alpha, long val) {
            float r = size * 0.4f;
            int[] rim = {0xFFFFF8E1, 0xFFFFC107, 0xFFB26A00};
            if (val < 0) {
                int[][] jack = {{0xFFE3F2FD, 0xFF42A5F5, 0xFF0D47A1},
                        {0xFFE8F5E9, 0xFF66BB6A, 0xFF1B5E20},
                        {0xFFFCE4EC, 0xFFEC407A, 0xFF880E4F}};
                rim = jack[(int) Math.min(2, -val - 1)];
            }
            paint.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.4f, rim,
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            paint.setAlpha(alpha);
            cv.drawCircle(x, y, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.08f);
            paint.setColor(0xAA5D4037);
            paint.setAlpha(alpha);
            cv.drawCircle(x, y, r * 0.82f, paint);
            paint.setStyle(Paint.Style.FILL);
            String text = val < 0 ? SlotMath.JACKPOT_NAMES[(int) (-val - 1)]
                    : (val == 0 ? "" : fmt(val));
            float ts = r * (text.length() > 4 ? 0.5f : 0.7f);
            label(cv, text, x, y, ts, 0xFFFFFFFF, alpha);
        }

        private void drawGem(Canvas cv, char s, float x, float y, float size, int alpha) {
            switch (s) {
                case 'H':
                    emoji(cv, "💗", x, y, size * 0.6f, alpha);
                    return;
                case 'O':
                    emoji(cv, "⭐", x, y, size * 0.6f, alpha);
                    return;
                case 'C':
                    emoji(cv, "🌙", x, y, size * 0.6f, alpha);
                    return;
                case 'S':
                    emoji(cv, "💫", x, y - size * 0.05f, size * 0.55f, alpha);
                    label(cv, "FREE", x, y + size * 0.33f, size * 0.15f, 0xFFFFEB3B, alpha);
                    return;
                default:
            }
            int[][] cols = {{0xFFFF8A80, 0xFFE53935, 0xFF7F0000},     // ruby
                    {0xFFB9F6CA, 0xFF00C853, 0xFF1B5E20},              // emerald
                    {0xFF82B1FF, 0xFF2962FF, 0xFF0D1F6B},              // sapphire
                    {0xFFEA80FC, 0xFFAA00FF, 0xFF4A0072},              // amethyst
                    {0xFFFFE57F, 0xFFFFAB00, 0xFF8D5A00}};             // topaz
            int k = "RMPAT".indexOf(s);
            if (k < 0) return;
            float r = size * 0.38f;
            // A cut gem: flat top, angled sides, pointed bottom.
            path.reset();
            path.moveTo(x - r * 0.55f, y - r * 0.75f);
            path.lineTo(x + r * 0.55f, y - r * 0.75f);
            path.lineTo(x + r, y - r * 0.2f);
            path.lineTo(x, y + r);
            path.lineTo(x - r, y - r * 0.2f);
            path.close();
            paint.setShader(new LinearGradient(x - r, y - r, x + r, y + r, cols[k],
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            paint.setAlpha(alpha);
            cv.drawPath(path, paint);
            paint.setShader(null);
            // Facets.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.05f);
            paint.setColor(0x66FFFFFF);
            paint.setAlpha(alpha * 2 / 5);
            cv.drawLine(x - r, y - r * 0.2f, x + r, y - r * 0.2f, paint);
            cv.drawLine(x - r * 0.3f, y - r * 0.75f, x - r * 0.45f, y - r * 0.2f, paint);
            cv.drawLine(x + r * 0.3f, y - r * 0.75f, x + r * 0.45f, y - r * 0.2f, paint);
            cv.drawLine(x - r * 0.45f, y - r * 0.2f, x, y + r, paint);
            cv.drawLine(x + r * 0.45f, y - r * 0.2f, x, y + r, paint);
            paint.setStyle(Paint.Style.FILL);
            // Sparkle.
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(alpha * 3 / 4);
            cv.drawCircle(x - r * 0.35f, y - r * 0.5f, r * 0.1f, paint);
        }

        // ---------------------------------------------------------- hold & spin and wheel

        private void drawHold(Canvas cv, float p, float top, float cw, float ch, long t, long now) {
            long[][] cur = frame.coins;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    float x = p + (c + 0.5f) * cw;
                    float y = top + (r + 0.5f) * ch;
                    float size = Math.min(cw, ch);
                    long v = cur[r][c];
                    boolean fresh = v != 0 && coinsBefore != null && coinsBefore[r][c] == 0;
                    boolean spinningCell = phase == HOLD && (v == 0 || fresh) && t < phaseLength * 0.6f;
                    paint.setShader(null);
                    paint.setColor(0xFF161B28);
                    rect.set(x - size * 0.46f, y - size * 0.46f, x + size * 0.46f, y + size * 0.46f);
                    cv.drawRoundRect(rect, size * 0.12f, size * 0.12f, paint);
                    if (spinningCell) {
                        // Empty spots flicker while they spin.
                        paint.setColor(0xFF2A3350);
                        paint.setAlpha(120 + (int) (100 * Math.abs(Math.sin((now + r * 97 + c * 31)
                                / 60.0))));
                        cv.drawRoundRect(rect, size * 0.12f, size * 0.12f, paint);
                        paint.setAlpha(255);
                    } else if (v != 0) {
                        float s = 1;
                        if (fresh && phase == HOLD) {
                            float k = (t - phaseLength * 0.6f) / 220f;
                            s = 0.6f + 0.4f * easeOut(k) + 0.15f * (float) Math.sin(Math.PI
                                    * Math.max(0, Math.min(1, k)));
                        }
                        cv.save();
                        cv.scale(s, s, x, y);
                        drawCoin(cv, x, y, size, 255, v);
                        cv.restore();
                    }
                }
            }
        }

        private void drawWheel(Canvas cv, float cx, float cy, float r, float angle) {
            int n = SlotMath.WHEEL_SEGMENTS.length;
            float seg = 360f / n;
            paint.setShader(null);
            paint.setColor(0xCC000000);
            cv.drawCircle(cx, cy, r * 1.08f, paint);
            cv.save();
            cv.rotate(angle, cx, cy);
            rect.set(cx - r, cy - r, cx + r, cy + r);
            for (int i = 0; i < n; i++) {
                int val = SlotMath.WHEEL_SEGMENTS[i];
                int color = val >= 500 ? 0xFFFFD600 : (val >= 100 ? 0xFFE53935
                        : (i % 2 == 0 ? 0xFF1E88E5 : 0xFF3949AB));
                paint.setColor(color);
                // Segment i is centered straight up when the wheel is turned to -i * seg.
                cv.drawArc(rect, -90 + i * seg - seg / 2, seg, true, paint);
                cv.save();
                cv.rotate(i * seg, cx, cy);
                label(cv, String.valueOf(val), cx, cy - r * 0.72f, r * 0.12f,
                        val >= 500 ? 0xFF000000 : 0xFFFFFFFF, 255);
                cv.restore();
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.04f);
            paint.setColor(0xFFFFD54F);
            cv.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            cv.restore();
            paint.setShader(new RadialGradient(cx - r * 0.05f, cy - r * 0.05f, r * 0.2f,
                    0xFFFFF8E1, 0xFFB26A00, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r * 0.16f, paint);
            paint.setShader(null);
            // The pointer at the top.
            path.reset();
            path.moveTo(cx - r * 0.09f, cy - r * 1.1f);
            path.lineTo(cx + r * 0.09f, cy - r * 1.1f);
            path.lineTo(cx, cy - r * 0.86f);
            path.close();
            paint.setColor(0xFFFFFFFF);
            cv.drawPath(path, paint);
        }
    }
}
