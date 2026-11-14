package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Set a price on any ticker and get a notification when it gets there. */
public class AlertsActivity extends Activity {
    private LinearLayout list;
    private Button checkBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "m_alerts")) {
            Toast.makeText(this, "Unlock Price alerts in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.topBar(this, "Price alerts"), Ui.full(this, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = Ui.dp(this, 14);
        root.addView(scroll, sp);

        checkBtn = Ui.button(this, "Check prices now", false);
        checkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkNow();
            }
        });
        root.addView(checkBtn, Ui.full(this, 12));
        Button add = Ui.button(this, "+  New alert", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                add();
            }
        });
        root.addView(add, Ui.full(this, 10));

        setContentView(root);
        Bg.apply(this, root);
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Alerts.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) paint();
    }

    private void paint() {
        final List<Alerts.Item> items = Alerts.load(this);
        list.removeAllViews();
        list.addView(Ui.text(this, "The app checks about every 15 minutes in the "
                + "background while your phone has internet. Android decides the exact "
                + "timing and may wait longer to save battery, so treat these as a "
                + "heads-up, not a guarantee. Prices can be delayed. An alert switches "
                + "off once it's reached.", 13, Ui.SUB), Ui.full(this, 0));
        if (MarketData.netOff) {
            list.addView(Ui.text(this, "Internet is switched off in Settings, so alerts "
                    + "can't be checked.", 14, Ui.DANGER), Ui.full(this, 10));
        }
        if (items.isEmpty()) {
            list.addView(Ui.text(this, "No alerts yet. Tap New alert to add one.", 15,
                    Ui.TEXT), Ui.full(this, 18));
        }
        for (final Alerts.Item it : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
            if (it.on) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            card.setBackground(Ui.ripple(this, bg, 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView title = Ui.text(this, Alerts.describe(it), 17, it.on ? Ui.TEXT : Ui.SUB);
            title.setTypeface(Ui.medium());
            card.addView(title);
            String status = it.on ? "Waiting" : "Off";
            if (!it.note.isEmpty()) status += "  •  " + it.note;
            card.addView(Ui.text(this, status, 13, it.on ? Ui.ACCENT : Ui.SUB),
                    Ui.full(this, 4));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    options(it.id);
                }
            });
            list.addView(card, Ui.full(this, 10));
        }
    }

    private void options(final int id) {
        final List<Alerts.Item> items = Alerts.load(this);
        Alerts.Item found = null;
        for (Alerts.Item it : items) {
            if (it.id == id) found = it;
        }
        if (found == null) return;
        final Alerts.Item it = found;
        new AlertDialog.Builder(this)
                .setTitle(Alerts.describe(it))
                .setItems(new String[]{it.on ? "Switch off" : "Switch back on",
                        "Open the chart", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            it.on = !it.on;
                            if (it.on) it.note = "";
                            Alerts.save(AlertsActivity.this, items);
                        } else if (which == 1) {
                            startActivity(new android.content.Intent(AlertsActivity.this,
                                    ChartActivity.class).putExtra("symbol", it.symbol)
                                    .putExtra("name", it.symbol));
                        } else {
                            items.remove(it);
                            Alerts.save(AlertsActivity.this, items);
                        }
                        paint();
                    }
                })
                .show();
    }

    private void add() {
        if (Alerts.load(this).size() >= Alerts.MAX) {
            Toast.makeText(this, "That's the limit of " + Alerts.MAX + " alerts. Delete one "
                    + "first.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText ticker = new EditText(this);
        ticker.setSingleLine(true);
        ticker.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        ticker.setHint("Ticker, like AAPL or BTC-USD");
        box.addView(ticker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final EditText price = new EditText(this);
        price.setSingleLine(true);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setHint("Target price");
        box.addView(price, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        DialogInterface.OnClickListener done = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                String sym = MarketData.clean(ticker.getText().toString());
                double target;
                try {
                    target = Double.parseDouble(price.getText().toString().trim());
                } catch (Exception e) {
                    target = 0;
                }
                if (sym == null || target <= 0) {
                    Toast.makeText(AlertsActivity.this, "Enter a ticker and a price above "
                            + "zero.", Toast.LENGTH_LONG).show();
                    return;
                }
                Alerts.Item it = new Alerts.Item();
                it.id = Alerts.newId(AlertsActivity.this);
                it.symbol = sym;
                it.price = target;
                it.above = which == DialogInterface.BUTTON_POSITIVE;
                List<Alerts.Item> items = Alerts.load(AlertsActivity.this);
                items.add(it);
                Alerts.save(AlertsActivity.this, items);
                paint();
            }
        };
        new AlertDialog.Builder(this)
                .setTitle("New alert")
                .setMessage("Tell me when the price...")
                .setView(box)
                .setPositiveButton("Rises to it", done)
                .setNegativeButton("Falls to it", done)
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void checkNow() {
        if (MarketData.netOff) {
            Toast.makeText(this, "Internet is switched off in Settings.", Toast.LENGTH_LONG)
                    .show();
            return;
        }
        checkBtn.setEnabled(false);
        checkBtn.setText("Checking...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                int r;
                try {
                    r = Alerts.check(getApplicationContext());
                } catch (Exception e) {
                    r = -1;
                }
                final int hits = r;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        checkBtn.setEnabled(true);
                        checkBtn.setText("Check prices now");
                        Toast.makeText(AlertsActivity.this, hits < 0
                                ? "Couldn't load prices. Check your internet."
                                : (hits == 0 ? "Checked. Nothing reached yet."
                                : hits + " alert" + (hits == 1 ? "" : "s") + " reached."),
                                Toast.LENGTH_LONG).show();
                        paint();
                    }
                });
            }
        }).start();
    }
}
