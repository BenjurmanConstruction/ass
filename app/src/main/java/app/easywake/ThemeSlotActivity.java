package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The themed video slots on the casino floor (ThemeSlotMath has the math). One screen for all
 * eight themes; the "theme" extra picks which. Bets are in Fun Coupons.
 */
public class ThemeSlotActivity extends Activity {
    private static final int[] BETS = {10, 25, 50, 100, 250, 500};

    private int theme;
    private ThemeSlotMath.Theme th;
    private Reels view;
    private final Random rnd = new Random();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        theme = Math.max(0, Math.min(ThemeSlotMath.THEMES.length - 1,
                getIntent().getIntExtra("theme", 0)));
        th = ThemeSlotMath.THEMES[theme];
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new Reels(this);
        setContentView(view);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void info() {
        int bet = BETS[view.betIdx];
        StringBuilder s = new StringBuilder(th.blurb).append("\n\n");
        boolean classic = th.has(ThemeSlotMath.F_CLASSIC);
        s.append(classic ? "Three in a row on any of 5 lines pays:\n"
                : "Pays for 3 / 4 / 5 in a row from the left on any of 20 lines:\n");
        int lines = classic ? 5 : 20;
        int[][] pay = {{5, 15, 50}, {5, 20, 60}, {8, 25, 80}, {10, 30, 100}, {15, 50, 200},
                {20, 75, 300}, {30, 100, 500}, {50, 200, 1000}};
        int[] pay3 = {6, 8, 10, 14, 20, 30, 60, 120};
        for (int k = 7; k >= 0; k--) {
            if (th.sym[k].isEmpty()) continue;
            s.append(th.sym[k]).append("   ");
            if (classic) {
                s.append(fc(pay3[k] * bet * th.scale / lines));
            } else {
                for (int n = 0; n < 3; n++) {
                    if (n > 0) s.append(" / ");
                    s.append(fc(pay[k][n] * bet * th.scale / lines));
                }
            }
            if (k == 7) s.append("   (wild: stands in for anything)");
            s.append('\n');
        }
        if (!th.sym[8].isEmpty()) {
            s.append(th.sym[8]).append("   3 / 4 / 5 anywhere: ")
                    .append(fc(2 * bet * th.scale)).append(" / ").append(fc(10 * bet * th.scale))
                    .append(" / ").append(fc(50 * bet * th.scale)).append(" + free spins\n");
        }
        s.append(String.format(Locale.US,
                "\nAbout 95%% comes back over the long run. Bet: %,d FC a spin.", bet));
        new AlertDialog.Builder(this).setTitle(th.name).setMessage(s.toString())
                .setPositiveButton("Got it", null).show();
    }

    private static String fc(double v) {
        return String.format(Locale.US, "%,d", Math.round(v));
    }

    // ------------------------------------------------------------------ the machine

    final class Reels extends View {
        final float d = getResources().getDisplayMetrics().density;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int cols = th.cols();
        int betIdx = 1;
        /** What's on the glass now, [row][col]. */
        int[][] grid;
        int[][] wildX;
        ThemeSlotMath.Spin spin;
        int step;
        /** Spinning: seconds since each reel started; stop times; strip offsets. */
        boolean spinning;
        float t;
        final float[] stopAt = new float[5];
        final float[] scroll = new float[5];
        final boolean[] stopped = new boolean[5];
        /** Showing wins: cycling through lines. */
        float showT;
        double shownWin, countTo, paidThisSpin;
        String banner;
        float bannerT;
        boolean waitTap;
        boolean picking;
        final boolean[] chestOpen = new boolean[9];
        int picksMade;
        boolean auto;
        long pending;
        long last;
        float cell, gx, gy;
        final RectF spinBtn = new RectF(), minus = new RectF(), plus = new RectF(),
                infoBtn = new RectF(), autoBtn = new RectF(), back = new RectF();
        final Bitmap[] symCache = new Bitmap[11];
        float symSize;
        float revealT = -1;

