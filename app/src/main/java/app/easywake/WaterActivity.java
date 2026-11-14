package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** A glass that fills as you drink through the day, with optional reminders. */
public class WaterActivity extends Activity {
    private GlassView glass;
    private TextView countText;
    private TextView noteText;
    private Ui.Row remindRow;
    private Ui.Row everyRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "l_water")) {
            Toast.makeText(this, "Unlock the Water reminder in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.topBar(this, "Drink water"), Ui.full(this, 0));

        glass = new GlassView(this);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(this, 190),
                Ui.dp(this, 250));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(this, 18);
        root.addView(glass, gp);

        countText = Ui.text(this, "", 24, Ui.TEXT);
        countText.setTypeface(Ui.medium());
        countText.setGravity(Gravity.CENTER);
        root.addView(countText, Ui.full(this, 14));
        noteText = Ui.text(this, "", 14, Ui.SUB);
        noteText.setGravity(Gravity.CENTER);
        root.addView(noteText, Ui.full(this, 4));

        Button drank = Ui.button(this, "I drank a glass", true);
        drank.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        drank.setMinHeight(Ui.dp(this, 70));
        drank.setMinimumHeight(Ui.dp(this, 70));
        drank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int earned = Water.drink(WaterActivity.this);
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                int n = Water.count(WaterActivity.this);
                Toast.makeText(WaterActivity.this, n == Water.GOAL
                        ? "That's all " + Water.GOAL + " for today. +1 Knowledge Point"
                        : (earned > 0 ? "Nice. +1 Knowledge Point"
                        : "Logged. Today's points are already earned."),
                        Toast.LENGTH_SHORT).show();
                paint();
            }
        });
        root.addView(drank, Ui.full(this, 16));

        root.addView(Ui.heading(this, "Reminders"), Ui.full(this, 24));
        remindRow = Ui.row(this, "Remind me", "");
        remindRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean turnOn = !Water.on(WaterActivity.this);
                Prefs.putInt(WaterActivity.this, "waterOn", turnOn ? 1 : 0);
                if (turnOn && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
                Water.schedule(WaterActivity.this);
                paint();
            }
        });
        root.addView(remindRow.view, Ui.full(this, 10));
        everyRow = Ui.row(this, "How often", "");
        everyRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = (Prefs.getInt(WaterActivity.this, "waterEvery", 1) + 1)
                        % Water.EVERY_HOURS.length;
                Prefs.putInt(WaterActivity.this, "waterEvery", i);
                Water.schedule(WaterActivity.this);
                paint();
            }
        });
        root.addView(everyRow.view, Ui.full(this, 10));
        root.addView(Ui.text(this, "Reminders arrive between 8 in the morning and 9 at "
                + "night, and stop for the day once you've had " + Water.GOAL + " glasses. "
                + "Each of the first " + Water.GOAL + " glasses a day earns 1 Knowledge "
                + "Point. Eight glasses is a common rule of thumb, not a medical target: "
                + "how much you need depends on your size, the heat and how active you "
                + "are.", 12, Ui.SUB), Ui.full(this, 12));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glass != null) paint();
    }

    private void paint() {
        int n = Water.count(this);
        glass.setLevel(Math.min(1f, n / (float) Water.GOAL));
        countText.setText(n + " of " + Water.GOAL + " glasses today");
        noteText.setText(n == 0 ? "Start with one now."
                : (n < Water.GOAL ? (Water.GOAL - n) + " to go."
                : "Done for today. Well watered."));
        remindRow.value.setText(Water.on(this) ? "On" : "Off");
        everyRow.value.setText(Water.everyHours(this) == 1 ? "Every hour"
                : "Every " + Water.everyHours(this) + " hours");
    }

    /** A drinking glass with gently moving water that rises with each glass logged. */
    static final class GlassView extends View {
        private final Paint water = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();
        private final Path wave = new Path();
        private float target;
        private float level;

        GlassView(Context c) {
            super(c);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(Ui.dp(c, 4));
            outline.setStrokeJoin(Paint.Join.ROUND);
            outline.setStrokeCap(Paint.Cap.ROUND);
            outline.setColor(0xCCFFFFFF);
            water.setColor(0xFF4FC3F7);
            shine.setStyle(Paint.Style.STROKE);
            shine.setStrokeWidth(Ui.dp(c, 5));
            shine.setStrokeCap(Paint.Cap.ROUND);
            shine.setColor(0x44FFFFFF);
        }

        void setLevel(float v) {
            target = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float pad = outline.getStrokeWidth();
            float topL = pad;
            float topR = w - pad;
            float botL = w * 0.2f;
            float botR = w * 0.8f;
            float top = pad;
            float bottom = h - pad;

            // Ease toward the new level so the water visibly rises.
            level += (target - level) * 0.08f;
            if (Math.abs(target - level) < 0.002f) level = target;

            shape.reset();
            shape.moveTo(topL, top);
            shape.lineTo(botL, bottom);
            shape.lineTo(botR, bottom);
            shape.lineTo(topR, top);
            shape.close();

            if (level > 0.001f) {
                float surface = bottom - (bottom - top) * 0.92f * level;
                float t = SystemClock.uptimeMillis() / 600f;
                float amp = h * 0.012f;
                wave.reset();
                wave.moveTo(0, h);
                wave.lineTo(0, surface);
                for (float x = 0; x <= w; x += w / 24f) {
                    wave.lineTo(x, surface + (float) Math.sin(t + x / w * 6.283f * 1.5f) * amp);
                }
                wave.lineTo(w, h);
                wave.close();
                cv.save();
                cv.clipPath(shape);
                cv.drawPath(wave, water);
                cv.restore();
            }
            cv.drawLine(topL, top, botL, bottom, outline);
            cv.drawLine(botL, bottom, botR, bottom, outline);
            cv.drawLine(botR, bottom, topR, top, outline);
            cv.drawLine(w * 0.24f, h * 0.2f, w * 0.32f, h * 0.72f, shine);

            // Keep the water moving while the screen is showing.
            postInvalidateOnAnimation();
        }
    }
}
