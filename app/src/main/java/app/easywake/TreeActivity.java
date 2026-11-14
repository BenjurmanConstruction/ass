package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The skill tree screen: spend Knowledge Points to unlock lessons, games and perks. */
public class TreeActivity extends Activity {
    private LinearLayout root;
    private ScrollView scroll;
    private String openLesson;

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
        build();
    }

    private void build() {
        final int y = scroll.getScrollY();
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Skill tree"), Ui.full(this, 0));

        int level = Progress.level(this);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.card(this, 22));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        TextView kp = Ui.text(this, Wallet.kp(this) + " Knowledge Points", 26, Ui.ACCENT);
        kp.setTypeface(Ui.light());
        hero.addView(kp);
        hero.addView(Ui.text(this, "Level " + level + ": " + Progress.title(level)
                + ". Earn points and levels in the Brain gym, then spend them here.", 13,
                Ui.SUB), Ui.full(this, 4));
        Button wallet = Ui.button(this, "Wallet: Gold and Fun Coupons", false);
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TreeActivity.this, WalletActivity.class));
            }
        });
        hero.addView(wallet, Ui.full(this, 12));
        root.addView(hero, Ui.full(this, 14));

        for (int br = 0; br < Tree.BRANCHES.length; br++) {
            root.addView(Ui.heading(this, Tree.BRANCHES[br]), Ui.full(this, 26));
            root.addView(Ui.text(this, Tree.BRANCH_NOTES[br], 13, Ui.SUB), Ui.full(this, 4));
            for (Tree.Node n : Tree.NODES) {
                if (n.branch == br) root.addView(card(n), Ui.full(this, 10));
            }
        }

        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, y);
            }
        });
    }

    private View card(final Tree.Node n) {
        final boolean owned = Tree.owned(this, n.id);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.card(this, 18);
        if (owned) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        card.setBackground(Ui.ripple(this, bg, 18));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));

        TextView name = Ui.text(this, n.name, 17, n.kind == Tree.SOON ? Ui.SUB : Ui.TEXT);
        name.setTypeface(Ui.medium());
        card.addView(name);
        card.addView(Ui.text(this, n.desc, 14, Ui.SUB));

        String status;
        int color = Ui.SUB;
        if (owned) {
            color = Ui.ACCENT;
            if (n.kind == Tree.LESSON) {
                status = n.id.equals(openLesson) ? "Unlocked  •  tap to close"
                        : "Unlocked  •  tap to read";
            } else if (n.kind == Tree.FEATURE) {
                status = "Unlocked  •  tap to open";
            } else {
                status = "Unlocked  •  active";
            }
        } else if (n.kind == Tree.SOON) {
            status = "Coming in a future update";
        } else {
            String block = Tree.blocker(this, n);
            status = n.cost + " Knowledge Points  •  level " + n.level
                    + (block == null ? "  •  tap to unlock" : "\n" + block);
            if (block == null) color = Ui.ACCENT;
        }
        card.addView(Ui.text(this, status, 13, color), Ui.full(this, 6));

        if (owned && n.kind == Tree.LESSON && n.id.equals(openLesson)) {
            TextView body = Ui.text(this, n.body, 15, Ui.TEXT);
            body.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(body, Ui.full(this, 10));
        }

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(n, owned);
            }
        });
        return card;
    }

    private void tap(final Tree.Node n, boolean owned) {
        if (n.kind == Tree.SOON) {
            Toast.makeText(this, "Not built yet. It's on the list.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (owned) {
            if (n.kind == Tree.LESSON) {
                openLesson = n.id.equals(openLesson) ? null : n.id;
                build();
            } else if ("slot".equals(n.id)) {
                startActivity(new Intent(this, SlotActivity.class));
            }
            return;
        }
        String block = Tree.blocker(this, n);
        if (block != null) {
            Toast.makeText(this, block + ".", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Unlock " + n.name + "?")
                .setMessage("This costs " + n.cost + " Knowledge Points. You have "
                        + Wallet.kp(this) + ".")
                .setPositiveButton("Unlock", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (Tree.buy(TreeActivity.this, n)) {
                            if (n.kind == Tree.LESSON) openLesson = n.id;
                            Toast.makeText(TreeActivity.this, "Unlocked: " + n.name,
                                    Toast.LENGTH_SHORT).show();
                            build();
                        }
                    }
                })
                .setNegativeButton("Not now", null)
                .show();
    }
}
