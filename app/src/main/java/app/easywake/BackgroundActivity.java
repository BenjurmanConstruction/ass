package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Background studio: pick a color theme, import a picture, or draw your own. */
public class BackgroundActivity extends Activity {
    private static final int PICK_IMAGE = 1;

    private ScrollView scroll;
    private FrameLayout preview;
    private LinearLayout swatches;
    private LinearLayout dimCard;
    private Button drawOnBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.topBar(this, "Background studio"), Ui.full(this, 0));

        // Live preview of how the home screen will look.
        preview = new FrameLayout(this);
        TextView sample = Ui.text(this, Ui.timeText(this, 7, 0), 44, Ui.TEXT);
        sample.setTypeface(Ui.light());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER;
        preview.addView(sample, sp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 190));
        pp.topMargin = Ui.dp(this, 14);
        root.addView(preview, pp);

        root.addView(Ui.heading(this, "Color themes"), Ui.full(this, 22));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(swatches);
        root.addView(hs, Ui.full(this, 10));

        root.addView(Ui.heading(this, "Your own picture"), Ui.full(this, 22));

        Button importBtn = Ui.button(this, "Import a picture", false);
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        root.addView(importBtn, Ui.full(this, 10));

        Button drawBtn = Ui.button(this, "Draw your own", false);
        drawBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BackgroundActivity.this, DrawActivity.class));
            }
        });
        root.addView(drawBtn, Ui.full(this, 10));

        drawOnBtn = Ui.button(this, "Draw on my picture", false);
        drawOnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BackgroundActivity.this, DrawActivity.class)
                        .putExtra("base", true));
            }
        });
        root.addView(drawOnBtn, Ui.full(this, 10));

        dimCard = new LinearLayout(this);
        dimCard.setOrientation(LinearLayout.VERTICAL);
        dimCard.setBackground(Ui.card(this, 18));
        dimCard.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 10));
        dimCard.addView(Ui.text(this, "Darken picture", 16, Ui.TEXT));
        dimCard.addView(Ui.text(this, "Darker makes the alarm times easier to read.", 12,
                Ui.SUB));
        SeekBar dim = new SeekBar(this);
        dim.setMax(85);
        dim.setProgress(Prefs.getInt(this, "bgDim", 45));
        dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) {
                    Prefs.putInt(BackgroundActivity.this, "bgDim", progress);
                    paint();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        dimCard.addView(dim, Ui.full(this, 8));
        root.addView(dimCard, Ui.full(this, 14));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        paint();
    }

    private void paint() {
        Bg.apply(this, scroll);
        preview.setBackground(Bg.drawable(this));

        boolean picture = Bg.usingPicture(this);
        dimCard.setVisibility(picture ? View.VISIBLE : View.GONE);
        drawOnBtn.setVisibility(Bg.hasPicture(this) ? View.VISIBLE : View.GONE);

        swatches.removeAllViews();
        int current = picture ? -1 : Bg.themeIndex(this);
        for (int i = 0; i < Bg.THEME_NAMES.length; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            View dot = new View(this);
            GradientDrawable g = Bg.themeDrawable(i);
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(Ui.dp(this, i == current ? 3 : 1), i == current ? Ui.ACCENT : Ui.STROKE);
            dot.setBackground(g);
            cell.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60)));

            TextView name = Ui.text(this, Bg.THEME_NAMES[i], 12,
                    i == current ? Ui.ACCENT : Ui.SUB);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            np.topMargin = Ui.dp(this, 6);
            cell.addView(name, np);

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.putInt(BackgroundActivity.this, "bgTheme", idx);
                    Prefs.putInt(BackgroundActivity.this, "bgMode", Bg.MODE_THEME);
                    paint();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = Ui.dp(this, 16);
            swatches.addView(cell, cp);
        }
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "No picture picker found on this phone.", Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        Toast.makeText(this, "Loading picture...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = Bg.decodeUri(BackgroundActivity.this, uri, 1600);
                final boolean ok = bm != null && Bg.saveBitmap(BackgroundActivity.this, bm);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            Prefs.putInt(BackgroundActivity.this, "bgMode", Bg.MODE_PICTURE);
                            paint();
                        } else {
                            Toast.makeText(BackgroundActivity.this,
                                    "Couldn't open that picture.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }
}
