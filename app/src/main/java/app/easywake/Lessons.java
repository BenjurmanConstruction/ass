package app.easywake;

/** Short plain-English lessons and the locked "vault" facts you buy with points. */
final class Lessons {
    /** The first few lessons are free; the rest cost points. */
    static final int FREE_LESSONS = 5;
    static final int LESSON_COST = 30;
    static final int FACT_COST = 15;

    static final String[] TITLES = {
            "The Monty Hall problem",
            "The memory palace",
            "Why the sky is blue",
            "The Rule of 72",
            "Why you wake up groggy",
            "Chart indicators in plain English",
            "S&P 500 vs Dow vs Nasdaq",
            "How GPS finds you",
            "The birthday paradox",
            "Spaced repetition",
            "The Dunning-Kruger effect",
            "Confirmation bias",
            "The sunk cost trap",
            "How noise cancelling works",
            "Why ice floats",
            "Black holes in a minute",
            "The placebo effect",
            "The Fermi paradox",
            "Why 0.999... equals 1",
            "Compound growth",
    };

    static final String[] BODIES = {
            "A game show has three doors: one car, two goats. You pick a door. The host, who "
                    + "knows where the car is, opens one of the other two to show a goat, then "
                    + "offers you a switch. You should switch. Your first pick had a 1 in 3 "
                    + "chance, and that doesn't change when a goat is revealed, so the other "
                    + "closed door now carries the remaining 2 in 3. It feels like 50-50, "
                    + "which is why it fooled thousands of people, including mathematicians.",

            "Memory champions don't have special brains; they use places. Picture a route you "
                    + "know well, like your home. To remember a list, put each item at a spot "
                    + "on the route as a vivid, ridiculous image: a giant egg cracking on the "
                    + "doormat, milk flooding the hallway. To recall, walk the route in your "
                    + "head. It works because brains are far better at remembering places and "
                    + "strange images than plain words or numbers.",

            "Sunlight looks white but is a mix of every color. As it passes through the air, "
                    + "it bumps into gas molecules that scatter short wavelengths (blue) much "
                    + "more than long ones (red). That scattered blue light reaches your eyes "
                    + "from every direction, so the whole sky looks blue. At sunset the light "
                    + "crosses much more air, most of the blue is scattered away before it "
                    + "reaches you, and what's left is orange and red.",

            "A quick way to estimate how long money takes to double: divide 72 by the yearly "
                    + "growth rate. At 6% a year it doubles in about 12 years; at 9%, about 8. "
                    + "It works in reverse for debt and inflation too: at 3% inflation, prices "
                    + "double in roughly 24 years. It's an approximation, but a close one for "
                    + "rates between about 4% and 12%.",

            "Sleep runs in cycles of roughly 90 minutes, moving from light sleep to deep sleep "
                    + "and then to dream (REM) sleep. If an alarm pulls you out of deep sleep, "
                    + "you get sleep inertia: that heavy, foggy feeling that can last half an "
                    + "hour. Waking near the end of a cycle, in light sleep, feels far better. "
                    + "That's why 7.5 hours can feel better than 8, and why a gentle fade-in "
                    + "alarm is easier than a sudden blast.",

            "A moving average (MA) is the average price over the last so many points; it "
                    + "smooths the line so you can see the trend. Bollinger Bands draw a band "
                    + "above and below a moving average; the band widens when prices swing "
                    + "more. RSI runs from 0 to 100 and measures how strong recent gains are "
                    + "against recent losses; traders often call above 70 overbought and below "
                    + "30 oversold. MACD compares a fast and a slow average to show momentum. "
                    + "VWAP is the average price paid today weighted by volume; many traders "
                    + "treat it as the day's fair price. None of these predict the future.",

            "They are three different baskets of stocks. The S&P 500 holds about 500 large US "
                    + "companies and weights them by size, so the biggest companies move it "
                    + "most. The Dow holds only 30 companies and weights them by share price, "
                    + "so a high-priced stock counts more than a bigger company with a cheaper "
                    + "share. The Nasdaq Composite covers the thousands of stocks listed on "
                    + "the Nasdaq exchange and leans heavily toward technology.",

            "GPS satellites each broadcast the exact time and their position. Your phone "
                    + "measures how long each signal took to arrive, which gives its distance "
                    + "from that satellite. With four or more satellites, there's only one "
                    + "spot on Earth that fits all the distances. The clocks must be "
                    + "incredibly precise: because of Einstein's relativity, satellite clocks "
                    + "run about 38 millionths of a second fast per day, and without that "
                    + "correction your position would drift by miles.",

            "In a room of just 23 people, there's a better than even chance that two share a "
                    + "birthday. With 50 people it's about 97%. It feels wrong because you "
                    + "imagine someone matching your birthday, but the question is about any "
                    + "pair, and 23 people make 253 different pairs. Your brain is bad at "
                    + "seeing how fast the number of pairs grows.",

            "You forget most new information within days unless you revisit it. The trick is "
                    + "when: review just as you're about to forget, and space the reviews "
                    + "further apart each time, for example after 1 day, 3 days, a week, a "
                    + "month. Each well-timed review makes the memory last much longer. A few "
                    + "short spaced sessions beat one long cram for the same total time.",

            "People who know only a little about a subject often rate themselves highly, "
                    + "because they don't yet know enough to see what they're missing. As they "
                    + "learn more, confidence usually drops before it climbs again with real "
                    + "skill. The flip side is that experts often underrate themselves, "
                    + "assuming what's easy for them is easy for everyone.",

            "We notice and remember evidence that agrees with what we already believe, and we "
                    + "explain away or forget what doesn't. It's why two people can read the "
                    + "same news and both come away more convinced. A practical defense: ask "
                    + "what evidence would change your mind, then go looking for exactly that.",

            "Money or time you've already spent is gone whichever way you choose next, so it "
                    + "shouldn't decide what you do now. Yet people sit through bad movies, "
                    + "keep failing projects alive and hold losing bets because they've "
                    + "already put so much in. The better question is: knowing what I know "
                    + "today, would I start this now?",

            "Sound is a wave of pressure. Noise-cancelling headphones use tiny microphones to "
                    + "listen to the noise around you, then play the exact opposite wave "
                    + "through the speakers. Where the noise pushes, the headphones pull, and "
                    + "the two cancel out. It works best on steady low sounds like engines and "
                    + "fans, and less well on sudden sounds like voices.",

            "Almost everything gets denser when it freezes. Water does the opposite: its "
                    + "molecules lock into an open, six-sided pattern that takes up about 9% "
                    + "more room than liquid water. That makes ice lighter than the water "
                    + "around it, so it floats. This matters a lot: lakes freeze from the top "
                    + "down, and the ice layer insulates the water below so fish survive the "
                    + "winter.",

            "A black hole is what's left when a huge amount of matter is crushed into a tiny "
                    + "space. Its gravity is so strong that past a boundary called the event "
                    + "horizon, nothing can escape, not even light. Time itself runs slower "
                    + "near one: to a distant watcher, something falling in would seem to slow "
                    + "and fade at the edge. The black hole at the center of our galaxy has "
                    + "about four million times the mass of the Sun.",

            "People often feel better after a treatment with no active ingredient, simply "
                    + "because they expect to. The effect is real and measurable, especially "
                    + "for pain, nausea and mood, and it's why new medicines are tested "
                    + "against dummy pills. It even works to a degree when people are told the "
                    + "pill is a placebo. It doesn't shrink tumors or cure infections; it "
                    + "changes how symptoms are felt.",

            "The universe is about 13.8 billion years old and our galaxy alone has hundreds of "
                    + "billions of stars, many with planets. If intelligent life were even "
                    + "slightly common, some of it should have spread across the galaxy by "
                    + "now. Yet we see no sign of anyone. Possible answers range from life "
                    + "being extremely rare, to civilizations not lasting long, to the "
                    + "distances simply being too great. Nobody knows.",

            "It isn't just very close to 1; it is exactly 1. One way to see it: one third is "
                    + "0.333..., and three thirds make 1, so 0.999... must be 1. Another: if "
                    + "two numbers are different, there's a number between them, and there is "
                    + "nothing you can fit between 0.999... and 1. They are two ways of writing "
                    + "the same number.",

            "Growth that builds on itself starts slow and ends huge. Put aside $200 a month "
                    + "at a 7% yearly return: after 10 years you have about $35,000, after 20 "
                    + "about $104,000, after 40 about $525,000, though you only paid in "
                    + "$96,000. Most of the total arrives in the final years, which is why "
                    + "starting early matters more than the amount. Returns are never "
                    + "guaranteed; this just shows the arithmetic.",
    };

