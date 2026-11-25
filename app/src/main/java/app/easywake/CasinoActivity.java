package app.easywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Walk around the casino and the strip in 3D. Joystick on the left, drag on the right to
 * look, buttons for whatever's nearby. Pick locks, find secrets, take contracts.
 */
public class CasinoActivity extends Activity {
    private CasinoWorld world;
    private GLSurfaceView gl;
    private Overlay overlay;
    private FrameLayout root;
    private LockView lockView;
    private final float[] viewProj = new float[16];
    private int glW = 1, glH = 1;
    private volatile float stickX, stickY;
    private final Random rnd = new Random();

    static final String FLAGS = "casinoFlags", PICKS = "lockpicks", STYLE = "casinoStyle";
    private MapView mapView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        world = new CasinoWorld(System.nanoTime());
        String saved = Prefs.getString(this, FLAGS);
        if (saved != null) {
            for (String f : saved.split(",")) {
                if (!f.isEmpty()) world.flags.add(f);
            }
        }
        world.applyFlags();
        world.hasPistol = world.flags.contains("item:pistol");
        world.worn = CasinoShop.parseWorn(Prefs.getString(this, STYLE));
        world.ownClothes();
        if (Prefs.getInt(this, PICKS, -1) < 0) Prefs.putInt(this, PICKS, 5);

