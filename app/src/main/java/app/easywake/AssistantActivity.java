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
            "Tell me a joke", "Crazy fact", "My points", "What can I unlock?",
            "Timer 10 minutes", "Concrete 10 x 12 x 4", "5 feet in meters", "Flip a coin",
            "Show my memos", "Play the fan"
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
        Stats.add(this, "helper", 1);
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

        // ----- calculator -----
        String sum = Calc.answer(raw);
        if (sum != null) return sum;

        // ----- conversions, job-site sums, timers, dates, small talk -----
        String extra = Helper2.answer(this, raw);
        if (extra != null) return extra;

        // ----- dictionary -----
        Matcher def = Pattern.compile("(?i)^\\s*(?:define|definition of|meaning of|"
                + "what does)\\s+(?:the word\\s+)?([a-z'-]+)(?:\\s+mean)?\\s*\\??$").matcher(raw);
        if (def.find()) {
            // Market terms are answered from the app's own notes first.
            String term = MarketKB.answer(raw);
            if (term != null && Tree.owned(this, "h_market")) return term;
            return define(def.group(1));
        }

        // ----- lookups that were asked for by name -----
        Matcher look = Pattern.compile("(?i)^\\s*(?:look ?up|search for|search|wiki|"
                + "wikipedia|who is|who was|who are|who were)\\s+(.+?)\\s*\\??$")
                .matcher(raw);
        if (look.find()) return lookup(look.group(1));

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

        // ----- newer tools -----
        if (has(q, "sudoku")) {
            open(SudokuActivity.class);
            return "Opening Sudoku.";
        }
        if (has(q, "peg pop", "pegs", "peggle")) {
            if (Tree.owned(this, "pegs")) {
                open(PegsActivity.class);
                return "Opening Peg Pop.";
            }
            return "Peg Pop is locked. It's on the Casino branch of the skill tree (40 "
                    + "Knowledge Points, level 3).";
        }
        if (has(q, "blackjack", "black jack")) {
            if (Tree.owned(this, "blackjack")) {
                open(BlackjackActivity.class);
                return "Opening blackjack.";
            }
            return "Blackjack is locked. It's on the skill tree after the slot (50 "
                    + "Knowledge Points, level 3).";
        }
        if (has(q, "poker", "hold'em", "holdem", "hold em")) {
            if (Tree.owned(this, "holdem")) {
                open(HoldemActivity.class);
                return "Opening Texas Hold'em.";
            }
            return "Texas Hold'em is locked. It's on the skill tree after blackjack (70 "
                    + "Knowledge Points, level 4).";
        }
        if (has(q, "water", "hydrat", "thirsty")) {
            if (Tree.owned(this, "l_water")) {
                open(WaterActivity.class);
                return "Opening your water glass. You've had " + Water.count(this) + " of "
                        + Water.GOAL + " today.";
            }
            return "The water reminder is locked. It's on the Life tools branch of the "
                    + "skill tree (25 Knowledge Points, level 2).";
        }
        if (has(q, "profile", "my stats", "statistics", "degenerate", "getting smarter")) {
            open(ProfileActivity.class);
            return "Opening your profile.";
        }

        if (has(q, "block drop", "blocks", "tetris", "falling block")) {
            if (Tree.owned(this, "blocks")) {
                open(BlocksActivity.class);
                return "Opening Block Drop.";
            }
            return "Block Drop is locked. It's on the Casino branch of the skill tree (40 "
                    + "Knowledge Points, level 3).";
        }
        if (has(q, "price alert", "alerts", "alert me", "set an alert", "set alert")) {
            if (Tree.owned(this, "m_alerts")) {
                open(AlertsActivity.class);
                return "Opening price alerts. Tap New alert to add one.";
            }
            return "Price alerts are locked. They're on the Market branch of the skill tree "
                    + "(40 Knowledge Points, level 3).";
        }
        if (has(q, "economic calendar", "fed meeting", "fomc", "cpi report", "jobs report",
                "inflation report", "next fed", "rate decision")) {
            if (Tree.owned(this, "m_econ")) {
                open(EconActivity.class);
                return "Opening the economic calendar.";
            }
            return "The economic calendar is locked. It's on the Market branch of the skill "
                    + "tree (25 Knowledge Points, level 2).";
        }

        // ----- market questions (needs the Market Q&A unlock) -----
        boolean asking = has(q, "what", "how", "why", "explain", "mean", "define", "tell me",
                "difference", "find", " is a ", " are ") || raw.trim().endsWith("?");
        boolean aboutApp = has(q, "alarm", "sound", "fan", "noise", "loud", "background",
                "memo", "app ");
        if (asking && !aboutApp) {
            String kb = MarketKB.answer(raw);
            if (kb != null) {
                if (Tree.owned(this, "h_market")) return kb;
                return "I can answer market questions like that once you unlock Market Q&A "
                        + "on the Helper branch of the skill tree (30 Knowledge Points, "
                        + "level 2).";
            }
        }
        if (has(q, "portfolio", "my positions", "my stocks")) {
            if (Tree.owned(this, "m_portfolio")) {
                open(PortfolioActivity.class);
                return "Opening your portfolio.";
            }
            return "The portfolio tracker is locked. It's on the Market branch of the skill "
                    + "tree (40 Knowledge Points, level 3).";
        }
        if (has(q, "paper trad", "practice trad", "fake money", "pretend money")) {
            if (Tree.owned(this, "m_paper")) {
                open(PaperActivity.class);
                return "Opening paper trading.";
            }
            return "Paper trading is locked. Unlock the \"Risk and position sizing\" lesson "
                    + "first, then Paper trading (60 Knowledge Points, level 4).";
        }
        if (has(q, "news", "headline")) {
            return "The news feed was removed because the headlines weren't useful. For "
                    + "what's moving markets, try \"economic calendar\" or \"how's the "
                    + "market\".";
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

        // ----- life tools -----
        if (has(q, "weather", "forecast", "temperature", "rain today", "outside")) {
            if (!Tree.owned(this, "l_weather")) {
                return "Weather is locked. It's on the Life tools branch of the skill tree "
                        + "(30 Knowledge Points, level 2).";
            }
            if (!Weather.hasCity(this)) {
                open(WeatherActivity.class);
                return "Pick your city first. Opening Weather.";
            }
            return weatherNow();
        }
        if (has(q, "event", "schedule", "what's planned", "whats planned", "agenda")) {
            String today = Events.todayText(this);
            return today.isEmpty() ? "Nothing on the calendar for today. Open the Calendar "
                    + "and tap a day to add an event." : "Today:\n" + today;
        }
        if (has(q, "recipe", "dinner", "cook", "what to eat", "hungry")) {
            if (Tree.owned(this, "l_recipes")) {
                open(RecipesActivity.class);
                return "Opening dinner recipes.";
            }
            return "Dinner recipes are locked. They're on the Life tools branch of the "
                    + "skill tree (30 Knowledge Points, level 2).";
        }
        if (has(q, "goal")) {
            if (Tree.owned(this, "l_goals")) {
                open(GoalsActivity.class);
                return "Opening your goals.";
            }
            return "The goal maker is locked. It's on the Life tools branch of the skill "
                    + "tree (30 Knowledge Points, level 2).";
        }
        if (has(q, "can't sleep", "cant sleep", "trouble sleeping", "insomnia", "fall asleep",
                "sleep better", "sleep tip")) {
            open(TreeActivity.class);
            return "A few things that help most: keep the same wake time every day, dim the "
                    + "lights in the last hour (this app's Red night mode is under Colors & "
                    + "night mode), skip caffeine within eight hours of bed, and if you're "
                    + "awake after about 20 minutes, get up and do something dull until "
                    + "you're sleepy. The Sleep branch on the skill tree has the full "
                    + "lessons; I've opened it.";
        }

        // ----- wallet, tree and casino -----
        if (has(q, "slot", "casino", "gamble", "spin", "jackpot")) {
            if (Tree.owned(this, "slot")) {
                open(SlotActivity.class);
                return "Opening Lucky Sevens. You have " + Wallet.coupons(this)
                        + " Fun Coupons.";
            }
            return "The slot is locked. Unlock \"Lucky Sevens slot\" in the skill tree "
                    + "(30 Knowledge Points, level 2).";
        }
        if (has(q, "wallet", "gold", "coupon", "balance", "exchange")) {
            return "You have " + Wallet.kp(this) + " Knowledge Points, " + Wallet.gold(this)
                    + " Gold and " + Wallet.coupons(this) + " Fun Coupons. Open the Wallet "
                    + "from Brain gym or the skill tree to move between them.";
        }
        if (has(q, "skill", "tree", "unlock")) {
            open(TreeActivity.class);
            return "Opening the skill tree.";
        }
        if (has(q, "setting", "internet", "offline", "wifi")) {
            open(SettingsActivity.class);
            return "Opening Settings. The internet switch is at the top.";
        }

        // ----- brain gym -----
        if (has(q, "point", "level", "streak", "badge", "xp", "score")) {
            int level = Progress.level(this);
            int streak = Progress.streak(this);
            return "You're level " + level + " (" + Progress.title(level) + ") with "
                    + Scores.points(this) + " Knowledge Points and a " + streak + "-day streak. "
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
        // Anything else phrased as "what is ..." goes to the encyclopedia.
        Matcher what = Pattern.compile("(?i)^\\s*(?:what is|what's|what are|what was|"
                + "what were|tell me about|where is|when was|when did)\\s+(.+?)\\s*\\??$")
                .matcher(raw);
        if (what.find()) return lookup(what.group(1));
        return "I didn't catch that. I'm a simple helper, so try one of these:\n" + help();
    }

    /** Looks a word up in a free online dictionary, falling back to Wikipedia. */
    private String define(final String word) {
        if (!Tree.owned(this, "h_lookup")) {
            return "I can look words up once you unlock Safe lookups on the Helper branch "
                    + "of the skill tree (30 Knowledge Points, level 2).";
        }
        if (MarketData.netOff) {
            return "Internet is switched off in Settings, so I can't look things up.";
        }
        final TextView pending = bubble("Looking up \"" + word + "\"...", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(MarketData.http(
                            "https://api.dictionaryapi.dev/api/v2/entries/en/"
                                    + java.net.URLEncoder.encode(word, "UTF-8")));
                    org.json.JSONArray meanings = arr.getJSONObject(0)
                            .getJSONArray("meanings");
                    StringBuilder sb = new StringBuilder(word.toLowerCase(Locale.US));
                    int shown = 0;
                    for (int i = 0; i < meanings.length() && shown < 4; i++) {
                        org.json.JSONObject m = meanings.getJSONObject(i);
                        org.json.JSONArray defs = m.getJSONArray("definitions");
                        for (int j = 0; j < defs.length() && j < 2 && shown < 4; j++) {
                            sb.append("\n\n").append(m.optString("partOfSpeech"))
                                    .append(": ").append(defs.getJSONObject(j)
                                            .optString("definition"));
                            shown++;
                        }
                    }
                    if (shown == 0) throw new Exception("empty");
                    out = sb + "\n\nSource: Free Dictionary API (Wiktionary).";
                } catch (Exception e) {
                    try {
                        out = wiki(word);
                    } catch (Exception e2) {
                        out = "I couldn't find \"" + word + "\". Check the spelling and "
                                + "your internet.";
                    }
                }
                final String reply = out;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) pending.setText(reply);
                    }
                });
            }
        }).start();
        return null;
    }

    /** Downloads the opening summary of the best-matching Wikipedia article. */
    private static String wiki(String topic) throws Exception {
        String url = "https://en.wikipedia.org/w/api.php?action=query"
                + "&generator=search&gsrlimit=1&prop=extracts&exintro=1"
                + "&explaintext=1&redirects=1&format=json&gsrsearch="
                + java.net.URLEncoder.encode(topic, "UTF-8");
        org.json.JSONObject pages = new org.json.JSONObject(MarketData.http(url))
                .getJSONObject("query").getJSONObject("pages");
        org.json.JSONObject page = pages.getJSONObject(pages.keys().next());
        String title = page.optString("title");
        String text = page.optString("extract").trim();
        if (text.isEmpty()) throw new Exception("empty");
        if (text.length() > 700) {
            int cut = text.lastIndexOf(". ", 700);
            text = text.substring(0, cut > 200 ? cut + 1 : 700) + " ...";
        }
        return title + "\n\n" + text + "\n\nSource: Wikipedia. Anyone can edit it, so "
                + "double-check anything important.";
    }

    /** Fetches a short plain-text summary from Wikipedia. Needs the Safe lookups unlock. */
    private String lookup(final String topicRaw) {
        final String topic = topicRaw.replaceAll("(?i)^(a|an|the)\\s+", "").trim();
        if (topic.isEmpty()) return "Look up what? Try \"look up the Hoover Dam\".";
        if (!Tree.owned(this, "h_lookup")) {
            return "I can look that up once you unlock Safe lookups on the Helper branch of "
                    + "the skill tree (30 Knowledge Points, level 2).";
        }
        if (MarketData.netOff) {
            return "Internet is switched off in Settings, so I can't look things up.";
        }
        final TextView pending = bubble("Looking up \"" + topic + "\"...", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out;
                try {
                    out = wiki(topic);
                } catch (Exception e) {
                    out = "I couldn't find anything for \"" + topic + "\". Try different "
                            + "words, or check your internet.";
                }
                final String reply = out;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) pending.setText(reply);
                    }
                });
            }
        }).start();
        return null;
    }

    private String help() {
        return "• \"Set alarm for 6:30 am\" or \"Wake me at 7\"\n"
                + "• \"Next alarm\"\n"
                + "• \"Memo call the bank\" or \"Remind me to bring lunch\"\n"
                + "• \"Show my memos\"\n"
                + "• \"How's the market?\" or \"Price of AAPL\"\n"
                + "• \"15% of 240\" or \"(40 + 15) / 5\" (calculator)\n"
                + "• \"5 feet in meters\", \"350 f to c\", \"how many cups in a gallon\"\n"
                + "• \"Concrete 10 x 12 x 4\", \"drywall 12 x 8\", \"paint 400\", "
                + "\"stairs 108\", \"board feet 2 x 6 x 10\"\n"
                + "• \"Timer 10 minutes\" or \"remind me in 2 hours to call Dan\"\n"
                + "• \"Tip on 48.50\", \"split 120 by 4\", \"percent change from 50 to 65\"\n"
                + "• \"Days until Christmas\", \"what's the date\", \"flip a coin\", "
                + "\"roll 2 dice\"\n"
                + "• \"What can I unlock?\", \"my level\", \"what's new\"\n"
                + "• \"Define frugal\" (needs Safe lookups)\n"
                + "• \"Look up the Hoover Dam\" or \"Who was Nikola Tesla?\" (needs Safe "
                + "lookups)\n"
                + "• \"How do I find support and resistance?\" (needs Market Q&A)\n"
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

    private String weatherNow() {
        final TextView pending = bubble("Checking the weather...", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Weather.Report r = Weather.fetch(AssistantActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (!r.ok) {
                            pending.setText("I couldn't get the forecast. Check your "
                                    + "internet.");
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(Weather.city(AssistantActivity.this)).append(": ")
                                .append(Weather.deg(r.temp)).append(", ")
                                .append(Weather.describe(r.code).toLowerCase(Locale.US))
                                .append(", feels like ").append(Weather.deg(r.feels))
                                .append(".");
                        if (r.hi.length > 0) {
                            sb.append(" Today's high ").append(Weather.deg(r.hi[0]))
                                    .append(", low ").append(Weather.deg(r.lo[0])).append(".");
                            if (r.rain[0] >= 20) {
                                sb.append(" ").append(r.rain[0]).append("% chance of rain.");
                            }
                        }
                        pending.setText(sb.toString());
                    }
                });
            }
        }).start();
        return null;
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
