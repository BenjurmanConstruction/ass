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
    private TextView joke;
    private LinearLayout strip;
    private int builtTheme;
    private int stripGen;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        builtTheme = Ui.themeVersion;
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

        // The three indexes at a glance. Tap to open the Market screen.
        strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
        strip.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        strip.setVisibility(View.GONE);
        strip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MarketActivity.class));
            }
        });
        root.addView(strip, Ui.full(this, 14));

        // Dad joke of the moment. Tap it for another.
        LinearLayout jokeCard = new LinearLayout(this);
        jokeCard.setOrientation(LinearLayout.VERTICAL);
        jokeCard.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
        jokeCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        joke = Ui.text(this, "", 15, Ui.TEXT);
        jokeCard.addView(joke);
        TextView jokeHint = Ui.text(this, "Joke  \u2022  tap for another", 11, Ui.SUB);
        jokeCard.addView(jokeHint, Ui.full(this, 4));
        jokeCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                joke.setText(Jokes.next());
            }
        });
        root.addView(jokeCard, Ui.full(this, 10));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = Ui.dp(this, 12);
        root.addView(scroll, sp);

        Button add = Ui.button(this, "+  New alarm", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
            }
        });
        root.addView(add, Ui.full(this, 12));

        // Small version stamp so it is easy to tell which build is installed.
        TextView version = Ui.text(this, "Version " + versionName(), 12, Ui.SUB);
        version.setGravity(Gravity.CENTER);
        root.addView(version, Ui.full(this, 8));

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
        if (builtTheme != Ui.themeVersion) {
            // Colors changed while another screen was open: rebuild with the new ones.
            recreate();
            return;
        }
        Bg.apply(this, root);
        joke.setText(Jokes.next());
        refresh();
        loadStrip();
    }

    /** Fetches the three indexes in the background and fills the strip. */
    private void loadStrip() {
        if (Prefs.getInt(this, "marketHome", 1) != 1) {
            strip.setVisibility(View.GONE);
            return;
        }
        final int gen = ++stripGen;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MarketData.Series[] out = new MarketData.Series[MarketData.INDEXES.length];
                for (int i = 0; i < out.length; i++) {
                    out[i] = MarketData.fetch(MarketData.INDEXES[i], 0);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen == stripGen && !isFinishing()) paintStrip(out);
                    }
                });
            }
        }).start();
    }

    private void paintStrip(MarketData.Series[] data) {
        strip.removeAllViews();
        boolean any = false;
        for (int i = 0; i < data.length; i++) {
            MarketData.Series s = data[i];
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(Ui.text(this, MarketData.INDEXES[i].name, 11, Ui.SUB));
            if (s.ok()) {
                any = true;
                TextView v = Ui.text(this, MarketData.fmt(s.last), 15, Ui.TEXT);
                v.setTypeface(Ui.medium());
                col.addView(v);
                double pct = s.pct();
                if (!Double.isNaN(pct)) {
                    col.addView(Ui.text(this, (pct >= 0 ? "\u25B2 +" : "\u25BC ")
                                    + MarketData.fmt(pct) + "%", 12,
                            pct >= 0 ? MarketData.UP : MarketData.DOWN));
                }
            } else {
                col.addView(Ui.text(this, "--", 15, Ui.SUB));
            }
            strip.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        strip.setVisibility(any ? View.VISIBLE : View.GONE);
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
        Ui.tint(sw);
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

        sheet.addView(menuItem("Sleep sounds", "Fan, noise, or your own recording",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, SleepActivity.class));
                    }
                }, d), Ui.full(this, 0));
        sheet.addView(menuItem("Calendar", "Month view with your alarm days marked",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, CalendarActivity.class));
                    }
                }, d), Ui.full(this, 10));
        sheet.addView(menuItem("Market", "Indexes, gold, oil, Bitcoin and your watchlist",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, MarketActivity.class));
                    }
                }, d), Ui.full(this, 10));
        sheet.addView(menuItem("Brain gym", "Crazy facts and quick brain tests",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, BrainActivity.class));
                    }
                }, d), Ui.full(this, 10));
        sheet.addView(menuItem("Colors & night mode", "Accent color and dim modes for late nights",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, ThemeActivity.class));
                    }
                }, d), Ui.full(this, 10));
        sheet.addView(menuItem("Background studio", "Color themes, your pictures, drawing",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, BackgroundActivity.class));
                    }
                }, d), Ui.full(this, 10));
        sheet.addView(menuItem("About", "Easy Wake " + versionName()
                        + "  \u2022  no ads. Internet is used only for market data.",
                null, d), Ui.full(this, 10));

        sheet.addView(menuItem("Uninstall Easy Wake", "Removes the app and all its alarms",
                new Runnable() {
                    @Override
                    public void run() {
                        uninstall();
                    }
                }, d), Ui.full(this, 10));

        // Scrolls on small screens now that the menu is longer.
        ScrollView sheetScroll = new ScrollView(this);
        sheetScroll.addView(sheet);
        d.setContentView(sheetScroll);
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

    /** Opens Android's own "uninstall this app?" prompt. */
    private void uninstall() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                Toast.makeText(this, "Hold the app icon on your home screen and tap Uninstall.",
                        Toast.LENGTH_LONG).show();
            }
        }
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
