package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sudoku. Every puzzle is made fresh on the phone and has exactly one solution. Your
 * place is saved, so you can leave and come back. Solving one earns Knowledge Points.
 */
public class SudokuActivity extends Activity {
    private static final String[] LEVELS = {"Easy", "Medium", "Hard"};
    /** How many squares start filled in at each level. */
    private static final int[] GIVENS = {40, 32, 26};
    private static final int[] REWARD = {10, 20, 35};
    /** Most Knowledge Points Sudoku can pay in one day. */
    private static final int DAILY_KP = 70;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final int[] solution = new int[81];
    private final int[] given = new int[81];
    private final int[] entry = new int[81];
    /** Pencil marks: bit n set means the small number n is written in the square. */
    private final int[] notes = new int[81];
    private int level;
    private int selected = -1;
    private boolean noteMode;
    private boolean playing;
    private int mistakes;
    private int hints;
    private long elapsedMs;
    private long resumedAt;
    private int token;

    private LinearLayout root;
    private GridView grid;
    private TextView info;
    private Button noteBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);
        setContentView(root);
        Bg.apply(this, root);
        if (load()) {
            showBoard();
        } else {
            showMenu();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playing) {
            elapsedMs += SystemClock.uptimeMillis() - resumedAt;
            resumedAt = SystemClock.uptimeMillis();
            save();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedAt = SystemClock.uptimeMillis();
    }

    @Override
    protected void onDestroy() {
        token++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- menu ----------

    private void showMenu() {
        playing = false;
        token++;
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Sudoku"), Ui.full(this, 0));
        root.addView(Ui.text(this, "Fill the grid so every row, every column and every "
                + "3 by 3 box holds the numbers 1 to 9 once each. Every puzzle here has "
                + "exactly one answer.", 14, Ui.SUB),
                Ui.full(this, 14));
        for (int i = 0; i < LEVELS.length; i++) {
            final int lvl = i;
            int done = Stats.get(this, "sudoku" + i);
            Button bt = Ui.button(this, LEVELS[i] + "  •  " + REWARD[i]
                    + " Knowledge Points\nAbout " + GIVENS[i] + " squares filled in"
                    + (done > 0 ? "  •  solved " + done : ""), i == 0);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    make(lvl);
                }
            });
            root.addView(bt, Ui.full(this, 12));
        }
        root.addView(Ui.text(this, "Each hint you take lowers the reward by 2. Sudoku pays "
                + "up to " + DAILY_KP + " Knowledge Points a day.", 12, Ui.SUB),
                Ui.full(this, 14));
    }

    private void make(final int lvl) {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Sudoku"), Ui.full(this, 0));
        TextView wait = Ui.text(this, "Making a " + LEVELS[lvl].toLowerCase(Locale.US)
                + " puzzle...", 18, Ui.TEXT);
        wait.setGravity(Gravity.CENTER);
        root.addView(wait, Ui.full(this, 60));
        final int mine = ++token;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] full = new int[81];
                fill(full, 0);
                final int[] puzzle = dig(full, GIVENS[lvl]);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mine != token || isFinishing()) return;
                        level = lvl;
                        for (int i = 0; i < 81; i++) {
                            solution[i] = full[i];
                            given[i] = puzzle[i];
                            entry[i] = puzzle[i];
                            notes[i] = 0;
                        }
                        mistakes = 0;
                        hints = 0;
                        elapsedMs = 0;
                        selected = -1;
                        resumedAt = SystemClock.uptimeMillis();
                        save();
                        showBoard();
                    }
                });
            }
        }).start();
    }

    // ---------- making puzzles ----------

    private static boolean fits(int[] g, int cell, int n) {
        int r = cell / 9;
        int c = cell % 9;
        for (int i = 0; i < 9; i++) {
            if (g[r * 9 + i] == n || g[i * 9 + c] == n) return false;
        }
        int br = r / 3 * 3;
        int bc = c / 3 * 3;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (g[(br + i) * 9 + bc + j] == n) return false;
            }
        }
        return true;
    }

    /** Fills an empty grid with a complete, valid, random solution. */
    private boolean fill(int[] g, int cell) {
        if (cell == 81) return true;
        List<Integer> order = new ArrayList<>();
        for (int n = 1; n <= 9; n++) order.add(n);
        Collections.shuffle(order, rnd);
        for (int n : order) {
            if (fits(g, cell, n)) {
                g[cell] = n;
                if (fill(g, cell + 1)) return true;
                g[cell] = 0;
            }
        }
        return false;
    }

    /** Counts solutions, stopping at two, always working on the most constrained square. */
    private static int count(int[] g, int limit) {
        int best = -1;
        int bestOptions = 10;
        for (int i = 0; i < 81; i++) {
            if (g[i] != 0) continue;
            int options = 0;
            for (int n = 1; n <= 9; n++) {
                if (fits(g, i, n)) options++;
            }
            if (options < bestOptions) {
                bestOptions = options;
                best = i;
                if (options <= 1) break;
            }
        }
        if (best < 0) return 1;
        if (bestOptions == 0) return 0;
        int total = 0;
        for (int n = 1; n <= 9 && total < limit; n++) {
            if (fits(g, best, n)) {
                g[best] = n;
                total += count(g, limit - total);
                g[best] = 0;
            }
        }
        return total;
    }

    /** Removes numbers from a full grid, never leaving more than one way to solve it. */
    private int[] dig(int[] full, int keep) {
        int[] g = full.clone();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 81; i++) order.add(i);
        Collections.shuffle(order, rnd);
        int left = 81;
        for (int cell : order) {
            if (left <= keep) break;
            int was = g[cell];
            g[cell] = 0;
            if (count(g.clone(), 2) == 1) {
                left--;
            } else {
                g[cell] = was;
            }
        }
        return g;
    }

    // ---------- saving ----------

    private static String pack(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static boolean unpack(String s, int[] into) {
        if (s == null) return false;
        String[] parts = s.split(",");
        if (parts.length != into.length) return false;
        try {
            for (int i = 0; i < parts.length; i++) into[i] = Integer.parseInt(parts[i]);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void save() {
        Prefs.putString(this, "sdkSolution", pack(solution));
        Prefs.putString(this, "sdkGiven", pack(given));
        Prefs.putString(this, "sdkEntry", pack(entry));
        Prefs.putString(this, "sdkNotes", pack(notes));
        Prefs.putString(this, "sdkMeta", pack(new int[]{level, mistakes, hints,
                (int) (elapsedMs / 1000)}));
    }

    private boolean load() {
        int[] meta = new int[4];
        if (!unpack(Prefs.getString(this, "sdkMeta"), meta)) return false;
        if (!unpack(Prefs.getString(this, "sdkSolution"), solution)) return false;
        if (!unpack(Prefs.getString(this, "sdkGiven"), given)) return false;
        if (!unpack(Prefs.getString(this, "sdkEntry"), entry)) return false;
        unpack(Prefs.getString(this, "sdkNotes"), notes);
        level = Math.max(0, Math.min(2, meta[0]));
        mistakes = meta[1];
        hints = meta[2];
        elapsedMs = meta[3] * 1000L;
        return true;
    }

    private void forget() {
        Prefs.putString(this, "sdkMeta", "");
    }

    // ---------- playing ----------

    private void showBoard() {
        playing = true;
        resumedAt = SystemClock.uptimeMillis();
        root.removeAllViews();
        LinearLayout bar = Ui.topBar(this, "Sudoku");
        Button fresh = Ui.button(this, "New", false);
        fresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        fresh.setPadding(0, 0, 0, 0);
        fresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SudokuActivity.this)
                        .setTitle("Give up this puzzle?")
                        .setMessage("You'll go back to the menu and this puzzle is lost.")
                        .setPositiveButton("Give up", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                forget();
                                showMenu();
                            }
                        })
                        .setNegativeButton("Keep playing", null)
                        .show();
            }
        });
        bar.addView(fresh, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 40)));
        root.addView(bar, Ui.full(this, 0));

        info = Ui.text(this, "", 14, Ui.ACCENT);
        root.addView(info, Ui.full(this, 8));

        grid = new GridView(this);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gp.topMargin = Ui.dp(this, 8);
        root.addView(grid, gp);

        // Number pad.
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.HORIZONTAL);
        for (int n = 1; n <= 9; n++) {
            final int num = n;
            Button bt = Ui.button(this, String.valueOf(n), false);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    put(num);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 54), 1f);
            int m = Ui.dp(this, 2);
            lp.setMargins(m, 0, m, 0);
            pad.addView(bt, lp);
        }
        root.addView(pad, Ui.full(this, 10));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button erase = Ui.button(this, "Erase", false);
        erase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                put(0);
            }
        });
        noteBtn = Ui.button(this, "Notes: off", false);
        noteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                noteMode = !noteMode;
                noteBtn.setText(noteMode ? "Notes: on" : "Notes: off");
            }
        });
        Button hint = Ui.button(this, "Hint", false);
        hint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hint();
            }
        });
        for (Button bt : new Button[]{erase, noteBtn, hint}) {
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            bt.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 50), 1f);
            int m = Ui.dp(this, 3);
            lp.setMargins(m, 0, m, 0);
            tools.addView(bt, lp);
        }
        root.addView(tools, Ui.full(this, 8));

        final int mine = ++token;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mine != token || !playing) return;
                long ms = elapsedMs + SystemClock.uptimeMillis() - resumedAt;
                info.setText(LEVELS[level] + "   •   " + clock(ms) + "   •   "
                        + mistakes + (mistakes == 1 ? " mistake" : " mistakes")
                        + (hints > 0 ? "   •   " + hints + (hints == 1 ? " hint"
                        : " hints") : ""));
                handler.postDelayed(this, 500);
            }
        });
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    /** Writes a number (or 0 to erase) into the chosen square. */
    private void put(int n) {
        if (!playing || selected < 0 || given[selected] != 0) return;
        if (n == 0) {
            entry[selected] = 0;
            notes[selected] = 0;
        } else if (noteMode) {
            if (entry[selected] == 0) notes[selected] ^= 1 << n;
        } else {
            if (entry[selected] == n) return;
            entry[selected] = n;
            notes[selected] = 0;
            if (n != solution[selected]) {
                mistakes++;
                grid.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            } else {
                clearNotesFor(selected, n);
            }
        }
        grid.invalidate();
        save();
        checkDone();
    }

    /** A right answer rubs that number out of the notes in its row, column and box. */
    private void clearNotesFor(int cell, int n) {
        int r = cell / 9;
        int c = cell % 9;
        for (int i = 0; i < 81; i++) {
            int rr = i / 9;
            int cc = i % 9;
            if (rr == r || cc == c || (rr / 3 == r / 3 && cc / 3 == c / 3)) {
                notes[i] &= ~(1 << n);
            }
        }
    }

    private void hint() {
        if (!playing) return;
        int cell = selected;
        if (cell < 0 || entry[cell] == solution[cell]) {
            // No useful square chosen: pick an empty or wrong one.
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < 81; i++) {
                if (entry[i] != solution[i]) open.add(i);
            }
            if (open.isEmpty()) return;
            cell = open.get(rnd.nextInt(open.size()));
        }
        entry[cell] = solution[cell];
        notes[cell] = 0;
        clearNotesFor(cell, solution[cell]);
        selected = cell;
        hints++;
        grid.invalidate();
        save();
        checkDone();
    }

    private void checkDone() {
        for (int i = 0; i < 81; i++) {
            if (entry[i] != solution[i]) return;
        }
        playing = false;
        token++;
        long ms = elapsedMs + SystemClock.uptimeMillis() - resumedAt;
        forget();

        int earnedToday = Prefs.getInt(this, "sdkKpDay", 0) == Progress.today()
                ? Prefs.getInt(this, "sdkKp", 0) : 0;
        int want = Math.max(3, REWARD[level] - hints * 2);
        int kp = Math.max(0, Math.min(want, DAILY_KP - earnedToday));
        if (kp > 0) {
            Scores.addPoints(this, kp);
            Prefs.putInt(this, "sdkKpDay", Progress.today());
            Prefs.putInt(this, "sdkKp", earnedToday + kp);
        }
        Stats.add(this, "sudoku", 1);
        Stats.add(this, "sudoku" + level, 1);
        Stats.addDay(this, Stats.BRAIN, 1);
        Stats.addDay(this, Stats.POINTS, kp);
        Stats.log(this, "Sudoku, " + LEVELS[level].toLowerCase(Locale.US) + ": solved in "
                + clock(ms) + ", +" + kp + " points");
        int best = Prefs.getInt(this, "sdkBest" + level, 0);
        boolean record = hints == 0 && (best == 0 || ms / 1000 < best);
        if (record) Prefs.putInt(this, "sdkBest" + level, (int) (ms / 1000));
        Sfx.play(this, Sfx.BIG_WIN);

        new AlertDialog.Builder(this)
                .setTitle("Solved!")
                .setMessage(LEVELS[level] + " puzzle in " + clock(ms) + "\n" + mistakes
                        + (mistakes == 1 ? " mistake, " : " mistakes, ") + hints
                        + (hints == 1 ? " hint" : " hints")
                        + (record ? "\n\nYour fastest " + LEVELS[level].toLowerCase(Locale.US)
                        + " solve without hints." : "")
                        + (kp > 0 ? "\n\n+" + kp + " Knowledge Points"
                        : "\n\nYou've earned today's " + DAILY_KP + " Knowledge Points "
                        + "from Sudoku. More tomorrow."))
                .setCancelable(false)
                .setPositiveButton("Another", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showMenu();
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

    /** The 9 by 9 board. */
    final class GridView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GridView(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
        }

        private float size() {
            return Math.min(getWidth(), getHeight());
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
            float s = size();
            float left = (getWidth() - s) / 2f;
            float top = (getHeight() - s) / 2f;
            int c = (int) ((e.getX() - left) / (s / 9f));
            int r = (int) ((e.getY() - top) / (s / 9f));
            if (c >= 0 && c < 9 && r >= 0 && r < 9 && e.getX() >= left && e.getY() >= top) {
                selected = r * 9 + c;
                invalidate();
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float s = size();
            float cell = s / 9f;
            float left = (getWidth() - s) / 2f;
            float top = (getHeight() - s) / 2f;
            float d = getResources().getDisplayMetrics().density;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99000000);
            cv.drawRect(left, top, left + s, top + s, paint);

            int selValue = selected >= 0 ? entry[selected] : 0;
            for (int i = 0; i < 81; i++) {
                int r = i / 9;
                int c = i % 9;
                float x = left + c * cell;
                float y = top + r * cell;
                boolean sameLine = selected >= 0 && (r == selected / 9 || c == selected % 9
                        || (r / 3 == selected / 9 / 3 && c / 3 == selected % 9 / 3));
                if (i == selected) {
                    paint.setColor((Ui.ACCENT & 0x00FFFFFF) | 0x66000000);
                    cv.drawRect(x, y, x + cell, y + cell, paint);
                } else if (selValue != 0 && entry[i] == selValue) {
                    paint.setColor((Ui.ACCENT & 0x00FFFFFF) | 0x33000000);
                    cv.drawRect(x, y, x + cell, y + cell, paint);
                } else if (sameLine) {
                    paint.setColor(0x14FFFFFF);
                    cv.drawRect(x, y, x + cell, y + cell, paint);
                }
                if (entry[i] != 0) {
                    boolean wrong = entry[i] != solution[i];
                    paint.setColor(wrong ? 0xFFEF5350 : (given[i] != 0 ? Ui.TEXT : Ui.ACCENT_HI));
                    paint.setFakeBoldText(given[i] != 0);
                    paint.setTextSize(cell * 0.6f);
                    cv.drawText(String.valueOf(entry[i]), x + cell / 2f,
                            y + cell / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
                    paint.setFakeBoldText(false);
                } else if (notes[i] != 0) {
                    paint.setColor(Ui.SUB);
                    paint.setTextSize(cell * 0.26f);
                    for (int n = 1; n <= 9; n++) {
                        if ((notes[i] & (1 << n)) == 0) continue;
                        float nx = x + cell * (0.2f + 0.3f * ((n - 1) % 3));
                        float ny = y + cell * (0.2f + 0.3f * ((n - 1) / 3));
                        cv.drawText(String.valueOf(n), nx,
                                ny - (paint.ascent() + paint.descent()) / 2f, paint);
                    }
                }
            }

            // Grid lines: thin between squares, thick between boxes.
            for (int i = 0; i <= 9; i++) {
                boolean thick = i % 3 == 0;
                paint.setColor(thick ? Ui.ACCENT : 0x44FFFFFF);
                paint.setStrokeWidth((thick ? 2.2f : 1f) * d);
                cv.drawLine(left + i * cell, top, left + i * cell, top + s, paint);
                cv.drawLine(left, top + i * cell, left + s, top + i * cell, paint);
            }
        }
    }
}
