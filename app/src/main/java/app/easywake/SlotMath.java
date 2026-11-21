package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The math behind the Slot Parlor machines, kept apart from the screens so every machine
 * can be checked with millions of test spins. Nothing here touches coupons.
 *
 * A spin produces an Outcome: a list of frames for the screen to play back in order (the
 * reels stopping, gems tumbling, coins locking, the wheel turning, free spins) and the
 * total won. Wins are counted in "units": the total bet is UNITS[machine] units, so a
 * machine's payback is the average win divided by its units.
 */
final class SlotMath {
    static final int CLASSIC = 0, WAYS = 1, COINS = 2, WHEEL = 3, GEMS = 4;
    static final String[] NAMES = {"Blazing Bars", "Thunder Plains", "Coin Vault",
            "Jackpot Wheel", "Gem Cascade", "Jade Dragon Link", "Fortune Drums",
            "Thunder Jackpot Wheel", "Lucky 7s Jackpot"};
    /** How many of the machines above are the original coupon machines; the rest are penny
     *  machines on the shared jackpot bank (see PennyMath). */
    static final int COUPON_MACHINES = 5;
    static final String[] BLURBS = {
            "Old-school 3 reels: bars, sevens and cherries. Wilds double your win.",
            "5 reels, 1,024 ways to win. Pick your free spins; 2x and 3x wilds multiply.",
            "Land 6 coins to hold & spin. Fill the screen for the Grand jackpot.",
            "Classic reels with a bonus wheel worth up to 1,000x your bet.",
            "Gems pay anywhere. Wins explode and new gems tumble in for more.",
            "Land 6 coins to hold & spin for credit prizes and the linked jackpots.",
            "Drums can strike on any spin: pick coins until three match a jackpot.",
            "Three wheel symbols spin the jackpot wheel: credits, free spins or jackpots.",
            "Classic 7s on 5 reels. Gold 7s pay anywhere; 9 of them pay 1,000x."};
    /** The total bet, in units, for each machine. */
    static final int[] UNITS = {5, 40, 20, 1, 20, 100, 100, 100, 100};
    /** Rows and columns shown. */
    static final int[] ROWS = {3, 4, 3, 3, 5, 3, 3, 3, 3};
    static final int[] COLS = {3, 5, 5, 3, 6, 5, 5, 5, 5};

    static final int REELS = 0, CASCADE = 1, HOLD = 2, WHEEL_SPIN = 3, BANNER = 4, CHOICE = 5,
            PICK = 6, JACKPOT = 7;

    private SlotMath() {
    }

    /** One thing for the screen to show. */
    static final class Frame {
        int kind;
        /** What the reels or grid show, [row][col]. */
        char[][] grid;
        /** Cells that are part of a win, to light up. */
        boolean[][] lit;
        /** Hold & spin: coin values in units, or a negative jackpot code; 0 = empty. */
        long[][] coins;
        /** Hold & spin: respins left after this one. */
        int respins;
        /** Jackpot wheel: which segment it stops on. */
        int segment;
        /** Free spins left after this frame, or -1 outside free spins. */
        int freeLeft = -1;
        /** Gem cascade: the free-spin multiplier in force. */
        int mult = 1;
        /** Units won by this frame. */
        long win;
        /** Short words for the screen: "3 wheels! Spin the wheel" and so on. */
        String text;
        /** True if the screen should slow the last reels for suspense. */
        boolean tease;
        /** True if, once this frame has landed, the player taps to go on (a bonus starts). */
        boolean waitTap;
        /** A jackpot this frame pays (JackpotBank level), or -1. */
        int jackpot = -1;
        /** Pick bonus: what each pick reveals, in order ('0'-'3' for MINI to GRAND). */
        char[] picks;
    }

    static final class Outcome {
        final List<Frame> frames = new ArrayList<>();
        long total;
        /** True if a bonus feature (free spins, hold & spin, wheel) happened. */
        boolean feature;
        /** Units won in the base spin alone, before any feature. */
        long baseWin;
        /** Thunder Plains: free spins won but not yet chosen and played (0 = none). */
        int pendingFree;
        /** Penny machines: jackpots won on this spin (JackpotBank levels). */
        final List<Integer> jackpots = new ArrayList<>();
    }

