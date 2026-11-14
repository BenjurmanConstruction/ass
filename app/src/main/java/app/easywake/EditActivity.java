package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
    private TextView repeatHint;
    private Ui.Row soundRow;
    private Ui.Row fadeRow;
    private Ui.Row vibrateRow;
    private Ui.Row snoozeRow;
    private Button previewBtn;
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
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, isNew ? "New alarm" : "Edit alarm"), Ui.full(this, 0));

        // Time picker sits on its own panel so it stays readable over any background.
        FrameLayout timeCard = new FrameLayout(this);
        timeCard.setBackground(Ui.card(this, 22));
        picker = (TimePicker) getLayoutInflater().inflate(R.layout.time_picker, timeCard, false);
        picker.setIs24HourView(DateFormat.is24HourFormat(this));
        picker.setHour(alarm.hour);
        picker.setMinute(alarm.minute);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        timeCard.addView(picker, pp);
        root.addView(timeCard, Ui.full(this, 14));

        root.addView(Ui.heading(this, "Repeat"), Ui.full(this, 22));
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView d = Ui.text(this, DAY_LETTERS[i], 16, Ui.TEXT);
            d.setGravity(Gravity.CENTER);
            d.setTypeface(Ui.medium());
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
        root.addView(days, Ui.full(this, 10));
        repeatHint = Ui.text(this, "", 13, Ui.SUB);
        root.addView(repeatHint, Ui.full(this, 8));
        paintDays();

        root.addView(Ui.heading(this, "Sound"), Ui.full(this, 22));
        soundRow = Ui.row(this, "Wake-up sound", "");
        soundRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseSound();
            }
        });
        root.addView(soundRow.view, Ui.full(this, 10));

        previewBtn = Ui.button(this, "▶  Play preview", false);
        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreview();
            }
        });
        root.addView(previewBtn, Ui.full(this, 10));

        fadeRow = Ui.row(this, "Volume fade-in", "");
        fadeRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseFade();
            }
        });
        root.addView(fadeRow.view, Ui.full(this, 10));

        root.addView(Ui.text(this, "Loudness follows your phone's alarm volume.", 13, Ui.SUB),
                Ui.full(this, 8));

        root.addView(Ui.heading(this, "Options"), Ui.full(this, 22));
        vibrateRow = Ui.row(this, "Vibrate", "");
        vibrateRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseVibrate();
            }
        });
        root.addView(vibrateRow.view, Ui.full(this, 10));

        snoozeRow = Ui.row(this, "Snooze length", "");
        snoozeRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseSnooze();
            }
        });
        root.addView(snoozeRow.view, Ui.full(this, 10));

        LinearLayout labelCard = new LinearLayout(this);
        labelCard.setOrientation(LinearLayout.VERTICAL);
        labelCard.setBackground(Ui.card(this, 18));
        labelCard.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 6));
        labelCard.addView(Ui.text(this, "Label (optional)", 13, Ui.SUB));
        labelEdit = new EditText(this);
        labelEdit.setSingleLine(true);
        labelEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        labelEdit.setTextColor(Ui.TEXT);
        labelEdit.setHintTextColor(Ui.SUB);
        labelEdit.setHint("Work, gym, ...");
        labelEdit.setText(alarm.label == null ? "" : alarm.label);
        labelCard.addView(labelEdit, Ui.full(this, 0));
        root.addView(labelCard, Ui.full(this, 10));

        Button save = Ui.button(this, "Save alarm", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        root.addView(save, Ui.full(this, 24));

        if (!isNew) {
            Button del = Ui.button(this, "Delete alarm", false);
            del.setTextColor(Ui.DANGER);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete();
                }
            });
            root.addView(del, Ui.full(this, 10));
        }

        setContentView(scroll);
        Bg.apply(this, scroll);
        paintRows();
    }

    private void paintDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = (alarm.days & (1 << i)) != 0;
            dayViews[i].setBackground(on ? Ui.gold(this, 23) : Ui.card(this, 23));
            dayViews[i].setTextColor(on ? Ui.ON_ACCENT : Ui.SUB);
        }
        repeatHint.setText(alarm.days == 0
                ? "Rings once. Tap days to repeat every week."
                : "Repeats: " + Ui.daysText(alarm.days));
    }

    private void paintRows() {
        soundRow.value.setText(alarm.soundText());
        fadeRow.value.setText(Ui.fadeText(alarm.fadeSec));
        vibrateRow.value.setText(Ui.vibrateText(alarm.vibrateMode));
        snoozeRow.value.setText(alarm.snoozeMin + " minutes");
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
                            paintRows();
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
        paintRows();
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
        for (int i = 0; i < items.length; i++) {
            items[i] = FADE_OPTIONS[i] == 0 ? "Off (full volume right away)"
                    : Ui.fadeText(FADE_OPTIONS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Volume fade-in")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        alarm.fadeSec = FADE_OPTIONS[which];
                        paintRows();
                    }
                })
                .show();
    }

    private void chooseVibrate() {
        final String[] items = {
                "Off",
                "After the fade-in (gentler)",
                "From the start, while the alarm rings"
        };
        new AlertDialog.Builder(this)
                .setTitle("Vibrate")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        alarm.vibrateMode = which;
                        paintRows();
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
                        paintRows();
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
            previewBtn.setText("■  Stop preview");
        } else {
            Toast.makeText(this, "Couldn't play that sound.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        preview.stop();
        if (previewBtn != null) previewBtn.setText("▶  Play preview");
    }

    private void save() {
        stopPreview();
        alarm.hour = picker.getHour();
        alarm.minute = picker.getMinute();
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
