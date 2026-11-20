package app.easywake;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The characters you play Hold'em against: how each one plays, and what each one says.
 * They only ever see their own cards and the board; they decide by estimating how often
 * their hand wins, then bend that through their personality. No Android code here.
 */
final class HoldemBot {
    static final String[] NAMES = {"Rusty", "Big Tony", "Chad", "Grandma Mae", "Dee",
            "Lucky Lou", "Professor Fitz", "Jolene"};
    static final String[] FACES = {"🤠", "😤", "😎", "👵", "🦈", "🍀", "🤓", "💅"};
    static final String[] STYLES = {
            "Old grinder. Plays tight, rarely bluffs, never shuts up about the old days.",
            "Loud, wild, bets huge and bluffs a lot. Gets worse when he loses.",
            "Crypto bro. Plays too many hands and thinks every pot is going to the moon.",
            "Sweet old lady. Waits for great cards and roasts you while she does.",
            "The shark. Solid, aggressive, cold as ice.",
            "Calls everything. Swears his lucky charm will save him.",
            "Quotes the odds at you. Plays by the numbers and lets you know it.",
            "Trucker with a mouth on her. Tricky, loves a bluff."};

    /** How good a hand must be to bet for value (1 = an average hand at the table). */
    private static final float[] VALUE = {1.5f, 1.15f, 1.2f, 1.75f, 1.35f, 1.6f, 1.45f, 1.3f};
    /** How often they bluff when it's checked to them. */
    private static final float[] BLUFF = {0.07f, 0.35f, 0.24f, 0.04f, 0.16f, 0.05f, 0.10f,
            0.24f};
    /** How readily they call: below 1 calls lighter than the price says, above 1 tighter. */
    private static final float[] CALL = {1.0f, 0.78f, 0.8f, 1.15f, 0.95f, 0.55f, 1.0f, 0.85f};
    /**
     * Before the flop: the one-on-one winning chance a hand needs to be played at all
     * (aces are about 0.85, seven-deuce about 0.35, an average hand 0.50).
     */
    private static final float[] PREFLOP = {0.575f, 0.50f, 0.51f, 0.615f, 0.555f, 0.48f,
            0.57f, 0.53f};
    /** How strong a hand must be to raise when facing a bet. */
    private static final float[] RERAISE = {1.9f, 1.45f, 1.6f, 2.1f, 1.65f, 2.4f, 1.8f, 1.6f};
    /** How likely they are to raise when they could. */
    private static final float[] AGGR = {0.55f, 0.95f, 0.8f, 0.5f, 0.85f, 0.3f, 0.65f, 0.8f};
    /** Bet size as a share of the pot. */
    private static final float[] SIZE = {0.6f, 1.1f, 0.85f, 0.55f, 0.7f, 0.5f, 0.66f, 0.8f};

    private HoldemBot() {
    }

    static final class Move {
        int type;
        long to;
        /** Something to say while making this move, or null. */
        String say;
    }

    // ------------------------------------------------------------------ playing

