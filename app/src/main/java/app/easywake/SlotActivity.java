package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
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

import java.util.Locale;
import java.util.Random;

/**
 * Lucky Sevens: a three-reel progressive slot played with Fun Coupons (play money only).
 *
 * Each spin's result is drawn from the odds table in {@link #roll()}, and the reels are
 * then set to show it. The three jackpots grow with every spin and are saved between visits.
 */
public class SlotActivity extends Activity {
    // Symbols, weakest to strongest.
    private static final String CHERRY = "🍒";
    private static final String LEMON = "🍋";
    private static final String BELL = "🔔";
    private static final String STAR = "⭐";
    private static final String DIAMOND = "💎";
    private static final String SEVEN = "7";
    private static final String CROWN = "👑";
    private static final String[] ALL = {CHERRY, LEMON, BELL, STAR, DIAMOND, SEVEN, CROWN};

    private static final int[] BETS = {1, 5, 10, 25};
    private static final int[] BETS_HIGH = {1, 5, 10, 25, 50, 100};
    /** Jackpots are sized for a 25-coupon bet; smaller bets win a matching share. */
    private static final int FULL_BET = 25;

    // Jackpot pots are stored in hundredths of a coupon so small bets still feed them.
    private static final String[] POT_KEYS = {"potMini", "potMajor", "potGrand"};
    private static final String[] POT_NAMES = {"MINI", "MAJOR", "GRAND"};
    private static final int[] POT_SEEDS = {25000, 250000, 2500000};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final ReelView[] reels = new ReelView[3];
    private final TextView[] pots = new TextView[3];
    private TextView balance;
    private TextView message;
    private TextView betText;
    private TextView freeText;
    private Button spinBtn;
    private int betIndex = 1;
    private boolean spinning;
    private int token;

