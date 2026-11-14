package app.easywake;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Pick the accent color and a night mode for late hours. */
public class ThemeActivity extends Activity {
    private static final String[] NIGHT_HINTS = {
            "Normal brightness and colors.",
            "Softer text and a dimmer screen inside the app.",
            "Dim red text and a very dim screen. Easiest on eyes in a dark room."
    };

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

        root.addView(Ui.topBar(this, "Colors & night mode"), Ui.full(this, 0));

        // Small sample so the effect is visible right away.
        LinearLayout sample = new LinearLayout(this);
        sample.setOrientation(LinearLayout.VERTICAL);
        sample.setBackground(Ui.card(this, 22));
        sample.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 18));
        TextView time = Ui.text(this, Ui.timeText(this, 6, 30), 40, Ui.TEXT);
        time.setTypeface(Ui.light());
        sample.addView(time);
        sample.addView(Ui.text(this, "Weekdays  •  Soft chimes", 14, Ui.SUB));
        Button demo = Ui.button(this, "Sample button", true);
        demo.setClickable(false);
        sample.addView(demo, Ui.full(this, 14));
        root.addView(sample, Ui.full(this, 14));

        root.addView(Ui.heading(this, "Accent color"), Ui.full(this, 24));
        int current = Ui.accentIndex(this);
        LinearLayout row = null;
        for (int i = 0; i < Ui.ACCENT_NAMES.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(row, Ui.full(this, 12));
            }
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            View dot = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Ui.ACCENTS[i][1], Ui.ACCENTS[i][2]});
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(Ui.dp(this, i == current ? 3 : 1),
                    i == current ? 0xFFFFFFFF : Ui.STROKE);
            dot.setBackground(g);
            cell.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));

            TextView name = Ui.text(this, Ui.ACCENT_NAMES[i], 11,
                    i == current ? Ui.TEXT : Ui.SUB);
            name.setGravity(Gravity.CENTER);
            cell.addView(name, Ui.full(this, 5));

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.putInt(ThemeActivity.this, "accent", idx);
                    Ui.loadTheme(ThemeActivity.this);
                    recreate();
                }
            });
            row.addView(cell, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        root.addView(Ui.heading(this, "Night mode"), Ui.full(this, 26));
        int night = Ui.nightLevel(this);
        for (int i = 0; i < Ui.NIGHT_NAMES.length; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable d = Ui.card(this, 18);
            if (i == night) d.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            card.setBackground(Ui.ripple(this, d, 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView t = Ui.text(this, Ui.NIGHT_NAMES[i], 17, Ui.TEXT);
            t.setTypeface(Ui.medium());
            card.addView(t);
            card.addView(Ui.text(this, NIGHT_HINTS[i], 13, Ui.SUB));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.putInt(ThemeActivity.this, "night", idx);
                    Ui.loadTheme(ThemeActivity.this);
                    recreate();
                }
            });
            root.addView(card, Ui.full(this, 8));
        }

        root.addView(Ui.text(this, "Night mode only dims this app, not the rest of the phone. "
                + "The ringing alarm screen follows it too.", 12, Ui.SUB), Ui.full(this, 12));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }
}
