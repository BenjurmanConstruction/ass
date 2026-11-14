package app.easywake;

/** A pile of corny dad jokes, with some dark humor mixed in, for the home screen. */
final class Jokes {

    private static final String[] ALL = {
            "I'm reading a book about anti-gravity. It's impossible to put down.",
            "Why don't eggs tell jokes? They'd crack each other up.",
            "I used to hate facial hair, but then it grew on me.",
            "What do you call a fake noodle? An impasta.",
            "I only know 25 letters of the alphabet. I don't know y.",
            "Why did the scarecrow win an award? He was outstanding in his field.",
            "I would tell you a construction joke, but I'm still working on it.",
            "What do you call cheese that isn't yours? Nacho cheese.",
            "Why can't a bicycle stand on its own? It's two tired.",
            "I'm on a seafood diet. I see food and I eat it.",
            "What did the ocean say to the beach? Nothing, it just waved.",
            "Why did the coffee file a police report? It got mugged.",
            "How does a penguin build its house? Igloos it together.",
            "I used to be a baker, but I couldn't make enough dough.",
            "What do you call a bear with no teeth? A gummy bear.",
            "Why did the math book look sad? It had too many problems.",
            "I don't trust stairs. They're always up to something.",
            "What's brown and sticky? A stick.",
            "Why did the golfer bring two pairs of pants? In case he got a hole in one.",
            "What do you call a fish wearing a bowtie? Sofishticated.",
            "I told my wife she was drawing her eyebrows too high. She looked surprised.",
            "Why don't skeletons fight each other? They don't have the guts.",
            "What do you call a pile of cats? A meowtain.",
            "How do you organize a space party? You planet.",
            "I got a new job at the calendar factory. I can't take any days off.",
            "Why did the tomato blush? It saw the salad dressing.",
            "What did one wall say to the other? I'll meet you at the corner.",
            "I'm afraid for the calendar. Its days are numbered.",
            "What do you call a cow with no legs? Ground beef.",
            "Why couldn't the pony sing? It was a little hoarse.",
            "What do lawyers wear to court? Lawsuits.",
            "Did you hear about the restaurant on the moon? Great food, no atmosphere.",
            "Why are elevator jokes so good? They work on many levels.",
            "What do you call a sleeping bull? A bulldozer.",
            "I used to play piano by ear. Now I use my hands.",
            "Why did the bicycle fall over? It was two tired. Still.",
            "What kind of shoes do ninjas wear? Sneakers.",
            "How do you make a tissue dance? Put a little boogie in it.",
            "Why did the cookie go to the doctor? It felt crummy.",
            "What do you call a dog that does magic? A labracadabrador.",
            "I ordered a chicken and an egg online. I'll let you know.",
            "Why can't you hear a pterodactyl in the bathroom? The p is silent.",
            "What did the janitor say when he jumped out of the closet? Supplies!",
            "Why do bees have sticky hair? They use honeycombs.",
            "What's orange and sounds like a parrot? A carrot.",
            "I thought about going on an all-almond diet. But that's just nuts.",
            "What do you call a boomerang that won't come back? A stick.",
            "Why did the stadium get hot after the game? All the fans left.",
            "How do you find Will Smith in the snow? Look for fresh prints.",
            "What do you call a factory that makes okay products? A satisfactory.",
            "I have a joke about chemistry, but I don't think it'll get a reaction.",
            "Why did the chicken join a band? It had the drumsticks.",
            "What did the left eye say to the right eye? Between us, something smells.",
            "Why don't oysters share? They're shellfish.",
            "What do you call an alligator in a vest? An investigator.",
            "I made a pencil with two erasers. It was pointless.",
            "What do you call a belt made of watches? A waist of time.",
            "Why did the picture go to jail? It was framed.",
            "How do trees get on the internet? They log in.",
            "What do you call a can opener that doesn't work? A can't opener.",
            "Why did the banana go to the doctor? It wasn't peeling well.",
            "I'm so good at sleeping I can do it with my eyes closed.",
            "What do you call a snowman with a six-pack? An abdominal snowman.",
            "Why did the computer go to the doctor? It had a virus.",
            "What did the grape do when it got stepped on? It let out a little wine.",
            "I used to be addicted to soap. I'm clean now.",
            "Why are ghosts bad liars? You can see right through them.",
            "What do you call a shoe made of a banana? A slipper.",
            "How does the moon cut its hair? Eclipse it.",
            "Why did the man fall down the well? He couldn't see that well.",
            "What do you get when you cross a snowman and a vampire? Frostbite.",
            "I was going to tell a time-travel joke, but you didn't like it.",
            "Why don't scientists trust atoms? They make up everything.",
            "What did the zero say to the eight? Nice belt.",
            "What do you call a lazy kangaroo? A pouch potato.",
            "Why was the broom late? It over-swept.",
            "I asked the alarm clock for advice. It told me to take it one snooze at a time.",
            "My alarm and I have a lot in common. Neither of us wants to go off in the morning.",
            "Why did the man put his alarm in his shoe? He didn't want to wake up on the "
                    + "wrong foot.",
            "I'm not a morning person. I'm barely an afternoon person.",
            "Why did the clock get sent to the principal? It tocked too much.",
            "What time is it when the clock strikes 13? Time to get a new clock.",
            "I threw my alarm clock out the window. I wanted to see time fly.",
            "Why do we tell actors to break a leg? Every play has a cast.",
            "What do you call two monkeys sharing an Amazon account? Prime mates.",
            "Did you hear about the claustrophobic astronaut? He just needed a little space.",
            "Why did the invisible man turn down the job? He couldn't see himself doing it.",
            "What do you call a magic owl? Hoodini.",
            "I got hit in the head with a can of soda. Luckily it was a soft drink.",
            "Why is Peter Pan always flying? He Neverlands.",
            "What do you call a nervous javelin thrower? Shakespeare.",
            "How do cows stay up to date? They read the moos-paper.",
            "What do you call a train carrying bubblegum? A chew-chew train.",
            "Why did the duck get arrested? It was selling quack.",
            "I told a joke about paper once. It was tearable.",
            "What's a skeleton's least favorite room? The living room.",
            "Why did the fish blush? It saw the ocean's bottom.",
            "What do you call a deer with no eyes? No idea.",
            "I bought the world's worst thesaurus. Not only is it terrible, it's terrible.",
            "Why did the gym close down? It just didn't work out.",
            "What did the buffalo say when his son left? Bison.",
            "How do you catch a squirrel? Climb a tree and act like a nut.",
            "Why shouldn't you write with a broken pencil? It's pointless.",
            "What do you call a hippie's wife? Mississippi.",
            "I named my dog Five Miles so I can say I walk Five Miles every day.",
            "What do sprinters eat before a race? Nothing. They fast.",
            "Why did the smartphone need glasses? It lost all its contacts.",
            "What do you call a pig that does karate? A pork chop.",
            "I'm terrified of elevators, so I'm taking steps to avoid them.",
            "Why did the mushroom get invited to the party? He was a fungi.",
            "What's the best thing about Switzerland? I don't know, but the flag is a big plus.",
            "Why can't your nose be 12 inches long? Then it would be a foot.",
            "What did the pirate say on his 80th birthday? Aye matey.",
            "I spent a lot on a limo, then found out it didn't come with a driver. All that "
                    + "money and nothing to chauffeur it.",
            "Why did the drum take a nap? It was beat.",
            "What do you call a line of rabbits walking backwards? A receding hare line.",
            "How did the hipster burn his tongue? He drank his coffee before it was cool.",
            "Dad, can you put my shoes on? No, I don't think they'll fit me.",
            "I'm hungry. Hi Hungry, I'm Dad.",

            // Darker ones.
            "I have a stepladder. I never knew my real ladder.",
            "My grandfather has the heart of a lion and a lifetime ban from the zoo.",
            "I'll never forget my grandfather's last words: \"Stop shaking the ladder.\"",
            "The cemetery is so crowded. People are dying to get in.",
            "I told my doctor I broke my arm in two places. He told me to stop "
                    + "going to those places.",
            "They say you are what you eat. I don't remember eating a disappointment.",
            "My boss told me to have a good day. So I went home.",
            "I was going to get a brain transplant, but I changed my mind.",
            "Why don't cannibals eat clowns? They taste funny.",
            "I bought my friend an elephant for his room. He said thanks. I said "
                    + "don't mention it.",
            "My wife told me to stop impersonating a flamingo. I had to put my foot down.",
            "I visited my friend at his new house. He said make yourself at home, "
                    + "so I threw him out. I hate visitors.",
            "The doctor gave me one year to live, so I shot him. The judge gave me "
                    + "fifteen. Problem solved.",
            "I have a clean conscience. It's never been used.",
            "My therapist says I have a preoccupation with revenge. We'll see about that.",
            "Today I gave my dead batteries away. Free of charge.",
            "What's the difference between a hippo and a zippo? One's really "
                    + "heavy, the other's a little lighter.",
            "I hate it when people say age is just a number. Age is clearly a word.",
            "Light travels faster than sound. That's why some people look bright "
                    + "until they speak.",
            "My parents raised me as an only child, which really annoyed my sister.",
            "I started a band called 999 Megabytes. We still haven't gotten a gig.",
            "Why did the old man fall in the well? He couldn't see that well. "
                    + "Nobody checked for days.",
            "The inventor of the throat lozenge died last week. There was no "
                    + "coffin at his funeral.",
            "Never trust an undertaker. Sooner or later he'll let you down.",
            "My uncle the taxidermist died. He's been stuffed with regret ever since.",
            "I asked the librarian for a book on paranoia. She whispered, \"It's "
                    + "right behind you.\"",
            "Life is short. Smile while you still have teeth.",
            "What do you call a cheap circumcision? A rip-off.",
            "I have a fear of speed bumps, but I'm slowly getting over it.",
            "The graveyard shift pays well, but the coworkers are dead quiet.",
            "Why did the skeleton go to the party alone? He had no body. Hasn't for years.",
            "I was addicted to the hokey pokey, but I turned myself around.",
            "My will says to spread my ashes at the mall, so my kids will visit "
                    + "twice a week.",
            "I told my suitcases there'd be no vacation this year. Now I'm dealing "
                    + "with emotional baggage.",
            "Someone stole my mood ring. I don't know how I feel about that.",
            "I'd tell you a joke about my retirement fund, but there's nothing in it.",
            "Morning is the worst time to wake up. Somebody should move it to later.",
            "Good morning. Another day closer to the weekend, and to everything else.",
            "The early bird gets the worm. The early worm gets eaten. Choose your "
                    + "role model.",
            "Alarm clocks: because every morning should begin with a small heart attack.",
            "I'm at the age where my back goes out more than I do.",
            "Dark humor is like food. Not everybody gets it.",
            "What's worse than finding a worm in your apple? Finding half a worm.",

            // Even darker.
            "I just got my doctor's test results and I'm really upset. Turns out I'm "
                    + "not going to be a doctor.",
            "My wife left a note on the fridge: 'This isn't working.' I opened it. "
                    + "Works fine.",
            "When I die, I want my ex to lower me into the grave so she can let me "
                    + "down one last time.",
            "I told the funeral director I wanted something cheap. He said, 'How "
                    + "about you do the digging?'",
            "My boss said I intimidate my coworkers. I stared at him until he apologized.",
            "The last thing my grandmother said before she died was, 'Are you still "
                    + "holding that ladder?'",
            "I was digging in the garden and found a chest full of gold. I ran to "
                    + "tell my wife, then remembered why I was digging in the garden.",
            "I asked my date to meet me at the gym. She never showed. Guess the two "
                    + "of us aren't going to work out.",
            "My pessimist friend says the glass is half empty. My optimist friend "
                    + "says half full. The glass has been dead for years.",
            "I keep my friends close and my enemies in the group chat.",
            "The doctor said I had six months to live. I couldn't pay the bill, so he "
                    + "gave me six more.",
            "My retirement plan is to be found dead at my desk. At least the company "
                    + "will finally notice me.",
            "I told my psychiatrist I keep hearing voices. He said, 'That's just me. "
                    + "Pay attention.'",
            "My landlord says he wants to talk about my heating bill. I told him my "
                    + "door is always open.",
            "What did the man say when he lost his job at the bank? Nothing. Losing "
                    + "interest came naturally.",
            "I come from a long line of people who didn't die before having kids. "
                    + "It's my one real achievement.",
            "The trouble with being punctual is that nobody's there to appreciate it. "
                    + "Same with being dead.",
            "I have a lot of growing up to do. I realized that the other day inside "
                    + "my blanket fort.",
            "My life coach quit. Said he couldn't watch anymore.",
            "They told me I'd never amount to anything because I procrastinate. I "
                    + "said, 'Just you wait.'",
            "I named my bed 'the gym' so I can say I spend eight hours a day there.",
            "A will is a dead giveaway.",
            "I'd like to thank my legs for supporting me, my arms for being by my "
                    + "side, and my liver for nothing.",
            "The older I get, the earlier it gets late.",
            "My bank account and I have something in common. We're both empty inside.",
            "I'm not saying I'm old, but my birth certificate is in Roman numerals.",
            "My wife and I laugh about how competitive we are. I laugh more.",
            "Sleep is my favorite thing. It's like death without the commitment.",
            "I've reached the age where 'happy hour' is a nap.",
            "Experience is what you get right after you needed it.",
            "I'm great at multitasking. I can waste time, be unproductive and "
                    + "procrastinate all at once.",
            "The secret to a long life is to keep breathing. Works every time until "
                    + "it doesn't.",
            "I asked the gym trainer which machine to use to impress people. He "
                    + "pointed at the ATM.",
            "Some people graduate with honors. I am just honored to graduate.",
            "My doctor told me to watch my drinking. So now I drink in front of a mirror.",
            "Whoever said money can't buy happiness simply didn't know where to shop. "
                    + "Or how broke I am.",
            "I don't have a carbon footprint. I just drive everywhere.",
            "Every morning I check the obituaries. If I'm not in them, I get dressed.",
            "Marriage is finding that one special person you want to annoy for the "
                    + "rest of your life.",
            "The good news: the alarm works. The bad news: you're awake for it.",
            "Rise and shine. Or just rise. Shining is optional and frankly unlikely.",
            "Nothing ruins a Friday like realizing it's Wednesday.",
            "I planned to conquer the world today, but the snooze button had other ideas.",
            "Be the reason someone smiles today. Or the reason they drink. Either "
                    + "way, be memorable."
    };

    private Jokes() {}

    private static String[] all;

    static String[] all() {
        if (all == null) {
            all = new String[ALL.length + Jokes2.ALL.length + Jokes3.ALL.length
                    + Jokes4.ALL.length];
            System.arraycopy(Jokes4.ALL, 0, all, ALL.length + Jokes2.ALL.length
                    + Jokes3.ALL.length, Jokes4.ALL.length);
            System.arraycopy(ALL, 0, all, 0, ALL.length);
            System.arraycopy(Jokes2.ALL, 0, all, ALL.length, Jokes2.ALL.length);
            System.arraycopy(Jokes3.ALL, 0, all, ALL.length + Jokes2.ALL.length,
                    Jokes3.ALL.length);
        }
        return all;
    }

    /** The next joke from a shuffled deck: every joke comes up once before any repeats. */
    static String next() {
        String[] a = all();
        if (App.ctx != null) Stats.add(App.ctx, "jokes", 1);
        return a[Deck.next(App.ctx, "jokes", a.length)];
    }
}
