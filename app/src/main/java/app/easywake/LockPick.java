package app.easywake;

import java.util.Random;

/**
 * A pin-tumbler lock. With turning pressure on, only one pin binds at a time, in an order
 * you have to find. Lift the binding pin to the shear line and let go to set it. The others
 * just spring back down. Push the binding pin too far and it jams (overset): let off the
 * pressure and start over, and the pick may snap.
 *
 * Heights run 0 (resting) to 1 (top of the chamber).
 */
final class LockPick {
    final int pins;
    final float[] target;
    final float[] lift;
    final boolean[] set;
    final int[] order;
    final float tol;
    int bound;
    boolean overset;
    /** True while the binding pin is inside its window: a click you can feel. */
    private boolean inWindow;
    static final float OVER = 0.2f;

    LockPick(int pins, Random r) {
        this.pins = pins;
        target = new float[pins];
        lift = new float[pins];
        set = new boolean[pins];
        order = new int[pins];
        // More pins, tighter window.
        tol = Math.max(0.055f, 0.11f - (pins - 3) * 0.014f);
        for (int i = 0; i < pins; i++) {
            target[i] = 0.3f + r.nextFloat() * 0.45f;
            order[i] = i;
        }
        for (int i = pins - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
    }

    int binding() {
        return bound < pins ? order[bound] : -1;
    }

    boolean open() {
        return bound >= pins;
    }

    static final int NOTHING = 0, CLICK = 1, JAM = 2;

    /**
     * Pushes pin i to height h while your finger's on it. The binding pin is stiff: it rises
     * slower than your finger. Returns CLICK on entering the window, JAM on overset.
     */
    int push(int i, float h) {
        if (i < 0 || i >= pins || set[i] || overset) return NOTHING;
        h = Math.max(0, Math.min(1, h));
        if (i == binding()) {
            lift[i] = h * 0.8f;
            if (lift[i] > target[i] + tol + OVER) {
                overset = true;
                inWindow = false;
                return JAM;
            }
            boolean in = Math.abs(lift[i] - target[i]) <= tol;
            int ev = in && !inWindow ? CLICK : NOTHING;
            inWindow = in;
            return ev;
        }
        lift[i] = h;
        return NOTHING;
    }

    /** Finger off pin i. Returns true if it set. */
    boolean release(int i) {
        if (i < 0 || i >= pins || set[i]) return false;
        inWindow = false;
        if (overset && i == binding()) return false;
        if (i == binding() && Math.abs(lift[i] - target[i]) <= tol) {
            set[i] = true;
            lift[i] = target[i];
            bound++;
            return true;
        }
        lift[i] = 0;
        return false;
    }

    /** Lets off the pressure: every pin drops. */
    void reset() {
        for (int i = 0; i < pins; i++) {
            lift[i] = 0;
            set[i] = false;
        }
        bound = 0;
        overset = false;
        inWindow = false;
    }
}
