package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Memos to self. A memo can also be set to show on the alarm screen. */
public class MemoActivity extends Activity {

    static final class Memo {
        String text = "";
        long when;
        /** Show this memo on the screen when an alarm rings. */
        boolean onAlarm;
    }

    static List<Memo> load(Context c) {
        List<Memo> out = new ArrayList<>();
        String raw = Prefs.getString(c, "memos");
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Memo m = new Memo();
                m.text = o.optString("t", "");
                m.when = o.optLong("w", 0);
                m.onAlarm = o.optBoolean("a", false);
                out.add(m);
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    static void save(Context c, List<Memo> memos) {
        JSONArray arr = new JSONArray();
        for (Memo m : memos) {
            try {
                JSONObject o = new JSONObject();
                o.put("t", m.text);
                o.put("w", m.when);
                o.put("a", m.onAlarm);
                arr.put(o);
            } catch (Exception e) {
                // Skip a memo that can't be written.
            }
        }
        Prefs.putString(c, "memos", arr.toString());
    }

    /** The memos marked to appear when an alarm rings, joined for display. */
    static String alarmText(Context c) {
        StringBuilder sb = new StringBuilder();
        for (Memo m : load(c)) {
            if (!m.onAlarm || m.text.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append("• ").append(m.text.trim());
        }
        return sb.toString();
    }

    private LinearLayout list;
    private List<Memo> memos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.topBar(this, "Memos"), Ui.full(this, 0));

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

        Button add = Ui.button(this, "+  New memo", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(-1);
            }
        });
        root.addView(add, Ui.full(this, 12));

        setContentView(root);
        Bg.apply(this, root);
        paint();
    }

    private void paint() {
        memos = load(this);
        list.removeAllViews();
        if (memos.isEmpty()) {
            TextView empty = Ui.text(this, "No memos yet.\nTap \"New memo\" to write one.", 16,
                    Ui.SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 60), 0, 0);
            list.addView(empty, Ui.full(this, 0));
            return;
        }
        // Newest first.
        for (int i = memos.size() - 1; i >= 0; i--) {
            final int idx = i;
            Memo m = memos.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 12));
            TextView t = Ui.text(this, m.text, 16, Ui.TEXT);
            t.setMaxLines(6);
            card.addView(t);
            String stamp = DateFormat.getMediumDateFormat(this).format(new Date(m.when)) + ", "
                    + DateFormat.getTimeFormat(this).format(new Date(m.when));
            card.addView(Ui.text(this, stamp + (m.onAlarm ? "  •  shows on alarm" : ""),
                    12, m.onAlarm ? Ui.ACCENT : Ui.SUB), Ui.full(this, 6));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    edit(idx);
                }
            });
            list.addView(card, Ui.full(this, 10));
        }
    }

    /** Opens the editor for an existing memo, or a new one when index is -1. */
    private void edit(final int index) {
        final Memo memo = index >= 0 && index < memos.size() ? memos.get(index) : new Memo();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(4);
        input.setGravity(Gravity.TOP);
        input.setHint("Write your memo");
        input.setText(memo.text);
        input.setSelection(input.getText().length());
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final CheckBox onAlarm = new CheckBox(this);
        onAlarm.setText("Show this on the alarm screen when I wake up");
        onAlarm.setChecked(memo.onAlarm);
        box.addView(onAlarm);

        AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "Edit memo" : "New memo")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) return;
                        memo.text = text;
                        memo.onAlarm = onAlarm.isChecked();
                        memo.when = System.currentTimeMillis();
                        if (index < 0) memos.add(memo);
                        save(MemoActivity.this, memos);
                        paint();
                    }
                })
                .setNegativeButton("Cancel", null);
        if (index >= 0) {
            dlg.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (index < memos.size()) memos.remove(index);
                    save(MemoActivity.this, memos);
                    paint();
                }
            });
        }
        dlg.show();
    }
}
