package app.easywake;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Home screen: the list of alarms, plus the menu. */
public class MainActivity extends Activity {
    private LinearLayout root;
    private LinearLayout list;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, "Easy Wake", 30, Ui.TEXT);
        title.setTypeface(Ui.light());
        titles.addView(title);
        subtitle = Ui.text(this, "", 14, Ui.ACCENT);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView menu = new ImageView(this);
        menu.setImageResource(R.drawable.ic_menu);
        menu.setScaleType(ImageView.ScaleType.CENTER);
        menu.setContentDescription("Menu");
        menu.setBackground(Ui.ripple(this, Ui.card(this, 24), 24));
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        header.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        root.addView(header, Ui.full(this, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = Ui.dp(this, 18);
        root.addView(scroll, sp);

        Button add = Ui.button(this, "+  New alarm", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
            }
        });
        root.addView(add, Ui.full(this, 12));

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
        Bg.apply(this, root);
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
        paintSubtitle(alarms);

        if (alarms.isEmpty()) {
            TextView empty = Ui.text(this, "No alarms yet.\nTap \"New alarm\" to make one.",
                    17, Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 70), 0, 0);
            list.addView(empty, Ui.full(this, 0));
            return;
        }
        for (Alarm a : alarms) {
            list.addView(row(a), rowParams());
        }
    }

    private void paintSubtitle(List<Alarm> alarms) {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (Alarm a : alarms) {
            if (a.enabled) next = Math.min(next, a.nextTrigger(now));
            if (a.snoozeUntil > now) next = Math.min(next, a.snoozeUntil);
        }
        subtitle.setText(next == Long.MAX_VALUE ? "No alarm set"
                : "Next alarm in " + Ui.untilSpan(next));
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Ui.dp(this, 12);
        return p;
    }

    private View row(final Alarm a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.ripple(this, Ui.card(this, 22), 22));
        row.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        final TextView time = Ui.text(this, Ui.timeText(this, a.hour, a.minute), 40,
                a.enabled ? Ui.TEXT : Ui.SUB);
        time.setTypeface(Ui.light());
        left.addView(time);

        StringBuilder sub = new StringBuilder();
        if (a.label != null && !a.label.isEmpty()) sub.append(a.label).append("  •  ");
        sub.append(Ui.daysText(a.days)).append("  •  ").append(a.soundText());
        if (a.snoozeUntil > System.currentTimeMillis()) sub.append("  •  Snoozed");
        left.addView(Ui.text(this, sub.toString(), 14, Ui.SUB));

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
                paintSubtitle(Store.load(MainActivity.this));
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

    /** Slide-up menu sheet. */
    private void showMenu() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141A2B);
        float r = Ui.dp(this, 26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        int pad = Ui.dp(this, 18);
        sheet.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 26));

        View grip = new View(this);
        grip.setBackground(Ui.round(this, 0x55FFFFFF, 2));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(this, 44),
                Ui.dp(this, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(this, 14);
        sheet.addView(grip, gp);

        sheet.addView(menuItem("Sleep sounds", "Fan, white, pink and brown noise",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, SleepActivity.class));
                    }
                }, d), Ui.full(this, 0));
        sheet.addView(menuItem("Background studio", "Color themes, your pictures, drawing",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, BackgroundActivity.class));
                    }
                }, d), Ui.full(this, 10));
        sheet.addView(menuItem("About", "Easy Wake " + versionName()
                        + "  •  no ads, no internet access",
                null, d), Ui.full(this, 10));

        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        d.show();
    }

    private View menuItem(String title, String sub, final Runnable action, final Dialog d) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
        item.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        TextView t = Ui.text(this, title, 18, Ui.TEXT);
        t.setTypeface(Ui.medium());
        item.addView(t);
        item.addView(Ui.text(this, sub, 13, Ui.SUB));
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                if (action != null) action.run();
            }
        });
        return item;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
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
        t.setBackground(Ui.round(this, Ui.WARN, 16));
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
