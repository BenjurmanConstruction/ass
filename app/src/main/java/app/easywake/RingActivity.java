package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Full-screen display shown while an alarm rings, including over the lock screen. */
public class RingActivity extends Activity {
    private final Runnable closer = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (AlarmService.ringingId == -1) {
            finish();
            return;
        }
        AlarmService.onStopped = closer;

        FrameLayout frame = new FrameLayout(this);
        Bg.apply(this, frame);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        // Extra tint so the buttons stand out over any background picture.
        root.setBackgroundColor(0x59000000);
        int pad = Ui.dp(this, 28);
        root.setPadding(pad, Ui.dp(this, 72), pad, Ui.dp(this, 36));
        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextClock clock = new TextClock(this);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        clock.setTextColor(Ui.TEXT);
        clock.setTypeface(Ui.light());
        clock.setGravity(Gravity.CENTER);
        root.addView(clock, wrap());

        String label = AlarmService.ringingLabel;
        TextView labelView = Ui.text(this, label == null || label.isEmpty() ? "Alarm" : label,
                20, Ui.SUB);
        labelView.setGravity(Gravity.CENTER);
        root.addView(labelView, wrap());

        View spacerTop = new View(this);
        root.addView(spacerTop, new LinearLayout.LayoutParams(1, 0, 1f));

        // Big round Snooze: the easy target when you are half asleep.
        Button snooze = new Button(this);
        snooze.setAllCaps(false);
        snooze.setStateListAnimator(null);
        snooze.setText("Snooze\n" + AlarmService.ringingSnoozeMin + " min");
        snooze.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        snooze.setTypeface(Ui.medium());
        snooze.setTextColor(Ui.ON_ACCENT);
        snooze.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.ACCENT_HI, Ui.ACCENT_LO});
        circle.setShape(GradientDrawable.OVAL);
        snooze.setBackground(circle);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(AlarmService.ACTION_SNOOZE);
            }
        });
        int size = Ui.dp(this, 220);
        root.addView(snooze, new LinearLayout.LayoutParams(size, size));

        View spacerBottom = new View(this);
        root.addView(spacerBottom, new LinearLayout.LayoutParams(1, 0, 1f));

        Button stop = Ui.button(this, "Stop alarm", false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        stop.setMinHeight(Ui.dp(this, 64));
        stop.setMinimumHeight(Ui.dp(this, 64));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(AlarmService.ACTION_DISMISS);
            }
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(frame);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void send(String action) {
        try {
            startService(new Intent(this, AlarmService.class).setAction(action));
        } catch (Exception e) {
            // Service already gone.
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AlarmService.ringingId == -1) finish();
    }

    @Override
    public void onBackPressed() {
        // Ignore Back so the alarm cannot be hidden by accident; use Snooze or Stop.
    }

    @Override
    protected void onDestroy() {
        if (AlarmService.onStopped == closer) AlarmService.onStopped = null;
        super.onDestroy();
    }
}
