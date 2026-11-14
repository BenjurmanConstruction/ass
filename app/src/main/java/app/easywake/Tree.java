package app.easywake;

import android.content.Context;

import java.util.List;

/** The skill tree: what can be unlocked with Knowledge Points, and what each thing needs. */
final class Tree {
    static final int LESSON = 0;
    static final int FEATURE = 1;
    static final int PERK = 2;
    static final int SOON = 3;

    static final String[] BRANCHES = {"Casino", "Market", "Wealth", "Life tools", "Helper",
            "Sleep"};
    static final String[] BRANCH_NOTES = {
            "Play-money games. Fun Coupons have no cash value.",
            "Learn to read charts and think about risk. Education only, not advice.",
            "Make your savings work harder.",
            "Everyday tools.",
            "Upgrades for the built-in helper.",
            "Tips for nights when sleep won't come. General information, not medical advice."
    };

    static final class Node {
        final String id;
        final int branch;
        final int kind;
        final String name;
        final String desc;
        final int cost;
        final int level;
        /** Id of a node that must be owned first, or null. */
        final String needs;
        /** Lesson text, for LESSON nodes. */
        final String body;

        Node(String id, int branch, int kind, String name, String desc, int cost, int level,
             String needs, String body) {
            this.id = id;
            this.branch = branch;
            this.kind = kind;
            this.name = name;
            this.desc = desc;
            this.cost = cost;
            this.level = level;
            this.needs = needs;
            this.body = body;
        }
    }

