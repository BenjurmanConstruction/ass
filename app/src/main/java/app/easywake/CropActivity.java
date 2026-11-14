package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The picture editor: position, zoom and rotate an imported picture, add a filter, change
 * the brightness, write on it, and save it to the gallery or use it as the background.
 */
public class CropActivity extends Activity {
    private CropView crop;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bitmap src = Bg.loadSource(this);
        if (src == null) {
            Toast.makeText(this, "Couldn't open that picture.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout bar = Ui.topBar(this, "Edit picture");
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 4));
        root.addView(bar);

        TextView hint = Ui.text(this,
                "Drag to move. Pinch to zoom. Drag your text to place it. The frame is your "
                        + "screen.",
                13, Ui.SUB);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 8));
        root.addView(hint);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        crop = new CropView(this, dm.heightPixels / (float) Math.max(1, dm.widthPixels));
        crop.setBitmap(src);
        FrameLayout holder = new FrameLayout(this);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        holder.addView(crop, cp);
        root.addView(holder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        int pad = Ui.dp(this, 14);

        // Brightness.
        LinearLayout brightRow = new LinearLayout(this);
        brightRow.setOrientation(LinearLayout.HORIZONTAL);
        brightRow.setGravity(Gravity.CENTER_VERTICAL);
        brightRow.setPadding(pad + Ui.dp(this, 4), Ui.dp(this, 8), pad, 0);
        brightRow.addView(Ui.text(this, "Brightness", 13, Ui.SUB));
        android.widget.SeekBar bright = new android.widget.SeekBar(this);
        bright.setMax(200);
        bright.setProgress(100);
        Ui.tint(bright);
        bright.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int value, boolean user) {
                crop.setBrightness(value - 100);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
            }
        });
        brightRow.addView(bright, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(brightRow);

        // Filter and text tools.
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final Button filter = Ui.button(this, "Filter: " + FILTER_NAMES[0], false);
        filter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter.setText("Filter: " + FILTER_NAMES[crop.nextFilter()]);
            }
        });
        Button text = Ui.button(this, "Text", false);
        text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText();
            }
        });
        Button gallery = Ui.button(this, "Save copy", false);
        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveToGallery();
            }
        });
        tools.addView(filter, weight(1.5f));
        tools.addView(text, weight(0.9f));
        tools.addView(gallery, weight(1.2f));
        root.addView(tools);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(pad, Ui.dp(this, 8), pad, pad);

        Button rotate = Ui.button(this, "Rotate", false);
        rotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                crop.rotate();
            }
        });
        Button reset = Ui.button(this, "Reset", false);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                crop.reset();
            }
        });
        Button use = Ui.button(this, "Use picture", true);
        use.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        actions.addView(rotate, weight(1f));
        actions.addView(reset, weight(1f));
        actions.addView(use, weight(1.5f));
        root.addView(actions);

        setContentView(root);
    }

    private LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), w);
        int m = Ui.dp(this, 4);
        p.setMargins(m, 0, m, 0);
        return p;
    }

    static final String[] FILTER_NAMES = {"None", "Black & white", "Sepia", "Warm", "Cool",
            "Vivid", "Faded", "Night red"};
    private static final int[] TEXT_COLORS = {0xFFFFFFFF, 0xFF000000, 0xFFE6B869, 0xFFFF5252,
            0xFF64B5F6};
    private static final String[] TEXT_COLOR_NAMES = {"White", "Black", "Gold", "Red", "Blue"};

    /** Asks for the words to write on the picture, their color and size. */
    private void editText() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Words to put on the picture");
        input.setText(crop.caption);
        input.setMaxLines(3);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final int[] color = {crop.captionColorIndex};
        final Button colorBtn = Ui.button(this, "Color: " + TEXT_COLOR_NAMES[color[0]], false);
        colorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                color[0] = (color[0] + 1) % TEXT_COLORS.length;
                colorBtn.setText("Color: " + TEXT_COLOR_NAMES[color[0]]);
            }
        });
        box.addView(colorBtn, Ui.full(this, 8));
        final String[] sizes = {"Small", "Medium", "Large", "Huge"};
        final int[] size = {crop.captionSize};
        final Button sizeBtn = Ui.button(this, "Size: " + sizes[size[0]], false);
        sizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                size[0] = (size[0] + 1) % sizes.length;
                sizeBtn.setText("Size: " + sizes[size[0]]);
            }
        });
        box.addView(sizeBtn, Ui.full(this, 8));
        new android.app.AlertDialog.Builder(this)
                .setTitle("Text on picture")
                .setView(box)
                .setPositiveButton("Done", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        crop.setCaption(input.getText().toString().trim(), color[0], size[0],
                                TEXT_COLORS[color[0]]);
                    }
                })
                .setNeutralButton("Remove text",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                crop.setCaption("", color[0], size[0], TEXT_COLORS[color[0]]);
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Saves the edited picture into the phone's Pictures folder. */
    private void saveToGallery() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Saving to the gallery needs Android 10 or newer.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Bitmap out = crop.render();
        if (out == null) {
            Toast.makeText(this, "Couldn't make the picture.", Toast.LENGTH_LONG).show();
            return;
        }
        android.net.Uri uri = null;
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    "EasyWake_" + System.currentTimeMillis() + ".jpg");
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/EasyWake");
            uri = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("no place to save");
            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null || !out.compress(Bitmap.CompressFormat.JPEG, 94, os)) {
                    throw new Exception("write failed");
                }
            }
            Toast.makeText(this, "Saved to Pictures/EasyWake in your gallery.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            Toast.makeText(this, "Couldn't save to the gallery.", Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        Bitmap out = crop.render();
        if (out != null && Bg.saveBitmap(this, out)) {
            Prefs.putInt(this, "bgMode", Bg.MODE_PICTURE);
            Toast.makeText(this, "Background set.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Couldn't save the picture.", Toast.LENGTH_LONG).show();
        }
    }

    /** Shows the picture in a phone-shaped frame; the picture always fills the frame. */
    static final class CropView extends View {
        private final float aspect;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector pinch;
        private Bitmap bm;
        private float scale;
        private float minScale;
        private float tx, ty;
        private float lastX, lastY;
        private boolean dragging;
        private boolean draggingCaption;
        private int filter;
        private int brightness;
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Rect textBounds = new android.graphics.Rect();
        String caption = "";
        int captionColorIndex;
        int captionSize = 1;
        /** Where the middle of the text sits, as a share of the frame's width and height. */
        private float capX = 0.5f, capY = 0.82f;

        int nextFilter() {
            filter = (filter + 1) % FILTER_NAMES.length;
            applyColor();
            return filter;
        }

        void setBrightness(int b) {
            brightness = b;
            applyColor();
        }

        void setCaption(String text, int colorIndex, int size, int color) {
            caption = text == null ? "" : text;
            captionColorIndex = colorIndex;
            captionSize = size;
            textPaint.setColor(color);
            invalidate();
        }

        /** Builds the color change for the chosen filter and brightness. */
        private void applyColor() {
            android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
            switch (filter) {
                case 1:
                    m.setSaturation(0f);
                    break;
                case 2:
                    m.setSaturation(0f);
                    m.postConcat(new android.graphics.ColorMatrix(new float[]{
                            1.07f, 0, 0, 0, 12,
                            0, 0.95f, 0, 0, 4,
                            0, 0, 0.74f, 0, -8,
                            0, 0, 0, 1, 0}));
                    break;
                case 3:
                    m.set(new float[]{
                            1.12f, 0, 0, 0, 6,
                            0, 1.02f, 0, 0, 2,
                            0, 0, 0.86f, 0, -6,
                            0, 0, 0, 1, 0});
                    break;
                case 4:
                    m.set(new float[]{
                            0.88f, 0, 0, 0, -4,
                            0, 1.0f, 0, 0, 0,
                            0, 0, 1.14f, 0, 8,
                            0, 0, 0, 1, 0});
                    break;
                case 5:
                    m.setSaturation(1.6f);
                    break;
                case 6:
                    m.setSaturation(0.55f);
                    m.postConcat(new android.graphics.ColorMatrix(new float[]{
                            0.85f, 0, 0, 0, 30,
                            0, 0.85f, 0, 0, 30,
                            0, 0, 0.85f, 0, 30,
                            0, 0, 0, 1, 0}));
                    break;
                case 7:
                    m.setSaturation(0f);
                    m.postConcat(new android.graphics.ColorMatrix(new float[]{
                            0.9f, 0, 0, 0, 0,
                            0, 0.18f, 0, 0, 0,
                            0, 0, 0.1f, 0, 0,
                            0, 0, 0, 1, 0}));
                    break;
                default:
                    break;
            }
            if (brightness != 0) {
                float add = brightness * 1.1f;
                m.postConcat(new android.graphics.ColorMatrix(new float[]{
                        1, 0, 0, 0, add,
                        0, 1, 0, 0, add,
                        0, 0, 1, 0, add,
                        0, 0, 0, 1, 0}));
            }
            paint.setColorFilter(filter == 0 && brightness == 0 ? null
                    : new android.graphics.ColorMatrixColorFilter(m));
            invalidate();
        }

        private void drawCaption(Canvas cv) {
            if (caption.isEmpty()) return;
            float[] sizes = {0.05f, 0.075f, 0.11f, 0.16f};
            textPaint.setTextSize(getWidth() * sizes[Math.max(0, Math.min(3, captionSize))]);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            // A soft shadow keeps the words readable on any picture.
            textPaint.setShadowLayer(textPaint.getTextSize() * 0.12f, 0,
                    textPaint.getTextSize() * 0.04f,
                    textPaint.getColor() == 0xFF000000 ? 0xAAFFFFFF : 0xCC000000);
            String[] lines = caption.split("\n");
            float lineH = textPaint.getTextSize() * 1.15f;
            float y = capY * getHeight() - (lines.length - 1) * lineH / 2f;
            for (String line : lines) {
                cv.drawText(line, capX * getWidth(), y - (textPaint.ascent()
                        + textPaint.descent()) / 2f, textPaint);
                y += lineH;
            }
        }

        private boolean onCaption(float x, float y) {
            if (caption.isEmpty()) return false;
            String[] lines = caption.split("\n");
            float widest = 0;
            for (String line : lines) widest = Math.max(widest, textPaint.measureText(line));
            float halfW = Math.max(widest / 2f, Ui.dp(getContext(), 30));
            float halfH = Math.max(lines.length * textPaint.getTextSize() * 0.7f,
                    Ui.dp(getContext(), 26));
            return Math.abs(x - capX * getWidth()) <= halfW
                    && Math.abs(y - capY * getHeight()) <= halfH;
        }

        CropView(Context c, float aspect) {
            super(c);
            this.aspect = aspect;
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Ui.dp(c, 2));
            border.setColor(Ui.ACCENT);
            pinch = new ScaleGestureDetector(c,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector d) {
                            float old = scale;
                            scale = Math.max(minScale,
                                    Math.min(minScale * 6f, scale * d.getScaleFactor()));
                            // Zoom around the fingers rather than the corner.
                            float f = scale / old;
                            tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                            ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                            clamp();
                            invalidate();
                            return true;
                        }
                    });
        }

        void setBitmap(Bitmap b) {
            bm = b;
            reset();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec);
            int maxH = MeasureSpec.getSize(hSpec);
            if (maxW <= 0 || maxH <= 0) {
                setMeasuredDimension(maxW, maxH);
                return;
            }
            int w = maxW;
            int h = Math.round(w * aspect);
            if (h > maxH) {
                h = maxH;
                w = Math.round(h / aspect);
            }
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            reset();
        }

        void reset() {
            if (bm == null || getWidth() <= 0 || getHeight() <= 0) return;
            minScale = Math.max(getWidth() / (float) bm.getWidth(),
                    getHeight() / (float) bm.getHeight());
            scale = minScale;
            tx = (getWidth() - bm.getWidth() * scale) / 2f;
            ty = (getHeight() - bm.getHeight() * scale) / 2f;
            invalidate();
        }

        void rotate() {
            if (bm == null) return;
            try {
                Matrix m = new Matrix();
                m.postRotate(90);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            } catch (Throwable t) {
                return;
            }
            reset();
        }

        private void clamp() {
            float w = bm.getWidth() * scale;
            float h = bm.getHeight() * scale;
            tx = Math.min(0, Math.max(getWidth() - w, tx));
            ty = Math.min(0, Math.max(getHeight() - h, ty));
        }

        private void drawPicture(Canvas cv) {
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(tx, ty);
            cv.drawBitmap(bm, matrix, paint);
            drawCaption(cv);
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (bm == null) return;
            drawPicture(cv);
            float half = border.getStrokeWidth() / 2f;
            cv.drawRect(half, half, getWidth() - half, getHeight() - half, border);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (bm == null) return false;
            pinch.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    dragging = true;
                    draggingCaption = onCaption(e.getX(), e.getY());
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    // Finger count changed: don't let the picture jump.
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() == 1 && !pinch.isInProgress()) {
                        if (dragging && draggingCaption) {
                            capX = Math.max(0.05f, Math.min(0.95f,
                                    capX + (e.getX() - lastX) / getWidth()));
                            capY = Math.max(0.04f, Math.min(0.96f,
                                    capY + (e.getY() - lastY) / getHeight()));
                            invalidate();
                        } else if (dragging) {
                            tx += e.getX() - lastX;
                            ty += e.getY() - lastY;
                            clamp();
                            invalidate();
                        }
                        lastX = e.getX();
                        lastY = e.getY();
                        dragging = true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    draggingCaption = false;
                    break;
                default:
                    break;
            }
            return true;
        }

        Bitmap render() {
            if (bm == null || getWidth() <= 0 || getHeight() <= 0) return null;
            try {
                Bitmap out = Bitmap.createBitmap(getWidth(), getHeight(),
                        Bitmap.Config.ARGB_8888);
                drawPicture(new Canvas(out));
                return out;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
