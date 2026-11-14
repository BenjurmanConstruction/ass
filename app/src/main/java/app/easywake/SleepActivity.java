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
import android.widget.SeekBar;
import android.widget.TextView;

/** Fan and noise sounds to fall asleep to. */
public class SleepActivity extends Activity {
    private static final int[] TIMER_OPTIONS = {0, 15, 30, 60, 120, 240, 480};

    private LinearLayout root;
    private final LinearLayout[] cards = new LinearLayout[NoiseGen.NAMES.length];
    private Ui.Row timerRow;
    private Button playBtn;
    private TextView status;
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
        selected = Prefs.getInt(this, "noiseType", 0);
        minutes = Prefs.getInt(this, "noiseTimer", 60);
        NoiseService.volume = Prefs.getInt(this, "noiseVol", 70) / 100f;

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        scroll.setFillViewport(true);

        root.addView(Ui.topBar(this, "Sleep sounds"), Ui.full(this, 0));

        root.addView(Ui.heading(this, "Sound"), Ui.full(this, 18));
        for (int i = 0; i < NoiseGen.NAMES.length; i++) {
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            TextView name = Ui.text(this, NoiseGen.NAMES[i], 18, Ui.TEXT);
            name.setTypeface(Ui.medium());
            card.addView(name);
            card.addView(Ui.text(this, NoiseGen.HINTS[i], 14, Ui.SUB));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selected = idx;
                    Prefs.putInt(SleepActivity.this, "noiseType", idx);
                    // Switch the sound live if something is already playing.
                    if (NoiseService.playingType != -1) startNoise();
                    paint();
                }
            });
            cards[i] = card;
            root.addView(card, Ui.full(this, 8));
        }

        root.addView(Ui.heading(this, "Options"), Ui.full(this, 22));
        timerRow = Ui.row(this, "Turn off after", "");
        timerRow.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseTimer();
            }
        });
        root.addView(timerRow.view, Ui.full(this, 8));

        LinearLayout volCard = new LinearLayout(this);
        volCard.setOrientation(LinearLayout.VERTICAL);
        volCard.setBackground(Ui.card(this, 18));
        volCard.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 10));
        volCard.addView(Ui.text(this, "Loudness", 16, Ui.TEXT));
        SeekBar vol = new SeekBar(this);
        vol.setMax(100);
        vol.setProgress(Prefs.getInt(this, "noiseVol", 70));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                NoiseService.volume = progress / 100f;
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                Prefs.putInt(SleepActivity.this, "noiseVol", s.getProgress());
            }
        });
        volCard.addView(vol, Ui.full(this, 8));
        volCard.addView(Ui.text(this, "Also follows your phone's media volume.", 12, Ui.SUB),
                Ui.full(this, 2));
        root.addView(volCard, Ui.full(this, 8));

        status = Ui.text(this, "", 14, Ui.SUB);
        status.setGravity(android.view.Gravity.CENTER);
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

    private void startNoise() {
        Intent i = new Intent(this, NoiseService.class)
                .setAction(NoiseService.ACTION_START)
                .putExtra("type", selected)
                .putExtra("minutes", minutes);
        startForegroundService(i);
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
        timerRow.value.setText(timerText(minutes));
        if (playing != -1) {
            playBtn.setText("Stop");
            long end = NoiseService.endAt;
            status.setText(end > 0
                    ? NoiseGen.NAMES[playing] + " playing. Turns off in " + Ui.untilSpan(end) + "."
                    : NoiseGen.NAMES[playing] + " playing until the alarm rings or you stop it.");
        } else {
            playBtn.setText("Play");
            status.setText("Keeps playing with the screen off. Stops when an alarm rings.");
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
