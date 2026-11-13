package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 28);
        root.setPadding(pad, pad, pad, pad);

        TextClock clock = new TextClock(this);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTextColor(Ui.TEXT);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock, wrap());

        String label = AlarmService.ringingLabel;
        TextView labelView = Ui.text(this, label == null || label.isEmpty() ? "Alarm" : label,
                20, Ui.SUB);
        labelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = wrap();
        lp.bottomMargin = Ui.dp(this, 56);
        root.addView(labelView, lp);

        Button snooze = Ui.button(this, "Snooze " + AlarmService.ringingSnoozeMin + " min", true);
        snooze.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        snooze.setMinHeight(Ui.dp(this, 84));
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(AlarmService.ACTION_SNOOZE);
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.bottomMargin = Ui.dp(this, 18);
        root.addView(snooze, sp);

        Button stop = Ui.button(this, "Stop", false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        stop.setMinHeight(Ui.dp(this, 68));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(AlarmService.ACTION_DISMISS);
            }
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
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
