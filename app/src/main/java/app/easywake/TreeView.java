package app.easywake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the skill tree as a tree: a trunk, six limbs, and one round node per unlock.
 * Gold nodes are owned, outlined ones can be bought now, dim ones are locked.
 */
final class TreeView extends View {
    interface Listener {
        void onNode(Tree.Node node);
    }

    /** One limb of the tree. Positions are fractions of the view's width and height. */
    private static final class Limb {
        final String label;
        final float sx, sy, cx, cy, ex, ey;

        Limb(String label, float sx, float sy, float cx, float cy, float ex, float ey) {
            this.label = label;
            this.sx = sx;
            this.sy = sy;
            this.cx = cx;
            this.cy = cy;
            this.ex = ex;
            this.ey = ey;
        }
    }

    // Limb order: casino, market lessons (the trunk's crown), market tools, wealth,
    // life tools, helper, sleep.
    private static final Limb[] LIMBS = {
            new Limb("CASINO", 0.50f, 0.50f, 0.20f, 0.47f, 0.12f, 0.25f),
            new Limb("MARKET LESSONS", 0.50f, 0.45f, 0.50f, 0.25f, 0.50f, 0.055f),
            new Limb("MARKET TOOLS", 0.50f, 0.36f, 0.82f, 0.34f, 0.88f, 0.10f),
            new Limb("WEALTH", 0.50f, 0.64f, 0.26f, 0.65f, 0.12f, 0.51f),
            new Limb("LIFE TOOLS", 0.50f, 0.58f, 0.76f, 0.59f, 0.88f, 0.41f),
            new Limb("HELPER", 0.50f, 0.76f, 0.76f, 0.78f, 0.88f, 0.64f),
            new Limb("SLEEP", 0.50f, 0.85f, 0.14f, 0.88f, 0.11f, 0.67f),
            new Limb("TABLES", 0.50f, 0.57f, 0.24f, 0.56f, 0.12f, 0.385f),
            new Limb("ARCADE", 0.50f, 0.47f, 0.80f, 0.46f, 0.88f, 0.255f),
    };

    private static final Map<String, String> GLYPHS = new HashMap<>();

    static {
        GLYPHS.put("slot", "🎰");
        GLYPHS.put("freespins", "🎁");
        GLYPHS.put("highroller", "💰");
        GLYPHS.put("casino_more", "🃏");
        GLYPHS.put("blocks", "\uD83E\uDDF1");
        GLYPHS.put("m_econ", "\uD83D\uDCC5");
        GLYPHS.put("pegs", "\uD83D\uDD35");
        GLYPHS.put("slot2", "\uD83D\uDC51");
        GLYPHS.put("vpoker", "\uD83C\uDFB4");
        GLYPHS.put("roulette", "\uD83C\uDFAF");
        GLYPHS.put("blackjack", "\uD83C\uDCCF");
        GLYPHS.put("holdem", "\u2660");
        GLYPHS.put("l_water", "\uD83D\uDCA7");
        GLYPHS.put("m_portfolio", "💼");
        GLYPHS.put("m_paper", "📝");
        GLYPHS.put("m_news", "📰");
        GLYPHS.put("m_alerts", "🔔");
        GLYPHS.put("w_rate", "🔁");
        GLYPHS.put("w_interest", "📈");
        GLYPHS.put("l_weather", "⛅");
        GLYPHS.put("l_goals", "🎯");
        GLYPHS.put("l_recipes", "🍲");
        GLYPHS.put("h_lookup", "🔎");
        GLYPHS.put("h_market", "💬");
        GLYPHS.put("l_reminders", "\u23F0");
    }

    Listener listener;
    String selected;

    private final Paint wood = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;

    private final List<Tree.Node> nodes = new ArrayList<>();
    private final List<float[]> spots = new ArrayList<>();

