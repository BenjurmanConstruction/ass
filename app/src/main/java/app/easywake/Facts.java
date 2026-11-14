package app.easywake;

/** Strange-but-true facts for the Brain gym. */
final class Facts {

    private static final String[] ALL = {
            "Octopuses have three hearts and blue blood.",
            "Botanically, bananas are berries and strawberries are not.",
            "A day on Venus is longer than a year on Venus.",
            "Wombat droppings are cube-shaped.",
            "Sharks have existed longer than trees.",
            "Oxford University is older than the Aztec Empire.",
            "Cleopatra lived closer in time to the Moon landing than to the building of the "
                    + "Great Pyramid.",
            "There are more possible games of chess than atoms in the observable universe.",
            "A group of flamingos is called a flamboyance.",
            "The Eiffel Tower grows about six inches taller in summer as the iron expands.",
            "Scotland's national animal is the unicorn.",
            "The shortest war in history, between Britain and Zanzibar in 1896, lasted under "
                    + "an hour.",
            "A lightning bolt is about five times hotter than the surface of the Sun.",
            "Your brain uses about a fifth of your body's energy while you rest.",
            "A teaspoon of neutron star would weigh billions of tons.",
            "Sunlight takes a little over eight minutes to reach Earth.",
            "Earth has more trees than the Milky Way has stars.",
            "Venus spins backwards compared with most planets.",
            "Saturn is less dense than water.",
            "Olympus Mons on Mars is roughly two and a half times as tall as Mount Everest.",
            "Hummingbirds are the only birds that can fly backwards.",
            "Koala fingerprints are so close to human ones they can be confused at a crime "
                    + "scene.",
            "A shrimp's heart is in its head.",
            "Butterflies taste with their feet.",
            "Crows can recognize individual human faces and remember them for years.",
            "Tardigrades can survive the vacuum of space.",
            "One species of jellyfish can turn back into its juvenile form instead of dying "
                    + "of old age.",
            "Most of an octopus's neurons are in its arms, not its head.",
            "A pineapple plant takes about two years to produce one pineapple.",
            "Peanuts are legumes, not nuts.",
            "Almonds are close relatives of peaches.",
            "Early farmed carrots were purple or yellow. Orange ones came later.",
            "In the 1830s ketchup was sold as medicine.",
            "The man who designed the Pringles can had some of his ashes buried in one.",
            "Nintendo was founded in 1889 as a playing-card company.",
            "The first American alarm clock, made in 1787, could only ring at 4 a.m.",
            "Before alarm clocks were common, people in Britain paid a 'knocker-upper' to tap "
                    + "on their window each morning.",
            "The dot over a lowercase i or j is called a tittle.",
            "Antarctica is the largest desert on Earth.",
            "Russia spans eleven time zones.",
            "Alaska is the westernmost, northernmost and easternmost US state, because its "
                    + "islands cross the 180th meridian.",
            "Reno, Nevada is farther west than Los Angeles.",
            "Mount Everest grows a few millimeters every year.",
            "Thousands of years ago the Sahara was green, with lakes and grassland.",
            "An ordinary fluffy cloud can weigh more than a million pounds.",
            "At one exact temperature and pressure, water can boil and freeze at the same "
                    + "time.",
            "You are slightly taller in the morning than in the evening.",
            "The fax machine was invented before the telephone.",
            "France's last guillotine execution was in 1977, the year Star Wars came out.",
            "Woolly mammoths were still alive when the Great Pyramid was built.",
            "Tyrannosaurus rex lived closer in time to us than to Stegosaurus.",
            "Bluetooth is named after a tenth-century Viking king, Harald Bluetooth.",
            "The computers that guided Apollo to the Moon were far weaker than a modern "
                    + "phone.",
            "The first computer 'bug' on record was a real moth stuck in a machine in 1947.",
            "The Moon drifts about an inch and a half farther from Earth every year.",
            "Footprints left on the Moon could last for millions of years.",
            "Jupiter's Great Red Spot is a storm that has raged for centuries.",
            "A million seconds is about eleven and a half days. A billion seconds is almost "
                    + "32 years.",
            "Shuffle a deck of cards well and that exact order has almost certainly never "
                    + "existed before.",
            "111,111,111 times 111,111,111 equals 12,345,678,987,654,321.",
            "A googol is a 1 followed by 100 zeros. Google's name comes from it.",
            "There is no Roman numeral for zero.",
            "If you could fold a sheet of paper in half 42 times, it would reach the Moon.",
            "In a room of just 23 people, there is a 50% chance two share a birthday.",
            "Elephants are among the few mammals that can't jump.",
            "Honey found in ancient Egyptian tombs was still edible.",
            "The Great Wall of China cannot be seen with the naked eye from the Moon.",
            "A blue whale's heart weighs about 400 pounds."
    };

    private Facts() {}

    /** The next fact from a shuffled deck: every fact comes up once before any repeats. */
    static String next() {
        return ALL[Deck.next(App.ctx, "facts", ALL.length)];
    }
}
