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
            "Inflation in plain English",
            "What the Federal Reserve does",
            "Stocks vs bonds",
            "Opportunity cost",
            "Loss aversion",
            "The anchoring effect",
            "How a credit score works",
            "How vaccines train your body",
            "Why the Moon shows one face",
            "Why we have leap years",
            "How your memory stores things",
            "How the internet finds a website",
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

            "Inflation means prices in general rise over time, so each dollar buys a "
                    + "little less. At 3% inflation, something that costs $100 today costs about "
                    + "$103 next year and about $134 in ten years. It's measured by tracking the "
                    + "price of a fixed basket of everyday goods and services. A little inflation "
                    + "is normal; the trouble starts when wages and savings grow slower than "
                    + "prices. That's why cash sitting idle quietly loses buying power, and why "
                    + "the return that matters is what you earn above inflation.",

            "The Federal Reserve is the central bank of the United States. Its main "
                    + "lever is a short-term interest rate that banks charge each other. When the "
                    + "Fed raises it, borrowing gets more expensive across the economy, which "
                    + "cools spending and inflation. When it lowers it, borrowing gets cheaper, "
                    + "which encourages spending and hiring. Its two goals are stable prices and "
                    + "high employment. Markets watch every Fed meeting closely because interest "
                    + "rates affect what almost every investment is worth.",

            "A stock is a small piece of ownership in a company: if the company grows, "
                    + "your piece is worth more, and if it fails, it can go to zero. A bond is a "
                    + "loan: you lend money and get it back with interest on a set schedule, as "
                    + "long as the borrower can pay. Stocks have historically grown more over "
                    + "long periods but swing far more along the way. Bonds usually pay less but "
                    + "move less. Mixing the two is the most common way people set how bumpy a "
                    + "ride they're willing to take.",

            "Every choice costs you the best thing you gave up to make it. That's "
                    + "opportunity cost. An hour spent scrolling isn't free; it cost you an hour "
                    + "of sleep, exercise or work. Money left in a drawer costs you what it could "
                    + "have earned elsewhere. Thinking this way changes the question from 'is "
                    + "this worth it?' to 'is this better than my next best option?', which is "
                    + "usually the more honest one.",

            "Losing $100 hurts about twice as much as gaining $100 feels good. This "
                    + "quirk, called loss aversion, makes people do odd things: hold a falling "
                    + "investment far too long to avoid 'making the loss real', refuse a fair "
                    + "bet, or pay extra for insurance on cheap items. Knowing about it helps. "
                    + "When you feel a strong urge to avoid a loss, ask what you'd do if you were "
                    + "starting fresh today with no history.",

            "The first number you hear sticks, and later judgments get pulled toward "
                    + "it. That first number is an anchor. A shirt marked 'was $80, now $40' "
                    + "feels like a bargain even if it was never worth $80. In salary talks, "
                    + "whoever names a figure first tends to set the range. Anchors work even "
                    + "when they're obviously random. The defense is to decide what something is "
                    + "worth to you before you look at the price tag.",

            "In the United States, a credit score is a number from 300 to 850 that "
                    + "estimates how likely you are to repay debt. The biggest factor, about a "
                    + "third of the score, is whether you pay on time. Next is how much of your "
                    + "available credit you're using; keeping it under about 30% helps. The rest "
                    + "comes from how long you've had credit, how often you apply for new credit, "
                    + "and the mix of account types. Checking your own score doesn't lower it.",

            "A vaccine shows your immune system a harmless piece or weakened form of a "
                    + "germ. Your body responds as if it were under attack: it makes antibodies "
                    + "and, more importantly, memory cells that remember what the germ looks "
                    + "like. If the real germ shows up later, those memory cells recognize it and "
                    + "launch a fast, strong defense before you get seriously ill. Boosters exist "
                    + "because that memory can fade or the germ can change.",

            "The Moon spins exactly once for every trip around the Earth, so the same "
                    + "side always faces us. That isn't a coincidence. Long ago Earth's gravity "
                    + "raised a bulge in the Moon and tugged on it, slowing the Moon's spin until "
                    + "it matched its orbit. This is called tidal locking, and most large moons "
                    + "in the solar system are locked to their planets the same way. The far side "
                    + "isn't dark; it gets just as much sunlight.",

            "Earth takes about 365 and a quarter days to circle the Sun, not exactly "
                    + "365. Without a fix, the calendar would drift about a day every four years, "
                    + "and eventually summer would fall in December. Adding February 29 every "
                    + "fourth year corrects most of it. Because the real figure is slightly less "
                    + "than a quarter day, century years are skipped unless they divide by 400. "
                    + "So 1900 wasn't a leap year, but 2000 was.",

            "Short-term memory holds a handful of items for seconds; that's what you "
                    + "use to keep a phone number in your head. Long-term memory has no known "
                    + "limit. To move something from one to the other, the brain needs "
                    + "repetition, meaning, or emotion, and much of that filing happens during "
                    + "sleep. That's why cramming all night works badly: you skip the step where "
                    + "the brain actually stores what you studied.",

            "Computers find each other by number, not by name. When you type a web "
                    + "address, your device asks a directory service called DNS to look up the "
                    + "number for that name, a bit like looking up a contact to get the phone "
                    + "number. Your device then connects to that number and asks for the page. "
                    + "The whole lookup usually takes a few thousandths of a second, and results "
                    + "are remembered for a while so the next visit is faster.",
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
            "The Statue of Liberty was the color of a new penny when it was built. "
                    + "Weather turned the copper green.",
            "Astronomers estimate there are more stars in the universe than grains of "
                    + "sand on all of Earth's beaches.",
            "Cashews grow on the outside of a fruit, hanging from the bottom of what's "
                    + "called a cashew apple.",
            "Sound travels more than four times faster through water than through air.",
            "Your heart beats about 100,000 times a day.",
            "Polar bears have black skin, and their fur is see-through, not white.",
            "Lightning flashes somewhere on Earth dozens of times every second.",
            "Some of the ashes of the man who perfected the Frisbee were molded into "
                    + "Frisbees.",
            "The point on Earth's surface farthest from its center is not Everest but "
                    + "Chimborazo, a mountain in Ecuador.",
            "Hawaii drifts about three inches closer to Japan every year.",
            "A lake in Massachusetts has a 45-letter name, the longest place name in "
                    + "the United States.",
            "Sea otters have the thickest fur of any animal: up to a million hairs per "
                    + "square inch.",
            "A group of crows is called a murder.",
            "An ostrich's eye is bigger than its brain.",
            "Starfish have no brain and no blood. They pump seawater through their "
                    + "bodies instead.",
            "Dolphins sleep with half of their brain at a time, so the other half can "
                    + "keep them breathing.",
            "A sloth can take up to a month to digest a single meal.",
            "On Mercury, the time from one sunrise to the next is about 176 Earth days.",
            "The Sun holds about 99.8 percent of all the mass in the solar system.",
            "Some neutron stars spin hundreds of times every second.",
            "Astronomers think one distant planet has sideways rain made of molten "
                    + "glass.",
            "The first product ever scanned with a barcode, in 1974, was a pack of "
                    + "chewing gum.",
            "Nintendo's Mario was named after the landlord of the company's American "
                    + "warehouse.",
            "When the Apollo 11 astronauts came home from the Moon, they signed a "
                    + "customs form.",
            "Before rubber erasers, people rubbed out pencil marks with bread.",
            "Napoleon was about average height for a Frenchman of his time.",
            "There is no evidence that Vikings wore horned helmets into battle.",
            "For about two centuries, many Europeans believed tomatoes were poisonous.",
            "The word 'salary' comes from the Latin word for salt.",
            "In the Middle Ages, a 'moment' was a unit of time equal to 90 seconds.",
            "Stock 'tickers' are named after the ticking sound of the machines that "
                    + "once printed prices on paper tape.",
            "The New York Stock Exchange traces back to an agreement signed under a "
                    + "buttonwood tree in 1792.",
    };

    private Lessons() {}
}
