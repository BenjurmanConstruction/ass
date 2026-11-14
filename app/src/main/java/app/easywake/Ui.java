package app.easywake;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** Shared colors and building blocks so every screen looks the same. */
final class Ui {
    static final int BG = 0xFF0B0F1A;
    static final int CARD = 0xD91A2030;
    static final int STROKE = 0x26FFFFFF;
    static final int ACCENT = 0xFFE6B869;
    static final int ACCENT_HI = 0xFFF3D08E;
    static final int ACCENT_LO = 0xFFD49B45;
    static final int TEXT = 0xFFF4F1EA;
    static final int SUB = 0xFFA7ADBB;
    static final int WARN = 0xE65D4037;
    static final int ON_ACCENT = 0xFF20160A;
    static final int DANGER = 0xFFEF9A9A;

    private static final String[] DAY_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static Typeface light() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** Translucent panel with a hairline border. */
    static GradientDrawable card(Context c, float radiusDp) {
        GradientDrawable d = round(c, CARD, radiusDp);
        d.setStroke(dp(c, 1), STROKE);
        return d;
    }

    static GradientDrawable gold(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ACCENT_HI, ACCENT_LO});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** Wraps a background so it flashes softly when pressed. */
    static Drawable ripple(Context c, Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content,
                round(c, 0xFFFFFFFF, radiusDp));
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** Pill button. Filled = the main gold action; otherwise a quiet outlined one. */
    static Button button(Context c, String s, boolean filled) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 56));
        b.setMinimumHeight(dp(c, 56));
        b.setTypeface(medium());
        b.setLetterSpacing(0.02f);
        int padH = dp(c, 22);
        b.setPadding(padH, 0, padH, 0);
        if (filled) {
            b.setBackground(ripple(c, gold(c, 28), 28));
            b.setTextColor(ON_ACCENT);
        } else {
            b.setBackground(ripple(c, card(c, 28), 28));
            b.setTextColor(TEXT);
        }
        return b;
    }

    /** A tappable settings line: name on the left, current choice on the right. */
    static final class Row {
        final LinearLayout view;
        final TextView value;

        Row(LinearLayout view, TextView value) {
            this.view = view;
            this.value = value;
        }
    }

    static Row row(Context c, String title, String value) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(ripple(c, card(c, 18), 18));
        l.setPadding(dp(c, 18), dp(c, 14), dp(c, 14), dp(c, 14));
        l.setMinimumHeight(dp(c, 60));
        l.setClickable(true);

        TextView t = text(c, title, 16, TEXT);
        l.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView v = text(c, value, 15, ACCENT);
        v.setGravity(Gravity.END);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(dp(c, 12), 0, 0, 0);
        l.addView(v, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text(c, "  ›", 22, SUB);
        l.addView(chevron);
        return new Row(l, v);
    }

    /** Screen header with a back arrow. Returns the bar so extra buttons can be added. */
    static LinearLayout topBar(final Activity a, String title) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text(a, "‹", 38, TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ripple(a, round(a, 0x00000000, 24), 24));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.finish();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));

        TextView t = text(a, title, 24, TEXT);
        t.setTypeface(light());
        t.setPadding(dp(a, 6), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    static LinearLayout.LayoutParams full(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static TextView heading(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, SUB);
        t.setLetterSpacing(0.12f);
        t.setTypeface(medium());
        return t;
    }

    static String timeText(Context c, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        return DateFormat.getTimeFormat(c).format(cal.getTime());
    }

    static String daysText(int days) {
        if (days == 0) return "One time";
        if (days == 127) return "Every day";
        if (days == 62) return "Weekdays";
        if (days == 65) return "Weekends";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(DAY_SHORT[i]);
            }
        }
        return sb.toString();
    }

    /** "7 hr 20 min" style length of time from now until {@code when}. */
    static String untilSpan(long when) {
        long mins = Math.max(1, Math.round((when - System.currentTimeMillis()) / 60000.0));
        long d = mins / (60 * 24);
        long h = (mins / 60) % 24;
        long m = mins % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(d == 1 ? " day " : " days ");
        if (h > 0) sb.append(h).append(" hr ");
        if (m > 0 || (d == 0 && h == 0)) sb.append(m).append(" min");
        return sb.toString().trim();
    }

    static String untilText(long when) {
        return "Rings in " + untilSpan(when);
    }

    static String fadeText(int sec) {
        if (sec <= 0) return "Off";
        if (sec < 60) return sec + " seconds";
        int m = sec / 60;
        return m + (m == 1 ? " minute" : " minutes");
    }

    /** "1:05" style position in a song. */
    static String clock(long ms) {
        long total = Math.max(0, ms) / 1000;
        long m = total / 60;
        long sec = total % 60;
        return m + ":" + (sec < 10 ? "0" : "") + sec;
    }

    static String vibrateText(int mode) {
        if (mode == 2) return "From the start";
        if (mode == 1) return "After the fade-in";
        return "Off";
    }
}
