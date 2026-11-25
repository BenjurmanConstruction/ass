package app.easywake;

import static app.easywake.CasinoWorld.EAST;
import static app.easywake.CasinoWorld.NORTH;
import static app.easywake.CasinoWorld.SOUTH;
import static app.easywake.CasinoWorld.WEST;

/**
 * Contracts for the walk-around casino: who the target is, the loop they walk every day,
 * and what the job pays. Payouts are Fun Coupons, play money only.
 */
final class CasinoMissions {
    private CasinoMissions() {}

    static final float N = Float.NaN;

    static final class Contract {
        final String id, name, title, brief;
        final int pay;
        /** Stops: x, z, seconds to wait, facing (NaN any), sit (1). */
        final float[][] route;
        int shirt, pants, hair = 0xFF1A120C;
        boolean hat;
        float scale = 1;
        String[] lines;
        /** A bodyguard's loop, or null for none. */
        float[][] guardRoute;
        /** Ways to do the job. */
        String tips;

        Contract(String id, String name, String title, int pay, String brief, float[][] route) {
            this.id = id;
            this.name = name;
            this.title = title;
            this.pay = pay;
            this.brief = brief;
            this.route = route;
        }
    }

    static final Contract[] ALL;

    static {
        Contract loan;
        loan = new Contract("loan", "Vinnie Marrow", "The Loan Shark", 1500,
                "Runs a loan racket out of the High Limit Room behind the staff door. Steps "
                        + "out to the corridor to take calls, and the door opens for him. "
                        + "The GM keeps a key card for that room in his desk. Watch the "
                        + "bodyguard.",
                new float[][]{
                        {-7.0f, -20.2f, 14, WEST, 1}, {-9.6f, -19.0f, 0, N, 0},
                        {-13, -22.3f, 10, NORTH, 0},
                        {-15.5f, -22.3f, 8, NORTH, 0}, {-9, -18.4f, 1, N, 0},
                        {-9, -15.6f, 9, SOUTH, 0}, {-9, -18.4f, 1, N, 0}});
        loan.shirt = 0xFF2A2A36;
        loan.pants = 0xFF1A1A22;
        loan.scale = 1.08f;
        loan.lines = new String[]{"You're late on the vig, pal.", "Nobody walks on Vinnie.",
                "Somebody get me an espresso.", "Tell him I'm being patient. For now."};
        loan.tips = "• The High Limit door takes the GM's key card (his desk, office lock is "
                + "4 pins), or just walk through in a security uniform.\n• Vinnie steps out "
                + "to the corridor to take calls. The supply closet there holds two bodies.\n"
                + "• Drop the bodyguard first, from behind.";
        loan.guardRoute = new float[][]{{-11, -19.2f, 6, NORTH, 0}, {-16, -19.2f, 6, NORTH, 0}};

        Contract gm = new Contract("gm", "Richard Vance", "The Crooked GM", 1200,
                "The general manager skims the cashier's cage. Sits in his office behind a "
                        + "four-pin lock, then walks the floor to the cage and back. Staff "
                        + "uniforms get you into the corridor without questions.",
                new float[][]{
                        {14, -22.9f, 16, SOUTH, 1}, {15.7f, -22.9f, 0, N, 0},
                        {15.7f, -20.6f, 0, N, 0}, {14, -19.5f, 0, N, 0}, {14, -15.6f, 0, N, 0},
                        {15.8f, -15.6f, 0, N, 0}, {15.8f, -12.6f, 0, N, 0}, {13, -9, 0, N, 0},
                        {12.5f, 4.5f, 0, N, 0}, {-6, 5.8f, 0, N, 0},
                        {-12.5f, 7.6f, 12, WEST, 0}, {-6, 5.8f, 0, N, 0}, {12.5f, 4.5f, 0, N, 0},
                        {13, -9, 0, N, 0}, {15.8f, -12.6f, 0, N, 0}, {15.8f, -15.6f, 0, N, 0},
                        {14, -15.6f, 0, N, 0}, {14, -19.5f, 0, N, 0}, {15.7f, -20.6f, 0, N, 0},
                        {15.7f, -22.9f, 0, N, 0}});
        gm.shirt = 0xFF3A3A44;
        gm.pants = 0xFF22222A;
        gm.hair = 0xFF9A9A9A;
        gm.tips = "• Take a dealer's uniform (knock one out from behind): staff walk "
                + "through the Staff Only door without picking it.\n• He walks out to the "
                + "cashier cage on the casino floor and back. Lots of eyes out there; get him "
                + "in the corridor or his office.\n• Security uniforms open his office.";
        gm.lines = new String[]{"Count it again.", "The house always wins. I'm the house.",
                "Who touched my key card?", "Smile. Cameras."};

        Contract counter = new Contract("counter", "Dexter Pike", "The Card Counter", 800,
                "Counts cards at blackjack, then crosses the street to the Midnight Diner "
                        + "and back. Never looks both ways. A shove at the wrong moment "
                        + "could look like an accident.",
                new float[][]{
                        {-0.34f, 3.76f, 20, -0.38f, 1}, {0.2f, 8, 0, N, 0}, {0, 13.3f, 0, N, 0},
                        {-2, 16.3f, 0, N, 0}, {-14, 17.4f, 0, N, 0}, {-14, 26.6f, 0, N, 0},
                        {-14, 30.8f, 0, N, 0}, {-15, 36.8f, 18, SOUTH, 1},
                        {-14, 30.8f, 0, N, 0}, {-14, 26.6f, 0, N, 0}, {-14, 17.4f, 0, N, 0},
                        {-2, 16.3f, 0, N, 0}, {0, 13.3f, 0, N, 0}, {0.2f, 8, 0, N, 0}});
        counter.shirt = 0xFF5A6A4A;
        counter.pants = 0xFF3A3328;
        counter.hat = true;
        counter.tips = "• He crosses the street at the west crosswalk. Punch him into the road "
                + "when a car's coming and it's an accident: +25%.\n• Or wait at the diner; "
                + "the walk-in freezer behind the counter hides bodies.\n• Blackjack is "
                + "crowded. Don't do it there.";
        counter.lines = new String[]{"True count plus four.", "I'm not counting, I'm just good.",
                "Coffee. Black. Like my odds.", "Six decks? Cute."};

        Contract fixer = new Contract("fixer", "Nadia Kroll", "The Fixer", 1000,
                "Rents room 7 at the Starlite. Crosses to the alley behind the casino for a "
                        + "smoke, back to the brick, then goes home. Quiet, careful, armed.",
                new float[][]{
                        {24.5f, 34.5f, 15, N, 0}, {24, 31.2f, 0, N, 0}, {24, 28.6f, 0, N, 0},
                        {22, 26.6f, 0, N, 0}, {22, 17.4f, 0, N, 0}, {22, 13.5f, 0, N, 0},
                        {24.9f, -11.5f, 22, EAST, 0}, {22, 13.5f, 0, N, 0}, {22, 17.4f, 0, N, 0},
                        {22, 26.6f, 0, N, 0}, {24, 28.6f, 0, N, 0}, {24, 31.2f, 0, N, 0}});
        fixer.shirt = 0xFF2A1A1A;
        fixer.pants = 0xFF1A1A1E;
        fixer.hair = 0xFF6A1A1A;
        fixer.scale = 0.95f;
        fixer.tips = "• She smokes alone by the graffiti in the back alley for about twenty "
                + "seconds. There's a dumpster right there.\n• The casino's back door is a "
                + "5-pin lock; a staff uniform opens it. Or walk around past the bar.\n• "
                + "Room 7 at the Starlite (4 pins) has a bathtub for bodies.";
        fixer.lines = new String[]{"Smoke break. Don't talk to me.", "Room seven's not for rent.",
                "You didn't see me.", "Keep walking."};

        ALL = new Contract[]{counter, fixer, gm, loan};
    }

    static Contract find(String id) {
        for (Contract c : ALL) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    /**
     * What a finished job pays. Repeats pay less; a clean job pays a bonus, an accident a
     * smaller one, and every bystander killed cuts the fee.
     */
    static int payout(Contract c, int timesDone, boolean silent, boolean accident,
                      int bystanders) {
        double base = Math.max(c.pay / 4.0, c.pay / (1.0 + timesDone));
        double mult = 1 + (silent ? 0.5 : 0) + (accident ? 0.25 : 0) - 0.2 * bystanders;
        return (int) Math.round(base * Math.max(0.25, mult));
    }

    static String rating(boolean silent, boolean spotted, int bystanders) {
        if (silent) return "Silent Assassin";
        if (bystanders >= 3) return "Bloodbath";
        if (bystanders > 0) return "Messy";
        if (spotted) return "Loud";
        return "Professional";
    }

    static boolean accident(int how) {
        return how == CasinoWorld.KILL_CAR || how == CasinoWorld.KILL_PUSH;
    }
}