        Reels(Context c) {
            super(c);
            text.setTextAlign(Paint.Align.CENTER);
            grid = new int[3][cols];
            for (int r = 0; r < 3; r++) for (int col = 0; col < cols; col++) grid[r][col] = rnd.nextInt(7);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float right = 130 * d;
            float aw = w - right - 24 * d, ah = h - 120 * d;
            cell = Math.min(aw / cols, ah / 3);
            gx = 12 * d + (aw - cell * cols) / 2;
            gy = 62 * d + (ah - cell * 3) / 2;
            float bx = w - right / 2 - 6 * d;
            spinBtn.set(bx - 50 * d, h / 2f - 50 * d, bx + 50 * d, h / 2f + 50 * d);
            minus.set(bx - 56 * d, spinBtn.bottom + 18 * d, bx - 10 * d, spinBtn.bottom + 58 * d);
            plus.set(bx + 10 * d, spinBtn.bottom + 18 * d, bx + 56 * d, spinBtn.bottom + 58 * d);
            autoBtn.set(bx - 46 * d, spinBtn.top - 54 * d, bx + 46 * d, spinBtn.top - 16 * d);
            infoBtn.set(w - 50 * d, 10 * d, w - 12 * d, 44 * d);
            back.set(12 * d, 10 * d, 92 * d, 44 * d);
            for (int i = 0; i < symCache.length; i++) symCache[i] = null;
        }

        /** Emoji drawn once per size into bitmaps: drawing text every frame is slow. */
        private Bitmap sym(int k) {
            int size = (int) cell;
            if (size <= 0) return null;
            if (symCache[k] == null || symCache[k].getWidth() != size) {
                Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                String s = th.sym[k];
                if (k == ThemeSlotMath.WILD) {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setShader(new RadialGradient(size / 2f, size / 2f, size * 0.48f,
                            (th.accent & 0x00FFFFFF) | 0x88000000, 0x00000000,
                            Shader.TileMode.CLAMP));
                    c.drawCircle(size / 2f, size / 2f, size * 0.48f, p);
                }
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(size * 0.62f);
                c.drawText(s, size / 2f, size / 2f + size * 0.22f, p);
                if (k == ThemeSlotMath.WILD) {
                    p.setTypeface(Typeface.DEFAULT_BOLD);
                    p.setTextSize(size * 0.17f);
                    p.setColor(0xFF000000);
                    c.drawText("WILD", size / 2f + 1, size * 0.93f + 1, p);
                    p.setColor(0xFFFFFFFF);
                    c.drawText("WILD", size / 2f, size * 0.93f, p);
                }
                symCache[k] = b;
            }
            return symCache[k];
        }

        // ---------------------------------------------------------------- spin flow

        void startSpin() {
            if (spinning || waitTap || picking) return;
            int bet = BETS[betIdx];
            if (Wallet.coupons(ThemeSlotActivity.this) < bet) {
                banner = "Not enough coupons";
                bannerT = 2;
                auto = false;
                invalidate();
                return;
            }
            Wallet.addCoupons(ThemeSlotActivity.this, -bet);
            spin = ThemeSlotMath.spin(theme, rnd);
            // Paid now, so nothing is lost if the app closes mid-spin; the balance on screen
            // holds the win back until the reels show it.
            pending = Math.round(spin.total * bet);
            if (pending > 0) Wallet.addCoupons(ThemeSlotActivity.this,
                    (int) Math.min(Integer.MAX_VALUE, pending));
            step = 0;
            shownWin = 0;
            paidThisSpin = 0;
            beginStep(true);
        }

        private void beginStep(boolean sound) {
            spinning = true;
            t = 0;
            showT = 0;
            revealT = -1;
            boolean classic = cols == 3;
            for (int c = 0; c < cols; c++) {
                stopped[c] = false;
                stopAt[c] = classic ? Sfx.REEL_MS[c] / 1000f : Sfx.REEL5_MS[c] / 1000f;
                if (step > 0) stopAt[c] *= 0.7f;
            }
            if (sound) Sfx.play(ThemeSlotActivity.this, classic ? Sfx.SPIN : Sfx.SPIN5);
            invalidate();
        }

        private void stepLanded() {
            int[][] target = spin.grids.get(step);
            int[][] before = spin.beforeMystery.get(step);
            grid = before != null ? before : target;
            wildX = spin.wildX.get(step);
            if (before != null) {
                revealT = 0.7f;
                return;
            }
            showWins();
        }

