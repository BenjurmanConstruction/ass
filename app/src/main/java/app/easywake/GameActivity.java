package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ScrollView;
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
    static final int SEQUENCE = 4;
    static final int CHIMP = 5;
    static final int SERIES = 6;
    static final int AIM = 7;

    static final String[] TITLES = {
            "Reaction time", "Speed math", "Number memory", "Color focus",
            "Sequence memory", "Chimp test", "Number patterns", "Aim trainer"
    };
    static final String[] HINTS = {
            "Tap the moment the panel turns green. Five tries.",
            "Solve as many sums as you can in 60 seconds.",
            "Memorize a number, then type it back. It gets longer each round.",
            "Tap the color the word is printed in, not what the word says. 20 rounds.",
            "Watch the tiles light up, then repeat the pattern. One more tile each level.",
            "Numbers appear, then hide when you tap 1. Tap the rest in order from memory.",
            "Work out what number comes next. Ten questions.",
            "Tap 20 targets as fast as you can."
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
        switch (mode) {
            case MATH:
                startMath();
                break;
            case DIGITS:
                startDigits();
                break;
            case STROOP:
                startStroop();
                break;
            case SEQUENCE:
                startSequence();
                break;
            case CHIMP:
                startChimp();
                break;
            case SERIES:
                startSeries();
                break;
            case AIM:
                startAim();
                break;
            default:
                startReaction();
                break;
        }
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

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 15, Ui.ACCENT);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private View spacer() {
        return new View(this);
    }

    private LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(1, 0, 1f);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && stage != null) {
            imm.hideSoftInputFromWindow(stage.getWindowToken(), 0);
        }
    }

    /**
     * Ends the test: awards points, offers a spot on the high score list, shows the board.
     *
     * @param rank   higher is better
     * @param result what goes on the board, for example "245 ms"
     */
    private void end(final int rank, final String result, String detail, int points) {
        token++;
        handler.removeCallbacksAndMessages(null);
        hideKeyboard();

        boolean newBest = Scores.isBest(this, mode, rank);
        final boolean makesBoard = Scores.qualifies(this, mode, rank);
        int earned = Math.max(1, points) + (newBest ? 15 : 0);
        Scores.addPoints(this, earned);

        stage.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        stage.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        col.addView(big(result, 40), Ui.full(this, 10));
        TextView d = Ui.text(this, detail, 15, Ui.SUB);
        d.setGravity(Gravity.CENTER);
        col.addView(d, Ui.full(this, 8));

        TextView pts = Ui.text(this, "+" + earned + " points" + (newBest ? "  (new #1 bonus!)"
                : "") + "   •   You have " + Scores.points(this), 15, Ui.ACCENT);
        pts.setTypeface(Ui.medium());
        pts.setGravity(Gravity.CENTER);
        col.addView(pts, Ui.full(this, 12));

        final LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);

        if (makesBoard) {
            final LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setBackground(Ui.card(this, 18));
            entry.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 14));
            entry.addView(Ui.text(this, "You made the top 10! Name for the board:", 15,
                    Ui.TEXT));
            final EditText name = new EditText(this);
            name.setSingleLine(true);
            name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            name.setTextColor(Ui.TEXT);
            name.setHintTextColor(Ui.SUB);
            name.setHint("Your name");
            String lastName = Prefs.getString(this, "playerName");
            if (lastName != null) name.setText(lastName);
            entry.addView(name, Ui.full(this, 4));
            Button save = Ui.button(this, "Save score", true);
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String n = name.getText().toString().trim();
                    if (n.length() > 16) n = n.substring(0, 16);
                    if (n.isEmpty()) n = "Player";
                    Prefs.putString(GameActivity.this, "playerName", n);
                    int pos = Scores.add(GameActivity.this, mode, n, rank, result);
                    hideKeyboard();
                    entry.setVisibility(View.GONE);
                    fillBoard(board, pos);
                }
            });
            entry.addView(save, Ui.full(this, 8));
            col.addView(entry, Ui.full(this, 16));
        }

        col.addView(Ui.heading(this, "High scores"), Ui.full(this, 22));
        col.addView(board, Ui.full(this, 8));
        fillBoard(board, -1);

        Button again = Ui.button(this, "Play again", true);
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        col.addView(again, Ui.full(this, 20));
        Button done = Ui.button(this, "Done", false);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        col.addView(done, Ui.full(this, 10));
    }

    private void fillBoard(LinearLayout board, int highlight) {
        board.removeAllViews();
        List<Scores.Entry> list = Scores.load(this, mode);
        if (list.isEmpty()) {
            board.addView(Ui.text(this, "No scores saved yet.", 14, Ui.SUB));
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Scores.Entry e = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = Ui.card(this, 14);
            if (i == highlight) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            row.setBackground(bg);
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            TextView place = Ui.text(this, (i + 1) + ".", 15, i == 0 ? Ui.ACCENT : Ui.SUB);
            place.setTypeface(Ui.medium());
            row.addView(place, new LinearLayout.LayoutParams(Ui.dp(this, 32),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(Ui.text(this, e.name, 16, Ui.TEXT), new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, e.text, 15, i == 0 ? Ui.ACCENT : Ui.TEXT));
            board.addView(row, Ui.full(this, 6));
        }
    }

    private Button answerButton(String label, float sp) {
        Button bt = Ui.button(this, label, false);
        bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return bt;
    }

    private LinearLayout.LayoutParams cell(int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, heightDp),
                1f);
        int m = Ui.dp(this, 5);
        p.setMargins(m, m, m, m);
        return p;
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
                end(-avg, avg + " ms", "Average of five tries. Most people land between 250 "
                        + "and 400 ms on a phone.", Math.max(5, Math.min(40, (600 - avg) / 10)));
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
        mathTimer = note("");
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
                Button bt = answerButton("", 26);
                bt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Object tag = mathBtns[idx].getTag();
                        if (tag instanceof Integer && (Integer) tag == mathAnswer) mathScore++;
                        mathNext();
                    }
                });
                row.addView(bt, cell(76));
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
                    end(mathScore, mathScore + " correct", "In 60 seconds.", 5 + mathScore);
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
        fillOptions(mathBtns, mathAnswer, kind == 2 ? 10 : 6);
    }

    /** Puts the right answer and three near-misses on four buttons, shuffled. */
    private void fillOptions(Button[] btns, int answer, int spread) {
        List<Integer> options = new ArrayList<>();
        options.add(answer);
        int guard = 0;
        while (options.size() < 4 && guard++ < 200) {
            int off = 1 + rnd.nextInt(Math.max(2, spread));
            int cand = answer + (rnd.nextBoolean() ? off : -off);
            if (!options.contains(cand)) options.add(cand);
        }
        while (options.size() < 4) options.add(answer + options.size() * 11);
        Collections.shuffle(options, rnd);
        for (int i = 0; i < 4; i++) {
            btns[i].setText(String.valueOf(options.get(i)));
            btns[i].setTag(options.get(i));
        }
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
        col.addView(note(digitLen + " digits. Memorize it."), Ui.full(this, 0));
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
                    end(reached, reached >= 3 ? reached + " digits" : "0 digits",
                            "The number was " + digitTarget + ". You typed "
                                    + (typed.isEmpty() ? "nothing" : typed)
                                    + ". Most adults hold about seven.",
                            Math.max(2, reached * 4));
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
        stroopCount = note("");
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
                Button bt = answerButton(COLOR_NAMES[idx], 20);
                bt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stroopPick(idx);
                    }
                });
                row.addView(bt, cell(76));
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
            end(stroopCorrect * 100000 - Math.min(avg, 99999),
                    stroopCorrect + "/20, " + secs(avg),
                    "Correct answers, and average time per answer.",
                    stroopCorrect + (avg < 1000 ? 10 : 0));
        } else {
            stroopNext();
        }
    }

    // ---------- sequence memory ----------

    private final View[] seqCells = new View[9];
    private final List<Integer> seq = new ArrayList<>();
    private int seqPos;
    private boolean seqInput;
    private TextView seqInfo;

    private void startSequence() {
        seq.clear();
        LinearLayout col = column();
        seqInfo = note("");
        col.addView(seqInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                final int idx = r * 3 + c;
                View tile = new View(this);
                tile.setBackground(Ui.card(this, 18));
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        seqTap(idx);
                    }
                });
                row.addView(tile, cell(100));
                seqCells[idx] = tile;
            }
            col.addView(row, Ui.full(this, 0));
        }
        col.addView(spacer(), grow());
        seqNextLevel();
    }

    private void seqLight(final int idx, int ms) {
        seqCells[idx].setBackground(Ui.gold(this, 18));
        final int mine = token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token) seqCells[idx].setBackground(Ui.card(GameActivity.this, 18));
            }
        }, ms);
    }

    private void seqNextLevel() {
        seqInput = false;
        seq.add(rnd.nextInt(9));
        seqInfo.setText("Level " + seq.size() + ". Watch...");
        final int mine = ++token;
        for (int k = 0; k < seq.size(); k++) {
            final int idx = seq.get(k);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mine == token) seqLight(idx, 380);
                }
            }, 700 + k * 620L);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                seqInput = true;
                seqPos = 0;
                seqInfo.setText("Level " + seq.size() + ". Your turn.");
            }
        }, 700 + seq.size() * 620L);
    }

    private void seqTap(int idx) {
        if (!seqInput) return;
        if (idx == seq.get(seqPos)) {
            seqLight(idx, 160);
            seqPos++;
            if (seqPos >= seq.size()) {
                seqInput = false;
                seqInfo.setText("Nice.");
                final int mine = token;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mine == token) seqNextLevel();
                    }
                }, 500);
            }
        } else {
            int done = seq.size() - 1;
            end(done, "Level " + done, "Longest pattern you repeated correctly.",
                    Math.max(2, done * 4));
        }
    }

    // ---------- chimp test ----------

    private static final int CHIMP_COLS = 5;
    private static final int CHIMP_ROWS = 6;
    private final TextView[] chimpCells = new TextView[CHIMP_COLS * CHIMP_ROWS];
    private final int[] chimpNumbers = new int[CHIMP_COLS * CHIMP_ROWS];
    private int chimpCount;
    private int chimpNext;
    private int chimpBest;
    private int chimpStrikes;
    private boolean chimpLocked;
    private TextView chimpInfo;

    private void startChimp() {
        chimpCount = 4;
        chimpBest = 0;
        chimpStrikes = 0;
        LinearLayout col = column();
        chimpInfo = note("");
        col.addView(chimpInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < CHIMP_ROWS; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < CHIMP_COLS; c++) {
                final int idx = r * CHIMP_COLS + c;
                TextView t = Ui.text(this, "", 22, Ui.ON_ACCENT);
                t.setTypeface(Ui.medium());
                t.setGravity(Gravity.CENTER);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chimpTap(idx);
                    }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                        Ui.dp(this, 58), 1f);
                int m = Ui.dp(this, 4);
                p.setMargins(m, m, m, m);
                row.addView(t, p);
                chimpCells[idx] = t;
            }
            col.addView(row, Ui.full(this, 0));
        }
        col.addView(spacer(), grow());
        chimpRound();
    }

    private void chimpRound() {
        chimpLocked = false;
        chimpNext = 1;
        List<Integer> spots = new ArrayList<>();
        for (int i = 0; i < chimpCells.length; i++) spots.add(i);
        Collections.shuffle(spots, rnd);
        for (int i = 0; i < chimpCells.length; i++) {
            chimpNumbers[i] = 0;
            chimpCells[i].setVisibility(View.INVISIBLE);
        }
        int count = Math.min(chimpCount, chimpCells.length);
        for (int n = 1; n <= count; n++) {
            int spot = spots.get(n - 1);
            chimpNumbers[spot] = n;
            chimpCells[spot].setText(String.valueOf(n));
            chimpCells[spot].setBackground(Ui.gold(this, 12));
            chimpCells[spot].setVisibility(View.VISIBLE);
        }
        chimpInfo.setText(chimpCount + " numbers   •   Strikes " + chimpStrikes + " of 3");
    }

    private void chimpTap(int idx) {
        if (chimpLocked) return;
        int n = chimpNumbers[idx];
        if (n == 0) return;
        if (n == chimpNext) {
            if (n == 1) {
                // From here on the numbers are hidden.
                for (int i = 0; i < chimpCells.length; i++) {
                    if (chimpNumbers[i] != 0) chimpCells[i].setText("");
                }
            }
            chimpNumbers[idx] = 0;
            chimpCells[idx].setVisibility(View.INVISIBLE);
            chimpNext++;
            if (chimpNext > Math.min(chimpCount, chimpCells.length)) {
                chimpBest = chimpCount;
                chimpCount++;
                chimpAgain(450);
            }
        } else {
            chimpStrikes++;
            // Show where everything was.
            for (int i = 0; i < chimpCells.length; i++) {
                if (chimpNumbers[i] != 0) {
                    chimpCells[i].setText(String.valueOf(chimpNumbers[i]));
                }
            }
            if (chimpStrikes >= 3) {
                chimpLocked = true;
                final int mine = ++token;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mine != token) return;
                        end(chimpBest, chimpBest + " numbers",
                                "Most numbers you cleared in order. Chimps famously beat "
                                        + "most people at this.", Math.max(2, chimpBest * 4));
                    }
                }, 1100);
            } else {
                chimpInfo.setText("Missed. Strike " + chimpStrikes + " of 3");
                chimpAgain(1100);
            }
        }
    }

    private void chimpAgain(int delay) {
        chimpLocked = true;
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token) chimpRound();
            }
        }, delay);
    }

    // ---------- number patterns ----------

    private int seriesRound;
    private int seriesCorrect;
    private int seriesAnswer;
    private long seriesStart;
    private TextView seriesInfo;
    private TextView seriesText;
    private final Button[] seriesBtns = new Button[4];

    private void startSeries() {
        seriesRound = 0;
        seriesCorrect = 0;
        LinearLayout col = column();
        seriesInfo = note("");
        col.addView(seriesInfo, Ui.full(this, 0));
        TextView hint = Ui.text(this, "What number comes next?", 14, Ui.SUB);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, Ui.full(this, 6));
        col.addView(spacer(), grow());
        seriesText = big("", 32);
        col.addView(seriesText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                Button bt = answerButton("", 24);
                bt.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Object tag = seriesBtns[idx].getTag();
                        if (tag instanceof Integer && (Integer) tag == seriesAnswer) {
                            seriesCorrect++;
                        }
                        seriesRound++;
                        if (seriesRound >= 10) {
                            int s = (int) ((SystemClock.uptimeMillis() - seriesStart) / 1000);
                            end(seriesCorrect * 1000 - Math.min(s, 999),
                                    seriesCorrect + "/10 in " + s + " s",
                                    "Correct answers first, then speed.",
                                    seriesCorrect * 3 + 2);
                        } else {
                            seriesNext();
                        }
                    }
                });
                row.addView(bt, cell(76));
                seriesBtns[idx] = bt;
            }
            col.addView(row, Ui.full(this, 0));
        }
        seriesStart = SystemClock.uptimeMillis();
        seriesNext();
    }

    private void seriesNext() {
        int[] t = new int[6];
        int kind = rnd.nextInt(6);
        int hard = seriesRound / 3;
        if (kind == 0) {
            // Add the same amount each time.
            int a = 1 + rnd.nextInt(20);
            int step = 2 + rnd.nextInt(7 + hard * 4);
            for (int i = 0; i < 6; i++) t[i] = a + i * step;
        } else if (kind == 1) {
            // Multiply each time.
            int a = 1 + rnd.nextInt(4);
            int r = 2 + rnd.nextInt(2);
            t[0] = a;
            for (int i = 1; i < 6; i++) t[i] = t[i - 1] * r;
        } else if (kind == 2) {
            // Squares, shifted.
            int shift = rnd.nextInt(6);
            int from = 1 + rnd.nextInt(3);
            for (int i = 0; i < 6; i++) t[i] = (from + i) * (from + i) + shift;
        } else if (kind == 3) {
            // The gap grows by one each time.
            int a = 1 + rnd.nextInt(10);
            int gap = 1 + rnd.nextInt(4);
            t[0] = a;
            for (int i = 1; i < 6; i++) t[i] = t[i - 1] + gap + (i - 1);
        } else if (kind == 4) {
            // Each number is the sum of the two before it.
            t[0] = 1 + rnd.nextInt(5);
            t[1] = 1 + rnd.nextInt(6);
            for (int i = 2; i < 6; i++) t[i] = t[i - 1] + t[i - 2];
        } else {
            // Two gaps taking turns.
            int a = 2 + rnd.nextInt(10);
            int g1 = 1 + rnd.nextInt(5);
            int g2 = 6 + rnd.nextInt(6);
            t[0] = a;
            for (int i = 1; i < 6; i++) t[i] = t[i - 1] + (i % 2 == 1 ? g1 : g2);
        }
        seriesAnswer = t[5];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(t[i]).append(",  ");
        sb.append("?");
        seriesText.setText(sb.toString());
        seriesInfo.setText("Question " + (seriesRound + 1) + " of 10");
        fillOptions(seriesBtns, seriesAnswer, Math.max(4, Math.abs(t[5] - t[4])));
    }

    // ---------- aim trainer ----------

    private int aimCount;
    private long aimStart;
    private FrameLayout aimArena;
    private View aimTarget;
    private TextView aimInfo;

    private void startAim() {
        aimCount = 0;
        LinearLayout col = column();
        aimInfo = note("Tap the target");
        col.addView(aimInfo, Ui.full(this, 0));
        aimArena = new FrameLayout(this);
        aimArena.setBackground(Ui.card(this, 22));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ap.topMargin = Ui.dp(this, 10);
        col.addView(aimArena, ap);

        aimTarget = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.ACCENT_HI, Ui.ACCENT_LO});
        g.setShape(GradientDrawable.OVAL);
        aimTarget.setBackground(g);
        aimTarget.setVisibility(View.INVISIBLE);
        aimTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aimCount++;
                if (aimCount >= 20) {
                    int avg = (int) ((SystemClock.uptimeMillis() - aimStart) / 20);
                    end(-avg, avg + " ms", "Average time per target over 20 targets.",
                            Math.max(5, Math.min(40, (900 - avg) / 15)));
                } else {
                    aimPlace();
                }
            }
        });
        int size = Ui.dp(this, 68);
        aimArena.addView(aimTarget, new FrameLayout.LayoutParams(size, size));
        final int mine = token;
        aimArena.post(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                aimStart = SystemClock.uptimeMillis();
                aimPlace();
            }
        });
    }

    private void aimPlace() {
        int size = Ui.dp(this, 68);
        int w = Math.max(1, aimArena.getWidth() - size);
        int h = Math.max(1, aimArena.getHeight() - size);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) aimTarget.getLayoutParams();
        lp.leftMargin = rnd.nextInt(w);
        lp.topMargin = rnd.nextInt(h);
        aimTarget.setLayoutParams(lp);
        aimTarget.setVisibility(View.VISIBLE);
        aimInfo.setText("Target " + (aimCount + 1) + " of 20");
    }
}
