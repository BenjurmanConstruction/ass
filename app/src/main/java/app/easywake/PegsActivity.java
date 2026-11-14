package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Peg Pop: aim the cannon, fire a ball and let it bounce down through a pattern of
 * pegs. Clear every gold peg before you run out of balls. Unlocked on the skill tree.
 */
public class PegsActivity extends Activity {
    private static final int BALLS = 10;
    /** Most Knowledge Points this game can pay in one day. */
    private static final int DAILY_KP = 50;
    private static final String[] BOARD_NAMES = {"Rainbows", "Bullseye", "Diamond", "Waves",
            "Chevrons", "Pillars", "Spiral", "Crossroads"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BoardView board;
    private TextView info;
    private int level;
    private int score;
    private int balls;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "pegs")) {
            Toast.makeText(this, "Unlock Peg Pop in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 12);
        root.setPadding(pad, pad, pad, pad);
        LinearLayout bar = Ui.topBar(this, "Peg Pop");
        Button how = small("How");
        how.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHow();
            }
        });
        bar.addView(how, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 40)));
        root.addView(bar, Ui.full(this, 0));

        info = Ui.text(this, "", 14, Ui.ACCENT);
        info.setTypeface(Ui.medium());
        root.addView(info, Ui.full(this, 6));

        board = new BoardView(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bp.topMargin = Ui.dp(this, 6);
        root.addView(board, bp);

        // Aim a hair left, fire, aim a hair right.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(nudge("◀", -1), side());
        Button fire = Ui.button(this, "FIRE", true);
        fire.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        fire.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                board.shoot();
            }
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 2f);
        int m = Ui.dp(this, 4);
        fp.setMargins(m, 0, m, 0);
        controls.addView(fire, fp);
        controls.addView(nudge("▶", 1), side());
        root.addView(controls, Ui.full(this, 8));

        setContentView(root);
        Bg.apply(this, root);
        newGame();
    }

    private Button small(String label) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bt.setPadding(0, 0, 0, 0);
        return bt;
    }

    private LinearLayout.LayoutParams side() {
        return new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f);
    }

    /** A button that turns the cannon a little, and keeps turning while held. */
    private Button nudge(String label, final int dir) {
        final Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        bt.setPadding(0, 0, 0, 0);
        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                board.turn(dir * 0.012f);
                handler.postDelayed(this, 40);
            }
        };
        bt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.setPressed(true);
                    board.turn(dir * 0.006f);
                    handler.postDelayed(repeat, 300);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.setPressed(false);
                    handler.removeCallbacks(repeat);
                }
                return true;
            }
        });
        return bt;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void newGame() {
        level = 1;
        score = 0;
        balls = BALLS;
        board.build(level);
        paint();
    }

    private void paint() {
        info.setText(String.format(Locale.US, "Level %d: %s   •   Balls %d   •   Gold "
                + "%d   •   x%d   •   %,d", level,
                BOARD_NAMES[(level - 1) % BOARD_NAMES.length], balls, board.goldLeft(),
                board.multiplier(), score));
    }

    /** Called by the board when a shot has finished and its pegs are cleared. */
    private void shotDone(int points, boolean caught, int feverBonus) {
        score += points + feverBonus;
        if (caught) {
            balls++;
            Toast.makeText(this, "In the bucket. Free ball!", Toast.LENGTH_SHORT).show();
        }
        if (board.goldLeft() == 0) {
            int bonus = balls * 1000;
            score += bonus;
            Sfx.play(this, Sfx.BIG_WIN);
            final int cleared = level;
            level++;
            balls = BALLS;
            board.build(level);
            paint();
            new AlertDialog.Builder(this)
                    .setTitle("Level " + cleared + " cleared!")
                    .setMessage(String.format(Locale.US, "Final shot bonus: %,d\nBalls left "
                            + "bonus: %,d\nScore: %,d\n\nNext board: %s", feverBonus, bonus,
                            score, BOARD_NAMES[(level - 1) % BOARD_NAMES.length]))
                    .setPositiveButton("Next level", null)
                    .show();
            return;
        }
        if (balls <= 0) {
            gameOver();
            return;
        }
        paint();
    }

    private void gameOver() {
        int best = Prefs.getInt(this, "pegsBest", 0);
        boolean record = score > best;
        if (record) Prefs.putInt(this, "pegsBest", score);
        int earnedToday = Prefs.getInt(this, "pegsKpDay", 0) == Progress.today()
                ? Prefs.getInt(this, "pegsKp", 0) : 0;
        int kp = Math.max(0, Math.min(Math.min(25, score / 10000), DAILY_KP - earnedToday));
        if (kp > 0) {
            Scores.addPoints(this, kp);
            Prefs.putInt(this, "pegsKpDay", Progress.today());
            Prefs.putInt(this, "pegsKp", earnedToday + kp);
        }
        Stats.add(this, "pegsGames", 1);
        Stats.log(this, String.format(Locale.US, "Peg Pop: %,d points, reached level %d",
                score, level));
        String msg = String.format(Locale.US, "Score %,d\nReached level %d", score, level)
                + (record ? "\n\nNew best score!" : String.format(Locale.US, "\nBest: %,d",
                best))
                + (kp > 0 ? "\n\n+" + kp + " Knowledge Points"
                : "\n\nNo Knowledge Points this time. They come at 1 per 10,000 points, up "
                + "to " + DAILY_KP + " a day.");
        paint();
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Out of balls")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Play again", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        newGame();
                    }
                })
                .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .show();
    }

    private void showHow() {
        new AlertDialog.Builder(this)
                .setTitle("How to play")
                .setMessage("Drag on the board to swing the cannon, or use the arrows for "
                        + "fine aim. The dotted line shows the ball's path up to its "
                        + "first bounce and a little beyond. Press FIRE to shoot.\n\n"
                        + "Every peg the ball touches lights up and pops when the shot "
                        + "ends. Clear all the GOLD pegs to win the level.\n\n"
                        + "• Blue pegs: 10 points\n"
                        + "• Gold pegs: 100 points\n"
                        + "• Purple peg: 500 points. It moves every shot.\n"
                        + "• Green pegs: fire a second ball from the peg.\n\n"
                        + "As the gold pegs run down, every point is multiplied: x2, x3, "
                        + "x5 and finally x10 for the last few. Long shots also build a "
                        + "combo.\n\n"
                        + "Land a ball in the bucket for a free ball. When you hit the "
                        + "last gold peg the ball slows down and drops into one of five "
                        + "bonus slots, worth up to 100,000.\n\n"
                        + "You get " + BALLS + " balls a level, and each ball left over "
                        + "is worth 1,000. When the game ends you earn 1 Knowledge Point "
                        + "per 10,000 points, up to 25 a game and " + DAILY_KP + " a day.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private static final class Peg {
        float x, y;
        /** 0 blue, 1 gold, 2 green, 3 purple. */
        int kind;
        boolean hit;
    }

    private static final class Ball {
        float x, y, vx, vy;
        float slowFor;
        boolean alive = true;
    }

    /** The board: pegs, balls, the bucket and the physics that moves them. */
    final class BoardView extends View {
        private static final float PEG_R = 0.021f;
        private static final float BALL_R = 0.018f;
        private static final float SPEED = 1.2f;
        private static final float GRAVITY = 1.45f;
        private static final float CANNON_Y = 0.07f;
        private static final float BUCKET_HALF = 0.13f;
        private final int[] colors = {0xFF4FA8F0, 0xFFFFA726, 0xFF66D17A, 0xFFC77DFF};
        private final int[] feverSlots = {10000, 50000, 100000, 50000, 10000};

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final Random rnd = new Random();
        private final List<Peg> pegs = new ArrayList<>();
        private final List<Ball> flying = new ArrayList<>();
        private int pendingLevel = -1;
        private int goldAtStart = 1;

        /** Cannon angle in radians: 0 points straight down, positive to the right. */
        private float angle = 0.3f;
        private boolean shooting;
        private boolean fever;
        private boolean caught;
        private int feverBonus;
        private long lastStep;
        private long shotStart;
        private int shotHits;
        private int shotPoints;
        private float bucketPhase;

        BoardView(Context c) {
            super(c);
        }

        private float heightUnits() {
            return getWidth() <= 0 ? 1.4f : getHeight() / (float) getWidth();
        }

        int goldLeft() {
            int n = 0;
            for (Peg p : pegs) {
                if (p.kind == 1) n++;
            }
            return n;
        }

        /** Points are worth more as the gold pegs run out. */
        int multiplier() {
            float left = goldLeft() / (float) Math.max(1, goldAtStart);
            if (left > 0.6f) return 1;
            if (left > 0.4f) return 2;
            if (left > 0.24f) return 3;
            if (left > 0.1f) return 5;
            return 10;
        }

        private float bucketX() {
            return 0.5f + 0.37f * (float) Math.sin(bucketPhase);
        }

        void turn(float by) {
            if (shooting) return;
            angle = Math.max(-1.35f, Math.min(1.35f, angle + by));
            invalidate();
        }

        // ---------- boards ----------

        private void put(float x, float y) {
            if (x < 0.06f || x > 0.94f) return;
            for (Peg p : pegs) {
                float dx = p.x - x;
                float dy = p.y - y;
                if (dx * dx + dy * dy < (PEG_R * 2.5f) * (PEG_R * 2.5f)) return;
            }
            Peg p = new Peg();
            p.x = x;
            p.y = y;
            pegs.add(p);
        }

        void build(int lvl) {
            if (getWidth() <= 0) {
                // Not laid out yet; build once the size is known.
                pendingLevel = lvl;
                return;
            }
            pendingLevel = -1;
            pegs.clear();
            flying.clear();
            shooting = false;
            fever = false;
            float h = heightUnits();
            float top = 0.3f;
            float bottom = h - 0.24f;
            float span = bottom - top;
            float mid = (top + bottom) / 2f;
            switch ((lvl - 1) % BOARD_NAMES.length) {
                case 0:
                    // Four arcs, each sagging in the middle.
                    for (int row = 0; row < 4; row++) {
                        for (int i = 0; i < 10; i++) {
                            float u = i / 9f;
                            put(0.09f + 0.82f * u, top + row * span / 4f
                                    + span * 0.2f * (float) Math.sin(Math.PI * u));
                        }
                    }
                    break;
                case 1:
                    // Three rings around a center peg.
                    put(0.5f, mid);
                    for (int ring = 1; ring <= 3; ring++) {
                        int n = ring * 7;
                        float r = Math.min(0.42f, span / 2f) * ring / 3f;
                        for (int i = 0; i < n; i++) {
                            double a = Math.PI * 2 * i / n + ring;
                            put(0.5f + r * (float) Math.cos(a), mid + r * (float) Math.sin(a));
                        }
                    }
                    break;
                case 2: {
                    // Rows that widen to the middle and narrow again.
                    int rows = 9;
                    for (int row = 0; row < rows; row++) {
                        int n = 2 + 2 * Math.min(row, rows - 1 - row);
                        for (int i = 0; i < n; i++) {
                            put(0.5f + (i - (n - 1) / 2f) * 0.088f, top + row * span / (rows - 1));
                        }
                    }
                    break;
                }
                case 3:
                    // Four wavy lines.
                    for (int row = 0; row < 4; row++) {
                        for (int i = 0; i < 11; i++) {
                            float u = i / 10f;
                            put(0.08f + 0.84f * u, top + (row + 0.5f) * span / 4f
                                    + span * 0.09f * (float) Math.sin(u * Math.PI * 3
                                    + row * 1.6f));
                        }
                    }
                    break;
                case 4:
                    // Stacked V shapes.
                    for (int row = 0; row < 4; row++) {
                        for (int i = 0; i < 9; i++) {
                            float u = i / 8f;
                            put(0.1f + 0.8f * u, top + row * span / 4.2f
                                    + span * 0.22f * (1 - Math.abs(u - 0.5f) * 2));
                        }
                    }
                    break;
                case 5:
                    // Columns, every other one shifted down, under a small arch.
                    for (int col = 0; col < 7; col++) {
                        for (int i = 0; i < 6; i++) {
                            put(0.11f + col * 0.13f, top + span * 0.16f + (i + (col % 2) * 0.5f)
                                    * span * 0.15f);
                        }
                    }
                    for (int i = 0; i < 7; i++) {
                        float u = i / 6f;
                        put(0.2f + 0.6f * u, top + span * 0.05f
                                - span * 0.05f * (float) Math.sin(Math.PI * u));
                    }
                    break;
                case 6:
                    // A spiral winding out from the center.
                    for (int i = 0; i < 46; i++) {
                        double a = i * 0.52;
                        float r = 0.04f + i * Math.min(0.42f, span / 2f) / 46f;
                        put(0.5f + r * (float) Math.cos(a), mid + r * (float) Math.sin(a));
                    }
                    break;
                default:
                    // Two diagonals crossing, with a ring where they meet.
                    for (int i = 0; i < 13; i++) {
                        float u = i / 12f;
                        put(0.1f + 0.8f * u, top + span * u);
                        put(0.9f - 0.8f * u, top + span * u);
                    }
                    for (int i = 0; i < 10; i++) {
                        double a = Math.PI * 2 * i / 10;
                        put(0.5f + 0.2f * (float) Math.cos(a), mid + 0.2f * (float) Math.sin(a));
                    }
                    for (int i = 0; i < 5; i++) {
                        put(0.1f + i * 0.05f, bottom - i * 0.004f);
                        put(0.9f - i * 0.05f, bottom - i * 0.004f);
                    }
                    break;
            }
            // About a third of the pegs are gold, a few more each time round the boards.
            List<Peg> pool = new ArrayList<>(pegs);
            Collections.shuffle(pool, rnd);
            int gold = Math.min(pool.size() - 6, Math.min(25, 12 + lvl));
            for (int i = 0; i < gold; i++) pool.get(i).kind = 1;
            for (int i = gold; i < gold + 2 && i < pool.size(); i++) pool.get(i).kind = 2;
            goldAtStart = Math.max(1, gold);
            pickPurple();
            invalidate();
        }

        /** Moves the purple bonus peg to a different blue peg. */
        private void pickPurple() {
            List<Peg> blue = new ArrayList<>();
            for (Peg p : pegs) {
                if (p.kind == 3) p.kind = 0;
                if (p.kind == 0) blue.add(p);
            }
            if (!blue.isEmpty()) blue.get(rnd.nextInt(blue.size())).kind = 3;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            if (pendingLevel > 0 || pegs.isEmpty()) {
                build(pendingLevel > 0 ? pendingLevel : level);
                PegsActivity.this.paint();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (shooting || getWidth() <= 0) return true;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                float x = e.getX() / getWidth() - 0.5f;
                float y = Math.max(0.03f, e.getY() / getWidth() - CANNON_Y);
                angle = Math.max(-1.35f, Math.min(1.35f, (float) Math.atan2(x, y)));
                invalidate();
            }
            return true;
        }

        void shoot() {
            if (shooting || balls <= 0 || getWidth() <= 0) return;
            Ball b = new Ball();
            b.x = 0.5f + (float) Math.sin(angle) * 0.05f;
            b.y = CANNON_Y + (float) Math.cos(angle) * 0.05f;
            b.vx = (float) Math.sin(angle) * SPEED;
            b.vy = (float) Math.cos(angle) * SPEED;
            flying.clear();
            flying.add(b);
            shooting = true;
            fever = false;
            caught = false;
            feverBonus = 0;
            balls--;
            shotHits = 0;
            shotPoints = 0;
            lastStep = SystemClock.uptimeMillis();
            shotStart = lastStep;
            Sfx.play(getContext(), Sfx.CHIPS);
            PegsActivity.this.paint();
            invalidate();
        }

        /**
         * Moves one ball forward a small step.
         *
         * @param real false when only tracing the aim line, so nothing is scored
         * @return true if the ball touched a peg or a wall on this step
         */
        private boolean step(Ball b, float dt, boolean real) {
            boolean bounced = false;
            b.vy += GRAVITY * dt;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.x < BALL_R) {
                b.x = BALL_R;
                b.vx = Math.abs(b.vx) * 0.88f;
                bounced = true;
            } else if (b.x > 1 - BALL_R) {
                b.x = 1 - BALL_R;
                b.vx = -Math.abs(b.vx) * 0.88f;
                bounced = true;
            }
            if (b.y < BALL_R) {
                b.y = BALL_R;
                b.vy = Math.abs(b.vy) * 0.88f;
                bounced = true;
            }
            float reach = BALL_R + PEG_R;
            for (int i = 0; i < pegs.size(); i++) {
                Peg p = pegs.get(i);
                float dx = b.x - p.x;
                float dy = b.y - p.y;
                float d2 = dx * dx + dy * dy;
                if (d2 >= reach * reach || d2 == 0) continue;
                float d = (float) Math.sqrt(d2);
                float nx = dx / d;
                float ny = dy / d;
                // Push the ball back out of the peg and bounce it off.
                b.x = p.x + nx * reach;
                b.y = p.y + ny * reach;
                float along = b.vx * nx + b.vy * ny;
                if (along < 0) {
                    b.vx -= 1.86f * along * nx;
                    b.vy -= 1.86f * along * ny;
                }
                bounced = true;
                if (real && !p.hit) hitPeg(p);
            }
            return bounced;
        }

        private void hitPeg(Peg p) {
            int before = multiplier();
            p.hit = true;
            shotHits++;
            int base = p.kind == 1 ? 100 : (p.kind == 3 ? 500 : 10);
            // A long run of pegs in one shot builds a combo on top of the multiplier.
            shotPoints += base * before * (1 + shotHits / 5);
            Sfx.play(getContext(), Sfx.PEG);
            if (p.kind == 2) {
                // Green: a second ball bursts out of the peg.
                Ball extra = new Ball();
                extra.x = p.x;
                extra.y = p.y - PEG_R * 2.2f;
                extra.vx = (rnd.nextBoolean() ? 0.5f : -0.5f);
                extra.vy = -0.55f;
                flying.add(extra);
            }
            if (p.kind == 1) {
                boolean anyLeft = false;
                for (Peg q : pegs) {
                    if (q.kind == 1 && !q.hit) anyLeft = true;
                }
                if (!anyLeft) fever = true;
            }
        }

        private void clearHit() {
            for (int i = pegs.size() - 1; i >= 0; i--) {
                if (pegs.get(i).hit) pegs.remove(i);
            }
        }

        private void advance(float dt) {
            float h = heightUnits();
            bucketPhase += dt * 0.75f;
            for (int i = flying.size() - 1; i >= 0; i--) {
                Ball b = flying.get(i);
                step(b, dt, true);
                // A ball resting on pegs would never finish, so once it has crawled for
                // a while, pop the pegs it touched and let it fall.
                float speed = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                b.slowFor = speed < 0.12f ? b.slowFor + dt : 0;
                if (b.slowFor > 1.1f) {
                    clearHit();
                    b.slowFor = 0;
                }
                if (fever) {
                    if (b.y > h - 0.06f) {
                        int slot = Math.max(0, Math.min(4, (int) (b.x * 5)));
                        feverBonus = Math.max(feverBonus, feverSlots[slot]);
                        flying.remove(i);
                    }
                } else if (b.y > h - 0.075f && b.vy > 0
                        && Math.abs(b.x - bucketX()) < BUCKET_HALF - BALL_R * 0.5f) {
                    caught = true;
                    flying.remove(i);
                } else if (b.y > h + BALL_R) {
                    flying.remove(i);
                }
            }
            if (SystemClock.uptimeMillis() - shotStart > 40000) flying.clear();
            if (flying.isEmpty()) {
                shooting = false;
                clearHit();
                pickPurple();
                if (caught) Sfx.play(getContext(), Sfx.WIN);
                shotDone(shotPoints, caught, feverBonus);
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0) return;
            float hu = heightUnits();

            long now = SystemClock.uptimeMillis();
            if (shooting) {
                float dt = Math.min(0.05f, (now - lastStep) / 1000f);
                // The last gold peg plays out in slow motion.
                if (fever) dt *= 0.4f;
                lastStep = now;
                // Several small steps a frame, so a fast ball can't pass through a peg.
                for (int i = 0; i < 6 && shooting; i++) advance(dt / 6f);
            } else {
                bucketPhase += 0.012f;
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x77000000);
            rect.set(0, 0, w, h);
            cv.drawRoundRect(rect, w * 0.04f, w * 0.04f, paint);

            // The aim line: the path up to the first bounce and a little after.
            if (!shooting && balls > 0) {
                Ball t = new Ball();
                t.x = 0.5f + (float) Math.sin(angle) * 0.05f;
                t.y = CANNON_Y + (float) Math.cos(angle) * 0.05f;
                t.vx = (float) Math.sin(angle) * SPEED;
                t.vy = (float) Math.cos(angle) * SPEED;
                int afterBounce = -1;
                paint.setColor(0xDDFFFFFF);
                for (int i = 0; i < 520; i++) {
                    boolean hit = step(t, 0.004f, false);
                    if (hit && afterBounce < 0) afterBounce = 0;
                    if (afterBounce >= 0 && ++afterBounce > 55) break;
                    if (t.y > hu) break;
                    if (i % 6 == 0) {
                        paint.setAlpha(afterBounce > 0 ? 110 : 220);
                        cv.drawCircle(t.x * w, t.y * w, (afterBounce > 0 ? 0.005f : 0.007f) * w,
                                paint);
                    }
                }
                paint.setAlpha(255);
            }

            for (Peg p : pegs) {
                int color = colors[p.kind];
                if (p.hit) {
                    paint.setColor((color & 0x00FFFFFF) | 0x66000000);
                    cv.drawCircle(p.x * w, p.y * w, PEG_R * w * 1.8f, paint);
                    paint.setColor(0xFFFFFFFF);
                    cv.drawCircle(p.x * w, p.y * w, PEG_R * w, paint);
                } else {
                    paint.setColor(color);
                    cv.drawCircle(p.x * w, p.y * w, PEG_R * w, paint);
                    paint.setColor(0x66FFFFFF);
                    cv.drawCircle(p.x * w - PEG_R * w * 0.3f, p.y * w - PEG_R * w * 0.3f,
                            PEG_R * w * 0.38f, paint);
                }
            }

            if (fever) {
                // Five bonus slots along the bottom for the final ball.
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(0.035f * w);
                paint.setFakeBoldText(true);
                for (int i = 0; i < 5; i++) {
                    paint.setColor(i == 2 ? Ui.ACCENT : (i % 2 == 1 ? 0xFFC77DFF : 0xFF4FA8F0));
                    rect.set(i * w / 5f + 0.008f * w, h - 0.07f * w, (i + 1) * w / 5f - 0.008f * w,
                            h);
                    cv.drawRoundRect(rect, 0.015f * w, 0.015f * w, paint);
                    paint.setColor(0xFF10141F);
                    cv.drawText(feverSlots[i] / 1000 + "K", (i + 0.5f) * w / 5f,
                            h - 0.022f * w, paint);
                }
                paint.setFakeBoldText(false);
            } else {
                // The bucket: wider at the top, like a real one.
                float bx = bucketX() * w;
                float half = BUCKET_HALF * w;
                path.reset();
                path.moveTo(bx - half, h - 0.075f * w);
                path.lineTo(bx + half, h - 0.075f * w);
                path.lineTo(bx + half * 0.8f, h);
                path.lineTo(bx - half * 0.8f, h);
                path.close();
                paint.setColor(Ui.ACCENT_LO);
                cv.drawPath(path, paint);
                paint.setColor(0xFF10141F);
                rect.set(bx - half * 0.86f, h - 0.065f * w, bx + half * 0.86f, h - 0.03f * w);
                cv.drawRoundRect(rect, 0.012f * w, 0.012f * w, paint);
                paint.setColor(Ui.ACCENT_HI);
                rect.set(bx - half, h - 0.082f * w, bx + half, h - 0.068f * w);
                cv.drawRoundRect(rect, 0.007f * w, 0.007f * w, paint);
            }

            // The cannon.
            float cx = 0.5f * w;
            float cy = CANNON_Y * w;
            cv.save();
            cv.rotate((float) Math.toDegrees(-angle), cx, cy);
            paint.setColor(Ui.ACCENT_LO);
            rect.set(cx - 0.026f * w, cy, cx + 0.026f * w, cy + 0.075f * w);
            cv.drawRoundRect(rect, 0.012f * w, 0.012f * w, paint);
            cv.restore();
            paint.setColor(Ui.ACCENT);
            cv.drawCircle(cx, cy, 0.04f * w, paint);
            paint.setColor(0xFF10141F);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(0.04f * w);
            paint.setFakeBoldText(true);
            cv.drawText(String.valueOf(balls), cx, cy - (paint.ascent() + paint.descent()) / 2f,
                    paint);
            paint.setFakeBoldText(false);

            paint.setColor(0xFFFFFFFF);
            for (Ball b : flying) cv.drawCircle(b.x * w, b.y * w, BALL_R * w, paint);
            postInvalidateOnAnimation();
        }
    }
}
