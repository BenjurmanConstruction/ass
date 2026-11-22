package app.easywake;

import java.util.Locale;

/**
 * The pictures painted onto the 3D casino: the carpet, every machine's screen and topper
 * sign, the area signs and the live jackpot board. They're drawn through a Painter so the
 * same art is made on the phone (Android Canvas) and in desktop previews.
 *
 * Everything goes on one 1024x1024 sheet (the atlas); the carpet is its own 256x256 tile.
 */
final class CasinoArt {
    static final int ATLAS = 1024;
    /** The sheet is twice as tall as it is wide. */
    static final int ATLAS_H = 2048;
    static final int CARPET = 256;

    /** Draws shapes and text in pixels on a sheet. */
    interface Painter {
        void rect(float x, float y, float w, float h, int color);

        void roundRect(float x, float y, float w, float h, float r, int color);

        void oval(float cx, float cy, float rx, float ry, int color);

        void line(float x1, float y1, float x2, float y2, float width, int color);

        void vgradient(float x, float y, float w, float h, int top, int bottom);

        /** align: 0 left, 1 center, 2 right. y is the text's vertical middle. */
        void text(String s, float x, float y, float size, int color, boolean bold, int align);

        float measure(String s, float size, boolean bold);
    }

    /** A spot on the sheet, in pixels. */
    static final class Region {
        final int x, y, w, h;

        Region(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        float u0() {
            return (x + 0.5f) / ATLAS;
        }

        float v0() {
            return (y + 0.5f) / ATLAS_H;
        }

        float u1() {
            return (x + w - 0.5f) / ATLAS;
        }

        float v1() {
            return (y + h - 0.5f) / ATLAS_H;
        }
    }

    // ------------------------------------------------------------------ machines

    static final int MACHINES = 12;
    static final int VIDEO_POKER = 11;
    static final String[] MACHINE_NAMES = {"BLAZING BARS", "THUNDER PLAINS", "COIN VAULT",
            "JACKPOT WHEEL", "GEM CASCADE", "JADE DRAGON LINK", "FORTUNE DRUMS",
            "THUNDER WHEEL", "LUCKY 7s JACKPOT", "LUCKY SEVENS", "ROYAL RICHES", "VIDEO POKER"};
    /** Each machine's glow color. */
    static final int[] GLOW = {0xFFFF7043, 0xFFFFB300, 0xFFFFD54F, 0xFF4FC3F7, 0xFFE040FB,
            0xFF4CE0A0, 0xFFFF8F00, 0xFF82B1FF, 0xFFFF1744, 0xFFFF5252, 0xFFB388FF, 0xFF40C4FF};
    /** Each machine's screen background, top and bottom. */
    private static final int[][] SCREEN = {{0xFF4A0E0E, 0xFF1A0505}, {0xFF3A2A0A, 0xFF120C02},
            {0xFF3A0A20, 0xFF14030A}, {0xFF0A2A4A, 0xFF030C18}, {0xFF2A0A4A, 0xFF0C0318},
            {0xFF0A3A28, 0xFF03140C}, {0xFF4A1A04, 0xFF180802}, {0xFF14224A, 0xFF050A18},
            {0xFF4A0A0A, 0xFF180303}, {0xFF3A0A0A, 0xFF120303}, {0xFF22104A, 0xFF0A0418},
            {0xFF0A1E4A, 0xFF030918}};

    static Region screen(int m) {
        return new Region((m % 6) * 168 + 4, (m / 6) * 188 + 4, 160, 120);
    }

    static Region topper(int m) {
        return new Region((m % 6) * 168 + 4, (m / 6) * 188 + 128, 160, 56);
    }

    // ------------------------------------------------------------------ signs