    /** What a spin came to. */
    private static final class Result {
        String[] show = new String[3];
        /** Payout as a multiple of the bet (0 for a loss or a jackpot). */
        int multiple;
        /** 0 mini, 1 major, 2 grand, or -1. */
        int jackpot = -1;
        String text = "";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "slot")) {
            Toast.makeText(this, "Unlock the slot in the skill tree first.", Toast.LENGTH_LONG)
                    .show();
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

        root.addView(Ui.topBar(this, "Lucky Sevens"), Ui.full(this, 0));

        // Jackpot meters.
        LinearLayout meters = new LinearLayout(this);
        meters.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 2; i >= 0; i--) {
            LinearLayout m = new LinearLayout(this);
            m.setOrientation(LinearLayout.VERTICAL);
            m.setGravity(Gravity.CENTER);
            m.setBackground(Ui.card(this, 16));
            m.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
            TextView name = Ui.text(this, POT_NAMES[i], 11, Ui.SUB);
            name.setLetterSpacing(0.15f);
            m.addView(name);
            pots[i] = Ui.text(this, "", i == 2 ? 18 : 16, Ui.ACCENT);
            pots[i].setTypeface(Ui.medium());
            m.addView(pots[i]);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, i == 2 ? 1.3f : 1f);
            int g = Ui.dp(this, 4);
            mp.setMargins(g, 0, g, 0);
            meters.addView(m, mp);
        }
        root.addView(meters, Ui.full(this, 14));

        // Reels.
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable fg = Ui.card(this, 24);
        fg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        frame.setBackground(fg);
        frame.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));
        for (int i = 0; i < 3; i++) {
            ReelView r = new ReelView(this);
            r.setBackground(Ui.round(this, 0x33000000, 16));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, Ui.dp(this, 210),
                    1f);
            int g = Ui.dp(this, 5);
            rp.setMargins(g, 0, g, 0);
            frame.addView(r, rp);
            reels[i] = r;
        }
        root.addView(frame, Ui.full(this, 14));

        message = Ui.text(this, "Good luck.", 18, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        root.addView(message, Ui.full(this, 12));

        balance = Ui.text(this, "", 16, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 4));
        freeText = Ui.text(this, "", 13, Ui.SUB);
        freeText.setGravity(Gravity.CENTER);
        root.addView(freeText, Ui.full(this, 2));

        // Bet selector.
        LinearLayout betRow = new LinearLayout(this);
        betRow.setOrientation(LinearLayout.HORIZONTAL);
        betRow.setGravity(Gravity.CENTER_VERTICAL);
        Button less = Ui.button(this, "−", false);
        less.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        less.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!spinning && betIndex > 0) betIndex--;
                paint();
            }
        });
        Button more = Ui.button(this, "+", false);
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!spinning && betIndex < bets().length - 1) betIndex++;
                paint();
            }
        });
        betText = Ui.text(this, "", 18, Ui.TEXT);
        betText.setGravity(Gravity.CENTER);
        betRow.addView(less, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 56)));
        betRow.addView(betText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        betRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 56)));
        root.addView(betRow, Ui.full(this, 12));

        spinBtn = Ui.button(this, "SPIN", true);
        spinBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        spinBtn.setMinHeight(Ui.dp(this, 76));
        spinBtn.setMinimumHeight(Ui.dp(this, 76));
        spinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spin();
            }
        });
        root.addView(spinBtn, Ui.full(this, 12));

        Button getCoupons = Ui.button(this, "Wallet: get coupons or cash out", false);
        getCoupons.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SlotActivity.this, WalletActivity.class));
            }
        });
        root.addView(getCoupons, Ui.full(this, 10));

        Button table = Ui.button(this, "Pay table and odds", false);
        table.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTable();
            }
        });
        root.addView(table, Ui.full(this, 10));

        root.addView(Ui.text(this, "Play money only. Fun Coupons can't be bought with or "
                + "cashed out for real money.", 12, Ui.SUB), Ui.full(this, 14));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (balance != null) paint();
    }

    @Override
    protected void onDestroy() {
        token++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int[] bets() {
        return Tree.owned(this, "highroller") ? BETS_HIGH : BETS;
    }

    private int pot(int i) {
        return Math.max(POT_SEEDS[i], Prefs.getInt(this, POT_KEYS[i], POT_SEEDS[i]));
    }

    /** Free spins left today (needs the skill-tree perk). */
    private int freeSpins() {
        if (!Tree.owned(this, "freespins")) return 0;
        if (Prefs.getInt(this, "freeSpinDay", 0) != Progress.today()) return 5;
        return Math.max(0, 5 - Prefs.getInt(this, "freeSpinsUsed", 0));
    }

    private void useFreeSpin() {
        int used = Prefs.getInt(this, "freeSpinDay", 0) == Progress.today()
                ? Prefs.getInt(this, "freeSpinsUsed", 0) : 0;
        Prefs.putInt(this, "freeSpinDay", Progress.today());
        Prefs.putInt(this, "freeSpinsUsed", used + 1);
    }

    private void paint() {
        if (betIndex >= bets().length) betIndex = bets().length - 1;
        for (int i = 0; i < 3; i++) {
            pots[i].setText(String.format(Locale.US, "%,d", pot(i) / 100));
        }
        balance.setText(String.format(Locale.US, "%,d Fun Coupons", Wallet.coupons(this)));
        int free = freeSpins();
        freeText.setText(free > 0 ? free + " free spins left today (played at a bet of 5)"
                : (Tree.owned(this, "freespins") ? "Free spins used. More tomorrow." : ""));
        int bet = bets()[betIndex];
        betText.setText("Bet " + bet + (bet < FULL_BET ? "\njackpots pay " + (bet * 100
                / FULL_BET) + "%" : "\nfull jackpots"));
        spinBtn.setText(free > 0 ? "FREE SPIN" : "SPIN");
    }

    private void spin() {
        if (spinning) return;
        final boolean free = freeSpins() > 0;
        final int bet = free ? 5 : bets()[betIndex];
        if (!free) {
            if (Wallet.coupons(this) < bet) {
                message.setText("Not enough coupons.\nGet more in the Wallet.");
                return;
            }
            Wallet.addCoupons(this, -bet);
        } else {
            useFreeSpin();
        }
        // Every spin feeds the jackpots: 1% of the bet to each.
        for (int i = 0; i < 3; i++) {
            Prefs.putInt(this, POT_KEYS[i], pot(i) + bet);
        }

        final Result res = roll();
        spinning = true;
        spinBtn.setAlpha(0.5f);
        message.setText("");
        paint();

        // Each reel scrolls and slows to a stop, left to right.
        final int mine = ++token;
        long[] durations = {1900, 2700, 3600};
        for (int i = 0; i < 3; i++) {
            final boolean last = i == 2;
            reels[i].spinTo(res.show[i], durations[i], new Runnable() {
                @Override
                public void run() {
                    if (last && mine == token) settle(res, bet);
                }
            });
        }
    }

    private void settle(Result res, int bet) {
        spinning = false;
        spinBtn.setAlpha(1f);
        int won = 0;
        if (res.jackpot >= 0) {
            // Smaller bets win a matching share of the pot; the rest stays in it.
            int share = (int) ((long) pot(res.jackpot) * Math.min(bet, FULL_BET) / FULL_BET);
            won = Math.max(1, share / 100);
            int left = pot(res.jackpot) - share;
            Prefs.putInt(this, POT_KEYS[res.jackpot], Math.max(POT_SEEDS[res.jackpot], left));
            message.setText(POT_NAMES[res.jackpot] + " JACKPOT!\n+" + String.format(Locale.US,
                    "%,d", won) + " coupons");
        } else if (res.multiple > 0) {
            won = res.multiple * bet;
            message.setText(res.text + "\n+" + won + " coupons");
        } else {
            message.setText(res.text.isEmpty() ? "No win." : res.text);
        }
        if (won > 0) Wallet.addCoupons(this, won);
        paint();
    }

    private String other(String... not) {
        for (int guard = 0; guard < 50; guard++) {
            String s = ALL[rnd.nextInt(4)];
            boolean clash = false;
            for (String n : not) {
                if (n.equals(s)) clash = true;
            }
            if (!clash) return s;
        }
        return LEMON;
    }

    private static Result three(String sym, int multiple, String text) {
        Result r = new Result();
        r.show = new String[]{sym, sym, sym};
        r.multiple = multiple;
        r.text = text;
        return r;
    }

    /**
     * Draws one spin. The chances below are the real odds shown in the pay table.
     * Out of 60,000: grand 3, major 24, mini 200, stars 150, bells 500, lemons 1000,
     * cherries 2000, two cherries 5000, one cherry 7500, and the rest lose.
     */
    private Result roll() {
        int n = rnd.nextInt(60000);
        if (n < 3) {
            Result r = three(CROWN, 0, "");
            r.jackpot = 2;
            return r;
        }
        if (n < 27) {
            Result r = three(SEVEN, 0, "");
            r.jackpot = 1;
            return r;
        }
        if (n < 227) {
            Result r = three(DIAMOND, 0, "");
            r.jackpot = 0;
            return r;
        }
        if (n < 377) return three(STAR, 40, "Three stars! 40x");
        if (n < 877) return three(BELL, 15, "Three bells! 15x");
        if (n < 1877) return three(LEMON, 8, "Three lemons. 8x");
        if (n < 3877) return three(CHERRY, 5, "Three cherries. 5x");
        Result r = new Result();
        if (n < 8877) {
            r.show = new String[]{CHERRY, CHERRY, other(CHERRY)};
            r.multiple = 2;
            r.text = "Two cherries. 2x";
            return r;
        }
        if (n < 16377) {
            String second = other(CHERRY);
            r.show = new String[]{CHERRY, second, ALL[rnd.nextInt(ALL.length)]};
            r.multiple = 1;
            r.text = "A cherry. Bet back.";
            return r;
        }
        // A loss: three reels that don't line up, with no staged near misses.
        String a = other(CHERRY);
        String b = other(CHERRY, a);
        r.show = new String[]{a, b, ALL[rnd.nextInt(ALL.length)]};
        return r;
    }

    private void showTable() {
        String t = CROWN + CROWN + CROWN + "   GRAND jackpot   (1 in 20,000)\n"
                + SEVEN + " " + SEVEN + " " + SEVEN + "   MAJOR jackpot   (1 in 2,500)\n"
                + DIAMOND + DIAMOND + DIAMOND + "   MINI jackpot   (1 in 300)\n"
                + STAR + STAR + STAR + "   40x your bet   (1 in 400)\n"
                + BELL + BELL + BELL + "   15x   (1 in 120)\n"
                + LEMON + LEMON + LEMON + "   8x   (1 in 60)\n"
                + CHERRY + CHERRY + CHERRY + "   5x   (1 in 30)\n"
                + CHERRY + CHERRY + " on the first two reels   2x   (1 in 12)\n"
                + CHERRY + " on the first reel   bet back   (1 in 8)\n\n"
                + "Every spin adds 1% of the bet to each jackpot, and they never drop below "
                + "their starting size. Jackpots are sized for a 25-coupon bet: a smaller "
                + "bet wins the same share of the pot (a bet of 5 wins 20%).\n\n"
                + "A bit more than 1 spin in 4 pays something. Over a long run the machine pays "
                + "back roughly 97 coupons for every 100 bet, so like any slot it slowly "
                + "wins. It's for fun, with play money only.";
        new AlertDialog.Builder(this)
                .setTitle("Pay table and odds")
                .setMessage(t)
                .setPositiveButton("Close", (DialogInterface.OnClickListener) null)
                .show();
    }

    /**
     * One reel: a strip of symbols that scrolls downward, runs at full speed for a while,
     * then slows and settles onto its result with a small bounce. It keeps its own clock
     * rather than using the system's animations, so it still spins on phones that have
     * animations switched off.
     */
    static final class ReelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private String[] strip;
        /** Which strip position sits on the pay line; fractions are mid-scroll. */
        private float pos = 1f;
        private boolean spinning;
        private long startAt;
        private long duration;
        private float from;
        private float to;
        private Runnable done;

        ReelView(android.content.Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Ui.ACCENT);
            paint.setTypeface(Ui.medium());
            line.setColor((Ui.ACCENT & 0x00FFFFFF) | 0x55000000);
            line.setStrokeWidth(Ui.dp(c, 2));
            strip = new String[]{pick(), pick(), pick()};
        }

        private String pick() {
            return ALL[rnd.nextInt(ALL.length)];
        }

        /** Scrolls to show {@code target} on the pay line, then calls {@code whenDone}. */
        void spinTo(String target, long millis, Runnable whenDone) {
            // Keep what is showing now at the start of the new strip so nothing jumps.
            int at = Math.round(pos);
            String above = get(at + 1);
            String center = get(at);
            String below = get(at - 1);
            // About 14 symbols a second at full speed.
            int length = 6 + (int) (millis * 14 / 1000);
            String[] next = new String[length];
            for (int i = 0; i < length; i++) next[i] = pick();
            next[0] = below;
            next[1] = center;
            next[2] = above;
            next[length - 2] = target;
            strip = next;
            from = 1f;
            to = length - 2;
            pos = from;
            duration = Math.max(200, millis);
            startAt = android.os.SystemClock.uptimeMillis();
            done = whenDone;
            spinning = true;
            invalidate();
        }

        /** Progress 0..1 to distance 0..1: steady speed, then braking to a stop. */
        private static float ease(float t) {
            float brakeAt = 0.62f;
            // Speed is constant until brakeAt, then falls to zero; this is its integral,
            // scaled so the total distance comes out as 1.
            float full = brakeAt + (1 - brakeAt) / 2f;
            float d;
            if (t < brakeAt) {
                d = t / full;
            } else {
                float u = (t - brakeAt) / (1 - brakeAt);
                d = (brakeAt + (1 - brakeAt) * (u - u * u / 2f)) / full;
            }
            return d;
        }

        private String get(int i) {
            if (strip.length == 0) return "";
            return strip[((i % strip.length) + strip.length) % strip.length];
        }

        @Override
        protected void onDetachedFromWindow() {
            spinning = false;
            done = null;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            boolean finished = false;
            if (spinning) {
                float t = (android.os.SystemClock.uptimeMillis() - startAt) / (float) duration;
                if (t >= 1f) {
                    pos = to;
                    spinning = false;
                    finished = true;
                } else {
                    pos = from + (to - from) * ease(t);
                    // Run a fifth of a symbol past the stop and drop back, like a real reel.
                    if (t > 0.9f) {
                        pos += 0.2f * (float) Math.sin(Math.PI * (t - 0.9f) / 0.1f);
                    }
                }
            }

            float cell = h / 3f;
            paint.setTextSize(Math.min(cell * 0.62f, w * 0.6f));
            float mid = h / 2f;
            float lift = (paint.ascent() + paint.descent()) / 2f;
            int base = (int) Math.floor(pos);
            cv.save();
            cv.clipRect(0, 0, w, h);
            for (int i = base - 2; i <= base + 3; i++) {
                // Higher strip positions enter from the top and travel down.
                float y = mid - (i - pos) * cell;
                float away = Math.min(1f, Math.abs(y - mid) / cell);
                paint.setAlpha((int) (255 - 150 * away));
                cv.drawText(get(i), w / 2f, y - lift, paint);
            }
            cv.restore();
            // Pay line markers on either side of the middle row.
            cv.drawLine(0, mid - cell / 2f, w, mid - cell / 2f, line);
            cv.drawLine(0, mid + cell / 2f, w, mid + cell / 2f, line);

            if (spinning) {
                postInvalidateOnAnimation();
            } else if (finished) {
                // A small thud as the reel lands.
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                Runnable r = done;
                done = null;
                if (r != null) post(r);
            }
        }
    }
}
