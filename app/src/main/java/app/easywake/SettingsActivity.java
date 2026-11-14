package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/** App settings: the internet switch, player name, and housekeeping. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Settings"), Ui.full(this, 0));

        // ----- internet -----
        root.addView(Ui.heading(this, "Internet"), Ui.full(this, 22));
        final Ui.Row net = Ui.row(this, "Allow this app to use the internet",
                MarketData.netOff ? "Off" : "On");
        net.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MarketData.netOff = !MarketData.netOff;
                Prefs.putInt(SettingsActivity.this, "netOff", MarketData.netOff ? 1 : 0);
                net.value.setText(MarketData.netOff ? "Off" : "On");
            }
        });
        root.addView(net.view, Ui.full(this, 10));
        root.addView(Ui.text(this, "When this is off, the app makes no connections at all. "
                + "Market prices, charts, ticker lookup and the helper's market answers "
                + "stop working; alarms, sleep sounds, the Brain gym and everything else "
                + "carry on as normal.", 13, Ui.SUB), Ui.full(this, 8));

        // ----- brain gym -----
        root.addView(Ui.heading(this, "Brain gym"), Ui.full(this, 26));
        String saved = Prefs.getString(this, "playerName");
        final Ui.Row name = Ui.row(this, "Name on high score lists",
                saved == null || saved.isEmpty() ? "Not set" : saved);
        name.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(SettingsActivity.this);
                input.setSingleLine(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                String cur = Prefs.getString(SettingsActivity.this, "playerName");
                if (cur != null) input.setText(cur);
                LinearLayout wrap = new LinearLayout(SettingsActivity.this);
                int p = Ui.dp(SettingsActivity.this, 20);
                wrap.setPadding(p, Ui.dp(SettingsActivity.this, 8), p, 0);
                wrap.addView(input, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Your name")
                        .setView(wrap)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                String n = input.getText().toString().trim();
                                if (n.length() > 16) n = n.substring(0, 16);
                                Prefs.putString(SettingsActivity.this, "playerName", n);
                                name.value.setText(n.isEmpty() ? "Not set" : n);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        root.addView(name.view, Ui.full(this, 10));

        // ----- look -----
        root.addView(Ui.heading(this, "Look"), Ui.full(this, 26));
        Ui.Row colors = Ui.row(this, "Colors & night mode", "");
        colors.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ThemeActivity.class));
            }
        });
        root.addView(colors.view, Ui.full(this, 10));
        Ui.Row bg = Ui.row(this, "Background studio", "");
        bg.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, BackgroundActivity.class));
            }
        });
        root.addView(bg.view, Ui.full(this, 10));

        // ----- about -----
        root.addView(Ui.heading(this, "About"), Ui.full(this, 26));
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "";
        }
        root.addView(Ui.text(this, "Easy Wake version " + version + ". No ads, no accounts, "
                + "no tracking. The internet is used only to fetch market data, and only "
                + "while the switch above is on.", 13, Ui.SUB), Ui.full(this, 8));

        Button uninstall = Ui.button(this, "Uninstall Easy Wake", false);
        uninstall.setTextColor(Ui.DANGER);
        uninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {
                        Toast.makeText(SettingsActivity.this,
                                "Hold the app icon on your home screen and tap Uninstall.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        root.addView(uninstall, Ui.full(this, 16));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }
}
