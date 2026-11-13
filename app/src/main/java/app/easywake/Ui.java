package app.easywake;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

import java.util.Calendar;

/** Shared colors and small helpers for building the screens. */
final class Ui {
    static final int BG = 0xFF101418;
    static final int CARD = 0xFF1C232B;
    static final int ACCENT = 0xFFFFB74D;
    static final int TEXT = 0xFFECEFF1;
    static final int SUB = 0xFF90A4AE;
    static final int WARN = 0xFF5D4037;
    static final int ON_ACCENT = 0xFF1B1B1B;

    private static final String[] DAY_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    static Button button(Context c, String s, boolean filled) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 52));
        if (filled) {
            b.setBackground(round(c, ACCENT, 14));
            b.setTextColor(ON_ACCENT);
            b.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            b.setBackground(round(c, CARD, 14));
            b.setTextColor(TEXT);
        }
        return b;
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

    /** "Rings in 7 hr 20 min" style text for a future time. */
    static String untilText(long when) {
        long mins = Math.max(1, Math.round((when - System.currentTimeMillis()) / 60000.0));
        long d = mins / (60 * 24);
        long h = (mins / 60) % 24;
        long m = mins % 60;
        StringBuilder sb = new StringBuilder("Rings in ");
        if (d > 0) sb.append(d).append(d == 1 ? " day " : " days ");
        if (h > 0) sb.append(h).append(" hr ");
        if (m > 0 || (d == 0 && h == 0)) sb.append(m).append(" min");
        return sb.toString().trim();
    }

    static String fadeText(int sec) {
        if (sec <= 0) return "Off (full volume right away)";
        if (sec < 60) return sec + " seconds";
        int m = sec / 60;
        return m + (m == 1 ? " minute" : " minutes");
    }
}
