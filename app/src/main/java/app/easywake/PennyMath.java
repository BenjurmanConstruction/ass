package app.easywake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The math for the penny machines (SlotMath machines 5 to 8). The total bet is 100 units,
 * so a win of 250 units pays 2.5 times the bet in credits. Jackpots from the shared bank
 * are not counted in units: they're listed in Outcome.jackpots and paid from the bank.
 *
 * Every spin rolls once for a progressive jackpot, with a chance that grows with the bet
 * (JackpotBank.roll); each machine then shows that win its own way.
 */
final class PennyMath {
    static final int DRAGON = 5, DRUMS = 6, WHEEL = 7, SEVENS = 8;
    static final int BET = 100;

    private PennyMath() {
    }

    static SlotMath.Outcome play(int machine, Random rnd, long betCredits) {
        int jackpot = JackpotBank.roll(rnd, betCredits);
        switch (machine) {
            case DRAGON:
                return dragon(rnd, jackpot, betCredits);
            case DRUMS:
                return drums(rnd, jackpot);
            case WHEEL:
                return wheel(rnd, jackpot);
            default:
                return sevens(rnd, jackpot);
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /**
     * Ways to win from the left on a 5x3 grid, with a wild that stands in for any paying
     * symbol. Pays are per way, in units, for 3, 4 and 5 of a kind. Lights winning cells.
     */
    static long ways(char[][] g, String syms, int[][] pay, char wild, boolean[][] lit) {
        long total = 0;
        int rows = g.length;
        int cols = g[0].length;
        for (int k = 0; k < syms.length(); k++) {
            char s = syms.charAt(k);
            long ways = 1;
            int reach = 0;
            for (int c = 0; c < cols; c++) {
                int hits = 0;
                for (int r = 0; r < rows; r++) {
                    if (g[r][c] == s || (wild != 0 && g[r][c] == wild)) hits++;
                }
                if (hits == 0) break;
                ways *= hits;
                reach++;
            }
            // A way made only of wilds doesn't count for every symbol at once.
            if (reach >= 3 && hasSymbol(g, s, reach)) {
                total += ways * pay[k][reach - 3];
                if (lit != null) {
                    for (int c = 0; c < reach; c++) {
                        for (int r = 0; r < rows; r++) {
                            if (g[r][c] == s || (wild != 0 && g[r][c] == wild)) lit[r][c] = true;
                        }
                    }
                }
            }
        }
        return total;
    }

    private static boolean hasSymbol(char[][] g, char s, int reach) {
        for (int c = 0; c < reach; c++) {
            for (char[] row : g) {
                if (row[c] == s) return true;
            }
        }
        return false;
    }

    /** Scatter pays (in units) and free spins for 3, 4 and 5 bonus symbols. */
    static final int[] SCATTER_PAY = {200, 500, 2000};
    static final int[] FREE = {8, 12, 20};

    /**
     * Plays free spins on the given strips, adding frames. Every win is multiplied by mult;
     * more scatters add spins. Returns the units won.
     */
    private static long freeSpins(SlotMath.Outcome o, Random rnd, int spins, int mult,
                                  char[][] strips, String syms, int[][] pay, char wild,
                                  char scatter) {
        long sum = 0;
        int left = spins;
        int played = 0;
        while (left > 0 && played < 150) {
            left--;
            played++;
            char[][] g = SlotMath.spin(strips, 3, rnd);
            SlotMath.Frame f = SlotMath.newFrame(SlotMath.REELS, g);
            long won = ways(g, syms, pay, wild, f.lit) * mult;
            int sc = SlotMath.countOf(g, scatter);
            if (sc >= 3) {
                int i = Math.min(sc, 5) - 3;
                won += SCATTER_PAY[i] * mult;
                left += FREE[i];
                SlotMath.light(f, scatter);
                f.text = "+" + FREE[i] + " FREE SPINS!";
            }
            f.freeLeft = left;
            f.mult = mult;
            f.win = won;
            f.tease = tease(g, scatter);
            o.frames.add(f);
            sum += won;
        }
        return sum;
    }

    private static boolean tease(char[][] g, char s) {
        int n = 0;
        for (int c = 0; c < 3; c++) {
            for (char[] row : g) {
                if (row[c] == s) n++;
            }
        }
        return n >= 2;
    }

    /**
     * The base spin shared by the 5-reel machines: ways pays plus a scatter for free spins.
     * Returns the frame (already added).
     */
    private static SlotMath.Frame base(SlotMath.Outcome o, char[][] g, String syms,
                                       int[][] pay, char wild, char scatter) {
        SlotMath.Frame f = SlotMath.newFrame(SlotMath.REELS, g);
        long won = ways(g, syms, pay, wild, f.lit);
        f.win = won;
        f.tease = tease(g, scatter);
        o.frames.add(f);
        o.baseWin = won;
        o.total = won;
        return f;
    }

    /** Free spins from 3+ scatters on a base spin, if any. */
    private static void maybeFree(SlotMath.Outcome o, SlotMath.Frame f, Random rnd, int mult,
                                  char[][] freeStrips, String syms, int[][] pay, char wild,
                                  char scatter, String what) {
        int sc = SlotMath.countOf(f.grid, scatter);
        if (sc < 3) return;
        int i = Math.min(sc, 5) - 3;
        f.win += SCATTER_PAY[i];
        o.total += SCATTER_PAY[i];
        SlotMath.light(f, scatter);
        f.text = sc + " " + what + ": " + FREE[i] + " FREE SPINS, WINS x" + mult + "!";
        f.waitTap = true;
        o.feature = true;
        o.total += freeSpins(o, rnd, FREE[i], mult, freeStrips, syms, pay, wild, scatter);
    }

    private static void jackpotFrame(SlotMath.Outcome o, int level, String text) {
        SlotMath.Frame j = new SlotMath.Frame();
        j.kind = SlotMath.JACKPOT;
        j.jackpot = level;
        j.text = text;
        o.frames.add(j);
        o.jackpots.add(level);
        o.feature = true;
    }

    // ------------------------------------------------------------------ Jade Dragon Link

    /** R dragon, G tiger, X koi, L lantern, A, Y (K), Q, J, T (10); W wild; S envelope; O coin. */
    static final String DRAGON_SYMS = "RGXLAYQJT";
    static final int[][] DRAGON_PAY = {{200, 598, 1995}, {160, 479, 1596}, {120, 399, 1197},
            {100, 319, 998}, {60, 160, 479}, {60, 160, 479}, {40, 120, 399}, {40, 120, 399},
            {40, 100, 319}};
    static final char[][] DRAGON_STRIPS = {
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 501),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 502),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 503),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 504),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 505)};
    static final char[][] DRAGON_FREE = {
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{4, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 511),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{4, 4, 5, 5, 7, 7, 8, 8, 9, 3, 2}, 512),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{4, 4, 5, 5, 7, 7, 8, 8, 9, 3, 2}, 513),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{4, 4, 5, 5, 7, 7, 8, 8, 9, 3, 2}, 514),
            SlotMath.pennyStrip("RGXLAYQJTWS", new int[]{4, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 515)};
    static final double DRAGON_COIN = 0.14;
    static final long[] DRAGON_VALUES = {40, 80, 120, 160, 240, 400, 800};
    static final int[] DRAGON_WEIGHTS = {32, 26, 14, 10, 8, 6, 4};
    static final double[] DRAGON_RESPIN = {0.11, 0.11, 0.11, 0.11, 0.11, 0.11, 0.11, 0.11,
            0.10, 0.085, 0.07, 0.055, 0.04, 0.02, 0.006, 0};

    private static long dragonCoin(Random rnd) {
        int total = 0;
        for (int w : DRAGON_WEIGHTS) total += w;
        int pick = rnd.nextInt(total);
        for (int i = 0; i < DRAGON_WEIGHTS.length; i++) {
            pick -= DRAGON_WEIGHTS[i];
            if (pick < 0) return DRAGON_VALUES[i];
        }
        return DRAGON_VALUES[0];
    }

    /** Chance a spin lands 6 or more coins on its own (15 spots, each a coin by chance). */
    private static double naturalHold() {
        double p = DRAGON_COIN;
        double sum = 0;
        for (int k = 6; k <= 15; k++) {
            double c = 1;
            for (int i = 0; i < k; i++) c = c * (15 - i) / (i + 1);
            sum += c * Math.pow(p, k) * Math.pow(1 - p, 15 - k);
        }
        return sum;
    }

    private static final double NATURAL_HOLD = naturalHold();

    private static SlotMath.Outcome dragon(Random rnd, int jackpot, long betCredits) {
        SlotMath.Outcome o = new SlotMath.Outcome();
        char[][] g = SlotMath.spin(DRAGON_STRIPS, 3, rnd);
        char[][] plain = new char[3][];
        for (int r = 0; r < 3; r++) plain[r] = g[r].clone();
        long[][] cv = new long[3][5];
        int n = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 5; c++) {
                if (g[r][c] != 'S' && rnd.nextDouble() < DRAGON_COIN) {
                    g[r][c] = 'O';
                    cv[r][c] = dragonCoin(rnd);
                    n++;
                }
            }
        }
        // Jackpots start a hold & spin of their own, so at bigger bets (more jackpots) some
        // ordinary ones don't happen: the number of hold & spins, and the payback, stay the
        // same at every bet.
        if (jackpot < 0 && n >= 6) {
            double forced = JackpotBank.anyChance(betCredits);
            if (rnd.nextDouble() < Math.min(1, forced / NATURAL_HOLD)) {
                for (int r = 0; r < 3; r++) g[r] = plain[r].clone();
                cv = new long[3][5];
                n = 0;
            }
        }
        // A jackpot always comes through hold & spin on this machine: make sure it starts.
        while (jackpot >= 0 && n < 6) {
            int r = rnd.nextInt(3);
            int c = rnd.nextInt(5);
            if (g[r][c] == 'O' || g[r][c] == 'S') continue;
            g[r][c] = 'O';
            cv[r][c] = dragonCoin(rnd);
            n++;
        }
        SlotMath.Frame f = base(o, g, DRAGON_SYMS, DRAGON_PAY, 'W', 'S');
        f.coins = cv;
        int early = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                if (g[r][c] == 'O') early++;
            }
        }
        f.tease = f.tease || early >= 4;
        if (n >= 6) {
            o.feature = true;
            f.text = n + " coins: HOLD & SPIN!";
            SlotMath.light(f, 'O');
            f.waitTap = true;
            long[][] held = new long[3][5];
            for (int r = 0; r < 3; r++) held[r] = cv[r].clone();
            int filled = n;
            int respins = 3;
            SlotMath.Frame start = new SlotMath.Frame();
            start.kind = SlotMath.HOLD;
            start.coins = copy(held);
            start.respins = respins;
            start.text = "3 respins. Every new coin resets them to 3.";
            o.frames.add(start);
            // A jackpot coin (MINI to MAJOR) lands on the first respin; a GRAND fills the
            // whole screen. Without a GRAND on the way the last spot never fills.
            boolean grand = jackpot == JackpotBank.GRAND;
            boolean jackpotPlaced = jackpot < 0 || grand;
            while ((respins > 0 || grand) && filled < 15) {
                respins--;
                boolean landed = false;
                if (!jackpotPlaced && filled < 14) {
                    List<int[]> empty = new ArrayList<>();
                    for (int r = 0; r < 3; r++) {
                        for (int c = 0; c < 5; c++) {
                            if (held[r][c] == 0) empty.add(new int[]{r, c});
                        }
                    }
                    int[] spot = empty.get(rnd.nextInt(empty.size()));
                    held[spot[0]][spot[1]] = -(jackpot + 1);
                    filled++;
                    landed = true;
                    jackpotPlaced = true;
                }
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 5; c++) {
                        if (held[r][c] != 0) continue;
                        if (!grand && filled >= 14) continue;
                        double p = grand ? 0.35 : DRAGON_RESPIN[filled];
                        if (rnd.nextDouble() < p) {
                            held[r][c] = dragonCoin(rnd);
                            filled++;
                            landed = true;
                        }
                    }
                }
                if (landed || grand) respins = 3;
                SlotMath.Frame h = new SlotMath.Frame();
                h.kind = SlotMath.HOLD;
                h.coins = copy(held);
                h.respins = respins;
                o.frames.add(h);
            }
            long pay = 0;
            for (long[] row : held) {
                for (long c : row) {
                    if (c > 0) pay += c;
                    else if (c < 0) o.jackpots.add((int) (-c - 1));
                }
            }
            SlotMath.Frame end = new SlotMath.Frame();
            end.kind = SlotMath.HOLD;
            end.coins = copy(held);
            end.win = pay;
            if (filled == 15) {
                o.jackpots.add(JackpotBank.GRAND);
                end.text = "FULL SCREEN: GRAND JACKPOT!";
            } else {
                end.text = "Hold & spin over";
            }
            o.frames.add(end);
            o.total += pay;
        }
        maybeFree(o, f, rnd, 2, DRAGON_FREE, DRAGON_SYMS, DRAGON_PAY, 'W', 'S',
                "red envelopes");
        return o;
    }

    private static long[][] copy(long[][] a) {
        long[][] b = new long[a.length][];
        for (int i = 0; i < a.length; i++) b[i] = a[i].clone();
        return b;
    }

    // ------------------------------------------------------------------ Fortune Drums

    /** D drum, I gold ingot, F fan, P frog, A, Y (K), Q, J, T; W wild; S firecracker. */
    static final String DRUM_SYMS = "DIFPAYQJT";
    static final int[][] DRUM_PAY = {{152, 506, 1518}, {101, 380, 1012}, {76, 253, 759},
            {63, 202, 632}, {37, 101, 304}, {37, 101, 304}, {25, 76, 253}, {25, 76, 253},
            {25, 63, 202}};
    static final char[][] DRUM_STRIPS = {
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 601),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 602),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 603),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 604),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 605)};
    static final char[][] DRUM_FREE = {
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{6, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 611),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{6, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 612),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{6, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 613),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{6, 4, 5, 5, 7, 7, 8, 8, 9, 2, 2}, 614),
            SlotMath.pennyStrip("DIFPAYQJTWS", new int[]{6, 4, 5, 5, 7, 7, 8, 8, 9, 0, 2}, 615)};

    /**
     * The order the picks reveal: the winning jackpot's third match comes last, and no
     * other jackpot gets three before it. The rest are shown afterwards.
     */
    static char[] pickScript(int level, Random rnd) {
        List<Character> other = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            if (k == level) continue;
            int n = rnd.nextInt(3);
            for (int i = 0; i < n; i++) other.add((char) ('0' + k));
        }
        List<Character> seq = new ArrayList<>(other);
        seq.add((char) ('0' + level));
        seq.add((char) ('0' + level));
        Collections.shuffle(seq, rnd);
        seq.add((char) ('0' + level));
        // Fill to 12 with what's left, so the whole board can be shown at the end.
        int[] used = new int[4];
        for (char c : seq) used[c - '0']++;
        while (seq.size() < 12) {
            int k = rnd.nextInt(4);
            if (k == level || used[k] >= 3) continue;
            seq.add((char) ('0' + k));
            used[k]++;
        }
        char[] out = new char[12];
        for (int i = 0; i < 12; i++) out[i] = seq.get(i);
        return out;
    }

    /** How many picks the script takes before the jackpot is decided. */
    static int picksNeeded(char[] script, int level) {
        int n = 0;
        for (int i = 0; i < script.length; i++) {
            if (script[i] - '0' == level && ++n == 3) return i + 1;
        }
        return script.length;
    }

    private static SlotMath.Outcome drums(Random rnd, int jackpot) {
        SlotMath.Outcome o = new SlotMath.Outcome();
        char[][] g = SlotMath.spin(DRUM_STRIPS, 3, rnd);
        SlotMath.Frame f = base(o, g, DRUM_SYMS, DRUM_PAY, 'W', 'S');
        if (jackpot >= 0) {
            f.text = "THE DRUMS ARE BEATING! PICK FOR A JACKPOT!";
            f.waitTap = true;
            SlotMath.Frame p = new SlotMath.Frame();
            p.kind = SlotMath.PICK;
            p.jackpot = jackpot;
            p.picks = pickScript(jackpot, rnd);
            p.text = "Pick coins until three match";
            o.frames.add(p);
            o.jackpots.add(jackpot);
            o.feature = true;
        }
        maybeFree(o, f, rnd, 3, DRUM_FREE, DRUM_SYMS, DRUM_PAY, 'W', 'S', "firecrackers");
        return o;
    }

    // ------------------------------------------------------------------ Thunder Jackpot Wheel

    /** H horse, B bull, E eagle, U cactus, A, Y (K), Q, J, T; W wild; S wheel (reels 2-4). */
    static final String WHEEL_SYMS = "HBEUAYQJT";
    static final int[][] WHEEL_PAY = {{140, 421, 1404}, {112, 337, 1123}, {84, 281, 842},
            {70, 225, 702}, {42, 112, 337}, {42, 112, 337}, {28, 84, 281}, {28, 84, 281},
            {28, 70, 225}};
    static final char[][] WHEEL_STRIPS = {
            SlotMath.pennyStrip("HBEUAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 0, 0}, 701),
            SlotMath.pennyStrip("HBEUAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 5}, 702),
            SlotMath.pennyStrip("HBEUAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 5}, 703),
            SlotMath.pennyStrip("HBEUAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 2, 5}, 704),
            SlotMath.pennyStrip("HBEUAYQJTWS", new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9, 0, 0}, 705)};
    /**
     * The jackpot wheel's 16 slices. Positive: times the bet. -1 to -4: MINI to GRAND.
     * 100 + n: n free spins.
     */
    static final int[] WHEEL_SLICES = {15, -1, 25, 108, 10, -2, 40, 12, -3, 20, 112, 50, -1, 18,
            -4, 100};
    /** How often natural wheel spins land on each slice (jackpot slices only by a jackpot). */
    static final int[] WHEEL_WEIGHTS = {16, 0, 10, 9, 20, 0, 6, 18, 0, 12, 4, 4, 0, 14, 0, 3};

    static String sliceLabel(int v) {
        if (v < 0) return JackpotBank.NAMES[-v - 1];
        if (v > 100) return (v - 100) + " FREE";
        return v + "x";
    }

    private static SlotMath.Outcome wheel(Random rnd, int jackpot) {
        SlotMath.Outcome o = new SlotMath.Outcome();
        char[][] g = SlotMath.spin(WHEEL_STRIPS, 3, rnd);
        if (jackpot >= 0) {
            // Bring three wheel symbols in, one on each of reels 2 to 4.
            for (int c = 1; c <= 3; c++) {
                boolean has = false;
                for (int r = 0; r < 3; r++) has |= g[r][c] == 'S';
                if (!has) g[rnd.nextInt(3)][c] = 'S';
            }
        }
        SlotMath.Frame f = SlotMath.newFrame(SlotMath.REELS, g);
        long won = ways(g, WHEEL_SYMS, WHEEL_PAY, 'W', f.lit);
        f.win = won;
        o.frames.add(f);
        o.baseWin = won;
        o.total = won;
        boolean trigger = true;
        for (int c = 1; c <= 3; c++) {
            boolean has = false;
            for (int r = 0; r < 3; r++) has |= g[r][c] == 'S';
            trigger &= has;
        }
        int early = 0;
        for (int c = 1; c <= 2; c++) {
            for (int r = 0; r < 3; r++) {
                if (g[r][c] == 'S') {
                    early++;
                    break;
                }
            }
        }
        f.tease = early == 2;
        if (!trigger) return o;
        o.feature = true;
        SlotMath.light(f, 'S');
        f.text = "JACKPOT WHEEL!";
        f.waitTap = true;
        int slice;
        if (jackpot >= 0) {
            List<Integer> spots = new ArrayList<>();
            for (int i = 0; i < WHEEL_SLICES.length; i++) {
                if (WHEEL_SLICES[i] == -(jackpot + 1)) spots.add(i);
            }
            slice = spots.get(rnd.nextInt(spots.size()));
        } else {
            int total = 0;
            for (int w : WHEEL_WEIGHTS) total += w;
            int pick = rnd.nextInt(total);
            slice = 0;
            for (int i = 0; i < WHEEL_WEIGHTS.length; i++) {
                pick -= WHEEL_WEIGHTS[i];
                if (pick < 0) {
                    slice = i;
                    break;
                }
            }
        }
        SlotMath.Frame w = new SlotMath.Frame();
        w.kind = SlotMath.WHEEL_SPIN;
        w.segment = slice;
        int v = WHEEL_SLICES[slice];
        if (v < 0) {
            w.text = JackpotBank.NAMES[-v - 1] + " JACKPOT!";
            o.jackpots.add(-v - 1);
        } else if (v > 100) {
            w.text = (v - 100) + " FREE SPINS, WINS x2!";
        } else {
            w.win = v * BET;
            w.text = v + "x your bet!";
        }
        o.frames.add(w);
        o.total += w.win;
        if (v > 100) {
            o.total += freeSpins(o, rnd, v - 100, 2, WHEEL_STRIPS, WHEEL_SYMS, WHEEL_PAY, 'W',
                    'Z');
        }
        return o;
    }

    // ------------------------------------------------------------------ Lucky 7s Jackpot

    /**
     * R red 7, U blue 7, 3/2/1 bars, C cherry; W wild; G gold 7 (pays anywhere); F free
     * spins bonus (reels 1, 3, 5).
     */
    static final String SEVEN_SYMS = "RU321C";
    static final int[][] SEVEN_PAY = {{46, 152, 608}, {30, 114, 380}, {19, 61, 190},
            {15, 46, 152}, {11, 30, 91}, {8, 19, 61}};
    static final char[][] SEVEN_STRIPS = {
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 0, 3, 3}, 801),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 2, 3, 0}, 802),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 2, 3, 3}, 803),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 2, 3, 0}, 804),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 0, 3, 3}, 805)};
    static final char[][] SEVEN_FREE = {
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 0, 5, 1}, 811),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 2, 5, 0}, 812),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 2, 5, 1}, 813),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 2, 5, 0}, 814),
            SlotMath.pennyStrip("RU321CWGF", new int[]{3, 4, 6, 7, 8, 10, 0, 5, 1}, 815)};
    /** Gold 7s anywhere: pays for 3 to 9 or more, in units. */
    static final int[] GOLD_PAY = {0, 0, 0, 150, 450, 1000, 2500, 10000, 50000, 100000};
    static final int SEVEN_FREE_SPINS = 10;

    private static long gold(SlotMath.Frame f) {
        int n = SlotMath.countOf(f.grid, 'G');
        if (n < 3) return 0;
        SlotMath.light(f, 'G');
        return GOLD_PAY[Math.min(9, n)];
    }

    private static SlotMath.Outcome sevens(Random rnd, int jackpot) {
        SlotMath.Outcome o = new SlotMath.Outcome();
        char[][] g = SlotMath.spin(SEVEN_STRIPS, 3, rnd);
        SlotMath.Frame f = SlotMath.newFrame(SlotMath.REELS, g);
        long won = ways(g, SEVEN_SYMS, SEVEN_PAY, 'W', f.lit) + gold(f);
        int bonus = SlotMath.countOf(g, 'F');
        f.tease = tease(g, 'F') || SlotMath.countOf(g, 'G') >= 5;
        f.win = won;
        o.frames.add(f);
        o.baseWin = won;
        o.total = won;
        if (jackpot >= 0) {
            jackpotFrame(o, jackpot, "777 " + JackpotBank.NAMES[jackpot] + " JACKPOT!");
        }
        if (bonus >= 3) {
            o.feature = true;
            SlotMath.light(f, 'F');
            f.text = "3 BONUS: " + SEVEN_FREE_SPINS + " FREE SPINS, WINS x2!";
            f.waitTap = true;
            int left = SEVEN_FREE_SPINS;
            int played = 0;
            while (left > 0 && played < 150) {
                left--;
                played++;
                char[][] fg = SlotMath.spin(SEVEN_FREE, 3, rnd);
                SlotMath.Frame ff = SlotMath.newFrame(SlotMath.REELS, fg);
                long w = (ways(fg, SEVEN_SYMS, SEVEN_PAY, 'W', ff.lit) + gold(ff)) * 2;
                if (SlotMath.countOf(fg, 'F') >= 3) {
                    left += 5;
                    SlotMath.light(ff, 'F');
                    ff.text = "+5 FREE SPINS!";
                }
                ff.freeLeft = left;
                ff.mult = 2;
                ff.win = w;
                o.frames.add(ff);
                o.total += w;
            }
        }
        return o;
    }
}
