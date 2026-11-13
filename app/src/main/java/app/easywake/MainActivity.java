package app.easywake;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Home screen: the list of alarms. */
public class MainActivity extends Activity {
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, "Alarms", 30, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = Ui.dp(this, 14);
        root.addView(title, tp);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = Ui.button(this, "+ Add alarm", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ap.topMargin = Ui.dp(this, 12);
        root.addView(add, ap);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Scheduler.rescheduleAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        addWarnings();

        List<Alarm> alarms = Store.load(this);
        Collections.sort(alarms, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm x, Alarm y) {
                return (x.hour * 60 + x.minute) - (y.hour * 60 + y.minute);
            }
        });

        if (alarms.isEmpty()) {
            TextView empty = Ui.text(this, "No alarms yet.\nTap \"+ Add alarm\" to make one.",
                    17, Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 60), 0, 0);
            list.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (Alarm a : alarms) {
            list.addView(row(a), rowParams());
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Ui.dp(this, 10);
        return p;
    }

    private View row(final Alarm a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.round(this, Ui.CARD, 16));
        int pad = Ui.dp(this, 16);
        row.setPadding(pad, pad, pad, pad);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        final TextView time = Ui.text(this, Ui.timeText(this, a.hour, a.minute), 34,
                a.enabled ? Ui.TEXT : Ui.SUB);
        left.addView(time);

        StringBuilder sub = new StringBuilder();
        if (a.label != null && !a.label.isEmpty()) sub.append(a.label).append("  •  ");
        sub.append(Ui.daysText(a.days)).append("  •  ").append(a.soundText());
        if (a.snoozeUntil > System.currentTimeMillis()) sub.append("  •  Snoozed");
        TextView subView = Ui.text(this, sub.toString(), 14, Ui.SUB);
        left.addView(subView);

        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(a.enabled);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean on) {
                Alarm fresh = Store.get(MainActivity.this, a.id);
                if (fresh == null) return;
                fresh.enabled = on;
                if (!on) fresh.snoozeUntil = 0;
                Store.put(MainActivity.this, fresh);
                Scheduler.rescheduleAll(MainActivity.this);
                time.setTextColor(on ? Ui.TEXT : Ui.SUB);
                if (on) {
                    Toast.makeText(MainActivity.this,
                            Ui.untilText(fresh.nextTrigger(System.currentTimeMillis())),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class)
                        .putExtra("id", a.id));
            }
        });
        return row;
    }

    /** Shows a tappable notice for each phone setting that would stop alarms working. */
    private void addWarnings() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        final Uri pkg = Uri.parse("package:" + getPackageName());

        if (nm != null && !nm.areNotificationsEnabled()) {
            addWarning("Notifications are off, so alarms can't show on screen. Tap to turn on.",
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
        if (Build.VERSION.SDK_INT >= 31 && !Scheduler.canExact(this)) {
            addWarning("Alarms may ring late until you allow \"Alarms & reminders\". Tap to allow.",
                    new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg));
        }
        if (Build.VERSION.SDK_INT >= 34 && nm != null && !nm.canUseFullScreenIntent()) {
            addWarning("Allow full-screen alarms so the Snooze and Stop buttons appear on the "
                            + "lock screen. Tap to allow.",
                    new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg));
        }
    }

    private void addWarning(String msg, final Intent settings) {
        TextView t = Ui.text(this, msg, 15, Ui.TEXT);
        t.setBackground(Ui.round(this, Ui.WARN, 14));
        int pad = Ui.dp(this, 14);
        t.setPadding(pad, pad, pad, pad);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(settings);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {
                        // No settings screen available.
                    }
                }
            }
        });
        list.addView(t, rowParams());
    }
}
