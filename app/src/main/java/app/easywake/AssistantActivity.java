package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small built-in helper you can chat with. It matches keywords; it is not an AI, so it
 * only understands the kinds of requests listed in its help.
 */
public class AssistantActivity extends Activity {
    private static final String[] SUGGESTIONS = {
            "What can you do?", "Next alarm", "Set alarm for 7 am", "How's the market?",
            "Tell me a joke", "Crazy fact", "My points", "Show my memos", "Play the fan"
    };

    private LinearLayout chat;
    private ScrollView scroll;
    private EditText input;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, Ui.dp(this, 12));
        root.addView(Ui.topBar(this, "Helper"), Ui.full(this, 0));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(chat, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = Ui.dp(this, 10);
        root.addView(scroll, sp);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (final String s : SUGGESTIONS) {
            TextView chip = Ui.text(this, s, 13, Ui.TEXT);
            chip.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
            chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    send(s);
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = Ui.dp(this, 8);
            chips.addView(chip, cp);
        }
        hs.addView(chips);
        root.addView(hs, Ui.full(this, 8));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.SUB);
        input.setHint("Ask or tell me something");
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submit();
                return true;
            }
        });
        bar.addView(input, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button go = Ui.button(this, "Send", true);
        go.setMinHeight(Ui.dp(this, 46));
        go.setMinimumHeight(Ui.dp(this, 46));
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 46));
        gp.leftMargin = Ui.dp(this, 8);
        bar.addView(go, gp);
        root.addView(bar, Ui.full(this, 8));

        setContentView(root);
        Bg.apply(this, root);

        bubble(greeting() + " I'm the built-in helper. I can set alarms, save memos, check "
                + "the market, play sleep sounds, tell jokes and facts, and explain how "
                + "things in this app work. Tap a suggestion below or type what you need.",
                false);
    }

    private String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 5) return "Up late.";
        if (h < 12) return "Good morning.";
        if (h < 18) return "Good afternoon.";
        return "Good evening.";
    }

    private void submit() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        send(text);
    }

    private void send(String text) {
        bubble(text, true);
        String reply;
        try {
            reply = answer(text);
        } catch (Exception e) {
            reply = "Something went wrong with that one. Try wording it differently.";
        }
        if (reply != null) bubble(reply, false);
    }

    private TextView bubble(String text, boolean mine) {
        TextView t = Ui.text(this, text, 15, mine ? Ui.ON_ACCENT : Ui.TEXT);
        t.setBackground(mine ? Ui.gold(this, 18) : Ui.card(this, 18));
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        if (mine) lp.leftMargin = Ui.dp(this, 48);
        else lp.rightMargin = Ui.dp(this, 48);
        chat.addView(t, lp);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
        return t;
    }

    private static boolean has(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) return true;
        }
        return false;
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    /** Works out what was asked and does it. Returns the reply, or null if one comes later. */
    private String answer(String raw) {
        String q = " " + raw.toLowerCase(Locale.US).replaceAll("[?!.,]", " ") + " ";

        // ----- alarms -----
        if (has(q, "set", "wake me", "new alarm", "add alarm", "create")
                && has(q, "alarm", "wake")) {
            return setAlarm(raw);
        }
        if (has(q, "alarm") || has(q, "wake")) {
            return alarmSummary();
        }

        // ----- memos -----
        Matcher memo = Pattern.compile("(?i)^\\s*(?:memo|note|remind me to|remind me|"
                + "remember to|remember|write down)[:\\s]+(.+)$").matcher(raw);
        if (memo.find()) {
            String text = memo.group(1).trim();
            List<MemoActivity.Memo> memos = MemoActivity.load(this);
            MemoActivity.Memo m = new MemoActivity.Memo();
            m.text = text.substring(0, 1).toUpperCase(Locale.US) + text.substring(1);
            m.when = System.currentTimeMillis();
            m.onAlarm = has(q, "remind");
            memos.add(m);
            MemoActivity.save(this, memos);
            return "Saved: \"" + m.text + "\"." + (m.onAlarm
                    ? " I'll show it on the alarm screen when you wake up."
                    : " It's in Memos.");
        }
        if (has(q, "memo", "note", "reminder", "planned", "plans", "to do", "todo")) {
            List<MemoActivity.Memo> memos = MemoActivity.load(this);
            if (memos.isEmpty()) {
                return "You have no memos. Type \"memo\" followed by what to remember, like "
                        + "\"memo call the bank\".";
            }
            StringBuilder sb = new StringBuilder("Your memos:");
            for (int i = memos.size() - 1; i >= 0 && i >= memos.size() - 8; i--) {
                sb.append("\n• ").append(memos.get(i).text);
            }
            return sb.toString();
        }

        // ----- market -----
        Matcher quote = Pattern.compile("(?i)(?:price of|quote for|quote|price|chart of|"
                + "chart for|chart|look up|lookup)\\s+\\$?([A-Za-z0-9.^=\\-]{1,12})\\s*$")
                .matcher(raw.trim());
        if (quote.find() && !has(q, "market")) {
            return quoteFor(quote.group(1));
        }
        if (has(q, "market", "stocks", "s&p", "dow", "nasdaq", "indexes", "indices")) {
            return marketSummary();
        }

        // ----- sleep sounds -----
        if (has(q, "stop") && has(q, "fan", "noise", "sound")) {
            startService(new Intent(this, NoiseService.class)
                    .setAction(NoiseService.ACTION_STOP));
            return "Sleep sound stopped.";
        }
        if (has(q, "fan", "white noise", "pink noise", "brown noise", "sleep sound")) {
            int type = has(q, "white") ? 1 : has(q, "pink") ? 2 : has(q, "brown") ? 3 : 0;
            int minutes = Prefs.getInt(this, "noiseTimer", 60);
            startForegroundService(new Intent(this, NoiseService.class)
                    .setAction(NoiseService.ACTION_START)
                    .putExtra("type", type)
                    .putExtra("minutes", minutes));
            return NoiseGen.NAMES[type] + " is playing" + (minutes > 0 ? " for " + minutes
                    + " minutes" : "") + ". Say \"stop the fan\" to end it, or open Sleep "
                    + "sounds to adjust tone and loudness.";
        }

        // ----- fun -----
        if (has(q, "joke", "funny", "laugh")) return Jokes.next();
        if (has(q, "fact", "teach me", "something cool", "learn something")) return Facts.next();

        // ----- brain gym -----
        if (has(q, "point", "level", "streak", "badge", "xp", "score")) {
            int level = Progress.level(this);
            int streak = Progress.streak(this);
            return "You're level " + level + " (" + Progress.title(level) + ") with "
                    + Scores.points(this) + " points to spend and a " + streak + "-day streak. "
                    + (Progress.threshold(level + 1) - Progress.xp(this))
                    + " more points gets you to level " + (level + 1) + ". Today's daily "
                    + "challenge is " + Games.TITLES[Progress.challengeMode()]
                    + (Progress.challengeDone(this) ? " (already done)." : " (double points).");
        }
        if (has(q, "challenge")) {
            return "Today's daily challenge is " + Games.TITLES[Progress.challengeMode()]
                    + (Progress.challengeDone(this) ? ". You've already done it today."
                    : ". Your first go today pays double. Find it at the top of Brain gym.");
        }
        if (has(q, "brain", "test", "game", "gym", "quiz", "trivia")) {
            open(BrainActivity.class);
            return "Opening Brain gym. There are " + Games.COUNT + " tests; each has a "
                    + "how-to screen before it starts.";
        }

        // ----- date and time -----
        if (has(q, "what time", "the time", "time is it")) {
            return "It's " + DateFormat.getTimeFormat(this).format(new Date()) + ".";
        }
        if (has(q, "today", "date", "what day")) {
            return "Today is " + new SimpleDateFormat("EEEE, MMMM d, yyyy",
                    Locale.getDefault()).format(new Date()) + ".";
        }

        // ----- open a screen -----
        if (has(q, "calendar")) {
            open(CalendarActivity.class);
            return "Opening the calendar. Days with an alarm have a dot.";
        }
        if (has(q, "learn", "lesson", "vault")) {
            open(LearnActivity.class);
            return "Opening Learn, where you spend points on vault facts and lessons.";
        }

        // ----- how-to answers -----
        if (has(q, "background", "wallpaper", "picture", "photo", "draw")) {
            return "Menu, then Background studio. Pick a color theme, import a picture (you "
                    + "can drag, pinch-zoom and rotate it), or draw your own. The slider "
                    + "darkens a picture so the times stay readable.";
        }
        if (has(q, "color", "colour", "night", "dim", "dark mode", "accent")) {
            return "Menu, then Colors & night mode. Pick an accent color, or turn on Dim or "
                    + "Red night for a darker screen late at night.";
        }
        if (has(q, "vibrat")) {
            return "Open an alarm, tap Vibrate, and choose Off, After the fade-in, or From "
                    + "the start. You'll feel a short buzz when you pick one.";
        }
        if (has(q, "song", "mp3", "music", "part of")) {
            return "Open an alarm, tap Wake-up sound, then \"Pick a music file\". A timeline "
                    + "opens where you drag two handles to choose which part plays, and you "
                    + "can preview it before saving.";
        }
        if (has(q, "fade", "gentle", "volume", "loud")) {
            return "Each alarm has a Volume fade-in setting from off to 5 minutes. Loudness "
                    + "follows your phone's alarm volume, not the media volume.";
        }
        if (has(q, "snooze")) {
            return "Each alarm has its own Snooze length, from 5 to 30 minutes. When it "
                    + "rings, the big round button snoozes and \"Stop alarm\" ends it.";
        }
        if (has(q, "vwap", "indicator", "candle", "rsi", "macd", "bollinger", "extended",
                "premarket", "pre-market", "after hours")) {
            return "Open Market and tap any card for the full chart. Under it you can switch "
                    + "chart style, line color, indicators (MA, Bollinger, RSI, MACD, "
                    + "volume, VWAP) and Extended hours. VWAP and extended hours need a "
                    + "stock, ETF or future, not the indexes themselves.";
        }
        if (has(q, "uninstall", "delete the app", "remove the app")) {
            return "Menu, then Uninstall Easy Wake. Or hold the app icon and tap Uninstall.";
        }
        if (has(q, "update", "new version", "version")) {
            return "You install updates from the download link, with Auto Blocker turned "
                    + "off for a moment. Your version number is at the bottom of the home "
                    + "screen.";
        }

        // ----- small talk and help -----
        if (has(q, " hi ", " hey ", "hello", "sup ", "yo ")) {
            return greeting() + " What do you need?";
        }
        if (has(q, "thank", " thx ", "appreciate")) return "Any time.";
        if (has(q, "who are you", "what are you", "are you ai", "are you real")) {
            return "I'm a small helper built into this app. I match keywords rather than "
                    + "truly understanding language, so I only handle what's on my list.";
        }
        if (has(q, "help", "what can you", "can you do", "commands")) {
            return help();
        }
        return "I didn't catch that. I'm a simple helper, so try one of these:\n" + help();
    }

    private String help() {
        return "• \"Set alarm for 6:30 am\" or \"Wake me at 7\"\n"
                + "• \"Next alarm\"\n"
                + "• \"Memo call the bank\" or \"Remind me to bring lunch\"\n"
                + "• \"Show my memos\"\n"
                + "• \"How's the market?\" or \"Price of AAPL\"\n"
                + "• \"Play the fan\", \"Play white noise\", \"Stop the fan\"\n"
                + "• \"Tell me a joke\", \"Crazy fact\"\n"
                + "• \"My points\", \"Daily challenge\"\n"
                + "• \"Open calendar\", \"Open brain gym\"\n"
                + "• \"How do I change the background?\" and other how-to questions";
    }

    private String alarmSummary() {
        List<Alarm> alarms = Store.load(this);
        final long now = System.currentTimeMillis();
        Collections.sort(alarms, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm x, Alarm y) {
                return Long.compare(x.nextTrigger(now), y.nextTrigger(now));
            }
        });
        StringBuilder sb = new StringBuilder();
        for (Alarm a : alarms) {
            if (!a.enabled) continue;
            if (sb.length() == 0) {
                sb.append("Your next alarm is ").append(Ui.timeText(this, a.hour, a.minute))
                        .append(", in ").append(Ui.untilSpan(a.nextTrigger(now))).append(".");
            } else {
                sb.append("\n• ").append(Ui.timeText(this, a.hour, a.minute)).append("  ")
                        .append(Ui.daysText(a.days));
            }
        }
        if (sb.length() == 0) {
            return "No alarms are switched on. Say \"set alarm for 7 am\" and I'll make one.";
        }
        return sb.toString();
    }

    private String setAlarm(String raw) {
        Matcher m = Pattern.compile("(?i)(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?")
                .matcher(raw);
        if (!m.find()) {
            return "What time? Try \"set alarm for 6:30 am\".";
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String ampm = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.US);
        if (hour > 23 || minute > 59) return "That doesn't look like a time I can use.";
        if (ampm.startsWith("p") && hour < 12) hour += 12;
        if (ampm.startsWith("a") && hour == 12) hour = 0;
        boolean guessed = ampm.isEmpty() && hour >= 1 && hour <= 12;

        Alarm a = new Alarm();
        a.id = Store.newId(this);
        a.hour = hour;
        a.minute = minute;
        a.enabled = true;
        String lower = raw.toLowerCase(Locale.US);
        if (lower.contains("every day") || lower.contains("everyday")
                || lower.contains("daily")) {
            a.days = 127;
        } else if (lower.contains("weekday")) {
            a.days = 62;
        } else if (lower.contains("weekend")) {
            a.days = 65;
        }
        Store.put(this, a);
        Scheduler.rescheduleAll(this);
        return "Alarm set for " + Ui.timeText(this, hour, minute) + " ("
                + Ui.daysText(a.days).toLowerCase(Locale.US) + "). "
                + Ui.untilText(a.nextTrigger(System.currentTimeMillis())) + "."
                + (guessed ? " You didn't say am or pm, so I took it as morning. Say it "
                + "again with \"pm\" if you meant evening, and delete this one from the "
                + "home screen." : "")
                + " It uses the default gentle sound; open it on the home screen to change "
                + "anything.";
    }

    private String marketSummary() {
        final TextView pending = bubble("Checking the market...", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder sb = new StringBuilder();
                for (MarketData.Sym sym : MarketData.INDEXES) {
                    MarketData.Series s = MarketData.fetch(sym, 0);
                    if (!s.ok()) continue;
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(sym.name).append(": ").append(MarketData.fmt(s.last));
                    double pct = s.pct();
                    if (!Double.isNaN(pct)) {
                        sb.append(pct >= 0 ? "  ▲ +" : "  ▼ ")
                                .append(MarketData.fmt(pct)).append("%");
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        pending.setText(sb.length() == 0
                                ? "I couldn't reach the market data. Check your internet."
                                : sb + "\nChange is versus the previous close. Data can run "
                                + "a few minutes behind.");
                    }
                });
            }
        }).start();
        return null;
    }

    private String quoteFor(String typed) {
        final String ticker = MarketData.clean(typed);
        if (ticker == null) return "That doesn't look like a ticker symbol.";
        final TextView pending = bubble("Looking up " + ticker + "...", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MarketData.Series s = MarketData.fetch(
                        new MarketData.Sym(ticker, ticker, null), 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (!s.ok()) {
                            pending.setText("I couldn't find a price for " + ticker
                                    + ". Check the symbol and your internet.");
                            return;
                        }
                        pending.setText(ticker + ": " + MarketData.fmt(s.last) + "  "
                                + MarketData.changeText(s) + "\nToday's low "
                                + MarketData.fmt(s.low) + ", high " + MarketData.fmt(s.high)
                                + ". Tap here for the full chart.");
                        pending.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startActivity(new Intent(AssistantActivity.this,
                                        ChartActivity.class)
                                        .putExtra("symbol", ticker)
                                        .putExtra("name", ticker));
                            }
                        });
                    }
                });
            }
        }).start();
        return null;
    }
}