    private static final Node[] BASE = {
            // ----- Casino -----
            new Node("slot", 0, FEATURE, "Lucky Sevens slot",
                    "A progressive penny slot with Mini, Major and Grand jackpots.",
                    30, 2, null, null),
            new Node("freespins", 0, PERK, "Daily free spins",
                    "Five free spins every day.", 50, 3, "slot", null),
            new Node("highroller", 0, PERK, "High roller",
                    "Unlocks 50 and 100 coupon bets.", 90, 5, "slot", null),
            new Node("casino_more", 0, SOON, "More games",
                    "Blackjack and video poker.", 0, 0, null, null),

            // ----- Market -----
            new Node("m_sr", 1, LESSON, "Support and resistance",
                    "Where prices tend to stall, and how to find those levels.", 25, 2, null,
                    "Support is a price where a falling market has repeatedly stopped and "
                            + "bounced, because buyers stepped in there. Resistance is the "
                            + "opposite: a price where rallies have repeatedly stalled as "
                            + "sellers showed up.\n\nHow to find them: open a chart on the 6M "
                            + "or 1Y range and look for prices the line has turned at more "
                            + "than once. Two or three touches make a level worth noting. "
                            + "Round numbers and old highs or lows often act as levels too. "
                            + "Think of them as zones, not exact prices.\n\nTwo things to "
                            + "remember. First, when a level finally breaks, it often swaps "
                            + "roles: old resistance becomes new support. Second, levels fail "
                            + "all the time. They show where others are likely to act, which "
                            + "is useful, but they are not a promise."),
            new Node("m_trend", 1, LESSON, "Trends and trend lines",
                    "Telling an uptrend from a downtrend, and drawing the line.", 25, 2,
                    "m_sr",
                    "An uptrend is a series of higher highs and higher lows: each dip stops "
                            + "above the last one. A downtrend is lower highs and lower lows. "
                            + "If you can't tell, it's probably moving sideways in a range."
                            + "\n\nTo draw a trend line in an uptrend, connect the lows; in a "
                            + "downtrend, connect the highs. Two points make a line, a third "
                            + "touch confirms it. The longer it has held, the more people are "
                            + "watching it.\n\nA trend is considered intact until price "
                            + "clearly breaks the line and then fails to get back above it. "
                            + "Zoom out before deciding: a scary drop on the 1D chart is "
                            + "often a small dip on the 1Y chart."),
            new Node("m_candles", 1, LESSON, "Reading candlesticks",
                    "What each candle on a candle chart is telling you.", 30, 3, null,
                    "Each candle covers one period (five minutes, a day, a week). The thick "
                            + "body runs from the opening price to the closing price. Green "
                            + "means it closed higher than it opened; red means lower. The "
                            + "thin lines above and below, called wicks, show the highest "
                            + "and lowest prices reached.\n\nA long body means one side was "
                            + "in control the whole time. A small body with long wicks means "
                            + "a fight that neither side won. A long lower wick shows sellers "
                            + "pushed price down but buyers pushed it back up, which can "
                            + "hint at support; a long upper wick is the reverse.\n\nSingle "
                            + "candles are weak evidence. They matter more when they appear "
                            + "at a support or resistance level and when volume is high."),
            new Node("m_ma", 1, LESSON, "Using moving averages",
                    "How traders actually use MA 20 and MA 50.", 30, 3, "m_trend",
                    "A moving average smooths price into a line. Turn on MA 20 and MA 50 on "
                            + "any chart to see them.\n\nThree common uses. Direction: if the "
                            + "average is sloping up and price is above it, the trend is up. "
                            + "Dynamic support: in a healthy uptrend, pullbacks often stop "
                            + "near a rising average. Crossovers: the faster average (20) "
                            + "crossing above the slower one (50) is read as strengthening "
                            + "momentum, and crossing below as weakening.\n\nThe catch is "
                            + "lag. Averages are built from past prices, so they confirm a "
                            + "move after it has started and they give false signals in "
                            + "sideways markets, where price just whips back and forth "
                            + "across the line."),
            new Node("m_risk", 1, LESSON, "Risk and position sizing",
                    "Deciding how much to put at risk before anything else.", 40, 4, null,
                    "Professionals think about how much they can lose before how much they "
                            + "might make. A common rule of thumb is to risk no more than 1% "
                            + "to 2% of your account on any single idea.\n\nRisk is not the "
                            + "amount you invest; it's the amount you'd lose if you're wrong. "
                            + "If you buy at $50 and would sell at $46, you're risking $4 a "
                            + "share. With a $10,000 account and a 1% limit ($100), that's "
                            + "25 shares.\n\nThe math behind this: a 50% loss needs a 100% "
                            + "gain to get back to even, so avoiding big losses matters more "
                            + "than catching big wins. Deciding your exit before you enter "
                            + "also takes emotion out of the worst moment to be making "
                            + "decisions. This is general education, not advice for your "
                            + "situation."),
            new Node("m_index", 1, LESSON, "Index funds and diversification",
                    "Why spreading out lowers risk, and what an index fund is.", 30, 3, null,
                    "Diversification means owning many different things so that one bad "
                            + "outcome can't sink you. A single company can go to zero; 500 "
                            + "companies at once essentially can't.\n\nAn index fund is a "
                            + "fund that simply buys everything in an index, such as the S&P "
                            + "500, instead of paying someone to pick winners. Because "
                            + "there's no picking, fees are very low, and over long periods "
                            + "most professional stock pickers have failed to beat the index "
                            + "after their fees.\n\nDiversifying doesn't remove risk: when "
                            + "the whole market falls, an index fund falls with it. It "
                            + "removes the risk of being wrong about one company. This is "
                            + "general education, not a recommendation for you."),
            new Node("m_portfolio", 1, FEATURE, "Portfolio tracker",
                    "Enter your real positions and cost basis; see value and gain.", 40, 3,
                    null, null),
            new Node("m_news", 1, FEATURE, "Market news",
                    "Headlines from CNBC, MarketWatch and Yahoo Finance.", 40, 3, null, null),
            new Node("m_paper", 1, FEATURE, "Paper trading",
                    "Practice with $10,000 of pretend cash. Add more with Knowledge Points.",
                    60, 4, "m_risk", null),
            new Node("m_alerts", 1, SOON, "Alerts and economic calendar",
                    "Price alerts and the dates of big economic reports.", 0, 0, null, null),

            // ----- Wealth -----
            new Node("w_rate", 2, PERK, "Better exchange rate",
                    "12 Fun Coupons per Knowledge Point instead of 10.", 40, 3, null, null),
            new Node("w_interest", 2, PERK, "Higher interest",
                    "Gold earns 3% a day instead of 2%.", 70, 4, null, null),

            // ----- Life tools -----
            new Node("l_weather", 3, FEATURE, "Weather",
                    "A forecast for the city you choose, shown on the home screen.", 30, 2,
                    null, null),
            new Node("l_recipes", 3, FEATURE, "Dinner recipes",
                    "Fourteen easy dinners with short ingredient lists.", 30, 2, null, null),
            new Node("l_goals", 3, FEATURE, "Goal maker",
                    "Set goals, track progress, earn points for finishing.", 30, 2, null,
                    null),
            new Node("l_reminders", 3, FEATURE, "Reminders",
                    "Daily and weekly reminders that arrive as notifications.", 30, 3, null,
                    null),

            // ----- Helper -----
            new Node("h_lookup", 4, SOON, "Safe lookups",
                    "The helper fetches text answers from trusted sources.", 0, 0, null, null),
            new Node("h_market", 4, PERK, "Market Q&A",
                    "Ask the helper things like 'how do I find support and resistance?'",
                    30, 2, null, null),
    };

