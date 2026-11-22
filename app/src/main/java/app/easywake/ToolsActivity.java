package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One place to open everything unlocked on the skill tree. */
public class ToolsActivity extends Activity {
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

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "My tools"), Ui.full(this, 0));
        root.addView(Ui.text(this, "Everything you can unlock on the skill tree that opens "
                + "its own screen.", 13, Ui.SUB), Ui.full(this, 10));

        // The Slot Parlor sits outside the skill tree: one unlock opens every machine.
        root.addView(Ui.heading(this, "Slot Parlor"), Ui.full(this, 22));
        LinearLayout parlor = new LinearLayout(this);
        parlor.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable pbg = Ui.card(this, 18);
        pbg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        parlor.setBackground(Ui.ripple(this, pbg, 18));
        parlor.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        TextView pname = Ui.text(this, "Slot Parlor", 17, Ui.TEXT);
        pname.setTypeface(Ui.medium());
        parlor.addView(pname);
        parlor.addView(Ui.text(this, "Five slot machines: classic reels, 1,024 ways, hold & "
                + "spin jackpots, a bonus wheel and tumbling gems.", 14, Ui.SUB));
        parlor.addView(Ui.text(this, ParlorActivity.owned(this) ? "Tap to open"
                : "One unlock for all  •  " + ParlorActivity.COST + " Knowledge Points", 13,
                Ui.ACCENT), Ui.full(this, 6));
        parlor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ToolsActivity.this, ParlorActivity.class));
            }
        });
        root.addView(parlor, Ui.full(this, 10));

        // The 3D casino floor: walk to any machine or table and play it from there.
        LinearLayout walk = new LinearLayout(this);
        walk.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable wbg = Ui.card(this, 18);
        wbg.setStroke(Ui.dp(this, 2), 0xFFE6B869);
        walk.setBackground(Ui.ripple(this, wbg, 18));
        walk.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        TextView wname = Ui.text(this, "Walk the casino (3D)", 17, Ui.TEXT);
        wname.setTypeface(Ui.medium());
        walk.addView(wname);
        walk.addView(Ui.text(this, "Walk the floor, sit down at any machine or table, and "
                + "play. Turn your phone sideways.", 14, Ui.SUB));
        walk.addView(Ui.text(this, "Free  •  games inside still need their unlocks", 13,
                Ui.ACCENT), Ui.full(this, 6));
        walk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ToolsActivity.this, CasinoActivity.class));
            }
        });
        root.addView(walk, Ui.full(this, 10));

        int lastBranch = -1;
        for (final Tree.Node n : Tree.NODES) {
            if (n.kind != Tree.FEATURE) continue;
            if (n.branch != lastBranch) {
                root.addView(Ui.heading(this, Tree.BRANCHES[n.branch]), Ui.full(this, 22));
                lastBranch = n.branch;
            }
            final boolean owned = Tree.owned(this, n.id);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
            if (owned) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            card.setBackground(Ui.ripple(this, bg, 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView name = Ui.text(this, n.name, 17, owned ? Ui.TEXT : Ui.SUB);
            name.setTypeface(Ui.medium());
            card.addView(name);
            card.addView(Ui.text(this, n.desc, 14, Ui.SUB));
            card.addView(Ui.text(this, owned ? "Tap to open" : "Locked  •  " + n.cost
                    + " Knowledge Points, level " + n.level, 13, owned ? Ui.ACCENT : Ui.SUB),
                    Ui.full(this, 6));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Class<?> screen = Tree.screen(n.id);
                    if (owned && screen != null) {
                        startActivity(new Intent(ToolsActivity.this, screen));
                    } else {
                        startActivity(new Intent(ToolsActivity.this, TreeActivity.class));
                    }
                }
            });
            root.addView(card, Ui.full(this, 10));
        }

        Button tree = Ui.button(this, "Open the skill tree", true);
        tree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ToolsActivity.this, TreeActivity.class));
            }
        });
        root.addView(tree, Ui.full(this, 22));
    }
}
