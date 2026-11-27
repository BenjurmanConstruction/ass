package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The themed video slots on the casino floor: one engine, eight looks, each with its own
 * bonus. Kept apart from the screen so every theme's payback can be checked with millions of
 * test spins (ThemeSlotTest off-device). Wins are in multiples of the total bet.
 *
 * Symbols: 0-3 low, 4-6 high, 7 wild, 8 scatter (free spins), 9 bonus (pick a chest),
 * 10 mystery (all turn into the same symbol).
 */
final class ThemeSlotMath {
    static final int WILD = 7, SCATTER = 8, BONUS = 9, MYSTERY = 10;

    /** Features. */
    static final int F_STICKY = 1, F_EXPAND = 2, F_WILDX = 4, F_MYSTERY = 8, F_PICK = 16,
            F_CLASSIC = 32, F_FREEX = 64;

    static final class Theme {
        final String name, blurb;
        final String[] sym;
        final int top, bottom, accent, features;
        /** Free spins awarded and their multiplier. */
        final int freeSpins, freeMult;
        /** Scales every pay so the long-run payback lands near 95%. */
        final double scale;

        Theme(String name, String blurb, String[] sym, int top, int bottom, int accent,
              int features, int freeSpins, int freeMult, double scale) {
            this.name = name;
            this.blurb = blurb;
            this.sym = sym;
            this.top = top;
            this.bottom = bottom;
            this.accent = accent;
            this.features = features;
            this.freeSpins = freeSpins;
            this.freeMult = freeMult;
            this.scale = scale;
        }

        boolean has(int f) {
            return (features & f) != 0;
        }

        int cols() {
            return has(F_CLASSIC) ? 3 : 5;
        }
    }

    // Symbols run low → high, then wild, scatter, bonus, mystery.
    static final Theme[] THEMES = {
            new Theme("Pirate's Plunder",
                    "3 maps = 10 free spins. Wilds that land in free spins stay put.",
                    new String[]{"⚓", "🦜", "🗡️", "🍾", "🦑", "💰", "🏴‍☠️", "☠️", "🗺️", "", ""},
                    0xFF0E3A5A, 0xFF04121E, 0xFFFFC94A, F_STICKY, 10, 1, 0.819),
            new Theme("Cosmic Cash",
                    "Wilds on reels 2-4 grow to fill the reel. 3 planets = 8 free spins.",
                    new String[]{"🌙", "☄️", "🛰️", "👽", "🪐", "🚀", "🌟", "🌀", "🌍", "", ""},
                    0xFF1A0A3A, 0xFF04020E, 0xFF8AE0FF, F_EXPAND, 8, 1, 0.812),
            new Theme("Fruit Fiesta",
                    "Old-school 3 reels, 5 lines. Wild stars double or triple the win.",
                    new String[]{"🍒", "🍋", "🍊", "🍇", "🍉", "🔔", "7️⃣", "⭐", "", "", ""},
                    0xFF5A0E2A, 0xFF1A0410, 0xFFFFE070, F_CLASSIC | F_WILDX, 0, 1, 0.718),
            new Theme("Safari Stampede",
                    "3 sunsets = 10 free spins with every win tripled.",
                    new String[]{"🦓", "🦒", "🐆", "🦏", "🐘", "🦁", "🦍", "🌿", "🌅", "", ""},
                    0xFF5A3A0A, 0xFF1A1004, 0xFFFFB84A, F_FREEX, 10, 3, 1.224),
            new Theme("Haunted Manor",
                    "Ghosts turn into the same symbol. 3 moons = 10 free spins at 2x.",
                    new String[]{"🕯️", "🦇", "🕷️", "🎃", "🧪", "🧛", "💀", "🔮", "🌕", "",
                            "👻"},
                    0xFF2A0A3A, 0xFF08020E, 0xFFB98AFF, F_MYSTERY | F_FREEX, 10, 2, 1.299),
            new Theme("Deep Sea Treasure",
                    "Chests on reels 1, 3 and 5: pick 3 for prizes up to 50x. 3 shells = 8 free spins.",
                    new String[]{"🐚", "🐠", "🦀", "🐙", "🐬", "🦈", "💎", "🧜", "🫧", "🧰", ""},
                    0xFF04304A, 0xFF010C16, 0xFF5AE0FF, F_PICK, 8, 1, 1.401),
            new Theme("Sweet Tooth",
                    "Wild cupcakes double every win they're in. 3 lollipops = 10 free spins.",
                    new String[]{"🍬", "🍭", "🍩", "🍪", "🍫", "🍰", "🍦", "🧁", "🎂", "", ""},
                    0xFF5A1A4A, 0xFF1A0616, 0xFFFF9AD6, F_WILDX, 10, 1, 0.670),
            new Theme("Viking Raid",
                    "3 longships = 10 free spins: the middle reel is all wild.",
                    new String[]{"🪓", "🛡️", "🍖", "🐺", "🐻", "⚔️", "👑", "⚡", "⛵", "", ""},
                    0xFF2A2A3A, 0xFF0A0A12, 0xFFE0E6FF, F_FREEX, 10, 1, 1.077),
    };

