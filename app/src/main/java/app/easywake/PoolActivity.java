package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.Locale;
import java.util.Random;

/**
 * Eight-ball against the regulars, for Fun Coupons. Opened from the pool tables in the casino
 * and The Last Call ("venue" extra: "casino" or "bar").
 *
 * Touch the felt to aim (the cue follows your finger), slide the strip under the table to fine
 * tune, pull the power bar down and let go to shoot. The spin ball sets follow or draw.
 */
public class PoolActivity extends Activity {

    /** Name, how good (0..1), stake in coupons, and a line they open with. */
    private static final Object[][] BAR = {
            {"Rookie Ray", 0.2f, 50, "First time? Me too. Kind of."},
            {"Sally Side-Pocket", 0.4f, 100, "I call my shots. Mostly the side ones."},
            {"Barstool Bob", 0.55f, 250, "Hold my drink. Actually, don't touch my drink."},
            {"Big Mo", 0.72f, 500, "Table's mine. You're just renting it."}};
    private static final Object[][] CASINO = {
            {"Velvet Vince", 0.55f, 500, "Lovely evening for taking your money."},
            {"Duchess Delacroix", 0.7f, 1000, "Do try to keep up, darling."},
            {"Ice Man Ito", 0.82f, 2500, "..."},
            {"The Professor", 0.92f, 5000, "Geometry, my friend. Just geometry."}};
    private static final String[] THEY_MISS = {"Rats.", "Table's crooked.", "I meant that.",
            "Your turn. Don't get used to it.", "Chalk's bad tonight."};
    private static final String[] THEY_POT = {"Too easy.", "And another.", "Watch and learn.",
            "Nothing but pocket."};
    private static final String[] YOU_MISS = {"Ooh, close.", "Unlucky.", "Ha!",
            "That's the one I'd have missed too."};