    static final String[] SIGNS = {"PENNY PALACE", "SLOT PARLOR", "TEXAS HOLD'EM", "BLACKJACK",
            "ROULETTE", "VIDEO POKER", "CASHIER", "CLASSIC SLOTS", "THE GILDED ACE",
            "LUCKY PAWN", "MIDNIGHT DINER", "STARLITE MOTEL", "THE LAST CALL", "HIGH LIMIT",
            "STAFF ONLY", "VACANCY", "VAULT", "SECURITY", "MANAGER", "OPEN 24 HRS"};
    static final int PENNY = 0, PARLOR = 1, HOLDEM = 2, BLACKJACK = 3, ROULETTE = 4, POKER = 5,
            CASHIER = 6, CLASSIC = 7, GILDED = 8, PAWN = 9, DINER = 10, MOTEL = 11, BAR = 12,
            HIGH = 13, STAFF = 14, VACANCY = 15, VAULT = 16, SECURITY = 17, MANAGER = 18,
            OPEN = 19;
    private static final int[] SIGN_COLOR = {0xFF4CE0A0, 0xFFFFB300, 0xFFE6B869, 0xFFE6B869,
            0xFFE6B869, 0xFF40C4FF, 0xFFE6B869, 0xFFFF5252, 0xFFFFC94A, 0xFF69F0AE, 0xFFFF6FA8,
            0xFF6EC8FF, 0xFFFF4A3A, 0xFFD6A8FF, 0xFFFFFFFF, 0xFFFF3B30, 0xFFC0C4CC, 0xFF9AA4B4,
            0xFFE6B869, 0xFFFF6FA8};

    static Region sign(int i) {
        if (i >= 8) return new Region((i % 2) * 512 + 4, 1024 + ((i - 8) / 2) * 100, 504, 92);
        return new Region((i % 2) * 512 + 4, 384 + (i / 2) * 100, 504, 92);
    }

    static final Region JACKPOT_BOARD = new Region(4, 788, 504, 200);
    static final Region ROULETTE_LAYOUT = new Region(516, 788, 252, 96);
    static final Region BLACKJACK_FELT = new Region(772, 788, 248, 96);
    static final Region HOLDEM_FELT = new Region(516, 892, 252, 96);
    /** A plain white spot, for anything that just wants its color. */
    static final Region WHITE = new Region(780, 900, 8, 8);

    // ------------------------------------------------------------------ painting

    /** Paints the whole atlas sheet (jackpot values as given, in credits). */
    static void paintAtlas(Painter p, double[] jackpots) {
        p.rect(0, 0, ATLAS, ATLAS_H, 0xFF000000);
        for (int m = 0; m < MACHINES; m++) {
            paintScreen(p, m);
            paintTopper(p, m);
        }
        for (int i = 0; i < SIGNS.length; i++) paintSign(p, i);
        paintJackpots(p, jackpots);
        paintRoulette(p);
        paintFelt(p, BLACKJACK_FELT, "BLACKJACK PAYS 3 TO 2", "DEALER STANDS ON ALL 17s");
        paintFelt(p, HOLDEM_FELT, "TEXAS HOLD'EM", "NO LIMIT");
        p.rect(WHITE.x - 2, WHITE.y - 2, WHITE.w + 4, WHITE.h + 4, 0xFFFFFFFF);
    }