    /** Plays a spin through to the end, picking any free-spin choice at random. */
    static Outcome play(int machine, Random rnd) {
        Outcome o = play(machine, rnd, false);
        if (o.pendingFree > 0) waysFree(o, rnd, rnd.nextInt(FREE_CHOICES.length));
        return o;
    }

    /**
     * Plays a spin. With leaveChoice, a Thunder Plains free-spin win stops at a CHOICE
     * frame so the player can pick; call waysFree with their pick to finish it.
     */
    static Outcome play(int machine, Random rnd, boolean leaveChoice) {
        switch (machine) {
            case CLASSIC:
                return classic(rnd);
            case WAYS:
                return ways(rnd);
            case COINS:
                return coins(rnd);
            case WHEEL:
                return wheel(rnd);
            default:
                return gems(rnd);
        }
    }

    // ------------------------------------------------------------------ shared helpers

    /** Reel strips for the penny machines are built the same way. */
    static char[] pennyStrip(String symbols, int[] counts, long seed) {
        return strip(symbols, counts, false, seed);
    }

    static char[][] spin(char[][] strips, int rows, Random rnd) {
        return spinStrips(strips, rows, rnd);
    }

    static Frame newFrame(int kind, char[][] grid) {
        return frame(kind, grid);
    }

    static int countOf(char[][] g, char s) {
        return count(g, s);
    }

    static void light(Frame f, char s) {
        lightAll(f, s);
    }

