package app.easywake;

/**
 * Clothes, bling and gear for your character in the 3D casino. Prices are Fun Coupons
 * (play money). Owned items are saved as "own:<id>" flags; what you wear is one id per slot.
 */
final class CasinoShop {
    private CasinoShop() {}

    static final int SHIRT = 0, JACKET = 1, PANTS = 2, SHOES = 3, HAT = 4, GLASSES = 5,
            CHAIN = 6, WATCH = 7, GEAR = 8;
    static final String[] SLOT_NAMES = {"Shirt", "Jacket", "Pants", "Shoes", "Hat", "Glasses",
            "Chain", "Watch", "Gear"};

    /** Hat looks. */
    static final int FEDORA = 1, CAP = 2, COWBOY = 3, CROWN = 4, BEANIE = 5;

    static final class Item {
        final String id, name, store;
        final int slot, price, color, style;

        Item(String id, String name, String store, int slot, int price, int color, int style) {
            this.id = id;
            this.name = name;
            this.store = store;
            this.slot = slot;
            this.price = price;
            this.color = color;
            this.style = style;
        }
    }

    static final String TAILOR = "tailor", JEWELER = "jeweler", PAWN = "pawn";

    static final Item[] ALL = {
            // Threads: clothes.
            new Item("tee_white", "White tee", TAILOR, SHIRT, 100, 0xFFE8E6E0, 0),
            new Item("tee_black", "Black tee", TAILOR, SHIRT, 100, 0xFF1C1C20, 0),
            new Item("shirt_red", "Red silk shirt", TAILOR, SHIRT, 350, 0xFF9A1420, 0),
            new Item("shirt_hawaii", "Loud vacation shirt", TAILOR, SHIRT, 450, 0xFFE0702A, 0),
            new Item("shirt_purple", "Purple dress shirt", TAILOR, SHIRT, 400, 0xFF5A2A8A, 0),
            new Item("hoodie", "Grey hoodie", TAILOR, SHIRT, 300, 0xFF6A6C72, 0),
            new Item("leather", "Leather jacket", TAILOR, JACKET, 1200, 0xFF1E1612, 0),
            new Item("suit_black", "Black suit", TAILOR, JACKET, 2000, 0xFF16161C, 1),
            new Item("suit_white", "White suit", TAILOR, JACKET, 2500, 0xFFE4E0D6, 1),
            new Item("suit_velvet", "Red velvet suit", TAILOR, JACKET, 4000, 0xFF6A0E18, 1),
            new Item("suit_gold", "Gold lamé suit", TAILOR, JACKET, 9000, 0xFFC8A030, 1),
            new Item("jeans", "Dark jeans", TAILOR, PANTS, 150, 0xFF1E2A44, 0),
            new Item("slacks_white", "White slacks", TAILOR, PANTS, 300, 0xFFD8D4CA, 0),
            new Item("pants_leather", "Leather pants", TAILOR, PANTS, 800, 0xFF141012, 0),
            new Item("sneakers_white", "White sneakers", TAILOR, SHOES, 250, 0xFFF0F0F0, 0),
            new Item("boots", "Snakeskin boots", TAILOR, SHOES, 900, 0xFF6A5232, 0),
            new Item("sneakers_gold", "Gold sneakers", TAILOR, SHOES, 3000, 0xFFE0B030, 1),
            new Item("cap", "Ball cap", TAILOR, HAT, 150, 0xFF1C2A4A, CAP),
            new Item("beanie", "Beanie", TAILOR, HAT, 120, 0xFF2A2A2E, BEANIE),
            new Item("fedora", "Fedora", TAILOR, HAT, 450, 0xFF2A1E16, FEDORA),
            new Item("cowboy", "Cowboy hat", TAILOR, HAT, 600, 0xFF7A5A36, COWBOY),
            // Gold Rush: bling.
            new Item("shades", "Black shades", JEWELER, GLASSES, 300, 0xFF0A0A0C, 0),
            new Item("aviators", "Gold aviators", JEWELER, GLASSES, 900, 0xFFD4A838, 1),
            new Item("chain_gold", "Gold chain", JEWELER, CHAIN, 2500, 0xFFE0B040, 0),
            new Item("chain_cuban", "Thick Cuban link", JEWELER, CHAIN, 6000, 0xFFF0C24A, 1),
            new Item("chain_ice", "Iced-out chain", JEWELER, CHAIN, 15000, 0xFFDCEEFF, 2),
            new Item("watch_gold", "Gold watch", JEWELER, WATCH, 1500, 0xFFE0B040, 0),
            new Item("watch_ice", "Diamond watch", JEWELER, WATCH, 8000, 0xFFDCEEFF, 1),
            new Item("crown", "Solid gold crown", JEWELER, HAT, 25000, 0xFFF0C24A, CROWN),
            // Lucky Pawn: gear that does something.
            new Item("propicks", "Pro lockpick set (picks never snap)", PAWN, GEAR, 1500,
                    0, 0),
            new Item("sneakers_soft", "Soft-sole shoes (people notice you slower)", PAWN,
                    SHOES, 2000, 0xFF2A2420, 2),
    };

    static Item find(String id) {
        if (id == null) return null;
        for (Item i : ALL) {
            if (i.id.equals(id)) return i;
        }
        return null;
    }

    /** What the player's wearing: one item id (or null) per slot, saved as "a,b,,c". */
    static String[] parseWorn(String saved) {
        String[] out = new String[GEAR];
        if (saved == null) return out;
        String[] parts = saved.split(",", -1);
        for (int i = 0; i < out.length && i < parts.length; i++) {
            out[i] = parts[i].isEmpty() ? null : parts[i];
        }
        return out;
    }

    static String saveWorn(String[] worn) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worn.length; i++) {
            if (i > 0) sb.append(',');
            if (worn[i] != null) sb.append(worn[i]);
        }
        return sb.toString();
    }

    /** Puts what's worn onto a person (your own clothes; uniforms override shirt/pants). */
    static void apply(CasinoWorld.Person p, String[] worn, int baseShirt, int basePants) {
        Item shirt = find(worn[SHIRT]), jacket = find(worn[JACKET]), pants = find(worn[PANTS]);
        Item shoes = find(worn[SHOES]), hat = find(worn[HAT]), glasses = find(worn[GLASSES]);
        Item chain = find(worn[CHAIN]), watch = find(worn[WATCH]);
        p.shirt = shirt != null ? shirt.color : baseShirt;
        p.pants = pants != null ? pants.color : basePants;
        p.jacket = jacket != null ? jacket.color : 0;
        p.suit = jacket != null && jacket.style == 1;
        p.shoes = shoes != null ? shoes.color : 0;
        p.shoeGlow = shoes != null && shoes.style == 1;
        p.hatType = hat != null ? hat.style : 0;
        p.hatColor = hat != null ? hat.color : 0;
        p.glasses = glasses != null ? glasses.color : 0;
        p.chain = chain != null ? chain.style + 1 : 0;
        p.chainColor = chain != null ? chain.color : 0;
        p.watch = watch != null ? watch.color : 0;
        if (p.suit) p.tie = true;
    }
}
