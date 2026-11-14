package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
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
 * Block Drop: a falling-blocks puzzle. Slide and turn the falling shapes to fill whole
 * rows; full rows clear and score. Unlocked on the skill tree, and clearing rows earns
 * Knowledge Points.
 */
public class BlocksActivity extends Activity {
    private static final int COLS = 10;
    private static final int ROWS = 20;
    /** Most Knowledge Points this game can pay in one day. */
    private static final int DAILY_KP = 50;

    /** The seven shapes, each as four cells (x, y) in its starting position. */
    private static final int[][][] SHAPES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},   // long bar
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},   // square
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},   // tee
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},   // left hook
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}},   // right hook
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},   // right step
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},   // left step
    };
    /** The box each shape turns inside. */
    private static final int[] BOX = {4, 4, 3, 3, 3, 3, 3};
    private static final int[] COLORS = {0xFF4DD0E1, 0xFFFFD54F, 0xFFBA68C8, 0xFF64B5F6,
            0xFFFFB74D, 0xFF81C784, 0xFFE57373};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final int[][] board = new int[ROWS][COLS];
    private final List<Integer> bag = new ArrayList<>();
    private int kind;
    private int nextKind;
    private int[][] cells;
    private int px;
    private int py;
    private int score;
    private int lines;
    private boolean running;
    private boolean over;
    private boolean paused;

    private BoardView boardView;
    private NextView nextView;
    private TextView scoreText;
    private TextView infoText;
    private Button pauseBtn;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!move(0, 1)) land();
            if (running) handler.postDelayed(this, delay());
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "blocks")) {
            Toast.makeText(this, "Unlock Block Drop in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.topBar(this, "Block Drop"), Ui.full(this, 0));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        boardView = new BoardView(this);
        middle.addView(boardView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(Ui.dp(this, 10), 0, 0, 0);
        side.addView(Ui.text(this, "NEXT", 11, Ui.SUB));
        nextView = new NextView(this);
        side.addView(nextView, new LinearLayout.LayoutParams(Ui.dp(this, 76),
                Ui.dp(this, 60)));
        scoreText = Ui.text(this, "", 20, Ui.ACCENT);
        scoreText.setTypeface(Ui.medium());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 12);
        side.addView(scoreText, sp);
        infoText = Ui.text(this, "", 13, Ui.SUB);
        side.addView(infoText);
        pauseBtn = Ui.button(this, "Pause", false);
        pauseBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pauseBtn.setPadding(0, 0, 0, 0);
        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (over) {
                    newGame();
                } else {
                    setPaused(!paused);
                }
            }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.dp(this, 76),
                Ui.dp(this, 44));
        pp.topMargin = Ui.dp(this, 14);
        side.addView(pauseBtn, pp);
        Button how = Ui.button(this, "How", false);
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        how.setPadding(0, 0, 0, 0);
        how.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!over) setPaused(true);
                showHow();
            }
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(this, 76),
                Ui.dp(this, 44));
        hp.topMargin = Ui.dp(this, 8);
        side.addView(how, hp);
        middle.addView(side, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        mp.topMargin = Ui.dp(this, 10);
        root.addView(middle, mp);

        // Controls.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(hold("◀", new Runnable() {
            @Override
            public void run() {
                move(-1, 0);
            }
        }), weight());
        controls.addView(hold("▶", new Runnable() {
            @Override
            public void run() {
                move(1, 0);
            }
        }), weight());
        controls.addView(tap("↻", new Runnable() {
            @Override
            public void run() {
                turn();
            }
        }), weight());
        controls.addView(hold("▼", new Runnable() {
            @Override
            public void run() {
                if (move(0, 1)) score++;
                paint();
            }
        }), weight());
        controls.addView(tap("DROP", new Runnable() {
            @Override
            public void run() {
                drop();
            }
        }), weight());
        root.addView(controls, Ui.full(this, 10));

        setContentView(root);
        Bg.apply(this, root);
        newGame();
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
        int m = Ui.dp(this, 3);
        p.setMargins(m, 0, m, 0);
        return p;
    }

    private Button plain(String label) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 1 ? 14 : 22);
        bt.setPadding(0, 0, 0, 0);
        return bt;
    }

    private Button tap(String label, final Runnable action) {
        Button bt = plain(label);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) action.run();
            }
        });
        return bt;
    }

    /** A button that repeats while it is held down. */
    private Button hold(String label, final Runnable action) {
        final Button bt = plain(label);
        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                action.run();
                handler.postDelayed(this, 60);
            }
        };
        bt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (running) action.run();
                        handler.postDelayed(repeat, 230);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        handler.removeCallbacks(repeat);
                        return true;
                    default:
                        return true;
                }
            }
        });
        return bt;
    }

    private int level() {
        return lines / 10 + 1;
    }

    private long delay() {
        return Math.max(90, 720 - (level() - 1) * 60L);
    }

    private void newGame() {
        for (int[] row : board) java.util.Arrays.fill(row, 0);
        bag.clear();
        score = 0;
        lines = 0;
        over = false;
        paused = false;
        nextKind = draw();
        spawn();
        running = true;
        pauseBtn.setText("Pause");
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delay());
        paint();
    }

    /** Deals shapes from a shuffled bag of all seven, so none goes missing for long. */
    private int draw() {
        if (bag.isEmpty()) {
            for (int i = 0; i < SHAPES.length; i++) bag.add(i);
            Collections.shuffle(bag, rnd);
        }
        return bag.remove(bag.size() - 1);
    }

    private void spawn() {
        kind = nextKind;
        nextKind = draw();
        cells = new int[4][2];
        for (int i = 0; i < 4; i++) {
            cells[i][0] = SHAPES[kind][i][0];
            cells[i][1] = SHAPES[kind][i][1];
        }
        px = (COLS - BOX[kind]) / 2;
        py = kind == 0 ? -1 : 0;
        if (!fits(cells, px, py)) gameOver();
    }

    private boolean fits(int[][] shape, int x, int y) {
        for (int[] c : shape) {
            int cx = x + c[0];
            int cy = y + c[1];
            if (cx < 0 || cx >= COLS || cy >= ROWS) return false;
            if (cy >= 0 && board[cy][cx] != 0) return false;
        }
        return true;
    }

    private boolean move(int dx, int dy) {
        if (!running) return false;
        if (!fits(cells, px + dx, py + dy)) return false;
        px += dx;
        py += dy;
        boardView.invalidate();
        return true;
    }

    private void turn() {
        if (!running || kind == 1) return;
        int n = BOX[kind];
        int[][] turned = new int[4][2];
        for (int i = 0; i < 4; i++) {
            turned[i][0] = n - 1 - cells[i][1];
            turned[i][1] = cells[i][0];
        }
        // If the turned shape hits a wall or a block, try nudging it sideways or up.
        int[][] kicks = {{0, 0}, {-1, 0}, {1, 0}, {-2, 0}, {2, 0}, {0, -1}};
        for (int[] k : kicks) {
            if (fits(turned, px + k[0], py + k[1])) {
                cells = turned;
                px += k[0];
                py += k[1];
                boardView.invalidate();
                return;
            }
        }
    }

    private void drop() {
        if (!running) return;
        int fell = 0;
        while (fits(cells, px, py + 1)) {
            py++;
            fell++;
        }
        score += fell * 2;
        boardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        land();
        if (running) {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, delay());
        }
    }

    private int ghostY() {
        int y = py;
        while (fits(cells, px, y + 1)) y++;
        return y;
    }

    private void land() {
        for (int[] c : cells) {
            int cy = py + c[1];
            int cx = px + c[0];
            if (cy >= 0) board[cy][cx] = kind + 1;
        }
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(board[rr - 1], 0, board[rr], 0, COLS);
                }
                java.util.Arrays.fill(board[0], 0);
                r++;
            }
        }
        if (cleared > 0) {
            int[] worth = {0, 100, 300, 500, 800};
            score += worth[cleared] * level();
            lines += cleared;
            Sfx.play(this, cleared >= 4 ? Sfx.BIG_WIN : Sfx.WIN);
            boardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        spawn();
        paint();
    }

    private void setPaused(boolean p) {
        if (over) return;
        paused = p;
        running = !p;
        handler.removeCallbacks(tick);
        if (running) handler.postDelayed(tick, delay());
        pauseBtn.setText(p ? "Resume" : "Pause");
        boardView.invalidate();
    }

    private void gameOver() {
        running = false;
        over = true;
        handler.removeCallbacks(tick);
        pauseBtn.setText("New");
        int best = Prefs.getInt(this, "blocksBest", 0);
        boolean record = score > best;
        if (record) Prefs.putInt(this, "blocksBest", score);

        // Two cleared rows earn a Knowledge Point, up to a daily limit.
        int earnedToday = Prefs.getInt(this, "blocksKpDay", 0) == Progress.today()
                ? Prefs.getInt(this, "blocksKp", 0) : 0;
        int kp = Math.max(0, Math.min(lines / 2, DAILY_KP - earnedToday));
        if (kp > 0) {
            Scores.addPoints(this, kp);
            Prefs.putInt(this, "blocksKpDay", Progress.today());
            Prefs.putInt(this, "blocksKp", earnedToday + kp);
        }
        Stats.add(this, "blocksGames", 1);
        Stats.add(this, "blocksRows", lines);
        Stats.log(this, String.format(Locale.US, "Block Drop: %,d points, %d rows", score,
                lines));
        String msg = String.format(Locale.US, "Score %,d\nRows cleared: %d\nLevel %d",
                score, lines, level())
                + (record ? "\n\nNew best score!" : String.format(Locale.US,
                "\nBest: %,d", best))
                + (kp > 0 ? "\n\n+" + kp + " Knowledge Points"
                : (lines >= 2 ? "\n\nYou've earned today's " + DAILY_KP
                + " Knowledge Points from this game. More tomorrow."
                : "\n\nClear two rows to earn a Knowledge Point."));
        paint();
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Game over")
                .setMessage(msg)
                .setPositiveButton("Play again", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        newGame();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showHow() {
        new AlertDialog.Builder(this)
                .setTitle("How to play")
                .setMessage("Shapes fall from the top. Fill a whole row with no gaps and "
                        + "it clears.\n\n"
                        + "◀ ▶  slide the shape (hold to keep sliding)\n"
                        + "↻  turn it (or tap the board)\n"
                        + "▼  fall faster\n"
                        + "DROP  send it straight down\n\n"
                        + "The faint outline shows where the shape will land. Clearing "
                        + "more rows at once scores more: 100, 300, 500, then 800 for "
                        + "four, times your level. Every 10 rows the level goes up and "
                        + "the shapes fall faster.\n\n"
                        + "Every 2 rows cleared earns 1 Knowledge Point when the game "
                        + "ends, up to " + DAILY_KP + " a day.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void paint() {
        scoreText.setText(String.format(Locale.US, "%,d", score));
        infoText.setText(String.format(Locale.US, "Rows %d\nLevel %d\nBest %,d", lines,
                level(), Math.max(score, Prefs.getInt(this, "blocksBest", 0))));
        boardView.invalidate();
        nextView.invalidate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) setPaused(true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static void block(Canvas cv, Paint p, RectF r, float x, float y, float size,
                              int color, boolean outlineOnly) {
        float gap = size * 0.06f;
        r.set(x + gap, y + gap, x + size - gap, y + size - gap);
        float rad = size * 0.18f;
        if (outlineOnly) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.07f);
            p.setColor((color & 0x00FFFFFF) | 0x77000000);
            cv.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            return;
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        cv.drawRoundRect(r, rad, rad, p);
        // A lighter strip along the top gives each block a little depth.
        p.setColor(0x33FFFFFF);
        r.set(x + gap * 2, y + gap * 2, x + size - gap * 2, y + size * 0.35f);
        cv.drawRoundRect(r, rad, rad, p);
    }

    /** The playing field. */
    final class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        BoardView(Context c) {
            super(c);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (running) turn();
                }
            });
        }

        @Override
        protected void onDraw(Canvas cv) {
            float size = Math.min(getWidth() / (float) COLS, getHeight() / (float) ROWS);
            float left = (getWidth() - size * COLS) / 2f;
            float top = (getHeight() - size * ROWS) / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x66000000);
            rect.set(left, top, left + size * COLS, top + size * ROWS);
            cv.drawRoundRect(rect, size * 0.3f, size * 0.3f, paint);
            paint.setColor(0x14FFFFFF);
            paint.setStrokeWidth(1f);
            for (int c = 1; c < COLS; c++) {
                cv.drawLine(left + c * size, top, left + c * size, top + size * ROWS, paint);
            }
            for (int r = 1; r < ROWS; r++) {
                cv.drawLine(left, top + r * size, left + size * COLS, top + r * size, paint);
            }

            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] != 0) {
                        block(cv, paint, rect, left + c * size, top + r * size, size,
                                COLORS[board[r][c] - 1], false);
                    }
                }
            }
            if (cells != null && !over) {
                int gy = ghostY();
                for (int[] cell : cells) {
                    int y = gy + cell[1];
                    if (y >= 0 && gy != py) {
                        block(cv, paint, rect, left + (px + cell[0]) * size, top + y * size,
                                size, COLORS[kind], true);
                    }
                }
                for (int[] cell : cells) {
                    int y = py + cell[1];
                    if (y >= 0) {
                        block(cv, paint, rect, left + (px + cell[0]) * size, top + y * size,
                                size, COLORS[kind], false);
                    }
                }
            }
            if (paused || over) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x99000000);
                rect.set(left, top, left + size * COLS, top + size * ROWS);
                cv.drawRoundRect(rect, size * 0.3f, size * 0.3f, paint);
                paint.setColor(Ui.TEXT);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(size * 1.1f);
                cv.drawText(over ? "GAME OVER" : "PAUSED", left + size * COLS / 2f,
                        top + size * ROWS / 2f, paint);
            }
        }
    }

    /** Shows the shape that comes next. */
    final class NextView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        NextView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float size = Math.min(getWidth() / 4f, getHeight() / 3f);
            float offX = (getWidth() - BOX[nextKind] * size) / 2f;
            for (int[] cell : SHAPES[nextKind]) {
                block(cv, paint, rect, offX + cell[0] * size, size * 0.4f + cell[1] * size,
                        size, COLORS[nextKind], false);
            }
        }
    }
}