    /**
     * Builds a reel strip from symbol counts, with blanks between symbols if asked, in an
     * order that is shuffled once with a fixed seed so it is the same on every phone.
     */
    private static char[] strip(String symbols, int[] counts, boolean blanks, long seed) {
        List<Character> list = new ArrayList<>();
        for (int i = 0; i < symbols.length(); i++) {
            for (int k = 0; k < counts[i]; k++) list.add(symbols.charAt(i));
        }
        java.util.Collections.shuffle(list, new Random(seed));
        // Spread out repeats so the same symbol is rarely stacked by accident.
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).equals(list.get(i - 1))) {
                    int j = (i + 3) % list.size();
                    Character t = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, t);
                }
            }
        }
        char[] out = new char[blanks ? list.size() * 2 : list.size()];
        for (int i = 0; i < list.size(); i++) {
            if (blanks) {
                out[2 * i] = list.get(i);
                out[2 * i + 1] = '-';
            } else {
                out[i] = list.get(i);
            }
        }
        return out;
    }

    /** Spins reel strips into a grid of the given rows. */
    private static char[][] spinStrips(char[][] strips, int rows, Random rnd) {
        int cols = strips.length;
        char[][] g = new char[rows][cols];
        for (int c = 0; c < cols; c++) {
            char[] s = strips[c];
            int stop = rnd.nextInt(s.length);
            for (int r = 0; r < rows; r++) g[r][c] = s[(stop + r) % s.length];
        }
        return g;
    }

    private static Frame frame(int kind, char[][] grid) {
        Frame f = new Frame();
        f.kind = kind;
        f.grid = grid;
        f.lit = new boolean[grid.length][grid[0].length];
        return f;
    }

    private static int count(char[][] g, char s) {
        int n = 0;
        for (char[] row : g) {
            for (char c : row) {
                if (c == s) n++;
            }
        }
        return n;
    }

    private static void lightAll(Frame f, char s) {
        for (int r = 0; r < f.grid.length; r++) {
            for (int c = 0; c < f.grid[0].length; c++) {
                if (f.grid[r][c] == s) f.lit[r][c] = true;
            }
        }
    }

    // ------------------------------------------------------------------ Blazing Bars

    /** Symbols: - blank, C cherry, 1/2/3 one, two and three bars, 7 seven, W wild (x2). */
    static final String CLASSIC_SYMS = "C1237W";
    static final char[][] CLASSIC_STRIPS = {
            strip(CLASSIC_SYMS, new int[]{1, 7, 5, 4, 3, 1}, true, 101),
            strip(CLASSIC_SYMS, new int[]{1, 7, 5, 4, 3, 1}, true, 102),
            strip(CLASSIC_SYMS, new int[]{1, 7, 5, 4, 3, 1}, true, 103)};
    /** Three of a kind, as a multiple of the line bet: cherry, 1 bar, 2 bar, 3 bar, 7. */
    static final int[] CLASSIC_PAY = {15, 10, 22, 35, 100};
    static final int CLASSIC_ANY_BAR = 6;
    static final int CLASSIC_WILDS = 800;
    static final int[] CLASSIC_CHERRY = {0, 2, 6, 15};
    /** The five lines: top, middle, bottom, and the two diagonals. */
    static final int[][] CLASSIC_LINES = {{1, 1, 1}, {0, 0, 0}, {2, 2, 2}, {0, 1, 2},
            {2, 1, 0}};

    /** What three symbols on a line pay, in line bets. Each wild doubles the win. */
    static int classicLine(char a, char b, char c) {
        char[] l = {a, b, c};
        int wilds = 0;
        for (char s : l) {
            if (s == 'W') wilds++;
        }
        if (wilds == 3) return CLASSIC_WILDS;
        int mult = 1 << wilds;
        int best = 0;
        // Three of a kind, wilds standing in.
        String kinds = "C1237";
        for (int k = 0; k < kinds.length(); k++) {
            char want = kinds.charAt(k);
            boolean ok = true;
            for (char s : l) {
                if (s != 'W' && s != want) ok = false;
            }
            if (ok) best = Math.max(best, CLASSIC_PAY[k] * mult);
        }
        // Any mix of bars.
        boolean bars = true;
        for (char s : l) {
            if (s != 'W' && s != '1' && s != '2' && s != '3') bars = false;
        }
        if (bars) best = Math.max(best, CLASSIC_ANY_BAR * mult);
        // Cherries pay on their own, wherever they sit on the line.
        int cherries = 0;
        for (char s : l) {
            if (s == 'C') cherries++;
        }
        best = Math.max(best, CLASSIC_CHERRY[cherries]);
        return best;
    }

    private static Outcome classic(Random rnd) {
        Outcome o = new Outcome();
        char[][] g = spinStrips(CLASSIC_STRIPS, 3, rnd);
        Frame f = frame(REELS, g);
        long won = 0;
        for (int[] line : CLASSIC_LINES) {
            int p = classicLine(g[line[0]][0], g[line[1]][1], g[line[2]][2]);
            if (p > 0) {
                won += p;
                for (int c = 0; c < 3; c++) f.lit[line[c]][c] = true;
            }
        }
        f.win = won;
        o.frames.add(f);
        o.total = won;
        o.baseWin = won;
        return o;
    }

    // ------------------------------------------------------------------ Thunder Plains

    /**
     * Symbols: E eagle, F wolf, B bear, D elk, then card ranks A K Q J T(10) N(9),
     * W wild (reels 2 to 4 only), S gold scatter.
     */
    static final String WAYS_SYMS = "EFBDAKQJTNWS";
    static final char[][] WAYS_STRIPS = {
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 0, 2}, false, 201),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 3, 2}, false, 202),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 3, 2}, false, 203),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 3, 2}, false, 204),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 0, 2}, false, 205)};
    /** Free spins use their own reels with more wilds, as real machines do. */
    static final char[][] WAYS_FREE_STRIPS = {
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 0, 1}, false, 211),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 4, 2}, false, 212),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 4, 2}, false, 213),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 4, 2}, false, 214),
            strip(WAYS_SYMS, new int[]{4, 5, 5, 6, 7, 7, 8, 8, 9, 9, 0, 1}, false, 215)};
    /** Pays per way for 3, 4 and 5 of a kind, in units (the bet is 40 units). */
    static final int[][] WAYS_PAY = {{15, 60, 250}, {12, 50, 175}, {10, 30, 125}, {8, 25, 100},
            {5, 15, 50}, {5, 15, 50}, {4, 10, 40}, {4, 10, 40}, {4, 8, 30}, {4, 8, 30}};
    /** Scatter pays in units (2x, 10x, 50x the bet) and the free spins given, for 3/4/5. */
    static final int[] WAYS_SCATTER_PAY = {160, 400, 2000};
    static final int[] WAYS_FREE = {8, 15, 20};

    /**
     * Adds up every way to win on a grid, in units, and lights the winning cells. In free
     * spins each wild shows a 2x or 3x, and a way through several wilds multiplies them.
     *
     * @param wildMult the multiplier on each wild cell (1 outside free spins)
     */
    static long waysPay(char[][] g, int[][] wildMult, boolean[][] lit) {
        int rows = g.length;
        int cols = g[0].length;
        long total = 0;
        for (int k = 0; k < 10; k++) {
            char s = WAYS_SYMS.charAt(k);
            long ways = 1;
            int reach = 0;
            for (int c = 0; c < cols; c++) {
                long hits = 0;
                for (int r = 0; r < rows; r++) {
                    if (g[r][c] == s) hits += 1;
                    else if (g[r][c] == 'W') hits += wildMult == null ? 1 : wildMult[r][c];
                }
                if (hits == 0) break;
                ways *= hits;
                reach++;
            }
            if (reach >= 3) {
                total += ways * WAYS_PAY[k][reach - 3];
                if (lit != null) {
                    for (int c = 0; c < reach; c++) {
                        for (int r = 0; r < rows; r++) {
                            if (g[r][c] == s || g[r][c] == 'W') lit[r][c] = true;
                        }
                    }
                }
            }
        }
        return total;
    }

    private static Outcome ways(Random rnd) {
        Outcome o = new Outcome();
        char[][] g = spinStrips(WAYS_STRIPS, 4, rnd);
        Frame f = frame(REELS, g);
        long tenths = waysPay(g, null, f.lit);
        int scat = count(g, 'S');
        int free = 0;
        if (scat >= 3) {
            int i = Math.min(scat, 5) - 3;
            tenths += WAYS_SCATTER_PAY[i];
            free = WAYS_FREE[i];
            lightAll(f, 'S');
            f.text = scat + " gold coins: " + free + " free spins!";
        }
        f.tease = scatterTease(g, 'S');
        f.win = tenths;
        o.frames.add(f);
        o.baseWin = tenths;
        o.total = tenths;
        if (free > 0) {
            o.feature = true;
            o.pendingFree = free;
            Frame c = new Frame();
            c.kind = CHOICE;
            c.freeLeft = free;
            c.text = "Pick your free spins";
            c.waitTap = true;
            o.frames.add(c);
        }
        return o;
    }

    /**
     * Free-spin choices for Thunder Plains: how many of the won spins you get, and what
     * every win is multiplied by. Fewer spins, bigger multiplier: the same on average.
     */
    static final int[][] FREE_CHOICES = {{1, 1}, {2, 2}, {3, 3}};

    static int choiceSpins(int won, int choice) {
        return (won + FREE_CHOICES[choice][0] - 1) / FREE_CHOICES[choice][0];
    }

    /** Plays the free spins after the player picks, adding frames and winnings. */
    static void waysFree(Outcome o, Random rnd, int choice) {
        int x = FREE_CHOICES[choice][1];
        int left = choiceSpins(o.pendingFree, choice);
        o.pendingFree = 0;
        long sum = o.total;
        int played = 0;
        while (left > 0 && played < 200) {
            left--;
            played++;
            char[][] fg = spinStrips(WAYS_FREE_STRIPS, 4, rnd);
            int[][] wm = new int[4][5];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 5; c++) wm[r][c] = fg[r][c] == 'W' ? 2 + rnd.nextInt(2) : 1;
            }
            Frame ff = frame(REELS, fg);
            long t = waysPay(fg, wm, ff.lit) * x;
            int sc = count(fg, 'S');
            if (sc >= 3) {
                int i = Math.min(sc, 5) - 3;
                t += WAYS_SCATTER_PAY[i];
                int more = choiceSpins(WAYS_FREE[i], choice);
                left += more;
                lightAll(ff, 'S');
                ff.text = "More free spins! +" + more;
            }
            ff.coins = new long[4][5];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 5; c++) ff.coins[r][c] = fg[r][c] == 'W' ? wm[r][c] : 0;
            }
            ff.freeLeft = left;
            ff.mult = x;
            ff.win = t;
            ff.tease = scatterTease(fg, 'S');
            o.frames.add(ff);
            sum += t;
        }
        o.total = sum;
    }

    /** True when two scatters are already showing before the last reels stop. */
    private static boolean scatterTease(char[][] g, char s) {
        int n = 0;
        int cols = g[0].length;
        for (int c = 0; c < cols - 2; c++) {
            for (char[] row : g) {
                if (row[c] == s) n++;
            }
        }
        return n >= 2;
    }

    // ------------------------------------------------------------------ Coin Vault

    /** Symbols: R dragon, G tiger, X koi, L lantern, A ace, Y king, Q, J, T (10); O coin. */
    static final String VAULT_SYMS = "RGXLAYQJT";
    static final char[][] VAULT_STRIPS = {
            strip(VAULT_SYMS, new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9}, false, 301),
            strip(VAULT_SYMS, new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9}, false, 302),
            strip(VAULT_SYMS, new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9}, false, 303),
            strip(VAULT_SYMS, new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9}, false, 304),
            strip(VAULT_SYMS, new int[]{3, 4, 5, 5, 7, 7, 8, 8, 9}, false, 305)};
    /** Pays per way for 3, 4, 5 of a kind, in units (the bet is 20 units). */
    static final int[][] VAULT_PAY = {{40, 150, 500}, {32, 100, 400}, {24, 96, 360}, {24, 72, 300},
            {25, 60, 150}, {25, 60, 150}, {22, 45, 120}, {22, 45, 120}, {21, 35, 100}};
    /** Chance of a coin on each cell in the base game. */
    static final double VAULT_COIN_BASE = 0.155;
    /**
     * Chance of a coin on each empty cell on each respin, by how many cells are already
     * filled. It gets harder as the screen fills, so the Grand is a rare event.
     */
    static final double[] VAULT_COIN_RESPIN = {0.105, 0.105, 0.105, 0.105, 0.105, 0.105, 0.105,
            0.105, 0.1, 0.085, 0.07, 0.055, 0.04, 0.02, 0.006, 0};
    static final int VAULT_TRIGGER = 6;
    /** Coin values in units (bet = 20), with how often each turns up. */
    static final long[] VAULT_VALUES = {10, 20, 30, 40, 60, 100, 250};
    static final int[] VAULT_WEIGHTS = {34, 28, 14, 9, 7, 5, 3};
    /** Jackpots in units: Mini 10x, Minor 25x, Major 100x, Grand 1,000x the bet. */
    static final long[] VAULT_JACKPOTS = {200, 500, 2000, 20000};
    static final String[] JACKPOT_NAMES = {"MINI", "MINOR", "MAJOR", "GRAND"};
    /** Out of 1,000 coins: how many are Mini, Minor, Major (Major only in respins). */
    static final int VAULT_MINI = 18, VAULT_MINOR = 8, VAULT_MAJOR = 2;

    /** A random coin: a value in units, or -1 to -3 for Mini, Minor, Major. */
    private static long coin(Random rnd, boolean respin) {
        int j = rnd.nextInt(1000);
        if (j < VAULT_MINI) return -1;
        if (j < VAULT_MINI + VAULT_MINOR) return -2;
        if (respin && j < VAULT_MINI + VAULT_MINOR + VAULT_MAJOR) return -3;
        int total = 0;
        for (int w : VAULT_WEIGHTS) total += w;
        int pick = rnd.nextInt(total);
        for (int i = 0; i < VAULT_WEIGHTS.length; i++) {
            pick -= VAULT_WEIGHTS[i];
            if (pick < 0) return VAULT_VALUES[i];
        }
        return VAULT_VALUES[0];
    }

    static long coinUnits(long c) {
        return c >= 0 ? c : VAULT_JACKPOTS[(int) (-c) - 1];
    }

    static long vaultWays(char[][] g, boolean[][] lit) {
        long total = 0;
        for (int k = 0; k < VAULT_SYMS.length(); k++) {
            char s = VAULT_SYMS.charAt(k);
            long ways = 1;
            int reach = 0;
            for (int c = 0; c < 5; c++) {
                int hits = 0;
                for (int r = 0; r < 3; r++) {
                    if (g[r][c] == s) hits++;
                }
                if (hits == 0) break;
                ways *= hits;
                reach++;
            }
            if (reach >= 3) {
                total += ways * VAULT_PAY[k][reach - 3];
                if (lit != null) {
                    for (int c = 0; c < reach; c++) {
                        for (int r = 0; r < 3; r++) {
                            if (g[r][c] == s) lit[r][c] = true;
                        }
                    }
                }
            }
        }
        return total;
    }

    private static Outcome coins(Random rnd) {
        Outcome o = new Outcome();
        char[][] g = spinStrips(VAULT_STRIPS, 3, rnd);
        long[][] cv = new long[3][5];
        int n = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 5; c++) {
                if (rnd.nextDouble() < VAULT_COIN_BASE) {
                    g[r][c] = 'O';
                    cv[r][c] = coin(rnd, false);
                    n++;
                }
            }
        }
        Frame f = frame(REELS, g);
        f.coins = cv;
        f.win = vaultWays(g, f.lit);
        o.frames.add(f);
        o.baseWin = f.win;
        long sum = f.win;
        // Suspense when 4 or more coins are already up before the last reel stops.
        int early = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                if (g[r][c] == 'O') early++;
            }
        }
        f.tease = early >= 4;
        if (n >= VAULT_TRIGGER) {
            o.feature = true;
            f.text = n + " coins: HOLD & SPIN!";
            f.waitTap = true;
            lightAll(f, 'O');
            long[][] held = new long[3][5];
            for (int r = 0; r < 3; r++) held[r] = cv[r].clone();
            int respins = 3;
            int filled = n;
            Frame start = new Frame();
            start.kind = HOLD;
            start.coins = copy(held);
            start.respins = respins;
            start.text = "3 respins. Every new coin resets them to 3.";
            o.frames.add(start);
            while (respins > 0 && filled < 15) {
                respins--;
                boolean landed = false;
                int filledBefore = filled;
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 5; c++) {
                        if (held[r][c] == 0 && rnd.nextDouble() < VAULT_COIN_RESPIN[filledBefore]) {
                            held[r][c] = coin(rnd, true);
                            filled++;
                            landed = true;
                        }
                    }
                }
                if (landed) respins = 3;
                Frame h = new Frame();
                h.kind = HOLD;
                h.coins = copy(held);
                h.respins = respins;
                o.frames.add(h);
            }
            long pay = 0;
            for (long[] row : held) {
                for (long c : row) pay += coinUnits(c);
            }
            Frame end = new Frame();
            end.kind = HOLD;
            end.coins = copy(held);
            end.respins = 0;
            if (filled == 15) {
                pay += VAULT_JACKPOTS[3];
                end.text = "FULL SCREEN: GRAND JACKPOT!";
            } else {
                end.text = "Hold & spin over";
            }
            end.win = pay;
            o.frames.add(end);
            sum += pay;
        }
        o.total = sum;
        return o;
    }

    private static long[][] copy(long[][] a) {
        long[][] b = new long[a.length][];
        for (int i = 0; i < a.length; i++) b[i] = a[i].clone();
        return b;
    }

    // ------------------------------------------------------------------ Jackpot Wheel

    /** Symbols: - blank, 1/2/3 bars, 7, D double wild (x2), S wheel (reel 3 only). */
    static final char[][] WHEEL_STRIPS = {
            strip("1237D", new int[]{6, 5, 4, 3, 1}, true, 401),
            strip("1237D", new int[]{6, 5, 4, 3, 1}, true, 402),
            strip("1237DS", new int[]{16, 13, 10, 5, 3, 2}, true, 403)};
    /** Three of a kind on the line, as a multiple of the bet: 1 bar, 2 bar, 3 bar, 7. */
    static final int[] WHEEL_PAY = {5, 10, 20, 55};
    static final int WHEEL_ANY_BAR = 2;
    static final int WHEEL_WILDS = 500;
    /** The wheel's 20 segments, as multiples of the bet. */
    static final int[] WHEEL_SEGMENTS = {10, 25, 15, 50, 10, 20, 100, 15, 30, 10, 250, 15,
            40, 20, 75, 10, 1000, 20, 30, 15};

    static int wheelLine(char a, char b, char c) {
        char[] l = {a, b, c};
        int wilds = 0;
        for (char s : l) {
            if (s == 'D') wilds++;
        }
        if (wilds == 3) return WHEEL_WILDS;
        int mult = 1 << wilds;
        int best = 0;
        String kinds = "1237";
        for (int k = 0; k < 4; k++) {
            boolean ok = true;
            for (char s : l) {
                if (s != 'D' && s != kinds.charAt(k)) ok = false;
            }
            if (ok) best = Math.max(best, WHEEL_PAY[k] * mult);
        }
        boolean bars = true;
        for (char s : l) {
            if (s != 'D' && s != '1' && s != '2' && s != '3') bars = false;
        }
        if (bars) best = Math.max(best, WHEEL_ANY_BAR * mult);
        return best;
    }

    /**
     * The wheel's segments are not all equally likely: like a real wheel game, the big
     * ones are rarer. These are the weights for each segment, in the same order.
     */
    static final int[] WHEEL_WEIGHTS = {14, 8, 12, 4, 14, 10, 2, 12, 6, 14, 1, 12, 4, 10, 3,
            14, 1, 10, 6, 12};

    private static Outcome wheel(Random rnd) {
        Outcome o = new Outcome();
        char[][] g = spinStrips(WHEEL_STRIPS, 3, rnd);
        Frame f = frame(REELS, g);
        int p = wheelLine(g[1][0], g[1][1], g[1][2]);
        if (p > 0) {
            for (int c = 0; c < 3; c++) f.lit[1][c] = true;
        }
        f.win = p;
        o.frames.add(f);
        o.baseWin = p;
        long sum = p;
        if (g[1][2] == 'S') {
            o.feature = true;
            f.lit[1][2] = true;
            f.text = "WHEEL BONUS!";
            f.waitTap = true;
            int total = 0;
            for (int w : WHEEL_WEIGHTS) total += w;
            int pick = rnd.nextInt(total);
            int seg = 0;
            for (int i = 0; i < WHEEL_WEIGHTS.length; i++) {
                pick -= WHEEL_WEIGHTS[i];
                if (pick < 0) {
                    seg = i;
                    break;
                }
            }
            Frame w = new Frame();
            w.kind = WHEEL_SPIN;
            w.segment = seg;
            w.win = WHEEL_SEGMENTS[seg];
            w.text = WHEEL_SEGMENTS[seg] + "x your bet!";
            o.frames.add(w);
            sum += w.win;
        }
        // Suspense when the first two reels already line up something good.
        f.tease = g[1][0] == '7' || g[1][0] == 'D';
        o.total = sum;
        return o;
    }

    // ------------------------------------------------------------------ Gem Cascade

    /** Gems from most to least valuable: R ruby, M emerald, P sapphire, A amethyst, T topaz,
     *  H heart, O star, C crescent; S is the scatter. */
    static final String GEM_SYMS = "RMPATHOCS";
    static final int[] GEM_WEIGHTS = {10, 14, 18, 22, 26, 30, 34, 38, 4};
    /** Pays anywhere on the board for 8-9, 10-11 and 12 or more, in units (bet = 20). */
    static final int[][] GEM_PAY = {{200, 500, 1000}, {50, 200, 500}, {40, 100, 300},
            {22, 40, 240}, {15, 30, 200}, {12, 24, 160}, {9, 20, 100}, {7, 16, 80}};
    static final int GEM_FREE = 10;

    private static char gem(Random rnd) {
        int total = 0;
        for (int w : GEM_WEIGHTS) total += w;
        int pick = rnd.nextInt(total);
        for (int i = 0; i < GEM_WEIGHTS.length; i++) {
            pick -= GEM_WEIGHTS[i];
            if (pick < 0) return GEM_SYMS.charAt(i);
        }
        return 'C';
    }

    /** Pays for a board as it stands, lighting the winning gems. */
    static long gemPay(char[][] g, boolean[][] lit) {
        long total = 0;
        for (int k = 0; k < 8; k++) {
            char s = GEM_SYMS.charAt(k);
            int n = count(g, s);
            if (n >= 8) {
                total += GEM_PAY[k][n >= 12 ? 2 : (n >= 10 ? 1 : 0)];
                for (int r = 0; r < g.length; r++) {
                    for (int c = 0; c < g[0].length; c++) {
                        if (g[r][c] == s) lit[r][c] = true;
                    }
                }
            }
        }
        return total;
    }

    /**
     * Plays one gem spin with all its tumbles, adding frames. Returns units won.
     *
     * @param mult the free-spin multiplier holder (grows with each tumble), or null
     */
    private static long gemRound(Outcome o, Random rnd, int[] mult, int freeLeft) {
        char[][] g = new char[5][6];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 6; c++) g[r][c] = gem(rnd);
        }
        long won = 0;
        boolean first = true;
        for (int step = 0; step < 50; step++) {
            Frame f = frame(first ? REELS : CASCADE, copyGrid(g));
            long p = gemPay(f.grid, f.lit);
            if (mult != null) {
                f.mult = mult[0];
                p *= mult[0];
            }
            f.win = p;
            f.freeLeft = freeLeft;
            if (first) f.tease = count(g, 'S') >= 3;
            o.frames.add(f);
            first = false;
            if (p == 0) break;
            won += p;
            if (mult != null) mult[0]++;
            // Winning gems pop; the rest fall and new ones drop in from the top.
            for (int c = 0; c < 6; c++) {
                int write = 4;
                for (int r = 4; r >= 0; r--) {
                    if (!f.lit[r][c]) g[write--][c] = g[r][c];
                }
                while (write >= 0) g[write--][c] = gem(rnd);
            }
        }
        // Scatters are counted on the board as it finally settles.
        int sc = count(g, 'S');
        o.frames.get(o.frames.size() - 1).grid = copyGrid(g);
        return won * 10 + Math.min(sc, 9);
    }

    private static char[][] copyGrid(char[][] g) {
        char[][] b = new char[g.length][];
        for (int i = 0; i < g.length; i++) b[i] = g[i].clone();
        return b;
    }

    private static Outcome gems(Random rnd) {
        Outcome o = new Outcome();
        long packed = gemRound(o, rnd, null, -1);
        long sum = packed / 10;
        int sc = (int) (packed % 10);
        o.baseWin = sum;
        if (sc >= 4) {
            o.feature = true;
            Frame last = o.frames.get(o.frames.size() - 1);
            last.lit = new boolean[5][6];
            lightAll(last, 'S');
            long scatterPay = sc == 4 ? 60 : (sc == 5 ? 100 : 2000);
            last.win += scatterPay;
            last.text = sc + " stars: " + GEM_FREE + " free spins! Every tumble raises the "
                    + "multiplier.";
            last.waitTap = true;
            sum += scatterPay;
            int left = GEM_FREE;
            int[] mult = {2};
            int played = 0;
            while (left > 0 && played < 100) {
                left--;
                played++;
                long pk = gemRound(o, rnd, mult, left);
                sum += pk / 10;
                if (pk % 10 >= 4) {
                    left += 5;
                    o.frames.get(o.frames.size() - 1).text = "+5 free spins!";
                }
            }
        }
        o.total = sum;
        return o;
    }
}
