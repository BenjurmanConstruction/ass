package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Pick which part of a song the alarm plays, with a live preview. */
public class TrimActivity extends Activity {
    private MediaPlayer mp;
    private long duration;
    private boolean playing;

    private RangeBar bar;
    private TextView rangeText;
    private Button playBtn;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (mp == null || !playing) return;
            try {
                int pos = mp.getCurrentPosition();
                if (pos >= bar.end || pos < bar.start - 1500) {
                    mp.seekTo(bar.start, MediaPlayer.SEEK_CLOSEST);
                    pos = (int) bar.start;
                }
                bar.playhead = pos;
                bar.invalidate();
            } catch (IllegalStateException e) {
                return;
            }
            handler.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String uri = getIntent().getStringExtra("uri");
        String name = getIntent().getStringExtra("name");

        mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(this, Uri.parse(uri));
            mp.prepare();
            duration = mp.getDuration();
        } catch (Exception e) {
            duration = 0;
        }
        if (duration <= 0) {
            Toast.makeText(this, "Couldn't open that song.", Toast.LENGTH_LONG).show();
            release();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Part of song"), Ui.full(this, 0));

        TextView song = Ui.text(this, name == null ? "" : name, 15, Ui.SUB);
        song.setMaxLines(1);
        song.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        root.addView(song, Ui.full(this, 10));

        rangeText = Ui.text(this, "", 30, Ui.TEXT);
        rangeText.setTypeface(Ui.light());
        rangeText.setGravity(Gravity.CENTER);
        root.addView(rangeText, Ui.full(this, 18));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, 22));
        card.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 12));

        bar = new RangeBar(this);
        bar.duration = duration;
        bar.minGap = Math.min(3000, duration);
        long s = getIntent().getLongExtra("start", 0);
        long e = getIntent().getLongExtra("end", 0);
        if (e > s && s >= 0 && e <= duration + 500) {
            bar.start = s;
            bar.end = Math.min(e, duration);
        } else {
            bar.start = 0;
            bar.end = duration;
        }
        bar.listener = new RangeBar.Listener() {
            @Override
            public void onChange(boolean startMoved, boolean done) {
                paint();
                if (done && playing) jump(startMoved);
            }
        };
        card.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 76)));

        LinearLayout ends = new LinearLayout(this);
        ends.setOrientation(LinearLayout.HORIZONTAL);
        ends.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        ends.addView(Ui.text(this, "0:00", 12, Ui.SUB), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ends.addView(Ui.text(this, Ui.clock(duration), 12, Ui.SUB));
        card.addView(ends);
        root.addView(card, Ui.full(this, 14));

        root.addView(Ui.text(this,
                "Drag the two gold handles to choose where the alarm starts and stops. "
                        + "That part repeats until you stop the alarm.", 13, Ui.SUB),
                Ui.full(this, 10));

        root.addView(Ui.heading(this, "Fine-tune"), Ui.full(this, 20));
        root.addView(nudgeRow("Start", true), Ui.full(this, 10));
        root.addView(nudgeRow("End", false), Ui.full(this, 10));

        playBtn = Ui.button(this, "", false);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playing) pause();
                else play();
            }
        });
        root.addView(playBtn, Ui.full(this, 22));

        Button whole = Ui.button(this, "Use the whole song", false);
        whole.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bar.start = 0;
                bar.end = duration;
                bar.invalidate();
                paint();
                if (playing) jump(true);
            }
        });
        root.addView(whole, Ui.full(this, 10));

        Button done = Ui.button(this, "Use this part", true);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishWithResult();
            }
        });
        root.addView(done, Ui.full(this, 10));

        setContentView(scroll);
        Bg.apply(this, scroll);
        paint();
    }

    private View nudgeRow(String label, final boolean isStart) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, label, 16, Ui.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        int[] steps = {-5000, -1000, 1000, 5000};
        String[] names = {"− 5s", "− 1s", "+ 1s", "+ 5s"};
        for (int i = 0; i < steps.length; i++) {
            final int step = steps[i];
            Button b = Ui.button(this, names[i], false);
            b.setTextSize(14);
            b.setMinHeight(Ui.dp(this, 42));
            b.setMinimumHeight(Ui.dp(this, 42));
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    nudge(isStart, step);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42));
            p.leftMargin = Ui.dp(this, 6);
            row.addView(b, p);
        }
        return row;
    }

    private void nudge(boolean isStart, int step) {
        if (isStart) {
            bar.start = Math.max(0, Math.min(bar.start + step, bar.end - bar.minGap));
        } else {
            bar.end = Math.min(duration, Math.max(bar.end + step, bar.start + bar.minGap));
        }
        bar.invalidate();
        paint();
        if (playing) jump(isStart);
    }

    /** After a change, jump so you hear the new start, or the last few seconds before the end. */
    private void jump(boolean toStart) {
        if (mp == null) return;
        long target = toStart ? bar.start : Math.max(bar.start, bar.end - 3000);
        try {
            mp.seekTo(target, MediaPlayer.SEEK_CLOSEST);
        } catch (IllegalStateException e) {
            // Player gone.
        }
    }

    private void play() {
        if (mp == null) return;
        try {
            mp.seekTo(bar.start, MediaPlayer.SEEK_CLOSEST);
            mp.start();
            playing = true;
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 50);
        } catch (IllegalStateException e) {
            playing = false;
        }
        paint();
    }

    private void pause() {
        handler.removeCallbacks(tick);
        playing = false;
        if (mp != null) {
            try {
                if (mp.isPlaying()) mp.pause();
            } catch (IllegalStateException e) {
                // Player gone.
            }
        }
        if (bar != null) {
            bar.playhead = -1;
            bar.invalidate();
        }
        paint();
    }

    private void paint() {
        if (rangeText == null) return;
        boolean whole = bar.start <= 0 && bar.end >= duration;
        rangeText.setText(Ui.clock(bar.start) + "  to  " + Ui.clock(bar.end));
        playBtn.setText(playing ? "■  Stop preview"
                : (whole ? "▶  Preview whole song"
                : "▶  Preview this part (" + Ui.clock(bar.end - bar.start) + ")"));
    }

    private void finishWithResult() {
        boolean whole = bar.start <= 0 && bar.end >= duration;
        setResult(RESULT_OK, new Intent()
                .putExtra("start", whole ? 0L : bar.start)
                .putExtra("end", whole ? 0L : bar.end));
        finish();
    }

    private void release() {
        handler.removeCallbacks(tick);
        playing = false;
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception e) {
                // Already stopped.
            }
            mp.release();
            mp = null;
        }
    }

    @Override
    protected void onPause() {
        if (playing) pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        release();
        super.onDestroy();
    }

    /** Timeline with two draggable handles and a moving playhead. */
    static final class RangeBar extends View {
        interface Listener {
            void onChange(boolean startMoved, boolean done);
        }

        long duration = 1;
        long start;
        long end;
        long minGap = 3000;
        long playhead = -1;
        Listener listener;

        private int active;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        RangeBar(Context c) {
            super(c);
            track.setColor(0x33FFFFFF);
            sel.setColor(Ui.ACCENT);
            knob.setColor(Ui.ACCENT_HI);
            knobRing.setColor(0xFF20160A);
            knobRing.setStyle(Paint.Style.STROKE);
            knobRing.setStrokeWidth(Ui.dp(c, 2));
            head.setColor(0xFFFFFFFF);
            head.setStrokeWidth(Ui.dp(c, 2));
        }

        private float padX() {
            return Ui.dp(getContext(), 24);
        }

        private float xFor(long t) {
            float w = getWidth() - 2 * padX();
            return padX() + w * (t / (float) duration);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float cy = getHeight() / 2f;
            float half = Ui.dp(getContext(), 4);
            float r = Ui.dp(getContext(), 14);
            float xs = xFor(start);
            float xe = xFor(end);

            rect.set(padX(), cy - half, getWidth() - padX(), cy + half);
            cv.drawRoundRect(rect, half, half, track);
            rect.set(xs, cy - half, xe, cy + half);
            cv.drawRoundRect(rect, half, half, sel);

            if (playhead >= start && playhead <= end) {
                float xp = xFor(playhead);
                cv.drawLine(xp, cy - r - half, xp, cy + r + half, head);
            }
            cv.drawCircle(xs, cy, r, knob);
            cv.drawCircle(xs, cy, r, knobRing);
            cv.drawCircle(xe, cy, r, knob);
            cv.drawCircle(xe, cy, r, knobRing);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth() - 2 * padX();
            if (w <= 0) return false;
            float x = e.getX();
            float frac = Math.max(0f, Math.min(1f, (x - padX()) / w));
            long t = (long) (frac * duration);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    float ds = Math.abs(x - xFor(start));
                    float de = Math.abs(x - xFor(end));
                    if (ds < de) active = 1;
                    else if (de < ds) active = 2;
                    else active = x > xFor(end) ? 2 : 1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    move(t, false);
                    break;
                case MotionEvent.ACTION_MOVE:
                    move(t, false);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    move(t, true);
                    active = 0;
                    break;
                default:
                    break;
            }
            return true;
        }

        private void move(long t, boolean done) {
            if (active == 0) return;
            if (active == 1) {
                start = Math.max(0, Math.min(t, end - minGap));
            } else {
                end = Math.min(duration, Math.max(t, start + minGap));
            }
            invalidate();
            if (listener != null) listener.onChange(active == 1, done);
        }
    }
}
