package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Brain gym home: level, streak, daily challenge, badges, and every test. */
public class BrainActivity extends Activity {
    private LinearLayout root;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int y = scroll.getScrollY();
        build();
        final int keep = y;
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, keep);
            }
        });
    }

    private void play(int mode) {
        startActivity(new Intent(this, GameActivity2.class).putExtra("mode", mode));
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Brain gym"), Ui.full(this, 0));

        // ----- level, progress and points -----
        int level = Progress.level(this);
        int xp = Progress.xp(this);
        int from = Progress.threshold(level);
        int to = Progress.threshold(level + 1);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.card(this, 22));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        hero.addView(Ui.heading(this, "Level " + level));
        TextView title = Ui.text(this, Progress.title(level), 30, Ui.TEXT);
        title.setTypeface(Ui.light());
        hero.addView(title);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.round(this, 0x33FFFFFF, 5));
        View fill = new View(this);
        fill.setBackground(Ui.gold(this, 5));
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        float done = to > from ? Math.max(0.02f, Math.min(1f, (xp - from) / (float) (to - from)))
                : 1f;
        track.addView(fill, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, done));
        track.addView(new View(this), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f - done));
        bar.addView(track, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 10));
        bp.topMargin = Ui.dp(this, 10);
        hero.addView(bar, bp);
        hero.addView(Ui.text(this, (to - xp) + " points to level " + (level + 1) + ": "
                + Progress.title(level + 1), 13, Ui.SUB), Ui.full(this, 6));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        int streak = Progress.streak(this);
        stats.addView(stat(String.valueOf(Scores.points(this)), "Knowledge Points"), third());
        stats.addView(stat(streak + (streak == 1 ? " day" : " days"), "streak"), third());
        stats.addView(stat(String.valueOf(Prefs.getInt(this, "plays", 0)), "tests done"),
                third());
        hero.addView(stats, Ui.full(this, 14));

        if (streak > 0 && !Progress.playedToday(this)) {
            hero.addView(Ui.text(this, "Play any test today to keep your " + streak
                    + "-day streak and earn +" + Math.min(50, (streak + 1) * 5) + ".", 13,
                    Ui.ACCENT), Ui.full(this, 10));
        } else if (streak == 0) {
            hero.addView(Ui.text(this, "Play one test a day to build a streak. The bonus "
                    + "grows by 5 points each day, up to +50.", 13, Ui.SUB), Ui.full(this, 10));
        }

        Button tree = Ui.button(this, "Skill tree: unlock games, lessons and perks", true);
        tree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BrainActivity.this, TreeActivity.class));
            }
        });
        hero.addView(tree, Ui.full(this, 14));
        Button walletBtn = Ui.button(this, "Wallet: " + Wallet.gold(this) + " Gold, "
                + Wallet.coupons(this) + " coupons", false);
        walletBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BrainActivity.this, WalletActivity.class));
            }
        });
        hero.addView(walletBtn, Ui.full(this, 10));

        Button spend = Ui.button(this, "Vault facts and lessons", false);
        spend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BrainActivity.this, LearnActivity.class));
            }
        });
        hero.addView(spend, Ui.full(this, 10));
        root.addView(hero, Ui.full(this, 14));

        // ----- daily challenge -----
        final int challenge = Progress.challengeMode();
        boolean challengeDone = Progress.challengeDone(this);
        LinearLayout daily = new LinearLayout(this);
        daily.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable dg = Ui.card(this, 20);
        if (!challengeDone) dg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        daily.setBackground(Ui.ripple(this, dg, 20));
        daily.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        daily.addView(Ui.heading(this, "Daily challenge"));
        TextView dt = Ui.text(this, Games.TITLES[challenge], 20, Ui.TEXT);
        dt.setTypeface(Ui.medium());
        daily.addView(dt);
        daily.addView(Ui.text(this, challengeDone ? "Done for today. A new one arrives "
                + "tomorrow." : "Double points on your first go today. Tap to play.", 13,
                challengeDone ? Ui.SUB : Ui.ACCENT));
        daily.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(challenge);
            }
        });
        root.addView(daily, Ui.full(this, 12));

        // ----- crazy fact -----
        root.addView(Ui.heading(this, "Crazy fact"), Ui.full(this, 24));
        LinearLayout factCard = new LinearLayout(this);
        factCard.setOrientation(LinearLayout.VERTICAL);
        factCard.setBackground(Ui.ripple(this, Ui.card(this, 22), 22));
        factCard.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 16));
        final TextView fact = Ui.text(this, Facts.next(), 19, Ui.TEXT);
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

        // ----- tests, grouped -----
        for (int cat = 0; cat < Games.CATEGORIES.length; cat++) {
            root.addView(Ui.heading(this, Games.CATEGORIES[cat]), Ui.full(this, 26));
            for (int i = 0; i < Games.COUNT; i++) {
                if (Games.CATEGORY[i] == cat) root.addView(testCard(i), Ui.full(this, 10));
            }
        }

        // ----- badges -----
        int have = 0;
        for (int i = 0; i < Progress.BADGE_NAMES.length; i++) {
            if (Progress.hasBadge(this, i)) have++;
        }
        root.addView(Ui.heading(this, "Badges  •  " + have + " of "
                + Progress.BADGE_NAMES.length), Ui.full(this, 28));
        for (int i = 0; i < Progress.BADGE_NAMES.length; i++) {
            boolean got = Progress.hasBadge(this, i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable g = Ui.card(this, 16);
            if (got) g.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            row.setBackground(g);
            row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView n = Ui.text(this, Progress.BADGE_NAMES[i], 16, got ? Ui.TEXT : Ui.SUB);
            n.setTypeface(Ui.medium());
            left.addView(n);
            left.addView(Ui.text(this, Progress.BADGE_HOW[i], 13, Ui.SUB));
            row.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, got ? "Earned" : "+" + Progress.BADGE_REWARD[i], 14,
                    got ? Ui.ACCENT : Ui.SUB));
            root.addView(row, Ui.full(this, 8));
        }

        root.addView(Ui.text(this, "These are for fun and a quick mental warm-up. Getting "
                + "better at them mostly makes you better at these tests; it isn't proven to "
                + "raise overall brain power.", 12, Ui.SUB), Ui.full(this, 18));
    }

    private LinearLayout.LayoutParams third() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View stat(String value, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView v = Ui.text(this, value, 22, Ui.ACCENT);
        v.setTypeface(Ui.medium());
        col.addView(v);
        col.addView(Ui.text(this, label, 12, Ui.SUB));
        return col;
    }

    private View testCard(final int mode) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.ripple(this, Ui.card(this, 20), 20));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(this, 48),
                Ui.dp(this, 48));
        bp.rightMargin = Ui.dp(this, 14);
        card.addView(Ui.badge(this, Games.ICONS[mode], 48), bp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, Games.TITLES[mode], 18, Ui.TEXT);
        t.setTypeface(Ui.medium());
        left.addView(t);
        left.addView(Ui.text(this, Games.HINTS[mode], 14, Ui.SUB));
        left.addView(Ui.text(this, Scores.bestText(this, mode), 13, Ui.ACCENT),
                Ui.full(this, 6));
        card.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView board = Ui.text(this, "Top 5", 13, Ui.TEXT);
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
                play(mode);
            }
        });
        return card;
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
                .setTitle(Games.TITLES[mode] + ": high scores")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }
}
