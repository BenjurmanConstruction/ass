package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Runs one Brain gym test. Which one is chosen by the "mode" extra. */
public class GameActivity extends Activity {
    static final int REACTION = 0;
    static final int MATH = 1;
    static final int DIGITS = 2;
    static final int STROOP = 3;

    private static final String[] TITLES = {
            "Reaction time", "Speed math", "Number memory", "Color focus"
    };
    private static final String[] COLOR_NAMES = {"RED", "BLUE", "GREEN", "YELLOW"};
    private static final int[] COLOR_VALUES = {0xFFFF5A5A, 0xFF5AA0FF, 0xFF5FD38A, 0xFFFFD84D};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private FrameLayout stage;
    private int mode;
    /** Bumped whenever a round ends, so stale timers do nothing. */
    private int token;

    static String secs(int ms) {
        return String.format(Locale.US, "%.2f s", ms / 1000f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getIntExtra("mode", REACTION);
        if (mode < 0 || mode >= TITLES.length) mode = REACTION;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.topBar(this, TITLES[mode]), Ui.full(this, 0));

        stage = new FrameLayout(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = Ui.dp(this, 14);
        root.addView(stage, sp);

        setContentView(root);
        Bg.apply(this, root);
        start();
    }

    @Override
    protected void onDestroy() {
        token++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void start() {
        token++;
        handler.removeCallbacksAndMessages(null);
        if (mode == MATH) startMath();
        else if (mode == DIGITS) startDigits();
        else if (mode == STROOP) startStroop();
        else startReaction();
    }

    // ---------- shared pieces ----------

    private LinearLayout column() {
        stage.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return col;
    }

    private TextView big(String s, float sp) {
        TextView t = Ui.text(this, s, sp, Ui.TEXT);
        t.setTypeface(Ui.light());
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private View spacer() {
        return new View(this);
    }

    private LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(1, 0, 1f);
    }

    private void showResult(String headline, String detail, boolean newBest) {
        token++;
        handler.removeCallbacksAndMessages(null);
        hideKeyboard();
        LinearLayout col = column();
        col.addView(spacer(), grow());
        col.addView(big(headline, 40), Ui.full(this, 0));
        TextView d = Ui.text(this, detail, 16, Ui.SUB);
        d.setGravity(Gravity.CENTER);
        col.addView(d, Ui.full(this, 10));
        if (newBest) {
            TextView best = Ui.text(this, "New personal best!", 17, Ui.ACCENT);
            best.setTypeface(Ui.medium());
            best.setGravity(Gravity.CENTER);
            col.addView(best, Ui.full(this, 14));
        }
        col.addView(spacer(), grow());
        Button again = Ui.button(this, "Play again", true);
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        col.addView(again, Ui.full(this, 0));
        Button done = Ui.button(this, "Done", false);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        col.addView(done, Ui.full(this, 10));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && stage != null) {
            imm.hideSoftInputFromWindow(stage.getWindowToken(), 0);
        }
    }

    // ---------- reaction time ----------

    private int reactRound;
    private int reactTotal;
    private long reactGoAt;
    /** 0 = idle, 1 = waiting for green, 2 = green. */
    private int reactState;
    private TextView reactPanel;

    private void startReaction() {
        reactRound = 0;
        reactTotal = 0;
        reactState = 0;
        LinearLayout col = column();
        reactPanel = big("Tap to start", 30);
        reactPanel.setBackground(Ui.card(this, 28));
        reactPanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reactionTap();
            }
        });
        col.addView(reactPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = Ui.text(this, "Wait for green, then tap as fast as you can.", 14,
                Ui.SUB);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, Ui.full(this, 12));
    }

    private void reactionArm() {
        reactState = 1;
        reactPanel.setText("Wait for green...");
        reactPanel.setTextColor(Ui.TEXT);
        reactPanel.setBackground(Ui.round(this, 0xFF7A2E2E, 28));
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                reactState = 2;
                reactGoAt = SystemClock.uptimeMillis();
                reactPanel.setText("TAP!");
                reactPanel.setTextColor(0xFF08240F);
                reactPanel.setBackground(Ui.round(GameActivity.this, 0xFF5FD38A, 28));
            }
        }, 1500 + rnd.nextInt(3000));
    }

    private void reactionTap() {
        if (reactState == 0) {
            reactionArm();
        } else if (reactState == 1) {
            // Jumped the gun: this try doesn't count.
            token++;
            reactState = 0;
            reactPanel.setText("Too soon!\nTap to try again");
            reactPanel.setTextColor(Ui.TEXT);
            reactPanel.setBackground(Ui.card(this, 28));
        } else {
            int ms = (int) (SystemClock.uptimeMillis() - reactGoAt);
            reactTotal += ms;
            reactRound++;
            reactState = 0;
            if (reactRound >= 5) {
                int avg = reactTotal / 5;
                int best = Prefs.getInt(this, "bestReaction", 0);
                boolean isBest = best == 0 || avg < best;
                if (isBest) Prefs.putInt(this, "bestReaction", avg);
                showResult(avg + " ms", "Average of five tries. Most people land between "
                        + "250 and 400 ms on a phone.", isBest);
            } else {
                reactPanel.setText(ms + " ms\nTap for try " + (reactRound + 1) + " of 5");
                reactPanel.setTextColor(Ui.TEXT);
                reactPanel.setBackground(Ui.card(this, 28));
            }
        }
    }

    // ---------- speed math ----------

    private int mathScore;
    private int mathAnswer;
    private long mathEndAt;
    private TextView mathTimer;
    private TextView mathProblem;
    private final Button[] mathBtns = new Button[4];

    private void startMath() {
        mathScore = 0;
        mathEndAt = SystemClock.uptimeMillis() + 60000;
        LinearLayout col = column();
        mathTimer = Ui.text(this, "", 16, Ui.ACCENT);
        mathTimer.setGravity(Gravity.CENTER);
        col.addView(mathTimer, Ui.full(this, 0));
        col.addView(spacer(), grow());
        mathProblem = big("", 54);
        col.addView(mathProblem, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                Button bt = Ui.button(this, "", false);
                bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
                bt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mathPick(idx);
                    }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                        Ui.dp(this, 76), 1f);
                int m = Ui.dp(this, 5);
                p.setMargins(m, m, m, m);
                row.addView(bt, p);
                mathBtns[idx] = bt;
            }
            col.addView(row, Ui.full(this, 0));
        }
        mathNext();
        final int mine = ++token;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                long left = mathEndAt - SystemClock.uptimeMillis();
                if (left <= 0) {
                    int best = Prefs.getInt(GameActivity.this, "bestMath", -1);
                    boolean isBest = mathScore > best;
                    if (isBest) Prefs.putInt(GameActivity.this, "bestMath", mathScore);
                    showResult(mathScore + " correct", "In 60 seconds.", isBest);
                    return;
                }
                mathTimer.setText((left / 1000 + 1) + " s left   •   " + mathScore
                        + " correct");
                handler.postDelayed(this, 200);
            }
        });
    }

    private void mathNext() {
        int level = Math.min(4, mathScore / 5);
        int kind = rnd.nextInt(3);
        int a;
        int b2;
        String text;
        if (kind == 0) {
            a = 3 + rnd.nextInt(12 + level * 15);
            b2 = 2 + rnd.nextInt(12 + level * 15);
            mathAnswer = a + b2;
            text = a + " + " + b2;
        } else if (kind == 1) {
            a = 8 + rnd.nextInt(14 + level * 15);
            b2 = 2 + rnd.nextInt(Math.max(2, a - 3));
            mathAnswer = a - b2;
            text = a + " − " + b2;
        } else {
            a = 2 + rnd.nextInt(8 + level);
            b2 = 2 + rnd.nextInt(8 + level);
            mathAnswer = a * b2;
            text = a + " × " + b2;
        }
        mathProblem.setText(text);

        List<Integer> options = new ArrayList<>();
        options.add(mathAnswer);
        int guard = 0;
        while (options.size() < 4 && guard++ < 100) {
            int off = 1 + rnd.nextInt(kind == 2 ? 10 : 6);
            int cand = mathAnswer + (rnd.nextBoolean() ? off : -off);
            if (cand >= 0 && !options.contains(cand)) options.add(cand);
        }
        while (options.size() < 4) options.add(mathAnswer + options.size() * 11);
        Collections.shuffle(options, rnd);
        for (int i = 0; i < 4; i++) {
            mathBtns[i].setText(String.valueOf(options.get(i)));
            mathBtns[i].setTag(options.get(i));
        }
    }

    private void mathPick(int idx) {
        Object tag = mathBtns[idx].getTag();
        if (tag instanceof Integer && (Integer) tag == mathAnswer) mathScore++;
        mathNext();
    }

    // ---------- number memory ----------

    private int digitLen;
    private String digitTarget;

    private void startDigits() {
        digitLen = 3;
        digitsShow();
    }

    private void digitsShow() {
        hideKeyboard();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digitLen; i++) {
            sb.append(i == 0 ? 1 + rnd.nextInt(9) : rnd.nextInt(10));
        }
        digitTarget = sb.toString();

        LinearLayout col = column();
        TextView level = Ui.text(this, digitLen + " digits. Memorize it.", 16, Ui.ACCENT);
        level.setGravity(Gravity.CENTER);
        col.addView(level, Ui.full(this, 0));
        col.addView(spacer(), grow());
        TextView num = big(digitTarget, digitLen > 9 ? 34 : 46);
        num.setLetterSpacing(0.12f);
        col.addView(num, Ui.full(this, 0));
        col.addView(spacer(), grow());

        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token) digitsAsk();
            }
        }, 1200 + digitLen * 600L);
    }

    private void digitsAsk() {
        LinearLayout col = column();
        TextView q = Ui.text(this, "What was the number?", 18, Ui.TEXT);
        q.setGravity(Gravity.CENTER);
        col.addView(q, Ui.full(this, 20));

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        input.setTextColor(Ui.TEXT);
        col.addView(input, Ui.full(this, 16));

        Button check = Ui.button(this, "Check", true);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String typed = input.getText().toString().trim();
                if (typed.equals(digitTarget)) {
                    digitLen++;
                    digitsShow();
                } else {
                    int reached = digitLen - 1;
                    int best = Prefs.getInt(GameActivity.this, "bestDigits", 0);
                    boolean isBest = reached > best && reached >= 3;
                    if (isBest) Prefs.putInt(GameActivity.this, "bestDigits", reached);
                    showResult(reached >= 3 ? reached + " digits" : "Missed it",
                            "The number was " + digitTarget + ". You typed "
                                    + (typed.isEmpty() ? "nothing" : typed)
                                    + ". Most adults hold about seven.", isBest);
                }
            }
        });
        col.addView(check, Ui.full(this, 16));

        input.requestFocus();
        final int mine = token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                InputMethodManager imm = (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    // ---------- color focus (Stroop) ----------

    private int stroopRound;
    private int stroopCorrect;
    private int stroopInk;
    private long stroopStart;
    private TextView stroopWord;
    private TextView stroopCount;

    private void startStroop() {
        stroopRound = 0;
        stroopCorrect = 0;
        LinearLayout col = column();
        stroopCount = Ui.text(this, "", 16, Ui.ACCENT);
        stroopCount.setGravity(Gravity.CENTER);
        col.addView(stroopCount, Ui.full(this, 0));
        TextView hint = Ui.text(this, "Tap the color of the ink, not the word.", 14, Ui.SUB);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, Ui.full(this, 6));
        col.addView(spacer(), grow());
        stroopWord = big("", 58);
        stroopWord.setTypeface(Ui.medium());
        col.addView(stroopWord, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                Button bt = Ui.button(this, COLOR_NAMES[idx], false);
                bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                bt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stroopPick(idx);
                    }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                        Ui.dp(this, 76), 1f);
                int m = Ui.dp(this, 5);
                p.setMargins(m, m, m, m);
                row.addView(bt, p);
            }
            col.addView(row, Ui.full(this, 0));
        }
        stroopStart = SystemClock.uptimeMillis();
        stroopNext();
    }

    private void stroopNext() {
        int word = rnd.nextInt(4);
        // Mostly mismatched, which is what makes it hard.
        stroopInk = rnd.nextInt(10) < 7 ? (word + 1 + rnd.nextInt(3)) % 4 : word;
        stroopWord.setText(COLOR_NAMES[word]);
        stroopWord.setTextColor(COLOR_VALUES[stroopInk]);
        stroopCount.setText("Round " + (stroopRound + 1) + " of 20");
    }

    private void stroopPick(int idx) {
        if (idx == stroopInk) stroopCorrect++;
        stroopRound++;
        if (stroopRound >= 20) {
            int avg = (int) ((SystemClock.uptimeMillis() - stroopStart) / 20);
            int best = Prefs.getInt(this, "bestStroop", -1);
            int bestMs = Prefs.getInt(this, "bestStroopMs", 0);
            boolean isBest = stroopCorrect > best
                    || (stroopCorrect == best && (bestMs == 0 || avg < bestMs));
            if (isBest) {
                Prefs.putInt(this, "bestStroop", stroopCorrect);
                Prefs.putInt(this, "bestStroopMs", avg);
            }
            showResult(stroopCorrect + " of 20", secs(avg) + " per answer.", isBest);
        } else {
            stroopNext();
        }
    }
}
