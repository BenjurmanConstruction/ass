package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Lucky Sevens: a classic three-reel slot with bars, sevens and a 3x wild diamond, played
 * with Fun Coupons (play money only).
 *
 * Each reel is a fixed strip of 61 stops, most of them holding a symbol. A spin picks a random stop on each reel, the
 * three rows around it are shown, and every line you bet on is paid from the table in
 * {@link #linePay}. The odds on the pay table screen come from counting every possible
 * combination of this strip.
 */
public class SlotActivity extends Activity {
    // Symbol codes used on the strip.
    static final char WILD = 'W';
    static final char SEVEN = '7';
    static final char BAR3 = '3';
    static final char BAR2 = '2';
    static final char BAR1 = '1';
    static final char CHERRY = 'C';
    static final char BELL = 'G';
    static final char GRAPES = 'P';
    static final char LEMON = 'L';
    static final char BLANK = 'X';

    /**
     * The reel strip: 2 wilds, 3 sevens, 4 triple bars, 6 double, 7 single, 3 cherries,
     * 7 bells, 8 grapes, 9 lemons and 12 blanks.
     */
    private static final String STRIP =
            "G2G2L1XG2P1L7CGLXP123PL73XG1LPX7XP2XP3X1L2C3XLGPWCX1LXPWGLX1X";

    /** Which row (0 top, 1 middle, 2 bottom) each line uses on reels 1, 2 and 3. */
    static final int[][] LINES = {
            {1, 1, 1}, {0, 0, 0}, {2, 2, 2}, {0, 1, 2}, {2, 1, 0}, {0, 1, 0}, {2, 1, 2}
    };
    private static final int[] LINE_CHOICES = {1, 3, 5, 7};
    private static final int[] BETS = {1, 2, 5, 10, 25};
    private static final int[] BETS_HIGH = {1, 2, 5, 10, 25, 50, 100};
    /** Jackpots are sized for a 25-coupon line bet; smaller bets win a matching share. */
    private static final int FULL_BET = 25;

    // Jackpot pots are stored in hundredths of a coupon so small bets still feed them.
    // New keys, so pots from the older single-line machine don't carry over.
    private static final String[] POT_KEYS = {"pot2Mini", "pot2Major", "pot2Grand"};
    private static final String[] POT_NAMES = {"MINI", "MAJOR", "GRAND"};
    private static final int[] POT_SEEDS = {100000, 500000, 5000000};

    private final Random rnd = new Random();
    private final ReelView[] reels = new ReelView[3];
    private final TextView[] pots = new TextView[3];
    private LinesView linesView;
    private TextView balance;
    private TextView message;
    private TextView betText;
    private TextView freeText;
    private Button spinBtn;
    private Button linesBtn;
    private int betIndex = 0;
    private int lineChoice = 1;
    private boolean spinning;
    private int token;
    private final Stats.Sitting sitting = new Stats.Sitting("Slot", "spin");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "slot")) {
            Toast.makeText(this, "Unlock the slot in the skill tree first.", Toast.LENGTH_LONG)
                    .show();
            finish();
            return;
        }
        lineChoice = Math.max(0, Math.min(LINE_CHOICES.length - 1,
                Prefs.getInt(this, "slotLines", 1)));

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

        // Reels, with the pay lines drawn over the top.
        FrameLayout cabinet = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable fg = Ui.card(this, 24);
        fg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        cabinet.setBackground(fg);
        int inset = Ui.dp(this, 12);
        cabinet.setPadding(inset, inset, inset, inset);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            ReelView r = new ReelView(this);
            r.setBackground(Ui.round(this, 0x33000000, 14));
            int at = rnd.nextInt(STRIP.length());
            r.show(sym(at - 1), sym(at), sym(at + 1));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            frame.addView(r, rp);
            reels[i] = r;
        }
        cabinet.addView(frame, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        linesView = new LinesView(this);
        cabinet.addView(linesView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 290));
        cp.topMargin = Ui.dp(this, 14);
        root.addView(cabinet, cp);

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

        // Lines and bet-per-line selectors.
        LinearLayout betRow = new LinearLayout(this);
        betRow.setOrientation(LinearLayout.HORIZONTAL);
        betRow.setGravity(Gravity.CENTER_VERTICAL);
        linesBtn = Ui.button(this, "", false);
        linesBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        linesBtn.setPadding(0, 0, 0, 0);
        linesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spinning) return;
                lineChoice = (lineChoice + 1) % LINE_CHOICES.length;
                Prefs.putInt(SlotActivity.this, "slotLines", lineChoice);
                linesView.setWins(null);
                paint();
            }
        });
        Button less = Ui.button(this, "−", false);
        less.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        less.setPadding(0, 0, 0, 0);
        less.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!spinning && betIndex > 0) betIndex--;
                paint();
            }
        });
        Button more = Ui.button(this, "+", false);
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        more.setPadding(0, 0, 0, 0);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!spinning && betIndex < bets().length - 1) betIndex++;
                paint();
            }
        });
        betText = Ui.text(this, "", 15, Ui.TEXT);
        betText.setGravity(Gravity.CENTER);
        betRow.addView(linesBtn, new LinearLayout.LayoutParams(Ui.dp(this, 92),
                Ui.dp(this, 56)));
        LinearLayout.LayoutParams lessP = new LinearLayout.LayoutParams(Ui.dp(this, 52),
                Ui.dp(this, 56));
        lessP.leftMargin = Ui.dp(this, 8);
        betRow.addView(less, lessP);
        betRow.addView(betText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        betRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 56)));
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
    protected void onPause() {
        super.onPause();
        sitting.close(this);
    }

    @Override
    protected void onDestroy() {
        token++;
        super.onDestroy();
    }

    private static char sym(int i) {
        int n = STRIP.length();
        return STRIP.charAt(((i % n) + n) % n);
    }

    private int[] bets() {
        return Tree.owned(this, "highroller") ? BETS_HIGH : BETS;
    }

    private int lines() {
        return LINE_CHOICES[lineChoice];
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
        freeText.setText(free > 0 ? free + " free spins left today (all 7 lines at 1 each)"
                : (Tree.owned(this, "freespins") ? "Free spins used. More tomorrow." : ""));
        int bet = bets()[betIndex];
        linesBtn.setText(lines() == 1 ? "1 line" : lines() + " lines");
        betText.setText(bet + " per line • total " + bet * lines() + "\n"
                + (bet < FULL_BET ? "jackpots pay " + (bet * 100 / FULL_BET) + "%"
                : "full jackpots"));
        spinBtn.setText(free > 0 ? "FREE SPIN" : "SPIN");
        if (!spinning) linesView.setActive(free > 0 ? LINES.length : lines());
    }

    private void spin() {
        if (spinning) return;
        final boolean free = freeSpins() > 0;
        final int bet = free ? 1 : bets()[betIndex];
        final int lineCount = free ? LINES.length : lines();
        final int total = bet * lineCount;
        if (!free) {
            if (Wallet.coupons(this) < total) {
                message.setText("Not enough coupons for a bet of " + total
                        + ".\nLower the bet or get more in the Wallet.");
                return;
            }
            Wallet.addCoupons(this, -total);
        } else {
            useFreeSpin();
        }
        // Every spin feeds the jackpots: 1% of the total bet to each.
        for (int i = 0; i < 3; i++) {
            Prefs.putInt(this, POT_KEYS[i], pot(i) + total);
        }

        // Pick where each reel stops. Everything else follows from these three numbers.
        final char[][] grid = new char[3][3];
        for (int r = 0; r < 3; r++) {
            int stop = rnd.nextInt(STRIP.length());
            grid[0][r] = sym(stop - 1);
            grid[1][r] = sym(stop);
            grid[2][r] = sym(stop + 1);
        }

        linesView.setActive(lineCount);
        linesView.setWins(null);
        spinning = true;
        spinBtn.setAlpha(0.5f);
        message.setText("");
        paint();

        final int mine = ++token;
        long[] durations = {1900, 2700, 3600};
        for (int i = 0; i < 3; i++) {
            final boolean last = i == 2;
            reels[i].spinTo(grid[0][i], grid[1][i], grid[2][i], durations[i], new Runnable() {
                @Override
                public void run() {
                    if (last && mine == token) settle(grid, bet, lineCount, free);
                }
            });
        }
    }

    /**
     * What one line pays, as a multiple of the line bet. Jackpots are returned as
     * negative numbers: -1 mini, -2 major, -3 grand.
     */
    static int linePay(char a, char b, char c) {
        char[] s = {a, b, c};
        int wilds = 0;
        List<Character> rest = new ArrayList<>();
        for (char x : s) {
            if (x == WILD) wilds++;
            else rest.add(x);
        }
        if (wilds == 3) return -3;
        if (wilds > 0) {
            int mult = wilds == 1 ? 3 : 9;
            boolean same = rest.size() == 1 || rest.get(0).equals(rest.get(1));
            if (same) {
                char x = rest.get(0);
                if (x == BLANK) return wilds == 1 ? 1 : 5;
                return base(x) * mult;
            }
            if (isBar(rest.get(0)) && isBar(rest.get(1))) return 2 * mult;
            if (rest.contains(CHERRY)) return 2 * mult;
            return 1;
        }
        if (a == b && b == c) {
            if (a == SEVEN) return -2;
            if (a == BAR3) return -1;
            if (a == BLANK) return 0;
            return base(a);
        }
        if (isBar(a) && isBar(b) && isBar(c)) return 2;
        int cherries = 0;
        for (char x : s) {
            if (x == CHERRY) cherries++;
        }
        if (cherries == 2) return 2;
        if (cherries == 1) return 1;
        return 0;
    }

    private static boolean isBar(char x) {
        return x == BAR1 || x == BAR2 || x == BAR3;
    }

    /** Pay for three of a kind (when a wild helps complete it, for sevens and triple bars). */
    private static int base(char x) {
        switch (x) {
            case SEVEN:
                return 60;
            case BAR3:
                return 30;
            case BAR2:
                return 12;
            case CHERRY:
                return 8;
            case BAR1:
                return 6;
            case BELL:
                return 5;
            case GRAPES:
                return 3;
            case LEMON:
                return 2;
            default:
                return 0;
        }
    }

    private void settle(char[][] grid, int bet, int lineCount, boolean free) {
        spinning = false;
        spinBtn.setAlpha(1f);
        int won = 0;
        int bestJackpot = -1;
        boolean[] wins = new boolean[LINES.length];
        int winLines = 0;
        for (int l = 0; l < lineCount; l++) {
            int[] rows = LINES[l];
            int p = linePay(grid[rows[0]][0], grid[rows[1]][1], grid[rows[2]][2]);
            if (p == 0) continue;
            wins[l] = true;
            winLines++;
            if (p < 0) {
                int which = -p - 1;
                // Smaller bets win a matching share of the pot; the rest stays in it.
                int share = (int) ((long) pot(which) * Math.min(bet, FULL_BET) / FULL_BET);
                won += Math.max(1, share / 100);
                Prefs.putInt(this, POT_KEYS[which],
                        Math.max(POT_SEEDS[which], pot(which) - share));
                bestJackpot = Math.max(bestJackpot, which);
            } else {
                won += p * bet;
            }
        }
        linesView.setWins(winLines > 0 ? wins : null);
        if (bestJackpot >= 0) {
            message.setText(POT_NAMES[bestJackpot] + " JACKPOT!\n+" + String.format(Locale.US,
                    "%,d", won) + " coupons");
        } else if (won > 0) {
            message.setText((winLines == 1 ? "Winner on 1 line" : "Winner on " + winLines
                    + " lines") + "\n+" + String.format(Locale.US, "%,d", won) + " coupons");
        } else {
            message.setText("No win.");
        }
        if (won > 0) Wallet.addCoupons(this, won);
        Stats.bet(this, "slot", free ? 0 : bet * lineCount, won);
        sitting.round(free ? 0 : bet * lineCount, won);
        paint();
    }

    private void showTable() {
        String t = "Pays are multiples of your bet per line.\n\n"
                + "💎 💎 💎   GRAND jackpot\n"
                + "7 7 7   MAJOR jackpot\n"
                + "Three triple BARs   MINI jackpot\n"
                + "Three double BARs   12x\n"
                + "Three cherries   8x\n"
                + "Three single BARs   6x\n"
                + "Three bells   5x\n"
                + "Three grapes   3x\n"
                + "Three lemons   2x\n"
                + "Any three BARs, mixed   2x\n"
                + "Any two cherries   2x\n"
                + "Any one cherry   1x\n\n"
                + "The diamond is wild. It stands in for any symbol, and it multiplies the "
                + "win: one diamond pays 3 times the normal amount, two diamonds pay 9 "
                + "times. With a diamond helping, three 7s pay 60x and three triple BARs "
                + "30x before the multiplier (the jackpots need the real symbols). One "
                + "diamond on a line with nothing else pays 1x; two pay 5x.\n\n"
                + "Lines: 1 line is the middle row. 3 lines adds the top and bottom rows. "
                + "5 lines adds both criss-cross diagonals. 7 lines adds a V and an "
                + "upside-down V. Your total bet is the line bet times the number of "
                + "lines.\n\n"
                + "Odds per line, per spin: Grand 1 in 28,373. Major 1 in 8,407. Mini 1 in "
                + "3,547. About 1 line in 4 pays something.\n\n"
                + "Every spin adds 1% of the total bet to each jackpot, and they never drop "
                + "below their starting size. Jackpots are sized for a 25-coupon line bet: "
                + "a smaller line bet wins the same share of the pot.\n\n"
                + "Over a long run the machine pays back roughly 95 coupons for every 100 "
                + "bet, so like any slot it slowly wins. It's for fun, with play money only.";
        new AlertDialog.Builder(this)
                .setTitle("Pay table and odds")
                .setMessage(t)
                .setPositiveButton("Close", (DialogInterface.OnClickListener) null)
                .show();
    }

    /**
     * Draws over the reels: a soft band behind each of the three rows, and the pay lines
     * in play. Each line has its own color and sits a little above or below its
     * neighbors so they can be told apart. Winning lines turn bright.
     */
    static final class LinesView extends View {
        private static final int[] COLORS = {0xFFFFD54F, 0xFF4DD0E1, 0xFFBA68C8, 0xFF81C784,
                0xFFFF8A65, 0xFF64B5F6, 0xFFF06292};
        /** How far each line is nudged off the middle of its row, in dp. */
        private static final float[] NUDGE = {0, 0, 0, -14, 14, -28, 28};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tag = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final android.graphics.Path path = new android.graphics.Path();
        private int active = 1;
        private boolean[] wins;

        LinesView(Context c) {
            super(c);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            tag.setTextAlign(Paint.Align.CENTER);
            tag.setFakeBoldText(true);
        }

        void setActive(int n) {
            active = n;
            invalidate();
        }

        void setWins(boolean[] w) {
            wins = w;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float d = getResources().getDisplayMetrics().density;
            float cell = h / 3f;

            // The three rows.
            paint.setStyle(Paint.Style.FILL);
            for (int r = 0; r < 3; r++) {
                paint.setColor(r == 1 ? 0x1FFFFFFF : 0x10FFFFFF);
                rect.set(3 * d, r * cell + 3 * d, w - 3 * d, (r + 1) * cell - 3 * d);
                cv.drawRoundRect(rect, 12 * d, 12 * d, paint);
            }

            float[] colX = {w / 6f, w / 2f, w * 5f / 6f};
            float edge = 15 * d;
            tag.setTextSize(9 * d);
            for (int l = 0; l < LINES.length && l < active; l++) {
                boolean win = wins != null && wins[l];
                // After a spin, fade the lines that didn't win so the winners stand out.
                int alpha = win ? 0xFF : (wins != null ? 0x30 : 0x80);
                int color = (COLORS[l] & 0x00FFFFFF) | (alpha << 24);
                int[] rows = LINES[l];
                float off = NUDGE[l] * d;
                float y0 = rows[0] * cell + cell / 2f + off;
                float y1 = rows[1] * cell + cell / 2f + off;
                float y2 = rows[2] * cell + cell / 2f + off;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth((win ? 4.5f : 2f) * d);
                paint.setColor(color);
                path.reset();
                path.moveTo(edge, y0);
                path.lineTo(colX[0], y0);
                path.lineTo(colX[1], y1);
                path.lineTo(colX[2], y2);
                path.lineTo(w - edge, y2);
                cv.drawPath(path, paint);

                // A numbered tag at each end of the line.
                paint.setStyle(Paint.Style.FILL);
                cv.drawCircle(edge - 7 * d, y0, 7 * d, paint);
                cv.drawCircle(w - edge + 7 * d, y2, 7 * d, paint);
                tag.setColor(0xFF10141F);
                tag.setAlpha(Math.min(255, alpha + 0x60));
                float lift = (tag.ascent() + tag.descent()) / 2f;
                cv.drawText(String.valueOf(l + 1), edge - 7 * d, y0 - lift, tag);
                cv.drawText(String.valueOf(l + 1), w - edge + 7 * d, y2 - lift, tag);
            }
        }
    }

    /**
     * One reel: a strip of symbols that scrolls downward, runs at full speed for a while,
     * then slows and settles onto its result with a small bounce. It keeps its own clock
     * rather than using the system's animations, so it still spins on phones that have
     * animations switched off.
     */
    static final class ReelView extends View {
        private static final char[] FILL = {LEMON, BAR1, GRAPES, BAR2, CHERRY, BLANK, BAR3,
                BELL, SEVEN, BAR1, LEMON, WILD, BLANK, BAR2, GRAPES, BELL, LEMON, BLANK};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Random rnd = new Random();
        private char[] strip = {BLANK, BLANK, BLANK};
        /** Which strip position sits on the middle row; fractions are mid-scroll. */
        private float pos = 1f;
        private boolean spinning;
        private long startAt;
        private long duration;
        private float from;
        private float to;
        private Runnable done;

        ReelView(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
            box.setStyle(Paint.Style.STROKE);
        }

        /** Shows three symbols without spinning. */
        void show(char top, char middle, char bottom) {
            strip = new char[]{bottom, middle, top};
            pos = 1f;
            invalidate();
        }

        /** Scrolls until the given three symbols fill the window, then calls back. */
        void spinTo(char top, char middle, char bottom, long millis, Runnable whenDone) {
            // Keep what is showing now at the start of the new strip so nothing jumps.
            int at = Math.round(pos);
            char above = get(at + 1);
            char center = get(at);
            char below = get(at - 1);
            // About 14 symbols a second at full speed.
            int length = 7 + (int) (millis * 14 / 1000);
            char[] next = new char[length];
            for (int i = 0; i < length; i++) next[i] = FILL[rnd.nextInt(FILL.length)];
            next[0] = below;
            next[1] = center;
            next[2] = above;
            next[length - 3] = bottom;
            next[length - 2] = middle;
            next[length - 1] = top;
            strip = next;
            from = 1f;
            to = length - 2;
            pos = from;
            duration = Math.max(200, millis);
            startAt = SystemClock.uptimeMillis();
            done = whenDone;
            spinning = true;
            invalidate();
        }

        /** Progress 0..1 to distance 0..1: steady speed, then braking to a stop. */
        private static float ease(float t) {
            float brakeAt = 0.62f;
            float full = brakeAt + (1 - brakeAt) / 2f;
            if (t < brakeAt) return t / full;
            float u = (t - brakeAt) / (1 - brakeAt);
            return (brakeAt + (1 - brakeAt) * (u - u * u / 2f)) / full;
        }

        private char get(int i) {
            // Past either end of the strip, show blanks.
            if (i < 0 || i >= strip.length) return BLANK;
            return strip[i];
        }

        @Override
        protected void onDetachedFromWindow() {
            spinning = false;
            done = null;
            super.onDetachedFromWindow();
        }

        private void drawSymbol(Canvas cv, char s, float cx, float cy, float cell, int alpha) {
            float d = getResources().getDisplayMetrics().density;
            paint.setFakeBoldText(false);
            if (s == BLANK) return;
            if (s == SEVEN) {
                paint.setColor(0xFFFF5252);
                paint.setAlpha(alpha);
                paint.setFakeBoldText(true);
                paint.setTextSize(cell * 0.78f);
                cv.drawText("7", cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
                paint.setFakeBoldText(false);
                return;
            }
            if (s == CHERRY || s == WILD || s == BELL || s == GRAPES || s == LEMON) {
                String face = s == WILD ? "\uD83D\uDC8E" : (s == CHERRY ? "\uD83C\uDF52"
                        : (s == BELL ? "\uD83D\uDD14" : (s == GRAPES ? "\uD83C\uDF47"
                        : "\uD83C\uDF4B")));
                paint.setColor(0xFFFFFFFF);
                paint.setAlpha(alpha);
                paint.setTextSize(cell * (s == WILD ? 0.5f : 0.56f));
                float lift = (paint.ascent() + paint.descent()) / 2f;
                float y = s == WILD ? cy - cell * 0.1f : cy;
                cv.drawText(face, cx, y - lift, paint);
                if (s == WILD) {
                    paint.setColor(Ui.ACCENT);
                    paint.setAlpha(alpha);
                    paint.setFakeBoldText(true);
                    paint.setTextSize(cell * 0.2f);
                    cv.drawText("WILD 3x", cx, cy + cell * 0.4f, paint);
                    paint.setFakeBoldText(false);
                }
                return;
            }
            // Bars: one, two or three stacked plates.
            int n = s == BAR1 ? 1 : (s == BAR2 ? 2 : 3);
            float plateH = cell * 0.2f;
            float gap = cell * 0.045f;
            float totalH = n * plateH + (n - 1) * gap;
            float halfW = Math.min(getWidth() * 0.36f, cell * 0.55f);
            paint.setTextSize(plateH * 0.74f);
            paint.setFakeBoldText(true);
            box.setStrokeWidth(1.5f * d);
            for (int i = 0; i < n; i++) {
                float top = cy - totalH / 2f + i * (plateH + gap);
                rect.set(cx - halfW, top, cx + halfW, top + plateH);
                paint.setColor(0xFF10141F);
                paint.setAlpha(alpha);
                cv.drawRoundRect(rect, 4 * d, 4 * d, paint);
                box.setColor(Ui.ACCENT);
                box.setAlpha(alpha);
                cv.drawRoundRect(rect, 4 * d, 4 * d, box);
                paint.setColor(Ui.ACCENT_HI);
                paint.setAlpha(alpha);
                cv.drawText("BAR", cx, top + plateH / 2f
                        - (paint.ascent() + paint.descent()) / 2f, paint);
            }
            paint.setFakeBoldText(false);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            boolean finished = false;
            if (spinning) {
                float t = (SystemClock.uptimeMillis() - startAt) / (float) duration;
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
            float mid = h / 2f;
            int base = (int) Math.floor(pos);
            cv.save();
            cv.clipRect(0, 0, w, h);
            for (int i = base - 2; i <= base + 3; i++) {
                // Higher strip positions enter from the top and travel down.
                float y = mid - (i - pos) * cell;
                drawSymbol(cv, get(i), w / 2f, y, cell, 255);
            }
            cv.restore();

            if (spinning) {
                postInvalidateOnAnimation();
            } else if (finished) {
                // A small thud as the reel lands.
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                Runnable r = done;
                done = null;
                if (r != null) post(r);
            }
        }
    }
}
