package app.easywake;

import android.content.Context;

/**
 * The three in-app currencies. None of them is real money and none can be bought or
 * cashed out.
 *
 * Knowledge Points (KP): earned from Brain gym tests; spent on the skill tree and lessons.
 * Fun Coupons: play money for the casino games.
 * Gold: savings. Gold earns a little interest on each day you play a test.
 */
final class Wallet {
    static final int COUPONS_PER_GOLD = 100;

    private Wallet() {}

    static int kp(Context c) {
        return Scores.points(c);
    }

    static int gold(Context c) {
        return Prefs.getInt(c, "gold", 0);
    }

    static int coupons(Context c) {
        return Prefs.getInt(c, "coupons", 0);
    }

    static void addCoupons(Context c, int n) {
        Prefs.putInt(c, "coupons", Math.max(0, coupons(c) + n));
    }

    /** How many coupons one Knowledge Point buys (a skill-tree perk raises it). */
    static int couponRate(Context c) {
        return Tree.owned(c, "w_rate") ? 12 : 10;
    }

    /** Daily Gold interest in percent (a skill-tree perk raises it). */
    static int interestPercent(Context c) {
        return Tree.owned(c, "w_interest") ? 3 : 2;
    }

    static boolean kpToCoupons(Context c, int kp) {
        if (kp <= 0 || !Scores.spend(c, kp)) return false;
        addCoupons(c, kp * couponRate(c));
        return true;
    }

    static boolean kpToGold(Context c, int kp) {
        if (kp <= 0 || !Scores.spend(c, kp)) return false;
        Prefs.putInt(c, "gold", gold(c) + kp);
        return true;
    }

    static boolean goldToKp(Context c, int amount) {
        if (amount <= 0 || gold(c) < amount) return false;
        Prefs.putInt(c, "gold", gold(c) - amount);
        Scores.addPoints(c, amount);
        return true;
    }

    /** Cashes coupons out into Gold, in whole lots of 100. Returns the Gold gained. */
    static int couponsToGold(Context c, int goldWanted) {
        int cost = goldWanted * COUPONS_PER_GOLD;
        if (goldWanted <= 0 || coupons(c) < cost) return 0;
        addCoupons(c, -cost);
        Prefs.putInt(c, "gold", gold(c) + goldWanted);
        return goldWanted;
    }

    /**
     * Pays the day's Gold interest. Called once on the first test of each day.
     * Returns the Gold added.
     */
    static int payInterest(Context c) {
        int g = gold(c);
        if (g <= 0) return 0;
        int add = Math.min(60, (int) Math.ceil(g * interestPercent(c) / 100.0));
        Prefs.putInt(c, "gold", g + add);
        return add;
    }
}
