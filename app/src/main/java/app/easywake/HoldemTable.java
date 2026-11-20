package app.easywake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A no-limit Texas Hold'em table for up to six players: blinds, betting rounds, all-ins,
 * side pots and the showdown. No Android code here, so whole games can be tested at once.
 * Chips are Fun Coupons; nothing here touches the wallet.
 */
final class HoldemTable {
    static final int SEATS = 6;
    static final int FOLD = 0, CHECK = 1, CALL = 2, RAISE = 3;

    static final class Seat {
        boolean occupied;
        boolean human;
        /** Which character sits here (see HoldemBot), or -1 for you. */
        int persona = -1;
        String name = "";
        long stack;
        int c1 = -1;
        int c2 = -1;
        boolean inHand;
        boolean folded;
        boolean allIn;
        boolean acted;
        /** Put in on this betting round. */
        long bet;
        /** Put in during this whole hand. */
        long total;
        long startStack;
        /** How this player last acted, for the table to show. */
        String lastAction = "";
        /** Bad losses make a character play wilder for a while. */
        float tilt;
        /** True if this seat's cards were turned face up at the showdown. */
        boolean shown;
    }

    static final class Win {
        int seat;
        long amount;
        String hand = "";
    }

    final Seat[] seats = new Seat[SEATS];
    final Random rnd;
    int button = -1;
    long sb;
    long bb;
    final int[] board = new int[5];
    /** Board cards face up: 0, 3, 4 or 5. */
    int shown;
    /** 0 pre-flop, 1 flop, 2 turn, 3 river, 4 hand over. */
    int street = 4;
    long currentBet;
    long minRaise;
    /** Whose turn it is, or -1 if no one acts (between rounds or the hand is over). */
    int toAct = -1;
    boolean handLive;
    /** True when everyone left is all in and the rest of the board just needs dealing. */
    boolean runningOut;
    boolean wentToShowdown;
    long potAtEnd;
    final List<Win> wins = new ArrayList<>();
    /** Who made the last bet or raise this hand, for the table talk. */
    int aggressor = -1;
    /** Bets and raises so far this round (the big blind counts as one before the flop). */
    int raises;
    private List<Integer> deck = new ArrayList<>();
    private int handCount;

    HoldemTable(Random rnd) {
        this.rnd = rnd;
        for (int i = 0; i < SEATS; i++) seats[i] = new Seat();
    }

    // ------------------------------------------------------------------ simple queries

    long pot() {
        long p = 0;
        for (Seat s : seats) p += s.total;
        return p;
    }

    /** Players still in the hand (not folded). */
    int contenders() {
        int n = 0;
        for (Seat s : seats) {
            if (s.inHand && !s.folded) n++;
        }
        return n;
    }

    /** Players still in the hand who can still bet. */
    int canAct() {
        int n = 0;
        for (Seat s : seats) {
            if (s.inHand && !s.folded && !s.allIn) n++;
        }
        return n;
    }

    long toCall(int i) {
        Seat s = seats[i];
        return Math.min(currentBet - s.bet, s.stack);
    }

    long minRaiseTo(int i) {
        Seat s = seats[i];
        return Math.min(currentBet + minRaise, s.bet + s.stack);
    }

    long maxRaiseTo(int i) {
        Seat s = seats[i];
        return s.bet + s.stack;
    }

    /** Raising only makes sense with chips beyond the call and someone left to bet against. */
    boolean canRaise(int i) {
        Seat s = seats[i];
        if (s.stack <= currentBet - s.bet) return false;
        for (int k = 0; k < SEATS; k++) {
            Seat o = seats[k];
            if (k != i && o.inHand && !o.folded && !o.allIn) return true;
        }
        return false;
    }

    int seatedWithChips() {
        int n = 0;
        for (Seat s : seats) {
            if (s.occupied && s.stack > 0) n++;
        }
        return n;
    }

    private int nextSeat(int from, boolean needChipsToStart) {
        for (int k = 1; k <= SEATS; k++) {
            int i = (from + k + SEATS) % SEATS;
            Seat s = seats[i];
            if (needChipsToStart ? (s.occupied && s.stack > 0) : s.inHand) return i;
        }
        return -1;
    }

