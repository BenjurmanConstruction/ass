package app.easywake;

import java.util.Locale;
import java.util.Random;

/**
 * The jackpots shared by every penny machine. Amounts are in credits (100 credits = 1 Fun
 * Coupon). No Android code here, so the math can be checked on its own.
 *
 * Four progressives (MINI, MINOR, MAJOR, GRAND) start at a seed amount and grow by a small
 * slice of every bet on any penny machine. The chance of hitting one goes up in step with
 * the bet, so every credit bet has the same shot.
 *
 * Two "must hit by" mystery jackpots also grow with every bet. Each secretly picks a point
 * between its start and its limit when it resets, and pays out on whichever spin pushes it
 * past that point, so it always drops before the limit.
 */
final class JackpotBank {
    static final int MINI = 0, MINOR = 1, MAJOR = 2, GRAND = 3, MYSTERY_SMALL = 4,
            MYSTERY_BIG = 5;
    static final String[] NAMES = {"MINI", "MINOR", "MAJOR", "GRAND", "MYSTERY",
            "MEGA MYSTERY"};
    /** Where each jackpot starts after it's won, in credits. */
    static final double[] SEED = {1000, 5000, 50000, 500000, 1000, 10000};
    /** Share of every bet added to each meter. */
    static final double[] RATE = {0.006, 0.005, 0.004, 0.003, 0.004, 0.002};
    /** Chance per credit bet of hitting each progressive (so it scales with the bet). */
    static final double[] HIT_PER_CREDIT = {1.8e-5, 2.4e-6, 1.6e-7, 1.0e-8};
    /** The mystery jackpots' guaranteed limits, in credits. */
    static final double[] MUST_HIT_BY = {0, 0, 0, 0, 2500, 25000};

    final double[] meter = new double[6];
    /** Where each mystery jackpot will drop (hidden from the player). */
    final double[] hitAt = new double[6];

    JackpotBank(Random rnd) {
        for (int i = 0; i < 6; i++) reset(i, rnd);
    }

    void reset(int level, Random rnd) {
        meter[level] = SEED[level];
        if (level >= MYSTERY_SMALL) {
            hitAt[level] = SEED[level] + rnd.nextDouble() * (MUST_HIT_BY[level] - SEED[level]);
        }
    }

    /** Adds this bet's slices to every meter. */
    void contribute(long betCredits) {
        for (int i = 0; i < 6; i++) meter[i] += betCredits * RATE[i];
    }

    /**
     * Rolls for a progressive on a spin of this size: GRAND, MAJOR, MINOR, MINI, or -1.
     * The bigger the bet, the better the chance.
     */
    static int roll(Random rnd, long betCredits) {
        double x = rnd.nextDouble();
        double acc = 0;
        for (int i = GRAND; i >= MINI; i--) {
            acc += HIT_PER_CREDIT[i] * betCredits;
            if (x < acc) return i;
        }
        return -1;
    }

    /** The chance per spin of any progressive at this bet. */
    static double anyChance(long betCredits) {
        double p = 0;
        for (double h : HIT_PER_CREDIT) p += h * betCredits;
        return p;
    }

    /** Pays a jackpot: returns its credits and starts it over. */
    long award(int level, Random rnd) {
        long won = (long) Math.floor(meter[level]);
        reset(level, rnd);
        return won;
    }

    /**
     * After a spin's contributions: any mystery jackpot that has reached its hidden point
     * pays now. Returns the levels that hit (bit 4 and bit 5), or 0.
     */
    int mysteryDue() {
        int due = 0;
        for (int i = MYSTERY_SMALL; i <= MYSTERY_BIG; i++) {
            if (meter[i] >= hitAt[i]) due |= 1 << i;
        }
        return due;
    }

    String save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(String.format(Locale.US, "%.4f,%.4f;", meter[i], hitAt[i]));
        }
        return sb.toString();
    }

    static JackpotBank load(String s, Random rnd) {
        JackpotBank b = new JackpotBank(rnd);
        if (s == null || s.isEmpty()) return b;
        try {
            String[] parts = s.split(";");
            for (int i = 0; i < 6 && i < parts.length; i++) {
                String[] v = parts[i].split(",");
                double m = Double.parseDouble(v[0]);
                double h = Double.parseDouble(v[1]);
                if (m >= SEED[i]) b.meter[i] = m;
                if (i >= MYSTERY_SMALL && h >= SEED[i] && h <= MUST_HIT_BY[i]) b.hitAt[i] = h;
            }
        } catch (Exception e) {
            return new JackpotBank(rnd);
        }
        return b;
    }
}