    /**
     * How often seat i's hand would win against the players still in (or against
     * forceOpps random hands, if that's above 0), by dealing the rest out many times.
     */
    static float equity(HoldemTable t, int i, Random rnd, int trials, int forceOpps) {
        HoldemTable.Seat me = t.seats[i];
        int opps = forceOpps > 0 ? forceOpps : t.contenders() - 1;
        if (opps <= 0) return 1f;
        List<Integer> unseen = new ArrayList<>();
        for (int c = 0; c < 52; c++) {
            boolean known = c == me.c1 || c == me.c2;
            for (int b = 0; b < t.shown; b++) known |= c == t.board[b];
            if (!known) unseen.add(c);
        }
        int[] mine = new int[7];
        int[] theirs = new int[7];
        float wins = 0;
        int need = opps * 2 + (5 - t.shown);
        for (int n = 0; n < trials; n++) {
            for (int k = 0; k < need; k++) {
                int j = k + rnd.nextInt(unseen.size() - k);
                Integer tmp = unseen.get(k);
                unseen.set(k, unseen.get(j));
                unseen.set(j, tmp);
            }
            int[] bd = new int[5];
            for (int b = 0; b < 5; b++) {
                bd[b] = b < t.shown ? t.board[b] : unseen.get(opps * 2 + b - t.shown);
            }
            mine[0] = me.c1;
            mine[1] = me.c2;
            System.arraycopy(bd, 0, mine, 2, 5);
            long my = Cards.score(mine);
            boolean beaten = false;
            int ties = 0;
            for (int o = 0; o < opps && !beaten; o++) {
                theirs[0] = unseen.get(2 * o);
                theirs[1] = unseen.get(2 * o + 1);
                System.arraycopy(bd, 0, theirs, 2, 5);
                long th = Cards.score(theirs);
                if (th > my) beaten = true;
                else if (th == my) ties++;
            }
            if (!beaten) wins += 1f / (1 + ties);
        }
        return wins / trials;
    }

    static Move decide(HoldemTable t, int i, Random rnd, int trials) {
        HoldemTable.Seat s = t.seats[i];
        int p = Math.max(0, Math.min(NAMES.length - 1, s.persona));
        float tilt = Math.min(1f, s.tilt);
        long owe = t.currentBet - s.bet;
        long pot = t.pot();
        boolean canRaise = t.canRaise(i);
        int players = t.contenders();
        Move m = new Move();
        float noise = (rnd.nextFloat() - 0.5f) * 0.04f;

        if (t.street == 0) {
            // Before the flop: judge the two cards one-on-one, against this character's
            // standard. Each raise already made asks for a better hand.
            float hu = equity(t, i, rnd, trials, 1);
            float need = PREFLOP[p] - tilt * 0.06f + noise;
            float raiseNeed = need + 0.07f;
            if (t.raises >= 2) {
                need += 0.04f * (t.raises - 1);
                raiseNeed = Math.max(need + 0.06f, 0.64f + 0.03f * (t.raises - 2)
                        - (p == 1 ? 0.06f : 0));
            }
            if (owe <= 0) {
                // Checked to us (the big blind): raise good hands, otherwise check.
                if (canRaise && (hu >= raiseNeed || rnd.nextFloat() < BLUFF[p] * 0.3f)) {
                    m.type = HoldemTable.RAISE;
                    m.to = betSize(t, i, p, pot, rnd, 0);
                } else {
                    m.type = HoldemTable.CHECK;
                }
            } else if (canRaise && hu >= raiseNeed && rnd.nextFloat() < 0.4f + AGGR[p] * 0.6f) {
                m.type = HoldemTable.RAISE;
                m.to = t.raises <= 1 ? betSize(t, i, p, pot, rnd, 0)
                        : raiseSize(t, i, p, pot, owe, rnd);
            } else if (hu >= need || (owe <= t.bb / 2 && hu >= need - 0.05f)) {
                m.type = HoldemTable.CALL;
            } else {
                m.type = HoldemTable.FOLD;
            }
            return finish(t, i, m, hu * 2, rnd);
        }

        // After the flop: how often this hand beats everyone still in.
        float eq = equity(t, i, rnd, trials, -1);
        float strength = eq * players;
        float value = VALUE[p] - tilt * 0.3f + noise * 5;
        float bluff = (BLUFF[p] + tilt * 0.2f) * (players <= 2 ? 1f : 0.4f);
        // Raise wars: after two bets this round, only big hands go again.
        boolean war = t.raises >= 2;
        if (owe <= 0) {
            if (canRaise && (strength > value || (!war && rnd.nextFloat() < bluff))) {
                m.type = HoldemTable.RAISE;
                m.to = betSize(t, i, p, pot, rnd, strength);
            } else {
                m.type = HoldemTable.CHECK;
            }
            return finish(t, i, m, strength, rnd);
        }
        float price = owe / (float) (pot + owe);
        boolean play = eq >= price * (CALL[p] - tilt * 0.2f) + noise;
        float reraise = RERAISE[p] - tilt * 0.3f + (war ? 0.6f * (t.raises - 1) : 0);
        if (canRaise && strength > reraise && rnd.nextFloat() < AGGR[p]) {
            m.type = HoldemTable.RAISE;
            m.to = raiseSize(t, i, p, pot, owe, rnd);
        } else if (canRaise && !war && rnd.nextFloat() < bluff * 0.2f) {
            m.type = HoldemTable.RAISE;
            m.to = raiseSize(t, i, p, pot, owe, rnd);
        } else if (play) {
            m.type = HoldemTable.CALL;
        } else {
            m.type = HoldemTable.FOLD;
        }
        return finish(t, i, m, strength, rnd);
    }

