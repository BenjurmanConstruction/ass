package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.Locale;
import java.util.Random;

/**
 * Royal Riches: a five-reel video slot with ten pay lines, a wild star and a bonus
 * symbol that awards free spins. Played with Fun Coupons (play money only).
 *
 * Each reel is a fixed strip. A spin picks a random stop on each reel and every line is
 * paid from the left. The pay table and odds on screen were worked out from these strips.
 */
public class Slot2Activity extends Activity {
    // Symbol codes: W wild, S bonus, A to C the picture symbols, D to H the card ranks.
    private static final String[] STRIPS = {
            "HBCAHDSDGFEHECWEGHGFBFEFDFHAGCGD",
            "CEHGDHFDEGEFWHCAHWFGBSAEDCFGBGFHD",
            "FBGCGSBEGEGEFADGHCHWFHFHAFDWECDHD",
            "EFDHDFHCWHDEHGFHDEGCAWBFECGBFGSGA",
            "EDBDHFGEGFCEGHDAWGBGFEHDFASHCFHC"};
    private static final String ORDER = "ABCDEFGH";
    /** What three, four and five of each symbol pay, as a multiple of the line bet. */
    private static final int[][] PAYS = {{40, 200, 1000}, {30, 120, 500}, {25, 80, 300},
            {15, 50, 200}, {10, 40, 150}, {8, 25, 100}, {6, 20, 80}, {6, 20, 80}};
    private static final int FIVE_WILDS = 1000;
    /** Which row (0 top, 1 middle, 2 bottom) each line uses on reels 1 to 5. */
    private static final int[][] LINES = {{1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2},
            {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2}, {0, 0, 1, 2, 2}, {2, 2, 1, 0, 0},
            {1, 0, 0, 0, 1}, {1, 2, 2, 2, 1}, {0, 1, 1, 1, 0}};
    private static final int[] BETS = {1, 2, 5, 10};
    private static final int[] BETS_HIGH = {1, 2, 5, 10, 25};
    private static final int FREE_SPINS = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final Stats.Sitting sitting = new Stats.Sitting("Royal Riches", "spin");
    private final Reel[] reels = new Reel[5];
    private Overlay overlay;
    private TextView balance;
    private TextView message;
    private TextView betText;
    private Button spinBtn;
    private int betIndex;
    private int freeLeft;
    private int freeBet;
    private int freeWon;
    private boolean spinning;
    private int token;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "slot2")) {
            Toast.makeText(this, "Unlock Royal Riches in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.topBar(this, "Royal Riches"), Ui.full(this, 0));

        FrameLayout cabinet = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable fg = Ui.round(this, 0xCC2A1245, 22);
        fg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        cabinet.setBackground(fg);
        int inset = Ui.dp(this, 8);
        cabinet.setPadding(inset, inset, inset, inset);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            Reel r = new Reel(this);
            int at = rnd.nextInt(STRIPS[i].length());
            r.show(sym(i, at - 1), sym(i, at), sym(i, at + 1));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            int g = Ui.dp(this, 2);
            rp.setMargins(g, 0, g, 0);
            r.setBackground(Ui.round(this, 0x44000000, 10));
            frame.addView(r, rp);
            reels[i] = r;
        }
        cabinet.addView(frame, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay = new Overlay(this);
        cabinet.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 230));
        cp.topMargin = Ui.dp(this, 12);
        root.addView(cabinet, cp);

        message = Ui.text(this, "Ten lines, paid from the left. Three bonus gifts win "
                + "free spins.", 16, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        root.addView(message, Ui.full(this, 10));
        balance = Ui.text(this, "", 16, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 4));

        LinearLayout betRow = new LinearLayout(this);
        betRow.setOrientation(LinearLayout.HORIZONTAL);
        betRow.setGravity(Gravity.CENTER_VERTICAL);
        Button less = step("−", -1);
        Button more = step("+", 1);
        betText = Ui.text(this, "", 15, Ui.TEXT);
        betText.setGravity(Gravity.CENTER);
        betRow.addView(less, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 54)));
        betRow.addView(betText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        betRow.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 54)));
        root.addView(betRow, Ui.full(this, 10));

        spinBtn = Ui.button(this, "SPIN", true);
        spinBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        spinBtn.setMinHeight(Ui.dp(this, 72));
        spinBtn.setMinimumHeight(Ui.dp(this, 72));
        spinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spin();
            }
        });
        root.addView(spinBtn, Ui.full(this, 10));
        root.addView(Sfx.toggle(this), Ui.full(this, 10));

        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Slot2Activity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 10));
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
        paint();
    }

    private Button step(String label, final int dir) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bt.setPadding(0, 0, 0, 0);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spinning || freeLeft > 0) return;
                betIndex = Math.max(0, Math.min(bets().length - 1, betIndex + dir));
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
        Sfx.stop(Sfx.SPIN5);
    }

    @Override
    protected void onDestroy() {
        token++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static char sym(int reel, int i) {
        String s = STRIPS[reel];
        int n = s.length();
        return s.charAt(((i % n) + n) % n);
    }

    private int[] bets() {
        return Tree.owned(this, "highroller") ? BETS_HIGH : BETS;
    }

    private void paint() {
        if (betIndex >= bets().length) betIndex = bets().length - 1;
        balance.setText(String.format(Locale.US, "%,d Fun Coupons", Wallet.coupons(this)));
        int bet = freeLeft > 0 ? freeBet : bets()[betIndex];
        betText.setText(bet + " per line • 10 lines\nTotal bet " + bet * 10);
        spinBtn.setText(freeLeft > 0 ? "FREE SPIN (" + freeLeft + " left, wins x2)" : "SPIN");
        spinBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, freeLeft > 0 ? 17 : 24);
    }

    private void spin() {
        if (spinning) return;
        final boolean free = freeLeft > 0;
        final int bet = free ? freeBet : bets()[betIndex];
        if (!free) {
            if (Wallet.coupons(this) < bet * 10) {
                message.setText("Not enough coupons for a bet of " + bet * 10
                        + ".\nLower the bet or get more in the Wallet.");
                return;
            }
            Wallet.addCoupons(this, -bet * 10);
        } else {
            freeLeft--;
        }
        final char[][] grid = new char[3][5];
        for (int r = 0; r < 5; r++) {
            int stop = rnd.nextInt(STRIPS[r].length());
            grid[0][r] = sym(r, stop - 1);
            grid[1][r] = sym(r, stop);
            grid[2][r] = sym(r, stop + 1);
        }
        spinning = true;
        spinBtn.setAlpha(0.5f);
        message.setText("");
        overlay.setWins(null);
        paint();
        Sfx.play(this, Sfx.SPIN5);
        final int mine = ++token;
        for (int i = 0; i < 5; i++) {
            final boolean last = i == 4;
            reels[i].spinTo(grid[0][i], grid[1][i], grid[2][i], Sfx.REEL5_MS[i], new Runnable() {
                @Override
                public void run() {
                    if (last && mine == token) settle(grid, bet, free);
                }
            });
        }
    }

    /** What one line pays, as a multiple of the line bet. Lines pay from the left. */
    static int linePay(char[] l) {
        char first = 0;
        int run = 0;
        for (char s : l) {
            if (s == 'S') break;
            if (s == 'W') {
                run++;
            } else if (first == 0) {
                first = s;
                run++;
            } else if (s == first) {
                run++;
            } else {
                break;
            }
        }
        if (first == 0) {
            // Nothing but wilds: they pay as the top symbol, or the special five-wild prize.
            if (run == 5) return FIVE_WILDS;
            return run >= 3 ? PAYS[0][run - 3] : 0;
        }
        int wilds = 0;
        for (char s : l) {
            if (s != 'W') break;
            wilds++;
        }
        // A line that opens with three wilds is paid whichever way is worth more.
        int best = wilds >= 3 ? PAYS[0][wilds - 3] : 0;
        if (run >= 3) best = Math.max(best, PAYS[ORDER.indexOf(first)][run - 3]);
        return best;
    }

    private void settle(char[][] grid, int bet, boolean free) {
        spinning = false;
        spinBtn.setAlpha(1f);
        int won = 0;
        boolean[] wins = new boolean[LINES.length];
        int winLines = 0;
        for (int l = 0; l < LINES.length; l++) {
            char[] line = new char[5];
            for (int r = 0; r < 5; r++) line[r] = grid[LINES[l][r]][r];
            int p = linePay(line);
            if (p > 0) {
                wins[l] = true;
                winLines++;
                won += p * bet;
            }
        }
        if (free) won *= 2;
        int bonus = 0;
        for (char[] row : grid) {
            for (char s : row) {
                if (s == 'S') bonus++;
            }
        }
        String extra = "";
        if (bonus >= 3) {
            // Bonus symbols pay on the total bet, wherever they land.
            int pay = (bonus == 3 ? 5 : (bonus == 4 ? 20 : 100)) * bet * 10;
            won += pay;
            if (freeLeft == 0 && !free) {
                freeBet = bet;
                freeWon = 0;
            }
            freeLeft += FREE_SPINS;
            extra = "\n" + bonus + " bonus gifts: " + FREE_SPINS + " free spins!";
        }
        overlay.setWins(winLines > 0 ? wins : null);
        if (won > 0) Wallet.addCoupons(this, won);
        if (free) freeWon += won;
        message.setText((won > 0 ? (winLines > 0 ? "Winner on " + winLines + (winLines == 1
                ? " line" : " lines") : "Bonus pay") + ": +" + String.format(Locale.US, "%,d",
                won) + " coupons" : "No win.") + extra
                + (free && freeLeft == 0 ? "\nFree spins over. They won " + String.format(
                Locale.US, "%,d", freeWon) + " in all." : ""));
        if (bonus >= 3 || won >= bet * 10 * 20) Sfx.play(this, Sfx.JACKPOT);
        else if (won >= bet * 10 * 4) Sfx.play(this, Sfx.BIG_WIN);
        else if (won > 0) Sfx.play(this, Sfx.WIN);
        Stats.bet(this, "slot", free ? 0 : bet * 10, won);
        sitting.round(free ? 0 : bet * 10, won);
        paint();
    }

    private void showTable() {
        StringBuilder sb = new StringBuilder("Pays are multiples of your bet per line, for "
                + "three, four and five in a row starting from the left reel.\n\n");
        String[] names = {"👑 Crown", "💎 Diamond", "🍀 Clover",
                "A", "K", "Q", "J", "10"};
        for (int i = 0; i < names.length; i++) {
            sb.append(names[i]).append("   ").append(PAYS[i][0]).append(" / ")
                    .append(PAYS[i][1]).append(" / ").append(PAYS[i][2]).append('\n');
        }
        sb.append("\n⭐ The star is WILD and stands in for anything except the bonus "
                + "gift. Five stars on a line pay ").append(FIVE_WILDS).append(".\n\n"
                + "🎁 Three or more bonus gifts anywhere pay 5, 20 or 100 times "
                + "your total bet and award ").append(FREE_SPINS).append(" free spins. In "
                + "free spins every line win is doubled, and more gifts add more spins.\n\n"
                + "All ten lines are always in play, so the total bet is ten times the "
                + "line bet. About 3 spins in 10 pay something, and the free spins come "
                + "about once every 150 spins.\n\n"
                + "Over a long run the machine pays back roughly 92 coupons for every 100 "
                + "bet. It's for fun, with play money only.");
        new AlertDialog.Builder(this)
                .setTitle("Pay table and odds")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    /** Draws the winning lines over the reels. */
    static final class Overlay extends View {
        private static final int[] COLORS = {0xFFFFD54F, 0xFF4DD0E1, 0xFFBA68C8, 0xFF81C784,
                0xFFFF8A65, 0xFF64B5F6, 0xFFF06292, 0xFFAED581, 0xFFFFB74D, 0xFF9575CD};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean[] wins;

        Overlay(Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setWins(boolean[] w) {
            wins = w;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (wins == null) return;
            float w = getWidth();
            float h = getHeight();
            float d = getResources().getDisplayMetrics().density;
            paint.setStrokeWidth(3.5f * d);
            for (int l = 0; l < LINES.length; l++) {
                if (!wins[l]) continue;
                paint.setColor(COLORS[l]);
                path.reset();
                float nudge = (l - 4.5f) * 2.2f * d;
                for (int r = 0; r < 5; r++) {
                    float x = w * (r + 0.5f) / 5f;
                    float y = h * (LINES[l][r] + 0.5f) / 3f + nudge;
                    if (r == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                cv.drawPath(path, paint);
            }
        }
    }

    /** One reel, scrolling on its own clock so it spins even with animations switched off. */
    static final class Reel extends View {
        private static final String FILL = "ABCDEFGHWSDEFGH";
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private char[] strip = {'H', 'H', 'H'};
        private float pos = 1f;
        private boolean spinning;
        private long startAt;
        private long duration;
        private float to;
        private Runnable done;

        Reel(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void show(char top, char middle, char bottom) {
            strip = new char[]{bottom, middle, top};
            pos = 1f;
            invalidate();
        }

        void spinTo(char top, char middle, char bottom, long millis, Runnable whenDone) {
            int at = Math.round(pos);
            int length = 7 + (int) (millis * 14 / 1000);
            char[] next = new char[length];
            for (int i = 0; i < length; i++) next[i] = FILL.charAt(rnd.nextInt(FILL.length()));
            next[0] = get(at - 1);
            next[1] = get(at);
            next[2] = get(at + 1);
            next[length - 3] = bottom;
            next[length - 2] = middle;
            next[length - 1] = top;
            strip = next;
            pos = 1f;
            to = length - 2;
            duration = Math.max(200, millis);
            startAt = SystemClock.uptimeMillis();
            done = whenDone;
            spinning = true;
            invalidate();
        }

        private static float ease(float t) {
            float brakeAt = 0.62f;
            float full = brakeAt + (1 - brakeAt) / 2f;
            if (t < brakeAt) return t / full;
            float u = (t - brakeAt) / (1 - brakeAt);
            return (brakeAt + (1 - brakeAt) * (u - u * u / 2f)) / full;
        }

        private char get(int i) {
            return i < 0 || i >= strip.length ? 'H' : strip[i];
        }

        @Override
        protected void onDetachedFromWindow() {
            spinning = false;
            done = null;
            super.onDetachedFromWindow();
        }

        private void drawSymbol(Canvas cv, char s, float cx, float cy, float cell) {
            float size = Math.min(cell, getWidth());
            String face;
            int color = 0xFFFFFFFF;
            float scale = 0.6f;
            switch (s) {
                case 'W':
                    face = "⭐";
                    break;
                case 'S':
                    face = "🎁";
                    break;
                case 'A':
                    face = "👑";
                    break;
                case 'B':
                    face = "💎";
                    break;
                case 'C':
                    face = "🍀";
                    break;
                default:
                    String[] ranks = {"A", "K", "Q", "J", "10"};
                    int[] colors = {0xFFEF5350, 0xFF42A5F5, 0xFFAB47BC, 0xFF66BB6A, 0xFFFFA726};
                    face = ranks[s - 'D'];
                    color = colors[s - 'D'];
                    scale = 0.62f;
                    break;
            }
            paint.setColor(color);
            paint.setFakeBoldText(s >= 'D' && s <= 'H');
            paint.setTextSize(size * scale);
            float y = s == 'W' || s == 'S' ? cy - size * 0.08f : cy;
            cv.drawText(face, cx, y - (paint.ascent() + paint.descent()) / 2f, paint);
            if (s == 'W' || s == 'S') {
                paint.setColor(Ui.ACCENT);
                paint.setFakeBoldText(true);
                paint.setTextSize(size * 0.2f);
                cv.drawText(s == 'W' ? "WILD" : "BONUS", cx, cy + size * 0.42f, paint);
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
                    pos = 1f + (to - 1f) * ease(t);
                    if (t > 0.9f) pos += 0.2f * (float) Math.sin(Math.PI * (t - 0.9f) / 0.1f);
                }
            }
            float cell = h / 3f;
            int base = (int) Math.floor(pos);
            cv.save();
            cv.clipRect(0, 0, w, h);
            for (int i = base - 2; i <= base + 3; i++) {
                drawSymbol(cv, get(i), w / 2f, h / 2f - (i - pos) * cell, cell);
            }
            cv.restore();
            if (spinning) {
                postInvalidateOnAnimation();
            } else if (finished) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                Runnable r = done;
                done = null;
                if (r != null) post(r);
            }
        }
    }
}
