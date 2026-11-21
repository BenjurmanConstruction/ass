package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
 * A penny machine on the shared jackpot bank. You put coupons in as credits (1 coupon = 100
 * credits), play, and cash out a ticket when you're done. Credits left in the machine are
 * kept for next time. Play money only.
 */
public class PennyActivity extends Activity {
    static final String CREDITS = "pennyCredits";
    static final String BANK = "jackpotBank";
    private static final int[] BETS = {25, 50, 100, 150, 250, 500};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private int m;
    private JackpotBank bank;
    private MachineActivity.MachineView view;
    private Board board;
    private TextView message;
    private TextView creditText;
    private TextView betText;
    private TextView winMeter;
    private Button spinBtn;
    private Button autoBtn;
    private int betIndex = 2;
    private boolean busy;
    private int autoLeft;
    private long shownWin;
    private long rollTarget;

    static long credits(Context c) {
        return Prefs.getInt(c, CREDITS, 0);
    }

    static void setCredits(Context c, long v) {
        Prefs.putInt(c, CREDITS, (int) Math.max(0, Math.min(Integer.MAX_VALUE, v)));
    }

    static JackpotBank loadBank(Context c, Random rnd) {
        return JackpotBank.load(Prefs.getString(c, BANK), rnd);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!ParlorActivity.owned(this)) {
            Toast.makeText(this, "Unlock the Slot Parlor first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        m = Math.max(SlotMath.COUPON_MACHINES, Math.min(SlotMath.NAMES.length - 1,
                getIntent().getIntExtra("machine", SlotMath.COUPON_MACHINES)));
        bank = loadBank(this, rnd);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        root.addView(Ui.topBar(this, SlotMath.NAMES[m]), Ui.full(this, 0));

        board = new Board(this);
        root.addView(board, Ui.full(this, 8));

        view = new MachineActivity.MachineView(this, m);
        android.graphics.drawable.GradientDrawable fg = Ui.round(this,
                MachineActivity.CABINET[m], 22);
        fg.setStroke(Ui.dp(this, 3), MachineActivity.TRIM[m]);
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
        root.addView(view, Ui.full(this, 8));

