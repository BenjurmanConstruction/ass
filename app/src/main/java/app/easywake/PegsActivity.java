package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Peg Pop: aim a ball from the top of the board and let it bounce down through the pegs.
 * Clear every gold peg before you run out of balls. Unlocked on the skill tree.
 */
public class PegsActivity extends Activity {
    private static final int BALLS = 10;
    /** Most Knowledge Points this game can pay in one day. */
    private static final int DAILY_KP = 50;

    private BoardView board;
    private TextView info;
    private int level;
    private int score;
    private int balls;
    private int levelScoreStart;

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
        int pad = Ui.dp(this, 14);
        root.setPadding(pad, pad, pad, pad);
        LinearLayout bar = Ui.topBar(this, "Peg Pop");
        Button how = Ui.button(this, "How", false);
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        how.setPadding(0, 0, 0, 0);
        how.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHow();
            }
        });
        bar.addView(how, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 40)));
        root.addView(bar, Ui.full(this, 0));

        info = Ui.text(this, "", 15, Ui.ACCENT);
        info.setTypeface(Ui.medium());
        root.addView(info, Ui.full(this, 8));

        board = new BoardView(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bp.topMargin = Ui.dp(this, 8);
        root.addView(board, bp);
        root.addView(Ui.text(this, "Drag anywhere on the board to aim. Let go to shoot.",
                12, Ui.SUB), Ui.full(this, 8));

        setContentView(root);
        Bg.apply(this, root);
        newGame();
    }

    private void newGame() {
        level = 1;
        score = 0;
        balls = BALLS;
        levelScoreStart = 0;
        board.build(level);
        paint();
    }

    private void paint() {
        info.setText(String.format(Locale.US, "Level %d   •   Balls %d   •   Gold left "
                + "%d   •   %,d", level, balls, board.goldLeft(), score));
    }

    /** Called by the board when a shot has finished and its pegs are cleared. */
    private void shotDone(int points, boolean caught) {
        score += points;
        if (caught) {
            balls++;
            Toast.makeText(this, "Caught it. Free ball!", Toast.LENGTH_SHORT).show();
        }
        if (board.goldLeft() == 0) {
            int bonus = balls * 500;
            score += bonus;
            Sfx.play(this, Sfx.BIG_WIN);
            final int cleared = level;
            level++;
            balls = BALLS;
            levelScoreStart = score;
            board.build(level);
            paint();
            new AlertDialog.Builder(this)
                    .setTitle("Level " + cleared + " cleared")
                    .setMessage(String.format(Locale.US, "Bonus for the balls you had "
                            + "left: %,d\nScore: %,d\n\nLevel %d has more gold pegs.", bonus,
                            score, level))
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
        int kp = Math.max(0, Math.min(Math.min(25, score / 1500), DAILY_KP - earnedToday));
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
                : "\n\nNo Knowledge Points this time. They come at 1 per 1,500 points, up "
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
                .setMessage("Drag on the board to aim the launcher at the top, and let go "
                        + "to shoot. The dotted line shows where the ball starts out.\n\n"
                        + "The ball bounces down through the pegs. Every peg it touches "
                        + "lights up and disappears when the shot ends. Clear every GOLD "
                        + "peg to finish the level. Blue pegs are just in the way, and "
                        + "worth a few points.\n\n"
                        + "You get " + BALLS + " balls a level. Land a ball in the moving "
                        + "bucket at the bottom and you get it back. Balls you have left "
                        + "when the level ends are worth 500 each.\n\n"
                        + "Scoring: a blue peg is 10 and a gold peg is 100, and each peg "
                        + "in the same shot is worth more than the last, so long bounces "
                        + "pay best.\n\n"
                        + "When the game ends you earn 1 Knowledge Point per 1,500 points, "
                        + "up to 25 a game and " + DAILY_KP + " a day.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private static final class Peg {
        float x, y;
        boolean gold;
        boolean hit;
    }

    /** The board: pegs, the ball, the bucket and the physics that moves them. */
    final class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Random rnd = new Random();
        private final List<Peg> pegs = new ArrayList<>();
        private int pendingLevel = -1;

        // Everything is measured in units of the board's width.
        private float pegR = 0.024f;
        private float ballR = 0.019f;
        private float aimX = 0.5f, aimY = 0.5f;
        private boolean aiming;
        private boolean flying;
        private float bx, by, vx, vy;
        private long lastStep;
        private long shotStart;
        private float slowFor;
        private int shotHits;
        private int shotPoints;
        private float bucketX = 0.5f;
        private float bucketDir = 1f;

        BoardView(Context c) {
            super(c);
        }

        private float heightUnits() {
            return getWidth() <= 0 ? 1.4f : getHeight() / (float) getWidth();
        }

        int goldLeft() {
            int n = 0;
            for (Peg p : pegs) {
                if (p.gold) n++;
            }
            return n;
        }

        void build(int lvl) {
            if (getWidth() <= 0) {
                // Not laid out yet; build once the size is known.
                pendingLevel = lvl;
                return;
            }
            pendingLevel = -1;
            pegs.clear();
            flying = false;
            float h = heightUnits();
            float top = 0.3f;
            float bottom = h - 0.22f;
            int rows = Math.max(5, Math.min(9, Math.round((bottom - top) / 0.105f)));
            float rowGap = (bottom - top) / (rows - 1);
            for (int r = 0; r < rows; r++) {
                boolean shifted = r % 2 == 1;
                int cols = shifted ? 7 : 8;
                for (int c = 0; c < cols; c++) {
                    // Leave some gaps so every board plays differently.
                    if (rnd.nextInt(100) < 16) continue;
                    Peg p = new Peg();
                    float x = (shifted ? 0.125f : 0.0625f) + c * 0.125f;
                    // Odd levels curve the rows into gentle waves.
                    float wave = lvl % 2 == 0 ? 0f
                            : (float) Math.sin(x * Math.PI * 2 + r) * rowGap * 0.22f;
                    p.x = x;
                    p.y = top + r * rowGap + wave;
                    pegs.add(p);
                }
            }
            // More gold pegs each level, up to most of the board.
            int gold = Math.min(pegs.size() - 4, 8 + lvl * 2);
            List<Peg> pool = new ArrayList<>(pegs);
            java.util.Collections.shuffle(pool, rnd);
            for (int i = 0; i < gold && i < pool.size(); i++) pool.get(i).gold = true;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            if (pendingLevel > 0 || pegs.isEmpty()) {
                build(pendingLevel > 0 ? pendingLevel : level);
                paint();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (flying || balls <= 0 || getWidth() <= 0) return true;
            float x = e.getX() / getWidth();
            float y = e.getY() / getWidth();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    aiming = true;
                    aimX = x;
                    aimY = Math.max(0.12f, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    if (aiming) shoot();
                    aiming = false;
                    break;
                default:
                    aiming = false;
                    break;
            }
            return true;
        }

        private void shoot() {
            float dx = aimX - 0.5f;
            float dy = aimY - 0.06f;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.01f) return;
            float speed = 1.15f;
            bx = 0.5f;
            by = 0.06f;
            vx = dx / len * speed;
            vy = dy / len * speed;
            flying = true;
            balls--;
            shotHits = 0;
            shotPoints = 0;
            slowFor = 0;
            lastStep = SystemClock.uptimeMillis();
            shotStart = lastStep;
            PegsActivity.this.paint();
            invalidate();
        }

        private void step(float dt) {
            float h = heightUnits();
            vy += 1.5f * dt;
            bx += vx * dt;
            by += vy * dt;
            if (bx < ballR) {
                bx = ballR;
                vx = Math.abs(vx) * 0.85f;
            } else if (bx > 1 - ballR) {
                bx = 1 - ballR;
                vx = -Math.abs(vx) * 0.85f;
            }
            if (by < ballR) {
                by = ballR;
                vy = Math.abs(vy) * 0.85f;
            }
            float reach = ballR + pegR;
            for (Peg p : pegs) {
                float dx = bx - p.x;
                float dy = by - p.y;
                float d2 = dx * dx + dy * dy;
                if (d2 >= reach * reach || d2 == 0) continue;
                float d = (float) Math.sqrt(d2);
                float nx = dx / d;
                float ny = dy / d;
                // Push the ball back out of the peg and bounce it off.
                bx = p.x + nx * reach;
                by = p.y + ny * reach;
                float along = vx * nx + vy * ny;
                if (along < 0) {
                    vx -= 1.8f * along * nx;
                    vy -= 1.8f * along * ny;
                }
                if (!p.hit) {
                    p.hit = true;
                    shotHits++;
                    // Each peg in a shot is worth a little more than the last.
                    shotPoints += (p.gold ? 100 : 10) * (1 + shotHits / 4);
                    Sfx.play(getContext(), Sfx.PEG);
                }
            }
            bucketX += bucketDir * 0.32f * dt;
            if (bucketX > 0.88f) {
                bucketX = 0.88f;
                bucketDir = -1;
            } else if (bucketX < 0.12f) {
                bucketX = 0.12f;
                bucketDir = 1;
            }

            // A ball that has come to rest on the pegs would never finish, so once it has
            // crawled for a while, clear the pegs it touched and let it fall.
            float speed = (float) Math.sqrt(vx * vx + vy * vy);
            slowFor = speed < 0.12f ? slowFor + dt : 0;
            if (slowFor > 1.2f) {
                clearHit();
                slowFor = 0;
            }

            if (by > h + ballR) {
                finishShot(false);
            } else if (by > h - 0.07f && vy > 0 && Math.abs(bx - bucketX) < 0.1f) {
                finishShot(true);
            } else if (SystemClock.uptimeMillis() - shotStart > 30000) {
                finishShot(false);
            }
        }

        private void clearHit() {
            for (int i = pegs.size() - 1; i >= 0; i--) {
                if (pegs.get(i).hit) pegs.remove(i);
            }
        }

        private void finishShot(boolean caught) {
            flying = false;
            clearHit();
            if (caught) Sfx.play(getContext(), Sfx.WIN);
            shotDone(shotPoints, caught);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0) return;

            if (flying) {
                long now = SystemClock.uptimeMillis();
                float dt = Math.min(0.05f, (now - lastStep) / 1000f);
                lastStep = now;
                // Several small steps a frame, so a fast ball can't pass through a peg.
                int steps = 6;
                for (int i = 0; i < steps && flying; i++) step(dt / steps);
            } else {
                bucketX += bucketDir * 0.32f * 0.016f;
                if (bucketX > 0.88f) {
                    bucketX = 0.88f;
                    bucketDir = -1;
                } else if (bucketX < 0.12f) {
                    bucketX = 0.12f;
                    bucketDir = 1;
                }
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x66000000);
            rect.set(0, 0, w, h);
            cv.drawRoundRect(rect, w * 0.04f, w * 0.04f, paint);

            for (Peg p : pegs) {
                int color = p.gold ? 0xFFFFB74D : 0xFF64B5F6;
                if (p.hit) {
                    paint.setColor((color & 0x00FFFFFF) | 0x55000000);
                    cv.drawCircle(p.x * w, p.y * w, pegR * w * 1.7f, paint);
                    paint.setColor(0xFFFFFFFF);
                } else {
                    paint.setColor(color);
                }
                cv.drawCircle(p.x * w, p.y * w, pegR * w, paint);
                if (!p.hit) {
                    paint.setColor(0x55FFFFFF);
                    cv.drawCircle(p.x * w - pegR * w * 0.3f, p.y * w - pegR * w * 0.3f,
                            pegR * w * 0.35f, paint);
                }
            }

            // The bucket.
            paint.setColor(Ui.ACCENT);
            rect.set((bucketX - 0.1f) * w, h - 0.05f * w, (bucketX + 0.1f) * w, h + 0.03f * w);
            cv.drawRoundRect(rect, 0.02f * w, 0.02f * w, paint);
            paint.setColor(0xAA000000);
            rect.set((bucketX - 0.08f) * w, h - 0.035f * w, (bucketX + 0.08f) * w, h + 0.03f * w);
            cv.drawRoundRect(rect, 0.015f * w, 0.015f * w, paint);

            // The launcher and the aim line.
            paint.setColor(Ui.ACCENT);
            cv.drawCircle(0.5f * w, 0.06f * w, 0.035f * w, paint);
            if (!flying && balls > 0) {
                float dx = aimX - 0.5f;
                float dy = aimY - 0.06f;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 0.01f) {
                    // Show the first part of the path the ball will take.
                    float px = 0.5f, py = 0.06f;
                    float pvx = dx / len * 1.15f, pvy = dy / len * 1.15f;
                    paint.setColor(aiming ? 0xCCFFFFFF : 0x55FFFFFF);
                    for (int i = 0; i < 14; i++) {
                        for (int k = 0; k < 4; k++) {
                            pvy += 1.5f * 0.01f;
                            px += pvx * 0.01f;
                            py += pvy * 0.01f;
                        }
                        if (px < 0 || px > 1) break;
                        cv.drawCircle(px * w, py * w, 0.006f * w, paint);
                    }
                }
                paint.setColor(0xFFFFFFFF);
                cv.drawCircle(0.5f * w, 0.06f * w, ballR * w, paint);
            }
            if (flying) {
                paint.setColor(0xFFFFFFFF);
                cv.drawCircle(bx * w, by * w, ballR * w, paint);
            }
            postInvalidateOnAnimation();
        }
    }
}
