package app.easywake;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The scheduled US reports and Federal Reserve meetings that move markets, with the
 * latest reading of each number loaded live from the St. Louis Fed's public data.
 *
 * Dates were copied in November 2026 from the Federal Reserve's meeting calendar, the
 * Bureau of Labor Statistics release schedule and the Bureau of Economic Analysis
 * release schedule. They are built into the app, so they don't update on their own.
 */
public class EconActivity extends Activity {
    private static final int FED = 0, MINUTES = 1, CPI = 2, PPI = 3, PCE = 4, GDP = 5,
            JOBS = 6;
    private static final String[] NAMES = {
            "Fed interest rate decision", "Fed meeting minutes",
            "CPI inflation report", "PPI wholesale inflation report",
            "PCE inflation, income and spending", "GDP report", "Jobs report"};
    private static final String[] TIMES = {"2:00 PM ET", "2:00 PM ET", "8:30 AM ET",
            "8:30 AM ET", "8:30 AM ET", "8:30 AM ET", "8:30 AM ET"};
    private static final String[] IMPACT = {"Very high", "Medium", "Very high", "Medium",
            "High", "High", "Very high"};
    private static final String[] WHY = {
            "The Fed says whether it is raising, cutting or holding rates. The chair's "
                    + "press conference at 2:30 often moves markets more than the decision.",
            "The detailed notes from the last Fed meeting. Traders read them for hints "
                    + "about what comes next.",
            "How much consumer prices rose. A number above or below forecasts changes "
                    + "what traders expect from the Fed, so stocks and bonds can jump.",
            "Prices that producers get for their goods. It can hint at where consumer "
                    + "prices are heading.",
            "The inflation measure the Fed itself watches most, released with figures "
                    + "on income and spending.",
            "How fast the whole economy grew. Each quarter gets three estimates: "
                    + "advance, second and third. The advance one moves markets most.",
            "Jobs added last month and the unemployment rate. A big miss either way can "
                    + "swing the whole market at the open."};
    /** Which filter each kind of event belongs to: 1 Fed, 2 inflation, 3 jobs, 4 growth. */
    private static final int[] GROUP = {1, 1, 2, 2, 2, 4, 3};
    private static final String[] FILTERS = {"All", "Fed", "Inflation", "Jobs", "Growth"};

    // Each date is year*10000 + month*100 + day.
    private static final int[] FED_DAYS = {20261209, 20270127, 20270317, 20270428, 20270609,
            20270728, 20270915, 20271027, 20271208};
    /** Minutes come out three weeks after each decision. */
    private static final int[] MINUTES_DAYS = {20261230, 20270217, 20270407, 20270519,
            20270630, 20270818, 20271006, 20271117, 20271229};
    private static final int[] CPI_DAYS = {20261210, 20270113, 20270211, 20270310, 20270413,
            20270512, 20270610, 20270714, 20270811, 20270914, 20271014, 20271110, 20271210};
    private static final int[] PPI_DAYS = {20261215, 20270114, 20270212, 20270312, 20270414,
            20270513, 20270611, 20270715, 20270810, 20270910, 20271015, 20271112, 20271215};
    private static final int[] JOBS_DAYS = {20261204, 20270108, 20270205, 20270305, 20270402,
            20270507, 20270604, 20270702, 20270806, 20270903, 20271008, 20271105, 20271203};
    /** GDP and the PCE report come out together. */
    private static final int[] BEA_DAYS = {20261223, 20270128, 20270225, 20270331, 20270429,
            20270527, 20270625, 20270729, 20270825, 20270930, 20271028, 20271124, 20271222};
    private static final String[] GDP_LABELS = {
            "3rd quarter 2026, third estimate", "4th quarter 2026, advance estimate",
            "4th quarter 2026, second estimate", "4th quarter 2026, third estimate",
            "1st quarter 2027, advance estimate", "1st quarter 2027, second estimate",
            "1st quarter 2027, third estimate", "2nd quarter 2027, advance estimate",
            "2nd quarter 2027, second estimate", "2nd quarter 2027, third estimate",
            "3rd quarter 2027, advance estimate", "3rd quarter 2027, second estimate",
            "3rd quarter 2027, third estimate"};

