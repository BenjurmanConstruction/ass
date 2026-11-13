package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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
import android.widget.TimePicker;
import android.widget.Toast;

/** Screen for creating or changing one alarm. */
public class EditActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int[] FADE_OPTIONS = {0, 15, 30, 60, 120, 300};
    private static final int[] SNOOZE_OPTIONS = {5, 10, 15, 20, 30};
    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private Alarm alarm;
    private boolean isNew;

    private TimePicker picker;
    private final TextView[] dayViews = new TextView[7];
    private Button soundBtn;
    private Button previewBtn;
    private Button fadeBtn;
    private Button snoozeBtn;
    private CheckBox vibrateBox;
    private EditText labelEdit;

    private final SoundPlayer preview = new SoundPlayer();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        int id = getIntent().getIntExtra("id", -1);
        alarm = id == -1 ? null : Store.get(this, id);
        isNew = alarm == null;
        if (isNew) alarm = new Alarm();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, isNew ? "New alarm" : "Edit alarm", 26, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, full(0));

        picker = (TimePicker) getLayoutInflater().inflate(R.layout.time_picker, root, false);
        picker.setIs24HourView(DateFormat.is24HourFormat(this));
        picker.setHour(alarm.hour);
        picker.setMinute(alarm.minute);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(picker, pp);

        root.addView(heading("Repeat (leave all off to ring once)"), full(10));
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView d = Ui.text(this, DAY_LETTERS[i], 17, Ui.TEXT);
            d.setGravity(Gravity.CENTER);
            d.setTypeface(Typeface.DEFAULT_BOLD);
            d.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alarm.days ^= (1 << bit);
                    paintDays();
                }
            });
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f);
            int m = Ui.dp(this, 3);
            dp.setMargins(m, 0, m, 0);
            days.addView(d, dp);
            dayViews[i] = d;
        }
        root.addView(days, full(6));
        paintDays();

        root.addView(heading("Wake-up sound"), full(18));
        soundBtn = Ui.button(this, "", false);
        soundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseSound();
            }
        });
        root.addView(soundBtn, full(6));

        previewBtn = Ui.button(this, "Play preview", false);
        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreview();
            }
        });
        root.addView(previewBtn, full(8));

        TextView volNote = Ui.text(this,
                "Loudness follows your phone's alarm volume.", 13, Ui.SUB);
        root.addView(volNote, full(6));

        root.addView(heading("Volume fade-in"), full(18));
        fadeBtn = Ui.button(this, "", false);
        fadeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseFade();
            }
        });
        root.addView(fadeBtn, full(6));

        root.addView(heading("Snooze length"), full(18));
        snoozeBtn = Ui.button(this, "", false);
        snoozeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseSnooze();
            }
        });
        root.addView(snoozeBtn, full(6));

        vibrateBox = new CheckBox(this);
        vibrateBox.setText("Also vibrate (starts after the fade-in)");
        vibrateBox.setTextColor(Ui.TEXT);
        vibrateBox.setTextSize(16);
        vibrateBox.setChecked(alarm.vibrate);
        root.addView(vibrateBox, full(16));

        root.addView(heading("Label (optional)"), full(16));
        labelEdit = new EditText(this);
        labelEdit.setSingleLine(true);
        labelEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        labelEdit.setTextColor(Ui.TEXT);
        labelEdit.setHintTextColor(Ui.SUB);
        labelEdit.setHint("Work, gym, ...");
        labelEdit.setText(alarm.label == null ? "" : alarm.label);
        root.addView(labelEdit, full(2));

        Button save = Ui.button(this, "Save", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        root.addView(save, full(22));

        if (!isNew) {
            Button del = Ui.button(this, "Delete alarm", false);
            del.setTextColor(0xFFEF9A9A);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete();
                }
            });
            root.addView(del, full(10));
        }

        setContentView(scroll);
        paintButtons();
    }

    private LinearLayout.LayoutParams full(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, topDp);
        return p;
    }

    private TextView heading(String s) {
        return Ui.text(this, s, 14, Ui.SUB);
    }

    private void paintDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = (alarm.days & (1 << i)) != 0;
            dayViews[i].setBackground(Ui.round(this, on ? Ui.ACCENT : Ui.CARD, 23));
            dayViews[i].setTextColor(on ? Ui.ON_ACCENT : Ui.SUB);
        }
    }

    private void paintButtons() {
        soundBtn.setText(alarm.soundText());
        fadeBtn.setText(Ui.fadeText(alarm.fadeSec));
        snoozeBtn.setText(alarm.snoozeMin + " minutes");
    }

    private void chooseSound() {
        stopPreview();
        final String[] items = new String[ToneGen.NAMES.length + 1];
        System.arraycopy(ToneGen.NAMES, 0, items, 0, ToneGen.NAMES.length);
        items[items.length - 1] = "Pick a music file (MP3)...";
        new AlertDialog.Builder(this)
                .setTitle("Wake-up sound")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < ToneGen.NAMES.length) {
                            alarm.tone = which;
                            alarm.uri = null;
                            alarm.soundName = null;
                            paintButtons();
                        } else {
                            pickFile();
                        }
                    }
                })
                .show();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker found on this phone.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILE || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            // Keep access to the file after the phone restarts.
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            // Some file apps do not support this; the file still works until a restart.
        }
        alarm.uri = uri.toString();
        alarm.soundName = displayName(uri);
        paintButtons();
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception e) {
            name = null;
        }
        return name == null || name.isEmpty() ? "Music file" : name;
    }

    private void chooseFade() {
        final String[] items = new String[FADE_OPTIONS.length];
        for (int i = 0; i < items.length; i++) items[i] = Ui.fadeText(FADE_OPTIONS[i]);
        new AlertDialog.Builder(this)
                .setTitle("Volume fade-in")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        alarm.fadeSec = FADE_OPTIONS[which];
                        paintButtons();
                    }
                })
                .show();
    }

    private void chooseSnooze() {
        final String[] items = new String[SNOOZE_OPTIONS.length];
        for (int i = 0; i < items.length; i++) items[i] = SNOOZE_OPTIONS[i] + " minutes";
        new AlertDialog.Builder(this)
                .setTitle("Snooze length")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        alarm.snoozeMin = SNOOZE_OPTIONS[which];
                        paintButtons();
                    }
                })
                .show();
    }

    private void togglePreview() {
        if (preview.isPlaying()) {
            stopPreview();
            return;
        }
        preview.start(this, alarm, false, null);
        if (preview.isPlaying()) {
            previewBtn.setText("Stop preview");
        } else {
            Toast.makeText(this, "Couldn't play that sound.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        preview.stop();
        if (previewBtn != null) previewBtn.setText("Play preview");
    }

    private void save() {
        stopPreview();
        alarm.hour = picker.getHour();
        alarm.minute = picker.getMinute();
        alarm.vibrate = vibrateBox.isChecked();
        alarm.label = labelEdit.getText().toString().trim();
        alarm.enabled = true;
        alarm.snoozeUntil = 0;
        if (isNew) alarm.id = Store.newId(this);
        Store.put(this, alarm);
        Scheduler.rescheduleAll(this);
        Toast.makeText(this, Ui.untilText(alarm.nextTrigger(System.currentTimeMillis())),
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage("Delete this alarm?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Scheduler.cancel(EditActivity.this, alarm.id);
                        Store.delete(EditActivity.this, alarm.id);
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onPause() {
        stopPreview();
        super.onPause();
    }
}