    static final int[][] LINES5 = {{1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2},
            {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2}, {0, 0, 1, 2, 2}, {2, 2, 1, 0, 0}, {1, 0, 0, 0, 1},
            {1, 2, 2, 2, 1}, {1, 0, 1, 2, 1}, {1, 2, 1, 0, 1}, {0, 1, 1, 1, 0}, {2, 1, 1, 1, 2},
            {0, 1, 0, 1, 0}, {2, 1, 2, 1, 2}, {1, 1, 0, 1, 1}, {1, 1, 2, 1, 1}, {0, 0, 2, 0, 0},
            {2, 2, 0, 2, 2}, {0, 2, 2, 2, 0}};
    static final int[][] LINES3 = {{1, 1, 1}, {0, 0, 0}, {2, 2, 2}, {0, 1, 2}, {2, 1, 0}};

    /** Line pays, in line bets, for 3/4/5 of a kind (3-reel: 3 of a kind only). */
    private static final int[][] PAY = {{5, 15, 50}, {5, 20, 60}, {8, 25, 80}, {10, 30, 100},
            {15, 50, 200}, {20, 75, 300}, {30, 100, 500}, {50, 200, 1000}};
    private static final int[] PAY3 = {6, 8, 10, 14, 20, 30, 60, 120};
    /** Scatter pays, in total bets, for 3/4/5 anywhere. */
    private static final int[] SCATTER_PAY = {2, 10, 50};
    static final int[] CHESTS = {2, 3, 5, 5, 8, 10, 15, 25, 50};

    private static final int[][][] STRIPS = new int[THEMES.length][][];

    private ThemeSlotMath() {
    }

    /** Reel strips, built once per theme from symbol weights. */
    static synchronized int[][] strips(int t) {
        if (STRIPS[t] != null) return STRIPS[t];
        Theme th = THEMES[t];
        int cols = th.cols();
        int[][] out = new int[cols][];
        Random r = new Random(1000 + t);
        for (int c = 0; c < cols; c++) {
            List<Integer> s = new ArrayList<>();
            int[] w = {9, 9, 8, 8, 5, 4, 3};
            for (int k = 0; k < w.length; k++) for (int i = 0; i < w[k]; i++) s.add(k);
            int wild = th.has(F_CLASSIC) ? 2 : (c == 0 && th.has(F_EXPAND) ? 0 : 2);
            for (int i = 0; i < wild; i++) s.add(WILD);
            if (!th.has(F_CLASSIC) && th.freeSpins > 0) s.add(SCATTER);
            if (!th.has(F_CLASSIC) && th.freeSpins > 0 && c % 2 == 0) s.add(SCATTER);
            if (th.has(F_PICK) && c % 2 == 0) {
                s.add(BONUS);
                s.add(BONUS);
            }
            if (th.has(F_MYSTERY)) for (int i = 0; i < 3; i++) s.add(MYSTERY);
            java.util.Collections.shuffle(s, r);
            out[c] = new int[s.size()];
            for (int i = 0; i < s.size(); i++) out[c][i] = s.get(i);
        }
        STRIPS[t] = out;
        return out;
    }

    /** What one spin did, for the screen to play back. */
    static final class Spin {
        /** Grids to show: the base spin, then each free spin. [step][row][col]. */
        final List<int[][]> grids = new ArrayList<>();
        /** Wild multiplier per cell (Fruit Fiesta), parallel to grids; null if none. */
        final List<int[][]> wildX = new ArrayList<>();
        /** Lines won per grid: {line, count, symbol} and the win in total bets. */
        final List<List<int[]>> lines = new ArrayList<>();
        final List<Double> stepWin = new ArrayList<>();
        /** Grids before mystery symbols turned, for the reveal. */
        final List<int[][]> beforeMystery = new ArrayList<>();
        int freeAwarded;
        int[] chests;
        int[] picked;
        double pickWin;
        double total;
    }