    private static void paintScreen(Painter p, int m) {
        Region r = screen(m);
        p.vgradient(r.x, r.y, r.w, r.h, SCREEN[m][0], SCREEN[m][1]);
        int glow = GLOW[m];
        if (m == VIDEO_POKER) {
            // Five cards and the pay table look.
            p.rect(r.x + 6, r.y + 6, r.w - 12, 34, 0xFF0D1B3A);
            String[] rows = {"ROYAL FLUSH  800", "STRAIGHT FLUSH  50", "FOUR OF A KIND  25"};
            for (int i = 0; i < 3; i++) {
                p.text(rows[i], r.x + 10, r.y + 13 + i * 11, 9, 0xFFFFEB3B, true, 0);
            }
            String[] cards = {"A", "K", "Q", "J", "10"};
            int[] suit = {0xFFD32F2F, 0xFF111111, 0xFFD32F2F, 0xFF111111, 0xFFD32F2F};
            for (int i = 0; i < 5; i++) {
                float x = r.x + 10 + i * 29;
                p.roundRect(x, r.y + 50, 25, 36, 3, 0xFFFDFDFD);
                p.text(cards[i], x + 12.5f, r.y + 63, 13, suit[i], true, 1);
                p.oval(x + 12.5f, r.y + 77, 4, 4, suit[i]);
            }
            p.text("DEAL", r.x + r.w / 2f, r.y + 104, 12, glow, true, 1);
            return;
        }
        boolean three = m == 0 || m == 3 || m == 9 || m == 10;
        int cols = three ? 3 : 5;
        int rows = 3;
        float gx = r.x + 8;
        float gy = r.y + 8;
        float gw = r.w - 16;
        float gh = r.h - 34;
        float cw = gw / cols;
        float ch = gh / rows;
        p.rect(gx, gy, gw, gh, 0xFF0B0E16);
        int seed = m * 7919 + 13;
        for (int c = 0; c < cols; c++) {
            if (c > 0) p.rect(gx + c * cw - 0.5f, gy, 1, gh, 0x33FFFFFF);
            for (int rr = 0; rr < rows; rr++) {
                seed = seed * 1103515245 + 12345;
                int kind = (seed >>> 16) % 6;
                symbol(p, kind, gx + (c + 0.5f) * cw, gy + (rr + 0.5f) * ch,
                        Math.min(cw, ch) * 0.8f, m);
            }
        }
        // Win line and credit strip.
        p.rect(gx, gy + gh / 2 - 1, gw, 2, (glow & 0x00FFFFFF) | 0xAA000000);
        p.rect(r.x, r.y + r.h - 24, r.w, 24, 0xFF05060A);
        p.text(m >= 5 && m <= 8 ? "CREDITS 2,500" : "WIN 0", r.x + 8, r.y + r.h - 12, 10,
                0xFFFFE082, true, 0);
        p.text("SPIN", r.x + r.w - 8, r.y + r.h - 12, 10, glow, true, 2);
    }

    /** A small reel symbol: a 7, a bar, a cherry, a gem, a bell or a coin. */
    private static void symbol(Painter p, int kind, float x, float y, float s, int m) {
        switch (kind) {
            case 0:
                p.text("7", x, y, s * 0.95f, 0xFFFF3D3D, true, 1);
                break;
            case 1:
                p.roundRect(x - s * 0.38f, y - s * 0.14f, s * 0.76f, s * 0.28f, 2, 0xFF111111);
                p.text("BAR", x, y, s * 0.22f, 0xFFFFFFFF, true, 1);
                break;
            case 2:
                p.oval(x - s * 0.13f, y + s * 0.12f, s * 0.14f, s * 0.14f, 0xFFE53935);
                p.oval(x + s * 0.15f, y + s * 0.1f, s * 0.14f, s * 0.14f, 0xFFC62828);
                p.line(x - s * 0.1f, y, x + s * 0.12f, y - s * 0.3f, 2, 0xFF2E7D32);
                break;
            case 3:
                p.oval(x, y, s * 0.3f, s * 0.24f, GLOW[m]);
                p.oval(x - s * 0.1f, y - s * 0.08f, s * 0.07f, s * 0.05f, 0xCCFFFFFF);
                break;
            case 4:
                p.oval(x, y, s * 0.28f, s * 0.3f, 0xFFFFC107);
                p.rect(x - s * 0.3f, y + s * 0.18f, s * 0.6f, s * 0.08f, 0xFFFFA000);
                break;
            default:
                p.oval(x, y, s * 0.3f, s * 0.3f, 0xFFFFD54F);
                p.oval(x, y, s * 0.2f, s * 0.2f, 0xFFFFA000);
                break;
        }
    }

