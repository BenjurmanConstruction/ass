package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Fan and noise sounds to fall asleep to. */
public class SleepActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int[] TIMER_OPTIONS = {0, 15, 30, 60, 120, 240, 480};
    private static final int CUSTOM_INDEX = NoiseGen.NAMES.length;

    private final LinearLayout[] cards = new LinearLayout[NoiseGen.NAMES.length + 1];
    private TextView customHint;
    private Button changeFile;
    private LinearLayout toneCard;
    private LinearLayout humCard;
    private Ui.Row timerRow;
    private Button playBtn;
    private TextView status;
    /** 0..3 = built-in sounds, CUSTOM_INDEX = the user's own file. */
    private int selected;
    private int minutes;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            paint();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int saved = Prefs.getInt(this, "noiseType", 0);
        selected = saved == NoiseService.TYPE_CUSTOM ? CUSTOM_INDEX : saved;
        if (selected < 0 || selected > CUSTOM_INDEX) selected = 0;
        if (selected == CUSTOM_INDEX && Prefs.getString(this, "noiseUri") == null) selected = 0;
        minutes = Prefs.getInt(this, "noiseTimer", 60);
        NoiseService.volume = Prefs.getInt(this, "noiseVol", 70) / 100f;
        NoiseService.tone = Prefs.getInt(this, "noiseTone", 40);
        NoiseService.hum = Prefs.getInt(this, "noiseHum", 35);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Sleep sounds"), Ui.full(this, 0));

        root.addView(Ui.heading(this, "Sound"), Ui.full(this, 18));
        for (int i = 0; i <= CUSTOM_INDEX; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView name = Ui.text(this,
                    i == CUSTOM_INDEX ? "My own recording" : NoiseGen.NAMES[i], 18, Ui.TEXT);
            name.setTypeface(Ui.medium());
            card.addView(name);
            TextView hint = Ui.text(this, i == CUSTOM_INDEX ? "" : NoiseGen.HINTS[i], 14,
                    Ui.SUB);
            card.addView(hint);
            if (i == CUSTOM_INDEX) customHint = hint;
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == CUSTOM_INDEX && Prefs.getString(SleepActivity.this, "noiseUri")
                            == null) {
                        pickFile();
                        return;
                    }
                    select(idx);
                }
            });
            cards[i] = card;
            root.addView(card, Ui.full(this, 8));
        }

        changeFile = Ui.button(this, "Choose a different recording", false);
        changeFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFile();
            }
        });
        root.addView(changeFile, Ui.full(this, 8));

        root.addView(Ui.heading(this, "Tune it"), Ui.full(this, 22));

        toneCard = sliderCard("Tone", "Left is deeper and softer. Right is brighter.",
                Prefs.getInt(this, "noiseTone", 40), new SliderListener() {
                    @Override
                    public void onValue(int v, boolean done) {
                        NoiseService.tone = v;
                        if (done) Prefs.putInt(SleepActivity.this, "noiseTone", v);
                    }
                });
        root.addView(toneCard, Ui.full(this, 8));

        humCard = sliderCard("Motor hum", "How much of the fan's low hum you hear.",
                Prefs.getInt(this, "noiseHum", 35), new SliderListener() {
                    @Override
                    public void onValue(int v, boolean done) {
                        NoiseService.hum = v;
                        if (done) Prefs.putInt(SleepActivity.this, "noiseHum", v);
                    }
                });
        root.addView(humCard, Ui.full(this, 8));

        root.addView(sliderCard("Loudness", "Also follows your phone's media volume.",
                Prefs.getInt(this, "noiseVol", 70), new SliderListener() {
                    @Override
                    public void onValue(int v, boolean done) {
                        NoiseService.volume = v / 100f;
                        if (done) Prefs.putInt(SleepActivity.this, "noiseVol", v);
                    }
                }), Ui.full(this, 8));

        timerRow = Ui.row(this, "Turn off after", "");
        timerRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseTimer();
            }
        });
        root.addView(timerRow.view, Ui.full(this, 8));

        status = Ui.text(this, "", 14, Ui.SUB);
        status.setGravity(Gravity.CENTER);
        root.addView(status, Ui.full(this, 20));

        playBtn = Ui.button(this, "Play", true);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (NoiseService.playingType != -1) {
                    startService(new Intent(SleepActivity.this, NoiseService.class)
                            .setAction(NoiseService.ACTION_STOP));
                } else {
                    startNoise();
                }
            }
        });
        root.addView(playBtn, Ui.full(this, 10));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    private interface SliderListener {
        void onValue(int value, boolean done);
    }

    private LinearLayout sliderCard(String title, String hint, int value,
                                    final SliderListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, 18));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 10));
        card.addView(Ui.text(this, title, 16, Ui.TEXT));
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(value);
        Ui.tint(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                listener.onValue(progress, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                listener.onValue(s.getProgress(), true);
            }
        });
        card.addView(bar, Ui.full(this, 8));
        card.addView(Ui.text(this, hint, 12, Ui.SUB), Ui.full(this, 2));
        return card;
    }

    private int typeOf(int index) {
        return index == CUSTOM_INDEX ? NoiseService.TYPE_CUSTOM : index;
    }

    private void select(int idx) {
        selected = idx;
        Prefs.putInt(this, "noiseType", typeOf(idx));
        // Switch the sound live if something is already playing.
        if (NoiseService.playingType != -1) startNoise();
        paint();
    }

    private void startNoise() {
        Intent i = new Intent(this, NoiseService.class)
                .setAction(NoiseService.ACTION_START)
                .putExtra("type", typeOf(selected))
                .putExtra("minutes", minutes);
        startForegroundService(i);
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
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            // Some file apps do not support this; the file still works until a restart.
        }
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception e) {
            name = null;
        }
        Prefs.putString(this, "noiseUri", uri.toString());
        Prefs.putString(this, "noiseName", name == null ? "My recording" : name);
        select(CUSTOM_INDEX);
    }

    private static String timerText(int min) {
        if (min <= 0) return "Never (until the alarm)";
        if (min < 60) return min + " minutes";
        int h = min / 60;
        return h + (h == 1 ? " hour" : " hours");
    }

    private void chooseTimer() {
        final String[] items = new String[TIMER_OPTIONS.length];
        for (int i = 0; i < items.length; i++) items[i] = timerText(TIMER_OPTIONS[i]);
        new AlertDialog.Builder(this)
                .setTitle("Turn off after")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        minutes = TIMER_OPTIONS[which];
                        Prefs.putInt(SleepActivity.this, "noiseTimer", minutes);
                        if (NoiseService.playingType != -1) startNoise();
                        paint();
                    }
                })
                .show();
    }

    private void paint() {
        int playing = NoiseService.playingType;
        for (int i = 0; i < cards.length; i++) {
            GradientDrawable d = Ui.card(this, 18);
            if (i == selected) d.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            cards[i].setBackground(Ui.ripple(this, d, 18));
        }
        boolean hasFile = Prefs.getString(this, "noiseUri") != null;
        customHint.setText(hasFile
                ? NoiseService.nameOf(this, NoiseService.TYPE_CUSTOM)
                : "Tap to pick a real fan recording (or any sound) from your phone");
        boolean custom = selected == CUSTOM_INDEX;
        changeFile.setVisibility(custom && hasFile ? View.VISIBLE : View.GONE);
        toneCard.setVisibility(custom ? View.GONE : View.VISIBLE);
        humCard.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);

        timerRow.value.setText(timerText(minutes));
        if (playing != -1) {
            playBtn.setText("Stop");
            String name = NoiseService.nameOf(this, playing);
            long end = NoiseService.endAt;
            status.setText(end > 0
                    ? name + " playing. Turns off in " + Ui.untilSpan(end) + "."
                    : name + " playing until the alarm rings or you stop it.");
        } else {
            playBtn.setText("Play");
            status.setText("Press Play, then move the sliders until it sounds right. "
                    + "Keeps playing with the screen off.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        NoiseService.onChange = refresher;
        paint();
    }

    @Override
    protected void onPause() {
        if (NoiseService.onChange == refresher) NoiseService.onChange = null;
        super.onPause();
    }
}
