package app.easywake;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deals items like a shuffled deck of cards: every item comes up once before any repeats.
 * The remaining order is saved, so it carries on where it left off the next time the app opens.
 */
final class Deck {
    private static final Random RND = new Random();

    private Deck() {}

    /** The next index from 0 to size-1 for the deck saved under this name. */
    static int next(Context c, String name, int size) {
        if (size <= 0) return 0;
        if (c == null) return RND.nextInt(size);
        String key = "deck_" + name;
        String saved = Prefs.getString(c, key);
        List<Integer> left = new ArrayList<>();
        int oldSize = 0;
        if (saved != null && saved.contains(":")) {
            try {
                String[] halves = saved.split(":", 2);
                oldSize = Integer.parseInt(halves[0]);
                for (String p : halves[1].split(",")) {
                    if (p.isEmpty()) continue;
                    int v = Integer.parseInt(p);
                    if (v >= 0 && v < size) left.add(v);
                }
            } catch (Exception e) {
                left.clear();
                oldSize = 0;
            }
        }
        // An update added new items: mix them into what's left so they show up soon.
        if (oldSize > 0 && oldSize < size) {
            for (int i = oldSize; i < size; i++) left.add(i);
            Collections.shuffle(left, RND);
        }
        int lastDealt = Prefs.getInt(c, key + "_last", -1);
        if (left.isEmpty()) {
            for (int i = 0; i < size; i++) left.add(i);
            Collections.shuffle(left, RND);
            // Don't start a fresh deck with the item that just ended the old one.
            if (size > 1 && left.get(left.size() - 1) == lastDealt) {
                Collections.swap(left, 0, left.size() - 1);
            }
        }
        int pick = left.remove(left.size() - 1);
        StringBuilder sb = new StringBuilder().append(size).append(':');
        for (int i = 0; i < left.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(left.get(i));
        }
        Prefs.putString(c, key, sb.toString());
        Prefs.putInt(c, key + "_last", pick);
        return pick;
    }
}