        winMeter = Ui.text(this, "", 26, Ui.ACCENT);
        winMeter.setTypeface(Ui.medium());
        winMeter.setGravity(Gravity.CENTER);
        root.addView(winMeter, Ui.full(this, 6));
        message = Ui.text(this, SlotMath.BLURBS[m] + "\nTap the machine or SPIN. Tap again to "
                + "stop the reels.", 15, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        root.addView(message, Ui.full(this, 2));
        creditText = Ui.text(this, "", 16, Ui.ACCENT);
        creditText.setGravity(Gravity.CENTER);
        creditText.setTypeface(Ui.medium());
        root.addView(creditText, Ui.full(this, 6));

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
                } else {
                    autoLeft = 10;
                    if (!busy) spin();
                }
                paint();
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
                if (busy) view.slam();
                else spin();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(this, 72), 1f);
        sp.leftMargin = Ui.dp(this, 8);
        spinRow.addView(spinBtn, sp);
        root.addView(spinRow, Ui.full(this, 8));

        LinearLayout money = new LinearLayout(this);
        money.setOrientation(LinearLayout.HORIZONTAL);
        Button add = Ui.button(this, "Add coupons", false);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCoupons();
            }
        });
        Button out = Ui.button(this, "Cash out ticket", false);
        out.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cashOut();
            }
        });
        money.addView(add, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f);
        op.leftMargin = Ui.dp(this, 8);
        money.addView(out, op);
        root.addView(money, Ui.full(this, 10));

        root.addView(Sfx.toggle(this), Ui.full(this, 10));
        Button table = Ui.button(this, "Pay table and odds", false);
        table.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTable();
            }
        });
        root.addView(table, Ui.full(this, 10));
        root.addView(Ui.text(this, "Penny machine: 100 credits = 1 Fun Coupon. The jackpots "
                + "are shared by every penny machine. Play money only: Fun Coupons can't be "
                + "bought with or cashed out for real money.", 12, Ui.SUB), Ui.full(this, 14));

        setContentView(scroll);
        Bg.apply(this, scroll);
        view.setBet(bet());
        view.showIdle(rnd);
        paint();
        if (credits(this) < BETS[0]) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) addCoupons();
                }
            }, 400);
        }
    }

    private Button step(String label, final int dir) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bt.setPadding(0, 0, 0, 0);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) return;
                betIndex = Math.max(0, Math.min(BETS.length - 1, betIndex + dir));
                view.setBet(bet());
                paint();
            }
        });
        return bt;
    }

    private int bet() {
        return BETS[betIndex];
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoLeft = 0;
        Prefs.putString(this, BANK, bank.save());
        Sfx.stop(Sfx.SPIN5);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (view != null) view.cancel();
        super.onDestroy();
    }

    private void paint() {
        long c = credits(this);
        creditText.setText(String.format(Locale.US, "CREDITS  %,d   (%,.2f coupons)", c,
                c / 100.0));
        betText.setText(String.format(Locale.US, "Bet %,d credits", bet()));
        autoBtn.setText(autoLeft > 0 ? "STOP\n" + autoLeft : "AUTO\n10");
        spinBtn.setAlpha(busy ? 0.5f : 1f);
        board.invalidate();
    }

    // ------------------------------------------------------------------ money in and out

    private void addCoupons() {
        final int[] amounts = {50, 100, 250, 500, 1000};
        final int have = Wallet.coupons(this);
        List<String> labels = new ArrayList<>();
        final List<Integer> ok = new ArrayList<>();
        for (int a : amounts) {
            if (a <= have) {
                labels.add(String.format(Locale.US, "%,d coupons  →  %,d credits", a, a * 100));
                ok.add(a);
            }
        }
        if (ok.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Not enough coupons")
                    .setMessage("You have " + have + " Fun Coupons. Get more in the Wallet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.US, "Add coupons (you have %,d)", have))
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        int a = ok.get(which);
                        if (Wallet.coupons(PennyActivity.this) < a) return;
                        Wallet.addCoupons(PennyActivity.this, -a);
                        setCredits(PennyActivity.this, credits(PennyActivity.this) + a * 100L);
                        Sfx.play(PennyActivity.this, Sfx.CHIPS);
                        paint();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cashOut() {
        if (busy) return;
        long c = credits(this);
        int coupons = (int) (c / 100);
        if (coupons <= 0) {
            Toast.makeText(this, "Less than 100 credits in the machine: nothing to cash out.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Wallet.addCoupons(this, coupons);
        setCredits(this, c % 100);
        Sfx.play(this, Sfx.WIN);
        paint();
        new AlertDialog.Builder(this)
                .setTitle("🎫 TICKET")
                .setMessage(String.format(Locale.US, "CASH OUT VOUCHER\n\n%,d Fun Coupons\n\n"
                        + "Added to your wallet.%s", coupons, c % 100 > 0
                        ? String.format(Locale.US, "\n%d credits left in the machine.", c % 100)
                        : ""))
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------------ spinning

    private void spin() {
        if (busy) return;
        final int bet = bet();
        long c = credits(this);
        if (c < bet) {
            autoLeft = 0;
            message.setText("Not enough credits. Lower the bet or tap Add coupons.");
            paint();
            return;
        }
        setCredits(this, c - bet);
        bank.contribute(bet);
        if (autoLeft > 0) autoLeft--;
        busy = true;
        shownWin = 0;
        rollTarget = 0;
        winMeter.setText("");
        message.setText("Good luck!");
        paint();
        final SlotMath.Outcome o = PennyMath.play(m, rnd, bet);
        view.setBet(bet);
        view.play(o, new MachineActivity.MachineView.Listener() {
            long units;

            @Override
            public void frameWon(long u) {
                units += u;
                rollTo(Math.round(units * (double) bet / PennyMath.BET));
            }

            @Override
            public void featureStarted() {
                autoLeft = 0;
                message.setText("BONUS! Tap the machine to play it.");
                Sfx.play(PennyActivity.this, Sfx.JACKPOT);
                paint();
            }

            @Override
            public void done() {
                settle(o, bet);
            }
        });
    }

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
            handler.postDelayed(this, 30);
        }
    };

    private void settle(SlotMath.Outcome o, final int bet) {
        long machineWin = Math.round(o.total * (double) bet / PennyMath.BET);
        // Jackpots from this spin, then any mystery jackpot that's due.
        final List<String> titles = new ArrayList<>();
        final List<Long> amounts = new ArrayList<>();
        for (int level : o.jackpots) {
            long amt = bank.award(level, rnd);
            titles.add(JackpotBank.NAMES[level] + " JACKPOT!");
            amounts.add(amt);
        }
        int due = bank.mysteryDue();
        for (int level = JackpotBank.MYSTERY_SMALL; level <= JackpotBank.MYSTERY_BIG; level++) {
            if ((due & (1 << level)) != 0) {
                long amt = bank.award(level, rnd);
                titles.add(JackpotBank.NAMES[level] + " JACKPOT!");
                amounts.add(amt);
            }
        }
        long jackpots = 0;
        for (long a : amounts) jackpots += a;
        final long total = machineWin + jackpots;
        setCredits(this, credits(this) + total);
        Prefs.putString(this, BANK, bank.save());
        for (int i = 0; i < titles.size(); i++) {
            Stats.log(this, String.format(Locale.US, "%s %s %,d credits on %s", "Hit the",
                    titles.get(i).toLowerCase(Locale.US), amounts.get(i), SlotMath.NAMES[m]));
        }
        if (!titles.isEmpty()) autoLeft = 0;
        celebrate(titles, amounts, 0, new Runnable() {
            @Override
            public void run() {
                finishSpin(total, bet, !titles.isEmpty());
            }
        });
        paint();
    }

    /** One jackpot celebration after another, then carry on. */
    private void celebrate(final List<String> titles, final List<Long> amounts, final int i,
                           final Runnable then) {
        if (i >= titles.size()) {
            then.run();
            return;
        }
        message.setText(String.format(Locale.US, "%s  %,d credits!", titles.get(i),
                amounts.get(i)));
        view.jackpotParty(titles.get(i), amounts.get(i), new Runnable() {
            @Override
            public void run() {
                celebrate(titles, amounts, i + 1, then);
            }
        });
    }

    private void finishSpin(long won, int bet, boolean jackpot) {
        rollTo(won);
        double x = won / (double) bet;
        if (won == 0) {
            message.setText("No win.");
        } else if (won < bet) {
            message.setText(String.format(Locale.US, "Won %,d back.", won));
        } else {
            message.setText(String.format(Locale.US, "Winner! %,d credits (%.1fx your bet)",
                    won, x));
        }
        if (!jackpot && x < 10) {
            if (x >= 3) Sfx.play(this, Sfx.BIG_WIN);
            else if (won > 0) Sfx.play(this, Sfx.WIN);
        }
        busy = false;
        paint();
        if (autoLeft > 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing() && autoLeft > 0 && !busy) spin();
                }
            }, won > 0 ? 1000 : 400);
        }
    }

    // ------------------------------------------------------------------ the jackpot board

    /** The shared jackpot meters across the top, counting up as they grow. */
    final class Board extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final double[] shown = new double[6];
        private long last;

        Board(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, (int) (width * 0.42f));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float d = getResources().getDisplayMetrics().density;
            long now = SystemClock.uptimeMillis();
            boolean moving = false;
            for (int i = 0; i < 6; i++) {
                double target = bank.meter[i];
                if (shown[i] == 0 || shown[i] > target) shown[i] = target;
                if (shown[i] < target) {
                    // Count up like a real meter.
                    double step = Math.max(0.37, (target - shown[i]) * 0.08);
                    shown[i] = Math.min(target, shown[i] + step);
                    moving = true;
                }
            }
            float gap = 4 * d;
            float rowH = (h - 2 * gap) / 3f;
            int[] order = {JackpotBank.GRAND, JackpotBank.MAJOR, JackpotBank.MINOR,
                    JackpotBank.MINI, JackpotBank.MYSTERY_SMALL, JackpotBank.MYSTERY_BIG};
            int[] top = {0xFFFFF176, 0xFFFF8A80, 0xFFB9F6CA, 0xFF82B1FF, 0xFFE1BEE7, 0xFFFFCC80};
            int[] bottom = {0xFFFF8F00, 0xFFC62828, 0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A,
                    0xFFE65100};
            for (int k = 0; k < 6; k++) {
                int i = order[k];
                int row = k / 2;
                int col = k % 2;
                float x0 = col == 0 ? 0 : w / 2 + gap / 2;
                float x1 = col == 0 ? w / 2 - gap / 2 : w;
                float y0 = row * (rowH + gap);
                rect.set(x0, y0, x1, y0 + rowH);
                paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, top[k],
                        bottom[k], Shader.TileMode.CLAMP));
                cv.drawRoundRect(rect, 10 * d, 10 * d, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * d);
                paint.setColor(0x88FFFFFF);
                cv.drawRoundRect(rect, 10 * d, 10 * d, paint);
                paint.setStyle(Paint.Style.FILL);
                String name = JackpotBank.NAMES[i];
                String value = String.format(Locale.US, "%,.2f", shown[i] / 100.0);
                float cx = rect.centerX();
                text(cv, name, cx, rect.top + rowH * 0.24f, rowH * 0.2f, 0xFF000000, 0xFFFFFFFF);
                text(cv, value, cx, rect.top + rowH * 0.58f, rowH * 0.34f, 0xFF000000,
                        0xFFFFFFFF);
                if (i >= JackpotBank.MYSTERY_SMALL) {
                    text(cv, String.format(Locale.US, "MUST HIT BY %,.0f",
                            JackpotBank.MUST_HIT_BY[i] / 100.0), cx, rect.top + rowH * 0.87f,
                            rowH * 0.16f, 0xFF000000, 0xFFFFF59D);
                } else {
                    text(cv, "coupons", cx, rect.top + rowH * 0.87f, rowH * 0.14f, 0xFF000000,
                            0xDDFFFFFF);
                }
            }
            if (moving) postInvalidateDelayed(40);
            last = now;
        }

        private void text(Canvas cv, String t, float x, float y, float size, int outline,
                          int fill) {
            paint.setTextSize(size);
            paint.setFakeBoldText(true);
            float base = y - (paint.ascent() + paint.descent()) / 2;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.16f);
            paint.setColor(outline);
            cv.drawText(t, x, base, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            cv.drawText(t, x, base, paint);
            paint.setFakeBoldText(false);
        }
    }

    // ------------------------------------------------------------------ odds

    private void showTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("243 ways: matching symbols on reels next to each other from the left win, "
                + "and more of a symbol on a reel multiplies the win. Pays are for a "
                + "100-credit bet and scale with your bet.\n\n");
        switch (m) {
            case PennyMath.DRAGON:
                pays(sb, new String[]{"🐉", "🐯", "🐟", "🏮", "A", "K", "Q", "J", "10"},
                        PennyMath.DRAGON_PAY);
                sb.append("\n🀄 WILD on reels 2-4 stands in for any symbol.\n🪙 HOLD & SPIN: 6 "
                        + "or more coins lock in place with 3 respins; every new coin resets "
                        + "them. Coins pay 0.4x to 8x your bet, or a MINI, MINOR or MAJOR "
                        + "jackpot. Fill all 15 spots for the GRAND.\n🧧 3, 4 or 5 red "
                        + "envelopes: 8, 12 or 20 free spins with every win doubled (more "
                        + "envelopes add spins).\n\nOdds: hold & spin about 1 in 51 spins, "
                        + "free spins about 1 in 112. Pays back about 98.8% including the "
                        + "jackpots (4 million test spins).");
                break;
            case PennyMath.DRUMS:
                pays(sb, new String[]{"🥁", "💰", "🍊", "🐸", "A", "K", "Q", "J", "10"},
                        PennyMath.DRUM_PAY);
                sb.append("\n🀄 WILD on reels 2-4.\n🥁 THE DRUMS can strike on any spin: pick "
                        + "gold coins until three show the same jackpot, and it's yours.\n"
                        + "🧨 3, 4 or 5 firecrackers: 8, 12 or 20 free spins with every win "
                        + "TRIPLED and more drums on the reels.\n\nOdds: free spins about 1 in "
                        + "77 spins. Pays back about 98.1% including the jackpots (4 million "
                        + "test spins).");
                break;
            case PennyMath.WHEEL:
                pays(sb, new String[]{"🐎", "🐂", "🦅", "🌵", "A", "K", "Q", "J", "10"},
                        PennyMath.WHEEL_PAY);
                sb.append("\n⚡ WILD on reels 2-4.\n🎡 A wheel symbol on reels 2, 3 and 4 spins "
                        + "the JACKPOT WHEEL: credit prizes from 10x to 100x your bet, 8 or "
                        + "12 free spins with wins doubled, or MINI, MINOR, MAJOR or GRAND."
                        + "\n\nOdds: the wheel about 1 in 64 spins. Pays back about 98.4% "
                        + "including the jackpots (4 million test spins).");
                break;
            default:
                pays(sb, new String[]{"Red 7", "Blue 7", "3BAR", "2BAR", "BAR", "🍒"},
                        PennyMath.SEVEN_PAY);
                sb.append("\n🔥 WILD on reels 2-4.\nGOLD 7s pay anywhere on the screen: 3 pay "
                        + "1.5x your bet, 4 pay 4.5x, 5 pay 10x, 6 pay 25x, 7 pay 100x, 8 "
                        + "pay 500x, 9 or more pay 1,000x.\n⭐ BONUS on reels 1, 3 and 5: 10 "
                        + "free spins with wins doubled and more gold 7s (3 more bonus "
                        + "symbols add 5 spins).\nJackpots strike at random.\n\nOdds: "
                        + "something pays about 1 spin in 1.6; free spins about 1 in 122. "
                        + "Pays back about 98.7% including the jackpots (4 million test "
                        + "spins).");
                break;
        }
        sb.append(String.format(Locale.US, "\n\nSHARED JACKPOTS\nEvery penny machine feeds "
                + "the same meters, and they grow with every bet. The bigger your bet, the "
                + "better your chance: at %d credits a spin, about 1 in %,.0f spins hits a "
                + "jackpot. At 100 credits: MINI about 1 in 550 spins, MINOR 1 in 4,200, "
                + "MAJOR 1 in 62,000, GRAND about 1 in a million.\n\nMUST HIT BY mystery "
                + "jackpots drop on a random spin before their meters reach the limit shown, "
                + "on any penny machine.\n\nPlay money only. It's for fun.", bet(),
                1 / JackpotBank.anyChance(bet())));
        new AlertDialog.Builder(this)
                .setTitle(SlotMath.NAMES[m] + ": pays and odds")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private static void pays(StringBuilder sb, String[] names, int[][] pay) {
        for (int i = 0; i < names.length; i++) {
            sb.append(names[i]).append("   ").append(pay[i][0]).append(" / ").append(pay[i][1])
                    .append(" / ").append(pay[i][2]).append('\n');
        }
    }
}