    private static final class Event {
        int day;
        int type;
        String detail = "";
    }

    private final List<Event> events = new ArrayList<>();
    /** The latest reading for each kind of event, once loaded. */
    private final Map<Integer, String> latest = new HashMap<>();
    private final List<String> numbers = new ArrayList<>();
    private LinearLayout list;
    private LinearLayout numbersCard;
    private LinearLayout filterRow;
    private int filter;
    private boolean alive = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "m_econ")) {
            Toast.makeText(this, "Unlock the Economic calendar in the skill tree first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildEvents();

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.topBar(this, "Economic calendar"), Ui.full(this, 0));

        root.addView(Ui.heading(this, "Where things stand now"), Ui.full(this, 16));
        numbersCard = new LinearLayout(this);
        numbersCard.setOrientation(LinearLayout.VERTICAL);
        numbersCard.setBackground(Ui.card(this, 18));
        numbersCard.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18),
                Ui.dp(this, 14));
        root.addView(numbersCard, Ui.full(this, 10));

        root.addView(Ui.heading(this, "Coming up"), Ui.full(this, 24));
        filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(filterRow, Ui.full(this, 10));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, Ui.full(this, 0));

        root.addView(Ui.text(this, "Every Thursday at 8:30 AM ET the weekly jobless "
                + "claims number also comes out. It is a quick read on layoffs.", 13,
                Ui.SUB), Ui.full(this, 18));
        root.addView(Ui.text(this, "Dates come from the Federal Reserve, the Bureau of "
                + "Labor Statistics and the Bureau of Economic Analysis, as published in "
                + "November 2026. They can change (2027 Fed dates are marked tentative, "
                + "and government shutdowns have delayed reports before), and the app "
                + "doesn't update them by itself. Minutes dates are worked out as three "
                + "weeks after each meeting. The live numbers come from the St. Louis "
                + "Fed's FRED database. This is information, not advice.", 12, Ui.SUB),
                Ui.full(this, 12));

        setContentView(scroll);
        Bg.apply(this, scroll);
        paintNumbers(true);
        paint();
        loadNumbers();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    private void add(int[] days, int type, String[] details) {
        for (int i = 0; i < days.length; i++) {
            Event e = new Event();
            e.day = days[i];
            e.type = type;
            if (details != null) {
                e.detail = details[i];
            } else if (type == CPI || type == PPI || type == JOBS || type == PCE) {
                // These reports cover the month before they come out.
                Calendar c = Calendar.getInstance();
                c.set(days[i] / 10000, days[i] / 100 % 100 - 1, 1);
                c.add(Calendar.MONTH, -1);
                e.detail = "Covers " + new SimpleDateFormat("MMMM yyyy", Locale.US)
                        .format(c.getTime());
            }
            events.add(e);
        }
    }

    private void buildEvents() {
        add(FED_DAYS, FED, null);
        add(MINUTES_DAYS, MINUTES, null);
        add(CPI_DAYS, CPI, null);
        add(PPI_DAYS, PPI, null);
        add(JOBS_DAYS, JOBS, null);
        add(BEA_DAYS, GDP, GDP_LABELS);
        add(BEA_DAYS, PCE, null);
        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event x, Event y) {
                return x.day != y.day ? Integer.compare(x.day, y.day)
                        : Integer.compare(x.type, y.type);
            }
        });
    }

    private void paintNumbers(boolean loading) {
        numbersCard.removeAllViews();
        if (numbers.isEmpty()) {
            numbersCard.addView(Ui.text(this, MarketData.netOff
                    ? "Internet is switched off in Settings, so the latest numbers can't "
                    + "be loaded. The dates below still work."
                    : (loading ? "Loading the latest numbers..."
                    : "Couldn't load the latest numbers. Check your internet. The dates "
                    + "below still work."), 14, Ui.SUB));
            return;
        }
        for (String line : numbers) {
            String[] parts = line.split("\\|", 3);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(Ui.text(this, parts[0], 15, Ui.TEXT));
            if (parts.length > 2) left.addView(Ui.text(this, parts[2], 12, Ui.SUB));
            row.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = Ui.text(this, parts.length > 1 ? parts[1] : "", 17, Ui.ACCENT);
            v.setTypeface(Ui.medium());
            row.addView(v);
            numbersCard.addView(row, Ui.full(this, 8));
        }
    }

    private void paint() {
        filterRow.removeAllViews();
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            Button bt = Ui.button(this, FILTERS[i], i == filter);
            bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            bt.setPadding(0, 0, 0, 0);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filter = idx;
                    paint();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 44), i == 2 ? 1.3f : 1f);
            int m = Ui.dp(this, 2);
            lp.setMargins(m, 0, m, 0);
            filterRow.addView(bt, lp);
        }

        list.removeAllViews();
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.YEAR) * 10000 + (now.get(Calendar.MONTH) + 1) * 100
                + now.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE, MMM d", Locale.US);
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.US);
        int shown = 0;
        int lastMonth = -1;
        for (Event e : events) {
            if (e.day < today) continue;
            if (filter != 0 && GROUP[e.type] != filter) continue;
            Calendar c = Calendar.getInstance();
            c.set(e.day / 10000, e.day / 100 % 100 - 1, e.day % 100, 12, 0, 0);
            int month = e.day / 100;
            if (month != lastMonth) {
                list.addView(Ui.heading(this, monthFmt.format(c.getTime())),
                        Ui.full(this, 20));
                lastMonth = month;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = Ui.card(this, 18);
            if (shown == 0) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            card.setBackground(bg);
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            TextView name = Ui.text(this, NAMES[e.type], 17, Ui.TEXT);
            name.setTypeface(Ui.medium());
            card.addView(name);
            long days = Math.round((c.getTimeInMillis() - noon(now)) / 86400000.0);
            String when = e.day == today ? "Today" : (days == 1 ? "Tomorrow"
                    : "In " + days + " days");
            card.addView(Ui.text(this, dayFmt.format(c.getTime()) + "  •  " + TIMES[e.type]
                    + "  •  " + when, 13, Ui.ACCENT), Ui.full(this, 4));
            String facts = "Impact: " + IMPACT[e.type];
            if (!e.detail.isEmpty()) facts = e.detail + "  •  " + facts;
            card.addView(Ui.text(this, facts, 13, Ui.TEXT), Ui.full(this, 4));
            String last = latest.get(e.type);
            if (last != null) {
                card.addView(Ui.text(this, "Last reading: " + last, 13, Ui.TEXT),
                        Ui.full(this, 2));
            }
            // Explain each kind of event the first time it appears in the list.
            if (shown < 12) {
                card.addView(Ui.text(this, WHY[e.type], 12, Ui.SUB), Ui.full(this, 6));
            }
            list.addView(card, Ui.full(this, 10));
            shown++;
        }
        if (shown == 0) {
            list.addView(Ui.text(this, "The dates built into this version have all "
                    + "passed. Update the app for the new schedule.", 15, Ui.TEXT),
                    Ui.full(this, 18));
        }
    }

    private static long noon(Calendar now) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---------- live numbers ----------

    /** One series from the St. Louis Fed: dates and values, oldest first. */
    private static final class Fred {
        final List<String> dates = new ArrayList<>();
        final List<Double> values = new ArrayList<>();

        double last() {
            return values.get(values.size() - 1);
        }

        /** Percent change from the reading this many steps earlier. */
        double changeFrom(int stepsBack) {
            double then = values.get(values.size() - 1 - stepsBack);
            return (last() - then) / then * 100;
        }

        /** The month of the latest reading, like "Sep 2026". */
        String month() {
            try {
                return new SimpleDateFormat("MMM yyyy", Locale.US).format(
                        new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                .parse(dates.get(dates.size() - 1)));
            } catch (Exception e) {
                return "";
            }
        }

        String day() {
            try {
                return new SimpleDateFormat("MMM d", Locale.US).format(
                        new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                .parse(dates.get(dates.size() - 1)));
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static Fred fred(String id) {
        Fred f = new Fred();
        try {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -30);
            String start = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            String body = MarketData.http("https://fred.stlouisfed.org/graph/fredgraph.csv?id="
                    + id + "&cosd=" + start);
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].trim().split(",");
                if (parts.length < 2) continue;
                try {
                    f.values.add(Double.parseDouble(parts[1]));
                    f.dates.add(parts[0]);
                } catch (NumberFormatException e) {
                    // Missing readings are listed as a "." instead of a number.
                }
            }
        } catch (Exception ignored) {
        }
        return f;
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    private void loadNumbers() {
        if (MarketData.netOff) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> out = new ArrayList<>();
                final Map<Integer, String> last = new HashMap<>();

                Fred lo = fred("DFEDTARL");
                Fred hi = fred("DFEDTARU");
                if (!lo.values.isEmpty() && !hi.values.isEmpty()) {
                    String range = String.format(Locale.US, "%.2f%% to %.2f%%", lo.last(),
                            hi.last());
                    out.add("Fed interest rate|" + range + "|The target range for overnight "
                            + "loans between banks");
                    last.put(FED, "target range " + range);
                    last.put(MINUTES, "target range " + range);
                }
                Fred cpi = fred("CPIAUCSL");
                Fred core = fred("CPILFESL");
                if (cpi.values.size() > 12) {
                    String v = pct(cpi.changeFrom(12));
                    String coreText = core.values.size() > 12
                            ? ", core " + pct(core.changeFrom(12)) : "";
                    out.add("CPI inflation|" + v + "|Consumer prices over the past year, "
                            + cpi.month() + coreText);
                    last.put(CPI, v + " over the year" + coreText + " (" + cpi.month() + ")");
                }
                Fred ppi = fred("PPIFIS");
                if (ppi.values.size() > 12) {
                    String v = pct(ppi.changeFrom(12));
                    out.add("PPI inflation|" + v + "|Producer prices over the past year, "
                            + ppi.month());
                    last.put(PPI, v + " over the year (" + ppi.month() + ")");
                }
                Fred pce = fred("PCEPI");
                Fred pceCore = fred("PCEPILFE");
                if (pce.values.size() > 12) {
                    String v = pct(pce.changeFrom(12));
                    String coreText = pceCore.values.size() > 12
                            ? ", core " + pct(pceCore.changeFrom(12)) : "";
                    out.add("PCE inflation|" + v + "|The Fed's preferred measure, "
                            + pce.month() + coreText);
                    last.put(PCE, v + " over the year" + coreText + " (" + pce.month() + ")");
                }
                Fred jobless = fred("UNRATE");
                Fred payrolls = fred("PAYEMS");
                if (!jobless.values.isEmpty()) {
                    String v = pct(jobless.last());
                    String added = "";
                    if (payrolls.values.size() > 1) {
                        long change = Math.round(payrolls.last()
                                - payrolls.values.get(payrolls.values.size() - 2));
                        added = ", jobs " + (change >= 0 ? "up " : "down ")
                                + String.format(Locale.US, "%,d", Math.abs(change) * 1000);
                    }
                    out.add("Unemployment rate|" + v + "|" + jobless.month() + added);
                    last.put(JOBS, v + " unemployment" + added + " (" + jobless.month() + ")");
                }
                Fred gdp = fred("A191RL1Q225SBEA");
                if (!gdp.values.isEmpty()) {
                    String v = pct(gdp.last());
                    out.add("GDP growth|" + v + "|Latest quarter, at a yearly rate, after "
                            + "inflation");
                    last.put(GDP, v + " at a yearly rate");
                }
                Fred claims = fred("ICSA");
                if (!claims.values.isEmpty()) {
                    out.add("Weekly jobless claims|" + String.format(Locale.US, "%,d",
                            Math.round(claims.last())) + "|New claims, week ending "
                            + claims.day());
                }
                Fred ten = fred("DGS10");
                if (!ten.values.isEmpty()) {
                    out.add("10-year Treasury yield|" + String.format(Locale.US, "%.2f%%",
                            ten.last()) + "|As of " + ten.day());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!alive || isFinishing()) return;
                        numbers.clear();
                        numbers.addAll(out);
                        latest.clear();
                        latest.putAll(last);
                        paintNumbers(false);
                        paint();
                    }
                });
            }
        }).start();
    }
}