    private static void paintTopper(Painter p, int m) {
        Region r = topper(m);
        int glow = GLOW[m];
        p.vgradient(r.x, r.y, r.w, r.h, darker(glow, 0.55f), darker(glow, 0.2f));
        // Chaser bulbs along the edge.
        for (int i = 0; i < 16; i++) {
            float x = r.x + 6 + i * (r.w - 12) / 15f;
            p.oval(x, r.y + 4, 2.2f, 2.2f, 0xFFFFF8E1);
            p.oval(x, r.y + r.h - 4, 2.2f, 2.2f, 0xFFFFF8E1);
        }
        String name = MACHINE_NAMES[m];
        float size = fit(p, name, r.w - 16, 22, true);
        p.text(name, r.x + r.w / 2f + 1, r.y + r.h / 2f + 1, size, 0xAA000000, true, 1);
        p.text(name, r.x + r.w / 2f, r.y + r.h / 2f, size, 0xFFFFFFFF, true, 1);
    }

    private static void paintSign(Painter p, int i) {
        Region r = sign(i);
        int c = SIGN_COLOR[i];
        p.rect(r.x, r.y, r.w, r.h, 0xFF0A0A0E);
        p.rect(r.x + 3, r.y + 3, r.w - 6, 3, c);
        p.rect(r.x + 3, r.y + r.h - 6, r.w - 6, 3, c);
        String s = SIGNS[i];
        float size = fit(p, s, r.w - 40, 54, true);
        p.text(s, r.x + r.w / 2f, r.y + r.h / 2f, size, (c & 0x00FFFFFF) | 0x55000000, true, 1);
        p.text(s, r.x + r.w / 2f, r.y + r.h / 2f, size * 0.97f, c, true, 1);
    }

    /** The jackpot board: GRAND and MAJOR big, MINOR and MINI below. Values in credits. */
    static void paintJackpots(Painter p, double[] v) {
        Region r = JACKPOT_BOARD;
        p.rect(r.x, r.y, r.w, r.h, 0xFF07070B);
        p.rect(r.x + 2, r.y + 2, r.w - 4, 2, 0xFFFFD54F);
        p.rect(r.x + 2, r.y + r.h - 4, r.w - 4, 2, 0xFFFFD54F);
        String[] names = {"GRAND", "MAJOR", "MINOR", "MINI"};
        int[] colors = {0xFFFFD600, 0xFFFF5252, 0xFF69F0AE, 0xFF82B1FF};
        int[] order = {3, 2, 1, 0};
        float[] ys = {r.y + 44, r.y + 108, r.y + 160, r.y + 160};
        float[] xs = {r.x + r.w / 2f, r.x + r.w / 2f, r.x + r.w * 0.27f, r.x + r.w * 0.73f};
        float[] sizes = {38, 30, 20, 20};
        for (int k = 0; k < 4; k++) {
            int level = order[k];
            double credits = v == null || v.length <= level ? 0 : v[level];
            String amount = String.format(Locale.US, "%,.2f", credits / 100.0);
            p.text(names[k], xs[k], ys[k] - sizes[k] * 0.72f, sizes[k] * 0.42f, colors[k], true,
                    1);
            p.text(amount, xs[k], ys[k] + sizes[k] * 0.12f, sizes[k], colors[k], true, 1);
        }
    }

    private static void paintRoulette(Painter p) {
        Region r = ROULETTE_LAYOUT;
        p.rect(r.x, r.y, r.w, r.h, 0xFF0E5A34);
        float cw = (r.w - 24) / 12f;
        float ch = (r.h - 8) / 3f;
        p.rect(r.x + 2, r.y + 4, 20, r.h - 8, 0xFF1B8A4C);
        p.text("0", r.x + 12, r.y + r.h / 2f, 12, 0xFFFFFFFF, true, 1);
        int[] red = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
        for (int c = 0; c < 12; c++) {
            for (int row = 0; row < 3; row++) {
                int n = c * 3 + (3 - row);
                boolean isRed = false;
                for (int k : red) isRed |= k == n;
                float x = r.x + 22 + c * cw;
                float y = r.y + 4 + row * ch;
                p.rect(x + 1, y + 1, cw - 2, ch - 2, isRed ? 0xFFB71C1C : 0xFF111111);
                p.text(String.valueOf(n), x + cw / 2, y + ch / 2, 9, 0xFFFFFFFF, true, 1);
            }
        }
    }