    static final String[] VAULT = {
            "The Pacific Ocean is wider than the Moon.",
            "Lake Baikal in Siberia holds about a fifth of the world's unfrozen fresh surface "
                    + "water.",
            "Millions of years ago the Amazon River flowed the other way, toward the Pacific.",
            "Bananas are very slightly radioactive because of the potassium in them.",
            "You can hear the difference between hot and cold water being poured.",
            "Your stomach grows a new lining every few days so it doesn't digest itself.",
            "Some nerve signals travel faster than 250 miles an hour.",
            "The brain itself cannot feel pain; it has no pain sensors.",
            "The king of hearts is the only king in a deck without a mustache.",
            "A Venus flytrap counts: it only snaps shut after two touches about 20 seconds "
                    + "apart.",
            "Octopuses can taste with the suckers on their arms.",
            "Pigeons have been trained to tell a Monet painting from a Picasso.",
            "Rats make ultrasonic giggling sounds when they're tickled.",
            "Goats have rectangular pupils.",
            "Honeybees can be trained to recognize human faces.",
            "A hummingbird's heart can beat more than 1,200 times a minute.",
            "A mantis shrimp punches so fast that the water around its claw briefly boils.",
            "A fungus in tropical forests takes over the bodies of ants and steers them to a "
                    + "good spot to spread its spores.",
            "Wood frogs freeze solid in winter, heart stopped, and thaw back to life in "
                    + "spring.",
            "Greenland sharks can live for more than 250 years.",
            "The world's first webcam was set up to watch a coffee pot at Cambridge "
                    + "University.",
            "The first thing ever sold on eBay was a broken laser pointer.",
            "A solar storm in 1859 was so strong that telegraph wires sparked and some "
                    + "operators got shocks.",
            "Clocks on GPS satellites tick slightly faster than clocks on the ground, and "
                    + "the system has to correct for it.",
            "Light made in the Sun's core takes many thousands of years to reach the surface, "
                    + "then about eight minutes to reach Earth.",
            "There is a cloud in space, near the center of our galaxy, that contains vast "
                    + "amounts of alcohol.",
            "Scientists think it may rain diamonds deep inside Uranus and Neptune.",
            "Days on Earth are getting longer by about two thousandths of a second every "
                    + "century.",
            "Astronauts can grow up to two inches taller in space as their spines stretch.",
            "A US dollar bill is made of cotton and linen, not paper.",
            "'Typewriter' can be typed using only the top row of letters on a keyboard.",
            "Nearly all the gold on Earth was made in exploding or colliding stars.",
            "In lab tests, people have detected a single particle of light with the naked "
                    + "eye.",
            "Trees in a forest can pass nutrients to each other through underground fungal "
                    + "threads.",
    };

    private Lessons() {}
}
