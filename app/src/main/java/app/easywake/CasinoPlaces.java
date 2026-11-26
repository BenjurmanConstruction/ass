package app.easywake;

import static app.easywake.CasinoWorld.EAST;
import static app.easywake.CasinoWorld.NORTH;
import static app.easywake.CasinoWorld.SOUTH;
import static app.easywake.CasinoWorld.T;
import static app.easywake.CasinoWorld.WEST;

/**
 * Everything beyond the casino floor: the staff corridor and back rooms, the night strip
 * outside, the parking lot, the alley, the pawn shop, the diner, the motel and the bar.
 * Also where every secret is: lucky chips, notes, stashes and hiding places.
 */
final class CasinoPlaces {
    private CasinoPlaces() {}

    static final float NAN = Float.NaN;

    // ---------------------------------------------------------------- people who roam outside

    static final float[][] OUTSIDE_AREAS = {
            {-95, 14.8f, 95, 17.4f}, // The Strip, casino side
            {-95, 26.6f, 95, 29.3f}, // The Strip, far side
            {-46, -12, -20, 12}, // parking lot
            {-95, -31.6f, 95, -29.2f}, // Back Street
            {-95, 53.4f, 95, 55.6f}, // Harbor Road
            {-49.8f, -60, -48.2f, 50}, // West Avenue
            {48.2f, -60, 49.8f, 50}, // East Avenue
    };

    /** Security's rounds: x, z, seconds to wait, facing, sit. */
    static final float[][] FLOOR_PATROL = {
            {-4, 11, 2, NAN, 0}, {-14, 6, 3, NAN, 0}, {-14, -4, 2, NAN, 0}, {0, -5, 3, NAN, 0},
            {12, -6, 2, NAN, 0}, {14, 6, 3, NAN, 0}, {4, 11, 2, NAN, 0}};
    static final float[][] BACK_PATROL = {
            {-14, -15.5f, 3, NAN, 0}, {6, -15.5f, 0, NAN, 0}, {6, -20, 5, SOUTH, 0},
            {6, -15.5f, 0, NAN, 0}, {16, -15.5f, 3, NAN, 0}};

    static final String[] DICE_TALK = {"Come on, seven!", "Snake eyes again? Rigged.",
            "Double or nothing.", "Blow on 'em for luck.", "You owe me twenty.",
            "Keep walking, tourist."};

    // ---------------------------------------------------------------- collectibles and notes

    static final String[] CHIPS = {"moon", "royal", "snakeeyes", "doubledown", "vaultgold",
            "whale", "neonace", "pawnedheart", "midnight", "starlite", "lastcall", "lucky13"};
    static final String[] CHIP_NAMES = {"Blue Moon", "Royal Flush", "Snake Eyes", "Double Down",
            "Vault Gold", "The Whale", "Neon Ace", "Pawned Heart", "Midnight", "Starlite",
            "Last Call", "Lucky 13"};
    static final String[] CHIP_HINTS = {"Somewhere on the casino floor, low down.",
            "A quiet corner of the casino floor.", "Where they shoot dice in the alley.",
            "In the manager's office.", "Inside the vault.", "In the High Limit Room.",
            "In the security office.", "Locked in a display case.", "Under a diner booth.",
            "In motel room 7.", "Inside The Last Call.", "At the shooting range."};

    static final String[] NOTES = {"memo", "seclog", "vault", "hl", "ledger", "napkin", "diary",
            "graffiti", "tab"};
    static final String[] NOTE_TITLES = {"Memo from the GM", "Security log",
            "Note in the vault", "High Limit plaque", "Pawn ledger", "Napkin", "Diary, room 7",
            "Alley graffiti", "Bar tab"};
    static final String[] NOTE_TEXT = {
            "TO ALL FLOOR STAFF:\n\nGrandma Mae is NOT to be cut off. She tips better than the "
                    + "whales.\n\nWhoever keeps testing the penny machines with a magnet: I "
                    + "know it's you, Chad.\n\nThe High Limit key card stays in MY desk. Do "
                    + "not take it again.\n\n— R. Vance, General Manager",
            "02:14  Guest punched another guest at blackjack. Escorted out.\n02:15  Same "
                    + "guest. Back inside. Punched a dealer.\n02:16  Same guest. I'm going on "
                    + "break.\n03:40  Back door alarm keeps chirping. Five-pin lock, who "
                    + "picks a five-pin lock?\n04:02  The GM walked to the cage again with an "
                    + "empty bag. Not my business.",
            "If you're reading this, you got through six pins. Respect.\n\nTake what's on the "
                    + "cart. Leave the deposit boxes, they're full of bad poetry and "
                    + "somebody's teeth.\n\n— the last guy",
            "WELCOME TO THE HIGH LIMIT ROOM\nMinimum bet: your dignity.\n\nComplimentary "
                    + "espresso is for members only. Vinnie, that means you pay.",
            "BOUGHT: one 'lucky' rabbit foot. Not lucky. Seller lost everything an hour later.\n"
                    + "SOLD: lockpicks to a guy who said he was 'locked out of his own vault.' "
                    + "Sure, buddy.\nSOLD: a quiet little pistol to a woman from the Starlite. "
                    + "Paid cash. Didn't blink.",
            "Rusty owes me $40 and a slice of pie. If he says he paid, he's lying.\n— Dee",
            "Night 6. The GM drinks at the Last Call till close, then walks to the cage with an "
                    + "empty bag and comes back with a full one.\n\nNight 7. Vinnie wants "
                    + "him gone. Vance wants Vinnie gone. Everybody wants somebody gone.\n\n"
                    + "Night 8. The vault's six pins. I counted the clicks. Not tonight.",
            "THE HOUSE ALWAYS WINS\n(except when it doesn't)\n\nsmall, underneath: "
                    + "the back door's 5 pins. bring a spare pick.",
            "TAB — R. Vance\n14 old fashioneds\n1 ginger ale (?)\n\nSays the vault combo is "
                    + "'the year he stopped losing.' Lol. There's no combo, it's a pin lock, "
                    + "Richard."};

    static String noteText(String id) {
        for (int i = 0; i < NOTES.length; i++) {
            if (NOTES[i].equals(id)) return NOTE_TEXT[i];
        }
        return "";
    }

    static String noteTitle(String id) {
        for (int i = 0; i < NOTES.length; i++) {
            if (NOTES[i].equals(id)) return NOTE_TITLES[i];
        }
        return id;
    }

    static String chipName(String id) {
        for (int i = 0; i < CHIPS.length; i++) {
            if (CHIPS[i].equals(id)) return CHIP_NAMES[i];
        }
        return id;
    }

    // ---------------------------------------------------------------- place names

    /** Named areas, most specific first: x0, z0, x1, z1. */
    static final Object[][] PLACES = {
            {"High Limit Room", -18f, -24f, -6f, -17f}, {"Vault", -6f, -24f, 2f, -17f},
            {"Security office", 2f, -24f, 10f, -17f}, {"Manager's office", 10f, -24f, 18f, -17f},
            {"Staff corridor", -18f, -17f, 18f, -14f}, {"Room 7", 21f, 30f, 27f, 38f},
            {"Lucky Pawn", -40f, 30f, -26f, 42f}, {"Gold Rush Jewelers", -48f, 30f, -40f, 42f},
            {"Threads", -6f, 30f, 0f, 42f}, {"Midnight Diner", -22f, 30f, -6f, 42f},
            {"Starlite Motel", 0f, 28f, 30f, 42f}, {"Bullseye Range", 34f, 30f, 48f, 42f},
            {"The Last Call", 26f, 4f, 48f, 14f}, {"Back alley", 18f, -28f, 26f, 14f},
            {"Parking lot", -48f, -14f, -18f, 14f}, {"Casino floor", -18f, -14f, 18f, 14f},
            {"Police HQ", 58f, -32f, 88f, 18f}, {"Spray Shop", -76f, 29f, -64f, 41f},
            {"The Strip", -48f, 14f, 48f, 30f}};

