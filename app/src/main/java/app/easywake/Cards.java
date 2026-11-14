package app.easywake;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Playing cards for the card games. A card is a number from 0 to 51: its rank is
 * card % 13 (0 is a two, 12 is an ace) and its suit is card / 13.
 */
final class Cards {
    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J",
            "Q", "K", "A"};
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] HAND_NAMES = {"High card", "A pair", "Two pair",
            "Three of a kind", "A straight", "A flush", "A full house", "Four of a kind",
            "A straight flush"};

    private Cards() {}

    static int rank(int card) {
        return card % 13;
    }

    static int suit(int card) {
        return card / 13;
    }

    static String label(int card) {
        return RANKS[rank(card)] + SUITS[suit(card)];
    }

    /** A freshly shuffled deck; take cards from the end. */
    static List<Integer> deck(Random rnd) {
        List<Integer> d = new ArrayList<>();
        for (int i = 0; i < 52; i++) d.add(i);
        Collections.shuffle(d, rnd);
        return d;
    }

    /** A drawn card. Pass -1 for a face-down card and -2 for an empty outline. */
    static TextView view(Context c, int card, int widthDp) {
        TextView t = new TextView(c);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(widthDp * 0.36f);
        t.setIncludeFontPadding(false);
        if (card == -2) {
            android.graphics.drawable.GradientDrawable bg = Ui.round(c, 0x14FFFFFF, 8);
            bg.setStroke(Ui.dp(c, 1), 0x33FFFFFF);
            t.setBackground(bg);
        } else if (card < 0) {
            android.graphics.drawable.GradientDrawable bg = Ui.round(c, 0xFF24304A, 8);
            bg.setStroke(Ui.dp(c, 2), Ui.ACCENT);
            t.setBackground(bg);
            t.setText("◆");
            t.setTextColor(Ui.ACCENT);
        } else {
            t.setBackground(Ui.round(c, 0xFFF7F4EC, 8));
            boolean red = suit(card) == 1 || suit(card) == 2;
            t.setTextColor(red ? 0xFFC62828 : 0xFF14181F);
            t.setText(RANKS[rank(card)] + "\n" + SUITS[suit(card)]);
            t.setLineSpacing(0, 0.9f);
        }
        return t;
    }

    /** Fills a row with cards. */
    static void show(Context c, LinearLayout row, int[] cards, int widthDp) {
        row.removeAllViews();
        row.setGravity(Gravity.CENTER);
        for (int card : cards) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, widthDp),
                    Ui.dp(c, widthDp * 1.4f));
            int m = Ui.dp(c, 3);
            lp.setMargins(m, 0, m, 0);
            row.addView(view(c, card, widthDp), lp);
        }
    }

    /** The highest card of a straight in this set of ranks (bit i = rank i), or -1. */
    private static int straightHigh(int mask) {
        for (int high = 12; high >= 4; high--) {
            int need = 0x1F << (high - 4);
            if ((mask & need) == need) return high;
        }
        // Ace, 2, 3, 4, 5.
        int wheel = (1 << 12) | 0xF;
        return (mask & wheel) == wheel ? 3 : -1;
    }

    private static long pack(int category, int... kickers) {
        long v = category;
        for (int i = 0; i < 5; i++) v = v * 13 + (i < kickers.length ? kickers[i] : 0);
        return v;
    }

    /** Scores the best five-card poker hand in five to seven cards. Higher is better. */
    static long score(int[] cards) {
        int[] count = new int[13];
        int[] suitCount = new int[4];
        int[] suitMask = new int[4];
        int mask = 0;
        for (int card : cards) {
            count[rank(card)]++;
            suitCount[suit(card)]++;
            suitMask[suit(card)] |= 1 << rank(card);
            mask |= 1 << rank(card);
        }
        int flush = -1;
        for (int s = 0; s < 4; s++) {
            if (suitCount[s] >= 5) flush = s;
        }
        if (flush >= 0) {
            int sf = straightHigh(suitMask[flush]);
            if (sf >= 0) return pack(8, sf);
        }
        int four = -1;
        int three = -1;
        int pairHigh = -1;
        int pairLow = -1;
        for (int r = 12; r >= 0; r--) {
            if (count[r] == 4 && four < 0) {
                four = r;
            } else if (count[r] == 3 && three < 0) {
                three = r;
            } else if (count[r] >= 2) {
                if (pairHigh < 0) pairHigh = r;
                else if (pairLow < 0) pairLow = r;
            }
        }
        if (four >= 0) return pack(7, four, top(count, 1, four, -1)[0]);
        if (three >= 0 && pairHigh >= 0) return pack(6, three, pairHigh);
        if (flush >= 0) {
            int[] k = new int[5];
            int n = 0;
            for (int r = 12; r >= 0 && n < 5; r--) {
                if ((suitMask[flush] & (1 << r)) != 0) k[n++] = r;
            }
            return pack(5, k);
        }
        int st = straightHigh(mask);
        if (st >= 0) return pack(4, st);
        if (three >= 0) {
            int[] k = top(count, 2, three, -1);
            return pack(3, three, k[0], k[1]);
        }
        if (pairHigh >= 0 && pairLow >= 0) {
            return pack(2, pairHigh, pairLow, top(count, 1, pairHigh, pairLow)[0]);
        }
        if (pairHigh >= 0) {
            int[] k = top(count, 3, pairHigh, -1);
            return pack(1, pairHigh, k[0], k[1], k[2]);
        }
        return pack(0, top(count, 5, -1, -1));
    }

    /** The highest ranks held, skipping up to two given ranks. */
    private static int[] top(int[] count, int howMany, int skipA, int skipB) {
        int[] out = new int[howMany];
        int n = 0;
        for (int r = 12; r >= 0 && n < howMany; r--) {
            if (count[r] > 0 && r != skipA && r != skipB) out[n++] = r;
        }
        return out;
    }

    static String handName(long score) {
        long category = score;
        for (int i = 0; i < 5; i++) category /= 13;
        return HAND_NAMES[(int) Math.max(0, Math.min(8, category))];
    }
}
