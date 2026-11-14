package app.easywake;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Month calendar. Days with an alarm get a gold dot; tap a day to see its alarms. */
public class CalendarActivity extends Activity {
    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private final Calendar month = Calendar.getInstance();
    private final Calendar picked = Calendar.getInstance();
    private TextView monthTitle;
    private LinearLayout grid;
    private TextView dayTitle;
    private LinearLayout dayList;
    private List<Alarm> alarms = new ArrayList<>();
    private List<Events.Event> events = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        month.set(Calendar.DAY_OF_MONTH, 1);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = Ui.topBar(this, "Calendar");
        Button today = Ui.button(this, "Today", false);
        today.setTextSize(14);
        today.setMinHeight(Ui.dp(this, 40));
        today.setMinimumHeight(Ui.dp(this, 40));
        today.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar now = Calendar.getInstance();
                month.setTimeInMillis(now.getTimeInMillis());
                month.set(Calendar.DAY_OF_MONTH, 1);
                picked.setTimeInMillis(now.getTimeInMillis());
                paint();
            }
        });
        bar.addView(today, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        root.addView(bar, Ui.full(this, 0));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, 22));
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 14));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(arrow("‹", -1), new LinearLayout.LayoutParams(Ui.dp(this, 48),
                Ui.dp(this, 48)));
        monthTitle = Ui.text(this, "", 20, Ui.TEXT);
        monthTitle.setTypeface(Ui.medium());
        monthTitle.setGravity(Gravity.CENTER);
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(arrow("›", 1), new LinearLayout.LayoutParams(Ui.dp(this, 48),
                Ui.dp(this, 48)));
        card.addView(nav);

        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        for (String d : DAY_LETTERS) {
            TextView t = Ui.text(this, d, 12, Ui.SUB);
            t.setGravity(Gravity.CENTER);
            week.addView(t, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(week, Ui.full(this, 6));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        card.addView(grid, Ui.full(this, 6));
        root.addView(card, Ui.full(this, 14));

        dayTitle = Ui.text(this, "", 18, Ui.TEXT);
        dayTitle.setTypeface(Ui.medium());
        root.addView(dayTitle, Ui.full(this, 22));
        dayList = new LinearLayout(this);
        dayList.setOrientation(LinearLayout.VERTICAL);
        root.addView(dayList, Ui.full(this, 8));

        setContentView(scroll);
        Bg.apply(this, scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        alarms = Store.load(this);
        events = Events.load(this);
        paint();
    }

    private TextView arrow(String glyph, final int delta) {
        TextView t = Ui.text(this, glyph, 32, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(this, Ui.round(this, 0x00000000, 24), 24));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                month.add(Calendar.MONTH, delta);
                paint();
            }
        });
        return t;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Alarms that will ring on the given day. */
    private List<Alarm> alarmsOn(Calendar day) {
        List<Alarm> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        Calendar endOfDay = (Calendar) day.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        if (endOfDay.getTimeInMillis() < now) return out;
        int bit = day.get(Calendar.DAY_OF_WEEK) - 1;
        for (Alarm a : alarms) {
            if (!a.enabled) continue;
            if (a.days == 0) {
                Calendar t = Calendar.getInstance();
                t.setTimeInMillis(a.nextTrigger(now));
                if (sameDay(t, day)) out.add(a);
            } else if ((a.days & (1 << bit)) != 0) {
                Calendar at = (Calendar) day.clone();
                at.set(Calendar.HOUR_OF_DAY, a.hour);
                at.set(Calendar.MINUTE, a.minute);
                at.set(Calendar.SECOND, 0);
                if (at.getTimeInMillis() > now) out.add(a);
            }
        }
        return out;
    }

    private void paint() {
        monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(month.getTime()));
        grid.removeAllViews();

        Calendar today = Calendar.getInstance();
        Calendar cur = (Calendar) month.clone();
        cur.set(Calendar.DAY_OF_MONTH, 1);
        int lead = cur.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = cur.getActualMaximum(Calendar.DAY_OF_MONTH);
        int cells = lead + daysInMonth;
        int rows = (cells + 6) / 7;

        int dayNum = 1;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 7; c++) {
                int index = r * 7 + c;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                if (index >= lead && dayNum <= daysInMonth) {
                    final Calendar day = (Calendar) month.clone();
                    day.set(Calendar.DAY_OF_MONTH, dayNum);
                    boolean isToday = sameDay(day, today);
                    boolean isPicked = sameDay(day, picked);

                    TextView num = Ui.text(this, String.valueOf(dayNum), 16,
                            isToday ? Ui.ON_ACCENT : Ui.TEXT);
                    num.setGravity(Gravity.CENTER);
                    if (isToday) {
                        num.setTypeface(Ui.medium());
                        GradientDrawable g = Ui.gold(this, 17);
                        num.setBackground(g);
                    } else if (isPicked) {
                        GradientDrawable g = Ui.round(this, 0x00000000, 17);
                        g.setStroke(Ui.dp(this, 2), Ui.ACCENT);
                        num.setBackground(g);
                    }
                    cell.addView(num, new LinearLayout.LayoutParams(Ui.dp(this, 34),
                            Ui.dp(this, 34)));

                    // Gold dot = an alarm that day; blue dot = an event.
                    LinearLayout dots = new LinearLayout(this);
                    dots.setOrientation(LinearLayout.HORIZONTAL);
                    boolean hasAlarm = !alarmsOn(day).isEmpty();
                    boolean hasEvent = !Events.on(events, Events.dayOf(day)).isEmpty();
                    int[] colors = {Ui.ACCENT, 0xFF7FB8E6};
                    boolean[] show = {hasAlarm, hasEvent};
                    for (int k = 0; k < 2; k++) {
                        View dot = new View(this);
                        GradientDrawable dd = new GradientDrawable();
                        dd.setShape(GradientDrawable.OVAL);
                        dd.setColor(colors[k]);
                        dot.setBackground(dd);
                        dot.setVisibility(show[k] ? View.VISIBLE : View.GONE);
                        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                                Ui.dp(this, 5), Ui.dp(this, 5));
                        dp.setMargins(Ui.dp(this, 1), 0, Ui.dp(this, 1), 0);
                        dots.addView(dot, dp);
                    }
                    LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 5));
                    dotsParams.topMargin = Ui.dp(this, 3);
                    cell.addView(dots, dotsParams);

                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            picked.setTimeInMillis(day.getTimeInMillis());
                            paint();
                        }
                    });
                    dayNum++;
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f));
            }
            grid.addView(row);
        }

        dayTitle.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                .format(picked.getTime()));
        dayList.removeAllViews();

        // Events first, then alarms.
        final int pickedDay = Events.dayOf(picked);
        for (final Events.Event e : Events.on(events, pickedDay)) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable eg = Ui.card(this, 18);
            eg.setStroke(Ui.dp(this, 2), 0xFF7FB8E6);
            item.setBackground(Ui.ripple(this, eg, 18));
            item.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18),
                    Ui.dp(this, 12));
            TextView et = Ui.text(this, e.title, 18, Ui.TEXT);
            et.setTypeface(Ui.medium());
            item.addView(et);
            item.addView(Ui.text(this, (e.minutes >= 0 ? Ui.timeText(this, e.minutes / 60,
                    e.minutes % 60) : "All day") + "  \u2022  event  \u2022  tap to remove",
                    13, Ui.SUB));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new android.app.AlertDialog.Builder(CalendarActivity.this)
                            .setMessage("Remove \"" + e.title + "\"?")
                            .setPositiveButton("Remove",
                                    new android.content.DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                android.content.DialogInterface d, int w) {
                                            events.remove(e);
                                            Events.save(CalendarActivity.this, events);
                                            paint();
                                        }
                                    })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
            dayList.addView(item, Ui.full(this, 8));
        }

        Button addEvent = Ui.button(this, "+  Add an event on this day", false);
        addEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addEvent(pickedDay);
            }
        });
        dayList.addView(addEvent, Ui.full(this, 8));

        List<Alarm> list = alarmsOn(picked);
        if (list.isEmpty()) {
            dayList.addView(Ui.text(this, "No alarms on this day.", 15, Ui.SUB),
                    Ui.full(this, 12));
        } else {
            for (Alarm a : list) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackground(Ui.card(this, 18));
                item.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18),
                        Ui.dp(this, 12));
                TextView t = Ui.text(this, Ui.timeText(this, a.hour, a.minute), 24, Ui.TEXT);
                t.setTypeface(Ui.light());
                item.addView(t);
                String sub = (a.label != null && !a.label.isEmpty() ? a.label + "  •  " : "")
                        + Ui.daysText(a.days);
                item.addView(Ui.text(this, sub, 13, Ui.SUB));
                dayList.addView(item, Ui.full(this, 8));
            }
        }
    }

    /** Asks for a title and an optional time, then saves the event. */
    private void addEvent(final int day) {
        final int[] minutes = {-1};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final android.widget.EditText title = new android.widget.EditText(this);
        title.setSingleLine(true);
        title.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setHint("What's happening?");
        box.addView(title);
        final Button time = new Button(this);
        time.setAllCaps(false);
        time.setText("All day  (tap to set a time)");
        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.TimePickerDialog(CalendarActivity.this,
                        new android.app.TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(android.widget.TimePicker p, int h, int m) {
                                minutes[0] = h * 60 + m;
                                time.setText("At " + Ui.timeText(CalendarActivity.this, h, m));
                            }
                        }, 9, 0, android.text.format.DateFormat.is24HourFormat(
                        CalendarActivity.this)).show();
            }
        });
        box.addView(time);
        new android.app.AlertDialog.Builder(this)
                .setTitle("New event")
                .setView(box)
                .setPositiveButton("Save", new android.content.DialogInterface
                        .OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String t = title.getText().toString().trim();
                        if (t.isEmpty()) return;
                        Events.Event e = new Events.Event();
                        e.day = day;
                        e.title = t;
                        e.minutes = minutes[0];
                        events.add(e);
                        Events.save(CalendarActivity.this, events);
                        paint();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
