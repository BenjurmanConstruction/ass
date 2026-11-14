package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Brain gym: points, a crazy fact, the tests and their high scores. */
public class BrainActivity extends Activity {
    private final TextView[] bests = new TextView[GameActivity.TITLES.length];
    private TextView fact;
    private TextView points;

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

        // Points and where to spend them.
        LinearLayout bank = new LinearLayout(this);
        bank.setOrientation(LinearLayout.VERTICAL);
        bank.setBackground(Ui.card(this, 22));
        bank.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        bank.addView(Ui.heading(this, "Your points"));
        points = Ui.text(this, "0", 40, Ui.ACCENT);
        points.setTypeface(Ui.light());
        bank.addView(points);
        bank.addView(Ui.text(this, "Every test earns points. A new #1 earns a bonus. Spend "
                + "them on vault facts and lessons.", 13, Ui.SUB));
        Button spend = Ui.button(this, "Cash in: vault facts and lessons", true);
        spend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BrainActivity.this, LearnActivity.class));
            }
        });
        bank.addView(spend, Ui.full(this, 12));
        root.addView(bank, Ui.full(this, 14));

        root.addView(Ui.heading(this, "Crazy fact"), Ui.full(this, 24));
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
        for (int i = 0; i < GameActivity.TITLES.length; i++) {
            final int mode = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 20), 20));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(this, GameActivity.TITLES[i], 18, Ui.TEXT);
            t.setTypeface(Ui.medium());
            left.addView(t);
            left.addView(Ui.text(this, GameActivity.HINTS[i], 14, Ui.SUB));
            bests[i] = Ui.text(this, "", 13, Ui.ACCENT);
            left.addView(bests[i], Ui.full(this, 6));
            card.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView board = Ui.text(this, "Top 10", 13, Ui.TEXT);
            board.setGravity(Gravity.CENTER);
            board.setBackground(Ui.ripple(this, Ui.card(this, 16), 16));
            board.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            board.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBoard(mode);
                }
            });
            card.addView(board);

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

    private void showBoard(int mode) {
        List<Scores.Entry> list = Scores.load(this, mode);
        StringBuilder sb = new StringBuilder();
        if (list.isEmpty()) {
            sb.append("No scores yet. Play it to get on the board.");
        } else {
            for (int i = 0; i < list.size(); i++) {
                Scores.Entry e = list.get(i);
                sb.append(i + 1).append(".  ").append(e.name).append("  —  ")
                        .append(e.text).append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(GameActivity.TITLES[mode] + ": high scores")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        points.setText(String.valueOf(Scores.points(this)));
        for (int i = 0; i < bests.length; i++) {
            bests[i].setText(Scores.bestText(this, i));
        }
    }
}
