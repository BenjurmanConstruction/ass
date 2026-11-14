package app.easywake;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The second batch of Brain gym tests (modes 8 and up). Shares its plumbing with GameActivity. */
public class GameActivity2 extends GameActivity {
    static final int VERBAL = 8;
    static final int VISUAL = 9;
    static final int PAIRS = 10;
    static final int GRID = 11;
    static final int ODD = 12;
    static final int GONOGO = 13;
    static final int ARROWS = 14;
    static final int NBACK = 15;
    static final int DOTS = 16;
    static final int TIME = 17;
    static final int TYPING = 18;
    static final int SCRAMBLE = 19;
    static final int CHAIN = 20;
    static final int TRIVIA = 21;
    static final int VOCAB = 22;
    static final int LOGIC = 23;

    @Override
    void startOther() {
        switch (mode) {
            case VERBAL:
                startVerbal();
                break;
            case VISUAL:
                startVisual();
                break;
            case PAIRS:
                startPairs();
                break;
            case GRID:
                startGrid();
                break;
            case ODD:
                startOdd();
                break;
            case GONOGO:
                startGoNoGo();
                break;
            case ARROWS:
                startArrows();
                break;
            case NBACK:
                startNBack();
                break;
            case DOTS:
                startDots();
                break;
            case TIME:
                startTime();
                break;
            case TYPING:
                startTyping();
                break;
            case SCRAMBLE:
                startScramble();
                break;
            case CHAIN:
                startChain();
                break;
            case TRIVIA:
                startQuiz(Bank2.trivia(), null, "seenTrivia");
                break;
            case VOCAB:
                startQuiz(Bank2.vocab(), "What does this word mean?", "seenVocab");
                break;
            case LOGIC:
                startQuiz(Bank2.logic(), null, "seenLogic");
                break;
            default:
                super.startOther();
                break;
        }
    }

    // ---------- shared: a row of big choice buttons ----------

    interface Choice {
        void pick(int index);
    }