    private TableView view;
    private PoolGame game;
    private final Random rnd = new Random();
    private Object[] opp;
    private boolean practice;
    private int stake;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new TableView(this);
        setContentView(view);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        newGame(null);
        lobby();
    }

    private Object[][] roster() {
        return "casino".equals(getIntent().getStringExtra("venue")) ? CASINO : BAR;
    }

    /** Pick who to play. */
    private void lobby() {
        final Object[][] r = roster();
        String[] items = new String[r.length + 1];
        items[0] = "Practice table (free, just you)";
        for (int i = 0; i < r.length; i++) {
            float sk = (Float) r[i][1];
            int stars = Math.max(1, Math.round(sk * 5));
            StringBuilder s = new StringBuilder();
            for (int k = 0; k < 5; k++) s.append(k < stars ? '★' : '☆');
            items[i + 1] = String.format(Locale.US, "%s  %s  stake %,d FC", r[i][0], s,
                    (Integer) r[i][2]);
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.US, "Eight-ball  (you have %,d FC)",
                        Wallet.coupons(this)))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            practice = true;
                            opp = null;
                            stake = 0;
                            newGame("Practice: break when you're ready.");
                            return;
                        }
                        Object[] o = r[which - 1];
                        int st = (Integer) o[2];
                        if (Wallet.coupons(PoolActivity.this) < st) {
                            view.say(String.format(Locale.US,
                                    "You need %,d FC to play %s.", st, o[0]));
                            lobby();
                            return;
                        }
                        practice = false;
                        opp = o;
                        stake = st;
                        // The stake goes in the pot now; win and it comes back doubled.
                        Wallet.addCoupons(PoolActivity.this, -st);
                        newGame(o[0] + ": \"" + o[3] + "\"");
                    }
                })
                .setNegativeButton("Leave", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void newGame(String hello) {
        game = new PoolGame(rnd.nextLong());
        game.listener = view.sounds;
        // Lag for the break: a coin flip.
        game.turn = practice || opp == null || rnd.nextBoolean() ? 0 : 1;
        view.reset();
        if (hello != null) view.say(hello);
        if (opp != null && game.turn == 1) {
            view.say(opp[0] + " won the lag and breaks.");
            computerTurn();
        } else if (hello != null) {
            view.hint = "Drag the cue ball behind the line, aim, then pull the power bar down.";
        }
    }

    private String oppName() {
        return opp == null ? "Them" : (String) opp[0];
    }

    /** The balls have stopped: rules, then whoever's next. */
    private void afterShot(boolean clearBefore, int shooter) {
        game.endShot(clearBefore);
        if (practice) {
            game.turn = 0;
            if (game.winner >= 0) {
                view.say(game.winner == 0 ? "Eight's down. Nice rack!" : game.news);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        newGame("Fresh rack. Break 'em.");
                    }
                }, 2200);
                return;
            }
            if (!game.news.isEmpty()) view.say(game.news);
            return;
        }
        if (game.winner >= 0) {
            gameOver();
            return;
        }
        String line = game.news;
        if (shooter == 1 && opp != null) {
            if (game.turn == 1 && !game.potted.isEmpty()) {
                line = THEY_POT[rnd.nextInt(THEY_POT.length)];
            } else if (game.turn == 0 && game.lastFoul == null) {
                line = oppName() + ": \"" + THEY_MISS[rnd.nextInt(THEY_MISS.length)] + "\"";
            }
        } else if (shooter == 0 && game.turn == 1 && game.lastFoul == null && opp != null
                && rnd.nextFloat() < 0.4f) {
            line = oppName() + ": \"" + YOU_MISS[rnd.nextInt(YOU_MISS.length)] + "\"";
        }
        if (line != null && !line.isEmpty()) view.say(line);
        if (game.turn == 1) computerTurn();
        else if (game.ballInHand) view.hint = "Ball in hand: drag the cue ball anywhere.";
    }

    private void gameOver() {
        boolean won = game.winner == 0;
        Stats.add(this, won ? "poolWins" : "poolLosses", 1);
        String msg;
        if (won) {
            Wallet.addCoupons(this, stake * 2);
            Sfx.play(this, Sfx.BIG_WIN);
            msg = String.format(Locale.US, "%s\n\nYou beat %s and won %,d FC.", game.news,
                    oppName(), stake);
            Stats.log(this, String.format(Locale.US, "Pool: beat %s, +%,d FC", oppName(), stake));
        } else {
            Sfx.play(this, Sfx.LOSE);
            msg = String.format(Locale.US, "%s\n\n%s takes the %,d FC.", game.news, oppName(),
                    stake);
        }
        view.say(won ? "YOU WIN!" : oppName() + " wins.");
        final Object[] again = opp;
        new AlertDialog.Builder(this)
                .setTitle(won ? "You win!" : "You lose")
                .setMessage(msg)
                .setPositiveButton("Rematch", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int st = (Integer) again[2];
                        if (Wallet.coupons(PoolActivity.this) < st) {
                            view.say("Not enough coupons for a rematch.");
                            lobby();
                            return;
                        }
                        Wallet.addCoupons(PoolActivity.this, -st);
                        newGame(oppName() + ": \"Again? Rack 'em.\"");
                    }
                })
                .setNeutralButton("Someone else", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        lobby();
                    }
                })
                .setNegativeButton("Leave", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    /** The computer thinks on another thread, then lines up and shoots on screen. */
    private void computerTurn() {
        if (opp == null) return;
        view.thinking = true;
        final PoolGame copy = new PoolGame(game);
        final float skill = (Float) opp[1];
        final long seed = rnd.nextLong();
        final int token = view.token;
        new Thread(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                final PoolGame.Plan p = PoolGame.plan(copy, skill, new Random(seed));
                long wait = Math.max(0, 900 - (System.currentTimeMillis() - t0));
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (token != view.token || isFinishing()) return;
                        view.thinking = false;
                        if (game.ballInHand && p.placeX > 0) game.place(p.placeX, p.placeY);
                        view.computerShot(p);
                    }
                }, wait);
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (opp != null && game.winner < 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Walk away?")
                    .setMessage(String.format(Locale.US,
                            "Leaving now forfeits the %,d FC stake.", stake))
                    .setPositiveButton("Forfeit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            finish();
                        }
                    })
                    .setNegativeButton("Keep playing", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ the table

    private static final int[] COLORS = {0xFFF4F1E8, 0xFFF5C518, 0xFF1E4FD8, 0xFFD62828,
            0xFF6A2C91, 0xFFF77F00, 0xFF1B7F3B, 0xFF7A1E1E, 0xFF141414};

    static int colorOf(int b) {
        return COLORS[b <= 8 ? b : b - 8];
    }

    final class TableView extends View {
        final float d = getResources().getDisplayMetrics().density;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap tableArt;
        float s, ox, oy;
        final RectF tableRect = new RectF(), powerRect = new RectF(), fineRect = new RectF();
        float spinCx, spinCy, spinR;

        float aim = 0, power, spin, fineScroll;
        int aimPtr = -1, powerPtr = -1, finePtr = -1, spinPtr = -1, cuePtr = -1;
        float powerY0, fineX0;
        /** Stroke animation: time left, and the power being struck. */
        float stroke = -1, strokePower, strokeSpin;
        boolean computerAiming;
        PoolGame.Plan plan;
        float planT, aimFrom;
        boolean thinking;
        int token;
        int shooter;
        boolean clearBefore;
        String msg = "";
        float msgT;
        String hint;
        long last;
        float shake;

        TableView(Context c) {
            super(c);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void reset() {
            token++;
            power = 0;
            spin = 0;
            stroke = -1;
            computerAiming = false;
            thinking = false;
            aim = 0;
            hint = null;
            invalidate();
        }

        void say(String m) {
            msg = m;
            msgT = 3.5f;
            invalidate();
        }

        final PoolGame.Listener sounds = new PoolGame.Listener() {
            long clackAt, railAt;

            @Override
            public void clack(float v) {
                long now = System.nanoTime();
                if (now - clackAt < 25_000_000L || v < 0.03f) return;
                clackAt = now;
                Sfx.play(PoolActivity.this, Sfx.CLACK, Math.min(1, v / 2.5f));
                if (v > 3) shake = Math.min(1, v / 8);
            }

            @Override
            public void rail(float v) {
                long now = System.nanoTime();
                if (now - railAt < 40_000_000L) return;
                railAt = now;
                Sfx.play(PoolActivity.this, Sfx.RAIL, Math.min(1, v / 3));
            }

            @Override
            public void pocket(int ball) {
                Sfx.play(PoolActivity.this, Sfx.POCKET);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        };

        boolean myTurn() {
            return game.turn == 0 && game.winner < 0 && !game.moving && stroke < 0
                    && !computerAiming && !thinking;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float top = 44 * d, rightW = 104 * d, bottom = 40 * d, pad = 8 * d;
            float rail = 0.085f;
            float aw = w - rightW - pad * 2, ah = h - top - bottom - pad;
            s = Math.min(aw / (PoolGame.W + rail * 2), ah / (PoolGame.H + rail * 2));
            float tw = (PoolGame.W + rail * 2) * s, th = (PoolGame.H + rail * 2) * s;
            float tx = pad + (aw - tw) / 2, ty = top + (ah - th) / 2;
            tableRect.set(tx, ty, tx + tw, ty + th);
            ox = tx + rail * s;
            oy = ty + rail * s;
            fineRect.set(tableRect.left + tw * 0.15f, h - bottom + 6 * d,
                    tableRect.right - tw * 0.15f, h - 8 * d);
            powerRect.set(w - rightW + 30 * d, top + 6 * d, w - 30 * d, h - 118 * d);
            spinR = 30 * d;
            spinCx = w - rightW / 2;
            spinCy = h - 58 * d;
            paintTable(w, h);
        }

        float px(float x) {
            return ox + x * s;
        }

        float py(float y) {
            return oy + y * s;
        }

        /** The wood, cushions, felt and pockets, painted once. */
        private void paintTable(int w, int h) {
            if (w <= 0 || h <= 0) return;
            tableArt = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tableArt);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Room: a dim bar with a lamp over the table.
            p.setShader(new RadialGradient(tableRect.centerX(), tableRect.centerY(),
                    Math.max(w, h) * 0.7f, 0xFF2A1E14, 0xFF070504, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            // Shadow under the table.
            p.setColor(0x88000000);
            c.drawRoundRect(new RectF(tableRect.left + 6 * d, tableRect.top + 10 * d,
                    tableRect.right + 6 * d, tableRect.bottom + 12 * d), 18 * d, 18 * d, p);
            // Wood rails.
            p.setShader(new LinearGradient(0, tableRect.top, 0, tableRect.bottom,
                    new int[]{0xFF7A4622, 0xFF4E2A12, 0xFF6A3B1C}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(tableRect, 16 * d, 16 * d, p);
            p.setShader(null);
            p.setColor(0x33FFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * d);
            c.drawRoundRect(new RectF(tableRect.left + 2 * d, tableRect.top + 2 * d,
                    tableRect.right - 2 * d, tableRect.bottom - 2 * d), 14 * d, 14 * d, p);
            p.setStyle(Paint.Style.FILL);
            // Cushions (a darker green lip) and the felt.
            float cu = 0.035f * s;
            p.setColor(0xFF0E5A34);
            c.drawRect(px(0) - cu, py(0) - cu, px(PoolGame.W) + cu, py(PoolGame.H) + cu, p);
            p.setShader(new RadialGradient(px(PoolGame.W / 2), py(PoolGame.H / 2),
                    PoolGame.W * s * 0.6f, 0xFF1E9A5A, 0xFF11703F, Shader.TileMode.CLAMP));
            c.drawRect(px(0), py(0), px(PoolGame.W), py(PoolGame.H), p);
            p.setShader(null);
            // Cushion edge highlight.
            p.setColor(0x2AFFFFFF);
            c.drawRect(px(0), py(0), px(PoolGame.W), py(0) + 1.5f * d, p);
            // Diamonds on the rails.
            p.setColor(0xFFF2E6C8);
            float rm = (oy - tableRect.top) / 2;
            for (int i = 1; i < 8; i++) {
                if (i == 4) continue;
                float x = px(PoolGame.W * i / 8f);
                diamond(c, p, x, tableRect.top + rm);
                diamond(c, p, x, tableRect.bottom - rm);
            }
            for (int i = 1; i < 4; i++) {
                float y = py(PoolGame.H * i / 4f);
                diamond(c, p, tableRect.left + rm, y);
                diamond(c, p, tableRect.right - rm, y);
            }
            // Pockets: leather cups with a dark hole.
            for (int i = 0; i < 6; i++) {
                float cxp = px(PoolGame.POCKETS[i][0]), cyp = py(PoolGame.POCKETS[i][1]);
                float r = (i == 1 || i == 4 ? 0.062f : 0.07f) * s;
                p.setColor(0xFF2A1A0E);
                c.drawCircle(cxp, cyp, r * 1.25f, p);
                p.setShader(new RadialGradient(cxp, cyp, r, 0xFF000000, 0xFF0A0604,
                        Shader.TileMode.CLAMP));
                c.drawCircle(cxp, cyp, r, p);
                p.setShader(null);
            }
            // Head string and foot spot.
            p.setColor(0x22FFFFFF);
            c.drawRect(px(PoolGame.HEAD) - 0.5f * d, py(0), px(PoolGame.HEAD) + 0.5f * d,
                    py(PoolGame.H), p);
            c.drawCircle(px(PoolGame.FOOT_X), py(PoolGame.FOOT_Y), 2 * d, p);
            c.drawCircle(px(PoolGame.HEAD), py(PoolGame.H / 2), 2 * d, p);
        }

        private void diamond(Canvas c, Paint p, float x, float y) {
            Path path = new Path();
            float r = 3.2f * d;
            path.moveTo(x, y - r);
            path.lineTo(x + r * 0.6f, y);
            path.lineTo(x, y + r);
            path.lineTo(x - r * 0.6f, y);
            path.close();
            c.drawPath(path, p);
        }

        // ---------------------------------------------------------------- frame

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0 : Math.min(1 / 20f, (now - last) / 1e9f);
            last = now;
            update(dt);
            int w = getWidth(), h = getHeight();
            c.save();
            if (shake > 0) {
                c.translate((rnd.nextFloat() - 0.5f) * shake * 6 * d,
                        (rnd.nextFloat() - 0.5f) * shake * 6 * d);
                shake = Math.max(0, shake - dt * 4);
            }
            if (tableArt != null) c.drawBitmap(tableArt, 0, 0, null);
            // Ball shadows, then balls.
            float br = PoolGame.R * s;
            for (int b = 0; b < 16; b++) {
                if (!game.on[b]) continue;
                paint.setColor(0x55000000);
                c.drawCircle(px(game.x[b]) + br * 0.22f, py(game.y[b]) + br * 0.32f, br, paint);
            }
            boolean aimingNow = (myTurn() || stroke >= 0 && shooter == 0) && game.on[0];
            if (aimingNow && stroke < 0) drawGuide(c, br);
            for (int b = 0; b < 16; b++) {
                if (game.on[b]) ball(c, b, px(game.x[b]), py(game.y[b]), br);
            }
            if (game.on[0] && (aimingNow || computerAiming || stroke >= 0)) drawCue(c, br);
            if (game.ballInHand && myTurn()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setColor(0xAAFFFFFF);
                c.drawCircle(px(game.x[0]), py(game.y[0]), br + 7 * d
                        + (float) Math.sin(now / 2e8) * 2 * d, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            c.restore();
            drawHud(c, w, h);
            if (game.moving || stroke >= 0 || computerAiming || msgT > 0 || thinking
                    || shake > 0) {
                postInvalidateOnAnimation();
            } else {
                last = 0;
            }
        }

        private void update(float dt) {
            if (msgT > 0) msgT -= dt;
            if (stroke >= 0) {
                stroke -= dt;
                if (stroke < 0) strike();
                return;
            }
            if (computerAiming) {
                planT += dt;
                float k = Math.min(1, planT / 0.8f);
                k = k * k * (3 - 2 * k);
                aim = aimFrom + angleDiff(plan.angle, aimFrom) * k;
                power = planT < 0.9f ? 0 : Math.min(plan.power, (planT - 0.9f) * 1.4f);
                spin = plan.spin;
                if (planT > 0.9f + plan.power / 1.4f + 0.25f) {
                    computerAiming = false;
                    startStroke(plan.power, plan.spin);
                }
                return;
            }
            if (game.moving) {
                game.step(dt);
                if (!game.moving) afterShot(clearBefore, shooter);
            }
        }

        void computerShot(PoolGame.Plan p) {
            plan = p;
            planT = 0;
            aimFrom = aim;
            computerAiming = true;
            shooter = 1;
            invalidate();
        }

        private void startStroke(float pw, float sp) {
            stroke = 0.09f;
            strokePower = pw;
            strokeSpin = sp;
            shooter = game.turn;
            invalidate();
        }

        private void strike() {
            clearBefore = game.onEight(game.turn);
            Sfx.play(PoolActivity.this, Sfx.CUE, 0.3f + strokePower * 0.7f);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            game.shoot(aim, strokePower, strokeSpin);
            power = 0;
            hint = null;
            invalidate();
        }

        // ---------------------------------------------------------------- drawing pieces

        private final Bitmap[] ballBmp = new Bitmap[16];
        private int[] pix;

        /**
         * Each ball is shaded pixel by pixel as a real sphere: its color or stripe band, the
         * number spot, a soft light from the lamp and a sharp highlight. They're small, so this
         * is cheap, and the stripes and numbers roll properly.
         */
        private void ball(Canvas c, int b, float x, float y, float r) {
            int size = (int) Math.ceil(r * 2) + 2;
            if (ballBmp[b] == null || ballBmp[b].getWidth() != size) {
                ballBmp[b] = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            }
            if (pix == null || pix.length < size * size) pix = new int[size * size];
            int col = b == 0 ? COLORS[0] : colorOf(b);
            boolean stripe = b >= 9;
            float nx = game.nx[b], ny = game.ny[b], nz = game.nz[b];
            float qx = game.qx[b], qy = game.qy[b], qz = game.qz[b];
            int cr = (col >> 16) & 255, cg = (col >> 8) & 255, cb = col & 255;
            int ir = 0xF4, ig = 0xF1, ib = 0xE8;
            // Light from up and to the left.
            float lx = -0.38f, ly = -0.5f, lz = 0.78f;
            float hx = lx, hy = ly, hz = lz + 1;
            float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
            hx /= hl;
            hy /= hl;
            hz /= hl;
            float half = size / 2f;
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    float u = (i + 0.5f - half) / r, v = (j + 0.5f - half) / r;
                    float d2 = u * u + v * v;
                    float edge = (1 - (float) Math.sqrt(d2)) * r + 0.5f;
                    if (edge <= 0) {
                        pix[j * size + i] = 0;
                        continue;
                    }
                    float w = (float) Math.sqrt(Math.max(0, 1 - d2));
                    int rr, gg, bb;
                    boolean white;
                    if (b == 0) white = true;
                    else if (nx * u + ny * v + nz * w > 0.93f) white = true;
                    else white = stripe && Math.abs(qx * u + qy * v + qz * w) > 0.5f;
                    if (white) {
                        rr = ir;
                        gg = ig;
                        bb = ib;
                    } else {
                        rr = cr;
                        gg = cg;
                        bb = cb;
                    }
                    float diff = 0.32f + 0.68f * Math.max(0, lx * u + ly * v + lz * w);
                    float sp = Math.max(0, hx * u + hy * v + hz * w);
                    sp = sp * sp;
                    sp = sp * sp;
                    sp = sp * sp;
                    sp = sp * sp * sp * 0.85f;
                    int R2 = Math.min(255, (int) (rr * diff + sp * 255));
                    int G2 = Math.min(255, (int) (gg * diff + sp * 255));
                    int B2 = Math.min(255, (int) (bb * diff + sp * 255));
                    int a = (int) (Math.min(1, edge) * 255);
                    pix[j * size + i] = (a << 24) | (R2 << 16) | (G2 << 8) | B2;
                }
            }
            ballBmp[b].setPixels(pix, 0, size, 0, 0, size, size);
            c.drawBitmap(ballBmp[b], x - half, y - half, null);
            if (b > 0 && nz > 0.35f) {
                float k = 0.7f;
                float cx = x + nx * r * k, cy = y + ny * r * k;
                text.setTextSize(r * 0.62f * (0.55f + 0.45f * nz));
                text.setColor(0xFF111111);
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.valueOf(b), cx, cy + text.getTextSize() * 0.36f, text);
            }
        }

        private void drawGuide(Canvas c, float br) {
            float[] hit = game.aimHit(aim);
            float cx = px(game.x[0]), cy = py(game.y[0]);
            float hx = px(hit[1]), hy = py(hit[2]);
            paint.setStrokeWidth(1.6f * d);
            paint.setColor(0xCCFFFFFF);
            dashed(c, cx, cy, hx, hy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xDDFFFFFF);
            c.drawCircle(hx, hy, br, paint);
            paint.setStyle(Paint.Style.FILL);
            int b = (int) hit[0];
            float dx = (float) Math.cos(aim), dy = (float) Math.sin(aim);
            if (b > 0) {
                float nx = game.x[b] - hit[1], ny = game.y[b] - hit[2];
                float nl = (float) Math.hypot(nx, ny);
                nx /= nl;
                ny /= nl;
                float full = dx * nx + dy * ny;
                boolean legal = game.legalFirst(b);
                // Where the object ball goes.
                paint.setStrokeWidth(2.4f * d);
                paint.setColor(legal ? 0xEEFFFFFF : 0xEEFF5A5A);
                float len = (0.12f + 0.45f * full) * s;
                c.drawLine(px(game.x[b]), py(game.y[b]), px(game.x[b]) + nx * len,
                        py(game.y[b]) + ny * len, paint);
                // Where the cue ball glances off.
                float tx = dx - nx * full, ty = dy - ny * full;
                float tl = (float) Math.hypot(tx, ty);
                if (tl > 0.02f) {
                    paint.setColor(0x88A0E0FF);
                    paint.setStrokeWidth(1.6f * d);
                    float l2 = (0.05f + 0.25f * tl) * s;
                    c.drawLine(hx, hy, hx + tx / tl * l2, hy + ty / tl * l2, paint);
                }
                if (!legal) {
                    text.setTextSize(11 * d);
                    text.setColor(0xFFFF8080);
                    c.drawText("wrong ball", hx, hy - br - 6 * d, text);
                }
            } else {
                // Off the cushion.
                float rx = dx, ry = dy;
                if (hit[1] <= PoolGame.R + 1e-4f || hit[1] >= PoolGame.W - PoolGame.R - 1e-4f) {
                    rx = -rx;
                } else {
                    ry = -ry;
                }
                paint.setColor(0x66FFFFFF);
                float l = 0.25f * s;
                dashed(c, hx, hy, hx + rx * l, hy + ry * l);
            }
        }

        private void dashed(Canvas c, float x0, float y0, float x1, float y1) {
            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            if (len < 1) return;
            float ux = (x1 - x0) / len, uy = (y1 - y0) / len;
            float dash = 7 * d, gap = 5 * d;
            for (float t = 0; t < len; t += dash + gap) {
                float e = Math.min(len, t + dash);
                c.drawLine(x0 + ux * t, y0 + uy * t, x0 + ux * e, y0 + uy * e, paint);
            }
        }

        private void drawCue(Canvas c, float br) {
            float cx = px(game.x[0]), cy = py(game.y[0]);
            float pull;
            if (stroke >= 0) {
                pull = strokePower * 70 * d * (stroke / 0.09f);
            } else {
                pull = power * 70 * d;
            }
            float gap = br + 5 * d + pull;
            float len = Math.max(getWidth(), getHeight()) * 0.55f;
            c.save();
            c.rotate((float) Math.toDegrees(aim) + 180, cx, cy);
            float x0 = cx + gap;
            // Shaft taper drawn as a few segments: tip, ferrule, maple, wrap, butt.
            paint.setColor(0x55000000);
            c.drawRect(x0 + 4 * d, cy - 1 * d, x0 + len + 4 * d, cy + 6 * d, paint);
            paint.setColor(0xFF2E6FB0);
            c.drawRect(x0, cy - 2.2f * d, x0 + 3 * d, cy + 2.2f * d, paint);
            paint.setColor(0xFFF4EEDC);
            c.drawRect(x0 + 3 * d, cy - 2.4f * d, x0 + 12 * d, cy + 2.4f * d, paint);
            paint.setShader(new LinearGradient(x0 + 12 * d, 0, x0 + len * 0.62f, 0,
                    0xFFE8C98E, 0xFFC99A5A, Shader.TileMode.CLAMP));
            Path shaft = new Path();
            shaft.moveTo(x0 + 12 * d, cy - 2.5f * d);
            shaft.lineTo(x0 + len * 0.62f, cy - 4.2f * d);
            shaft.lineTo(x0 + len * 0.62f, cy + 4.2f * d);
            shaft.lineTo(x0 + 12 * d, cy + 2.5f * d);
            shaft.close();
            c.drawPath(shaft, paint);
            paint.setShader(null);
            paint.setColor(0xFF1A1A1A);
            c.drawRect(x0 + len * 0.62f, cy - 4.4f * d, x0 + len * 0.8f, cy + 4.4f * d, paint);
            paint.setColor(0xFF5A1E10);
            c.drawRect(x0 + len * 0.8f, cy - 5 * d, x0 + len, cy + 5 * d, paint);
            paint.setColor(0x44FFFFFF);
            c.drawRect(x0 + 12 * d, cy - 2.4f * d, x0 + len, cy - 1.2f * d, paint);
            c.restore();
        }

        private void drawHud(Canvas c, int w, int h) {
            // Top bar.
            paint.setColor(0xCC000000);
            c.drawRect(0, 0, w, 40 * d, paint);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(14 * d);
            boolean you = game.turn == 0;
            text.setColor(you ? 0xFFFFE070 : 0xFFAAAAAA);
            c.drawText((you ? "▶ " : "") + "YOU" + groupName(0), 12 * d, 26 * d, text);
            groupBalls(c, 0, 12 * d + text.measureText((you ? "▶ " : "") + "YOU"
                    + groupName(0)) + 10 * d, 20 * d, true);
            if (!practice) {
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(!you ? 0xFFFFE070 : 0xFFAAAAAA);
                String them = (thinking ? "(thinking) " : "") + oppName() + groupName(1)
                        + (!you ? " ◀" : "");
                c.drawText(them, w - 12 * d, 26 * d, text);
                groupBalls(c, 1, w - 12 * d - text.measureText(them) - 10 * d, 20 * d, false);
            }
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(11 * d);
            text.setColor(0xFFB0FFB0);
            c.drawText(practice ? "PRACTICE" : String.format(Locale.US, "POT %,d FC",
                    stake * 2), w / 2f, 14 * d, text);
            if (msgT > 0 && msg != null) {
                text.setTextSize(15 * d);
                float a = Math.min(1, msgT);
                text.setColor(((int) (a * 255) << 24) | 0xFFFFFF);
                c.drawText(msg, (tableRect.left + tableRect.right) / 2, 32 * d, text);
            } else if (hint != null && myTurn()) {
                text.setTextSize(12 * d);
                text.setColor(0xCCFFFFFF);
                c.drawText(hint, (tableRect.left + tableRect.right) / 2, 32 * d, text);
            }
            // Power bar.
            paint.setColor(0x66000000);
            c.drawRoundRect(powerRect, 12 * d, 12 * d, paint);
            float fill = power;
            if (fill > 0) {
                RectF f = new RectF(powerRect.left + 4 * d, powerRect.top + 4 * d,
                        powerRect.right - 4 * d,
                        powerRect.top + 4 * d + (powerRect.height() - 8 * d) * fill);
                paint.setShader(new LinearGradient(0, powerRect.top, 0, powerRect.bottom,
                        new int[]{0xFF6CFF8A, 0xFFFFE070, 0xFFFF4A3A}, null,
                        Shader.TileMode.CLAMP));
                c.drawRoundRect(f, 9 * d, 9 * d, paint);
                paint.setShader(null);
            }
            text.setTextSize(11 * d);
            text.setColor(0xCCFFFFFF);
            c.drawText("POWER", powerRect.centerX(), powerRect.bottom + 14 * d, text);
            if (power <= 0 && myTurn()) {
                text.setTextSize(18 * d);
                c.drawText("↓", powerRect.centerX(), powerRect.top + 26 * d, text);
                text.setTextSize(9 * d);
                c.drawText("PULL", powerRect.centerX(), powerRect.top + 40 * d, text);
            } else if (power > 0) {
                text.setTextSize(12 * d);
                c.drawText(Math.round(power * 100) + "%", powerRect.centerX(),
                        powerRect.bottom - 10 * d, text);
            }
            // Spin ball.
            paint.setColor(COLORS[0]);
            c.drawCircle(spinCx, spinCy, spinR, paint);
            paint.setColor(0x33000000);
            c.drawLine(spinCx - spinR, spinCy, spinCx + spinR, spinCy, paint);
            paint.setColor(0xFFD62828);
            c.drawCircle(spinCx, spinCy - spin * spinR * 0.75f, 5 * d, paint);
            text.setTextSize(10 * d);
            text.setColor(0xCCFFFFFF);
            c.drawText(spin > 0.15f ? "FOLLOW" : spin < -0.15f ? "DRAW" : "SPIN", spinCx,
                    spinCy - spinR - 6 * d, text);
            // Fine aim strip.
            paint.setColor(0x55000000);
            c.drawRoundRect(fineRect, 10 * d, 10 * d, paint);
            paint.setColor(0x66FFFFFF);
            float step = 9 * d;
            float off = ((fineScroll % step) + step) % step;
            for (float x = fineRect.left + off; x < fineRect.right; x += step) {
                c.drawLine(x, fineRect.top + 5 * d, x, fineRect.bottom - 5 * d, paint);
            }
            text.setTextSize(10 * d);
            text.setColor(0xDDFFFFFF);
            c.drawText("◀  slide to fine-tune aim  ▶", fineRect.centerX(),
                    fineRect.centerY() + 4 * d, text);
        }

        private String groupName(int p) {
            int g = game.group[p];
            if (g == PoolGame.NONE) return "";
            if (game.onEight(p)) return " (on the 8)";
            return g == PoolGame.SOLIDS ? " (solids)" : " (stripes)";
        }

        /** The balls a player still has to sink. */
        private void groupBalls(Canvas c, int p, float x, float y, boolean rightward) {
            int g = game.group[p];
            if (g == PoolGame.NONE) return;
            float r = 6.5f * d;
            int from = g == PoolGame.SOLIDS ? 1 : 9;
            float cx = x + (rightward ? r : -r);
            for (int b = from; b < from + 7; b++) {
                if (!game.on[b]) continue;
                ballTiny(c, b, cx, y, r);
                cx += (rightward ? 1 : -1) * r * 2.3f;
            }
        }

        private void ballTiny(Canvas c, int b, float x, float y, float r) {
            paint.setColor(b >= 9 ? COLORS[0] : colorOf(b));
            c.drawCircle(x, y, r, paint);
            if (b >= 9) {
                paint.setColor(colorOf(b));
                c.drawRect(x - r * 0.95f, y - r * 0.5f, x + r * 0.95f, y + r * 0.5f, paint);
            }
        }

        // ---------------------------------------------------------------- touch

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            int idx = e.getActionIndex();
            int id = e.getPointerId(idx);
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
                float x = e.getX(idx), y = e.getY(idx);
                if (!myTurn()) return true;
                RectF pr = new RectF(powerRect);
                pr.inset(-14 * d, -10 * d);
                if (pr.contains(x, y)) {
                    powerPtr = id;
                    powerY0 = y;
                    power = 0;
                } else if (Math.hypot(x - spinCx, y - spinCy) < spinR + 14 * d) {
                    spinPtr = id;
                    setSpin(y);
                } else if (fineRect.contains(x, y) || y > fineRect.top - 6 * d
                        && x > fineRect.left && x < fineRect.right) {
                    finePtr = id;
                    fineX0 = x;
                } else if (game.ballInHand && Math.hypot(x - px(game.x[0]), y - py(game.y[0]))
                        < PoolGame.R * s + 26 * d) {
                    cuePtr = id;
                } else if (tableRect.contains(x, y) || x < powerRect.left - 14 * d) {
                    aimPtr = id;
                    aimAt(x, y);
                }
                invalidate();
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i);
                    float x = e.getX(i), y = e.getY(i);
                    if (pid == powerPtr) {
                        power = Math.max(0, Math.min(1, (y - powerY0)
                                / (powerRect.height() * 0.85f)));
                    } else if (pid == spinPtr) {
                        setSpin(y);
                    } else if (pid == finePtr) {
                        float dx = x - fineX0;
                        fineX0 = x;
                        aim += dx * 0.0011f / d;
                        fineScroll += dx;
                    } else if (pid == cuePtr) {
                        game.place((x - ox) / s, (y - oy) / s);
                    } else if (pid == aimPtr) {
                        aimAt(x, y);
                    }
                }
                invalidate();
                return true;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP
                    || act == MotionEvent.ACTION_CANCEL) {
                if (id == powerPtr) {
                    powerPtr = -1;
                    if (act != MotionEvent.ACTION_CANCEL && power > 0.03f && myTurn()) {
                        game.ballInHand = false;
                        startStroke(power, spin);
                    } else {
                        power = 0;
                    }
                }
                if (id == aimPtr) aimPtr = -1;
                if (id == finePtr) finePtr = -1;
                if (id == spinPtr) spinPtr = -1;
                if (id == cuePtr) cuePtr = -1;
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    aimPtr = finePtr = spinPtr = cuePtr = -1;
                    if (powerPtr >= 0) {
                        powerPtr = -1;
                        power = 0;
                    }
                }
                invalidate();
                return true;
            }
            return true;
        }

        private void aimAt(float x, float y) {
            float dx = x - px(game.x[0]), dy = y - py(game.y[0]);
            if (Math.hypot(dx, dy) > 4 * d) aim = (float) Math.atan2(dy, dx);
        }

        private void setSpin(float y) {
            spin = Math.max(-1, Math.min(1, (spinCy - y) / (spinR * 0.75f)));
            if (Math.abs(spin) < 0.12f) spin = 0;
        }
    }

    static float angleDiff(float a, float b) {
        float d = a - b;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }
}