    static Spin spin(int t, Random rnd) {
        Theme th = THEMES[t];
        Spin s = new Spin();
        int[][] g = draw(t, rnd);
        settle(t, g, s, rnd, false, null);
        int scat = count(g, SCATTER);
        if (th.has(F_PICK) && g != null && bonusHit(g)) {
            int[] chests = CHESTS.clone();
            for (int i = chests.length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int tmp = chests[i];
                chests[i] = chests[j];
                chests[j] = tmp;
            }
            s.chests = chests;
            s.picked = new int[]{0, 1, 2};
            s.pickWin = (chests[0] + chests[1] + chests[2]) * th.scale;
            s.total += s.pickWin;
        }
        if (scat >= 3 && th.freeSpins > 0) {
            s.freeAwarded = th.freeSpins;
            int left = th.freeSpins;
            boolean[][] sticky = th.has(F_STICKY) ? new boolean[3][5] : null;
            int guard = 0;
            while (left > 0 && guard++ < 60) {
                left--;
                int[][] f = draw(t, rnd);
                if (sticky != null) {
                    for (int r = 0; r < 3; r++) {
                        for (int c = 0; c < 5; c++) {
                            if (sticky[r][c]) f[r][c] = WILD;
                            if (f[r][c] == WILD) sticky[r][c] = true;
                        }
                    }
                }
                if (t == 7) {
                    // Viking Raid: the middle reel is all wild in free spins.
                    for (int r = 0; r < 3; r++) f[r][2] = WILD;
                }
                settle(t, f, s, rnd, true, null);
                if (count(f, SCATTER) >= 3) {
                    left += th.freeSpins / 2;
                    s.freeAwarded += th.freeSpins / 2;
                }
            }
        }
        return s;
    }

    private static boolean bonusHit(int[][] g) {
        boolean a = false, b = false, c = false;
        for (int r = 0; r < 3; r++) {
            if (g[r][0] == BONUS) a = true;
            if (g[r][2] == BONUS) b = true;
            if (g[r][4] == BONUS) c = true;
        }
        return a && b && c;
    }

    private static int[][] draw(int t, Random rnd) {
        int[][] st = strips(t);
        int cols = st.length;
        int[][] g = new int[3][cols];
        for (int c = 0; c < cols; c++) {
            int[] strip = st[c];
            int stop = rnd.nextInt(strip.length);
            for (int r = 0; r < 3; r++) g[r][c] = strip[(stop + r) % strip.length];
        }
        return g;
    }

    /** Applies the base-game features to a grid, pays it, and records it. */
    private static void settle(int t, int[][] g, Spin s, Random rnd, boolean free,
                               int[][] unused) {
        Theme th = THEMES[t];
        int cols = g[0].length;
        if (th.has(F_MYSTERY)) {
            int[][] before = copy(g);
            int turn = rnd.nextInt(7);
            boolean any = false;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < cols; c++) {
                    if (g[r][c] == MYSTERY) {
                        g[r][c] = turn;
                        any = true;
                    }
                }
            }
            s.beforeMystery.add(any ? before : null);
        } else {
            s.beforeMystery.add(null);
        }
        if (th.has(F_EXPAND)) {
            for (int c = 1; c <= 3; c++) {
                boolean w = false;
                for (int r = 0; r < 3; r++) if (g[r][c] == WILD) w = true;
                if (w) for (int r = 0; r < 3; r++) g[r][c] = WILD;
            }
        }
        int[][] wx = null;
        if (th.has(F_WILDX)) {
            wx = new int[3][cols];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < cols; c++) {
                    if (g[r][c] == WILD) {
                        wx[r][c] = th.has(F_CLASSIC) ? (rnd.nextInt(3) == 0 ? 3 : 2) : 2;
                    }
                }
            }
        }
        List<int[]> won = new ArrayList<>();
        double win = 0;
        int[][] lines = th.has(F_CLASSIC) ? LINES3 : LINES5;
        double lineBet = 1.0 / lines.length;
        for (int li = 0; li < lines.length; li++) {
            int[] ln = lines[li];
            int first = -1;
            int n = 0;
            int mult = 1;
            for (int c = 0; c < cols; c++) {
                int v = g[ln[c]][c];
                if (v == SCATTER || v == BONUS) break;
                if (v == WILD) {
                    n++;
                    if (wx != null) mult *= wx[ln[c]][c];
                    continue;
                }
                if (first < 0) {
                    first = v;
                    n++;
                } else if (v == first) {
                    n++;
                } else {
                    break;
                }
            }
            int sym = first < 0 ? WILD : first;
            double pay = 0;
            if (th.has(F_CLASSIC)) {
                if (n == 3) pay = PAY3[sym];
            } else if (n >= 3) {
                pay = PAY[sym][n - 3];
            }
            // A line of all wilds pays the wild line, not times its own multiplier twice.
            if (pay > 0) {
                double w = pay * lineBet * mult * th.scale;
                win += w;
                won.add(new int[]{li, n, sym});
            }
        }
        int scat = count(g, SCATTER);
        if (scat >= 3) win += SCATTER_PAY[Math.min(5, scat) - 3] * th.scale;
        if (free) win *= th.freeMult;
        s.grids.add(g);
        s.wildX.add(wx);
        s.lines.add(won);
        s.stepWin.add(win);
        s.total += win;
    }

    static int count(int[][] g, int sym) {
        int n = 0;
        for (int[] row : g) for (int v : row) if (v == sym) n++;
        return n;
    }

    private static int[][] copy(int[][] g) {
        int[][] o = new int[g.length][];
        for (int i = 0; i < g.length; i++) o[i] = g[i].clone();
        return o;
    }
}