    static String placeName(float x, float z) {
        for (Object[] p : PLACES) {
            if (x >= (Float) p[1] && x <= (Float) p[3] && z >= (Float) p[2]
                    && z <= (Float) p[4]) {
                return (String) p[0];
            }
        }
        String road = CasinoCity.roadName(x, z);
        return road != null ? road : "Downtown";
    }

    // ---------------------------------------------------------------- colors

    static final int BRICK = 0xFF4A2A22, DRAB = 0xFF4A443C, CONCRETE = 0xFF55524C,
            ASPHALT = 0xFF17171B, LOT = 0xFF1E1E22, FENCE = 0xFF3A3A40, WOOD = 0xFF5A3A22,
            METAL = 0xFF4A4E55, NIGHT_STONE = 0xFF2F2A30;

    // ---------------------------------------------------------------- building

    static void build(CasinoWorld w) {
        backRooms(w);
        frontAndStreet(w);
        parkingLot(w);
        alley(w);
        pawnShop(w);
        diner(w);
        motel(w);
        bar(w);
        emptyLot(w);
        // The two loose chips on the casino floor.
        chip(w, "moon", -13.4f, 4.6f, 0.25f);
        chip(w, "royal", 16.9f, -9.5f, 0.25f);
    }

    private static void backRooms(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float ceil = 3.4f, h = 3.7f;
        m.floor(-18, -24, 18, -14, 0, 0xFF2A2622, 0, 1);
        m.ceiling(-18, -24, 18, -14, ceil, 0xFF1A1816, 0);
        m.floor(-18 - T / 2, -28, 18 + T / 2, -14, h, 0xFF15110F, 0, 1);
        w.regions.add(new float[]{-18, -24, 18, -14, ceil});
        w.wallX(-24, -18 - T / 2, 18 + T / 2, h, CasinoWorld.STONE);
        w.wallZ(-18, -24, -14, h, CasinoWorld.STONE);
        w.wallZ(18, -28, -14, h, CasinoWorld.STONE, -16.1f, -14.9f);
        // Corridor wall with the four doorways, and the walls between rooms.
        w.wallX(-17, -18, 18, ceil, DRAB, -9.6f, -8.4f, -2.6f, -1.4f, 5.4f, 6.6f, 13.4f, 14.6f);
        w.wallZ(-6, -24, -17, ceil, DRAB);
        w.wallZ(2, -24, -17, ceil, DRAB);
        w.wallZ(10, -24, -17, ceil, DRAB);

        CasinoWorld.Door hl = new CasinoWorld.Door("highlimit", "High Limit", -9, -17, true,
                1.2f, 0, 0xFF2A2A30);
        hl.key = "hlkey";
        w.doors.add(hl);
        hl.access = 2;
        w.doors.add(new CasinoWorld.Door("vault", "Vault", -2, -17, true, 1.2f, 6, 0xFF6A6E75));
        CasinoWorld.Door office = new CasinoWorld.Door("office", "Manager's office", 14, -17,
                true, 1.2f, 4, WOOD);
        office.access = 2;
        w.doors.add(office);
        CasinoWorld.Door back = new CasinoWorld.Door("backdoor", "Back door", 18, -15.5f, false,
                1.2f, 5, 0xFF3A3E44);
        back.access = 1;
        w.doors.add(back);
        // Door signs, facing the corridor.
        float sz = -17 + T / 2 + 0.01f;
        m.panel(-9, sz, SOUTH, 1.8f, 2.68f, 3.0f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.HIGH));
        m.panel(-2, sz, SOUTH, 1.4f, 2.68f, 3.0f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.VAULT));
        m.panel(6, sz, SOUTH, 1.6f, 2.68f, 3.0f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.SECURITY));
        m.panel(14, sz, SOUTH, 1.6f, 2.68f, 3.0f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.MANAGER));

        // Lights.
        float[][] lights = {{-12, -15.5f, 2.5f, 0.3f, 1, 0.95f, 0.85f},
                {0, -15.5f, 2.5f, 0.3f, 1, 0.95f, 0.85f}, {12, -15.5f, 2.5f, 0.3f, 1, 0.95f, 0.85f},
                {-12, -20.5f, 4, 0.38f, 1, 0.8f, 0.55f}, {-2, -20.5f, 3, 0.3f, 0.8f, 0.9f, 1},
                {6, -20.5f, 3, 0.26f, 0.75f, 0.85f, 1}, {14, -20.5f, 3, 0.32f, 1, 0.85f, 0.6f}};
        for (float[] l : lights) {
            w.lamps.add(l);
            m.ceiling(l[0] - 0.5f, l[1] - 0.3f, l[0] + 0.5f, l[1] + 0.3f, ceil - 0.02f,
                    0xFFFFF2D8, 0.9f);
        }
        // Who's allowed where.
        w.zones.add(new float[]{-18, -17, 18, -14, 1});
        w.zones.add(new float[]{2, -24, 10, -17, 1});
        w.zones.add(new float[]{10, -24, 18, -17, 2});
        w.zones.add(new float[]{-18, -24, -6, -17, 2});
        w.zones.add(new float[]{-6, -24, 2, -17, 3});

        // Corridor: a supply closet you can stuff someone into, a water cooler.
        m.boxAA(-17.88f, 0, -16.88f, -16.6f, 2.3f, -15.0f, METAL, 0);
        m.boxAA(-16.62f, 1.1f, -16.0f, -16.58f, 1.3f, -15.9f, CasinoWorld.BRASS, 0.2f);
        w.solid(-17.88f, -16.88f, -16.6f, -15.0f, 2.3f);
        hide(w, "Supply closet", -16.0f, -15.9f, 2);
        m.cylinder(8.5f, -16.5f, 0.18f, 0.18f, 0, 1.0f, 0xFFD8D8DC, 0xFFD8D8DC, 0, 8);
        m.cylinder(8.5f, -16.5f, 0.15f, 0.15f, 1.0f, 1.45f, 0xFF6EB4E8, 0xFF6EB4E8, 0.2f, 8);
        w.solid(8.3f, -16.88f, 8.7f, -16.3f, 1.5f);

        // ---- High Limit Room: the secret game. Same machines, much bigger bets.
        m.floor(-18, -24, -6, -17, 0.003f, 0xFF2A1630, 0, 1);
        float wz = -24 + T / 2;
        int[] arts = {0, 3, 4};
        for (int i = 0; i < 3; i++) {
            float x = -15.5f + i * 2.5f;
            w.cabinet(x, wz + 0.33f, SOUTH, arts[i], true);
            CasinoWorld.Spot s = new CasinoWorld.Spot("High Limit " + SlotMath.NAMES[arts[i]],
                    "vip", arts[i], null, x, wz + 1.63f, NORTH);
            s.vip = true;
            w.spots.add(s);
        }
        m.panel(-13, wz + 0.01f, SOUTH, 3.6f, 2.75f, 3.3f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.HIGH));
        // Lounge: two armchairs and a low table.
        armchair(w, -7.0f, -20.2f, WEST);
        armchair(w, -7.0f, -21.8f, WEST);
        m.cylinder(-8.8f, -21.0f, 0.45f, 0.45f, 0, 0.55f, 0xFF1A120E, 0xFF2A1810, 0, 16);
        w.solid(-9.25f, -21.45f, -8.35f, -20.55f, 0.6f);
        chip(w, "whale", -8.8f, -21.0f, 0.75f);
        note(w, "hl", "the plaque", -10.2f, -17.8f);

        // ---- Vault: coupon pallets, deposit boxes, the cash cart.
        m.floor(-6, -24, 2, -17, 0.003f, 0xFF3A3C40, 0, 1);
        for (int i = 0; i < 3; i++) {
            float x = -5.4f + i * 1.3f;
            m.boxAA(x - 0.5f, 0, -23.7f, x + 0.5f, 0.15f, -22.7f, WOOD, 0);
            m.boxAA(x - 0.45f, 0.15f, -23.65f, x + 0.45f, 1.0f + i * 0.2f, -22.75f, 0xFF3C6E3A,
                    0.05f);
            w.solid(x - 0.5f, -23.88f, x + 0.5f, -22.7f, 1.4f);
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                float z = -23.6f + c * 0.5f;
                m.boxAA(1.6f, 0.4f + r * 0.5f, z - 0.22f, 1.88f, 0.85f + r * 0.5f, z + 0.22f,
                        0xFF8A8E96, 0);
                m.boxAA(1.58f, 0.6f + r * 0.5f, z - 0.03f, 1.6f, 0.66f + r * 0.5f, z + 0.03f,
                        CasinoWorld.BRASS, 0.3f);
            }
        }
        w.solid(1.5f, -23.88f, 1.88f, -21.1f, 2.4f);
        m.boxAA(-2.6f, 0.2f, -21.4f, -1.4f, 0.95f, -20.6f, 0xFF707680, 0);
        m.boxAA(-2.5f, 0.95f, -21.3f, -1.5f, 1.25f, -20.7f, 0xFF3C6E3A, 0.08f);
        w.solid(-2.6f, -21.4f, -1.4f, -20.6f, 1.3f);
        loot(w, "vault", "the cash cart", -2, -19.9f, 0, 1500);
        chip(w, "vaultgold", -4.1f, -23.2f, 1.35f);
        note(w, "vault", "the taped note", 0.9f, -22.6f);

        // ---- Security office: desk of monitors.
        m.boxAA(3, 0, -23.88f, 9, 0.8f, -22.9f, 0xFF2A2A30, 0);
        for (int i = 0; i < 4; i++) {
            float x = 3.8f + i * 1.45f;
            m.boxAA(x - 0.6f, 0.8f, -23.7f, x + 0.6f, 1.75f, -23.5f, 0xFF111114, 0);
            m.panel(x, -23.49f, SOUTH, 1.1f, 0.86f, 1.68f, 0xFF4A6A8A, 0.75f, null);
        }
        w.solid(3, -23.88f, 9, -22.9f, 1.8f);
        note(w, "seclog", "the security log", 5, -22.1f);
        m.boxAA(9.2f, 0, -23.88f, 9.88f, 1.35f, -23.0f, METAL, 0);
        w.solid(9.2f, -23.88f, 9.88f, -23.0f, 1.4f);
        chip(w, "neonace", 9.55f, -23.4f, 1.55f);

        // ---- Manager's office: desk, safe, bookshelf.
        m.floor(10, -24, 18, -17, 0.003f, 0xFF3A2418, 0, 1);
        m.boxAA(12.8f, 0, -22.3f, 15.2f, 0.8f, -21.5f, WOOD, 0);
        m.boxAA(12.7f, 0.8f, -22.4f, 15.3f, 0.85f, -21.4f, 0xFF2A1810, 0);
        w.solid(12.8f, -22.3f, 15.2f, -21.5f, 0.9f);
        m.boxAA(13.7f, 0, -23.4f, 14.3f, 1.2f, -23.1f, 0xFF1A1A1E, 0);
        loot(w, "safe", "the safe", 17.0f, -21.9f, 5, 400);
        m.boxAA(16.6f, 0, -23.88f, 17.88f, 1.2f, -22.9f, 0xFF5A5E66, 0);
        m.boxAA(16.9f, 0.45f, -22.92f, 17.5f, 0.75f, -22.88f, 0xFF2A2C30, 0);
        w.solid(16.6f, -23.88f, 17.88f, -22.9f, 1.3f);
        m.boxAA(10.12f, 0, -23.88f, 12.0f, 2.4f, -23.4f, WOOD, 0);
        for (int r = 0; r < 4; r++) {
            m.boxAA(10.2f, 0.4f + r * 0.5f, -23.4f, 11.9f, 0.44f + r * 0.5f, -23.35f,
                    0xFF2A1810, 0);
        }
        w.solid(10.12f, -23.88f, 12.0f, -23.4f, 2.4f);
        chip(w, "doubledown", 11.0f, -23.2f, 1.42f);
        key(w, "hlkey", "the key card", 14.7f, -21.9f, 0.88f);
        note(w, "memo", "the memo", 13.3f, -20.9f);
    }

    private static void armchair(CasinoWorld w, float x, float z, float yaw) {
        CasinoWorld.Mesh m = w.mesh;
        int c = 0xFF4A1A2A;
        m.box(x, z, yaw, 0.45f, 0.4f, 0, 0.45f, c, 0);
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        m.box(x - fx * 0.35f, z - fz * 0.35f, yaw, 0.45f, 0.1f, 0.45f, 1.05f, c, 0);
    }

    private static void frontAndStreet(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float fz = 14 + T / 2;
        // Sidewalks, street, lines and crosswalks.
        m.floor(-48, fz, 48, 18, 0, CONCRETE, 0, 1);
        m.floor(-48, 18, 48, 26, 0, ASPHALT, 0, 1);
        m.floor(-48, 26, 48, 30 - T / 2, 0, CONCRETE, 0, 1);
        m.floor(-48, 17.92f, 48, 18.08f, 0.004f, 0xFF8A8680, 0, 1);
        m.floor(-48, 25.92f, 48, 26.08f, 0.004f, 0xFF8A8680, 0, 1);
        for (float x = -47; x < 48; x += 3.2f) {
            m.floor(x, 21.94f, x + 1.6f, 22.06f, 0.004f, 0xFFB89A30, 0, 1);
        }
        for (float cx : new float[]{-14, 22}) {
            for (float z = 18.4f; z < 25.7f; z += 0.9f) {
                m.floor(cx - 1.3f, z, cx + 1.3f, z + 0.45f, 0.005f, 0xFFB8B8B0, 0, 1);
            }
        }
        for (float x = -46; x < 47; x += 6) {
            m.floor(x, 14.12f, x + 0.04f, 17.9f, 0.003f, 0xFF45423E, 0, 1);
            m.floor(x, 26.1f, x + 0.04f, 29.88f, 0.003f, 0xFF45423E, 0, 1);
        }
        // Street lamps.
        for (float x = -44; x < 46; x += 12) streetLamp(w, x, 17.5f, 1);
        for (float x = -32; x < 46; x += 12) streetLamp(w, x, 26.5f, -1);

        // The casino's face: parapet, gold trim, the big sign, an awning over the door.
        m.boxAA(-18 - T / 2, CasinoWorld.CEIL + 0.3f, 14 - T / 2, 18 + T / 2, 7.6f, fz + 0.2f,
                CasinoWorld.STONE, 0);
        m.boxAA(-18 - T / 2, 5.55f, fz, 18 + T / 2, 5.68f, fz + 0.25f, CasinoWorld.GOLD, 0.7f);
        m.panel(0, fz + 0.21f, SOUTH, 9.2f, 5.85f, 7.5f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.GILDED));
        m.boxAA(-3.2f, 3.0f, fz, 3.2f, 3.25f, 16.4f, 0xFF2A1A14, 0);
        m.boxAA(-3.2f, 2.96f, 16.3f, 3.2f, 3.0f, 16.4f, CasinoWorld.GOLD, 0.8f);
        m.boxAA(-3.25f, 3.25f, 16.35f, 3.25f, 3.3f, 16.45f, CasinoWorld.GOLD, 0.6f);
        for (float x : new float[]{-3.0f, 3.0f}) {
            m.cylinder(x, 16.2f, 0.06f, 0.06f, 0, 3.0f, CasinoWorld.BRASS, 0, 0.15f, 8);
        }
        // Gold light strips down the facade.
        for (float x = -16; x <= 16.1f; x += 4) {
            if (Math.abs(x) < 2) continue;
            m.boxAA(x - 0.06f, 0.3f, fz, x + 0.06f, 5.5f, fz + 0.08f, CasinoWorld.GOLD, 0.65f);
        }
        w.lamps.add(new float[]{0, 15.6f, 3.2f, 0.55f, 1, 0.85f, 0.55f});
        w.lamps.add(new float[]{-9, 15.4f, 3.5f, 0.25f, 1, 0.8f, 0.45f});
        w.lamps.add(new float[]{9, 15.4f, 3.5f, 0.25f, 1, 0.8f, 0.45f});
    }

    private static void streetLamp(CasinoWorld w, float x, float z, float dir) {
        CasinoWorld.Mesh m = w.mesh;
        m.cylinder(x, z, 0.08f, 0.08f, 0, 5.4f, 0xFF2A2C30, 0xFF2A2C30, 0, 8);
        m.boxAA(x - 0.04f, 5.3f, Math.min(z, z + dir * 1.2f), x + 0.04f, 5.38f,
                Math.max(z, z + dir * 1.2f), 0xFF2A2C30, 0);
        m.boxAA(x - 0.22f, 5.1f, z + dir * 1.2f - 0.3f, x + 0.22f, 5.3f, z + dir * 1.2f + 0.3f,
                0xFF2A2C30, 0);
        m.boxAA(x - 0.18f, 5.08f, z + dir * 1.2f - 0.26f, x + 0.18f, 5.1f,
                z + dir * 1.2f + 0.26f, 0xFFFFC27A, 1);
        w.solid(x - 0.12f, z - 0.12f, x + 0.12f, z + 0.12f, 5.4f);
        w.lamps.add(new float[]{x, z + dir * 1.2f, 5.5f, 0.85f, 1.0f, 0.66f, 0.34f});
    }

    private static void parkingLot(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float x1 = -18 - T / 2;
        m.floor(-48, -14, x1, 14 + T / 2, 0, LOT, 0, 1);
        w.wallX(-14, -48, x1, 2.2f, FENCE);
        // Parking stalls.
        for (float z : new float[]{-10.5f, 3.5f}) {
            for (float x = -45; x <= -21; x += 3) {
                m.floor(x - 0.05f, z - 2.6f, x + 0.05f, z + 2.6f, 0.004f, 0xFFB8B8B0, 0, 1);
            }
        }
        // x, z, color, type. North row faces south, south row faces north.
        float[][] cars = {{-43.5f, -10.5f, 0xFF9A1E1E, 0}, {-37.5f, -10.5f, 0xFFC8CCD2, 1},
                {-30, -10.5f, 0xFF2A4E8A, 0}, {-25.5f, -10.5f, 0xFF2E6A44, 3},
                {-40.5f, 3.5f, 0xFF1C1C20, 1}, {-34.5f, 3.5f, 0xFFE8E8EA, 0},
                {-22.5f, 3.5f, 0xFF5A2A5A, 0}};
        for (float[] c : cars) {
            // Heading: the north row faces south, the south row faces north.
            float h = c[1] < 0 ? CasinoWorld.PI : 0;
            w.parkedCars.add(new float[]{c[0], c[1], h, c[2], c[3]});
        }
        loot(w, "trunk", "the car trunk", -30, -7.2f, 3, 120);
        hide(w, "Car trunk", -34.5f, 6.7f, 1);
        for (float[] l : new float[][]{{-40, -4}, {-26, -4}, {-40, 9.5f}, {-26, 9.5f}}) {
            CasinoWorld.Mesh mm = w.mesh;
            mm.cylinder(l[0], l[1], 0.09f, 0.09f, 0, 6, 0xFF2A2C30, 0xFF2A2C30, 0, 8);
            mm.boxAA(l[0] - 0.35f, 5.8f, l[1] - 0.2f, l[0] + 0.35f, 6.0f, l[1] + 0.2f,
                    0xFF2A2C30, 0);
            mm.boxAA(l[0] - 0.3f, 5.78f, l[1] - 0.16f, l[0] + 0.3f, 5.8f, l[1] + 0.16f,
                    0xFFFFC27A, 1);
            w.solid(l[0] - 0.12f, l[1] - 0.12f, l[0] + 0.12f, l[1] + 0.12f, 6);
            w.lamps.add(new float[]{l[0], l[1], 7, 0.95f, 1.0f, 0.7f, 0.4f});
        }
    }

    private static void alley(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float x0 = 18 + T / 2;
        m.floor(x0, -28, 26, 14 + T / 2, 0, 0xFF262220, 0, 1);
        w.wallZ(26, -28, -6, 5, BRICK);
        // Dumpster and crates.
        m.boxAA(23.5f, 0, -20.2f, 25.8f, 1.4f, -18.3f, 0xFF2E4A34, 0);
        m.boxAA(23.45f, 1.4f, -20.25f, 25.85f, 1.5f, -18.25f, 0xFF223A28, 0);
        w.solid(23.5f, -20.2f, 25.8f, -18.3f, 1.5f);
        hide(w, "Dumpster", 22.8f, -19.25f, 3);
        m.boxAA(24.6f, 0, -14.0f, 25.7f, 0.9f, -13.0f, WOOD, 0);
        m.boxAA(24.8f, 0.9f, -13.9f, 25.6f, 1.6f, -13.2f, 0xFF6A4A2A, 0);
        w.solid(24.6f, -14.0f, 25.7f, -13.0f, 1.6f);
        // Graffiti on the brick.
        m.panel(25.87f, -10, WEST, 3.2f, 1.0f, 2.6f, 0xFF6A2A8A, 0.25f, null);
        m.panel(25.86f, -10, WEST, 2.6f, 1.5f, 2.1f, 0xFF2AA88A, 0.3f, null);
        note(w, "graffiti", "the graffiti", 24.6f, -10);
        // Dice in the alley.
        w.extraStaff.add(new float[]{21.5f, -2.5f, EAST, CasinoWorld.CIVILIAN, 1});
        w.extraStaffNames.add("Shooter");
        w.extraStaff.add(new float[]{23.0f, -2.5f, WEST, CasinoWorld.CIVILIAN, 1});
        w.extraStaffNames.add("Lil' Pete");
        chip(w, "snakeeyes", 22.25f, -1.7f, 0.12f);
        // A light over the back door.
        m.boxAA(18.12f, 2.9f, -16.0f, 18.4f, 3.05f, -15.0f, 0xFF2A2C30, 0);
        m.boxAA(18.12f, 2.88f, -15.9f, 18.36f, 2.9f, -15.1f, 0xFFFFD9A0, 1);
        w.lamps.add(new float[]{19.5f, -15.5f, 3.5f, 0.5f, 1, 0.8f, 0.55f});
        w.lamps.add(new float[]{22, -3, 4.5f, 0.55f, 1.0f, 0.66f, 0.34f});
        w.lamps.add(new float[]{22, 8, 4.5f, 0.5f, 1.0f, 0.66f, 0.34f});
        w.lamps.add(new float[]{22, -23, 4, 0.45f, 1.0f, 0.66f, 0.34f});
        for (float z : new float[]{-3, 8, -23}) {
            m.boxAA(25.6f, 3.4f, z - 0.25f, 25.88f, 3.55f, z + 0.25f, 0xFF2A2C30, 0);
            m.boxAA(25.58f, 3.38f, z - 0.2f, 25.84f, 3.4f, z + 0.2f, 0xFFFFC27A, 1);
        }
    }

    /** A building's shell: a facade on the street side and three plain walls. */
    private static void shell(CasinoWorld w, float x0, float x1, float h, int face, int walls,
                              float... doorGaps) {
        w.wallX(30, x0, x1, h, face, doorGaps);
        w.wallZ(x0, 30, 42, h, walls);
        w.wallZ(x1, 30, 42, h, walls);
        w.wallX(42, x0, x1, h, walls);
        w.mesh.floor(x0 - T / 2, 30 - T / 2, x1 + T / 2, 42 + T / 2, h, 0xFF15110F, 0, 1);
    }

    private static void filler(CasinoWorld w, float x0, float x1, float h, int c) {
        w.mesh.boxAA(x0, 0, 30 - T / 2, x1, h, 42, c, 0);
        w.solid(x0, 30 - T / 2, x1, 42, h);
        // A few lit windows so the block isn't a black slab.
        for (float x = x0 + 1; x < x1 - 1.2f; x += 2.2f) {
            for (float y = 3.2f; y < h - 1; y += 2.4f) {
                if (((int) (x * 7 + y * 3)) % 3 == 0) continue;
                w.mesh.panel(x + 0.5f, 30 - T / 2 - 0.01f, NORTH, 0.9f, y, y + 1.1f,
                        ((int) (x + y)) % 2 == 0 ? 0xFFFFD08A : 0xFF8AA8D8, 0.55f, null);
            }
        }
    }

    private static void pawnShop(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        jeweler(w);
        shell(w, -40, -26, 5, 0xFF3A3A2E, 0xFF2E2E26, -34.6f, -33.4f);
        w.regions.add(new float[]{-40, 30, -26, 42, 4});
        m.floor(-40, 30, -26, 42, 0, 0xFF3A3328, 0, 1);
        m.ceiling(-40, 30, -26, 42, 4, 0xFF1A1816, 0);
        m.panel(-34, 30 - T / 2 - 0.02f, NORTH, 5, 3.2f, 4.1f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.PAWN));
        m.panel(-37.5f, 30 - T / 2 - 0.01f, NORTH, 2.4f, 1.0f, 2.5f, 0xFF3A5A4A, 0.45f, null);
        m.panel(-30.5f, 30 - T / 2 - 0.01f, NORTH, 2.4f, 1.0f, 2.5f, 0xFF3A5A4A, 0.45f, null);
        // Counter, shelves of junk, and the pawnbroker.
        m.boxAA(-38, 0, 36.0f, -30, 1.05f, 36.8f, 0xFF3A4A4A, 0);
        m.boxAA(-38, 1.05f, 35.95f, -30, 1.1f, 36.85f, WOOD, 0);
        w.solid(-38, 36.0f, -30, 36.8f, 1.1f);
        int[] junk = {0xFF8A3A2E, 0xFF3A6A8A, 0xFFC9A24A, 0xFF6A6A72, 0xFF2E5A3A, 0xFFD8D4CC};
        for (int r = 0; r < 3; r++) {
            m.boxAA(-39.5f, 0.6f + r * 0.8f, 41.4f, -27, 0.65f + r * 0.8f, 41.88f, WOOD, 0);
            for (int i = 0; i < 9; i++) {
                float x = -39.0f + i * 1.35f + (r % 2) * 0.4f;
                float hgt = 0.2f + ((i * 3 + r * 5) % 4) * 0.1f;
                m.boxAA(x, 0.65f + r * 0.8f, 41.5f, x + 0.5f, 0.65f + r * 0.8f + hgt, 41.85f,
                        junk[(i + r) % junk.length], 0);
            }
        }
        w.solid(-39.5f, 41.4f, -27, 41.88f, 2.5f);
        w.extraStaff.add(new float[]{-34, 37.6f, NORTH, CasinoWorld.CIVILIAN, 0});
        w.extraStaffNames.add("Pawnbroker");
        CasinoWorld.Spot shop = new CasinoWorld.Spot(CasinoWorld.SHOP, "pawn",
                "Talk to the pawnbroker", -34, 35.2f);
        w.spots.add(shop);
        note(w, "ledger", "the ledger", -31, 35.2f);
        // The display case: a little lock, a lucky chip inside.
        m.boxAA(-28.6f, 0, 33.6f, -27.4f, 0.9f, 34.4f, WOOD, 0);
        m.boxAA(-28.55f, 0.9f, 33.65f, -27.45f, 1.2f, 34.35f, 0xFF8ABAC8, 0.35f);
        w.solid(-28.6f, 33.6f, -27.4f, 34.4f, 1.2f);
        CasinoWorld.Spot c = loot(w, "case", "the display case", -28, 35.2f, 3, 0);
        c.chip = "pawnedheart";
        w.lamps.add(new float[]{-33, 35.5f, 4.5f, 0.38f, 1, 0.9f, 0.7f});
        m.ceiling(-33.6f, 35.2f, -32.4f, 35.8f, 3.98f, 0xFFFFF2D8, 0.9f);
    }

    private static void diner(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        filler(w, -26, -22, 8, 0xFF2A2C34);
        shell(w, -22, -6, 4.5f, 0xFF2E3E48, 0xFF2A2C30, -14.6f, -13.4f);
        w.regions.add(new float[]{-22, 30, -6, 42, 4});
        m.floor(-22, 30, -6, 42, 0, 0xFFB8B0A0, 0, 1);
        for (float x = -21.5f; x < -6; x += 1) {
            for (float z = 30.5f; z < 42; z += 1) {
                if (((int) (x + 22) + (int) (z - 30)) % 2 == 0) {
                    m.floor(x, z, x + 1, z + 1, 0.003f, 0xFF2A2A2E, 0, 1);
                }
            }
        }
        m.ceiling(-22, 30, -6, 42, 4, 0xFF2A2A2E, 0);
        float fz = 30 - T / 2 - 0.01f;
        m.panel(-14, fz - 0.01f, NORTH, 6, 3.0f, 3.8f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.DINER));
        m.panel(-8.4f, fz - 0.01f, NORTH, 2.4f, 2.65f, 2.95f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.OPEN));
        m.panel(-18.1f, fz, NORTH, 6.4f, 1.0f, 2.5f, 0xFFFFE2B0, 0.6f, null);
        m.panel(-9.9f, fz, NORTH, 6.4f, 1.0f, 2.5f, 0xFFFFE2B0, 0.6f, null);
        m.panel(-18.1f, 30 + T / 2 + 0.01f, SOUTH, 6.4f, 1.0f, 2.5f, 0xFF1A2438, 0.5f, null);
        m.panel(-9.9f, 30 + T / 2 + 0.01f, SOUTH, 6.4f, 1.0f, 2.5f, 0xFF1A2438, 0.5f, null);
        // Counter with stools; the kitchen behind it.
        m.boxAA(-20, 0, 37.4f, -9, 1.05f, 38.2f, 0xFFB83A3A, 0);
        m.boxAA(-20, 1.05f, 37.35f, -9, 1.1f, 38.25f, 0xFFD8D8DC, 0);
        w.solid(-20, 37.4f, -9, 38.2f, 1.1f);
        for (float x = -19; x <= -10.9f; x += 2) w.stool(x, 36.8f, 0xFFB83A3A);
        w.extraSeats.add(new float[]{-19, 36.8f, SOUTH});
        w.extraSeats.add(new float[]{-11, 36.8f, SOUTH});
        w.zones.add(new float[]{-22, 38.2f, -6, 42, 1});
        m.boxAA(-19, 0, 41.2f, -12, 0.95f, 41.88f, 0xFFA8AAB0, 0);
        m.boxAA(-18.6f, 0.95f, 41.3f, -17.2f, 1.0f, 41.8f, 0xFF2A2A2E, 0);
        w.solid(-19, 41.2f, -12, 41.88f, 1.0f);
        w.extraStaff.add(new float[]{-15, 40.3f, SOUTH, CasinoWorld.COOK, 0});
        w.extraStaffNames.add("Cook");
        m.boxAA(-21.88f, 0, 39.5f, -20.6f, 2.3f, 41.88f, 0xFFC8CCD2, 0);
        m.boxAA(-20.62f, 1.0f, 40.4f, -20.58f, 1.4f, 40.5f, 0xFF2A2A2E, 0);
        w.solid(-21.88f, 39.5f, -20.6f, 41.88f, 2.3f);
        hide(w, "Walk-in freezer", -19.9f, 40.6f, 2);
        // Booths on the east side.
        for (float z : new float[]{32.5f, 35.2f}) {
            m.boxAA(-9.0f, 0, z - 0.4f, -7.6f, 0.75f, z + 0.4f, 0xFFD8D8DC, 0);
            m.boxAA(-6.88f, 0, z - 0.8f, -6.3f, 1.2f, z + 0.8f, 0xFFB83A3A, 0);
            m.boxAA(-10.3f, 0, z - 0.8f, -9.7f, 1.2f, z + 0.8f, 0xFFB83A3A, 0);
            w.solid(-10.3f, z - 0.8f, -6.12f, z + 0.8f, 1.2f);
        }
        w.extraSeats.add(new float[]{-7.0f, 32.5f, WEST});
        note(w, "napkin", "a napkin", -11.0f, 32.5f);
        chip(w, "midnight", -11.0f, 35.9f, 0.12f);
        w.lamps.add(new float[]{-17, 34, 3.5f, 0.34f, 1, 0.85f, 0.6f});
        w.lamps.add(new float[]{-10, 34, 3.5f, 0.34f, 1, 0.85f, 0.6f});
        w.lamps.add(new float[]{-15, 40, 3, 0.3f, 1, 1, 0.95f});
        for (float[] l : new float[][]{{-17, 34}, {-10, 34}, {-15, 40}}) {
            m.ceiling(l[0] - 0.4f, l[1] - 0.4f, l[0] + 0.4f, l[1] + 0.4f, 3.98f, 0xFFFFF2D8, 0.9f);
        }
        tailor(w);
    }

    /** Gold Rush Jewelers: black marble, glowing cases of gold. */
    private static void jeweler(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float x0 = -47.88f, x1 = -40.3f;
        shell(w, x0, x1, 5, 0xFF1A1A1E, 0xFF141418, -44.6f, -43.4f);
        w.regions.add(new float[]{x0, 30, x1, 42, 4});
        w.zones.add(new float[]{x0, 37.6f, x1, 42, 1});
        m.floor(x0, 30, x1, 42, 0, 0xFF16161C, 0, 1);
        m.ceiling(x0, 30, x1, 42, 4, 0xFF0E0E12, 0);
        float fz = 30 - T / 2 - 0.02f;
        m.panel(-44, fz, NORTH, 5.6f, 3.2f, 4.2f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.GOLDRUSH));
        m.panel(-46.4f, fz + 0.01f, NORTH, 2.2f, 1.0f, 2.5f, 0xFF3A3018, 0.5f, null);
        m.panel(-41.6f, fz + 0.01f, NORTH, 2.2f, 1.0f, 2.5f, 0xFF3A3018, 0.5f, null);
        // Glass cases along both walls, and the back counter.
        for (float z = 32.5f; z < 37; z += 2.2f) {
            for (float x : new float[]{-47.0f, -41.2f}) {
                m.boxAA(x - 0.55f, 0, z - 0.8f, x + 0.55f, 0.85f, z + 0.8f, 0xFF1C1C22, 0);
                m.boxAA(x - 0.5f, 0.85f, z - 0.75f, x + 0.5f, 0.9f, z + 0.75f, 0xFF4A7080, 0.5f);
                for (int k = 0; k < 4; k++) {
                    m.boxAA(x - 0.3f + (k % 2) * 0.4f, 0.9f, z - 0.5f + (k / 2) * 0.6f,
                            x - 0.18f + (k % 2) * 0.4f, 0.97f, z - 0.36f + (k / 2) * 0.6f,
                            k == 3 ? 0xFFDCEEFF : 0xFFE0B040, 0.9f);
                }
                w.solid(x - 0.55f, z - 0.8f, x + 0.55f, z + 0.8f, 1.2f);
            }
        }
        m.boxAA(-46.5f, 0, 37.6f, -41.7f, 1.05f, 38.3f, 0xFF1C1C22, 0);
        m.boxAA(-46.5f, 1.05f, 37.6f, -41.7f, 1.1f, 38.3f, 0xFFB8862E, 0.3f);
        w.solid(-46.5f, 37.6f, -41.7f, 38.3f, 1.1f);
        m.boxAA(-47.5f, 1.4f, 41.4f, -40.7f, 2.6f, 41.76f, 0xFF2A2418, 0);
        m.boxAA(-47.3f, 1.5f, 41.38f, -40.9f, 2.5f, 41.4f, 0xFFE0B040, 0.55f);
        w.extraStaff.add(new float[]{-44, 39.0f, NORTH, CasinoWorld.STAFF_OUTFIT, 0});
        w.extraStaffNames.add("Jeweler");
        w.spots.add(new CasinoWorld.Spot(CasinoWorld.SHOP, CasinoShop.JEWELER,
                "Shop at Gold Rush", -44, 36.9f));
        for (float z : new float[]{33, 37}) {
            w.lamps.add(new float[]{-44, z, 3.5f, 0.45f, 1, 0.9f, 0.7f});
            m.ceiling(-44.4f, z - 0.4f, -43.6f, z + 0.4f, 3.98f, 0xFFFFF2D8, 0.9f);
        }
    }

    /** Threads: a clothing store with racks and mannequins. */
    private static void tailor(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float x0 = -5.7f, x1 = -0.3f;
        shell(w, x0, x1, 5, 0xFF2A3A3E, 0xFF22282C, -3.6f, -2.4f);
        w.regions.add(new float[]{x0, 30, x1, 42, 4});
        m.floor(x0, 30, x1, 42, 0, 0xFF5A4030, 0, 1);
        m.ceiling(x0, 30, x1, 42, 4, 0xFF1A1816, 0);
        float fz = 30 - T / 2 - 0.02f;
        m.panel(-3, fz, NORTH, 4.6f, 3.2f, 4.2f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.THREADS));
        m.panel(-4.8f, fz + 0.01f, NORTH, 1.4f, 0.4f, 2.5f, 0xFF3A2A30, 0.45f, null);
        m.panel(-1.2f, fz + 0.01f, NORTH, 1.4f, 0.4f, 2.5f, 0xFF3A2A30, 0.45f, null);
        int[] cloth = {0xFF9A1420, 0xFF1C1C20, 0xFFE8E6E0, 0xFF5A2A8A, 0xFF1E2A44, 0xFFE0702A,
                0xFF6A6C72, 0xFFC8A030};
        for (float x : new float[]{-5.0f, -1.0f}) {
            m.boxAA(x - 0.03f, 1.6f, 31.5f, x + 0.03f, 1.66f, 37.5f, 0xFF8A8A90, 0.1f);
            for (int k = 0; k < 14; k++) {
                float z = 31.7f + k * 0.42f;
                m.boxAA(x - 0.28f, 0.75f, z, x + 0.28f, 1.58f, z + 0.1f,
                        cloth[(k + (x < -3 ? 0 : 3)) % cloth.length], 0);
            }
            w.solid(x - 0.35f, 31.5f, x + 0.35f, 37.5f, 1.7f);
        }
        // Mirror and counter at the back.
        m.panel(-3, 41.86f, SOUTH, 1.6f, 0.2f, 2.3f, 0xFFB8C8D0, 0.4f, null);
        m.boxAA(-4.6f, 0, 39.4f, -2.2f, 1.0f, 40.1f, 0xFF3A2A20, 0);
        w.solid(-4.6f, 39.4f, -2.2f, 40.1f, 1.0f);
        w.extraStaff.add(new float[]{-3.4f, 40.8f, NORTH, CasinoWorld.CIVILIAN, 0});
        w.extraStaffNames.add("Tailor");
        w.spots.add(new CasinoWorld.Spot(CasinoWorld.SHOP, CasinoShop.TAILOR,
                "Shop at Threads", -3.4f, 38.7f));
        w.lamps.add(new float[]{-3, 34, 3.5f, 0.42f, 1, 0.9f, 0.75f});
        w.lamps.add(new float[]{-3, 39, 3, 0.35f, 1, 0.9f, 0.75f});
        m.ceiling(-3.4f, 33.6f, -2.6f, 34.4f, 3.98f, 0xFFFFF2D8, 0.9f);
    }

    private static void motel(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float h = 4;
        w.wallX(30, 0, 30, h, 0xFF5A4A3A, 23.4f, 24.6f);
        w.wallZ(0, 30, 42, h, 0xFF4A3A2E);
        w.wallZ(30, 30, 42, h, 0xFF4A3A2E);
        w.wallX(42, 0, 30, h, 0xFF4A3A2E);
        m.floor(0 - T / 2, 30 - T / 2, 30 + T / 2, 42 + T / 2, h, 0xFF15110F, 0, 1);
        w.solid(0, 30 + T / 2, 21 - T / 2, 42, h);
        w.solid(27 + T / 2, 30 + T / 2, 30, 42, h);
        w.solid(21, 38 + T / 2, 27, 42, h);
        float fz = 30 - T / 2 - 0.01f;
        // Room doors along the walkway, each with a little lamp.
        for (float x : new float[]{3, 8, 13, 18}) {
            m.panel(x, fz, NORTH, 1.1f, 0, 2.3f, 0xFF6A4A2A, 0, null);
            m.panel(x + 0.4f, fz - 0.005f, NORTH, 0.08f, 1.1f, 1.18f, CasinoWorld.BRASS, 0.2f,
                    null);
            m.panel(x + 0.9f, fz - 0.01f, NORTH, 0.25f, 2.4f, 2.6f, 0xFFFFD08A, 1, null);
        }
        m.panel(24.9f, fz - 0.01f, NORTH, 0.25f, 2.4f, 2.6f, 0xFFFFD08A, 1, null);
        for (float x = 3; x < 30; x += 5) {
            w.lamps.add(new float[]{x + 0.9f, 29, 2.5f, 0.3f, 1, 0.8f, 0.5f});
        }
        // The tall sign.
        m.cylinder(1, 28.6f, 0.12f, 0.12f, 0, 6.2f, 0xFF2A2C30, 0xFF2A2C30, 0, 8);
        w.solid(0.85f, 28.45f, 1.15f, 28.75f, 6);
        m.box(1, 28.6f, NORTH, 1.7f, 0.08f, 5.0f, 6.0f, 0xFF050507, 0);
        m.panel(1, 28.51f, NORTH, 3.3f, 5.05f, 5.95f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.MOTEL));
        m.panel(1, 28.69f, SOUTH, 3.3f, 5.05f, 5.95f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.MOTEL));
        m.box(1, 28.6f, NORTH, 0.9f, 0.06f, 4.2f, 4.7f, 0xFF050507, 0);
        m.panel(1, 28.53f, NORTH, 1.7f, 4.25f, 4.65f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.VACANCY));

        // Room 7: locked. Somebody's been living here.
        CasinoWorld.Door d = new CasinoWorld.Door("room7", "Room 7", 24, 30, true, 1.2f, 4,
                0xFF6A4A2A);
        w.doors.add(d);
        w.regions.add(new float[]{21, 30, 27, 38, 3.2f});
        w.zones.add(new float[]{21, 30, 27, 38, 2});
        w.wallZ(21, 30, 38, 3.6f, 0xFF6A5A48);
        w.wallZ(27, 30, 38, 3.6f, 0xFF6A5A48);
        w.wallX(38, 21, 27, 3.6f, 0xFF6A5A48);
        m.floor(21, 30, 27, 38, 0, 0xFF4A3A30, 0, 1);
        m.ceiling(21, 30, 27, 38, 3.2f, 0xFF2A2420, 0);
        m.boxAA(24.2f, 0, 35.0f, 26.85f, 0.55f, 37.85f, 0xFF7A6A5A, 0);
        m.boxAA(24.25f, 0.55f, 35.05f, 26.8f, 0.62f, 37.8f, 0xFF8A3A3A, 0);
        m.boxAA(24.3f, 0.62f, 37.2f, 26.75f, 0.75f, 37.75f, 0xFFE8E4DC, 0);
        w.solid(24.2f, 35.0f, 26.85f, 37.85f, 0.8f);
        m.boxAA(23.3f, 0, 37.2f, 24.0f, 0.6f, 37.85f, WOOD, 0);
        w.solid(23.3f, 37.2f, 24.0f, 37.85f, 0.7f);
        m.boxAA(21.15f, 0, 31.2f, 21.9f, 0.95f, 33.0f, WOOD, 0);
        w.solid(21.15f, 31.2f, 21.9f, 33.0f, 1.0f);
        note(w, "diary", "the diary", 23.6f, 36.4f);
        loot(w, "duffel", "the duffel bag", 23.4f, 34.2f, 0, 200);
        chip(w, "starlite", 21.5f, 32.1f, 1.08f);
        hide(w, "Bathtub", 26.1f, 31.2f, 1);
        m.boxAA(25.3f, 0, 30.2f, 26.88f, 0.6f, 31.0f, 0xFFE8E8EC, 0);
        w.lamps.add(new float[]{24, 34, 3, 0.32f, 1, 0.8f, 0.5f});
        m.ceiling(23.7f, 33.7f, 24.3f, 34.3f, 3.18f, 0xFFFFE0A8, 0.9f);
        filler(w, 30, 34, 6, 0xFF2A2C34);
    }

    private static void bar(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        float h = 5;
        w.wallZ(26, -6, 14, h, BRICK);
        w.wallX(-6, 26, 48, h, BRICK);
        w.wallZ(48, -6, 14, h, BRICK);
        w.wallX(14, 26, 48, h, 0xFF3A1E1A, 36.4f, 37.6f);
        w.wallX(4, 26, 48, 4.2f, 0xFF2A1410);
        w.solid(26, -6, 48, 4, h);
        m.floor(26 - T / 2, -6 - T / 2, 48 + T / 2, 14 + T / 2, h, 0xFF15110F, 0, 1);
        w.regions.add(new float[]{26, 4, 48, 14, 4.2f});
        w.zones.add(new float[]{26, 4, 48, 14, 2});
        m.floor(26, 4, 48, 14, 0, 0xFF2A1A12, 0, 1);
        m.ceiling(26, 4, 48, 14, 4.2f, 0xFF120D0C, 0);
        CasinoWorld.Door d = new CasinoWorld.Door("bar", "The Last Call", 37, 14, true, 1.2f, 4,
                0xFF3A2414);
        w.doors.add(d);
        float fz = 14 + T / 2 + 0.02f;
        m.panel(37, fz, SOUTH, 5.2f, 3.1f, 3.9f, 0xFFFFFFFF, 1, CasinoArt.sign(CasinoArt.BAR));
        m.panel(31, fz - 0.01f, SOUTH, 4, 1.1f, 2.4f, 0xFF3A1810, 0.35f, null);
        m.panel(43, fz - 0.01f, SOUTH, 4, 1.1f, 2.4f, 0xFF3A1810, 0.35f, null);
        w.lamps.add(new float[]{37, 15.3f, 3, 0.4f, 1, 0.5f, 0.4f});
        // Inside: the counter, a wall of bottles, a few tables. Closed for the night.
        m.boxAA(30, 0, 7.0f, 44, 1.1f, 7.8f, 0xFF3A2114, 0);
        m.boxAA(29.9f, 1.1f, 6.95f, 44.1f, 1.16f, 7.85f, 0xFF1A0F0B, 0);
        w.solid(30, 7.0f, 44, 7.8f, 1.2f);
        for (float x = 31; x < 44; x += 2.6f) w.stool(x, 8.5f, 0xFF5A1A1A);
        int[] bottles = {0xFF2E8A4A, 0xFF8A5A1A, 0xFFB8862E, 0xFF5A1A2A, 0xFF4A7AB8};
        for (int r = 0; r < 3; r++) {
            m.boxAA(29, 1.2f + r * 0.6f, 4.12f, 45, 1.25f + r * 0.6f, 4.5f, WOOD, 0);
            for (int i = 0; i < 26; i++) {
                float x = 29.3f + i * 0.6f;
                m.boxAA(x, 1.25f + r * 0.6f, 4.2f, x + 0.12f, 1.6f + r * 0.6f, 4.4f,
                        bottles[(i + r * 2) % bottles.length], 0.45f);
            }
        }
        for (float x : new float[]{31, 37, 43}) {
            m.cylinder(x, 11.6f, 0.5f, 0.5f, 0.72f, 0.78f, 0xFF2A1810, 0xFF3A2114, 0, 14);
            m.cylinder(x, 11.6f, 0.06f, 0.06f, 0, 0.72f, 0xFF1A1A1E, 0, 0, 6);
            w.solid(x - 0.5f, 11.1f, x + 0.5f, 12.1f, 0.8f);
        }
        loot(w, "register", "the register", 40, 8.4f, 0, 150);
        m.boxAA(39.7f, 1.16f, 7.1f, 40.3f, 1.45f, 7.6f, 0xFF2A2A30, 0);
        note(w, "tab", "a bar tab", 34, 8.4f);
        chip(w, "lastcall", 43, 11.6f, 0.95f);
        m.boxAA(26.12f, 0, 9.0f, 27.6f, 1.6f, 11.0f, 0xFFA8AAB0, 0);
        w.solid(26.12f, 9.0f, 27.6f, 11.0f, 1.6f);
        hide(w, "Beer cooler", 28.3f, 10, 2);
        w.lamps.add(new float[]{37, 9.5f, 5, 0.28f, 1, 0.65f, 0.4f});
        m.ceiling(36.6f, 9.1f, 37.4f, 9.9f, 4.18f, 0xFFFFC890, 0.8f);
    }

    /** The Bullseye Range: a fenced outdoor range with a gun counter and pop-up targets. */
    private static void emptyLot(CasinoWorld w) {
        CasinoWorld.Mesh m = w.mesh;
        m.floor(34, 30 - T / 2, 48, 42, 0, 0xFF2A2620, 0, 1);
        m.floor(36.8f, 32.6f, 48, 42, 0.003f, 0xFF3A3020, 0, 1);
        w.wallX(30, 34, 48, 2.2f, FENCE, 40, 41.2f);
        m.boxAA(34, 0, 30, 34.1f, 2.2f, 42, FENCE, 0);
        w.wallX(42, 34, 48, 3.2f, 0xFF3A3A30);
        // Earth berm behind the targets.
        m.boxAA(34.2f, 0, 41.6f, 47.9f, 1.8f, 41.9f, 0xFF4A3A28, 0);
        // Sign over the gate.
        m.boxAA(39.2f, 2.2f, 29.9f, 42.0f, 2.95f, 30.0f, 0xFF050507, 0);
        m.panel(40.6f, 29.88f, NORTH, 2.7f, 2.25f, 2.9f, 0xFFFFFFFF, 1,
                CasinoArt.sign(CasinoArt.RANGE));
        // The shooting bench: you stand on the north side and shoot south.
        m.boxAA(36.8f, 0, 32.2f, 47.9f, 1.0f, 32.6f, 0xFF5A3A22, 0);
        m.boxAA(36.7f, 1.0f, 32.15f, 47.95f, 1.05f, 32.65f, 0xFF3A2418, 0);
        w.solid(34, 32.2f, 48, 32.6f, 1.05f);
        for (float x = 39.6f; x < 47; x += 2.4f) {
            m.boxAA(x - 0.04f, 1.05f, 32.3f, x + 0.04f, 1.9f, 32.5f, 0xFF2A2420, 0);
        }
        // The gun counter in the west corner, and its dumpster.
        m.boxAA(34.12f, 0, 30.12f, 36.6f, 1.05f, 30.8f, 0xFF2A2A30, 0);
        m.boxAA(34.1f, 1.05f, 30.1f, 36.6f, 1.1f, 30.82f, 0xFF15151A, 0);
        w.solid(34.1f, 30.1f, 36.6f, 30.8f, 1.1f);
        m.boxAA(34.12f, 1.6f, 32.0f, 36.6f, 2.6f, 32.18f, 0xFF15151A, 0);
        for (int i = 0; i < 5; i++) {
            m.boxAA(34.4f + i * 0.45f, 1.85f, 31.97f, 34.7f + i * 0.45f, 1.92f + (i % 2) * 0.05f,
                    32.0f, 0xFF1A1A1E, 0);
        }
        w.extraStaff.add(new float[]{35.3f, 31.3f, NORTH, CasinoWorld.SECURITY, 0});
        w.extraStaffNames.add("Range master");
        w.spots.add(new CasinoWorld.Spot(CasinoWorld.SHOP, "range", "Gun counter", 35.6f,
                30.0f + 1.5f));
        CasinoWorld.Spot start = new CasinoWorld.Spot(CasinoWorld.RANGE_START, "range",
                "Start a practice round", 42.0f, 31.3f);
        w.spots.add(start);
        m.boxAA(46.6f, 0, 30.12f, 47.88f, 0.9f, 31.2f, WOOD, 0);
        w.solid(46.6f, 30.12f, 47.88f, 31.2f, 1.0f);
        chip(w, "lucky13", 47.2f, 30.6f, 1.0f);
        m.boxAA(34.12f, 0, 40.0f, 36.3f, 1.4f, 41.6f, 0xFF4A2E22, 0);
        w.solid(34.12f, 40.0f, 36.3f, 41.6f, 1.5f);
        // Floodlights.
        for (float[] l : new float[][]{{38.5f, 36.5f}, {45, 36.5f}, {41.5f, 31.2f}}) {
            w.lamps.add(new float[]{l[0], l[1], 4.5f, 0.75f, 1, 0.95f, 0.85f});
        }
        for (float x : new float[]{36.9f, 47.7f}) {
            m.cylinder(x, 32.4f, 0.07f, 0.07f, 1.05f, 4.6f, 0xFF2A2C30, 0xFF2A2C30, 0, 8);
            m.boxAA(x - 0.3f, 4.4f, 32.3f, x + 0.3f, 4.7f, 32.6f, 0xFF2A2C30, 0);
            m.boxAA(x - 0.26f, 4.4f, 32.62f, x + 0.26f, 4.66f, 32.64f, 0xFFFFF4E0, 1);
        }
    }

    // ---------------------------------------------------------------- spot helpers

    private static void chip(CasinoWorld w, String id, float x, float z, float y) {
        CasinoWorld.Spot s = new CasinoWorld.Spot(CasinoWorld.CHIP, id, chipName(id), x, z);
        s.y = y;
        w.spots.add(s);
    }

    private static void key(CasinoWorld w, String id, String label, float x, float z, float y) {
        CasinoWorld.Spot s = new CasinoWorld.Spot(CasinoWorld.KEY, id, label, x, z);
        s.y = y;
        w.spots.add(s);
    }

    private static void note(CasinoWorld w, String id, String label, float x, float z) {
        CasinoWorld.Spot s = new CasinoWorld.Spot(CasinoWorld.NOTE, id, label, x, z);
        s.text = noteText(id);
        w.spots.add(s);
    }

    private static CasinoWorld.Spot loot(CasinoWorld w, String id, String label, float x,
                                         float z, int pins, int reward) {
        CasinoWorld.Spot s = new CasinoWorld.Spot(CasinoWorld.LOOT, id, label, x, z);
        s.pins = pins;
        s.reward = reward;
        w.spots.add(s);
        return s;
    }

    private static void hide(CasinoWorld w, String label, float x, float z, int capacity) {
        CasinoWorld.Spot s = new CasinoWorld.Spot(CasinoWorld.HIDE, label, label, x, z);
        s.capacity = capacity;
        w.spots.add(s);
    }
}