    private Tree() {}

    // The Sleep lessons are kept in their own array so the long text stays readable.
    static final Node[] SLEEP = {
            new Node("s_winddown", 5, LESSON, "Wind down before bed",
                    "A simple routine that tells your body it's time.", 0, 1, null,
                    "Your body falls asleep more easily when the hour before bed looks the "
                            + "same every night. Pick a bedtime and a wake time and keep "
                            + "them within about an hour, weekends included; the regular "
                            + "wake time matters most.\n\nIn the last 30 to 60 minutes, dim "
                            + "the lights, stop work and anything stressful, and do "
                            + "something quiet: a shower, stretching, a paper book, calm "
                            + "music. A warm shower an hour or two before bed helps because "
                            + "your body cools afterward, and falling body temperature is "
                            + "one of the signals for sleep.\n\nKeep the bed for sleep. If "
                            + "you work, scroll and watch TV there, your brain stops "
                            + "linking the bed with sleeping."),
            new Node("s_light", 5, LESSON, "Light and your body clock",
                    "Why evenings should be dim, and how Red night mode helps.", 10, 1,
                    "s_winddown",
                    "Light is the main thing that sets your internal clock. Bright light in "
                            + "the morning, ideally daylight within an hour of waking, makes "
                            + "you more alert by day and sleepier at the right time at "
                            + "night.\n\nIn the evening it works against you. Bright light, "
                            + "especially the blue-white kind from screens and overhead "
                            + "LEDs, tells your brain it's still daytime and delays the "
                            + "hormone that makes you sleepy. Dim, warm light does this far "
                            + "less, and red light least of all.\n\nThis app has a setting "
                            + "for that: Menu, then Colors & night mode, then Red night. It "
                            + "turns the text red and drops the screen brightness very low "
                            + "inside the app, so checking an alarm at 2 a.m. doesn't jolt "
                            + "you awake. Overall brightness matters more than color, so "
                            + "dim the room as well."),
            new Node("s_intake", 5, LESSON, "Caffeine, alcohol and late meals",
                    "What you take in during the day shows up at night.", 10, 1,
                    "s_winddown",
                    "Caffeine blocks the chemical that builds up sleep pressure through the "
                            + "day. Half of it is still in your system five or six hours "
                            + "later, so an afternoon coffee can still be working at "
                            + "bedtime. A common rule is no caffeine within eight hours of "
                            + "bed; that includes energy drinks, cola and strong tea.\n\n"
                            + "Alcohol is the sneaky one. It helps you fall asleep, then "
                            + "breaks up the second half of the night and cuts into dream "
                            + "sleep, so you wake unrefreshed. Nicotine is a stimulant "
                            + "too.\n\nA heavy meal within two or three hours of bed can "
                            + "cause heartburn and restlessness, and a lot of fluid late "
                            + "means trips to the bathroom. A light snack is fine if you're "
                            + "hungry."),
            new Node("s_awake", 5, LESSON, "When you can't fall asleep",
                    "What to do at 2 a.m. instead of staring at the ceiling.", 10, 1,
                    "s_winddown",
                    "Lying in bed trying hard to sleep usually backfires. If you've been "
                            + "awake for what feels like 20 minutes, get up. Go to another "
                            + "room, keep the lights low, and do something dull and calm "
                            + "until you feel sleepy, then go back. This keeps your brain "
                            + "from learning that bed is a place to lie awake.\n\nDon't "
                            + "watch the clock; doing the math on how little sleep is left "
                            + "only raises the pressure. Turn it away.\n\nFor a racing "
                            + "mind, write tomorrow's to-do list or your worries on paper "
                            + "earlier in the evening so they're parked somewhere. In bed, "
                            + "slow your breathing: in through the nose for about four "
                            + "seconds, out for about six, for a few minutes. Keep naps "
                            + "short, under half an hour, and not late in the day."),
            new Node("s_room", 5, LESSON, "Set up the room",
                    "Cool, dark and quiet, plus when to see a doctor.", 10, 1, "s_winddown",
                    "Three things about the room matter most. Cool: most people sleep best "
                            + "somewhere around 60 to 67 degrees Fahrenheit. Dark: block "
                            + "street light and cover small LEDs; even a little light can "
                            + "disturb sleep. Quiet: sudden noises wake you more than "
                            + "steady ones, so a constant background sound helps. This "
                            + "app's Sleep sounds (fan, brown noise and others) are for "
                            + "that, with a timer or left on all night.\n\nIn the morning, "
                            + "a gentle alarm is easier on you than a sudden blast, which "
                            + "is what the fade-in setting on each alarm does.\n\nWhen to "
                            + "get help: if you have trouble sleeping three or more nights "
                            + "a week for three months, if you snore loudly or wake up "
                            + "gasping, or if you're very sleepy during the day, talk to a "
                            + "doctor. For long-running insomnia, a structured program "
                            + "called CBT-I is the usual first treatment and works better "
                            + "over time than sleeping pills. This is general information, "
                            + "not medical advice."),
    };