    private static long betSize(HoldemTable t, int i, int p, long pot, Random rnd,
                                float strength) {
        if (t.street == 0) {
            // Open raise: about 3 big blinds, more from the wild ones.
            float x = (p == 1 ? 4.5f : (p == 2 || p == 7 ? 3.5f : 2.8f)) + rnd.nextFloat();
            return t.currentBet + Math.round(t.bb * x);
        }
        float frac = SIZE[p] * (0.8f + rnd.nextFloat() * 0.5f);
        return Math.max(t.bb, Math.round(pot * frac));
    }

    private static long raiseSize(HoldemTable t, int i, int p, long pot, long owe, Random rnd) {
        if (t.street == 0) {
            return Math.round(t.currentBet * (2.7f + rnd.nextFloat() * (p == 1 ? 1.5f : 0.6f)));
        }
        float frac = SIZE[p] * (0.9f + rnd.nextFloat() * 0.4f);
        return t.currentBet + Math.max(t.minRaise, Math.round((pot + owe) * frac));
    }

    /** Rounds a raise up to all in when it would commit most of the stack anyway. */
    private static Move finish(HoldemTable t, int i, Move m, float strength, Random rnd) {
        if (m.type != HoldemTable.RAISE) return m;
        HoldemTable.Seat s = t.seats[i];
        long max = t.maxRaiseTo(i);
        // Go all in only when the raise would leave next to nothing behind anyway.
        if (max - m.to < t.pot() * 0.35 || m.to >= max * 0.85) m.to = max;
        m.to = Math.max(t.minRaiseTo(i), Math.min(max, m.to));
        return m;
    }

    // ------------------------------------------------------------------ talking

    static final int JOIN = 0, GLOAT = 1, GLOAT_BIG = 2, BLUFF_WIN = 3, SALTY = 4,
            BAD_BEAT = 5, YOU_FOLDED = 6, YOU_RAISED = 7, SHOVE = 8, BUST = 9, IDLE = 10,
            UP_BIG = 11, ROAST = 12, REPLY_NICE = 13, REPLY_BLUFF = 14, REPLY_SHIP = 15,
            REPLY_CRY = 16, REPLY_WRECK = 17, BEAT_YOU = 18;

