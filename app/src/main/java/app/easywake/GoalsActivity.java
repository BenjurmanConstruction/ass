package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Goal maker: set a goal, nudge its progress along, and collect a reward for finishing. */
public class GoalsActivity extends Activity {
    private static final int REWARD = 15;
    private static final int REWARDS_PER_DAY = 2;

    static final class Goal {
        String title = "";
        String note = "";
        int progress;
        boolean done;
        /** Due date as a time in ms, or 0 for none. */
        long due;
    }

    static List<Goal> load(Context c) {
        List<Goal> out = new ArrayList<>();
        String raw = Prefs.getString(c, "goals");
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Goal g = new Goal();
                g.title = o.optString("t", "");
                g.note = o.optString("n", "");
                g.progress = o.optInt("p", 0);
                g.done = o.optBoolean("d", false);
                g.due = o.optLong("due", 0);
                out.add(g);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    static void save(Context c, List<Goal> goals) {
        JSONArray arr = new JSONArray();
        for (Goal g : goals) {
            try {
                JSONObject o = new JSONObject();
                o.put("t", g.title);
                o.put("n", g.note);
                o.put("p", g.progress);
                o.put("d", g.done);
                o.put("due", g.due);
                arr.put(o);
            } catch (Exception e) {
                // Skip a goal that can't be written.
            }
        }
        Prefs.putString(c, "goals", arr.toString());
    }

    private LinearLayout list;
    private List<Goal> goals = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "l_goals")) {
            Toast.makeText(this, "Unlock the goal maker in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.topBar(this, "Goals"), Ui.full(this, 0));

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

        Button add = Ui.button(this, "+  New goal", true);
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
        goals = load(this);
        list.removeAllViews();
        if (goals.isEmpty()) {
            TextView empty = Ui.text(this, "No goals yet.\nSet one, move the bar as you make "
                    + "progress, and earn " + REWARD + " Knowledge Points when you finish.",
                    15, Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 50), 0, 0);
            list.addView(empty, Ui.full(this, 0));
            return;
        }
        boolean header = false;
        for (final Goal g : goals) {
            if (!g.done) list.addView(card(g), Ui.full(this, 10));
        }
        for (final Goal g : goals) {
            if (!g.done) continue;
            if (!header) {
                list.addView(Ui.heading(this, "Finished"), Ui.full(this, 24));
                header = true;
            }
            list.addView(card(g), Ui.full(this, 10));
        }
    }

    private View card(final Goal g) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
        if (g.done) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        card.setBackground(Ui.ripple(this, bg, 18));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));

        TextView t = Ui.text(this, g.title, 17, g.done ? Ui.SUB : Ui.TEXT);
        t.setTypeface(Ui.medium());
        card.addView(t);
        if (!g.note.isEmpty()) card.addView(Ui.text(this, g.note, 14, Ui.SUB));
        if (g.due > 0) {
            boolean late = !g.done && g.due < System.currentTimeMillis() - 86400000L;
            card.addView(Ui.text(this, (late ? "Was due " : "Due ")
                    + DateFormat.getMediumDateFormat(this).format(new Date(g.due)), 13,
                    late ? Ui.DANGER : Ui.SUB), Ui.full(this, 2));
        }

        if (g.done) {
            card.addView(Ui.text(this, "Done", 14, Ui.ACCENT), Ui.full(this, 6));
        } else {
            FrameLayout bar = new FrameLayout(this);
            bar.setBackground(Ui.round(this, 0x33FFFFFF, 5));
            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            float done = Math.max(0.02f, g.progress / 100f);
            View fill = new View(this);
            fill.setBackground(Ui.gold(this, 5));
            track.addView(fill, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, done));
            track.addView(new View(this), new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f - done));
            bar.addView(track, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 10));
            bp.topMargin = Ui.dp(this, 10);
            card.addView(bar, bp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.text(this, g.progress + "%", 14, Ui.ACCENT),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f));
            Button step = small("+10%");
            step.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    g.progress = Math.min(100, g.progress + 10);
                    if (g.progress >= 100) finish(g);
                    save(GoalsActivity.this, goals);
                    paint();
                }
            });
            row.addView(step, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
            Button fin = small("Done");
            fin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(g);
                    save(GoalsActivity.this, goals);
                    paint();
                }
            });
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
            fp.leftMargin = Ui.dp(this, 8);
            row.addView(fin, fp);
            card.addView(row, Ui.full(this, 8));
        }

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(g);
            }
        });
        return card;
    }

    private Button small(String s) {
        Button b = Ui.button(this, s, false);
        b.setTextSize(14);
        b.setMinHeight(Ui.dp(this, 40));
        b.setMinimumHeight(Ui.dp(this, 40));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        return b;
    }

    /** Marks a goal finished and pays the reward, up to a couple of times a day. */
    private void finish(Goal g) {
        if (g.done) return;
        g.done = true;
        g.progress = 100;
        int today = Progress.today();
        int used = Prefs.getInt(this, "goalRewardDay", 0) == today
                ? Prefs.getInt(this, "goalRewardCount", 0) : 0;
        if (used < REWARDS_PER_DAY) {
            Prefs.putInt(this, "goalRewardDay", today);
            Prefs.putInt(this, "goalRewardCount", used + 1);
            Scores.addPoints(this, REWARD);
            Toast.makeText(this, "Goal finished. +" + REWARD + " Knowledge Points",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Goal finished. (Goal rewards are limited to "
                    + REWARDS_PER_DAY + " a day.)", Toast.LENGTH_LONG).show();
        }
    }

    private void edit(final Goal existing) {
        final Goal g = existing == null ? new Goal() : existing;
        final long[] due = {g.due};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setHint("The goal, like \"Read 12 books\"");
        title.setText(g.title);
        box.addView(title);
        final EditText note = new EditText(this);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setHint("Why, or the first step (optional)");
        note.setText(g.note);
        box.addView(note);
        final Button date = new Button(this);
        date.setAllCaps(false);
        date.setText(due[0] > 0 ? "Due " + DateFormat.getMediumDateFormat(this)
                .format(new Date(due[0])) : "Add a due date (optional)");
        date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar cal = Calendar.getInstance();
                if (due[0] > 0) cal.setTimeInMillis(due[0]);
                new DatePickerDialog(GoalsActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker p, int y, int m, int d) {
                                Calendar c = Calendar.getInstance();
                                c.set(y, m, d, 12, 0, 0);
                                due[0] = c.getTimeInMillis();
                                date.setText("Due " + DateFormat.getMediumDateFormat(
                                        GoalsActivity.this).format(new Date(due[0])));
                            }
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        box.addView(date);

        AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New goal" : "Edit goal")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String t = title.getText().toString().trim();
                        if (t.isEmpty()) return;
                        g.title = t;
                        g.note = note.getText().toString().trim();
                        g.due = due[0];
                        if (existing == null) goals.add(g);
                        save(GoalsActivity.this, goals);
                        paint();
                    }
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            dlg.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    goals.remove(existing);
                    save(GoalsActivity.this, goals);
                    paint();
                }
            });
        }
        dlg.show();
    }
}
