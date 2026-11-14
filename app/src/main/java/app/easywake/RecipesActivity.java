package app.easywake;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Easy dinner recipes. Tap one to open it. */
public class RecipesActivity extends Activity {
    private LinearLayout root;
    private int open = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "l_recipes")) {
            Toast.makeText(this, "Unlock Dinner recipes in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
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
        build();
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.topBar(this, "Dinner recipes"), Ui.full(this, 0));
        root.addView(Ui.text(this, "Simple dinners with short ingredient lists. Tap one to "
                + "open it.", 13, Ui.SUB), Ui.full(this, 10));

        for (int i = 0; i < Recipes.NAMES.length; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
            if (i == open) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            card.setBackground(Ui.ripple(this, bg, 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView name = Ui.text(this, Recipes.NAMES[i], 17, Ui.TEXT);
            name.setTypeface(Ui.medium());
            card.addView(name);
            card.addView(Ui.text(this, Recipes.INFO[i], 13, Ui.ACCENT), Ui.full(this, 2));

            if (i == open) {
                card.addView(Ui.heading(this, "You need"), Ui.full(this, 14));
                StringBuilder ing = new StringBuilder();
                for (String s : Recipes.INGREDIENTS[i]) {
                    if (ing.length() > 0) ing.append("\n");
                    ing.append("• ").append(s);
                }
                TextView it = Ui.text(this, ing.toString(), 15, Ui.TEXT);
                it.setLineSpacing(Ui.dp(this, 3), 1f);
                card.addView(it, Ui.full(this, 6));

                card.addView(Ui.heading(this, "Steps"), Ui.full(this, 16));
                String[] steps = Recipes.STEPS[i];
                for (int s = 0; s < steps.length; s++) {
                    TextView st = Ui.text(this, (s + 1) + ".  " + steps[s], 15, Ui.TEXT);
                    st.setLineSpacing(Ui.dp(this, 3), 1f);
                    card.addView(st, Ui.full(this, 8));
                }
            }

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open = open == idx ? -1 : idx;
                    build();
                }
            });
            root.addView(card, Ui.full(this, 10));
        }
    }
}