    /** The next player after seat i who still needs to act this round, or -1. */
    private int nextToAct(int from) {
        for (int k = 1; k <= SEATS; k++) {
            int i = (from + k) % SEATS;
            Seat s = seats[i];
            if (!s.inHand || s.folded || s.allIn) continue;
            if (!s.acted || s.bet < currentBet) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ a hand

    /** Starts a hand. Returns false if fewer than two players have chips. */
    boolean startHand() {
        if (seatedWithChips() < 2) return false;
        handCount++;
        wins.clear();
        wentToShowdown = false;
        runningOut = false;
        aggressor = -1;
        for (Seat s : seats) {
            s.inHand = s.occupied && s.stack > 0;
            s.folded = false;
            s.allIn = false;
            s.acted = false;
            s.bet = 0;
            s.total = 0;
            s.shown = false;
            s.lastAction = "";
            s.startStack = s.stack;
            s.c1 = -1;
            s.c2 = -1;
        }
        button = nextSeat(button < 0 ? rnd.nextInt(SEATS) : button, true);
        deck = new ArrayList<>();
        for (int c = 0; c < 52; c++) deck.add(c);
        Collections.shuffle(deck, rnd);
        // Deal two cards each, one at a time, starting left of the button.
        for (int round = 0; round < 2; round++) {
            int i = button;
            for (int k = 0; k < SEATS; k++) {
                i = (i + 1) % SEATS;
                if (!seats[i].inHand) continue;
                if (round == 0) seats[i].c1 = deck.remove(deck.size() - 1);
                else seats[i].c2 = deck.remove(deck.size() - 1);
            }
        }
        for (int i = 0; i < 5; i++) board[i] = deck.remove(deck.size() - 1);
        shown = 0;
        street = 0;
        currentBet = 0;
        minRaise = bb;
        handLive = true;
        raises = 1;

        int players = 0;
        for (Seat s : seats) {
            if (s.inHand) players++;
        }
        // Heads up the button is the small blind; otherwise the next two seats post.
        int sbSeat = players == 2 ? button : nextSeat(button, false);
        int bbSeat = nextSeat(sbSeat, false);
        post(sbSeat, sb, "Small blind");
        post(bbSeat, bb, "Big blind");
        currentBet = Math.max(seats[sbSeat].bet, seats[bbSeat].bet);
        currentBet = Math.max(currentBet, bb);
        toAct = nextToAct(bbSeat);
        if (toAct < 0) checkRoundEnd(bbSeat);
        return true;
    }

    private void post(int i, long amount, String label) {
        Seat s = seats[i];
        long put = Math.min(amount, s.stack);
        s.stack -= put;
        s.bet += put;
        s.total += put;
        if (s.stack == 0) s.allIn = true;
        s.lastAction = label;
    }

    /**
     * A player acts. For RAISE, amountTo is the total this player's bet becomes this round
     * (it's kept between the smallest and largest allowed raise).
     */
    void act(int i, int type, long amountTo) {
        if (!handLive || i != toAct) return;
        Seat s = seats[i];
        long owe = currentBet - s.bet;
        if (type == CHECK && owe > 0) type = CALL;
        if (type == CALL && owe <= 0) type = CHECK;
        if (type == RAISE && !canRaise(i)) type = owe > 0 ? CALL : CHECK;
        switch (type) {
            case FOLD:
                if (owe <= 0) {
                    // Folding when you can check for free is just a check.
                    s.lastAction = "Check";
                } else {
                    s.folded = true;
                    s.lastAction = "Fold";
                }
                break;
            case CHECK:
                s.lastAction = "Check";
                break;
            case CALL: {
                long put = Math.min(owe, s.stack);
                pay(s, put);
                s.lastAction = s.allIn ? "All in " + s.bet : "Call " + put;
                break;
            }
            default: {
                long to = Math.max(minRaiseTo(i), Math.min(maxRaiseTo(i), amountTo));
                boolean opening = currentBet == 0 || (street == 0 && currentBet <= bb
                        && aggressor < 0);
                pay(s, to - s.bet);
                long raiseBy = s.bet - currentBet;
                if (raiseBy > 0) {
                    if (raiseBy >= minRaise) minRaise = raiseBy;
                    currentBet = s.bet;
                    aggressor = i;
                    raises++;
                    for (int k = 0; k < SEATS; k++) {
                        if (k != i) seats[k].acted = false;
                    }
                }
                s.lastAction = s.allIn ? "All in " + s.bet : (opening && street > 0
                        ? "Bet " + s.bet : "Raise to " + s.bet);
                break;
            }
        }
        s.acted = true;
        if (contenders() == 1) {
            finishByFold();
            return;
        }
        int nxt = nextToAct(i);
        if (nxt >= 0) {
            toAct = nxt;
        } else {
            checkRoundEnd(i);
        }
    }

    private void pay(Seat s, long amount) {
        long put = Math.min(amount, s.stack);
        s.stack -= put;
        s.bet += put;
        s.total += put;
        if (s.stack == 0) s.allIn = true;
    }

    /** The betting round is over: deal on, or run the board out if no one can bet. */
    private void checkRoundEnd(int last) {
        toAct = -1;
        if (canAct() <= 1) {
            // At most one player can still bet; anyone owing has acted. Deal the rest.
            boolean owing = false;
            for (int k = 0; k < SEATS; k++) {
                Seat o = seats[k];
                if (o.inHand && !o.folded && !o.allIn && o.bet < currentBet) owing = true;
            }
            if (!owing) {
                runningOut = true;
                return;
            }
        }
        nextStreet();
    }

    /** Deals the next street, or goes to the showdown after the river. */
    void nextStreet() {
        for (Seat s : seats) {
            s.bet = 0;
            s.acted = false;
            if (s.inHand && !s.folded && !s.allIn) s.lastAction = "";
        }
        currentBet = 0;
        minRaise = bb;
        raises = 0;
        if (street >= 3) {
            showdown();
            return;
        }
        street++;
        shown = street == 1 ? 3 : street + 2;
        if (canAct() <= 1) {
            runningOut = true;
            toAct = -1;
            return;
        }
        toAct = nextToAct(button);
        if (toAct < 0) {
            runningOut = true;
        }
    }

    /** While running out, deals one more street (call until the hand is over). */
    void runOutStep() {
        if (!handLive) return;
        runningOut = false;
        nextStreet();
        if (handLive && toAct < 0 && !runningOut) runningOut = true;
    }

    private void finishByFold() {
        int w = -1;
        for (int k = 0; k < SEATS; k++) {
            if (seats[k].inHand && !seats[k].folded) w = k;
        }
        potAtEnd = pot();
        Win win = new Win();
        win.seat = w;
        win.amount = potAtEnd;
        wins.add(win);
        seats[w].stack += potAtEnd;
        end();
    }

    private void end() {
        handLive = false;
        street = 4;
        toAct = -1;
        runningOut = false;
        for (Seat s : seats) s.bet = 0;
    }

    int[] seven(int i) {
        Seat s = seats[i];
        return new int[]{s.c1, s.c2, board[0], board[1], board[2], board[3], board[4]};
    }

    /** Pays the main pot and any side pots to the best hands that can win them. */
    private void showdown() {
        shown = 5;
        wentToShowdown = true;
        potAtEnd = pot();
        long[] score = new long[SEATS];
        for (int k = 0; k < SEATS; k++) {
            Seat s = seats[k];
            if (s.inHand && !s.folded) {
                score[k] = Cards.score(seven(k));
                s.shown = true;
            }
        }
        long[] won = new long[SEATS];
        long[] totals = new long[SEATS];
        for (int k = 0; k < SEATS; k++) totals[k] = seats[k].total;
        // Pot by pot, from the smallest all-in up: each layer is won by the best hand
        // among the players who put in at least that much.
        while (true) {
            long level = Long.MAX_VALUE;
            for (int k = 0; k < SEATS; k++) {
                if (totals[k] > 0 && seats[k].inHand && !seats[k].folded) {
                    level = Math.min(level, totals[k]);
                }
            }
            if (level == Long.MAX_VALUE) break;
            List<Integer> eligible = new ArrayList<>();
            for (int k = 0; k < SEATS; k++) {
                if (seats[k].inHand && !seats[k].folded && totals[k] >= level) eligible.add(k);
            }
            long layer = 0;
            for (int k = 0; k < SEATS; k++) {
                long take = Math.min(totals[k], level);
                layer += take;
                totals[k] -= take;
            }
            long best = -1;
            for (int k : eligible) best = Math.max(best, score[k]);
            List<Integer> winners = new ArrayList<>();
            for (int k : eligible) {
                if (score[k] == best) winners.add(k);
            }
            long share = layer / winners.size();
            long odd = layer - share * winners.size();
            for (int k : winners) won[k] += share;
            // Odd chips go to the first winner left of the button.
            for (int step = 1; step <= SEATS && odd > 0; step++) {
                int k = (button + step) % SEATS;
                if (winners.contains(k)) {
                    won[k] += odd;
                    odd = 0;
                }
            }
        }
        // Chips from folded players left over above everyone's level (can't happen when
        // the last caller covers them, but make sure nothing is lost).
        long leftover = 0;
        for (int k = 0; k < SEATS; k++) leftover += totals[k];
        if (leftover > 0) {
            long best = -1;
            int who = -1;
            for (int k = 0; k < SEATS; k++) {
                if (seats[k].inHand && !seats[k].folded && score[k] > best) {
                    best = score[k];
                    who = k;
                }
            }
            if (who >= 0) won[who] += leftover;
        }
        for (int k = 0; k < SEATS; k++) {
            if (won[k] > 0) {
                seats[k].stack += won[k];
                Win w = new Win();
                w.seat = k;
                w.amount = won[k];
                w.hand = Cards.handName(score[k]);
                wins.add(w);
            }
        }
        end();
    }

    /** Hands played at this table so far. */
    int hands() {
        return handCount;
    }
}