        root = new FrameLayout(this);
        gl = new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(new Renderer());
        root.addView(gl);
        overlay = new Overlay(this);
        root.addView(overlay);
        setContentView(root);
        hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gl.onResume();
        overlay.refreshJackpots = true;
    }

    @Override
    protected void onPause() {
        gl.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (lockView != null) {
            closeLock();
            return;
        }
        if (mapView != null) {
            closeMap();
            return;
        }
        super.onBackPressed();
    }

    // ================================================================== saving

    private void saveFlags() {
        StringBuilder sb = new StringBuilder();
        synchronized (world) {
            for (String f : world.flags) {
                if (sb.length() > 0) sb.append(',');
                sb.append(f);
            }
        }
        Prefs.putString(this, FLAGS, sb.toString());
    }

    private int picks() {
        return Math.max(0, Prefs.getInt(this, PICKS, 5));
    }

    private int chipsFound() {
        int n = 0;
        for (String c : CasinoPlaces.CHIPS) {
            if (world.flags.contains("chip:" + c)) n++;
        }
        return n;
    }

    private void foundChip(String id) {
        synchronized (world) {
            world.flags.add("chip:" + id);
        }
        int n = chipsFound();
        Sfx.play(this, Sfx.BIG_WIN);
        overlay.show(String.format(Locale.US, "Lucky chip: %s  (%d of %d)",
                CasinoPlaces.chipName(id), n, CasinoPlaces.CHIPS.length));
        if (n == CasinoPlaces.CHIPS.length && !world.flags.contains("allchips")) {
            world.flags.add("allchips");
            Wallet.addCoupons(this, 2500);
            info("All twelve lucky chips",
                    "You found every lucky chip in town. The house pays out: 2,500 coupons.");
        }
        saveFlags();
    }

    // ================================================================== actions

    private void doAction(CasinoWorld.Action a) {
        switch (a.type) {
            case CasinoWorld.A_SPOT:
                useSpot((CasinoWorld.Spot) a.ref);
                break;
            case CasinoWorld.A_DOOR:
                useDoor((CasinoWorld.Door) a.ref);
                break;
            default:
                synchronized (world) {
                    world.doWorldAction(a);
                }
                if (a.type == CasinoWorld.A_TAKEDOWN) Sfx.play(this, Sfx.PUNCH);
                break;
        }
    }

    private void useDoor(final CasinoWorld.Door d) {
        if (d.key != null) {
            if (world.flags.contains("key:" + d.key)) {
                synchronized (world) {
                    world.unlockDoor(d);
                }
                Sfx.play(this, Sfx.CLICK);
                saveFlags();
                overlay.show(d.label + ": unlocked.");
            } else {
                overlay.show("Needs a key card. Somebody here keeps one in a desk.");
            }
            return;
        }
        startPick(d.label, d.pins, new Runnable() {
            @Override
            public void run() {
                synchronized (world) {
                    world.unlockDoor(d);
                }
                saveFlags();
                overlay.show(d.label + ": unlocked.");
            }
        });
    }

    private void useSpot(final CasinoWorld.Spot s) {
        switch (s.type) {
            case CasinoWorld.GAME:
                play(s);
                break;
            case CasinoWorld.LOOT:
                if (s.pins > 0) {
                    startPick(s.label, s.pins, new Runnable() {
                        @Override
                        public void run() {
                            takeLoot(s);
                        }
                    });
                } else {
                    takeLoot(s);
                }
                break;
            case CasinoWorld.NOTE:
                synchronized (world) {
                    world.flags.add("note:" + s.id);
                }
                saveFlags();
                info(CasinoPlaces.noteTitle(s.id), s.text);
                break;
            case CasinoWorld.CHIP:
                foundChip(s.id);
                break;
            case CasinoWorld.KEY:
                synchronized (world) {
                    world.flags.add("key:" + s.id);
                }
                saveFlags();
                Sfx.play(this, Sfx.CLICK);
                overlay.show("You took " + s.label + ".");
                break;
            case CasinoWorld.SHOP:
                if (CasinoShop.PAWN.equals(s.id)) shop();
                else store(s.id);
                break;
            default:
                break;
        }
    }

    private void takeLoot(CasinoWorld.Spot s) {
        synchronized (world) {
            world.flags.add("loot:" + s.id);
        }
        saveFlags();
        if (s.reward > 0) {
            Wallet.addCoupons(this, s.reward);
            Sfx.play(this, Sfx.WIN);
            overlay.show(String.format(Locale.US, "Found %,d coupons in %s.", s.reward,
                    s.label));
        }
        if (s.chip != null) foundChip(s.chip);
    }

    /** Opens the game for a spot, or says what it takes to unlock it. */
    private void play(CasinoWorld.Spot s) {
        boolean open;
        if (s.unlock == null) open = true;
        else if ("parlor".equals(s.unlock)) open = ParlorActivity.owned(this);
        else open = Tree.owned(this, s.unlock);
        if (!open) {
            String where = "parlor".equals(s.unlock)
                    ? "Unlock the Slot Parlor in My tools (" + ParlorActivity.COST + " KP)."
                    : "Unlock " + s.label + " on the skill tree first.";
            overlay.show(where);
            return;
        }
        Intent i;
        switch (s.key) {
            case "penny":
                i = new Intent(this, PennyActivity.class).putExtra("machine", s.extra);
                break;
            case "machine":
                i = new Intent(this, MachineActivity.class).putExtra("machine", s.extra);
                break;
            case "vip":
                i = new Intent(this, MachineActivity.class).putExtra("machine", s.extra)
                        .putExtra("vip", true);
                break;
            case "cashier":
                i = new Intent(this, WalletActivity.class);
                break;
            default:
                Class<?> c = Tree.screen(s.key);
                if (c == null) return;
                i = new Intent(this, c);
                break;
        }
        synchronized (world) {
            world.player.x = s.x + (float) Math.sin(s.yaw + Math.PI) * 0.3f;
            world.player.z = s.z - (float) Math.cos(s.yaw + Math.PI) * 0.3f;
        }
        stickX = stickY = 0;
        startActivity(i);
    }

    private void info(String title, String text) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text)
                .setPositiveButton("OK", null).show();
    }

    private boolean owns(String id) {
        return world.flags.contains("own:" + id);
    }

    private boolean pay(int price) {
        if (Wallet.coupons(this) < price) {
            overlay.show(String.format(Locale.US, "Not enough coupons. You need %,d.", price));
            return false;
        }
        Wallet.addCoupons(this, -price);
        Sfx.play(this, Sfx.CHIPS);
        return true;
    }

    /** Lucky Pawn: lockpicks, the pistol, and gear. */
    private void shop() {
        final boolean gun = world.flags.contains("item:pistol");
        final List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        items.add("5 lockpicks — 25  (you have " + picks() + ")");
        acts.add(new Runnable() {
            @Override
            public void run() {
                if (!pay(25)) return;
                Prefs.putInt(CasinoActivity.this, PICKS, picks() + 5);
                overlay.show("Five lockpicks. Don't tell me what for.");
            }
        });
        if (!gun) {
            items.add("A quiet pistol — 750");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    if (!pay(750)) return;
                    synchronized (world) {
                        world.flags.add("item:pistol");
                        world.hasPistol = true;
                    }
                    saveFlags();
                    overlay.show("A quiet pistol. Aim with the camera, then Shoot.");
                }
            });
        }
        addStoreItems(CasinoShop.PAWN, items, acts);
        new AlertDialog.Builder(this).setTitle(String.format(Locale.US,
                        "Lucky Pawn  •  %,d coupons", Wallet.coupons(this)))
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        acts.get(which).run();
                    }
                }).setNegativeButton("Leave", null).show();
    }

    /** Threads or Gold Rush. */
    private void store(String id) {
        List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        addStoreItems(id, items, acts);
        String title = CasinoShop.TAILOR.equals(id) ? "Threads" : "Gold Rush Jewelers";
        new AlertDialog.Builder(this).setTitle(String.format(Locale.US, "%s  •  %,d coupons",
                        title, Wallet.coupons(this)))
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        acts.get(which).run();
                    }
                }).setNegativeButton("Leave", null).show();
    }

    private void addStoreItems(String store, List<String> items, List<Runnable> acts) {
        for (final CasinoShop.Item it : CasinoShop.ALL) {
            if (!it.store.equals(store)) continue;
            boolean own = owns(it.id);
            boolean wearing = it.id.equals(world.worn[it.slot]);
            String state = !own ? String.format(Locale.US, "%,d", it.price)
                    : (it.slot == CasinoShop.GEAR ? "owned" : (wearing ? "wearing" : "owned: wear"));
            items.add(it.name + " — " + state);
            acts.add(new Runnable() {
                @Override
                public void run() {
                    buyOrWear(it);
                }
            });
        }
    }

    private void buyOrWear(final CasinoShop.Item it) {
        if (owns(it.id)) {
            if (it.slot != CasinoShop.GEAR) wear(it, !it.id.equals(world.worn[it.slot]));
            return;
        }
        new AlertDialog.Builder(this).setTitle(it.name)
                .setMessage(String.format(Locale.US, "Buy for %,d coupons? You have %,d.",
                        it.price, Wallet.coupons(this)))
                .setPositiveButton("Buy", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (!pay(it.price)) return;
                        synchronized (world) {
                            world.flags.add("own:" + it.id);
                        }
                        saveFlags();
                        if (it.slot != CasinoShop.GEAR) wear(it, true);
                        else overlay.show("Bought: " + it.name + ".");
                    }
                }).setNegativeButton("No", null).show();
    }

    private void wear(CasinoShop.Item it, boolean on) {
        synchronized (world) {
            world.worn[it.slot] = on ? it.id : null;
            // A jacket and a suit share the slot; nothing else conflicts.
            world.restyle();
        }
        Prefs.putString(this, STYLE, CasinoShop.saveWorn(world.worn));
        overlay.show(on ? "Wearing: " + it.name + "." : "Took off: " + it.name + ".");
    }

    /** Everything you're carrying and own. */
    private void bag() {
        final List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        items.add(String.format(Locale.US, "Coupons: %,d", Wallet.coupons(this)));
        acts.add(null);
        items.add("Lockpicks: " + picks() + (owns("propicks") ? "  (pro set: never snap)" : ""));
        acts.add(null);
        if (world.hasPistol) {
            items.add("Quiet pistol");
            acts.add(null);
        }
        if (world.flags.contains("key:hlkey")) {
            items.add("High Limit key card");
            acts.add(null);
        }
        items.add("Wearing: " + CasinoWorld.OUTFIT_NAMES[world.player.outfit]);
        acts.add(world.player.outfit == CasinoWorld.CIVILIAN ? null : new Runnable() {
            @Override
            public void run() {
                synchronized (world) {
                    world.ownClothes();
                }
                overlay.show("Back in your own clothes.");
            }
        });
        items.add(String.format(Locale.US, "Lucky chips: %d of %d", chipsFound(),
                CasinoPlaces.CHIPS.length));
        acts.add(new Runnable() {
            @Override
            public void run() {
                chipList();
            }
        });
        items.add("Wardrobe and bling…");
        acts.add(new Runnable() {
            @Override
            public void run() {
                wardrobe();
            }
        });
        new AlertDialog.Builder(this).setTitle("Bag")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Runnable r = acts.get(which);
                        if (r != null) r.run();
                        else bag();
                    }
                }).setNegativeButton("Close", null).show();
    }

    private void wardrobe() {
        final List<CasinoShop.Item> mine = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (CasinoShop.Item it : CasinoShop.ALL) {
            if (!owns(it.id) || it.slot == CasinoShop.GEAR) continue;
            mine.add(it);
            boolean on = it.id.equals(world.worn[it.slot]);
            names.add((on ? "✓ " : "    ") + it.name + "  (" + CasinoShop.SLOT_NAMES[it.slot]
                    + ")");
        }
        if (mine.isEmpty()) {
            info("Wardrobe", "Nothing yet. Threads (clothes) and Gold Rush (bling) are across "
                    + "the street from the casino.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Wardrobe: tap to wear or take off")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        CasinoShop.Item it = mine.get(which);
                        wear(it, !it.id.equals(world.worn[it.slot]));
                        wardrobe();
                    }
                }).setNegativeButton("Done", null).show();
    }

    /** The contracts screen: the active job, or the list to pick from. */
    private void jobs() {
        final CasinoMissions.Contract c = world.contract;
        if (c == null) {
            contracts();
            return;
        }
        String where = "";
        int meters = 0;
        synchronized (world) {
            if (world.targetP != null) {
                where = CasinoPlaces.placeName(world.targetP.x, world.targetP.z);
                meters = Math.round(CasinoWorld.dist(world.targetP, world.player));
            }
        }
        new AlertDialog.Builder(this).setTitle(c.title + ": " + c.name)
                .setMessage(String.format(Locale.US, "Last seen: %s, %d m away.\n\n%s\n\n%s"
                                + "\n\nFee: %,d coupons. Clean job: %,d.", where, meters,
                        c.brief, c.tips, CasinoMissions.payout(c, timesDone(c), false, false, 0),
                        CasinoMissions.payout(c, timesDone(c), true, false, 0)))
                .setPositiveButton("OK", null)
                .setNegativeButton("Abort job", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        synchronized (world) {
                            world.abortContract();
                        }
                        overlay.show("Contract dropped.");
                    }
                }).show();
    }

    // ================================================================== the phone

    private void phone() {
        List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (world.contract != null) {
            items.add("Abort contract: " + world.contract.name);
            acts.add(new Runnable() {
                @Override
                public void run() {
                    synchronized (world) {
                        world.abortContract();
                    }
                    overlay.show("Contract dropped.");
                }
            });
        } else {
            items.add("Contracts");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    contracts();
                }
            });
        }
        items.add(String.format(Locale.US, "Lucky chips (%d of %d)", chipsFound(),
                CasinoPlaces.CHIPS.length));
        acts.add(new Runnable() {
            @Override
            public void run() {
                chipList();
            }
        });
        items.add("Notes you've found");
        acts.add(new Runnable() {
            @Override
            public void run() {
                notesList();
            }
        });
        if (world.player.outfit != CasinoWorld.CIVILIAN) {
            items.add("Change back into your own clothes");
            acts.add(new Runnable() {
                @Override
                public void run() {
                    synchronized (world) {
                        world.ownClothes();
                    }
                }
            });
        }
        items.add("How it works");
        acts.add(new Runnable() {
            @Override
            public void run() {
                info("How it works", HOW);
            }
        });
        new AlertDialog.Builder(this).setTitle(String.format(Locale.US,
                        "Burner phone  •  %d lockpicks", picks()))
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        acts.get(which).run();
                    }
                }).show();
    }

    private static final String HOW = "Contracts pay Fun Coupons. Pick one on the phone and "
            + "the target shows up with a red marker. Learn their walk.\n\n"
            + "TAKEDOWN: sneak up behind someone who isn't on alert. Targets go down for "
            + "good; anyone else is just out cold.\n\n"
            + "BODIES: drag them out of sight and hide them in a closet, dumpster, freezer or "
            + "trunk. If someone finds one, security comes.\n\n"
            + "DISGUISES: take a uniform off someone you knocked out. Staff and cooks can "
            + "walk staff areas; security can go almost anywhere. The eye at the top fills "
            + "when people notice you somewhere you shouldn't be.\n\n"
            + "PAY: clean jobs (never spotted, no bodies found, nobody else killed) pay 50% "
            + "extra. Accidents pay 25% extra. Every bystander killed cuts the fee. Repeat "
            + "jobs pay less.\n\n"
            + "LOCKS: press on a pin and slide up. The stiff one is binding: lift it till it "
            + "clicks, then let go. Push too far and it jams; let off the pressure and try "
            + "again. Picks can snap. The pawn shop sells more.";

    private int timesDone(CasinoMissions.Contract c) {
        return Prefs.getInt(this, "hitDone_" + c.id, 0);
    }

    private void contracts() {
        final CasinoMissions.Contract[] all = CasinoMissions.ALL;
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            int fee = CasinoMissions.payout(all[i], timesDone(all[i]), false, false, 0);
            String best = Prefs.getString(this, "hitBest_" + all[i].id);
            items[i] = String.format(Locale.US, "%s%s — %s\n%,d coupons%s",
                    timesDone(all[i]) > 0 ? "✓ " : "", all[i].title, all[i].name, fee,
                    timesDone(all[i]) > 0 ? "  •  done " + timesDone(all[i]) + "x, best: "
                            + (best == null ? "?" : best) : "");
        }
        new AlertDialog.Builder(this).setTitle("Contracts")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        brief(all[which]);
                    }
                }).show();
    }

    private void brief(final CasinoMissions.Contract c) {
        int fee = CasinoMissions.payout(c, timesDone(c), false, false, 0);
        new AlertDialog.Builder(this).setTitle(c.title + ": " + c.name)
                .setMessage(c.brief + "\n\n" + c.tips + String.format(Locale.US,
                        "\n\nFee: %,d coupons. Clean job: %,d.", fee,
                        CasinoMissions.payout(c, timesDone(c), true, false, 0)))
                .setPositiveButton("Take the job", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        synchronized (world) {
                            world.startContract(c);
                        }
                        overlay.show("Target: " + c.name + ". Find the red marker.");
                    }
                }).setNegativeButton("Not now", null).show();
    }

    private void chipList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CasinoPlaces.CHIPS.length; i++) {
            boolean got = world.flags.contains("chip:" + CasinoPlaces.CHIPS[i]);
            sb.append(got ? "✓  " + CasinoPlaces.CHIP_NAMES[i]
                    : "?  " + CasinoPlaces.CHIP_HINTS[i]).append('\n');
        }
        sb.append("\nFind all twelve for a 2,500 coupon payout.");
        info(String.format(Locale.US, "Lucky chips: %d of %d", chipsFound(),
                CasinoPlaces.CHIPS.length), sb.toString());
    }

    private void notesList() {
        final List<String> ids = new ArrayList<>();
        for (String n : CasinoPlaces.NOTES) {
            if (world.flags.contains("note:" + n)) ids.add(n);
        }
        if (ids.isEmpty()) {
            info("Notes", "Nothing yet. Read things lying around: desks, walls, tables.");
            return;
        }
        String[] titles = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) titles[i] = CasinoPlaces.noteTitle(ids.get(i));
        new AlertDialog.Builder(this).setTitle("Notes")
                .setItems(titles, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        info(CasinoPlaces.noteTitle(ids.get(which)),
                                CasinoPlaces.noteText(ids.get(which)));
                    }
                }).show();
    }

    /** Pays out a finished contract and shows how it went. */
    private void contractFinished() {
        CasinoMissions.Contract c;
        boolean silent, accident, spotted;
        int bystanders;
        synchronized (world) {
            c = world.contract;
            if (c == null || !world.contractDone) return;
            silent = world.silentAssassin();
            accident = CasinoMissions.accident(world.killedHow);
            spotted = world.spotted;
            bystanders = world.civKills;
            world.contract = null;
            world.contractDone = false;
        }
        int done = timesDone(c);
        int pay = CasinoMissions.payout(c, done, silent, accident, bystanders);
        Prefs.putInt(this, "hitDone_" + c.id, done + 1);
        Wallet.addCoupons(this, pay);
        Sfx.play(this, Sfx.JACKPOT);
        String rating = CasinoMissions.rating(silent, spotted, bystanders);
        String[] order = {"Bloodbath", "Messy", "Loud", "Professional", "Silent Assassin"};
        String best = Prefs.getString(this, "hitBest_" + c.id);
        int bi = -1, ri = 0;
        for (int k = 0; k < order.length; k++) {
            if (order[k].equals(best)) bi = k;
            if (order[k].equals(rating)) ri = k;
        }
        if (ri > bi) Prefs.putString(this, "hitBest_" + c.id, rating);
        StringBuilder sb = new StringBuilder();
        sb.append(c.name).append(" is done.\n\nRating: ").append(rating).append('\n');
        if (accident) sb.append("Made it look like an accident: +25%\n");
        if (silent) sb.append("Never seen, nothing found: +50%\n");
        if (bystanders > 0) sb.append(bystanders).append(" bystander")
                .append(bystanders == 1 ? "" : "s").append(" killed: -")
                .append(20 * bystanders).append("%\n");
        sb.append(String.format(Locale.US, "\nPaid: %,d coupons", pay));
        info("Contract complete", sb.toString());
    }

    // ================================================================== the map

    /** Map pixels per metre. */
    static final int MAP_PX = 4;
    private Bitmap mapBmp;

    /** Draws the town from above once: ground, rooms, walls. */
    private Bitmap mapBitmap() {
        if (mapBmp != null) return mapBmp;
        int w = (int) ((CasinoWorld.WX1 - CasinoWorld.WX0) * MAP_PX);
        int h = (int) ((CasinoWorld.WZ1 - CasinoWorld.WZ0) * MAP_PX);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        c.drawColor(0xFF0B0D12);
        float[][] ground = {{-48, 14, 48, 18, 0xFF46433F}, {-48, 26, 48, 30, 0xFF46433F},
                {-48, 18, 48, 26, 0xFF26262C}, {-48, -14, -18, 14, 0xFF1E2028},
                {18, -28, 26, 14, 0xFF2A2624}, {34, 30, 48, 42, 0xFF24221E}};
        for (float[] g : ground) {
            p.setColor((int) g[4]);
            c.drawRect(mx(g[0]), mz(g[1]), mx(g[2]), mz(g[3]), p);
        }
        p.setColor(0xFFB89A30);
        for (float x = -47; x < 48; x += 3.2f) c.drawRect(mx(x), mz(21.9f), mx(x + 1.6f), mz(22.1f), p);
        for (int i = 0; i < world.regions.size(); i++) {
            float[] r = world.regions.get(i);
            p.setColor(i == 0 ? 0xFF4A1E26 : 0xFF3A2C24);
            c.drawRect(mx(r[0]), mz(r[1]), mx(r[2]), mz(r[3]), p);
        }
        for (float[] wl : world.walls) {
            if (wl.length > 5) continue;
            p.setColor(wl[4] >= 1.7f ? 0xFFC8B48A : 0xFF6A5E50);
            c.drawRect(mx(wl[0]), mz(wl[1]), Math.max(mx(wl[2]), mx(wl[0]) + 1),
                    Math.max(mz(wl[3]), mz(wl[1]) + 1), p);
        }
        mapBmp = b;
        return b;
    }

    private static float mx(float x) {
        return (x - CasinoWorld.WX0) * MAP_PX;
    }

    private static float mz(float z) {
        return (z - CasinoWorld.WZ0) * MAP_PX;
    }

    private void openMap() {
        if (mapView != null) return;
        mapView = new MapView(this);
        root.addView(mapView);
    }

    private void closeMap() {
        if (mapView == null) return;
        root.removeView(mapView);
        mapView = null;
    }

    /** The whole town with place names, you, your target, security and locked doors. */
    private final class MapView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        MapView(Context c) {
            super(c);
            d = getResources().getDisplayMetrics().density;
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(0xF2050608);
            Bitmap map = mapBitmap();
            float top = 44 * d;
            float sc = Math.min((w - 24 * d) / map.getWidth(), (h - top - 12 * d) / map.getHeight());
            float ox = (w - map.getWidth() * sc) / 2, oy = top;
            c.save();
            c.translate(ox, oy);
            c.scale(sc, sc);
            c.drawBitmap(map, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            c.restore();
            float k = sc * MAP_PX;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(16 * d);
            p.setColor(0xFFE6B869);
            c.drawText("Map  •  tap anywhere to close", w / 2f, 28 * d, p);
            p.setTextSize(10 * d);
            for (Object[] pl : CasinoPlaces.PLACES) {
                float x = ((Float) pl[1] + (Float) pl[3]) / 2, z = ((Float) pl[2] + (Float) pl[4]) / 2;
                if (pl[0].equals("The Strip")) z = 22;
                p.setColor(0xCCFFFFFF);
                c.drawText((String) pl[0], ox + (x - CasinoWorld.WX0) * k,
                        oy + (z - CasinoWorld.WZ0) * k, p);
            }
            synchronized (world) {
                for (CasinoWorld.Door dr : world.doors) {
                    if (dr.unlocked || world.canOpen(dr)) continue;
                    p.setColor(0xFFFF9A30);
                    float x = ox + (dr.x - CasinoWorld.WX0) * k, y = oy + (dr.z - CasinoWorld.WZ0) * k;
                    c.drawRect(x - 3 * d, y - 3 * d, x + 3 * d, y + 3 * d, p);
                    if (dr.key == null) c.drawText(dr.pins + "", x, y - 5 * d, p);
                }
                for (CasinoWorld.Spot sp : world.spots) {
                    if (sp.type != CasinoWorld.SHOP) continue;
                    p.setColor(0xFFFFD24A);
                    c.drawCircle(ox + (sp.x - CasinoWorld.WX0) * k, oy + (sp.z - CasinoWorld.WZ0) * k,
                            4 * d, p);
                }
                for (CasinoWorld.Person g : world.guards) {
                    if (!g.active || g.ko || g.dead) continue;
                    p.setColor(world.alarm ? 0xFFFF3030 : 0xFF5AA8FF);
                    c.drawCircle(ox + (g.x - CasinoWorld.WX0) * k, oy + (g.z - CasinoWorld.WZ0) * k,
                            4 * d, p);
                }
                CasinoWorld.Person t = world.targetP;
                if (t != null && t.active && !t.dead) {
                    p.setColor(0xFFFF2A2A);
                    float x = ox + (t.x - CasinoWorld.WX0) * k, y = oy + (t.z - CasinoWorld.WZ0) * k;
                    c.drawCircle(x, y, 6 * d, p);
                    p.setTextSize(12 * d);
                    c.drawText(t.name, x, y - 9 * d, p);
                    p.setTextSize(10 * d);
                }
                CasinoWorld.Person me = world.player;
                float x = ox + (me.x - CasinoWorld.WX0) * k, y = oy + (me.z - CasinoWorld.WZ0) * k;
                Path a = new Path();
                float ax = (float) Math.sin(me.yaw), az = (float) -Math.cos(me.yaw);
                a.moveTo(x + ax * 9 * d, y + az * 9 * d);
                a.lineTo(x - az * 6 * d - ax * 6 * d, y + ax * 6 * d - az * 6 * d);
                a.lineTo(x + az * 6 * d - ax * 6 * d, y - ax * 6 * d - az * 6 * d);
                a.close();
                p.setColor(0xFFFFFFFF);
                c.drawPath(a, p);
            }
            // Legend.
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(11 * d);
            float lx = 14 * d, ly = h - 14 * d;
            Object[][] legend = {{0xFFFFFFFF, "You"}, {0xFFFF2A2A, "Target"},
                    {0xFF5AA8FF, "Security"}, {0xFFFFD24A, "Store"}, {0xFFFF9A30, "Locked (pins)"}};
            for (Object[] l : legend) {
                p.setColor((Integer) l[0]);
                c.drawCircle(lx + 4 * d, ly - 4 * d, 4 * d, p);
                c.drawText((String) l[1], lx + 12 * d, ly, p);
                lx += p.measureText((String) l[1]) + 30 * d;
            }
            postInvalidateDelayed(250);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) closeMap();
            return true;
        }
    }

    // ================================================================== lock picking

    private void startPick(String label, int pins, final Runnable done) {
        if (picks() <= 0) {
            overlay.show("No lockpicks left. The pawn shop across the street sells them.");
            return;
        }
        if (lockView != null) return;
        stickX = stickY = 0;
        synchronized (world) {
            world.picking = true;
        }
        lockView = new LockView(this, label, new LockPick(pins, rnd), done);
        root.addView(lockView);
    }

    private void closeLock() {
        if (lockView == null) return;
        root.removeView(lockView);
        lockView = null;
        synchronized (world) {
            world.picking = false;
        }
    }

    private final class LockView extends View {
        private final LockPick lock;
        private final String label;
        private final Runnable done;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private int active = -1;
        private float downY;
        private final RectF resetBtn = new RectF(), quitBtn = new RectF();
        private String msg = "Press on a pin and slide up. Find the stiff one.";
        private boolean finished;
        private float left, colW, range;

        LockView(Context c, String label, LockPick lock, Runnable done) {
            super(c);
            this.label = label;
            this.lock = lock;
            this.done = done;
            d = getResources().getDisplayMetrics().density;
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            p.setColor(0xE0080808);
            c.drawRect(0, 0, w, h, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(18 * d);
            p.setColor(0xFFE6B869);
            c.drawText(label + "  •  " + lock.pins + " pins", w / 2f, 34 * d, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(13 * d);
            p.setColor(0xFFCCCCCC);
            c.drawText(msg, w / 2f, 58 * d, p);
            c.drawText(picks() + " lockpicks", w / 2f, h - 16 * d, p);
            float sus;
            boolean watched, alarm;
            String who = null;
            synchronized (world) {
                sus = world.suspicion;
                watched = world.watched;
                alarm = world.alarm;
                if (world.watcher != null) {
                    who = world.watcher.kind == CasinoWorld.GUARD ? "a guard"
                            : (world.watcher.name != null ? world.watcher.name : "someone");
                }
            }
            if (alarm && !finished) {
                finished = true;
                post(new Runnable() {
                    @Override
                    public void run() {
                        closeLock();
                        overlay.show("You got caught picking the lock!");
                    }
                });
            }
            // How noticed you are, right in the lock screen.
            float ex = w - 70 * d, ey = 34 * d;
            p.setColor(sus > 0.5f ? 0xFFFF9A30 : 0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            c.drawOval(new RectF(ex - 26 * d, ey - 10 * d, ex + 26 * d, ey + 10 * d), p);
            p.setStyle(Paint.Style.FILL);
            c.save();
            c.clipRect(ex - 26 * d, ey + 10 * d - 20 * d * Math.min(1, sus), ex + 26 * d,
                    ey + 10 * d);
            c.drawOval(new RectF(ex - 26 * d, ey - 10 * d, ex + 26 * d, ey + 10 * d), p);
            c.restore();
            if (watched) {
                p.setColor(0xFFFF5A4A);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(14 * d);
                c.drawText("Careful: " + who + " can see you. Stop and wait.", w / 2f,
                        78 * d, p);
                p.setTypeface(Typeface.DEFAULT);
            }
            postInvalidateDelayed(120);

            float lockW = Math.min(w * 0.7f, lock.pins * 74 * d);
            left = (w - lockW) / 2;
            colW = lockW / lock.pins;
            float top = 80 * d;
            float bottom = h - 70 * d;
            float shear = top + (bottom - top) * 0.52f;
            range = (bottom - top) * 0.36f;
            p.setColor(0xFF6A5A30);
            c.drawRoundRect(new RectF(left - 14 * d, top, left + lockW + 14 * d, bottom),
                    14 * d, 14 * d, p);
            float turn = lock.open() ? 1 : lock.bound / (float) lock.pins;
            p.setColor(lock.open() ? 0xFF4CAF50 : 0xFFB89A50);
            c.save();
            c.rotate(turn * 3, w / 2f, (shear + bottom) / 2);
            c.drawRect(left - 6 * d, shear, left + lockW + 6 * d, bottom - 10 * d, p);
            c.restore();
            p.setColor(0x66000000);
            c.drawRect(left - 14 * d, shear - 1 * d, left + lockW + 14 * d, shear + 1 * d, p);
            float keyBase = bottom - 22 * d;
            for (int i = 0; i < lock.pins; i++) {
                float cx = left + colW * (i + 0.5f);
                float pw = colW * 0.36f;
                p.setColor(0xFF1A1610);
                c.drawRect(cx - pw / 2 - 3 * d, top + 10 * d, cx + pw / 2 + 3 * d,
                        keyBase + 4 * d, p);
                float lift = lock.lift[i] * range * 1.25f;
                // Key pin, sized so that at the right height its top meets the shear line.
                float keyLen = (keyBase - shear) - lock.target[i] * range * 1.25f;
                float kb = keyBase - lift;
                float kt = kb - keyLen;
                p.setColor(lock.set[i] ? 0xFF6FD08A : 0xFFD8B04A);
                c.drawRoundRect(new RectF(cx - pw / 2, kt, cx + pw / 2, kb), 6 * d, 6 * d, p);
                float driverLen = range * 0.9f;
                p.setColor(lock.overset && i == lock.binding() ? 0xFFE05050 : 0xFFC8CCD4);
                c.drawRect(cx - pw / 2, kt - driverLen, cx + pw / 2, kt - 1 * d, p);
                p.setColor(0xFF8A8A90);
                p.setStrokeWidth(2 * d);
                float sTop = top + 12 * d, sBot = kt - driverLen;
                Path path = new Path();
                path.moveTo(cx, sTop);
                int coils = 6;
                for (int k = 1; k <= coils; k++) {
                    float y = sTop + (sBot - sTop) * k / coils;
                    path.lineTo(cx + (k % 2 == 0 ? -pw / 2 : pw / 2), y);
                }
                p.setStyle(Paint.Style.STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                if (i == active) {
                    p.setColor(0xFFE6E6EA);
                    p.setStrokeWidth(5 * d);
                    c.drawLine(left - 20 * d, bottom - 4 * d, cx, kb + 4 * d, p);
                }
            }
            p.setTextSize(14 * d);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            resetBtn.set(16 * d, h - 60 * d, 196 * d, h - 20 * d);
            quitBtn.set(w - 136 * d, h - 60 * d, w - 16 * d, h - 20 * d);
            p.setColor(lock.overset ? 0xFFE6B869 : 0x55FFFFFF);
            c.drawRoundRect(resetBtn, 20 * d, 20 * d, p);
            p.setColor(lock.overset ? 0xFF1A120C : 0xFFFFFFFF);
            c.drawText("Let off pressure", resetBtn.centerX(), resetBtn.centerY() + 5 * d, p);
            p.setColor(0x55FFFFFF);
            c.drawRoundRect(quitBtn, 20 * d, 20 * d, p);
            p.setColor(0xFFFFFFFF);
            c.drawText("Give up", quitBtn.centerX(), quitBtn.centerY() + 5 * d, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (finished) return true;
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (resetBtn.contains(x, y)) {
                        lock.reset();
                        msg = "Pressure off. All pins dropped. Start again.";
                        invalidate();
                        return true;
                    }
                    if (quitBtn.contains(x, y)) {
                        closeLock();
                        return true;
                    }
                    int i = (int) Math.floor((x - left) / colW);
                    if (i < 0 || i >= lock.pins || lock.set[i]) {
                        active = -1;
                        return true;
                    }
                    active = i;
                    downY = y;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (active < 0) return true;
                    float h = (downY - y) / (range * 1.25f);
                    int ev = lock.push(active, h);
                    if (ev == LockPick.CLICK) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        Sfx.play(CasinoActivity.this, Sfx.CLICK);
                    } else if (ev == LockPick.JAM) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        if (!owns("propicks") && rnd.nextFloat() < 0.35f) {
                            Prefs.putInt(CasinoActivity.this, PICKS, picks() - 1);
                            Sfx.play(CasinoActivity.this, Sfx.LOSE);
                            msg = "Jammed, and your pick snapped. Let off the pressure.";
                            if (picks() <= 0) {
                                closeLock();
                                overlay.show("Out of lockpicks. The pawn shop sells them.");
                                return true;
                            }
                        } else {
                            msg = "Overset: that pin went too far. Let off the pressure.";
                        }
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (active >= 0) {
                        boolean set = lock.release(active);
                        if (set) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            Sfx.play(CasinoActivity.this, Sfx.CLICK);
                            msg = lock.open() ? "Open." : "Set. " + (lock.pins - lock.bound)
                                    + " to go.";
                        }
                        active = -1;
                        invalidate();
                        if (lock.open()) {
                            finished = true;
                            Sfx.play(CasinoActivity.this, Sfx.WIN);
                            postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    closeLock();
                                    done.run();
                                }
                            }, 500);
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    // ================================================================== drawing in 3D

    private static final String VERTEX =
            "uniform mat4 uVP; uniform mat4 uModel; uniform vec3 uTint; uniform float uGlow;\n"
                    + "attribute vec3 aPos; attribute vec3 aNormal; attribute vec3 aColor;\n"
                    + "attribute vec2 aUv; attribute float aEmit; attribute float aTex;\n"
                    + "varying vec3 vWorld; varying vec3 vNormal; varying vec3 vColor;\n"
                    + "varying vec2 vUv; varying float vEmit; varying float vTex;\n"
                    + "void main() {\n"
                    + "  vec4 w = uModel * vec4(aPos, 1.0);\n"
                    + "  vWorld = w.xyz;\n"
                    + "  vNormal = mat3(uModel) * aNormal;\n"
                    + "  vColor = aColor * uTint; vUv = aUv; vEmit = max(aEmit, uGlow);\n"
                    + "  vTex = aTex;\n"
                    + "  gl_Position = uVP * w;\n"
                    + "}\n";

    /** Must match Preview.shade on the desktop. */
    private static final String FRAGMENT =
            "precision mediump float;\n"
                    + "uniform sampler2D uAtlas; uniform sampler2D uCarpet;\n"
                    + "uniform sampler2D uLight; uniform vec4 uLightBox;\n"
                    + "uniform vec3 uEye; uniform float uTime;\n"
                    + "varying vec3 vWorld; varying vec3 vNormal; varying vec3 vColor;\n"
                    + "varying vec2 vUv; varying float vEmit; varying float vTex;\n"
                    + "void main() {\n"
                    + "  vec3 base = vColor;\n"
                    + "  if (vTex > 1.5) base *= texture2D(uCarpet, vUv).rgb;\n"
                    + "  else if (vTex > 0.5) base *= texture2D(uAtlas, vUv).rgb;\n"
                    + "  vec3 n = normalize(vNormal);\n"
                    + "  vec4 L = texture2D(uLight, vec2((vWorld.x - uLightBox.x) * uLightBox.z,\n"
                    + "      (vWorld.z - uLightBox.y) * uLightBox.w));\n"
                    + "  float inside = L.a;\n"
                    + "  vec3 pool = L.rgb * 2.0;\n"
                    + "  float h = n.y * 0.5 + 0.5;\n"
                    + "  vec3 amb = mix(mix(vec3(0.05, 0.055, 0.08), vec3(0.12, 0.13, 0.2), h),\n"
                    + "      mix(vec3(0.16, 0.12, 0.11), vec3(0.36, 0.30, 0.26), h), inside);\n"
                    + "  float dif = max(0.0, dot(n, normalize(vec3(0.25, 0.9, 0.35))));\n"
                    + "  vec3 difCol = mix(vec3(0.16, 0.18, 0.28), vec3(0.62, 0.54, 0.44), inside);\n"
                    + "  vec3 lit = amb + dif * difCol + pool * (0.35 + 0.65 * h);\n"
                    + "  float flick = 1.0 + 0.04 * sin(uTime * 6.0 + vWorld.x * 3.0) * vEmit;\n"
                    + "  vec3 col = base * lit * (1.0 - vEmit) + base * vEmit * 1.1 * flick;\n"
                    + "  float d = distance(vWorld, uEye);\n"
                    + "  float fog = clamp((d - mix(14.0, 10.0, inside)) / mix(45.0, 30.0, inside),\n"
                    + "      0.0, 1.0) * 0.8;\n"
                    + "  vec3 fogCol = mix(vec3(0.02, 0.025, 0.045), vec3(0.035, 0.028, 0.026), inside);\n"
                    + "  gl_FragColor = vec4(mix(col, fogCol, fog), 1.0);\n"
                    + "}\n";

    private static final String SHADOW_VERTEX =
            "uniform mat4 uVP; uniform vec3 uAt;\n"
                    + "attribute vec3 aPos;\n"
                    + "varying float vK;\n"
                    + "void main() {\n"
                    + "  vK = aPos.y;\n"
                    + "  gl_Position = uVP * vec4(uAt.x + aPos.x * uAt.z, 0.012, uAt.y + aPos.z * uAt.z, 1.0);\n"
                    + "}\n";
    private static final String SHADOW_FRAGMENT =
            "precision mediump float; varying float vK;\n"
                    + "void main() { gl_FragColor = vec4(0.0, 0.0, 0.0, 0.55 * pow(vK, 0.8)); }\n";

    private final class Renderer implements GLSurfaceView.Renderer {
        private int prog, shadowProg;
        private int staticVbo, cubeVbo, diskVbo, staticCount, cubeCount, diskCount;
        private int atlasTex, carpetTex, lightTex;
        private final float[] view = new float[16], proj = new float[16], vp = new float[16],
                ident = new float[16], eye = new float[3];
        private long last;
        private float jackpotTimer;
        private Bitmap board;

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            prog = program(VERTEX, FRAGMENT);
            shadowProg = program(SHADOW_VERTEX, SHADOW_FRAGMENT);
            int[] light;
            synchronized (world) {
                CasinoWorld.Mesh m = world.mesh;
                staticVbo = upload(m.data, m.floats);
                staticCount = m.vertices();
                light = world.bakeLight();
            }
            CasinoWorld.Mesh cube = new CasinoWorld.Mesh();
            cube.box(0, 0, (float) Math.PI, 0.5f, 0.5f, -0.5f, 0.5f, 0xFFFFFFFF, 0);
            cube.quad(new float[]{-0.5f, -0.5f, -0.5f}, new float[]{0.5f, -0.5f, -0.5f},
                    new float[]{0.5f, -0.5f, 0.5f}, new float[]{-0.5f, -0.5f, 0.5f},
                    0xFFFFFFFF, 0);
            cubeVbo = upload(cube.data, cube.floats);
            cubeCount = cube.vertices();
            int segs = 20;
            float[] disk = new float[segs * 9];
            for (int i = 0; i < segs; i++) {
                double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
                float[] t = {0, 1, 0, (float) Math.cos(a0), 0, (float) Math.sin(a0),
                        (float) Math.cos(a1), 0, (float) Math.sin(a1)};
                System.arraycopy(t, 0, disk, i * 9, 9);
            }
            diskVbo = upload(disk, disk.length);
            diskCount = segs * 3;

            Bitmap atlas = Bitmap.createBitmap(CasinoArt.ATLAS, CasinoArt.ATLAS_H,
                    Bitmap.Config.ARGB_8888);
            CasinoArt.paintAtlas(new CanvasPainter(new Canvas(atlas)), jackpots());
            atlasTex = texture(atlas, false);
            atlas.recycle();
            Bitmap carpet = Bitmap.createBitmap(CasinoArt.CARPET, CasinoArt.CARPET,
                    Bitmap.Config.ARGB_8888);
            CasinoArt.paintCarpet(new CanvasPainter(new Canvas(carpet)));
            carpetTex = texture(carpet, true);
            carpet.recycle();
            lightTex = lightTexture(light);
            CasinoWorld.Mat.identity(ident);
            last = SystemClock.uptimeMillis();
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int w, int h) {
            GLES20.glViewport(0, 0, w, h);
            glW = w;
            glH = h;
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            long now = SystemClock.uptimeMillis();
            float dt = (now - last) / 1000f;
            last = now;
            final int sounds;
            final boolean finished;
            synchronized (world) {
                world.update(dt, stickX, stickY);
                sounds = world.sounds;
                finished = world.contractDone;
                world.camera(glW / (float) Math.max(1, glH), view, proj, eye);
                CasinoWorld.Mat.mul(vp, proj, view);
                System.arraycopy(vp, 0, viewProj, 0, 16);
            }
            if (sounds != 0 || finished) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if ((sounds & 4) != 0) Sfx.play(CasinoActivity.this, Sfx.SHOT);
                        else if ((sounds & 1) != 0) Sfx.play(CasinoActivity.this, Sfx.PUNCH);
                        else if ((sounds & 2) != 0) Sfx.play(CasinoActivity.this, Sfx.WHOOSH);
                        if (finished) contractFinished();
                    }
                });
            }
            jackpotTimer -= dt;
            if (jackpotTimer <= 0 || overlay.refreshJackpots) {
                jackpotTimer = 2;
                overlay.refreshJackpots = false;
                refreshBoard();
            }

            GLES20.glClearColor(0.02f, 0.025f, 0.045f, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDepthMask(true);

            GLES20.glUseProgram(prog);
            GLES20.glUniformMatrix4fv(loc(prog, "uVP"), 1, false, vp, 0);
            GLES20.glUniform3f(loc(prog, "uEye"), eye[0], eye[1], eye[2]);
            GLES20.glUniform1f(loc(prog, "uTime"), (now % 100000) / 1000f);
            GLES20.glUniform4f(loc(prog, "uLightBox"), CasinoWorld.WX0, CasinoWorld.WZ0,
                    1f / (CasinoWorld.WX1 - CasinoWorld.WX0),
                    1f / (CasinoWorld.WZ1 - CasinoWorld.WZ0));
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex);
            GLES20.glUniform1i(loc(prog, "uAtlas"), 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, carpetTex);
            GLES20.glUniform1i(loc(prog, "uCarpet"), 1);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lightTex);
            GLES20.glUniform1i(loc(prog, "uLight"), 2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            final int uModel = loc(prog, "uModel"), uTint = loc(prog, "uTint"),
                    uGlow = loc(prog, "uGlow");
            GLES20.glUniformMatrix4fv(uModel, 1, false, ident, 0);
            GLES20.glUniform3f(uTint, 1, 1, 1);
            GLES20.glUniform1f(uGlow, 0);
            bindMain(staticVbo);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, staticCount);

            bindMain(cubeVbo);
            final float[] shadows = new float[3 * 96];
            final int[] shadowCount = {0};
            synchronized (world) {
                world.drawPeople(new CasinoWorld.PartSink() {
                    @Override
                    public void part(float[] model, int color, float glow) {
                        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
                        GLES20.glUniform3f(uTint, ((color >> 16) & 255) / 255f,
                                ((color >> 8) & 255) / 255f, (color & 255) / 255f);
                        GLES20.glUniform1f(uGlow, glow);
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, cubeCount);
                    }

                    @Override
                    public void shadow(float x, float z, float r) {
                        int i = shadowCount[0];
                        if (i >= 96) return;
                        shadows[i * 3] = x;
                        shadows[i * 3 + 1] = z;
                        shadows[i * 3 + 2] = r;
                        shadowCount[0] = i + 1;
                    }
                });
            }
            GLES20.glUniform1f(uGlow, 0);

            GLES20.glUseProgram(shadowProg);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            GLES20.glUniformMatrix4fv(loc(shadowProg, "uVP"), 1, false, vp, 0);
            int aPos = GLES20.glGetAttribLocation(shadowProg, "aPos");
            for (int k = 0; k < 6; k++) GLES20.glDisableVertexAttribArray(k);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, diskVbo);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0);
            int uAt = loc(shadowProg, "uAt");
            for (int i = 0; i < shadowCount[0]; i++) {
                GLES20.glUniform3f(uAt, shadows[i * 3], shadows[i * 3 + 1], shadows[i * 3 + 2]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, diskCount);
            }
            GLES20.glDisableVertexAttribArray(aPos);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
            overlay.postInvalidate();
        }

        private void refreshBoard() {
            CasinoArt.Region r = CasinoArt.JACKPOT_BOARD;
            if (board == null) board = Bitmap.createBitmap(r.w, r.h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(board);
            c.translate(-r.x, -r.y);
            CasinoArt.paintJackpots(new CanvasPainter(c), jackpots());
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, r.x, r.y, board);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }

        private void bindMain(int vbo) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            int stride = CasinoWorld.Mesh.STRIDE * 4;
            attr("aPos", 3, stride, 0);
            attr("aNormal", 3, stride, 3);
            attr("aColor", 3, stride, 6);
            attr("aUv", 2, stride, 9);
            attr("aEmit", 1, stride, 11);
            attr("aTex", 1, stride, 12);
        }

        private void attr(String name, int size, int stride, int offsetFloats) {
            int a = GLES20.glGetAttribLocation(prog, name);
            if (a < 0) return;
            GLES20.glEnableVertexAttribArray(a);
            GLES20.glVertexAttribPointer(a, size, GLES20.GL_FLOAT, false, stride,
                    offsetFloats * 4);
        }
    }

    private double[] jackpots() {
        JackpotBank bank = JackpotBank.load(Prefs.getString(this, PennyActivity.BANK),
                new Random());
        return bank.meter.clone();
    }

    private static int loc(int prog, String name) {
        return GLES20.glGetUniformLocation(prog, name);
    }

    private static int program(String vs, String fs) {
        int v = shader(GLES20.GL_VERTEX_SHADER, vs);
        int f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("Casino shader link: " + GLES20.glGetProgramInfoLog(p));
        }
        return p;
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("Casino shader: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }

    private static int upload(float[] data, int floats) {
        FloatBuffer fb = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(data, 0, floats).position(0);
        int[] id = new int[1];
        GLES20.glGenBuffers(1, id, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floats * 4, fb, GLES20.GL_STATIC_DRAW);
        return id[0];
    }

    private static int texture(Bitmap b, boolean repeat) {
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        int wrap = repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        return id[0];
    }

    /** The baked light map: not a power of two, so no mipmaps and clamped edges. */
    private static int lightTexture(int[] argb) {
        int w = CasinoWorld.LM_W, h = CasinoWorld.LM_H;
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int c : argb) {
            bb.put((byte) ((c >> 16) & 255));
            bb.put((byte) ((c >> 8) & 255));
            bb.put((byte) (c & 255));
            bb.put((byte) ((c >>> 24) & 255));
        }
        bb.position(0);
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, bb);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        return id[0];
    }

    /** CasinoArt's painter on an Android Canvas. */
    static final class CanvasPainter implements CasinoArt.Painter {
        private final Canvas c;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        CanvasPainter(Canvas c) {
            this.c = c;
        }

        @Override
        public void rect(float x, float y, float w, float h, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRect(x, y, x + w, y + h, p);
        }

        @Override
        public void roundRect(float x, float y, float w, float h, float rad, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            r.set(x, y, x + w, y + h);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override
        public void oval(float cx, float cy, float rx, float ry, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            r.set(cx - rx, cy - ry, cx + rx, cy + ry);
            c.drawOval(r, p);
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, float width, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override
        public void vgradient(float x, float y, float w, float h, int top, int bottom) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x, y, x, y + h, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(x, y, x + w, y + h, p);
            p.setShader(null);
        }

        @Override
        public void text(String s, float x, float y, float size, int color, boolean bold,
                         int align) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextAlign(align == 0 ? Paint.Align.LEFT
                    : (align == 1 ? Paint.Align.CENTER : Paint.Align.RIGHT));
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p);
        }

        @Override
        public float measure(String s, float size, boolean bold) {
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return p.measureText(s);
        }
    }

    // ================================================================== controls on top

    private final class Overlay extends View {
        volatile boolean refreshJackpots;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] scratch = new float[3];
        private final float[] vpCopy = new float[16];
        private int stickId = -1, lookId = -1;
        private float stickCx, stickCy, stickPx, stickPy, lookX, lookY;
        private final RectF punchBtn = new RectF(), shootBtn = new RectF(),
                backBtn = new RectF(), phoneBtn = new RectF(), jobsBtn = new RectF(),
                bagBtn = new RectF(), miniBtn = new RectF();
        private final List<RectF> actionBtns = new ArrayList<>();
        private int coupons = -1, shownGain;
        private long couponsAt, gainUntil;
        private String targetLine;
        private List<CasinoWorld.Action> actions = new ArrayList<>();
        private String message;
        private long messageUntil;
        private final float d;

        Overlay(Context c) {
            super(c);
            d = getResources().getDisplayMetrics().density;
        }

        void show(String s) {
            message = s;
            messageUntil = SystemClock.uptimeMillis() + 3800;
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float escort, suspicion;
            boolean alarm, watched, trespass, pistol;
            String toast, target = null;
            synchronized (world) {
                System.arraycopy(viewProj, 0, vpCopy, 0, 16);
                actions = world.actions();
                escort = world.escort;
                toast = world.toast;
                world.toast = null;
                suspicion = world.suspicion;
                alarm = world.alarm;
                watched = world.watched;
                trespass = world.trespassing();
                pistol = world.hasPistol;
                if (world.contract != null) target = world.contract.name;
                targetLine = null;
                if (world.targetP != null && world.targetP.active && !world.targetP.dead) {
                    targetLine = String.format(Locale.US, "Target: %s  •  %s  •  %d m",
                            world.contract.name, CasinoPlaces.placeName(world.targetP.x,
                                    world.targetP.z),
                            Math.round(CasinoWorld.dist(world.targetP, world.player)));
                    tracker(c, w, h);
                }
                drawMinimap(c);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(13 * d);
                paint.setTextAlign(Paint.Align.CENTER);
                for (CasinoWorld.Person p : world.people) {
                    if (!p.active) continue;
                    float y = CasinoWorld.headY(p);
                    if (!CasinoWorld.Mat.project(vpCopy, p.x, y, p.z, w, h, scratch)) continue;
                    if (scratch[2] > 18) continue;
                    if (p.bubble != null && p.bubbleT > 0) {
                        bubble(c, p.bubble, scratch[0], scratch[1],
                                Math.min(1, p.bubbleT * 3));
                    } else if (p.name != null && scratch[2] < 7 && !p.dead && !p.ko) {
                        paint.setTextSize(11 * d);
                        paint.setColor(p.target ? 0xDDFF6A6A : 0xB3FFFFFF);
                        c.drawText(p.name, scratch[0], scratch[1], paint);
                        paint.setTextSize(13 * d);
                    }
                }
            }
            if (toast != null) show(toast);

            backBtn.set(12 * d, 12 * d, 56 * d, 56 * d);
            paint.setColor(0x88000000);
            c.drawRoundRect(backBtn, 22 * d, 22 * d, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(20 * d);
            c.drawText("‹", backBtn.centerX(), backBtn.centerY() + 7 * d, paint);
            phoneBtn.set(w - 84 * d, 12 * d, w - 12 * d, 52 * d);
            bagBtn.set(w - 156 * d, 12 * d, w - 92 * d, 52 * d);
            jobsBtn.set(w - 228 * d, 12 * d, w - 164 * d, 52 * d);
            topButton(c, phoneBtn, "Phone", 0xFFE6B869);
            topButton(c, bagBtn, "Bag", 0xFFE6B869);
            topButton(c, jobsBtn, "Jobs", target != null ? 0xFFFF6A6A : 0xFFE6B869);

            // Your coupons, always on screen.
            long now = SystemClock.uptimeMillis();
            if (now - couponsAt > 400) {
                int cNow = Wallet.coupons(getContext());
                if (coupons >= 0 && cNow > coupons) {
                    shownGain = cNow - coupons;
                    gainUntil = now + 2500;
                }
                coupons = cNow;
                couponsAt = now;
            }
            paint.setTextSize(15 * d);
            String money = String.format(Locale.US, "%,d", Math.max(0, coupons));
            float mw = paint.measureText(money) + 44 * d;
            RectF pill = new RectF(64 * d, 14 * d, 64 * d + mw, 50 * d);
            paint.setColor(0xAA000000);
            c.drawRoundRect(pill, 18 * d, 18 * d, paint);
            paint.setColor(0xFFE6B869);
            c.drawCircle(pill.left + 18 * d, pill.centerY(), 9 * d, paint);
            paint.setColor(0xFF1A120C);
            paint.setTextSize(12 * d);
            c.drawText("C", pill.left + 18 * d, pill.centerY() + 4 * d, paint);
            paint.setTextSize(15 * d);
            paint.setColor(0xFFFFE8A8);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(money, pill.left + 32 * d, pill.centerY() + 5 * d, paint);
            if (now < gainUntil) {
                paint.setColor(((int) (Math.min(1, (gainUntil - now) / 800f) * 255) << 24)
                        | 0x6CFF8A);
                c.drawText(String.format(Locale.US, "+%,d", shownGain), pill.right + 8 * d,
                        pill.centerY() + 5 * d, paint);
            }
            paint.setTextAlign(Paint.Align.CENTER);

            if (targetLine != null) {
                paint.setTextSize(13 * d);
                paint.setColor(0xFFFF6A6A);
                c.drawText(targetLine, w / 2f, 22 * d, paint);
            }
            if (suspicion > 0.01f || alarm || trespass) {
                float cx = w / 2f, cy = (target != null ? 52 : 34) * d;
                float rw = 30 * d, rh = 12 * d;
                int col = alarm ? 0xFFFF3030 : (suspicion > 0.5f ? 0xFFFF9A30 : 0xFFFFFFFF);
                paint.setColor(0x88000000);
                c.drawOval(new RectF(cx - rw - 4 * d, cy - rh - 4 * d, cx + rw + 4 * d,
                        cy + rh + 4 * d), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setColor(col);
                c.drawOval(new RectF(cx - rw, cy - rh, cx + rw, cy + rh), paint);
                paint.setStyle(Paint.Style.FILL);
                float fill = Math.min(1, suspicion);
                c.save();
                c.clipRect(cx - rw, cy + rh - 2 * rh * fill, cx + rw, cy + rh);
                c.drawOval(new RectF(cx - rw, cy - rh, cx + rw, cy + rh), paint);
                c.restore();
                paint.setColor(0xFF000000);
                c.drawCircle(cx, cy, 5 * d, paint);
                paint.setTextSize(11 * d);
                paint.setColor(col);
                String label = alarm ? "SECURITY ALERTED" : (watched ? "BEING WATCHED"
                        : (trespass ? "TRESPASSING" : ""));
                if (!label.isEmpty()) c.drawText(label, cx, cy + rh + 16 * d, paint);
            }

            if (stickId >= 0) {
                paint.setColor(0x33FFFFFF);
                c.drawCircle(stickCx, stickCy, 56 * d, paint);
                paint.setColor(0x88FFFFFF);
                c.drawCircle(stickPx, stickPy, 24 * d, paint);
            } else {
                paint.setColor(0x22FFFFFF);
                c.drawCircle(100 * d, h - 100 * d, 56 * d, paint);
                paint.setColor(0x44FFFFFF);
                c.drawCircle(100 * d, h - 100 * d, 24 * d, paint);
            }

            float pr = 36 * d;
            punchBtn.set(w - 24 * d - 2 * pr, h - 24 * d - 2 * pr, w - 24 * d, h - 24 * d);
            paint.setColor(0x99B71C1C);
            c.drawOval(punchBtn, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(13 * d);
            c.drawText("PUNCH", punchBtn.centerX(), punchBtn.centerY() + 5 * d, paint);
            if (pistol) {
                shootBtn.set(punchBtn.left - 16 * d - 2 * pr * 0.8f, h - 24 * d - 2 * pr * 0.8f,
                        punchBtn.left - 16 * d, h - 24 * d);
                paint.setColor(0x99202024);
                c.drawOval(shootBtn, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawText("SHOOT", shootBtn.centerX(), shootBtn.centerY() + 5 * d, paint);
                paint.setColor(0x88FFFFFF);
                c.drawCircle(w / 2f, h * 0.45f, 3 * d, paint);
            } else {
                shootBtn.setEmpty();
            }

            actionBtns.clear();
            float by = h - 84 * d;
            paint.setTextSize(15 * d);
            for (CasinoWorld.Action a : actions) {
                float tw = paint.measureText(a.label);
                RectF r = new RectF(w / 2f - tw / 2 - 22 * d, by, w / 2f + tw / 2 + 22 * d,
                        by + 46 * d);
                boolean kill = a.type == CasinoWorld.A_TAKEDOWN;
                paint.setColor(kill ? 0xE6B71C1C : 0xE6E6B869);
                c.drawRoundRect(r, 23 * d, 23 * d, paint);
                paint.setColor(kill ? 0xFFFFFFFF : 0xFF1A120C);
                c.drawText(a.label, r.centerX(), r.centerY() + 5 * d, paint);
                actionBtns.add(r);
                by -= 56 * d;
            }

            if (message != null && SystemClock.uptimeMillis() < messageUntil) {
                paint.setTextSize(15 * d);
                float tw = paint.measureText(message);
                float top = (target != null ? 90 : 70) * d;
                RectF m = new RectF(w / 2f - tw / 2 - 16 * d, top, w / 2f + tw / 2 + 16 * d,
                        top + 40 * d);
                paint.setColor(0xDD000000);
                c.drawRoundRect(m, 20 * d, 20 * d, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawText(message, m.centerX(), m.centerY() + 5 * d, paint);
            }

            if (escort > 0) {
                float k = escort > 0.6f ? (1.4f - escort) / 0.8f : escort / 0.6f;
                paint.setColor(((int) (Math.max(0, Math.min(1, k)) * 255) << 24));
                c.drawRect(0, 0, w, h, paint);
            }
        }

        private void topButton(Canvas c, RectF r, String label, int color) {
            paint.setColor(0x88000000);
            c.drawRoundRect(r, 20 * d, 20 * d, paint);
            paint.setColor(color);
            paint.setTextSize(14 * d);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(label, r.centerX(), r.centerY() + 5 * d, paint);
        }

        /** Turns a world offset into minimap pixels, with your view direction up. */
        private void toMini(float dx, float dz, float yaw, float k, float[] out) {
            float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
            out[0] = (dx * cs + dz * sn) * k;
            out[1] = (-dx * sn + dz * cs) * k;
        }

        /** A round map in the corner that turns with the camera. Call with the world locked. */
        private void drawMinimap(Canvas c) {
            Bitmap map = mapBitmap();
            float r = 60 * d, cx = 12 * d + r, cy = 64 * d + r;
            miniBtn.set(cx - r, cy - r, cx + r, cy + r);
            float k = r / 24f;
            CasinoWorld.Person me = world.player;
            float yaw = world.camYaw;
            paint.setColor(0xCC07080C);
            c.drawCircle(cx, cy, r + 3 * d, paint);
            c.save();
            Path clip = new Path();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            c.clipPath(clip);
            c.translate(cx, cy);
            c.rotate((float) -Math.toDegrees(yaw));
            c.scale(k / MAP_PX, k / MAP_PX);
            c.translate(-(me.x - CasinoWorld.WX0) * MAP_PX, -(me.z - CasinoWorld.WZ0) * MAP_PX);
            Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(map, 0, 0, bp);
            c.restore();
            float[] o = new float[2];
            // Locked doors.
            for (CasinoWorld.Door dr : world.doors) {
                if (dr.unlocked || world.canOpen(dr)) continue;
                toMini(dr.x - me.x, dr.z - me.z, yaw, k, o);
                if (o[0] * o[0] + o[1] * o[1] > (r - 4 * d) * (r - 4 * d)) continue;
                paint.setColor(0xFFFF9A30);
                c.drawRect(cx + o[0] - 2.5f * d, cy + o[1] - 2.5f * d, cx + o[0] + 2.5f * d,
                        cy + o[1] + 2.5f * d, paint);
            }
            // Stores.
            for (CasinoWorld.Spot sp : world.spots) {
                if (sp.type != CasinoWorld.SHOP) continue;
                toMini(sp.x - me.x, sp.z - me.z, yaw, k, o);
                if (o[0] * o[0] + o[1] * o[1] > (r - 4 * d) * (r - 4 * d)) continue;
                paint.setColor(0xFFFFD24A);
                c.drawCircle(cx + o[0], cy + o[1], 3 * d, paint);
            }
            // Security.
            for (CasinoWorld.Person g : world.guards) {
                if (!g.active || g.ko || g.dead) continue;
                toMini(g.x - me.x, g.z - me.z, yaw, k, o);
                if (o[0] * o[0] + o[1] * o[1] > (r - 4 * d) * (r - 4 * d)) continue;
                paint.setColor(world.alarm ? 0xFFFF3030 : 0xFF5AA8FF);
                c.drawCircle(cx + o[0], cy + o[1], 3.2f * d, paint);
            }
            // The target: pinned to the edge when it's off the map.
            CasinoWorld.Person t = world.targetP;
            if (t != null && t.active && !t.dead) {
                toMini(t.x - me.x, t.z - me.z, yaw, k, o);
                float l = (float) Math.hypot(o[0], o[1]);
                float max = r - 6 * d;
                if (l > max) {
                    o[0] *= max / l;
                    o[1] *= max / l;
                }
                float pulse = 4 * d + (float) Math.abs(Math.sin(world.time * 4)) * 2 * d;
                paint.setColor(0xFFFF2A2A);
                c.drawCircle(cx + o[0], cy + o[1], pulse, paint);
            }
            // You.
            float rel = me.yaw - yaw;
            Path arrow = new Path();
            float ax = (float) Math.sin(rel), az = (float) -Math.cos(rel);
            arrow.moveTo(cx + ax * 7 * d, cy + az * 7 * d);
            arrow.lineTo(cx - az * 5 * d - ax * 5 * d, cy + ax * 5 * d - az * 5 * d);
            arrow.lineTo(cx + az * 5 * d - ax * 5 * d, cy - ax * 5 * d - az * 5 * d);
            arrow.close();
            paint.setColor(0xFFFFFFFF);
            c.drawPath(arrow, paint);
            paint.setTextSize(10 * d);
            paint.setColor(0xAAFFFFFF);
            c.drawText(CasinoPlaces.placeName(me.x, me.z), cx, cy + r + 15 * d, paint);
        }

        /** A red marker on the target, or an arrow at the screen edge pointing to them. */
        private void tracker(Canvas c, int w, int h) {
            CasinoWorld.Person t = world.targetP;
            float[] o = new float[3];
            int meters = Math.round(CasinoWorld.dist(t, world.player));
            boolean on = CasinoWorld.Mat.project(vpCopy, t.x, CasinoWorld.headY(t) + 0.35f, t.z,
                    w, h, o) && o[0] > 30 * d && o[0] < w - 30 * d && o[1] > 70 * d
                    && o[1] < h - 30 * d;
            paint.setTextSize(12 * d);
            if (on) {
                Path dia = new Path();
                float sz = 8 * d;
                dia.moveTo(o[0], o[1] - sz);
                dia.lineTo(o[0] + sz * 0.7f, o[1]);
                dia.lineTo(o[0], o[1] + sz);
                dia.lineTo(o[0] - sz * 0.7f, o[1]);
                dia.close();
                paint.setColor(0xFFFF2A2A);
                c.drawPath(dia, paint);
                paint.setColor(0xFFFFFFFF);
                c.drawText(meters + " m", o[0], o[1] - sz - 4 * d, paint);
                return;
            }
            float[] m = new float[2];
            toMini(t.x - world.player.x, t.z - world.player.z, world.camYaw, 1, m);
            float ang = (float) Math.atan2(m[0], -m[1]);
            float rx = w * 0.42f, ry = h * 0.36f;
            float px = w / 2f + (float) Math.sin(ang) * rx;
            float py = h * 0.52f - (float) Math.cos(ang) * ry;
            float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
            Path a = new Path();
            float L = 14 * d;
            a.moveTo(px + sx * L, py + sy * L);
            a.lineTo(px - sy * L * 0.6f, py + sx * L * 0.6f);
            a.lineTo(px + sy * L * 0.6f, py - sx * L * 0.6f);
            a.close();
            paint.setColor(0xEEFF2A2A);
            c.drawPath(a, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawText(meters + " m", px - sx * 12 * d, py - sy * 12 * d + 4 * d, paint);
        }

        private void bubble(Canvas c, String s, float x, float y, float alpha) {
            float tw = paint.measureText(s);
            RectF r = new RectF(x - tw / 2 - 10 * d, y - 34 * d, x + tw / 2 + 10 * d, y - 8 * d);
            paint.setColor(((int) (alpha * 0xEE) << 24) | 0x111111);
            c.drawRoundRect(r, 12 * d, 12 * d, paint);
            paint.setColor(((int) (alpha * 255) << 24) | 0xFFFFFF);
            c.drawText(s, x, r.centerY() + 5 * d, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            int idx = e.getActionIndex();
            int id = e.getPointerId(idx);
            float x = e.getX(idx), y = e.getY(idx);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (backBtn.contains(x, y)) {
                        finish();
                        return true;
                    }
                    if (phoneBtn.contains(x, y) || bagBtn.contains(x, y)
                            || jobsBtn.contains(x, y) || miniBtn.contains(x, y)) {
                        stickX = stickY = 0;
                        stickId = -1;
                        if (phoneBtn.contains(x, y)) phone();
                        else if (bagBtn.contains(x, y)) bag();
                        else if (jobsBtn.contains(x, y)) jobs();
                        else openMap();
                        return true;
                    }
                    if (punchBtn.contains(x, y)) {
                        synchronized (world) {
                            world.punch();
                        }
                        return true;
                    }
                    if (!shootBtn.isEmpty() && shootBtn.contains(x, y)) {
                        synchronized (world) {
                            world.shoot();
                        }
                        return true;
                    }
                    for (int i = 0; i < actionBtns.size() && i < actions.size(); i++) {
                        if (actionBtns.get(i).contains(x, y)) {
                            CasinoWorld.Action a = actions.get(i);
                            if (a.type == CasinoWorld.A_SPOT || a.type == CasinoWorld.A_DOOR) {
                                stickX = stickY = 0;
                                stickId = -1;
                            }
                            doAction(a);
                            return true;
                        }
                    }
                    if (x < getWidth() * 0.45f && stickId < 0) {
                        stickId = id;
                        stickCx = stickPx = x;
                        stickCy = stickPy = y;
                    } else if (lookId < 0) {
                        lookId = id;
                        lookX = x;
                        lookY = y;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        int pid = e.getPointerId(i);
                        float px = e.getX(i), py = e.getY(i);
                        if (pid == stickId) {
                            float dx = px - stickCx, dy = py - stickCy;
                            float max = 56 * d;
                            float l = (float) Math.hypot(dx, dy);
                            if (l > max) {
                                dx *= max / l;
                                dy *= max / l;
                            }
                            stickPx = stickCx + dx;
                            stickPy = stickCy + dy;
                            stickX = dx / max;
                            stickY = dy / max;
                        } else if (pid == lookId) {
                            float dx = px - lookX, dy = py - lookY;
                            lookX = px;
                            lookY = py;
                            synchronized (world) {
                                world.orbit(dx * 0.006f, dy * 0.004f);
                            }
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stickId = -1;
                    lookId = -1;
                    stickX = stickY = 0;
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (id == stickId) {
                        stickId = -1;
                        stickX = stickY = 0;
                    }
                    if (id == lookId) lookId = -1;
                    return true;
                default:
                    return true;
            }
        }
    }
}
