package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** The app background: a color theme, or a picture the user imported or drew. */
final class Bg {
    static final String[] THEME_NAMES = {
            "Midnight", "Onyx", "Ocean", "Forest", "Plum", "Ember", "Dawn"
    };
    private static final int[][] THEMES = {
            {0xFF0B0F1A, 0xFF1B2440},
            {0xFF080808, 0xFF1E1E1E},
            {0xFF08182B, 0xFF14506B},
            {0xFF0C1A14, 0xFF1F3B2D},
            {0xFF1A0F24, 0xFF4A2545},
            {0xFF1A0E0A, 0xFF5A2A1A},
            {0xFF2B1B3D, 0xFFB5654A},
    };

    static final int MODE_THEME = 0;
    static final int MODE_PICTURE = 1;

    private static Bitmap cached;
    private static long cachedStamp;

    private Bg() {}

    static File file(Context c) {
        return new File(c.getFilesDir(), "bg.jpg");
    }

    static boolean hasPicture(Context c) {
        File f = file(c);
        return f.exists() && f.length() > 0;
    }

    static boolean usingPicture(Context c) {
        return Prefs.getInt(c, "bgMode", MODE_THEME) == MODE_PICTURE && hasPicture(c);
    }

    static int themeIndex(Context c) {
        int i = Prefs.getInt(c, "bgTheme", 0);
        return (i >= 0 && i < THEMES.length) ? i : 0;
    }

    static GradientDrawable themeDrawable(int i) {
        if (i < 0 || i >= THEMES.length) i = 0;
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, THEMES[i]);
    }

    static Drawable drawable(Context c) {
        if (usingPicture(c)) {
            Bitmap bm = bitmap(c);
            if (bm != null) {
                int dim = Prefs.getInt(c, "bgDim", 45);
                return new CoverDrawable(bm, (dim * 255 / 100) << 24);
            }
        }
        return themeDrawable(themeIndex(c));
    }

    /** Paints the background behind a screen and matches the system bars to it. */
    static void apply(Activity a, View root) {
        root.setBackground(drawable(a));
        int top = 0xFF000000;
        int bottom = 0xFF000000;
        if (!usingPicture(a)) {
            int[] t = THEMES[themeIndex(a)];
            top = t[0];
            bottom = t[1];
        }
        a.getWindow().setStatusBarColor(top);
        a.getWindow().setNavigationBarColor(bottom);
    }

    static synchronized Bitmap bitmap(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        long stamp = f.lastModified() + f.length();
        if (cached != null && stamp == cachedStamp) return cached;
        try {
            cached = BitmapFactory.decodeFile(f.getAbsolutePath());
            cachedStamp = stamp;
        } catch (Throwable t) {
            cached = null;
        }
        return cached;
    }

    static synchronized boolean saveBitmap(Context c, Bitmap bm) {
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            bm.compress(Bitmap.CompressFormat.JPEG, 90, out);
            cached = null;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Loads a picture from the phone, shrunk to a sensible size and turned upright. */
    static Bitmap decodeUri(Context c, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bm;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bm = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bm == null) return null;

            int rotate = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    ExifInterface exif = new ExifInterface(in);
                    int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
                    if (o == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
                }
            } catch (Exception e) {
                rotate = 0;
            }
            if (rotate != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            }
            return bm;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Draws a picture so it fills the area (cropping the overflow), with a dark tint on top. */
    static final class CoverDrawable extends Drawable {
        private final Bitmap bm;
        private final int overlay;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final RectF dst = new RectF();

        CoverDrawable(Bitmap bm, int overlay) {
            this.bm = bm;
            this.overlay = overlay;
        }

        @Override
        public void draw(Canvas cv) {
            Rect b = getBounds();
            if (b.isEmpty() || bm.isRecycled()) return;
            float s = Math.max(b.width() / (float) bm.getWidth(),
                    b.height() / (float) bm.getHeight());
            float w = bm.getWidth() * s;
            float h = bm.getHeight() * s;
            float l = b.left + (b.width() - w) / 2f;
            float t = b.top + (b.height() - h) / 2f;
            dst.set(l, t, l + w, t + h);
            cv.save();
            cv.clipRect(b);
            cv.drawBitmap(bm, null, dst, paint);
            if ((overlay >>> 24) != 0) cv.drawColor(overlay);
            cv.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