    /** Every node on the tree. */
    static final Node[] NODES = join(BASE, SLEEP);

    private static Node[] join(Node[] a, Node[] b) {
        Node[] out = new Node[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static Node find(String id) {
        for (Node n : NODES) {
            if (n.id.equals(id)) return n;
        }
        return null;
    }

    /** The screen a FEATURE node opens, or null. */
    static Class<?> screen(String id) {
        if ("slot".equals(id)) return SlotActivity.class;
        if ("m_portfolio".equals(id)) return PortfolioActivity.class;
        if ("m_paper".equals(id)) return PaperActivity.class;
        if ("m_news".equals(id)) return NewsActivity.class;
        if ("l_weather".equals(id)) return WeatherActivity.class;
        if ("l_recipes".equals(id)) return RecipesActivity.class;
        if ("l_goals".equals(id)) return GoalsActivity.class;
        if ("l_reminders".equals(id)) return RemindersActivity.class;
        return null;
    }

    static boolean owned(Context c, String id) {
        return MarketData.list(c, "treeOwned", "").contains(id);
    }

    /** Why this node can't be bought right now, or null if it can. */
    static String blocker(Context c, Node n) {
        if (n.kind == SOON) return "Coming in a future update";
        if (owned(c, n.id)) return "Already unlocked";
        if (n.needs != null && !owned(c, n.needs)) {
            Node need = find(n.needs);
            return "Unlock \"" + (need == null ? n.needs : need.name) + "\" first";
        }
        if (Progress.level(c) < n.level) return "Reach level " + n.level + " first";
        if (Wallet.kp(c) < n.cost) {
            return "You need " + (n.cost - Wallet.kp(c)) + " more Knowledge Points";
        }
        return null;
    }

    static boolean buy(Context c, Node n) {
        if (blocker(c, n) != null || !Scores.spend(c, n.cost)) return false;
        List<String> have = MarketData.list(c, "treeOwned", "");
        have.add(n.id);
        MarketData.saveList(c, "treeOwned", have);
        return true;
    }
}
