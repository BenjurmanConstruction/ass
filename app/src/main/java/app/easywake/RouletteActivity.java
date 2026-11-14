package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
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
 * Roulette with a single zero, played with Fun Coupons (play money only). Put chips on
 * numbers or on the outside bets, then spin.
 */
public class RouletteActivity extends Activity {
    /** The order of the numbers around a single-zero wheel. */
    private static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11,
            30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    private static final int[] REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30,
            32, 34, 36};
    private static final int[] CHIPS = {1, 5, 25, 100};
    private static final String[] OUTSIDE = {"Red", "Black", "Odd", "Even", "1 to 18",
            "19 to 36", "1st dozen", "2nd dozen", "3rd dozen", "Column 1", "Column 2",
            "Column 3"};
    private static final int MAX_PER_SPOT = 1000;

    private final Random rnd = new Random();
    private final Stats.Sitting sitting = new Stats.Sitting("Roulette", "spin");
    private final int[] onNumber = new int[37];
    private final int[] onOutside = new int[OUTSIDE.length];
    private final Button[] outsideBtns = new Button[OUTSIDE.length];
    private int chipIndex = 1;
    private boolean spinning;
    private int lastResult = -1;

    private WheelView wheel;
    private TableView table;
    private TextView message;
    private TextView balance;
    private Button chipBtn;
    private Button spinBtn;

    static boolean red(int n) {
        for (int r : REDS) {
            if (r == n) return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "roulette")) {
            Toast.makeText(this, "Unlock Roulette in the skill tree first.",
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
        root.addView(Ui.topBar(this, "Roulette"), Ui.full(this, 0));

        wheel = new WheelView(this);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(Ui.dp(this, 210),
                Ui.dp(this, 210));
        wp.gravity = Gravity.CENTER_HORIZONTAL;
        wp.topMargin = Ui.dp(this, 10);
        root.addView(wheel, wp);

        message = Ui.text(this, "Tap numbers or the bets below to place chips.", 15, Ui.TEXT);
        message.setGravity(Gravity.CENTER);
        message.setMinLines(2);
        root.addView(message, Ui.full(this, 8));
        balance = Ui.text(this, "", 15, Ui.ACCENT);
        balance.setGravity(Gravity.CENTER);
        root.addView(balance, Ui.full(this, 2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        chipBtn = Ui.button(this, "", false);
        chipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chipIndex = (chipIndex + 1) % CHIPS.length;
                paint();
            }
        });
        Button clear = Ui.button(this, "Clear", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spinning) return;
                java.util.Arrays.fill(onNumber, 0);
                java.util.Arrays.fill(onOutside, 0);
                paint();
            }
        });
        spinBtn = Ui.button(this, "SPIN", true);
        spinBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spin();
            }
        });
        Button[] row = {chipBtn, clear, spinBtn};
        float[] weights = {1.2f, 0.9f, 1.3f};
        for (int i = 0; i < row.length; i++) {
            row[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            row[i].setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 54),
                    weights[i]);
            int m = Ui.dp(this, 3);
            lp.setMargins(m, 0, m, 0);
            actions.addView(row[i], lp);
        }
        root.addView(actions, Ui.full(this, 8));

        table = new TableView(this);
        root.addView(table, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 290)));
        ((LinearLayout.LayoutParams) table.getLayoutParams()).topMargin = Ui.dp(this, 10);

        // Outside bets, three to a row.
        LinearLayout line = null;
        for (int i = 0; i < OUTSIDE.length; i++) {
            if (i % 3 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(line, Ui.full(this, 6));
            }
            final int idx = i;
            Button bt = Ui.button(this, "", false);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (spinning) return;
                    onOutside[idx] = Math.min(MAX_PER_SPOT, onOutside[idx] + CHIPS[chipIndex]);
                    paint();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f);
            int m = Ui.dp(this, 3);
            lp.setMargins(m, 0, m, 0);
            line.addView(bt, lp);
            outsideBtns[i] = bt;
        }

        root.addView(Sfx.toggle(this), Ui.full(this, 12));
        Button wallet = Ui.button(this, "Wallet: get coupons or cash out", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RouletteActivity.this, WalletActivity.class));
            }
        });
        root.addView(wallet, Ui.full(this, 10));
        Button rules = Ui.button(this, "Bets, payouts and odds", false);
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

    private int total() {
        int t = 0;
        for (int v : onNumber) t += v;
        for (int v : onOutside) t += v;
        return t;
    }

    private void paint() {
        balance.setText(String.format(Locale.US, "%,d Fun Coupons   •   on the table: %,d",
                Wallet.coupons(this), total()));
        chipBtn.setText("Chip: " + CHIPS[chipIndex]);
        for (int i = 0; i < OUTSIDE.length; i++) {
            outsideBtns[i].setText(OUTSIDE[i] + (onOutside[i] > 0 ? "\n" + onOutside[i] : ""));
        }
        table.invalidate();
    }

    /** Whether an outside bet wins on this number. Zero loses them all. */
    private static boolean outsideWins(int bet, int n) {
        if (n == 0) return false;
        switch (bet) {
            case 0:
                return red(n);
            case 1:
                return !red(n);
            case 2:
                return n % 2 == 1;
            case 3:
                return n % 2 == 0;
            case 4:
                return n <= 18;
            case 5:
                return n >= 19;
            case 6:
            case 7:
            case 8:
                return (n - 1) / 12 == bet - 6;
            default:
                return (n - 1) % 3 == bet - 9;
        }
    }

    private void spin() {
        if (spinning) return;
        final int stake = total();
        if (stake == 0) {
            message.setText("Place a bet first: tap a number or one of the bets below.");
            return;
        }
        if (Wallet.coupons(this) < stake) {
            message.setText("You have " + stake + " on the table but not enough coupons. "
                    + "Clear some bets or visit the Wallet.");
            return;
        }
        Wallet.addCoupons(this, -stake);
        final int result = rnd.nextInt(37);
        spinning = true;
        spinBtn.setAlpha(0.5f);
        message.setText("No more bets...");
        paint();
        Sfx.play(this, Sfx.CHIPS);
        wheel.spinTo(result, new Runnable() {
            @Override
            public void run() {
                settle(result, stake);
            }
        });
    }

    private void settle(int result, int stake) {
        spinning = false;
        spinBtn.setAlpha(1f);
        lastResult = result;
        // A winning number pays 35 to 1; the stake comes back as well.
        int back = onNumber[result] * 36;
        for (int i = 0; i < OUTSIDE.length; i++) {
            if (onOutside[i] > 0 && outsideWins(i, result)) {
                back += onOutside[i] * (i < 6 ? 2 : 3);
            }
        }
        if (back > 0) Wallet.addCoupons(this, back);
        int net = back - stake;
        String color = result == 0 ? "green" : (red(result) ? "red" : "black");
        message.setText(result + " " + color + ".\n" + (net > 0 ? "You win " + net
                + " coupons." : (net < 0 ? "You lose " + (-net) + " coupons."
                : "You break even.")));
        Sfx.play(this, net > 0 ? (net >= stake * 5 ? Sfx.BIG_WIN : Sfx.WIN) : Sfx.LOSE);
        Stats.bet(this, "roulette", stake, back);
        sitting.round(stake, back);
        paint();
    }

    private void showRules() {
        new AlertDialog.Builder(this)
                .setTitle("Bets, payouts and odds")
                .setMessage("The wheel has 37 pockets: 1 to 36, half red and half black, "
                        + "and a single green 0.\n\n"
                        + "Tap Chip to change the chip size, then tap where you want to "
                        + "bet. Tap again to add more. Your bets stay on the table after "
                        + "a spin, so you can spin again or press Clear.\n\n"
                        + "• A single number pays 35 to 1\n"
                        + "• A dozen or a column pays 2 to 1\n"
                        + "• Red, black, odd, even, 1 to 18 and 19 to 36 pay 1 to 1\n\n"
                        + "Column 1 is 1, 4, 7 and so on; column 2 is 2, 5, 8; column 3 "
                        + "is 3, 6, 9. If the ball lands on 0, every outside bet loses.\n\n"
                        + "Because of the 0, every bet gives the house the same edge of "
                        + "about 2.7%: over a long run you get back roughly 97 coupons for "
                        + "every 100 bet, however you spread your chips. No betting system "
                        + "changes that.")
                .setPositiveButton("Close", null)
                .show();
    }

    /** The number grid: 0 across the top, then 1 to 36 in six columns. */
    final class TableView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        TableView(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP || spinning) return true;
            float rowH = getHeight() / 7f;
            int row = (int) (e.getY() / rowH);
            int n;
            if (row <= 0) {
                n = 0;
            } else {
                int col = Math.max(0, Math.min(5, (int) (e.getX() / (getWidth() / 6f))));
                n = (Math.min(6, row) - 1) * 6 + col + 1;
            }
            onNumber[n] = Math.min(MAX_PER_SPOT, onNumber[n] + CHIPS[chipIndex]);
            RouletteActivity.this.paint();
            return true;
        }

        private void cell(Canvas cv, int n, float l, float t, float r, float b) {
            float d = getResources().getDisplayMetrics().density;
            rect.set(l + 2 * d, t + 2 * d, r - 2 * d, b - 2 * d);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(n == 0 ? 0xFF2E7D4F : (red(n) ? 0xFFB23A3A : 0xFF20242E));
            cv.drawRoundRect(rect, 6 * d, 6 * d, paint);
            if (n == lastResult) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * d);
                paint.setColor(0xFFFFFFFF);
                cv.drawRoundRect(rect, 6 * d, 6 * d, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(0xFFFFFFFF);
            paint.setFakeBoldText(true);
            paint.setTextSize(15 * d);
            float cy = (t + b) / 2f;
            cv.drawText(String.valueOf(n), (l + r) / 2f - (onNumber[n] > 0 ? 9 * d : 0),
                    cy - (paint.ascent() + paint.descent()) / 2f, paint);
            paint.setFakeBoldText(false);
            if (onNumber[n] > 0) {
                // A chip showing how much is on this number.
                float cx = (l + r) / 2f + 12 * d;
                paint.setColor(Ui.ACCENT);
                cv.drawCircle(cx, cy, 11 * d, paint);
                paint.setColor(0xFF10141F);
                paint.setTextSize(onNumber[n] >= 100 ? 8 * d : 10 * d);
                cv.drawText(String.valueOf(onNumber[n]), cx,
                        cy - (paint.ascent() + paint.descent()) / 2f, paint);
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float rowH = getHeight() / 7f;
            cell(cv, 0, 0, 0, w, rowH);
            for (int n = 1; n <= 36; n++) {
                int row = (n - 1) / 6;
                int col = (n - 1) % 6;
                cell(cv, n, col * w / 6f, (row + 1) * rowH, (col + 1) * w / 6f, (row + 2) * rowH);
            }
        }
    }

    /** The wheel. It turns on its own clock and slows to bring the result under the ball. */
    static final class WheelView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float angle;
        private float from;
        private float to;
        private long startAt;
        private boolean spinning;
        private Runnable done;
        private int shown = -1;

        WheelView(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void spinTo(int number, Runnable whenDone) {
            int index = 0;
            for (int i = 0; i < WHEEL.length; i++) {
                if (WHEEL[i] == number) index = i;
            }
            float step = 360f / WHEEL.length;
            // Turn the wheel so this pocket ends up at the top, after five full turns.
            float target = -(index + 0.5f) * step;
            from = angle % 360f;
            to = target - 360f * 5;
            while (to > from - 360f * 4) to -= 360f;
            startAt = SystemClock.uptimeMillis();
            done = whenDone;
            shown = -1;
            spinning = true;
            invalidate();
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
            float r = Math.min(w, h) / 2f;
            float cx = w / 2f;
            float cy = h / 2f;
            boolean finished = false;
            if (spinning) {
                float t = (SystemClock.uptimeMillis() - startAt) / 4200f;
                if (t >= 1f) {
                    angle = to;
                    spinning = false;
                    finished = true;
                } else {
                    // Fast at first, slowing all the way to the stop.
                    float e = 1 - (1 - t) * (1 - t) * (1 - t);
                    angle = from + (to - from) * e;
                }
            }
            float step = 360f / WHEEL.length;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.ACCENT_LO);
            cv.drawCircle(cx, cy, r, paint);
            oval.inset(r * 0.06f, r * 0.06f);
            cv.save();
            cv.rotate(angle, cx, cy);
            paint.setTextSize(r * 0.1f);
            paint.setFakeBoldText(true);
            for (int i = 0; i < WHEEL.length; i++) {
                int n = WHEEL[i];
                paint.setColor(n == 0 ? 0xFF2E7D4F : (red(n) ? 0xFFB23A3A : 0xFF20242E));
                // Pocket i is drawn starting from the top and going clockwise.
                cv.drawArc(oval, -90 + i * step, step + 0.4f, true, paint);
            }
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < WHEEL.length; i++) {
                cv.save();
                cv.rotate((i + 0.5f) * step, cx, cy);
                cv.drawText(String.valueOf(WHEEL[i]), cx, cy - r * 0.78f, paint);
                cv.restore();
            }
            cv.restore();
            paint.setFakeBoldText(false);
            // The hub, with the result once the wheel stops.
            paint.setColor(0xFF10141F);
            cv.drawCircle(cx, cy, r * 0.5f, paint);
            if (finished) {
                int index = Math.round((-angle / step) - 0.5f);
                shown = WHEEL[((index % WHEEL.length) + WHEEL.length) % WHEEL.length];
            }
            if (shown >= 0) {
                paint.setColor(shown == 0 ? 0xFF66BB6A : (red(shown) ? 0xFFEF5350 : 0xFFFFFFFF));
                paint.setFakeBoldText(true);
                paint.setTextSize(r * 0.5f);
                cv.drawText(String.valueOf(shown), cx,
                        cy - (paint.ascent() + paint.descent()) / 2f, paint);
                paint.setFakeBoldText(false);
            }
            // The ball sits at the top; the wheel turns beneath it.
            paint.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy - r * 0.93f, r * 0.05f, paint);

            if (spinning) {
                postInvalidateOnAnimation();
            } else if (finished) {
                Runnable run = done;
                done = null;
                if (run != null) post(run);
            }
        }
    }
}
