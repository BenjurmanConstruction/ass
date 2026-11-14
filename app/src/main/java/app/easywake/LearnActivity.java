package app.easywake;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Spend Brain gym points: unlock vault facts and short lessons. */
public class LearnActivity extends Activity {
    private LinearLayout root;
    private ScrollView scroll;
    private int openLesson = -1;
    private String justUnlocked;

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
        build();
    }

    private List<String> owned(String key) {
        return MarketData.list(this, key, "");
    }

    private boolean lessonOpen(int i) {
        return i < Lessons.FREE_LESSONS || owned("lessonsOwned").contains(String.valueOf(i));
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Learn"), Ui.full(this, 0));

        TextView pts = Ui.text(this, Scores.points(this) + " Knowledge Points", 22, Ui.ACCENT);
        pts.setTypeface(Ui.light());
        root.addView(pts, Ui.full(this, 12));
        root.addView(Ui.text(this, "Earn more by playing the Brain gym tests.", 13, Ui.SUB),
                Ui.full(this, 2));

        // ----- fact vault -----
        final List<String> facts = owned("vaultOwned");
        root.addView(Ui.heading(this, "Fact vault  •  " + facts.size() + " of "
                + Lessons.VAULT.length + " unlocked"), Ui.full(this, 24));

        if (justUnlocked != null) {
            LinearLayout fresh = new LinearLayout(this);
            fresh.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable g = Ui.card(this, 20);
            g.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            fresh.setBackground(g);
            fresh.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            fresh.addView(Ui.text(this, "Just unlocked", 12, Ui.ACCENT));
            TextView t = Ui.text(this, justUnlocked, 18, Ui.TEXT);
            t.setTypeface(Ui.light());
            fresh.addView(t, Ui.full(this, 4));
            root.addView(fresh, Ui.full(this, 10));
        }

        if (facts.size() < Lessons.VAULT.length) {
            Button unlock = Ui.button(this, "Unlock a vault fact  •  " + Lessons.FACT_COST
                    + " points", true);
            unlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    unlockFact();
                }
            });
            root.addView(unlock, Ui.full(this, 10));
        } else {
            root.addView(Ui.text(this, "You've unlocked the whole vault.", 14, Ui.SUB),
                    Ui.full(this, 10));
        }

        for (int i = facts.size() - 1; i >= 0; i--) {
            int idx;
            try {
                idx = Integer.parseInt(facts.get(i));
            } catch (NumberFormatException e) {
                continue;
            }
            if (idx < 0 || idx >= Lessons.VAULT.length) continue;
            if (Lessons.VAULT[idx].equals(justUnlocked)) continue;
            TextView t = Ui.text(this, Lessons.VAULT[idx], 15, Ui.TEXT);
            t.setBackground(Ui.card(this, 16));
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            root.addView(t, Ui.full(this, 8));
        }

        // ----- lessons -----
        root.addView(Ui.heading(this, "Quick lessons"), Ui.full(this, 26));
        for (int i = 0; i < Lessons.TITLES.length; i++) {
            final int idx = i;
            final boolean open = lessonOpen(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView title = Ui.text(this, Lessons.TITLES[i], 17, Ui.TEXT);
            title.setTypeface(Ui.medium());
            card.addView(title);
            if (!open) {
                card.addView(Ui.text(this, "Locked  •  " + Lessons.LESSON_COST
                        + " points to unlock", 13, Ui.ACCENT), Ui.full(this, 2));
            } else if (openLesson == i) {
                TextView body = Ui.text(this, Lessons.BODIES[i], 15, Ui.TEXT);
                body.setLineSpacing(Ui.dp(this, 3), 1f);
                card.addView(body, Ui.full(this, 8));
            } else {
                card.addView(Ui.text(this, i < Lessons.FREE_LESSONS ? "Free  •  tap to read"
                        : "Unlocked  •  tap to read", 13, Ui.SUB), Ui.full(this, 2));
            }
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (open) {
                        openLesson = openLesson == idx ? -1 : idx;
                        build();
                    } else {
                        unlockLesson(idx);
                    }
                }
            });
            root.addView(card, Ui.full(this, 8));
        }
    }

    private void announceBadges() {
        int bonus = Progress.checkLearnBadges(this);
        if (bonus > 0) {
            Toast.makeText(this, "Badge earned! +" + bonus + " points", Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void unlockFact() {
        List<String> facts = owned("vaultOwned");
        List<Integer> locked = new ArrayList<>();
        for (int i = 0; i < Lessons.VAULT.length; i++) {
            if (!facts.contains(String.valueOf(i))) locked.add(i);
        }
        if (locked.isEmpty()) return;
        if (!Scores.spend(this, Lessons.FACT_COST)) {
            Toast.makeText(this, "Not enough points yet. Play a test to earn more.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int pick = locked.get(new Random().nextInt(locked.size()));
        facts.add(String.valueOf(pick));
        MarketData.saveList(this, "vaultOwned", facts);
        announceBadges();
        Stats.add(this, "lessons", 1);
        Stats.log(this, "Unlocked a vault fact");
        justUnlocked = Lessons.VAULT[pick];
        build();
        scroll.scrollTo(0, 0);
    }

    private void unlockLesson(int idx) {
        if (!Scores.spend(this, Lessons.LESSON_COST)) {
            Toast.makeText(this, "You need " + Lessons.LESSON_COST + " points for this one. "
                    + "Play a test to earn more.", Toast.LENGTH_LONG).show();
            return;
        }
        List<String> owned = owned("lessonsOwned");
        owned.add(String.valueOf(idx));
        MarketData.saveList(this, "lessonsOwned", owned);
        Stats.add(this, "lessons", 1);
        Stats.log(this, "Unlocked a lesson");
        announceBadges();
        openLesson = idx;
        build();
    }
}