    /**
     * What each character says. Lines use {you} for you, {them} for another player, and
     * {amt} for coupons.
     */
    private static final String[][][] LINES = {
            // 0 Rusty: old grinder
            {
                    {"Pull up a chair. Don't touch my lucky ashtray.",
                            "Been playing this game since before your daddy shaved."},
                    {"That's how we did it back in '82.", "Slow and steady takes the pot, son.",
                            "Thank you kindly. I'll stack these right here.",
                            "Read you like the Sunday paper."},
                    {"Now THAT'S a pot! Ain't felt this good since Reagan.",
                            "I'm gonna frame this one and hang it in the shed.",
                            "Whoo! Somebody call my wife, Rusty's eatin' steak tonight!"},
                    {"You'll never know what I had. Never.",
                            "Fifty years of poker and you still fold to an old man."},
                    {"Well, shoot.", "Lucky. Pure dumb luck.",
                            "In my day you'd get run out of town for a call like that."},
                    {"Are you KIDDING me? Runner runner?! I've seen it all now.",
                            "That river's got it out for me. Always has."},
                    {"Smart. Real smart. Run along now.", "Heh. Knew you didn't have it.",
                            "That's what I thought."},
                    {"Easy there, cowboy.", "Ooh, a raise. You trying to scare an old man?",
                            "Hm. Either you got it or you're dumber than you look."},
                    {"All my chips, right in the middle. Your move, kid.",
                            "Shove. Let's see that backbone."},
                    {"Bah. I'm going to the buffet. This table's cursed.",
                            "Busted. Don't you dare tell my wife."},
                    {"Deal the cards, I ain't getting any younger.",
                            "Y'all play slower than a Sunday driver.",
                            "I've folded better hands than you've ever held."},
                    {"Look at this stack. Retirement fund's lookin' healthy.",
                            "I've got more chips than patience, and I got a LOT of chips.",
                            "Old man's runnin' this table, sorry kids."},
                    {"Sorry {them}, school's in session.", "Get off my lawn, {them}.",
                            "{them}, that was adorable. Wrong, but adorable."},
                    {"Well thank you. I am pretty great.", "Flattery won't get my chips."},
                    {"Bluffing? At my age I don't have time to bluff.",
                            "Pay to see 'em, kid."},
                    {"Ship it where? It's already in my stack.",
                            "Big talk for a small stack."},
                    {"I ain't crying, I'm sweating. It's hot in here.",
                            "Only thing crying is your wallet."},
                    {"Wrecked? I've been wrecked since '74. Still here.",
                            "Watch your mouth, whippersnapper."},
                    {"Thanks for the donation, {you}. Real generous.",
                            "Put that one on my tab, kid. Oh wait, I won.",
                            "You played that like my grandkid plays checkers."},
            },
            // 1 Big Tony: loud maniac
            {
                    {"TONY'S IN THE HOUSE! Somebody get me a sandwich!",
                            "Ayy! Who's ready to lose some money?!"},
                    {"BOOM! Get that outta here!", "Fuhgeddaboudit! Tony takes it!",
                            "That's mine! Say goodnight!", "Too easy! TOO EASY!"},
                    {"LOOK AT THIS! LOOK AT ALL THIS! It's beautiful!",
                            "Somebody call my cousin Vinny, I'm RICH!",
                            "I'm buyin' a boat! A BIG boat!",
                            "That's the biggest pot I ever seen and it's ALL TONY'S!"},
                    {"You folded to NOTHING! I had NOTHING! Hahaha!",
                            "Tony bluffed ya! Tony always bluffs ya!",
                            "Wanna know what I had? Ya don't wanna know. Seven deuce baby!"},
                    {"Ahh, come ON!", "This deck is rigged! RIGGED I tell ya!",
                            "I want a new dealer. And a new chair. And a sandwich."},
                    {"WHAT?! WHAT WAS THAT?! I had you DEAD!",
                            "Get outta here with that river! I'm flippin' the table!",
                            "That's it, I'm on tilt. Somebody's gonna pay."},
                    {"Ha! Scared! I smell fear!", "That's right, run! RUN!",
                            "Bawk bawk bawk! Chicken!"},
                    {"Oh you wanna DANCE?!", "You raised TONY?! Big mistake! Huge!",
                            "Ohhh, now it's personal."},
                    {"ALL IN, BABY! Call me if you got the guts!",
                            "Tony's pushin'! Whatcha gonna do?!",
                            "Every chip! Every last one! Let's GO!"},
                    {"This ain't over! I'm comin' back with my cousin's money!",
                            "Rigged! The whole thing! I'm outta here!"},
                    {"Can we speed this up?! Tony's got places to be!",
                            "I'm raising blind next hand. Don't care.",
                            "Who's scared of Tony? Everybody. Good."},
                    {"Look at my stack! It's like a skyscraper! TONY TOWER!",
                            "I'm up so much I'm gonna start tippin' the dealer in hundreds!",
                            "Who wants a loan? Hahaha, just kiddin', no."},
                    {"{them}, ya got smoked! SMOKED!", "Sit down, {them}. Sit DOWN.",
                            "{them}, you play like my grandmother. And she's dead."},
                    {"Damn right it was nice!", "I know, I'm amazing, thanks!"},
                    {"Maybe I am, maybe I ain't! Call and find out!",
                            "Tony ALWAYS bluffs. Or does he?!"},
                    {"Ship it? I'll ship YOU back home!", "Ship THIS! *makes rude gesture*"},
                    {"I ain't cryin'! YOU'RE cryin'!", "These are ANGRY tears!"},
                    {"Wrecked?! I'll wreck YOU! Next hand, you and me!",
                            "Keep talkin', I'm comin' for ya!"},
                    {"Thanks for the money, {you}! Ya want a receipt?!",
                            "Tony owns you! Get used to it!",
                            "Next time bring more chips, I'm hungry!"},
            },
            // 2 Chad: crypto bro
            {
                    {"Yo yo yo, what's good? Just closed a sick trade, let's gooo.",
                            "Chad's here. Hold onto your bags, fam."},
                    {"Number go UP!", "Ez money, bro.", "Diamond hands pay off, fam.",
                            "That's alpha, baby."},
                    {"TO THE MOOOOON! 🚀🚀🚀", "Bro I'm literally so up right now.",
                            "Gonna put this whole pot into a meme coin, no cap.",
                            "Generational wealth, fam. GENERATIONAL."},
                    {"Bro I had literally nothing lmao.", "Paper hands folded. Classic.",
                            "Bluffed you into the shadow realm, bro."},
                    {"Bro what.", "That's a temporary dip, I'm buying more.",
                            "Bear market, bro. We'll be back."},
                    {"BRO THAT'S A RUG PULL. THAT'S A LITERAL RUG PULL.",
                            "No way. NO WAY. I'm gonna need to touch grass after this.",
                            "Bro the river just liquidated me."},
                    {"Paper hands! Lmao!", "Weak hands get shaken out, bro.",
                            "NGMI, bro. Not gonna make it."},
                    {"Oh you're feeling bullish, huh?", "Bro thinks he's a whale.",
                            "Ok bet. Let's see it."},
                    {"Full port, bro. ALL IN. YOLO!", "Leveraged to the max, let's goooo!",
                            "Sending it. No stop loss."},
                    {"Got liquidated. Gonna go stare at charts.",
                            "Bro I'm down bad. Brb, selling my car."},
                    {"Anyone wanna hear about my NFTs?", "Gm. Gm everyone.",
                            "This is financial advice, bro: give me your chips."},
                    {"My stack's up 300%, bro, I'm basically a hedge fund.",
                            "Bro I'm printing. PRINTING.",
                            "Look at this bag. LOOK AT IT. Absolutely bullish."},
                    {"{them} got rekt lmao.", "Sorry {them}, you're exit liquidity.",
                            "{them} is down bad, bro."},
                    {"Appreciate it, king.", "Respect, bro. Respect."},
                    {"Bro I'm not bluffing, I'm just early.", "DYOR, bro."},
                    {"Ship it to my wallet, bro.", "Already shipped. On chain."},
                    {"Crying? Bro I'm HODLing.", "These are tears of profit, bro."},
                    {"Rekt? Never heard of her.", "Bro I've been rekt way worse than this."},
                    {"Thanks for the liquidity, {you}!", "You just got farmed, bro.",
                            "Rekt! Absolutely rekt! Gg ez."},
            },
            // 3 Grandma Mae: tight and savage
            {
                    {"Hello dears! I brought cookies. They're for me.",
                            "Grandma's here to take your lunch money, sweetie."},
                    {"Thank you, dear. Grandma needs a new hip.",
                            "Bless your heart. Hand them over.",
                            "Oh my, did I win again? How embarrassing for you.",
                            "Your mother would be so disappointed."},
                    {"Oh my stars! That's going straight to my grandkids' college fund!",
                            "I've buried two husbands and I'll bury this table too!",
                            "Heavens! I haven't been this excited since the 1960s!"},
                    {"Grandma doesn't always have it, sweetheart.",
                            "Oh I had garbage, dear. Don't tell anyone."},
                    {"Well, that's just rude.", "I'm too old for this nonsense.",
                            "Hmph. In my day we respected our elders."},
                    {"Well I NEVER! Lord give me strength.",
                            "That river was uncalled for. Completely uncalled for.",
                            "I'm going to pray for you, and not the nice kind."},
                    {"Good choice, sweetie. Go sit in the corner.",
                            "Smart boy. Grandma would've eaten you alive.",
                            "That's right, run along now."},
                    {"Oh, you're raising? That's cute.",
                            "Sweetie, I've been playing poker since Eisenhower.",
                            "Oh dear. You've made a terrible mistake."},
                    {"Grandma's going all in. Try me, sugar.",
                            "Everything in, dear. I'm old, I've got nothing to lose."},
                    {"Well! I'll be at bingo if anyone needs me.",
                            "That's enough excitement. Time for my stories."},
                    {"Could you hurry it up, dear? I might die before you act.",
                            "Has anyone seen my glasses? Oh, they're on my head.",
                            "You remind me of my late husband. He was terrible at cards too."},
                    {"Look at all my chips! Grandma's buying a cruise!",
                            "I'm up so much I might leave the grandkids out of the will.",
                            "Sweetie, I'm winning so much my pacemaker's racing."},
                    {"Oh {them}, bless your little heart.",
                            "{them}, sweetie, maybe try Go Fish.",
                            "Don't cry, {them}. Here, have a cookie. No wait, they're mine."},
                    {"Thank you, dear. I know.", "Flattery is nice, but I'll still take your money."},
                    {"Grandma never bluffs, sweetie. Grandma never needs to.",
                            "Call and find out, dear."},
                    {"Ship it? I can't even ship a package, dear.",
                            "Sweetie, I've been shipped more pots than you've seen."},
                    {"Crying? These are allergies, dear.",
                            "Oh honey, I stopped crying in 1958."},
                    {"Watch your language, young man!", "Wrecked? I'll wreck your inheritance."},
                    {"Thank you for the donation, sweetie.",
                            "Oh you poor thing. Grandma took ALL of it.",
                            "That's what happens when you mess with Grandma."},
            },
            // 4 Dee: the shark
            {
                    {"Sit down. Let's get this over with.", "Fresh fish."},
                    {"Mine.", "Predictable.", "Next.", "Thanks."},
                    {"That was never in doubt.", "Blood in the water.",
                            "I could do this all night. I probably will.",
                            "You can't beat me. You can only donate."},
                    {"You folded the best hand.", "I had air. You had fear."},
                    {"Fine.", "Variance.", "Enjoy it. It won't happen again."},
                    {"Statistically irrelevant. Still annoying.",
                            "You got lucky. Remember that when I take it back."},
                    {"Correct fold. Wrong game.", "Run, little fish.", "Smart. For once."},
                    {"Bold.", "Are you sure?", "Mistake."},
                    {"All in. Decide.", "Shove. Clock's ticking."},
                    {"Bad run. I'll be back with your money later.", "This isn't over."},
                    {"Deal.", "I'm bored. Somebody make a mistake.",
                            "Everyone at this table is a fish. Present company included."},
                    {"Look at the stacks. That's the scoreboard.",
                            "I'm not up. You're just down.",
                            "Count the chips. I'll wait."},
                    {"{them}, that was painful to watch.", "Sit out, {them}.",
                            "{them}: fish."},
                    {"I know.", "Obviously."},
                    {"Pay to see.", "Maybe. Find out."},
                    {"Already shipped.", "Cute."},
                    {"I don't cry. I collect.", "Projecting?"},
                    {"Words are cheap. Chips aren't.", "We'll see."},
                    {"Thanks, {you}.", "That's my money now, {you}.",
                            "You're the fish, {you}. Always have been."},
            },
            // 5 Lucky Lou: calling station
            {
                    {"Hiya folks! Got my lucky rabbit's foot right here!",
                            "Lou's here! Feeling LUCKY tonight!"},
                    {"See?! The rabbit's foot WORKS!", "Lucky Lou strikes again!",
                            "I just KNEW it! I could feel it!",
                            "Kissed my lucky penny before that one!"},
                    {"JACKPOT! Oh my goodness, JACKPOT!",
                            "I called with nothin' and look at THAT!",
                            "My horoscope said today was my day!"},
                    {"I don't even know what I had!", "Was that a bluff? Huh. Neat!"},
                    {"Aw shucks.", "Gotta rub the rabbit's foot harder.",
                            "That's ok, it's a long night!"},
                    {"My rabbit's foot BETRAYED me!", "I need a new lucky charm. A BIGGER one."},
                    {"Aw, you folded? I was gonna call anyway!",
                            "Hey come back, I was just gettin' warmed up!"},
                    {"I call! I always call!", "Ooh, raising! I'll call that too!"},
                    {"All of it! For luck!", "Lou's lettin' it ride!"},
                    {"Out of chips AND out of luck. Bye y'all!",
                            "Rabbit's foot's going in the trash."},
                    {"Anybody got a four-leaf clover?", "I can feel a big hand coming!",
                            "Never fold, that's my motto!"},
                    {"Look at all these chips! The rabbit's foot is ON FIRE!",
                            "I'm up! I'm actually up! Mom, look!"},
                    {"Sorry {them}, luck's on my side tonight!",
                            "{them}, ya gotta believe!"},
                    {"Aw thanks pal!", "You're nice! I'm still calling though."},
                    {"I dunno if I'm bluffing! I'll call to find out!",
                            "Bluff? I never fold so it don't matter!"},
                    {"Shipped with luck!", "Here it comes! Lucky delivery!"},
                    {"Happy tears, buddy!", "I cry at everything!"},
                    {"Hey, no need to be mean!", "Luck changes, pal!"},
                    {"Sorry {you}! Wasn't skill, just luck!",
                            "Rabbit's foot beat ya, {you}!"},
            },
            // 6 Professor Fitz: the math nerd
            {
                    {"Good evening. I've calculated I'll win 73% of tonight's pots.",
                            "Ah, a table of statistical outliers."},
                    {"As predicted.", "Q.E.D.", "Expected value, people. Look it up.",
                            "The math never lies."},
                    {"Exactly as my spreadsheet forecast.",
                            "That pot alone covers my sabbatical.",
                            "Fascinating. And by fascinating I mean I crushed you."},
                    {"Fold equity is a beautiful thing.",
                            "You folded a 41% hand to a 12% bluff. Pathetic."},
                    {"A deviation from the mean. Irrelevant.",
                            "Variance is a cruel mistress."},
                    {"The probability of that was 4.5%. FOUR POINT FIVE.",
                            "I'm writing a paper about how unlikely that was.",
                            "That violates several laws of mathematics."},
                    {"Correct fold. Your first correct decision tonight.",
                            "You had the right pot odds to fold. Congratulations."},
                    {"Your raise is mathematically questionable.",
                            "I've run the numbers on your raise. They don't add up."},
                    {"All in. My equity is overwhelming.",
                            "I've done the calculation. Shove."},
                    {"A statistically improbable outcome. I'll be publishing.",
                            "I'm leaving. This table has no respect for probability."},
                    {"Did you know there are 2,598,960 five-card hands?",
                            "Please act faster. I'm modeling your tells.",
                            "Statistically, half of you are below average."},
                    {"My chip count is two standard deviations above the mean.",
                            "Behold: the law of large numbers, in my favor.",
                            "I'm up. As the models predicted."},
                    {"{them}, your play had negative expected value.",
                            "I'd explain how you lost, {them}, but you wouldn't follow."},
                    {"Thank you. Skill is quantifiable.", "Appreciated. It was optimal."},
                    {"Perhaps. The probability is nonzero.", "I'll let the math decide."},
                    {"Shipped with a 94% confidence interval.", "Ship? It's already calculated."},
                    {"I don't cry. I recalculate.", "Emotion is a rounding error."},
                    {"Wrecked is not a statistical term.", "Your vocabulary needs work."},
                    {"Thank you, {you}. Your errors were consistent and profitable.",
                            "{you}, I'll send you the spreadsheet of your mistakes.",
                            "Predictable, {you}. Delightfully predictable."},
            },
            // 7 Jolene: the trucker
            {
                    {"Breaker breaker, Jolene's at the table!",
                            "Move over, boys. Mama's here to haul your chips."},
                    {"10-4, that's MINE!", "Hammer down, honey!", "Jolene takes the load!",
                            "Read ya like a highway sign, sugar."},
                    {"WOOO! That's a full rig of chips right there!",
                            "Haulin' this pot all the way to Tulsa!",
                            "Honey, I've made less money crossing three states!"},
                    {"Had nothin' but a cup of coffee and a dream, sugar.",
                            "Bluffed ya like a weigh station on a Sunday."},
                    {"Well, hell.", "Flat tire. Happens.",
                            "That's a speed bump, not a wreck."},
                    {"Are you SERIOUS?! That river's crookeder than a mountain road!",
                            "Jackknifed! Jackknifed on the river!",
                            "I've seen better luck at a truck stop slot machine."},
                    {"Back it up, honey! Beep beep beep!",
                            "That's right, pull over.", "Knew you were runnin' on empty."},
                    {"Oh you wanna race me, sugar?", "Big rig comin' through, honey.",
                            "You raise like a minivan merges."},
                    {"Hammer down! All in!", "Every chip on the load. Let's ride!"},
                    {"Outta gas. I'll be back after my next run.",
                            "Well, that's a wreck. Call the tow truck."},
                    {"Y'all play slower than a construction zone.",
                            "Somebody bet already, I got a delivery at dawn.",
                            "I've met smarter cows on the back of a trailer."},
                    {"Look at this haul! I'm runnin' heavy tonight, boys!",
                            "My stack's taller than my rig!",
                            "I'm gonna need a bigger trailer for all these chips."},
                    {"{them}, honey, you just got run over.",
                            "{them}, that was a fender bender, sweetie.",
                            "Bless your heart, {them}."},
                    {"Why thank ya, sugar!", "I know, honey. I know."},
                    {"Pay the toll and find out, sugar.", "Maybe I am. You chicken?"},
                    {"Shipped overnight, honey!", "Already loaded up, sugar."},
                    {"Cry me a river, honey. I'll drive over it.",
                            "I haul tears for a livin', sugar."},
                    {"Them's fightin' words!", "Keep it up and I'll park on ya."},
                    {"Thanks for the cargo, {you}!", "You just got hauled, {you}!",
                            "That's what happens when you play chicken with a big rig, {you}."},
            },
    };

    /** A line for this character and moment, with names filled in, or null if none. */
    static String line(int persona, int kind, Random rnd, String you, String them, long amt) {
        if (persona < 0 || persona >= LINES.length) return null;
        String[][] mine = LINES[persona];
        if (kind < 0 || kind >= mine.length || mine[kind].length == 0) return null;
        String l = mine[kind][rnd.nextInt(mine[kind].length)];
        return l.replace("{you}", you).replace("{them}", them == null ? "pal" : them)
                .replace("{amt}", String.valueOf(amt));
    }
}
