package app.easywake;

/**
 * Guns for the 3D world (sold at the Bullseye Range for Fun Coupons). Small ones are
 * concealed until drawn; long guns show on your back and make people nervous.
 */
final class CasinoWeapons {
    private CasinoWeapons() {}

    static final class Gun {
        final String id, name;
        final int price;
        final boolean concealable, auto, melee;
        /** Damage per hit (per pellet), headshot multiplier. People have 100, guards 150. */
        final float damage, headMult;
        /** Seconds between shots, magazine size, reload seconds. */
        final float delay, reload;
        final int mag, pellets;
        /** Random spread in radians, max range in metres, field of view when aiming. */
        final float spread, range, fov;
        /** How loud: 0 silent .. 1 everyone within ~28 m hears it. */
        final float loud;
        /** Model: length of the barrel box, and whether it's held two-handed. */
        final float length;
        final boolean twoHand;

        Gun(String id, String name, int price, boolean concealable, boolean auto,
            float damage, float headMult, float delay, int mag, float reload, int pellets,
            float spread, float range, float fov, float loud, float length, boolean twoHand) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.concealable = concealable;
            this.auto = auto;
            this.melee = range < 2;
            this.damage = damage;
            this.headMult = headMult;
            this.delay = delay;
            this.mag = mag;
            this.reload = reload;
            this.pellets = pellets;
            this.spread = spread;
            this.range = range;
            this.fov = fov;
            this.loud = loud;
            this.length = length;
            this.twoHand = twoHand;
        }
    }

    static final Gun KNIFE = new Gun("knife", "Switchblade", 400, true, false, 120, 1, 0.5f,
            1, 0, 1, 0, 1.5f, 62, 0, 0.18f, false);
    static final Gun PISTOL = new Gun("pistol", "Quiet pistol", 750, true, false, 55, 3, 0.3f,
            12, 1.3f, 1, 0.012f, 40, 46, 0.4f, 0.22f, false);
    static final Gun REVOLVER = new Gun("revolver", ".44 Revolver", 1200, true, false, 105, 2.5f,
            0.65f, 6, 2.1f, 1, 0.006f, 50, 42, 1, 0.3f, false);
    static final Gun SMG = new Gun("smg", "Compact SMG", 4000, false, true, 26, 2.5f, 0.085f,
            30, 1.8f, 1, 0.03f, 35, 50, 0.9f, 0.42f, true);
    static final Gun SHOTGUN = new Gun("shotgun", "Pump shotgun", 3500, false, false, 20, 1.5f,
            0.85f, 6, 2.6f, 8, 0.075f, 16, 52, 1, 0.75f, true);
    static final Gun SNIPER = new Gun("sniper", "Bolt-action rifle", 6000, false, false, 210, 3,
            1.3f, 5, 2.5f, 1, 0, 90, 16, 1, 0.95f, true);

    static final Gun[] ALL = {KNIFE, PISTOL, REVOLVER, SMG, SHOTGUN, SNIPER};

    /** The pistol's silencer, sold separately. */
    static final String SILENCER = "silencer";
    static final int SILENCER_PRICE = 1000;

    static Gun find(String id) {
        if (id == null) return null;
        for (Gun g : ALL) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }
}
