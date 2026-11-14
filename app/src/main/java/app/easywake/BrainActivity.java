package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Brain gym: a crazy fact, plus quick tests with your best scores. */
public class BrainActivity extends Activity {
    private static final String[] TITLES = {
            "Reaction time", "Speed math", "Number memory", "Color focus"
    };
    private static final String[] HINTS = {
            "Tap the moment the panel turns green. Five tries.",
            "Solve as many sums as you can in 60 seconds.",
            "Memorize a number, then type it back. It gets longer each round.",
            "Tap the color the word is printed in, not what the word says. 20 rounds."
    };

    private final TextView[] bests = new TextView[TITLES.length];
    private TextView fact;

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

        root.addView(Ui.topBar(this, "Brain gym"), Ui.full(this, 0));

        root.addView(Ui.heading(this, "Crazy fact"), Ui.full(this, 18));
        LinearLayout factCard = new LinearLayout(this);
        factCard.setOrientation(LinearLayout.VERTICAL);
        factCard.setBackground(Ui.ripple(this, Ui.card(this, 22), 22));
        factCard.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 16));
        fact = Ui.text(this, Facts.next(), 19, Ui.TEXT);
        fact.setTypeface(Ui.light());
        factCard.addView(fact);
        factCard.addView(Ui.text(this, "Tap for another", 12, Ui.ACCENT), Ui.full(this, 10));
        factCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fact.setText(Facts.next());
            }
        });
        root.addView(factCard, Ui.full(this, 10));

        root.addView(Ui.heading(this, "Tests"), Ui.full(this, 26));
        for (int i = 0; i < TITLES.length; i++) {
            final int mode = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 20), 20));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView t = Ui.text(this, TITLES[i], 18, Ui.TEXT);
            t.setTypeface(Ui.medium());
            card.addView(t);
            card.addView(Ui.text(this, HINTS[i], 14, Ui.SUB));
            bests[i] = Ui.text(this, "", 13, Ui.ACCENT);
            card.addView(bests[i], Ui.full(this, 6));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(BrainActivity.this, GameActivity.class)
                            .putExtra("mode", mode));
                }
            });
            root.addView(card, Ui.full(this, 10));
        }

        root.addView(Ui.text(this, "These are for fun and a quick mental warm-up. Getting "
                + "better at them mostly makes you better at these tests; it isn't proven to "
                + "raise overall brain power.", 12, Ui.SUB), Ui.full(this, 16));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int r = Prefs.getInt(this, "bestReaction", 0);
        bests[0].setText(r > 0 ? "Best: " + r + " ms" : "Not tried yet");
        int m = Prefs.getInt(this, "bestMath", -1);
        bests[1].setText(m >= 0 ? "Best: " + m + " correct" : "Not tried yet");
        int d = Prefs.getInt(this, "bestDigits", 0);
        bests[2].setText(d > 0 ? "Best: " + d + " digits" : "Not tried yet");
        int sc = Prefs.getInt(this, "bestStroop", -1);
        int sm = Prefs.getInt(this, "bestStroopMs", 0);
        bests[3].setText(sc >= 0 ? "Best: " + sc + " of 20, " + GameActivity.secs(sm)
                + " each" : "Not tried yet");
    }
}