        private void showWins() {
            grid = spin.grids.get(step);
            double win = spin.stepWin.get(step) * BETS[betIdx];
            countTo = shownWin + win;
            showT = 0;
            if (step == 0 && spin.chests != null) {
                banner = "PICK 3 CHESTS!";
                bannerT = 99;
                picking = true;
                picksMade = 0;
                for (int i = 0; i < 9; i++) chestOpen[i] = false;
                Sfx.play(ThemeSlotActivity.this, Sfx.BIG_WIN);
            } else if (step == 0 && spin.freeAwarded > 0) {
                banner = spin.freeAwarded + " FREE SPINS!  Tap to start";
                bannerT = 99;
                waitTap = true;
                Sfx.play(ThemeSlotActivity.this, Sfx.BIG_WIN);
            } else {
                afterStep();
            }
            if (win > 0) {
                double mult = win / BETS[betIdx];
                Sfx.play(ThemeSlotActivity.this, mult >= 50 ? Sfx.JACKPOT
                        : mult >= 10 ? Sfx.BIG_WIN : Sfx.WIN);
                if (mult >= 10) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }

        /** Next free spin, or the end of the whole spin. */
        private void afterStep() {
            if (step + 1 < spin.grids.size()) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        step++;
                        banner = "FREE SPIN " + step + " / " + (spin.grids.size() - 1)
                                + (th.freeMult > 1 ? "   ×" + th.freeMult : "");
                        bannerT = 99;
                        beginStep(false);
                    }
                }, spin.stepWin.get(step) > 0 ? 1300 : 500);
                return;
            }
            finish1();
        }

        private void finish1() {
            long total = pending;
            pending = 0;
            countTo = total;
            if (spin.grids.size() > 1 || spin.chests != null) {
                banner = String.format(Locale.US, "BONUS PAID %,d FC", total);
                bannerT = 3;
            } else if (bannerT > 50) {
                bannerT = 0;
            }
            if (total >= BETS[betIdx] * 20L) {
                banner = String.format(Locale.US, "BIG WIN!  %,d FC", total);
                bannerT = 3.5f;
            }
            spin = null;
            if (auto) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (auto && !spinning && spin == null) startSpin();
                    }
                }, total > 0 ? 1400 : 600);
            }
        }

        // ---------------------------------------------------------------- drawing

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            int w = getWidth(), h = getHeight();
            paint.setShader(new LinearGradient(0, 0, 0, h, th.top, th.bottom,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            // Faint giant symbols in the background.
            paint.setAlpha(28);
            Bitmap bg = sym(6);
            if (bg != null) {
                c.drawBitmap(bg, null, new RectF(w * 0.58f, h * 0.25f, w * 0.58f + h * 0.7f,
                        h * 0.95f), paint);
            }
            paint.setAlpha(255);

            // Title and balance.
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(24 * d);
            text.setColor(0x88000000);
            c.drawText(th.name.toUpperCase(Locale.US), gx + cell * cols / 2 + 2 * d, 40 * d,
                    text);
            text.setColor(th.accent);
            c.drawText(th.name.toUpperCase(Locale.US), gx + cell * cols / 2, 38 * d, text);
            text.setTextSize(13 * d);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.format(Locale.US, "%,d FC", Wallet.coupons(getContext()) - pending),
                    infoBtn.left - 12 * d, 32 * d, text);
            text.setTextAlign(Paint.Align.CENTER);
            button(c, back, "◀ Back", 0x55000000);
            button(c, infoBtn, "i", 0x55000000);

            // Reel window.
            RectF win = new RectF(gx - 8 * d, gy - 8 * d, gx + cell * cols + 8 * d,
                    gy + cell * 3 + 8 * d);
            paint.setColor(th.accent);
            c.drawRoundRect(win, 16 * d, 16 * d, paint);
            win.inset(4 * d, 4 * d);
            paint.setColor(0xF0080A10);
            c.drawRoundRect(win, 12 * d, 12 * d, paint);

            update(dt);
            c.save();
            c.clipRect(gx, gy, gx + cell * cols, gy + cell * 3);
            for (int col = 0; col < cols; col++) {
                if (col > 0) {
                    paint.setColor(0x22FFFFFF);
                    c.drawRect(gx + col * cell - 0.5f * d, gy, gx + col * cell + 0.5f * d,
                            gy + cell * 3, paint);
                }
                if (spinning && !stopped[col]) {
                    drawSpinningReel(c, col);
                } else {
                    for (int r = 0; r < 3; r++) drawCell(c, r, col, grid[r][col], 0);
                }
            }
            c.restore();
            if (!spinning && spin != null && revealT < 0) drawWinLines(c);
            if (picking) drawChests(c, w, h);

            // Win meter.
            if (shownWin < countTo) {
                shownWin = Math.min(countTo, shownWin + Math.max(1, countTo * dt * 1.2));
            }
            text.setTextSize(15 * d);
            text.setColor(0xFFFFE070);
            c.drawText(shownWin > 0 ? String.format(Locale.US, "WIN  %,d FC",
                    Math.round(shownWin)) : "GOOD LUCK", gx + cell * cols / 2,
                    gy + cell * 3 + 34 * d, text);

            // Controls.
            int bet = BETS[betIdx];
            paint.setShader(new RadialGradient(spinBtn.centerX(), spinBtn.centerY() - 10 * d,
                    spinBtn.width() * 0.7f, spinning ? 0xFF555555 : 0xFF6CFF8A,
                    spinning ? 0xFF222222 : 0xFF1A7A3A, Shader.TileMode.CLAMP));
            c.drawOval(spinBtn, paint);
            paint.setShader(null);
            text.setTextSize(20 * d);
            text.setColor(0xFFFFFFFF);
            c.drawText(waitTap ? "GO" : "SPIN", spinBtn.centerX(), spinBtn.centerY() + 7 * d,
                    text);
            button(c, minus, "−", 0x66000000);
            button(c, plus, "+", 0x66000000);
            text.setTextSize(12 * d);
            text.setColor(0xFFFFFFFF);
            c.drawText(String.format(Locale.US, "BET %,d", bet), spinBtn.centerX(),
                    minus.bottom + 18 * d, text);
            button(c, autoBtn, auto ? "AUTO ON" : "AUTO", auto ? 0xCC1A7A3A : 0x66000000);

            if (banner != null && bannerT > 0) {
                bannerT -= dt;
                text.setTextSize(26 * d);
                float bw = text.measureText(banner) + 40 * d;
                float cx = gx + cell * cols / 2, cy = gy + cell * 1.5f;
                if (picking) cy = gy - 4 * d;
                paint.setColor(0xDD000000);
                c.drawRoundRect(new RectF(cx - bw / 2, cy - 26 * d, cx + bw / 2, cy + 14 * d),
                        12 * d, 12 * d, paint);
                text.setColor(th.accent);
                c.drawText(banner, cx, cy + 3 * d, text);
            }
            if (spinning || shownWin < countTo || bannerT > 0 || !spinning && spin != null
                    || revealT >= 0) {
                postInvalidateOnAnimation();
            } else {
                last = 0;
            }
        }

        private void update(float dt) {
            if (revealT >= 0) {
                revealT -= dt;
                if (revealT < 0) showWins();
                return;
            }
            if (!spinning) {
                showT += dt;
                return;
            }
            t += dt;
            boolean all = true;
            for (int col = 0; col < cols; col++) {
                if (stopped[col]) continue;
                scroll[col] += dt * 14;
                if (t >= stopAt[col]) {
                    stopped[col] = true;
                    int[][] target = spin.beforeMystery.get(step) != null
                            ? spin.beforeMystery.get(step) : spin.grids.get(step);
                    for (int r = 0; r < 3; r++) grid[r][col] = target[r][col];
                    bounce[col] = 0.18f;
                } else {
                    all = false;
                }
            }
            for (int col = 0; col < cols; col++) if (bounce[col] > 0) bounce[col] -= dt;
            if (all) {
                spinning = false;
                stepLanded();
            }
        }

        final float[] bounce = new float[5];

        private void drawSpinningReel(Canvas c, int col) {
            int[] strip = ThemeSlotMath.strips(theme)[col];
            float off = scroll[col] % strip.length;
            int base = (int) off;
            float frac = off - base;
            for (int k = -1; k < 3; k++) {
                int v = strip[(base + k + strip.length * 4) % strip.length];
                float y = gy + (k + frac) * cell;
                drawSym(c, v, gx + col * cell, y, 0x99);
            }
            // Motion blur streaks.
            paint.setShader(new LinearGradient(0, gy, 0, gy + cell * 3,
                    new int[]{0xCC080A10, 0x00080A10, 0x00080A10, 0xCC080A10},
                    new float[]{0, 0.2f, 0.8f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(gx + col * cell, gy, gx + (col + 1) * cell, gy + cell * 3, paint);
            paint.setShader(null);
        }

        private void drawCell(Canvas c, int r, int col, int v, int alpha) {
            float b = bounce[col] > 0 ? (float) Math.sin(bounce[col] / 0.18f * Math.PI)
                    * cell * 0.08f : 0;
            float x = gx + col * cell, y = gy + r * cell + b;
            boolean lit = isLit(r, col);
            if (lit) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(showT * 8);
                paint.setColor(((int) (60 + 80 * pulse) << 24) | (th.accent & 0xFFFFFF));
                c.drawRoundRect(new RectF(x + 3 * d, y + 3 * d, x + cell - 3 * d,
                        y + cell - 3 * d), 10 * d, 10 * d, paint);
            }
            if (revealT >= 0 && v == ThemeSlotMath.MYSTERY) {
                float k = 1 + (0.7f - revealT) * 0.4f;
                c.save();
                c.scale(k, k, x + cell / 2, y + cell / 2);
                drawSym(c, v, x, y, 255);
                c.restore();
                return;
            }
            drawSym(c, v, x, y, 255);
            if (wildX != null && wildX[r][col] > 1 && v == ThemeSlotMath.WILD) {
                text.setTextSize(cell * 0.22f);
                text.setColor(0xFFFFE070);
                c.drawText("×" + wildX[r][col], x + cell * 0.78f, y + cell * 0.28f, text);
            }
        }

        private void drawSym(Canvas c, int v, float x, float y, int alpha) {
            Bitmap b = sym(v);
            if (b == null) return;
            paint.setAlpha(alpha);
            c.drawBitmap(b, x, y, paint);
            paint.setAlpha(255);
        }

        private boolean isLit(int r, int col) {
            if (spinning || spin == null || revealT >= 0) return false;
            List<int[]> won = spin.lines.get(step);
            if (won.isEmpty()) return grid[r][col] == ThemeSlotMath.SCATTER
                    && ThemeSlotMath.count(grid, ThemeSlotMath.SCATTER) >= 3;
            int[][] lines = cols == 3 ? ThemeSlotMath.LINES3 : ThemeSlotMath.LINES5;
            int[] cur = won.get((int) (showT / 1.1f) % won.size());
            return lines[cur[0]][col] == r && col < cur[1];
        }

        private void drawWinLines(Canvas c) {
            List<int[]> won = spin.lines.get(step);
            if (won.isEmpty()) return;
            int[][] lines = cols == 3 ? ThemeSlotMath.LINES3 : ThemeSlotMath.LINES5;
            int[] cur = won.get((int) (showT / 1.1f) % won.size());
            Path p = new Path();
            for (int col = 0; col < cols; col++) {
                float x = gx + (col + 0.5f) * cell, y = gy + (lines[cur[0]][col] + 0.5f) * cell;
                if (col == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5 * d);
            paint.setColor(0xAA000000);
            c.drawPath(p, paint);
            paint.setStrokeWidth(3 * d);
            paint.setColor(th.accent);
            c.drawPath(p, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private RectF chestRect(int i) {
            float size = cell * 0.9f;
            float x0 = gx + cell * cols / 2 - size * 1.6f, y0 = gy + cell * 0.1f;
            int r = i / 3, col = i % 3;
            return new RectF(x0 + col * size * 1.1f, y0 + r * size * 0.95f,
                    x0 + col * size * 1.1f + size, y0 + r * size * 0.95f + size * 0.85f);
        }

        private void drawChests(Canvas c, int w, int h) {
            paint.setColor(0xCC000000);
            c.drawRect(gx, gy, gx + cell * cols, gy + cell * 3, paint);
            int shown = 0;
            for (int i = 0; i < 9; i++) {
                RectF r = chestRect(i);
                paint.setColor(chestOpen[i] ? 0xFF3A2A10 : 0xFF6A4418);
                c.drawRoundRect(r, 10 * d, 10 * d, paint);
                paint.setColor(0xFFE6B869);
                c.drawRect(r.left, r.centerY() - 3 * d, r.right, r.centerY() + 3 * d, paint);
                text.setTextSize(r.height() * 0.38f);
                if (chestOpen[i]) {
                    text.setColor(0xFFFFE070);
                    c.drawText(chestValue(i) + "×", r.centerX(), r.centerY() + r.height() * 0.14f,
                            text);
                } else if (picksMade >= 3) {
                    text.setColor(0x88FFFFFF);
                    c.drawText(chestValue(i) + "×", r.centerX(), r.centerY() + r.height() * 0.14f,
                            text);
                } else {
                    text.setColor(0xFFFFFFFF);
                    c.drawText(th.sym[9].isEmpty() ? "?" : th.sym[9], r.centerX(),
                            r.centerY() + r.height() * 0.14f, text);
                }
                if (chestOpen[i]) shown++;
            }
        }

        /** Picks reveal the pre-drawn prizes in order; the rest show what was left behind. */
        private final int[] slotOf = new int[9];

        private int chestValue(int i) {
            return spin == null ? 0 : spin.chests[slotOf[i]];
        }

        private void pick(int i) {
            if (chestOpen[i] || picksMade >= 3) return;
            chestOpen[i] = true;
            // Assign the next pre-drawn prize to this chest, and the leftovers to the others.
            slotOf[i] = picksMade;
            picksMade++;
            Sfx.play(ThemeSlotActivity.this, Sfx.WIN);
            if (picksMade == 3) {
                int next = 3;
                for (int k = 0; k < 9; k++) if (!chestOpen[k]) slotOf[k] = next++;
                double pw = spin.pickWin * BETS[betIdx];
                countTo = shownWin + pw;
                banner = String.format(Locale.US, "CHESTS PAY %,d FC", Math.round(pw));
                bannerT = 99;
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        picking = false;
                        bannerT = 0;
                        if (spin.freeAwarded > 0) {
                            banner = spin.freeAwarded + " FREE SPINS!  Tap to start";
                            bannerT = 99;
                            waitTap = true;
                        } else {
                            afterStep();
                        }
                        invalidate();
                    }
                }, 1800);
            }
            invalidate();
        }

        private void button(Canvas c, RectF r, String s, int bg) {
            paint.setColor(bg);
            c.drawRoundRect(r, 12 * d, 12 * d, paint);
            text.setTextSize(Math.min(15 * d, r.height() * 0.45f));
            text.setColor(0xFFFFFFFF);
            c.drawText(s, r.centerX(), r.centerY() + text.getTextSize() * 0.35f, text);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getX(), y = e.getY();
            if (back.contains(x, y)) {
                if (!spinning && spin == null) finish();
                return true;
            }
            if (infoBtn.contains(x, y)) {
                info();
                return true;
            }
            if (picking) {
                for (int i = 0; i < 9; i++) {
                    if (chestRect(i).contains(x, y)) pick(i);
                }
                return true;
            }
            if (waitTap) {
                waitTap = false;
                bannerT = 0;
                afterStep();
                return true;
            }
            if (spinBtn.contains(x, y) || (x > gx && x < gx + cell * cols && y > gy
                    && y < gy + cell * 3)) {
                if (spinning) {
                    // Tap again to slam the reels to a stop.
                    for (int c = 0; c < cols; c++) stopAt[c] = Math.min(stopAt[c], t + c * 0.06f);
                } else if (spin == null) {
                    startSpin();
                }
                return true;
            }
            if (spin == null && !spinning) {
                if (minus.contains(x, y) && betIdx > 0) betIdx--;
                if (plus.contains(x, y) && betIdx < BETS.length - 1) betIdx++;
            }
            if (autoBtn.contains(x, y)) {
                auto = !auto;
                if (auto && spin == null && !spinning) startSpin();
            }
            invalidate();
            return true;
        }
    }

    @Override
    public void onBackPressed() {
        if (view.spin != null || view.spinning) return;
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view != null) view.auto = false;
    }
}
