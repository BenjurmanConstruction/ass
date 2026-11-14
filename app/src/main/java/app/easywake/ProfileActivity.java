package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Your profile: what you've been doing in the app, and what it says about you. */
public class ProfileActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private static String n(int v) {
        return String.format(Locale.US, "%,d", v);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, 18));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        return card;
    }

    private void line(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(Ui.text(this, label, 14, Ui.SUB), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.text(this, value, 14, Ui.TEXT);
        v.setTypeface(Ui.medium());
        row.addView(v);
        card.addView(row, Ui.full(this, 5));
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "My profile"), Ui.full(this, 0));

        // ----- who you are -----
        String name = Prefs.getString(this, "playerName");
        int level = Progress.level(this);
        LinearLayout who = card();
        TextView nm = Ui.text(this, name == null || name.isEmpty() ? "Player" : name, 26,
                Ui.TEXT);
        nm.setTypeface(Ui.medium());
        who.addView(nm);
        who.addView(Ui.text(this, "Level " + level + "  •  " + Progress.title(level), 15,
                Ui.ACCENT));
        line(who, "Knowledge Points", n(Scores.points(this)));
        line(who, "Fun Coupons", n(Wallet.coupons(this)));
        line(who, "Gold", n(Wallet.gold(this)));
        line(who, "Day streak", n(Progress.streak(this)));
        line(who, "Skill tree unlocks", n(MarketData.list(this, "treeOwned", "").size()));
        root.addView(who, Ui.full(this, 14));

        // ----- the verdict -----
        int brain7 = Stats.span(this, Stats.BRAIN, 0, 6);
        int gamble7 = Stats.span(this, Stats.GAMBLE, 0, 6);
        LinearLayout verdict = card();
        android.graphics.drawable.GradientDrawable vb = Ui.card(this, 18);
        vb.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        verdict.setBackground(vb);
        verdict.addView(Ui.heading(this, "This week's verdict"));
        String[] v = verdict(brain7, gamble7);
        TextView vt = Ui.text(this, v[0], 22, Ui.ACCENT);
        vt.setTypeface(Ui.medium());
        verdict.addView(vt, Ui.full(this, 4));
        verdict.addView(Ui.text(this, v[1], 14, Ui.TEXT), Ui.full(this, 4));
        verdict.addView(Ui.text(this, brain7 + " brain sessions and " + n(gamble7)
                + " bets in the last 7 days.", 13, Ui.SUB), Ui.full(this, 6));
        root.addView(verdict, Ui.full(this, 12));

        // ----- last two weeks -----
        root.addView(Ui.heading(this, "Last 14 days"), Ui.full(this, 22));
        LinearLayout chart = card();
        chart.addView(new DaysView(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 120)));
        chart.addView(Ui.text(this, "Gold bars: brain sessions. Red bars: bets, where 10 "
                + "bets count the same as one session. Today is on the right.", 12, Ui.SUB),
                Ui.full(this, 8));
        root.addView(chart, Ui.full(this, 10));

        // ----- are you getting smarter -----
        root.addView(Ui.heading(this, "Are you getting smarter?"), Ui.full(this, 22));
        LinearLayout smart = card();
        int lastWeek = Stats.span(this, Stats.BRAIN, 7, 13);
        int bests7 = Stats.span(this, Stats.BESTS, 0, 6);
        int pts7 = Stats.span(this, Stats.POINTS, 0, 6);
        int ptsLast = Stats.span(this, Stats.POINTS, 7, 13);
        int avg7 = brain7 == 0 ? 0 : Math.round(pts7 / (float) brain7);
        int avgLast = lastWeek == 0 ? 0 : Math.round(ptsLast / (float) lastWeek);
        smart.addView(Ui.text(this, smarter(brain7, lastWeek, bests7, avg7, avgLast), 15,
                Ui.TEXT));
        line(smart, "Tests this week / last week", brain7 + " / " + lastWeek);
        line(smart, "New personal bests this week", n(bests7));
        line(smart, "Points per test this week / last", avg7 + " / " + avgLast);
        int asked = Stats.get(this, "quizAsked");
        if (asked > 0) {
            line(smart, "Quiz answers right, all time", Stats.get(this, "quizRight") * 100
                    / asked + "% of " + n(asked));
        }
        root.addView(smart, Ui.full(this, 10));

        // ----- brain totals -----
        root.addView(Ui.heading(this, "Brain gym"), Ui.full(this, 22));
        LinearLayout brain = card();
        line(brain, "Tests finished", n(Stats.get(this, "tests")));
        line(brain, "Personal bests set", n(Stats.get(this, "bests")));
        line(brain, "Points earned from tests", n(Stats.get(this, "testPoints")));
        int favorite = -1;
        int favoriteCount = 0;
        for (int i = 0; i < Games.TITLES.length; i++) {
            int cnt = Stats.get(this, "test" + i);
            if (cnt > favoriteCount) {
                favoriteCount = cnt;
                favorite = i;
            }
        }
        if (favorite >= 0) {
            line(brain, "Most played", Games.TITLES[favorite] + " (" + favoriteCount + ")");
        }
        for (int i = 0; i < Games.CATEGORIES.length; i++) {
            int cnt = Stats.get(this, "cat" + i);
            if (cnt > 0) line(brain, Games.CATEGORIES[i] + " tests", n(cnt));
        }
        line(brain, "Lessons and facts unlocked", n(Stats.get(this, "lessons")));
        root.addView(brain, Ui.full(this, 10));

        // ----- gambling totals -----
        root.addView(Ui.heading(this, "Casino (play money)"), Ui.full(this, 22));
        LinearLayout casino = card();
        int in = Stats.get(this, "betIn");
        int out = Stats.get(this, "betOut");
        line(casino, "Total bets placed", n(Stats.get(this, "bets")));
        line(casino, "Coupons bet", n(in));
        line(casino, "Coupons won back", n(out));
        line(casino, "Overall", out - in == 0 ? "Even" : (out > in ? "Up " : "Down ")
                + n(Math.abs(out - in)));
        line(casino, "Biggest single win", n(Stats.get(this, "bigWin")));
        game(casino, "Slot spins", "slot");
        game(casino, "Blackjack hands", "bj");
        game(casino, "Poker hands", "poker");
        if (Stats.get(this, "bjBets") > 0) {
            line(casino, "Blackjack won / lost / tied", Stats.get(this, "bjWon") + " / "
                    + Stats.get(this, "bjLost") + " / " + Stats.get(this, "bjPush"));
        }
        if (Stats.get(this, "pokerBets") > 0) {
            line(casino, "Poker hands won", n(Stats.get(this, "pokerWon")));
        }
        root.addView(casino, Ui.full(this, 10));

        // ----- everything else -----
        root.addView(Ui.heading(this, "Everything else"), Ui.full(this, 22));
        LinearLayout rest = card();
        line(rest, "Block Drop games", n(Stats.get(this, "blocksGames")));
        line(rest, "Block Drop rows cleared", n(Stats.get(this, "blocksRows")));
        line(rest, "Glasses of water", n(Stats.get(this, "water")));
        line(rest, "Goals finished", n(Stats.get(this, "goals")));
        line(rest, "Questions asked the helper", n(Stats.get(this, "helper")));
        line(rest, "Jokes read", n(Stats.get(this, "jokes")));
        line(rest, "Facts read", n(Stats.get(this, "facts")));
        root.addView(rest, Ui.full(this, 10));

        // ----- recent activity -----
        root.addView(Ui.heading(this, "Recent activity"), Ui.full(this, 22));
        List<String[]> recent = Stats.recent(this, 40);
        LinearLayout log = card();
        if (recent.isEmpty()) {
            log.addView(Ui.text(this, "Nothing yet. Activity is recorded from this version "
                    + "on, so play a test or two and come back.", 14, Ui.SUB));
        }
        for (String[] e : recent) {
            long when = 0;
            try {
                when = Long.parseLong(e[0]);
            } catch (Exception ignored) {
            }
            CharSequence ago = DateUtils.getRelativeTimeSpanString(when,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
            log.addView(Ui.text(this, e[1], 14, Ui.TEXT), Ui.full(this, 8));
            log.addView(Ui.text(this, ago.toString(), 12, Ui.SUB));
        }
        root.addView(log, Ui.full(this, 10));
        root.addView(Ui.text(this, "All of this is stored only on your phone.", 12, Ui.SUB),
                Ui.full(this, 14));
    }

    private void game(LinearLayout card, String label, String key) {
        int bets = Stats.get(this, key + "Bets");
        if (bets == 0) return;
        int net = Stats.get(this, key + "Out") - Stats.get(this, key + "In");
        line(card, label, n(bets) + "  (" + (net == 0 ? "even" : (net > 0 ? "up " : "down ")
                + n(Math.abs(net))) + ")");
    }

    /** Title and one-line comment for the week, from brain sessions and bets. */
    private static String[] verdict(int brain, int gamble) {
        if (brain == 0 && gamble == 0) {
            return new String[]{"Ghost", "Nothing on the record this week. The brain gym "
                    + "misses you."};
        }
        // A test takes about as long as ten bets.
        float share = gamble / (float) (brain * 10 + gamble);
        if (share < 0.15f) {
            return new String[]{"Scholar", "Nearly all brain work and hardly a bet. Your "
                    + "mother would be proud."};
        }
        if (share < 0.4f) {
            return new String[]{"Well balanced", "Mostly training, with a little fun on "
                    + "the side. That's how it's meant to work."};
        }
        if (share < 0.65f) {
            return new String[]{"Weekend gambler", "About half and half. The tests pay "
                    + "for the casino, so keep them coming."};
        }
        if (share < 0.85f) {
            return new String[]{"Card shark", "More betting than thinking this week. The "
                    + "house thanks you."};
        }
        return new String[]{"Degenerate gambler", "Almost all bets. Good thing it's play "
                + "money. Go do a test and earn your next stake."};
    }

    private static String smarter(int tests, int lastWeek, int bests, int avg, int avgLast) {
        if (tests == 0 && lastWeek == 0) {
            return "Not enough to go on yet. Finish a few tests this week and this will "
                    + "start tracking you.";
        }
        if (tests == 0) return "No tests this week, so the answer is: not this week.";
        if (bests >= 3 || (lastWeek > 0 && avg > avgLast && bests >= 1)) {
            return "Yes. " + bests + " new personal best" + (bests == 1 ? "" : "s")
                    + " this week, and your scores are heading up.";
        }
        if (bests >= 1) {
            return "Looks like it. You beat your own record " + bests + " time"
                    + (bests == 1 ? "" : "s") + " this week.";
        }
        if (lastWeek > 0 && avg >= avgLast) {
            return "Holding steady. No new records this week, but your scores aren't "
                    + "slipping.";
        }
        if (lastWeek == 0) {
            return "Too early to tell. This is your first week on record, so there's "
                    + "nothing to compare with yet.";
        }
        return "Not this week. Scores dipped a little from last week. A few more "
                + "sessions usually fixes that.";
    }

    /** A bar for each of the last 14 days: brain sessions and bets side by side. */
    static final class DaysView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        DaysView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            int days = 14;
            float[] brain = new float[days];
            float[] gamble = new float[days];
            float top = 1;
            int today = Progress.today();
            for (int i = 0; i < days; i++) {
                int day = today - (days - 1 - i);
                brain[i] = Stats.day(getContext(), Stats.BRAIN, day);
                gamble[i] = Stats.day(getContext(), Stats.GAMBLE, day) / 10f;
                top = Math.max(top, Math.max(brain[i], gamble[i]));
            }
            float w = getWidth() / (float) days;
            float h = getHeight();
            float r = w * 0.12f;
            for (int i = 0; i < days; i++) {
                float x = i * w;
                paint.setColor(0x14FFFFFF);
                rect.set(x + w * 0.1f, 0, x + w * 0.9f, h);
                cv.drawRoundRect(rect, r, r, paint);
                paint.setColor(Ui.ACCENT);
                rect.set(x + w * 0.16f, h - h * brain[i] / top, x + w * 0.48f, h);
                if (brain[i] > 0) cv.drawRoundRect(rect, r, r, paint);
                paint.setColor(0xFFE57373);
                rect.set(x + w * 0.52f, h - h * gamble[i] / top, x + w * 0.84f, h);
                if (gamble[i] > 0) cv.drawRoundRect(rect, r, r, paint);
            }
        }
    }
}
