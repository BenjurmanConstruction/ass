package app.easywake;

/** More jokes, grouped by type. Dealt together with the first pile in {@link Jokes}. */
final class Jokes2 {
    private Jokes2() {}

    static final String[] ALL = {
            // ----- one-liners -----
            "My bed and I are perfect for each other. My alarm clock keeps trying to break "
                    + "us up.",
            "I'm not lazy. I'm on energy-saving mode.",
            "I put my phone on airplane mode. It still hasn't taken off.",
            "My wallet is like an onion. Opening it makes me cry.",
            "I'm great at multitasking. I can waste time, be unproductive and procrastinate "
                    + "all at once.",
            "I finally got eight hours of sleep. It took me three days.",
            "I don't need a hairstylist. My pillow gives me a new look every morning.",
            "I told myself I'd stop procrastinating. I'll start tomorrow.",
            "My diet plan: make all my friends cupcakes. The fatter they get, the thinner "
                    + "I look.",
            "I was going to tell a time-travel joke, but you didn't like it.",
            "Common sense is like deodorant. The people who need it most never use it.",
            "Life is short. Smile while you still have teeth.",
            "My favorite exercise is a cross between a lunge and a crunch. I call it lunch.",
            "I'm not arguing. I'm just explaining why I'm right.",
            "Some people graduate with honors. I am just honored to graduate.",
            "The older I get, the earlier it gets late.",
            "I wasn't planning on going for a run today, but those cops came out of nowhere.",
            "Whoever said nothing is impossible has never tried to slam a revolving door.",

            // ----- work and the job site -----
            "I asked the foreman for a raise. He handed me a ladder.",
            "Measure twice, cut once, then go buy more lumber anyway.",
            "Why did the carpenter quit? He was board.",
            "The roofer said the first job was on the house.",
            "Why was the electrician always calm? He knew how to stay grounded.",
            "I got fired from the calendar factory. All I did was take a day off.",
            "The drywall guy and I got into it. We patched things up.",
            "A plumber's favorite shoes? Clogs.",
            "I used to work at a shoe recycling shop. It was sole destroying.",
            "My boss told me to have a good day, so I went home.",
            "Concrete jokes are hard. Give them time to set in.",
            "Why don't painters ever lose an argument? They always have another coat.",
            "The best thing about teamwork is there's always someone else to blame.",
            "Why did the hammer get promoted? It always nailed it.",
            "I'd tell you a joke about a level, but I can't find the right angle. It's "
                    + "on the level, though.",
            "The tape measure retired. It had gone the distance.",
            "Why was the welder such a good friend? He formed strong bonds.",
            "I named my truck Promise. Now nobody can say I don't keep one.",

            // ----- money and the market -----
            "I bought the dip. Then it dipped again. Now I own guacamole.",
            "My portfolio is like a houseplant. I check it daily and it still dies.",
            "The stock market is the only store where people run away during a sale.",
            "I'm a long-term investor. That's what you call a short-term trade that went "
                    + "wrong.",
            "Why did the trader bring a ladder? To get over the resistance.",
            "My broker and I have a deal. He gets richer and I get experience.",
            "Bulls make money, bears make money, and I make donations.",
            "I asked for financial advice. He said buy low and sell high. I said, low on "
                    + "what?",
            "I have a diversified portfolio. I lose money in several different ways.",
            "Money talks. Mine just says goodbye.",
            "They say money doesn't grow on trees, yet banks have branches.",
            "Why is a budget like a diet? Everyone has one and nobody follows it.",
            "I'm saving for a rainy day. So far I can afford a light drizzle.",
            "The candlestick chart looked great until somebody blew it out.",
            "I set a stop loss. It was the only thing in my account that worked.",
            "My retirement plan is to keep working. It's fully funded.",
            "Why did the investor stare at his orange juice? It said concentrate.",
            "I finally understand inflation. My grocery bag got lighter and the bill got "
                    + "heavier.",

            // ----- tech -----
            "There are 10 kinds of people: those who understand binary and those who don't.",
            "My password is 'incorrect'. Whenever I forget it, the computer reminds me.",
            "Why did the phone go to the dentist? It had Bluetooth problems.",
            "I changed my password to 'fortnight', but apparently that's two week.",
            "The cloud is just somebody else's computer having a bad day.",
            "Why was the computer cold? It left its Windows open.",
            "Autocorrect has become my worst enema.",
            "I would tell you a UDP joke, but you might not get it.",
            "My wifi went down for five minutes, so I had to talk to my family. They seem "
                    + "like nice people.",
            "Why do programmers prefer dark mode? Because light attracts bugs.",
            "My smart speaker and I had a fight. Now it only speaks when spoken to.",
            "I got a universal remote. This changes everything.",
            "The robot vacuum ran away from home. It said the job sucked.",

            // ----- anti-jokes and deadpan -----
            "Why did the chicken cross the road? There was nothing stopping it.",
            "What's red and bad for your teeth? A brick.",
            "What's green and has wheels? Grass. I lied about the wheels.",
            "Knock knock. Who's there? A person knocking on your door.",
            "A horse walks into a bar. The bartender calls animal control.",
            "What did one stranger say to the other? Nothing. They were strangers.",
            "Why was six afraid of seven? It wasn't. Numbers don't have feelings.",
            "What do you call a dog with no legs? It doesn't matter. It isn't coming.",
            "How do you make a tissue dance? You can't. It's a tissue.",
            "Why did the man fall down the well? He couldn't see that well.",

            // ----- animals -----
            "What do you call a cow with no legs? Ground beef.",
            "Why don't oysters share? They're shellfish.",
            "What do you call an alligator in a vest? An investigator.",
            "Why do cows wear bells? Their horns don't work.",
            "What do you call a sleeping bull? A bulldozer.",
            "Why did the duck get arrested? It was selling quack.",
            "What's a cat's favorite color? Purrple.",
            "How do bees get to school? On the school buzz.",
            "What do you call a pig that does karate? A pork chop.",
            "Why are fish so smart? They live in schools.",
            "What do you get from a pampered cow? Spoiled milk.",
            "Why don't elephants use computers? They're afraid of the mouse.",
            "What do you call a lazy kangaroo? A pouch potato.",
            "Why did the owl get invited everywhere? He was a hoot.",

            // ----- food and drink -----
            "I burned 2,000 calories today. I left the pizza in the oven.",
            "Why did the tomato blush? It saw the salad dressing.",
            "What do you call a sad cup of coffee? A depresso.",
            "I ordered a chicken and an egg online. I'll let you know.",
            "Why did the banana go to the doctor? It wasn't peeling well.",
            "Bacon and eggs walk into a bar. The bartender says, sorry, we don't serve "
                    + "breakfast.",
            "What did the grape do when it got stepped on? It let out a little wine.",
            "I like my coffee how I like my mornings: over as fast as possible.",
            "Why did the cookie go to the nurse? It felt crummy.",
            "Decaf is like a hug from someone you don't like.",

            // ----- science and school -----
            "I'd tell you a chemistry joke, but I know I wouldn't get a reaction.",
            "Never trust atoms. They make up everything.",
            "Why can't you trust a math teacher holding graph paper? They're plotting "
                    + "something.",
            "Parallel lines have so much in common. It's a shame they'll never meet.",
            "What did one tectonic plate say when it bumped the other? My fault.",
            "A neutron walks into a bar and asks the price of a drink. For you, no charge.",
            "Why is the obtuse triangle always upset? It's never right.",
            "What do you call an educated tube? A graduated cylinder.",
            "The moon and the earth broke up. She needed space.",

            // ----- family and getting older -----
            "My kids call it nagging. I call it motivational speaking.",
            "I childproofed the house. They still get in.",
            "At my age, getting lucky means walking into a room and remembering why.",
            "My knees make more noise than my truck.",
            "I'm at the age where my back goes out more than I do.",
            "My wife told me to take the spider out instead of killing it. Nice guy. He's "
                    + "a web designer.",
            "You know you're getting old when the candles cost more than the cake.",
            "I asked my dog what two minus two is. He said nothing.",
            "The secret to a long marriage is two words: yes, dear.",

            // ----- darker -----
            "I'm not saying I hate mornings, but the sunrise and I have unfinished business.",
            "My life coach quit. He said even he has limits.",
            "The light at the end of the tunnel has been turned off to save on costs.",
            "My doctor told me to watch my drinking. Now I drink in front of a mirror.",
            "Experience is what you get right after you needed it.",
            "Smile. Tomorrow will be worse.",
            "My bank account and I are in a long-distance relationship with money.",
            "I'm an optimist. I always think things can't get worse, and I'm always "
                    + "surprised.",
            "The early bird can have the worm. I'll take the extra hour and the regret.",
            "My will to live called in sick. So did I."
    };
}