    private static void paintFelt(Painter p, Region r, String top, String bottom) {
        p.rect(r.x, r.y, r.w, r.h, 0xFF0E5A34);
        p.text(top, r.x + r.w / 2f, r.y + r.h * 0.38f, fit(p, top, r.w - 20, 20, true),
                0xFFE6B869, true, 1);
        p.text(bottom, r.x + r.w / 2f, r.y + r.h * 0.7f, fit(p, bottom, r.w - 30, 12, false),
                0xCCE6B869, false, 1);
    }

    // ------------------------------------------------------------------ carpet

    /**
     * A dark casino carpet that tiles seamlessly: deep burgundy, a gold lattice, and teal and
     * gold medallions.
     */
    static void paintCarpet(Painter p) {
        int n = CARPET;
        p.rect(0, 0, n, n, 0xFF2A0A12);
        // Soft mottling so it doesn't look flat.
        int seed = 99;
        for (int i = 0; i < 220; i++) {
            seed = seed * 1103515245 + 12345;
            float x = ((seed >>> 8) & 255);
            seed = seed * 1103515245 + 12345;
            float y = ((seed >>> 8) & 255);
            wrapOval(p, x, y, 10, 10, 0x0EFFFFFF & (i % 2 == 0 ? 0x10FFC0C0 : 0x10000000));
        }
        // Diagonal lattice.
        for (int k = -2; k <= 2; k++) {
            float o = k * n / 2f;
            p.line(o, 0, o + n, n, 1.6f, 0x40B8862E);
            p.line(o + n, 0, o, n, 1.6f, 0x40B8862E);
        }
        // Medallions at lattice crossings.
        float[][] at = {{0, 0}, {n / 2f, n / 2f}};
        for (float[] c : at) {
            wrapOval(p, c[0], c[1], 22, 22, 0xFF14303A);
            wrapOval(p, c[0], c[1], 18, 18, 0xFF2A0A12);
            wrapOval(p, c[0], c[1], 12, 12, 0xFF7A5A26);
            wrapOval(p, c[0], c[1], 7, 7, 0xFF4E1420);
        }
        float[][] small = {{n / 2f, 0}, {0, n / 2f}};
        for (float[] c : small) {
            wrapOval(p, c[0], c[1], 7, 7, 0xFF183C44);
            wrapOval(p, c[0], c[1], 3, 3, 0xFF8A6A30);
        }
        // Tiny gold flecks.
        for (int i = 0; i < 90; i++) {
            seed = seed * 1103515245 + 12345;
            float x = ((seed >>> 8) & 255);
            seed = seed * 1103515245 + 12345;
            float y = ((seed >>> 8) & 255);
            wrapOval(p, x, y, 1.0f, 1.0f, 0x55B8902E);
        }
    }

    /** Draws an oval and its copies across the tile edges so the carpet tiles cleanly. */
    private static void wrapOval(Painter p, float x, float y, float rx, float ry, int color) {
        int n = CARPET;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                float cx = x + dx * n;
                float cy = y + dy * n;
                if (cx + rx < 0 || cx - rx > n || cy + ry < 0 || cy - ry > n) continue;
                p.oval(cx, cy, rx, ry, color);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static float fit(Painter p, String s, float maxW, float size, boolean bold) {
        while (size > 6 && p.measure(s, size, bold) > maxW) size -= 1;
        return size;
    }

    static int darker(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * f);
        int b = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