    /** Adds buttons stacked one per row (good for longer text). */
    Button[] stackedChoices(LinearLayout col, int count, final Choice choice) {
        Button[] out = new Button[count];
        for (int i = 0; i < count; i++) {
            final int idx = i;
            Button bt = Ui.button(this, "", false);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choice.pick(idx);
                }
            });
            col.addView(bt, Ui.full(this, 8));
            out[i] = bt;
        }
        return out;
    }

    /** Adds two wide buttons side by side. */
    Button[] twoChoices(LinearLayout col, String left, String right, final Choice choice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button[] out = new Button[2];
        String[] labels = {left, right};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            Button bt = answerButton(labels[i], 20);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choice.pick(idx);
                }
            });
            row.addView(bt, cell(84));
            out[i] = bt;
        }
        col.addView(row, Ui.full(this, 0));
        return out;
    }

    // ---------- verbal memory ----------

    private final Set<String> verbalSeen = new HashSet<>();
    private List<String> verbalPool;
    private String verbalWord;
    private boolean verbalWasSeen;
    private int verbalScore;
    private int verbalLives;
    private TextView verbalInfo;
    private TextView verbalText;

    private void startVerbal() {
        verbalSeen.clear();
        verbalPool = new ArrayList<>();
        Collections.addAll(verbalPool, Bank.WORDS);
        Collections.shuffle(verbalPool, rnd);
        verbalScore = 0;
        verbalLives = 3;
        LinearLayout col = column();
        verbalInfo = note("");
        col.addView(verbalInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        verbalText = big("", 46);
        col.addView(verbalText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        twoChoices(col, "SEEN", "NEW", new Choice() {
            @Override
            public void pick(int index) {
                boolean saidSeen = index == 0;
                if (saidSeen == verbalWasSeen) verbalScore++;
                else verbalLives--;
                verbalSeen.add(verbalWord);
                if (verbalLives <= 0) {
                    end(verbalScore, verbalScore + " words", "Correct answers before losing "
                            + "three lives.", Math.max(2, Math.min(50, verbalScore)));
                } else {
                    verbalNext();
                }
            }
        });
        verbalNext();
    }

    private void verbalNext() {
        boolean repeat = !verbalSeen.isEmpty() && (verbalPool.isEmpty() || rnd.nextInt(100) < 45);
        if (repeat) {
            List<String> seen = new ArrayList<>(verbalSeen);
            String pick = seen.get(rnd.nextInt(seen.size()));
            // Avoid showing the same word twice in a row.
            if (pick.equals(verbalWord) && seen.size() > 1) {
                pick = seen.get((seen.indexOf(pick) + 1) % seen.size());
            }
            verbalWord = pick;
            verbalWasSeen = true;
        } else {
            verbalWord = verbalPool.remove(verbalPool.size() - 1);
            verbalWasSeen = false;
        }
        verbalText.setText(verbalWord);
        verbalInfo.setText("Score " + verbalScore + "   •   Lives " + verbalLives);
    }

    // ---------- visual memory ----------

    private int visLevel;
    private int visStrikes;
    private int visFound;
    private boolean visInput;
    private boolean[] visLit;
    private View[] visTiles;
    private TextView visInfo;
    private LinearLayout visGrid;

    private void startVisual() {
        visLevel = 1;
        visStrikes = 0;
        LinearLayout col = column();
        visInfo = note("");
        col.addView(visInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        visGrid = new LinearLayout(this);
        visGrid.setOrientation(LinearLayout.VERTICAL);
        col.addView(visGrid, Ui.full(this, 0));
        col.addView(spacer(), grow());
        visRound();
    }

    private void visRound() {
        visInput = false;
        visFound = 0;
        final int size = Math.min(6, 3 + (visLevel - 1) / 3);
        final int count = Math.min(size * size - 2, visLevel + 2);
        visGrid.removeAllViews();
        visTiles = new View[size * size];
        visLit = new boolean[size * size];
        List<Integer> spots = new ArrayList<>();
        for (int i = 0; i < size * size; i++) spots.add(i);
        Collections.shuffle(spots, rnd);
        for (int i = 0; i < count; i++) visLit[spots.get(i)] = true;
        int h = size <= 3 ? 96 : (size == 4 ? 76 : (size == 5 ? 62 : 52));
        for (int r = 0; r < size; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < size; c++) {
                final int idx = r * size + c;
                View tile = new View(this);
                tile.setBackground(visLit[idx] ? Ui.gold(this, 14) : Ui.card(this, 14));
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        visTap(idx, count);
                    }
                });
                row.addView(tile, cell(h));
                visTiles[idx] = tile;
            }
            visGrid.addView(row, Ui.full(this, 0));
        }
        visInfo.setText("Level " + visLevel + ". Memorize...");
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                for (View t : visTiles) t.setBackground(Ui.card(GameActivity2.this, 14));
                visInput = true;
                visInfo.setText("Level " + visLevel + "   •   Strikes " + visStrikes
                        + " of 3");
            }
        }, 900 + count * 130L);
    }

    private void visTap(int idx, int count) {
        if (!visInput) return;
        if (visLit[idx]) {
            visLit[idx] = false;
            visTiles[idx].setBackground(Ui.gold(this, 14));
            visFound++;
            if (visFound >= count) {
                visInput = false;
                visLevel++;
                visLater(450);
            }
        } else {
            visStrikes++;
            visTiles[idx].setBackground(Ui.round(this, 0xFF7A2E2E, 14));
            if (visStrikes >= 3) {
                visInput = false;
                int done = visLevel - 1;
                end(done, "Level " + done, "Levels cleared before three wrong taps.",
                        Math.max(2, done * 4));
            } else {
                visInfo.setText("Level " + visLevel + "   •   Strikes " + visStrikes
                        + " of 3");
            }
        }
    }

    private void visLater(int delay) {
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token) visRound();
            }
        }, delay);
    }

    // ---------- memory pairs ----------

    private static final String[] PAIR_SYMBOLS = {"★", "♥", "♦", "♣",
            "♠", "●", "▲", "■"};
    private final TextView[] pairCards = new TextView[16];
    private final int[] pairValue = new int[16];
    private final boolean[] pairDone = new boolean[16];
    private int pairFirst;
    private int pairMoves;
    private int pairMatched;
    private boolean pairLocked;
    private long pairStart;
    private TextView pairInfo;

    private void startPairs() {
        pairFirst = -1;
        pairMoves = 0;
        pairMatched = 0;
        pairLocked = false;
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            deck.add(i);
            deck.add(i);
        }
        Collections.shuffle(deck, rnd);
        LinearLayout col = column();
        pairInfo = note("Moves 0");
        col.addView(pairInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                final int idx = r * 4 + c;
                pairValue[idx] = deck.get(idx);
                pairDone[idx] = false;
                TextView card = Ui.text(this, "", 30, Ui.ON_ACCENT);
                card.setGravity(Gravity.CENTER);
                card.setBackground(Ui.card(this, 14));
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pairTap(idx);
                    }
                });
                row.addView(card, cell(78));
                pairCards[idx] = card;
            }
            col.addView(row, Ui.full(this, 0));
        }
        col.addView(spacer(), grow());
        pairStart = SystemClock.uptimeMillis();
    }

    private void pairShow(int idx, boolean up) {
        pairCards[idx].setText(up ? PAIR_SYMBOLS[pairValue[idx]] : "");
        pairCards[idx].setBackground(up ? Ui.gold(this, 14) : Ui.card(this, 14));
    }

    private void pairTap(final int idx) {
        if (pairLocked || pairDone[idx] || idx == pairFirst) return;
        pairShow(idx, true);
        if (pairFirst < 0) {
            pairFirst = idx;
            return;
        }
        pairMoves++;
        pairInfo.setText("Moves " + pairMoves);
        final int other = pairFirst;
        pairFirst = -1;
        if (pairValue[other] == pairValue[idx]) {
            pairDone[other] = true;
            pairDone[idx] = true;
            pairMatched++;
            if (pairMatched >= 8) {
                int s = (int) ((SystemClock.uptimeMillis() - pairStart) / 1000);
                end(-(pairMoves * 1000 + Math.min(s, 999)), pairMoves + " moves, " + s + " s",
                        "Eight is the perfect number of moves. Fewer moves ranks higher; "
                                + "time breaks ties.",
                        Math.max(5, Math.min(45, 45 - (pairMoves - 8) * 2)));
            }
        } else {
            pairLocked = true;
            final int mine = ++token;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mine != token) return;
                    pairShow(other, false);
                    pairShow(idx, false);
                    pairLocked = false;
                }
            }, 750);
        }
    }

    // ---------- number grid race (Schulte table) ----------

    private int gridNext;
    private long gridStart;
    private TextView gridInfo;

    private void startGrid() {
        gridNext = 1;
        List<Integer> nums = new ArrayList<>();
        for (int i = 1; i <= 25; i++) nums.add(i);
        Collections.shuffle(nums, rnd);
        LinearLayout col = column();
        gridInfo = note("Find 1");
        col.addView(gridInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        for (int r = 0; r < 5; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 5; c++) {
                final int value = nums.get(r * 5 + c);
                final TextView t = Ui.text(this, String.valueOf(value), 22, Ui.TEXT);
                t.setTypeface(Ui.medium());
                t.setGravity(Gravity.CENTER);
                t.setBackground(Ui.card(this, 12));
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (value != gridNext) return;
                        t.setTextColor(0x33FFFFFF);
                        gridNext++;
                        if (gridNext > 25) {
                            int ms = (int) (SystemClock.uptimeMillis() - gridStart);
                            end(-ms / 10, secs(ms), "Time to tap 1 through 25. Under 30 "
                                            + "seconds is solid; under 20 is excellent.",
                                    Math.max(5, Math.min(40, 55 - ms / 1000)));
                        } else {
                            gridInfo.setText("Find " + gridNext);
                        }
                    }
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                        Ui.dp(this, 62), 1f);
                int m = Ui.dp(this, 3);
                p.setMargins(m, m, m, m);
                row.addView(t, p);
            }
            col.addView(row, Ui.full(this, 0));
        }
        col.addView(spacer(), grow());
        gridStart = SystemClock.uptimeMillis();
    }

    // ---------- odd color out ----------

    private int oddScore;
    private long oddEndAt;
    private TextView oddInfo;
    private LinearLayout oddGrid;

    private void startOdd() {
        oddScore = 0;
        oddEndAt = SystemClock.uptimeMillis() + 30000;
        LinearLayout col = column();
        oddInfo = note("");
        col.addView(oddInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        oddGrid = new LinearLayout(this);
        oddGrid.setOrientation(LinearLayout.VERTICAL);
        col.addView(oddGrid, Ui.full(this, 0));
        col.addView(spacer(), grow());
        oddNext();
        final int mine = ++token;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                long left = oddEndAt - SystemClock.uptimeMillis();
                if (left <= 0) {
                    end(oddScore, oddScore + " found", "Odd tiles found in 30 seconds.",
                            oddScore * 2 + 2);
                    return;
                }
                oddInfo.setText((left / 1000 + 1) + " s left   •   " + oddScore + " found");
                handler.postDelayed(this, 200);
            }
        });
    }

    private void oddNext() {
        oddGrid.removeAllViews();
        int size = oddScore < 6 ? 4 : 5;
        float hue = rnd.nextInt(360);
        float sat = 0.45f + rnd.nextFloat() * 0.3f;
        float val = 0.55f + rnd.nextFloat() * 0.2f;
        // The gap between the odd tile and the rest shrinks as you score.
        float gap = Math.max(0.035f, 0.26f - oddScore * 0.018f);
        int base = Color.HSVToColor(new float[]{hue, sat, val});
        int odd = Color.HSVToColor(new float[]{hue, sat, Math.min(1f, val + gap)});
        final int oddIndex = rnd.nextInt(size * size);
        int h = size == 4 ? 78 : 62;
        for (int r = 0; r < size; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < size; c++) {
                final int idx = r * size + c;
                View tile = new View(this);
                tile.setBackground(Ui.round(this, idx == oddIndex ? odd : base, 12));
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (idx == oddIndex) {
                            oddScore++;
                            oddNext();
                        } else {
                            oddEndAt -= 2000;
                        }
                    }
                });
                row.addView(tile, cell(h));
            }
            oddGrid.addView(row, Ui.full(this, 0));
        }
    }

    // ---------- go / no-go ----------

    private int goTrial;
    private int goCorrect;
    private int goHits;
    private long goRtTotal;
    private long goShownAt;
    /** 0 = nothing showing, 1 = green, 2 = red. */
    private int goState;
    private boolean goAnswered;
    private View goCircle;
    private TextView goInfo;

    private void startGoNoGo() {
        goTrial = 0;
        goCorrect = 0;
        goHits = 0;
        goRtTotal = 0;
        goState = 0;
        LinearLayout col = column();
        goInfo = note("Get ready...");
        col.addView(goInfo, Ui.full(this, 0));
        FrameLayout area = new FrameLayout(this);
        area.setBackground(Ui.card(this, 24));
        goCircle = new View(this);
        int size = Ui.dp(this, 170);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(size, size);
        cp.gravity = Gravity.CENTER;
        area.addView(goCircle, cp);
        goCircle.setVisibility(View.INVISIBLE);
        area.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goTap();
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ap.topMargin = Ui.dp(this, 10);
        col.addView(area, ap);
        TextView hint = Ui.text(this, "Tap anywhere in the box for green. Leave red alone.",
                14, Ui.SUB);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, Ui.full(this, 10));
        goSchedule();
    }

    private void goSchedule() {
        goState = 0;
        goCircle.setVisibility(View.INVISIBLE);
        if (goTrial >= 25) {
            int avg = goHits > 0 ? (int) (goRtTotal / goHits) : 999;
            end(goCorrect * 10000 - Math.min(avg, 9999), goCorrect + "/25, " + avg + " ms",
                    "Correct responses, and average speed on green.",
                    goCorrect + (avg < 450 ? 8 : 0));
            return;
        }
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                goTrial++;
                goAnswered = false;
                boolean green = rnd.nextInt(100) < 70;
                goState = green ? 1 : 2;
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(green ? 0xFF5FD38A : 0xFFFF5A5A);
                goCircle.setBackground(g);
                goCircle.setVisibility(View.VISIBLE);
                goShownAt = SystemClock.uptimeMillis();
                goInfo.setText("Flash " + goTrial + " of 25");
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mine != token) return;
                        // Time's up for this flash: holding back on red was correct.
                        if (!goAnswered && goState == 2) goCorrect++;
                        goSchedule();
                    }
                }, 850);
            }
        }, 550 + rnd.nextInt(800));
    }

    private void goTap() {
        if (goState == 0 || goAnswered) return;
        goAnswered = true;
        if (goState == 1) {
            goCorrect++;
            goHits++;
            goRtTotal += SystemClock.uptimeMillis() - goShownAt;
        }
        goCircle.setVisibility(View.INVISIBLE);
    }

    // ---------- arrows (flanker) ----------

    private int arrowRound;
    private int arrowCorrect;
    private boolean arrowLeft;
    private long arrowStart;
    private TextView arrowText;
    private TextView arrowInfo;

    private void startArrows() {
        arrowRound = 0;
        arrowCorrect = 0;
        LinearLayout col = column();
        arrowInfo = note("");
        col.addView(arrowInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        arrowText = big("", 54);
        arrowText.setLetterSpacing(0.15f);
        col.addView(arrowText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        twoChoices(col, "◀  Left", "Right  ▶", new Choice() {
            @Override
            public void pick(int index) {
                if ((index == 0) == arrowLeft) arrowCorrect++;
                arrowRound++;
                if (arrowRound >= 20) {
                    int avg = (int) ((SystemClock.uptimeMillis() - arrowStart) / 20);
                    end(arrowCorrect * 100000 - Math.min(avg, 99999),
                            arrowCorrect + "/20, " + secs(avg),
                            "Correct answers, then average time per answer.",
                            arrowCorrect + (avg < 800 ? 10 : 0));
                } else {
                    arrowNext();
                }
            }
        });
        arrowStart = SystemClock.uptimeMillis();
        arrowNext();
    }

    private void arrowNext() {
        arrowLeft = rnd.nextBoolean();
        boolean same = rnd.nextBoolean();
        String mid = arrowLeft ? "<" : ">";
        String side = (arrowLeft == same) ? "<" : ">";
        arrowText.setText(side + side + mid + side + side);
        arrowInfo.setText("Round " + (arrowRound + 1) + " of 20");
    }

    // ---------- 2-back ----------

    private static final String NBACK_LETTERS = "BCDFGHKL";
    private final List<Character> nbackShown = new ArrayList<>();
    private int nbackScore;
    private boolean nbackPressed;
    private TextView nbackText;
    private TextView nbackInfo;
    private Button nbackBtn;

    private void startNBack() {
        nbackShown.clear();
        nbackScore = 0;
        LinearLayout col = column();
        nbackInfo = note("Get ready...");
        col.addView(nbackInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        nbackText = big("", 96);
        col.addView(nbackText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        nbackBtn = Ui.button(this, "MATCH", true);
        nbackBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        nbackBtn.setMinHeight(Ui.dp(this, 84));
        nbackBtn.setMinimumHeight(Ui.dp(this, 84));
        nbackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nbackPressed = true;
                nbackBtn.setAlpha(0.5f);
            }
        });
        col.addView(nbackBtn, Ui.full(this, 0));
        nbackStep();
    }

    private boolean nbackIsMatch() {
        int n = nbackShown.size();
        return n >= 3 && nbackShown.get(n - 1).equals(nbackShown.get(n - 3));
    }

    private void nbackStep() {
        // Score the letter that was just on screen.
        if (nbackShown.size() >= 3 && nbackPressed == nbackIsMatch()) nbackScore++;
        if (nbackShown.size() >= 25) {
            end(nbackScore, nbackScore + "/23", "Letters judged correctly. The first two "
                    + "can't be matches, so 23 count.", nbackScore * 2);
            return;
        }
        char next;
        int n = nbackShown.size();
        if (n >= 2 && rnd.nextInt(100) < 32) {
            next = nbackShown.get(n - 2);
        } else {
            next = NBACK_LETTERS.charAt(rnd.nextInt(NBACK_LETTERS.length()));
        }
        nbackShown.add(next);
        nbackPressed = false;
        nbackBtn.setAlpha(1f);
        nbackText.setText(String.valueOf(next));
        nbackInfo.setText("Letter " + nbackShown.size() + " of 25");
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token) nbackText.setText("");
            }
        }, 1700);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine == token) nbackStep();
            }
        }, 2100);
    }

    // ---------- dot count ----------

    private int dotRound;
    private int dotCorrect;
    private int dotAnswer;
    private long dotStart;
    private FrameLayout dotArena;
    private TextView dotInfo;
    private final Button[] dotBtns = new Button[4];
    private LinearLayout dotChoices;

    private void startDots() {
        dotRound = 0;
        dotCorrect = 0;
        LinearLayout col = column();
        dotInfo = note("");
        col.addView(dotInfo, Ui.full(this, 0));
        dotArena = new FrameLayout(this);
        dotArena.setBackground(Ui.card(this, 22));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ap.topMargin = Ui.dp(this, 10);
        col.addView(dotArena, ap);
        dotChoices = new LinearLayout(this);
        dotChoices.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button bt = answerButton("", 22);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object tag = dotBtns[idx].getTag();
                    if (tag instanceof Integer && (Integer) tag == dotAnswer) dotCorrect++;
                    dotRound++;
                    if (dotRound >= 10) {
                        int s = (int) ((SystemClock.uptimeMillis() - dotStart) / 1000);
                        end(dotCorrect * 1000 - Math.min(s, 999), dotCorrect + "/10",
                                "Rounds where you got the exact count.", dotCorrect * 3 + 2);
                    } else {
                        dotFlash();
                    }
                }
            });
            dotChoices.addView(bt, cell(70));
            dotBtns[i] = bt;
        }
        col.addView(dotChoices, Ui.full(this, 6));
        dotStart = SystemClock.uptimeMillis();
        final int mine = token;
        dotArena.post(new Runnable() {
            @Override
            public void run() {
                if (mine == token) dotFlash();
            }
        });
    }

    private void dotFlash() {
        dotChoices.setVisibility(View.INVISIBLE);
        dotArena.removeAllViews();
        dotAnswer = 4 + rnd.nextInt(5 + dotRound);
        int size = Ui.dp(this, 22);
        int w = Math.max(1, dotArena.getWidth() - size);
        int h = Math.max(1, dotArena.getHeight() - size);
        List<int[]> placed = new ArrayList<>();
        for (int i = 0; i < dotAnswer; i++) {
            int x = 0;
            int y = 0;
            // Try a few times to keep dots from overlapping.
            for (int attempt = 0; attempt < 30; attempt++) {
                x = rnd.nextInt(w);
                y = rnd.nextInt(h);
                boolean clash = false;
                for (int[] p : placed) {
                    if (Math.abs(p[0] - x) < size * 1.3f && Math.abs(p[1] - y) < size * 1.3f) {
                        clash = true;
                        break;
                    }
                }
                if (!clash) break;
            }
            placed.add(new int[]{x, y});
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Ui.ACCENT);
            dot.setBackground(g);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            lp.leftMargin = x;
            lp.topMargin = y;
            dotArena.addView(dot, lp);
        }
        dotInfo.setText("Round " + (dotRound + 1) + " of 10. Look!");
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                dotArena.removeAllViews();
                dotInfo.setText("How many dots?");
                fillOptions(dotBtns, dotAnswer, 3);
                dotChoices.setVisibility(View.VISIBLE);
            }
        }, 750);
    }

    // ---------- time sense ----------

    private int timeRound;
    private long timeErrTotal;
    private int timeTarget;
    private long timeStartAt;
    private boolean timeRunning;
    private TextView timeText;
    private TextView timeInfo;
    private Button timeBtn;

    private void startTime() {
        timeRound = 0;
        timeErrTotal = 0;
        timeRunning = false;
        LinearLayout col = column();
        timeInfo = note("");
        col.addView(timeInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        timeText = big("", 38);
        col.addView(timeText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        timeBtn = Ui.button(this, "Start", true);
        timeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        timeBtn.setMinHeight(Ui.dp(this, 90));
        timeBtn.setMinimumHeight(Ui.dp(this, 90));
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeTap();
            }
        });
        col.addView(timeBtn, Ui.full(this, 0));
        timeSetup();
    }

    private void timeSetup() {
        int[] targets = {5, 7, 10, 12, 15};
        timeTarget = targets[rnd.nextInt(targets.length)];
        timeText.setText("Target: " + timeTarget + " seconds");
        timeInfo.setText("Round " + (timeRound + 1) + " of 3");
        timeBtn.setText("Start");
    }

    private void timeTap() {
        if (!timeRunning) {
            timeRunning = true;
            timeStartAt = SystemClock.uptimeMillis();
            timeText.setText("Counting...\nTap Stop at " + timeTarget + " seconds");
            timeBtn.setText("Stop");
            return;
        }
        timeRunning = false;
        long elapsed = SystemClock.uptimeMillis() - timeStartAt;
        long err = Math.abs(elapsed - timeTarget * 1000L);
        timeErrTotal += err;
        timeRound++;
        if (timeRound >= 3) {
            int avg = (int) (timeErrTotal / 3);
            end(-avg, secs(avg) + " off", "Average distance from the target over three "
                    + "rounds. Your last stop was at " + secs((int) elapsed) + ".",
                    Math.max(2, Math.min(40, (3000 - avg) / 75)));
            return;
        }
        final String shown = "You stopped at " + secs((int) elapsed) + "\n("
                + secs((int) err) + " off)";
        timeText.setText(shown);
        timeBtn.setText("Next round");
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v2) {
                        timeTap();
                    }
                });
                timeSetup();
            }
        });
    }

    // ---------- typing speed ----------

    private long typeStartAt;

    private void startTyping() {
        typeStartAt = 0;
        final String target = Bank3.sentences()[Deck.next(this, "sentences", Bank3.sentences().length)];
        LinearLayout col = column();
        col.addView(note("Type this exactly:"), Ui.full(this, 0));
        TextView show = Ui.text(this, target, 20, Ui.TEXT);
        show.setBackground(Ui.card(this, 18));
        show.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        col.addView(show, Ui.full(this, 10));

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.SUB);
        input.setHint("Tap here and start typing");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        col.addView(input, Ui.full(this, 14));
        TextView tip = Ui.text(this, "The clock starts on your first letter and stops when "
                + "you've typed the whole sentence.", 13, Ui.SUB);
        col.addView(tip, Ui.full(this, 8));

        final int mine = token;
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (mine != token) return;
                String typed = e.toString();
                if (typeStartAt == 0 && typed.length() > 0) {
                    typeStartAt = SystemClock.uptimeMillis();
                }
                if (typed.length() >= target.length()) {
                    long ms = Math.max(1, SystemClock.uptimeMillis() - typeStartAt);
                    int right = 0;
                    for (int i = 0; i < target.length(); i++) {
                        if (typed.charAt(i) == target.charAt(i)) right++;
                    }
                    int accuracy = right * 100 / target.length();
                    double minutes = ms / 60000.0;
                    int wpm = (int) Math.round(target.length() / 5.0 / minutes);
                    int net = wpm * accuracy / 100;
                    end(net, net + " wpm, " + accuracy + "%", "Words per minute after "
                            + "mistakes. Raw speed was " + wpm + " wpm. Around 35 to 40 is "
                            + "typical on a phone.", Math.max(3, Math.min(45, net / 2)));
                }
            }
        });
    }

    // ---------- word scramble ----------

    private int scrRound;
    private int scrCorrect;
    private int scrRight;
    private long scrStart;
    private TextView scrText;
    private TextView scrInfo;
    private Button[] scrBtns;

    private void startScramble() {
        scrRound = 0;
        scrCorrect = 0;
        LinearLayout col = column();
        scrInfo = note("");
        col.addView(scrInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        scrText = big("", 40);
        scrText.setLetterSpacing(0.2f);
        col.addView(scrText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        scrBtns = stackedChoices(col, 4, new Choice() {
            @Override
            public void pick(int index) {
                if (index == scrRight) scrCorrect++;
                scrRound++;
                if (scrRound >= 10) {
                    int s = (int) ((SystemClock.uptimeMillis() - scrStart) / 1000);
                    end(scrCorrect * 1000 - Math.min(s, 999), scrCorrect + "/10 in " + s + " s",
                            "Correct answers first, then speed.", scrCorrect * 3 + 2);
                } else {
                    scrNext();
                }
            }
        });
        scrStart = SystemClock.uptimeMillis();
        scrNext();
    }

    private void scrNext() {
        String[] bank = Bank3.scramble();
        String word = bank[Deck.next(this, "scramble", bank.length)];
        List<Character> letters = new ArrayList<>();
        for (char ch : word.toCharArray()) letters.add(ch);
        String mixed = word;
        for (int attempt = 0; attempt < 20 && mixed.equals(word); attempt++) {
            Collections.shuffle(letters, rnd);
            StringBuilder sb = new StringBuilder();
            for (char ch : letters) sb.append(ch);
            mixed = sb.toString();
        }
        scrText.setText(mixed.toUpperCase());
        List<String> options = new ArrayList<>();
        options.add(word);
        int guard = 0;
        while (options.size() < 4 && guard++ < 300) {
            String other = bank[rnd.nextInt(bank.length)];
            // Same length makes the wrong answers believable.
            if (!options.contains(other) && (other.length() == word.length() || guard > 150)) {
                options.add(other);
            }
        }
        Collections.shuffle(options, rnd);
        scrRight = options.indexOf(word);
        for (int i = 0; i < 4; i++) {
            scrBtns[i].setText(i < options.size() ? options.get(i) : "");
        }
        scrInfo.setText("Word " + (scrRound + 1) + " of 10");
    }

    // ---------- math chain ----------

    private int chainRound;
    private int chainCorrect;
    private int chainAnswer;
    private TextView chainText;
    private TextView chainInfo;
    private final Button[] chainBtns = new Button[4];
    private LinearLayout chainChoices;

    private void startChain() {
        chainRound = 0;
        chainCorrect = 0;
        LinearLayout col = column();
        chainInfo = note("");
        col.addView(chainInfo, Ui.full(this, 0));
        col.addView(spacer(), grow());
        chainText = big("", 56);
        col.addView(chainText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        chainChoices = new LinearLayout(this);
        chainChoices.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button bt = answerButton("", 22);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object tag = chainBtns[idx].getTag();
                    if (tag instanceof Integer && (Integer) tag == chainAnswer) chainCorrect++;
                    chainRound++;
                    if (chainRound >= 6) {
                        end(chainCorrect, chainCorrect + "/6", "Chains where your final "
                                + "total was right.", chainCorrect * 6 + 2);
                    } else {
                        chainPlay();
                    }
                }
            });
            chainChoices.addView(bt, cell(74));
            chainBtns[i] = bt;
        }
        col.addView(chainChoices, Ui.full(this, 0));
        chainPlay();
    }

    private void chainPlay() {
        chainChoices.setVisibility(View.INVISIBLE);
        chainInfo.setText("Chain " + (chainRound + 1) + " of 6. Keep the total in your head.");
        final List<String> steps = new ArrayList<>();
        int total = 3 + rnd.nextInt(12);
        steps.add("Start: " + total);
        for (int i = 0; i < 4; i++) {
            int kind = rnd.nextInt(4);
            if (kind == 0 && total <= 25) {
                int m = 2 + rnd.nextInt(2);
                total *= m;
                steps.add("× " + m);
            } else if (kind == 1 && total > 6) {
                int sub = 2 + rnd.nextInt(Math.min(9, total - 2));
                total -= sub;
                steps.add("− " + sub);
            } else {
                int add = 2 + rnd.nextInt(11);
                total += add;
                steps.add("+ " + add);
            }
        }
        chainAnswer = total;
        final int mine = ++token;
        for (int i = 0; i < steps.size(); i++) {
            final String step = steps.get(i);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mine == token) chainText.setText(step);
                }
            }, 400 + i * 1500L);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                chainText.setText("= ?");
                fillOptions(chainBtns, chainAnswer, 6);
                chainChoices.setVisibility(View.VISIBLE);
            }
        }, 400 + steps.size() * 1500L);
    }

    // ---------- multiple-choice quizzes (trivia, word smarts, logic) ----------

    private List<String[]> quizSet;
    private int quizRound;
    private int quizCorrect;
    private int quizRight;
    private boolean quizLocked;
    private long quizStart;
    private TextView quizInfo;
    private TextView quizText;
    private Button[] quizBtns;

    private void startQuiz(String[][] bank, final String prompt, String seenKey) {
        // Draw from questions not seen yet; start over once the pool runs dry.
        List<String> seen = MarketData.list(this, seenKey, "");
        List<Integer> fresh = new ArrayList<>();
        for (int i = 0; i < bank.length; i++) {
            if (!seen.contains(String.valueOf(i))) fresh.add(i);
        }
        if (fresh.size() < 10) {
            seen.clear();
            fresh.clear();
            for (int i = 0; i < bank.length; i++) fresh.add(i);
        }
        Collections.shuffle(fresh, rnd);
        quizSet = new ArrayList<>();
        for (int i = 0; i < 10 && i < fresh.size(); i++) {
            quizSet.add(bank[fresh.get(i)]);
            seen.add(String.valueOf(fresh.get(i)));
        }
        MarketData.saveList(this, seenKey, seen);
        quizRound = 0;
        quizCorrect = 0;
        quizLocked = false;
        LinearLayout col = column();
        quizInfo = note("");
        col.addView(quizInfo, Ui.full(this, 0));
        if (prompt != null) {
            TextView p = Ui.text(this, prompt, 14, Ui.SUB);
            p.setGravity(Gravity.CENTER);
            col.addView(p, Ui.full(this, 6));
        }
        col.addView(spacer(), grow());
        quizText = big("", prompt != null ? 40 : 22);
        col.addView(quizText, Ui.full(this, 0));
        col.addView(spacer(), grow());
        quizBtns = stackedChoices(col, 4, new Choice() {
            @Override
            public void pick(int index) {
                quizPick(index);
            }
        });
        quizStart = SystemClock.uptimeMillis();
        quizNext();
    }

    private void quizNext() {
        quizLocked = false;
        String[] q = quizSet.get(quizRound);
        quizText.setText(q[0]);
        List<String> options = new ArrayList<>();
        for (int i = 1; i < q.length && i <= 4; i++) options.add(q[i]);
        Collections.shuffle(options, rnd);
        quizRight = options.indexOf(q[1]);
        for (int i = 0; i < 4; i++) {
            quizBtns[i].setText(i < options.size() ? options.get(i) : "");
            quizBtns[i].setBackground(Ui.ripple(this, Ui.card(this, 28), 28));
            quizBtns[i].setTextColor(Ui.TEXT);
        }
        quizInfo.setText("Question " + (quizRound + 1) + " of " + quizSet.size());
    }

    private void quizPick(int index) {
        if (quizLocked) return;
        quizLocked = true;
        boolean right = index == quizRight;
        if (right) quizCorrect++;
        // Show the right answer in green, and a wrong pick in red.
        quizBtns[quizRight].setBackground(Ui.round(this, 0xFF2E7D4F, 28));
        if (!right) quizBtns[index].setBackground(Ui.round(this, 0xFF7A2E2E, 28));
        final int mine = ++token;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine != token) return;
                quizRound++;
                if (quizRound >= quizSet.size()) {
                    int s = (int) ((SystemClock.uptimeMillis() - quizStart) / 1000);
                    end(quizCorrect * 1000 - Math.min(s, 999),
                            quizCorrect + "/" + quizSet.size() + " in " + s + " s",
                            "Correct answers first, then speed.", quizCorrect * 3 + 2);
                } else {
                    quizNext();
                }
            }
        }, right ? 650 : 1500);
    }
}
