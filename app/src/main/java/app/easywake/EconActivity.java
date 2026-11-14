package app.easywake;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The dates of the three scheduled events that move US markets most: Federal Reserve rate
 * decisions, the inflation (CPI) report and the monthly jobs report.
 *
 * Dates were copied from the Federal Reserve's meeting calendar and the Bureau of Labor
 * Statistics release schedule in November 2026. They are built into the app, so they
 * don't update on their own.
 */
public class EconActivity extends Activity {
    private static final int FED = 0;
    private static final int CPI = 1;
    private static final int JOBS = 2;
    private static final String[] NAMES = {"Fed rate decision", "Inflation report (CPI)",
            "Jobs report"};
    private static final String[] TIMES = {"2:00 PM Eastern", "8:30 AM Eastern",
            "8:30 AM Eastern"};

    /** Each entry is year*10000 + month*100 + day. For the Fed it is the decision day. */
    private static final int[] FED_DAYS = {20261209, 20270127, 20270317, 20270428, 20270609,
            20270728, 20270915, 20271027, 20271208};
    private static final int[] CPI_DAYS = {20261210, 20270113, 20270211, 20270310, 20270413,
            20270512, 20270610, 20270714, 20270811, 20270914, 20271014, 20271110, 20271210};
    private static final int[] JOBS_DAYS = {20261204, 20270108, 20270205, 20270305, 20270402,
            20270507, 20270604, 20270702, 20270806, 20270903, 20271008, 20271105, 20271203};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "m_econ")) {
            Toast.makeText(this, "Unlock the Economic calendar in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.topBar(this, "Economic calendar"), Ui.full(this, 0));

        List<int[]> all = new ArrayList<>();
        for (int d : FED_DAYS) all.add(new int[]{d, FED});
        for (int d : CPI_DAYS) all.add(new int[]{d, CPI});
        for (int d : JOBS_DAYS) all.add(new int[]{d, JOBS});
        Collections.sort(all, new Comparator<int[]>() {
            @Override
            public int compare(int[] x, int[] y) {
                return Integer.compare(x[0], y[0]);
            }
        });

        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.YEAR) * 10000 + (now.get(Calendar.MONTH) + 1) * 100
                + now.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE, MMM d", Locale.US);
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.US);

        int shown = 0;
        int lastMonth = -1;
        for (int[] e : all) {
            if (e[0] < today) continue;
            Calendar c = Calendar.getInstance();
            c.set(e[0] / 10000, e[0] / 100 % 100 - 1, e[0] % 100, 12, 0, 0);
            int month = e[0] / 100;
            if (month != lastMonth) {
                root.addView(Ui.heading(this, monthFmt.format(c.getTime())),
                        Ui.full(this, 22));
                lastMonth = month;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
            if (shown == 0) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            card.setBackground(bg);
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            TextView name = Ui.text(this, NAMES[e[1]], 17, Ui.TEXT);
            name.setTypeface(Ui.medium());
            card.addView(name);
            long days = Math.round((c.getTimeInMillis() - noon(now)) / 86400000.0);
            String when = e[0] == today ? "Today" : (days == 1 ? "Tomorrow"
                    : "In " + days + " days");
            card.addView(Ui.text(this, dayFmt.format(c.getTime()) + "  •  " + TIMES[e[1]]
                    + "  •  " + when, 13, shown == 0 ? Ui.ACCENT : Ui.SUB),
                    Ui.full(this, 4));
            root.addView(card, Ui.full(this, 10));
            shown++;
        }
        if (shown == 0) {
            root.addView(Ui.text(this, "The dates built into this version have all passed. "
                    + "Update the app for the new schedule.", 15, Ui.TEXT),
                    Ui.full(this, 18));
        }

        root.addView(Ui.heading(this, "What these are"), Ui.full(this, 26));
        root.addView(Ui.text(this, "Fed rate decision: the Federal Reserve's committee "
                + "announces whether it is raising, cutting or holding its interest rate. "
                + "The date shown is the second day of the two-day meeting, when the "
                + "decision comes out. The chair speaks to the press 30 minutes later, and "
                + "markets often move more on the speech than on the decision.\n\n"
                + "Inflation report (CPI): the Consumer Price Index shows how much prices "
                + "rose over the past month and year. A number above or below what "
                + "traders expected can move stocks and bonds sharply, because it changes "
                + "guesses about what the Fed does next.\n\n"
                + "Jobs report: how many jobs the economy added last month and the "
                + "unemployment rate. Usually the first Friday of the month.", 14, Ui.SUB),
                Ui.full(this, 8));
        root.addView(Ui.text(this, "Dates come from the Federal Reserve's meeting calendar "
                + "and the Bureau of Labor Statistics release schedule, as published in "
                + "November 2026. They can change (2027 Fed dates are marked tentative, "
                + "and government shutdowns have delayed reports before), and the app "
                + "doesn't update them by itself. Check federalreserve.gov or bls.gov "
                + "before relying on one.", 12, Ui.SUB), Ui.full(this, 16));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    private static long noon(Calendar now) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
