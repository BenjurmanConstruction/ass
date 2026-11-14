package app.easywake;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/** Levels, streaks, the daily challenge and badges: the reasons to come back. */
final class Progress {
    private static final String[] LEVEL_TITLES = {
            "Sleepyhead", "Early Riser", "Warming Up", "Sharp", "Quick Thinker",
            "Problem Solver", "Brainiac", "Strategist", "Mastermind", "Genius",
            "Prodigy", "Grandmaster", "Legend"
    };

    static final String[] BADGE_NAMES = {
            "First steps", "Explorer", "Completionist", "On a roll", "Habit",
            "Centurion", "Record breaker", "Lightning", "Human calculator", "Bookworm",
            "Collector", "High five", "Double digits"
    };
    static final String[] BADGE_HOW = {
            "Finish your first test", "Try 8 different tests", "Try every test",
            "Play 3 days in a row", "Play 7 days in a row", "Finish 100 tests",
            "Set a new #1 score 10 times", "Average under 250 ms in Reaction time",
            "Get 25 or more in Speed math", "Unlock 5 lessons", "Unlock 15 vault facts",
            "Reach level 5", "Reach level 10"
    };
    static final int[] BADGE_REWARD = {10, 40, 100, 30, 80, 100, 50, 40, 40, 30, 40, 50, 150};

    /** What finishing a test paid out, for the results screen. */
    static final class Award {
        int earned;
        final List<String> notes = new ArrayList<>();
    }

    private Progress() {}

    /** Days since 1970 in the phone's own time zone. */
    static int today() {
        long now = System.currentTimeMillis();
        return (int) ((now + TimeZone.getDefault().getOffset(now)) / 86400000L);
    }

    static int xp(Context c) {
        return Prefs.getInt(c, "xp", 0);
    }

    /** XP needed to reach a level (level 1 starts at 0). */
    static int threshold(int level) {
        return 20 * level * (level - 1);
    }

    static int level(Context c) {
        int x = xp(c);
        int level = 1;
        while (threshold(level + 1) <= x && level < 99) level++;
        return level;
    }

    static String title(int level) {
        return LEVEL_TITLES[Math.min(LEVEL_TITLES.length, Math.max(1, level)) - 1];
    }

    /** Current streak, or 0 if a day was missed. */
    static int streak(Context c) {
        int last = Prefs.getInt(c, "lastPlayDay", 0);
        int t = today();
        if (last == t || last == t - 1) return Prefs.getInt(c, "streak", 0);
        return 0;
    }

    static boolean playedToday(Context c) {
        return Prefs.getInt(c, "lastPlayDay", 0) == today();
    }

    /** Today's double-points test. The same for the whole day. */
    static int challengeMode() {
        return new Random(today() * 7919L).nextInt(Games.COUNT);
    }

    static boolean challengeDone(Context c) {
        return Prefs.getInt(c, "challengeDay", 0) == today();
    }

    static boolean hasBadge(Context c, int i) {
        return MarketData.list(c, "badges", "").contains(String.valueOf(i));
    }

    private static int grant(Context c, int i, Award a) {
        if (hasBadge(c, i)) return 0;
        List<String> have = MarketData.list(c, "badges", "");
        have.add(String.valueOf(i));
        MarketData.saveList(c, "badges", have);
        if (a != null) {
            a.notes.add("Badge earned: " + BADGE_NAMES[i] + "  (+" + BADGE_REWARD[i] + ")");
        }
        return BADGE_REWARD[i];
    }

    /** Badges that depend on what you've unlocked in Learn. Returns bonus points granted. */
    static int checkLearnBadges(Context c) {
        int bonus = 0;
        if (MarketData.list(c, "lessonsOwned", "").size() >= 5) bonus += grant(c, 9, null);
        if (MarketData.list(c, "vaultOwned", "").size() >= 15) bonus += grant(c, 10, null);
        if (bonus > 0) Scores.addPoints(c, bonus);
        return bonus;
    }

