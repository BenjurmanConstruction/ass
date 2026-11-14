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

/** Position, zoom and rotate an imported picture before it becomes the background. */
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

        LinearLayout bar = Ui.topBar(this, "Adjust picture");
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 4));
        root.addView(bar);

        TextView hint = Ui.text(this,
                "Drag to move. Pinch with two fingers to zoom. The frame is your screen.",
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

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dp(this, 14);
        actions.setPadding(pad, Ui.dp(this, 10), pad, pad);

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
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    // Finger count changed: don't let the picture jump.
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() == 1 && !pinch.isInProgress()) {
                        if (dragging) {
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
