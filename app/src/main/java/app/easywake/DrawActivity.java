package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Finger-painting canvas. What you draw becomes the app background. */
public class DrawActivity extends Activity {
    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFFE6B869, 0xFFFFD54F, 0xFFFF8A65, 0xFFE53935, 0xFFF06292,
            0xFFBA68C8, 0xFF7986CB, 0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFF8D6E63,
            0xFF90A4AE, 0xFF000000
    };
    private static final String[] CANVAS_NAMES = {
            "Midnight blue", "Black", "Deep green", "Plum", "Warm brown", "White"
    };
    private static final int[] CANVAS_COLORS = {
            0xFF0B0F1A, 0xFF000000, 0xFF0C1A14, 0xFF1A0F24, 0xFF2A1A12, 0xFFFFFFFF
    };

    private DrawView canvas;
    private LinearLayout palette;
    private int color = COLORS[1];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean useBase = getIntent().getBooleanExtra("base", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout bar = Ui.topBar(this, "Draw");
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        Button save = Ui.button(this, "Save", true);
        save.setMinHeight(Ui.dp(this, 42));
        save.setMinimumHeight(Ui.dp(this, 42));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        bar.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 42)));
        root.addView(bar);

        canvas = new DrawView(this);
        if (useBase) canvas.base = Bg.bitmap(this);
        canvas.color = color;
        root.addView(canvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        tools.setPadding(pad, Ui.dp(this, 10), pad, pad);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(palette);
        tools.addView(hs);
        paintPalette();

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.addView(Ui.text(this, "Brush", 14, Ui.SUB));
        SeekBar size = new SeekBar(this);
        size.setMax(56);
        size.setProgress(10);
        canvas.strokeWidth = Ui.dp(this, 4 + 10);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                canvas.strokeWidth = Ui.dp(DrawActivity.this, 4 + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        sizeRow.addView(size, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tools.addView(sizeRow, Ui.full(this, 8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button undo = small("Undo");
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                canvas.undo();
            }
        });
        Button clear = small("Clear");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                canvas.clear();
            }
        });
        Button paper = small("Canvas color");
        paper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCanvasColor();
            }
        });
        actions.addView(undo, weight());
        actions.addView(clear, weight());
        if (!useBase || canvas.base == null) actions.addView(paper, weight());
        tools.addView(actions, Ui.full(this, 8));

        root.addView(tools);
        setContentView(root);
    }

    private Button small(String s) {
        Button b = Ui.button(this, s, false);
        b.setTextSize(14);
        b.setMinHeight(Ui.dp(this, 44));
        b.setMinimumHeight(Ui.dp(this, 44));
        b.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        int m = Ui.dp(this, 4);
        p.setMargins(m, 0, m, 0);
        return p;
    }

    private void paintPalette() {
        palette.removeAllViews();
        for (final int c : COLORS) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(Ui.dp(this, c == color ? 3 : 1), c == color ? Ui.ACCENT : 0x66FFFFFF);
            dot.setBackground(g);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    color = c;
                    canvas.color = c;
                    paintPalette();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    Ui.dp(this, 40), Ui.dp(this, 40));
            p.rightMargin = Ui.dp(this, 10);
            palette.addView(dot, p);
        }
    }

    private void chooseCanvasColor() {
        new AlertDialog.Builder(this)
                .setTitle("Canvas color")
                .setItems(CANVAS_NAMES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        canvas.canvasColor = CANVAS_COLORS[which];
                        canvas.invalidate();
                    }
                })
                .show();
    }

    private void save() {
        Bitmap bm = canvas.render();
        if (bm != null && Bg.saveBitmap(this, bm)) {
            Prefs.putInt(this, "bgMode", Bg.MODE_PICTURE);
            // Artwork is shown close to full brightness.
            Prefs.putInt(this, "bgDim", 15);
            Toast.makeText(this, "Saved as your background.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Couldn't save the drawing.", Toast.LENGTH_LONG).show();
        }
    }

    /** The drawing surface itself. */
    static final class DrawView extends View {
        Bitmap base;
        int canvasColor = 0xFF0B0F1A;
        int color = 0xFFE6B869;
        float strokeWidth = 30f;

        private final List<Path> paths = new ArrayList<>();
        private final List<Paint> paints = new ArrayList<>();
        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();
        private Path current;
        private float lastX, lastY;

        DrawView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (base != null && !base.isRecycled()) {
                float s = Math.max(getWidth() / (float) base.getWidth(),
                        getHeight() / (float) base.getHeight());
                float w = base.getWidth() * s;
                float h = base.getHeight() * s;
                float l = (getWidth() - w) / 2f;
                float t = (getHeight() - h) / 2f;
                dst.set(l, t, l + w, t + h);
                cv.drawBitmap(base, null, dst, bitmapPaint);
            } else {
                cv.drawColor(canvasColor);
            }
            for (int i = 0; i < paths.size(); i++) {
                cv.drawPath(paths.get(i), paints.get(i));
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    p.setStrokeWidth(strokeWidth);
                    p.setColor(color);
                    current = new Path();
                    current.moveTo(x, y);
                    // A tiny line so a single tap leaves a dot.
                    current.lineTo(x + 0.1f, y + 0.1f);
                    paths.add(current);
                    paints.add(p);
                    lastX = x;
                    lastY = y;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        current.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (current != null) current.lineTo(x, y);
                    current = null;
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }

        void undo() {
            if (!paths.isEmpty()) {
                paths.remove(paths.size() - 1);
                paints.remove(paints.size() - 1);
                invalidate();
            }
        }

        void clear() {
            paths.clear();
            paints.clear();
            invalidate();
        }

        Bitmap render() {
            if (getWidth() <= 0 || getHeight() <= 0) return null;
            try {
                Bitmap bm = Bitmap.createBitmap(getWidth(), getHeight(),
                        Bitmap.Config.ARGB_8888);
                draw(new Canvas(bm));
                return bm;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
