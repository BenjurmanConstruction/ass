package app.easywake;

import android.content.Context;

import java.util.List;

/** The skill tree: what can be unlocked with Knowledge Points, and what each thing needs. */
final class Tree {
    static final int LESSON = 0;
    static final int FEATURE = 1;
    static final int PERK = 2;
    static final int SOON = 3;

    static final String[] BRANCHES = {"Casino", "Market", "Wealth", "Life tools", "Helper"};
    static final String[] BRANCH_NOTES = {
            "Play-money games. Fun Coupons have no cash value.",
            "Learn to read charts and think about risk. Education only, not advice.",
            "Make your savings work harder.",
            "Everyday tools. Arriving in the next updates.",
            "Upgrades for the built-in helper. Arriving in the next updates."
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

    static final Node[] NODES = {
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
            new Node("m_portfolio", 1, SOON, "Portfolio tracker",
                    "Enter your real positions and cost basis; see value and gain.", 0, 0,
                    null, null),
            new Node("m_paper", 1, SOON, "Paper trading",
                    "Practice with fake cash at live-ish prices.", 0, 0, null, null),
            new Node("m_news", 1, SOON, "News feed and alerts",
                    "Headlines, price alerts and an economic calendar.", 0, 0, null, null),

            // ----- Wealth -----
            new Node("w_rate", 2, PERK, "Better exchange rate",
                    "12 Fun Coupons per Knowledge Point instead of 10.", 40, 3, null, null),
            new Node("w_interest", 2, PERK, "Higher interest",
                    "Gold earns 3% a day instead of 2%.", 70, 4, null, null),

            // ----- Life tools -----
            new Node("l_weather", 3, SOON, "Weather on the home screen",
                    "A forecast for the city you choose.", 0, 0, null, null),
            new Node("l_goals", 3, SOON, "Goals and reminders",
                    "A goal maker with daily and weekly reminders.", 0, 0, null, null),
            new Node("l_recipes", 3, SOON, "Dinner recipes",
                    "Easy recipes that are hard to mess up.", 0, 0, null, null),

            // ----- Helper -----
            new Node("h_lookup", 4, SOON, "Safe lookups",
                    "The helper fetches text answers from trusted sources.", 0, 0, null, null),
            new Node("h_market", 4, SOON, "Market Q&A",
                    "Ask the helper chart and investing questions.", 0, 0, null, null),
    };

    private Tree() {}

    static Node find(String id) {
        for (Node n : NODES) {
            if (n.id.equals(id)) return n;
        }
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