    TreeView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        wood.setStyle(Paint.Style.STROKE);
        wood.setStrokeCap(Paint.Cap.ROUND);
        wood.setColor(0xFF6B4A32);
        ring.setStyle(Paint.Style.STROKE);
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(1.5f * d);
        dashed.setColor(0x66FFFFFF);
        dashed.setPathEffect(new DashPathEffect(new float[]{4 * d, 4 * d}, 0));
        glyph.setTextAlign(Paint.Align.CENTER);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(10 * d);
        label.setLetterSpacing(0.12f);
    }

    static int limbOf(Tree.Node n) {
        if ("blackjack".equals(n.id) || "holdem".equals(n.id) || "vpoker".equals(n.id)
                || "roulette".equals(n.id)) {
            return 7;
        }
        if ("blocks".equals(n.id) || "pegs".equals(n.id)) return 8;
        switch (n.branch) {
            case 0:
                return 0;
            case 1:
                return n.kind == Tree.LESSON ? 1 : 2;
            case 2:
                return 3;
            case 3:
                return 4;
            case 5:
                return 6;
            default:
                return 5;
        }
    }

    private static float bez(float a, float c, float b, float t) {
        float u = 1 - t;
        return u * u * a + 2 * u * t * c + t * t * b;
    }

    /** Works out where every node sits. */
    private void layout() {
        nodes.clear();
        spots.clear();
        float w = getWidth();
        float h = getHeight();
        for (int limb = 0; limb < LIMBS.length; limb++) {
            List<Tree.Node> on = new ArrayList<>();
            for (Tree.Node n : Tree.NODES) {
                if (limbOf(n) == limb) on.add(n);
            }
            Limb l = LIMBS[limb];
            for (int i = 0; i < on.size(); i++) {
                // Spread nodes from a little way out from the trunk to the limb's tip.
                float t = on.size() == 1 ? 0.8f : 0.3f + 0.7f * i / (on.size() - 1);
                nodes.add(on.get(i));
                spots.add(new float[]{bez(l.sx, l.cx, l.ex, t) * w, bez(l.sy, l.cy, l.ey, t) * h});
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        layout();
    }

    void refresh() {
        layout();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (nodes.isEmpty()) layout();
        Context c = getContext();

        // Ground.
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x33000000);
        cv.drawOval(w * 0.22f, h * 0.975f, w * 0.78f, h * 0.998f, fill);

        // Trunk, thicker at the base.
        wood.setStrokeWidth(26 * d);
        cv.drawLine(w * 0.5f, h * 0.985f, w * 0.5f, h * 0.80f, wood);
        wood.setStrokeWidth(20 * d);
        cv.drawLine(w * 0.5f, h * 0.80f, w * 0.5f, h * 0.60f, wood);
        wood.setStrokeWidth(14 * d);
        cv.drawLine(w * 0.5f, h * 0.60f, w * 0.5f, h * 0.36f, wood);

        // Limbs.
        for (int i = 0; i < LIMBS.length; i++) {
            Limb l = LIMBS[i];
            path.reset();
            path.moveTo(l.sx * w, l.sy * h);
            path.quadTo(l.cx * w, l.cy * h, l.ex * w, l.ey * h);
            wood.setStrokeWidth((i == 1 ? 11 : 8) * d);
            cv.drawPath(path, wood);

            // A puff of leaves at the tip, brighter once the limb has something unlocked.
            boolean alive = false;
            for (Tree.Node n : Tree.NODES) {
                if (limbOf(n) == i && Tree.owned(c, n.id)) alive = true;
            }
            leaf.setColor(alive ? 0x664CAF50 : 0x33607D8B);
            float lx = l.ex * w;
            float ly = l.ey * h;
            cv.drawCircle(lx, ly - 6 * d, 30 * d, leaf);
            cv.drawCircle(lx - 22 * d, ly + 8 * d, 22 * d, leaf);
            cv.drawCircle(lx + 22 * d, ly + 8 * d, 22 * d, leaf);

            label.setColor(alive ? Ui.ACCENT : Ui.SUB);
            float labelY = i == 1 ? ly - 30 * d : ly - 34 * d;
            float labelX = Math.max(52 * d, Math.min(w - 52 * d, lx));
            cv.drawText(l.label, labelX, Math.max(12 * d, labelY), label);
        }

        // Nodes.
        float r = 19 * d;
        glyph.setTextSize(17 * d);
        for (int i = 0; i < nodes.size(); i++) {
            Tree.Node n = nodes.get(i);
            float x = spots.get(i)[0];
            float y = spots.get(i)[1];
            boolean owned = Tree.owned(c, n.id);
            boolean can = !owned && n.kind != Tree.SOON && Tree.blocker(c, n) == null;

            fill.setStyle(Paint.Style.FILL);
            if (owned) {
                fill.setColor((Ui.ACCENT & 0x00FFFFFF) | 0x44000000);
                cv.drawCircle(x, y, r + 7 * d, fill);
                fill.setColor(Ui.ACCENT);
            } else {
                fill.setColor(0xFF1A2030);
            }
            cv.drawCircle(x, y, r, fill);

            if (n.kind == Tree.SOON) {
                cv.drawCircle(x, y, r, dashed);
            } else {
                ring.setStrokeWidth((can ? 2.5f : 1.5f) * d);
                ring.setColor(owned ? Ui.ACCENT_HI : (can ? Ui.ACCENT : 0x55FFFFFF));
                cv.drawCircle(x, y, r, ring);
            }
            if (n.id.equals(selected)) {
                ring.setStrokeWidth(2.5f * d);
                ring.setColor(0xFFFFFFFF);
                cv.drawCircle(x, y, r + 5 * d, ring);
            }

            String g = GLYPHS.get(n.id);
            if (g == null) g = n.branch == 5 ? "\uD83C\uDF19" : "\uD83D\uDCD6";
            glyph.setAlpha(owned || can ? 255 : 110);
            float lift = (glyph.ascent() + glyph.descent()) / 2f;
            cv.drawText(g, x, y - lift, glyph);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float best = 34 * d;
        Tree.Node hit = null;
        for (int i = 0; i < nodes.size(); i++) {
            float dx = spots.get(i)[0] - e.getX();
            float dy = spots.get(i)[1] - e.getY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < best) {
                best = dist;
                hit = nodes.get(i);
            }
        }
        if (hit != null) {
            selected = hit.id;
            invalidate();
            if (listener != null) listener.onNode(hit);
        }
        return true;
    }
}