    /**
     * Called when a test ends. Works out everything earned, saves it, and returns the
     * lines to show the player.
     */
    static Award award(Context c, int mode, int rank, int base, boolean newBest) {
        Award a = new Award();
        int points = Math.max(1, base);
        a.notes.add("Test score: +" + points);

        if (newBest) {
            points += 15;
            a.notes.add("New #1 on the board: +15");
            Prefs.putInt(c, "bestCount", Prefs.getInt(c, "bestCount", 0) + 1);
        }

        if (mode == challengeMode() && !challengeDone(c)) {
            int extra = Math.max(10, base);
            points += extra;
            Prefs.putInt(c, "challengeDay", today());
            a.notes.add("Daily challenge done, double points: +" + extra);
        }

        // Streak: the first test of each day keeps it alive and pays a growing bonus.
        int t = today();
        int last = Prefs.getInt(c, "lastPlayDay", 0);
        if (last != t) {
            int streak = last == t - 1 ? Prefs.getInt(c, "streak", 0) + 1 : 1;
            Prefs.putInt(c, "streak", streak);
            Prefs.putInt(c, "lastPlayDay", t);
            int bonus = Math.min(50, streak * 5);
            points += bonus;
            a.notes.add(streak == 1 ? "First test today: +" + bonus
                    : streak + "-day streak: +" + bonus);
            int interest = Wallet.payInterest(c);
            if (interest > 0) a.notes.add("Gold interest paid: +" + interest + " Gold");
        }

        // Play counts.
        int plays = Prefs.getInt(c, "plays", 0) + 1;
        Prefs.putInt(c, "plays", plays);
        List<String> tried = MarketData.list(c, "tried", "");
        if (!tried.contains(String.valueOf(mode))) {
            tried.add(String.valueOf(mode));
            MarketData.saveList(c, "tried", tried);
            points += 5;
            a.notes.add("First time trying this test: +5");
        }

        int before = level(c);
        Prefs.putInt(c, "xp", xp(c) + points);
        int after = level(c);

        // Badges.
        int bonus = 0;
        if (plays >= 1) bonus += grant(c, 0, a);
        if (tried.size() >= 8) bonus += grant(c, 1, a);
        if (tried.size() >= Games.COUNT) bonus += grant(c, 2, a);
        int streakNow = Prefs.getInt(c, "streak", 0);
        if (streakNow >= 3) bonus += grant(c, 3, a);
        if (streakNow >= 7) bonus += grant(c, 4, a);
        if (plays >= 100) bonus += grant(c, 5, a);
        if (Prefs.getInt(c, "bestCount", 0) >= 10) bonus += grant(c, 6, a);
        if (mode == GameActivity.REACTION && rank > -250) bonus += grant(c, 7, a);
        if (mode == GameActivity.MATH && rank >= 25) bonus += grant(c, 8, a);
        if (after >= 5) bonus += grant(c, 11, a);
        if (after >= 10) bonus += grant(c, 12, a);
        points += bonus;

        if (after > before) {
            a.notes.add("LEVEL UP! You are now level " + after + ": " + title(after));
            String gift = freeVaultFact(c);
            if (gift != null) a.notes.add("Level-up gift, a free vault fact: " + gift);
        } else {
            int need = threshold(after + 1) - xp(c);
            a.notes.add(need + " more to reach level " + (after + 1) + ": "
                    + title(after + 1));
        }

        Scores.addPoints(c, points);
        a.earned = points;
        return a;
    }

    private static String freeVaultFact(Context c) {
        List<String> have = MarketData.list(c, "vaultOwned", "");
        List<Integer> locked = new ArrayList<>();
        for (int i = 0; i < Lessons.VAULT.length; i++) {
            if (!have.contains(String.valueOf(i))) locked.add(i);
        }
        if (locked.isEmpty()) return null;
        int pick = locked.get(new Random().nextInt(locked.size()));
        have.add(String.valueOf(pick));
        MarketData.saveList(c, "vaultOwned", have);
        return Lessons.VAULT[pick];
    }
}
