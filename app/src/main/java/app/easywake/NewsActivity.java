package app.easywake;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Market headlines from public news feeds. Tapping one opens the story in your browser. */
public class NewsActivity extends Activity {
    private static final String CNBC = "https://www.cnbc.com/id/100003114/device/rss/rss.html";
    private static final String MARKETWATCH =
            "https://feeds.marketwatch.com/marketwatch/topstories";
    private static final String YAHOO_TICKERS =
            "https://feeds.finance.yahoo.com/rss/2.0/headline?region=US&lang=en-US&s=";

    private static final class Story {
        String title = "";
        String link = "";
        String source = "";
        long when;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView footer;
    private final Button[] tabs = new Button[2];
    private int tab;
    private int generation;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Tree.owned(this, "m_news")) {
            Toast.makeText(this, "Unlock the news feed in the skill tree first.",
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

        root.addView(Ui.topBar(this, "Market news"), Ui.full(this, 0));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Top stories", "My tickers"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            Button bt = Ui.button(this, names[i], false);
            bt.setTextSize(14);
            bt.setMinHeight(Ui.dp(this, 42));
            bt.setMinimumHeight(Ui.dp(this, 42));
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = idx;
                    paintTabs();
                    load();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f);
            int g = Ui.dp(this, 4);
            p.setMargins(g, 0, g, 0);
            row.addView(bt, p);
            tabs[i] = bt;
        }
        root.addView(row, Ui.full(this, 14));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, Ui.full(this, 6));

        footer = Ui.text(this, "", 12, Ui.SUB);
        root.addView(footer, Ui.full(this, 14));

        setContentView(scroll);
        Bg.apply(this, scroll);
        paintTabs();
        load();
    }

    private void paintTabs() {
        for (int i = 0; i < 2; i++) {
            boolean on = i == tab;
            tabs[i].setBackground(on ? Ui.gold(this, 21) : Ui.ripple(this, Ui.card(this, 21),
                    21));
            tabs[i].setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
        }
    }

    /** The tickers to pull headlines for: watchlist plus portfolio, or the indexes. */
    private String tickers() {
        List<String> all = new ArrayList<>(MarketData.list(this, "marketWatch", ""));
        for (Book.Pos p : Book.load(this, "portfolio").positions) {
            if (!all.contains(p.sym)) all.add(p.sym);
        }
        for (Book.Pos p : Book.load(this, "paper").positions) {
            if (!all.contains(p.sym)) all.add(p.sym);
        }
        if (all.isEmpty()) {
            all.add("^GSPC");
            all.add("^DJI");
            all.add("^IXIC");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < all.size() && i < 12; i++) {
            if (sb.length() > 0) sb.append(',');
            sb.append(all.get(i));
        }
        return sb.toString();
    }

    private void load() {
        final int gen = ++generation;
        final int which = tab;
        list.removeAllViews();
        footer.setText("Loading headlines...");
        final String symbols = tickers();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Story> stories = new ArrayList<>();
                String error = "";
                try {
                    if (which == 0) {
                        try {
                            stories.addAll(parse(MarketData.http(CNBC), "CNBC"));
                        } catch (Exception e) {
                            error = String.valueOf(e.getMessage());
                        }
                        try {
                            stories.addAll(parse(MarketData.http(MARKETWATCH), "MarketWatch"));
                        } catch (Exception e) {
                            error = String.valueOf(e.getMessage());
                        }
                    } else {
                        stories.addAll(parse(MarketData.http(YAHOO_TICKERS
                                + URLEncoder.encode(symbols, "UTF-8")), "Yahoo Finance"));
                    }
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                }
                Collections.sort(stories, new Comparator<Story>() {
                    @Override
                    public int compare(Story x, Story y) {
                        return Long.compare(y.when, x.when);
                    }
                });
                final String err = error;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen == generation && !isFinishing()) show(stories, err, symbols);
                    }
                });
            }
        }).start();
    }

    private void show(List<Story> stories, String error, String symbols) {
        list.removeAllViews();
        if (stories.isEmpty()) {
            footer.setText(MarketData.netOff
                    ? "Internet is switched off for this app. Turn it on in Menu, Settings."
                    : "Couldn't load headlines. Check your internet connection."
                    + (error.isEmpty() ? "" : " (Details: " + error + ")"));
            return;
        }
        int shown = 0;
        for (final Story s : stories) {
            if (shown++ >= 40) break;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.ripple(this, Ui.card(this, 18), 18));
            card.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 12));
            TextView t = Ui.text(this, s.title, 16, Ui.TEXT);
            t.setLineSpacing(Ui.dp(this, 2), 1f);
            card.addView(t);
            card.addView(Ui.text(this, s.source + (s.when > 0 ? "  •  " + ago(s.when)
                    : ""), 12, Ui.ACCENT), Ui.full(this, 6));
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(s.link)));
                    } catch (Exception e) {
                        Toast.makeText(NewsActivity.this, "Couldn't open that story.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            list.addView(card, Ui.full(this, 10));
        }
        footer.setText(tab == 0
                ? "Headlines from CNBC and MarketWatch. Tapping a story opens it in your "
                + "browser."
                : "Headlines from Yahoo Finance for: " + symbols.replace(",", ", ")
                + ". Add tickers to your watchlist or portfolio to change this list.");
    }

    private static String ago(long when) {
        long mins = Math.max(0, (System.currentTimeMillis() - when) / 60000);
        if (mins < 60) return mins + " min ago";
        long hours = mins / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    private static String tag(String item, String name) {
        Matcher m = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(item);
        if (!m.find()) return "";
        String v = m.group(1).trim();
        if (v.startsWith("<![CDATA[")) {
            int end = v.lastIndexOf("]]>");
            v = v.substring(9, end > 9 ? end : v.length());
        }
        return v.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&#x27;", "'").replace("&nbsp;", " ").trim();
    }

    private static long date(String s) {
        String[] patterns = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ssZ"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                if (p.endsWith("'Z'")) f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                Date d = f.parse(s.trim());
                if (d != null) return d.getTime();
            } catch (Exception e) {
                // Try the next pattern.
            }
        }
        return 0;
    }

    /** Pulls the stories out of an RSS feed. Tolerant of feeds that aren't perfectly formed. */
    private static List<Story> parse(String xml, String source) {
        List<Story> out = new ArrayList<>();
        String[] parts = xml.split("<item[\\s>]");
        for (int i = 1; i < parts.length; i++) {
            String item = parts[i];
            Story s = new Story();
            s.title = tag(item, "title");
            s.link = tag(item, "link");
            s.when = date(tag(item, "pubDate"));
            s.source = source;
            if (!s.title.isEmpty() && s.link.startsWith("http")) out.add(s);
        }
        return out;
    }
}
