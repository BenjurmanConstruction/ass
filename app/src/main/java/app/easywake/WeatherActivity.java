package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Forecast for the chosen city, plus where to pick the city and units. */
public class WeatherActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private Weather.Report report;
    private boolean loading;
    private int generation;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "l_weather")) {
            Toast.makeText(this, "Unlock Weather in the skill tree first.", Toast.LENGTH_LONG)
                    .show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        Bg.apply(this, scroll);
        build();
        if (Weather.hasCity(this)) load();
        else askCity();
    }

    private void load() {
        final int gen = ++generation;
        loading = true;
        build();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Weather.Report r = Weather.fetch(WeatherActivity.this);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation || isFinishing()) return;
                        report = r;
                        loading = false;
                        build();
                    }
                });
            }
        }).start();
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Weather"), Ui.full(this, 0));

        String city = Weather.city(this);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.card(this, 22));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        hero.addView(Ui.heading(this, city == null || city.isEmpty() ? "No city chosen"
                : city));
        if (!Weather.hasCity(this)) {
            hero.addView(Ui.text(this, "Pick a city to see its forecast here and on the home "
                    + "screen.", 15, Ui.SUB), Ui.full(this, 6));
        } else if (loading || report == null) {
            hero.addView(Ui.text(this, "Loading...", 22, Ui.SUB), Ui.full(this, 6));
        } else if (!report.ok) {
            hero.addView(Ui.text(this, MarketData.netOff
                    ? "Internet is switched off for this app. Turn it on in Settings."
                    : "Couldn't load the forecast. Check your internet. (" + report.error
                    + ")", 15, Ui.SUB), Ui.full(this, 6));
        } else {
            TextView now = Ui.text(this, Weather.icon(report.code) + "  "
                    + Weather.deg(report.temp), 54, Ui.TEXT);
            now.setTypeface(Ui.light());
            hero.addView(now);
            hero.addView(Ui.text(this, Weather.describe(report.code) + "   •   Feels like "
                    + Weather.deg(report.feels), 16, Ui.TEXT));
            hero.addView(Ui.text(this, "Wind " + Math.round(report.wind)
                    + (Weather.celsius(this) ? " km/h" : " mph"), 14, Ui.SUB),
                    Ui.full(this, 2));
        }
        root.addView(hero, Ui.full(this, 14));

        if (report != null && report.ok && !loading) {
            root.addView(Ui.heading(this, "Next days"), Ui.full(this, 22));
            for (int i = 0; i < report.days.length; i++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(Ui.card(this, 16));
                row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16),
                        Ui.dp(this, 12));
                TextView day = Ui.text(this, report.days[i], 16, Ui.TEXT);
                day.setTypeface(Ui.medium());
                row.addView(day, new LinearLayout.LayoutParams(Ui.dp(this, 64),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                String what = Weather.icon(report.codes[i]) + "  "
                        + Weather.describe(report.codes[i])
                        + (report.rain[i] >= 20 ? "  •  " + report.rain[i] + "% rain" : "");
                row.addView(Ui.text(this, what, 14, Ui.SUB), new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Ui.text(this, Weather.deg(report.hi[i]) + " / "
                        + Weather.deg(report.lo[i]), 16, Ui.ACCENT));
                root.addView(row, Ui.full(this, 8));
            }
        }

        Button change = Ui.button(this, Weather.hasCity(this) ? "Change city" : "Pick a city",
                true);
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askCity();
            }
        });
        root.addView(change, Ui.full(this, 18));

        final Ui.Row units = Ui.row(this, "Units", Weather.celsius(this) ? "Celsius, km/h"
                : "Fahrenheit, mph");
        units.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.putInt(WeatherActivity.this, "wxUnit",
                        Weather.celsius(WeatherActivity.this) ? 0 : 1);
                if (Weather.hasCity(WeatherActivity.this)) load();
                else build();
            }
        });
        root.addView(units.view, Ui.full(this, 10));

        final Ui.Row home = Ui.row(this, "Show on the home screen",
                Prefs.getInt(this, "wxHome", 1) == 1 ? "On" : "Off");
        home.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int now = Prefs.getInt(WeatherActivity.this, "wxHome", 1) == 1 ? 0 : 1;
                Prefs.putInt(WeatherActivity.this, "wxHome", now);
                home.value.setText(now == 1 ? "On" : "Off");
            }
        });
        root.addView(home.view, Ui.full(this, 10));

        root.addView(Ui.text(this, "Weather data by Open-Meteo.com. The app sends only the "
                + "coordinates of the city you picked; it never uses your phone's location.",
                12, Ui.SUB), Ui.full(this, 14));
    }

    private void askCity() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("City name, like Chicago");
        LinearLayout wrap = new LinearLayout(this);
        int pad = Ui.dp(this, 20);
        wrap.setPadding(pad, Ui.dp(this, 8), pad, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Which city?")
                .setView(wrap)
                .setPositiveButton("Search", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        final String q = input.getText().toString().trim();
                        if (q.isEmpty()) return;
                        Toast.makeText(WeatherActivity.this, "Searching...",
                                Toast.LENGTH_SHORT).show();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final List<String[]> found = Weather.search(q);
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isFinishing()) pickCity(found);
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickCity(final List<String[]> found) {
        if (found.isEmpty()) {
            Toast.makeText(this, MarketData.netOff
                    ? "Internet is switched off for this app in Settings."
                    : "No match. Check the spelling and your internet.", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        String[] labels = new String[found.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = found.get(i)[0];
        new AlertDialog.Builder(this)
                .setTitle("Pick the right one")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String[] p = found.get(which);
                        Weather.setCity(WeatherActivity.this, p[0], p[1], p[2]);
                        load();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
