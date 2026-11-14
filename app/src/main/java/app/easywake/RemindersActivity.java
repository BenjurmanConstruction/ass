package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Set up reminders that repeat daily, on weekdays, or once a week. */
public class RemindersActivity extends Activity {
    private static final String[] REPEAT_NAMES = {
            "Every day", "Weekdays", "Weekends", "Every Sunday", "Every Monday",
            "Every Tuesday", "Every Wednesday", "Every Thursday", "Every Friday",
            "Every Saturday", "Just once"
    };
    private static final int[] REPEAT_DAYS = {127, 62, 65, 1, 2, 4, 8, 16, 32, 64, 0};

    private LinearLayout list;
    private List<Reminders.Item> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "l_reminders")) {
            Toast.makeText(this, "Unlock Reminders in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.topBar(this, "Reminders"), Ui.full(this, 0));

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

        Button add = Ui.button(this, "+  New reminder", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(null);
            }
        });
        root.addView(add, Ui.full(this, 12));

        setContentView(root);
        Bg.apply(this, root);
        paint();
    }

    private void paint() {
        items = Reminders.load(this);
        list.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, "No reminders yet.\nThese arrive as a normal "
                    + "notification at the time you set, every day or once a week.", 15,
                    Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 50), 0, 0);
            list.addView(empty, Ui.full(this, 0));
            return;
        }
        for (final Reminders.Item it : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(this, it.text, 17, it.on ? Ui.TEXT : Ui.SUB);
            t.setTypeface(Ui.medium());
            left.addView(t);
            left.addView(Ui.text(this, Ui.timeText(this, it.hour, it.minute) + "  •  "
                    + Reminders.repeatText(it.days), 14, Ui.SUB));
            card.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(it.on);
            Ui.tint(sw);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean on) {
                    it.on = on;
                    Reminders.save(RemindersActivity.this, items);
                    Reminders.scheduleAll(RemindersActivity.this);
                }
            });
            card.addView(sw);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    edit(it);
                }
            });
            list.addView(card, Ui.full(this, 10));
        }
        list.addView(Ui.text(this, "Reminders need notifications allowed for this app. They "
                + "can arrive a minute or two late if the phone is in deep battery saving.",
                12, Ui.SUB), Ui.full(this, 14));
    }

    private static int repeatIndex(int days) {
        for (int i = 0; i < REPEAT_DAYS.length; i++) {
            if (REPEAT_DAYS[i] == days) return i;
        }
        return 0;
    }

    private void edit(final Reminders.Item existing) {
        final Reminders.Item it = existing == null ? new Reminders.Item() : existing;
        final int[] time = {it.hour, it.minute};
        final int[] repeat = {repeatIndex(it.days)};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText text = new EditText(this);
        text.setSingleLine(true);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setHint("Remind me to...");
        text.setText(it.text);
        box.addView(text);

        final Button timeBtn = new Button(this);
        timeBtn.setAllCaps(false);
        timeBtn.setText("At " + Ui.timeText(this, time[0], time[1]));
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(RemindersActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker p, int h, int m) {
                                time[0] = h;
                                time[1] = m;
                                timeBtn.setText("At " + Ui.timeText(RemindersActivity.this, h,
                                        m));
                            }
                        }, time[0], time[1], DateFormat.is24HourFormat(RemindersActivity.this))
                        .show();
            }
        });
        box.addView(timeBtn);

        final Button repeatBtn = new Button(this);
        repeatBtn.setAllCaps(false);
        repeatBtn.setText(REPEAT_NAMES[repeat[0]]);
        repeatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(RemindersActivity.this)
                        .setTitle("How often?")
                        .setItems(REPEAT_NAMES, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                repeat[0] = which;
                                repeatBtn.setText(REPEAT_NAMES[which]);
                            }
                        })
                        .show();
            }
        });
        box.addView(repeatBtn);

        AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New reminder" : "Edit reminder")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String t = text.getText().toString().trim();
                        if (t.isEmpty()) return;
                        it.text = t;
                        it.hour = time[0];
                        it.minute = time[1];
                        it.days = REPEAT_DAYS[repeat[0]];
                        it.on = true;
                        if (existing == null) {
                            it.id = Reminders.newId(RemindersActivity.this);
                            items.add(it);
                        }
                        Reminders.save(RemindersActivity.this, items);
                        Reminders.scheduleAll(RemindersActivity.this);
                        Toast.makeText(RemindersActivity.this, "Next reminder in "
                                + Ui.untilSpan(Reminders.next(it, System.currentTimeMillis())),
                                Toast.LENGTH_LONG).show();
                        paint();
                    }
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            dlg.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    Reminders.cancel(RemindersActivity.this, existing.id);
                    items.remove(existing);
                    Reminders.save(RemindersActivity.this, items);
                    paint();
                }
            });
        }
        dlg.show();
    }
}
