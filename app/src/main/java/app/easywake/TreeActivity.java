package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The skill tree screen: a drawn tree to tap, with the chosen node's details beneath it. */
public class TreeActivity extends Activity {
    private TextView kp;
    private TextView sub;
    private TreeView tree;
    private LinearLayout detail;
    private ScrollView scroll;
    private Tree.Node current;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Skill tree"), Ui.full(this, 0));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.card(this, 22));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        kp = Ui.text(this, "", 24, Ui.ACCENT);
        kp.setTypeface(Ui.light());
        hero.addView(kp);
        sub = Ui.text(this, "", 13, Ui.SUB);
        hero.addView(sub, Ui.full(this, 2));
        Button wallet = Ui.button(this, "Wallet: Gold and Fun Coupons", false);
        wallet.setMinHeight(Ui.dp(this, 44));
        wallet.setMinimumHeight(Ui.dp(this, 44));
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TreeActivity.this, WalletActivity.class));
            }
        });
        hero.addView(wallet, Ui.full(this, 10));
        root.addView(hero, Ui.full(this, 14));

        root.addView(Ui.text(this, "Tap a node. Gold is unlocked, a bright outline means you "
                + "can afford it now, dim is locked, dashed is coming later.", 12, Ui.SUB),
                Ui.full(this, 10));

        tree = new TreeView(this);
        tree.setBackground(Ui.card(this, 22));
        tree.listener = new TreeView.Listener() {
            @Override
            public void onNode(Tree.Node node) {
                current = node;
                paintDetail(true);
            }
        };
        root.addView(tree, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 880)));
        ((LinearLayout.LayoutParams) tree.getLayoutParams()).topMargin = Ui.dp(this, 8);

        detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        root.addView(detail, Ui.full(this, 12));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        paintHero();
        tree.refresh();
        paintDetail(false);
    }

    private void paintHero() {
        int level = Progress.level(this);
        kp.setText(Wallet.kp(this) + " Knowledge Points");
        sub.setText("Level " + level + ": " + Progress.title(level)
                + ". Earn points and levels in the Brain gym, then spend them here.");
    }

    /** Fills the panel under the tree for the selected node. */
    private void paintDetail(boolean scrollTo) {
        detail.removeAllViews();
        if (current == null) {
            TextView hint = Ui.text(this, "Pick a node on the tree to see what it unlocks.",
                    14, Ui.SUB);
            hint.setBackground(Ui.card(this, 18));
            hint.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            detail.addView(hint, Ui.full(this, 0));
            return;
        }
        final Tree.Node n = current;
        final boolean owned = Tree.owned(this, n.id);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = Ui.card(this, 20);
        if (owned) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        card.setBackground(bg);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));

        card.addView(Ui.heading(this, Tree.BRANCHES[n.branch]));
        TextView name = Ui.text(this, n.name, 21, Ui.TEXT);
        name.setTypeface(Ui.medium());
        card.addView(name);
        card.addView(Ui.text(this, n.desc, 15, Ui.SUB), Ui.full(this, 2));

        if (n.kind == Tree.SOON) {
            card.addView(Ui.text(this, "Coming in a future update.", 14, Ui.SUB),
                    Ui.full(this, 10));
        } else if (owned) {
            if (n.kind == Tree.LESSON) {
                TextView body = Ui.text(this, n.body, 15, Ui.TEXT);
                body.setLineSpacing(Ui.dp(this, 3), 1f);
                card.addView(body, Ui.full(this, 12));
            } else if (n.kind == Tree.FEATURE) {
                Button open = Ui.button(this, "Open", true);
                open.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Class<?> screen = Tree.screen(n.id);
                        if (screen != null) {
                            startActivity(new Intent(TreeActivity.this, screen));
                        }
                    }
                });
                card.addView(open, Ui.full(this, 14));
            } else {
                card.addView(Ui.text(this, "Unlocked and active.", 14, Ui.ACCENT),
                        Ui.full(this, 10));
            }
        } else {
            card.addView(Ui.text(this, "Costs " + n.cost + " Knowledge Points  •  needs "
                    + "level " + n.level, 14, Ui.ACCENT), Ui.full(this, 10));
            final String block = Tree.blocker(this, n);
            if (block != null) {
                card.addView(Ui.text(this, block + ".", 14, Ui.DANGER), Ui.full(this, 4));
            } else {
                Button unlock = Ui.button(this, "Unlock for " + n.cost + " Knowledge Points",
                        true);
                unlock.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirm(n);
                    }
                });
                card.addView(unlock, Ui.full(this, 14));
            }
        }
        detail.addView(card, Ui.full(this, 0));

        if (scrollTo) {
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    scroll.smoothScrollTo(0, detail.getTop());
                }
            });
        }
    }

    private void confirm(final Tree.Node n) {
        new AlertDialog.Builder(this)
                .setTitle("Unlock " + n.name + "?")
                .setMessage("This costs " + n.cost + " Knowledge Points. You have "
                        + Wallet.kp(this) + ".")
                .setPositiveButton("Unlock", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (Tree.buy(TreeActivity.this, n)) {
                            Toast.makeText(TreeActivity.this, "Unlocked: " + n.name,
                                    Toast.LENGTH_SHORT).show();
                            paintHero();
                            tree.refresh();
                            paintDetail(false);
                        }
                    }
                })
                .setNegativeButton("Not now", null)
                .show();
    }
}
