package app.easywake;

/** Names and explanations for every Brain gym test. The index is the test's "mode". */
final class Games {
    static final String[] CATEGORIES = {"Memory", "Speed", "Focus", "Logic and knowledge"};

    static final String[] TITLES = {
            "Reaction time", "Speed math", "Number memory", "Color focus",
            "Sequence memory", "Chimp test", "Number patterns", "Aim trainer",
            "Verbal memory", "Visual memory", "Memory pairs", "Number grid race",
            "Odd color out", "Go / No-go", "Arrows", "2-back",
            "Dot count", "Time sense", "Typing speed", "Word scramble",
            "Math chain", "Trivia quiz", "Word smarts", "Logic puzzles"
    };

    /** Which entry of CATEGORIES each test belongs to. */
    static final int[] CATEGORY = {
            1, 1, 0, 2,
            0, 0, 3, 1,
            0, 0, 0, 2,
            2, 2, 2, 0,
            1, 2, 1, 3,
            3, 3, 3, 3
    };

    /** One-line summary for the list. */
    static final String[] HINTS = {
            "Tap the moment the panel turns green.",
            "As many sums as you can in 60 seconds.",
            "Memorize a number and type it back.",
            "Tap the ink color, not the word.",
            "Repeat a growing pattern of flashing tiles.",
            "Tap hidden numbers in order from memory.",
            "Work out what number comes next.",
            "Hit 20 targets as fast as you can.",
            "Have you seen this word already?",
            "Remember which tiles lit up.",
            "Find the matching pairs in the fewest moves.",
            "Tap 1 to 25 in order on a scrambled grid.",
            "Find the tile that's a slightly different shade.",
            "Tap on green, hold back on red.",
            "Which way does the middle arrow point?",
            "Does this letter match the one two back?",
            "Count the dots before they vanish.",
            "Stop the hidden clock at the right moment.",
            "Type a sentence as fast and clean as you can.",
            "Pick the word hiding in the jumbled letters.",
            "Keep a running total in your head.",
            "Ten general-knowledge questions.",
            "Pick what the word means.",
            "Riddles, analogies and odd-one-out."
    };

    /** Full instructions shown before the test starts. */
    static final String[] HOWTO = {
            "Tap the panel to arm it. It turns red: wait. After a random pause it turns "
                    + "green: tap as fast as you can. You get five tries and your score is "
                    + "the average. Tapping before green doesn't count and you redo that try.",
            "A sum appears with four answers. Tap the right one. You have 60 seconds, and the "
                    + "sums get harder as you go. A wrong answer just moves you on, so don't "
                    + "freeze: speed matters more than being perfect.",
            "A number flashes on screen for a few seconds, then disappears. Type it back "
                    + "exactly. Get it right and the next number is one digit longer. The "
                    + "test ends at your first mistake. Tip: read the digits in chunks of "
                    + "three or four, like a phone number.",
            "A color word appears, printed in a different color. Tap the button for the "
                    + "color of the ink and ignore what the word says. There are 20 rounds. "
                    + "Your brain reads the word automatically, so the trick is to look at "
                    + "the color without reading.",
            "Tiles on a 3 by 3 grid light up one after another. When it's your turn, tap the "
                    + "same tiles in the same order. Each level adds one more tile to the "
                    + "end of the pattern. One wrong tap ends the test.",
            "Numbered squares appear scattered on a grid. Take as long as you like to study "
                    + "them. The moment you tap number 1, the rest go blank. Tap them in "
                    + "order from memory. Clear the board and you get one more number next "
                    + "round. Three misses and it's over.",
            "You see five numbers that follow a rule: adding, multiplying, growing gaps, or "
                    + "each number built from the ones before. Pick the sixth number. Ten "
                    + "questions. Tip: look at the gaps between neighbors first.",
            "A target appears somewhere in the box. Tap it and the next one appears. Hit 20. "
                    + "Your score is the average time per target, so stay loose and keep "
                    + "your thumb near the middle.",
            "Words appear one at a time. Tap SEEN if this word has already come up in this "
                    + "round, or NEW if it's the first time. The list keeps growing, so it "
                    + "gets harder. You have three lives.",
            "Some tiles on a grid light up briefly, all at once. When they go dark, tap the "
                    + "ones that were lit. Each level lights more tiles and the grid grows. "
                    + "Three wrong taps in total ends the test.",
            "Sixteen cards lie face down, hiding eight pairs of symbols. Flip two at a time. "
                    + "A matching pair stays up; otherwise they turn back over. Clear the "
                    + "board in as few moves as you can. Fewer moves beats faster time.",
            "A 5 by 5 grid holds the numbers 1 to 25 in random order. Tap them in order from "
                    + "1 to 25 as fast as you can. Tip: keep your eyes on the center and use "
                    + "your side vision to find the next number instead of scanning.",
            "Every tile is the same color except one that's a slightly different shade. Tap "
                    + "the odd one. Each one you find makes the next difference smaller. You "
                    + "have 30 seconds, and a wrong tap costs you two of them.",
            "A circle flashes green or red. Tap for green as fast as you can. Do nothing for "
                    + "red. There are 25 flashes. Most are green, so your hand gets used to "
                    + "tapping; the test is whether you can stop yourself on red.",
            "A row of five arrows appears. Tap Left or Right to match the middle arrow only. "
                    + "The arrows around it sometimes point the other way to throw you off. "
                    + "20 rounds, scored on correct answers and then speed.",
            "Letters appear one at a time. Press MATCH whenever the letter on screen is the "
                    + "same as the one shown two letters ago. Otherwise do nothing. For "
                    + "example in the run K, T, K the second K is a match. It's hard on "
                    + "purpose; this one really stretches working memory.",
            "A scatter of dots flashes for under a second. Then pick how many there were. "
                    + "Ten rounds, and the counts climb. Tip: don't count one by one. See "
                    + "them as small groups and add the groups.",
            "You're given a target, like 10 seconds. Tap Start, then tap Stop when you think "
                    + "exactly that much time has passed. There's no clock on screen. Three "
                    + "rounds; your score is how far off you were on average.",
            "A sentence appears. Tap the box and type it exactly, including capitals and "
                    + "punctuation. The clock starts on your first letter and stops when "
                    + "you reach the end. Score is words per minute, reduced for mistakes.",
            "The letters of a word have been shuffled. Pick the real word from four choices. "
                    + "Ten rounds, scored on correct answers and then speed. Tip: look for "
                    + "common endings like -ER, -ING or -LE.",
            "You're given a starting number, then four steps flash by one at a time, like "
                    + "plus 7 or times 2. Keep the running total in your head and pick the "
                    + "final answer. Six rounds. You can't go back, so say each total to "
                    + "yourself as you go.",
            "Ten multiple-choice questions on science, geography, history and odd facts, "
                    + "drawn at random from a bigger pool. The right answer is shown after "
                    + "each one, so you learn something even when you miss.",
            "A word appears with four possible meanings. Pick the one that means the same. "
                    + "Ten multiple-choice questions, not too hard. The right answer is "
                    + "shown after each one.",
            "Ten multiple-choice brain teasers: analogies (this is to that as...), which "
                    + "one doesn't belong, and classic trick questions. Read carefully; "
                    + "several are built to catch a fast first guess."
    };

    /** What each test exercises. */
    static final String[] TRAINS = {
            "Raw reaction speed.",
            "Mental arithmetic under time pressure.",
            "Short-term memory for digits.",
            "Blocking out a distraction you can't help noticing.",
            "Memory for order.",
            "Snapshot memory for positions.",
            "Spotting rules and patterns.",
            "Hand-eye speed and accuracy.",
            "Recognition memory for words.",
            "Memory for patterns in space.",
            "Remembering where things are.",
            "Fast visual search and wide attention.",
            "Fine color discrimination and scanning.",
            "Self-control: stopping an action you were about to make.",
            "Focusing on one thing while ignoring its neighbors.",
            "Working memory: holding and updating information.",
            "Quick estimation of quantity.",
            "Your internal sense of time.",
            "Typing speed and accuracy.",
            "Word recognition and mental rearranging.",
            "Holding and updating numbers in your head.",
            "General knowledge.",
            "Vocabulary.",
            "Careful reasoning."
    };

    /** A badge for each test. */
    static final String[] ICONS = {
            "\u26A1", "\uD83E\uDDEE", "\uD83D\uDD22", "\uD83C\uDFA8",
            "\uD83D\uDCA1", "\uD83D\uDC35", "\uD83E\uDDE9", "\uD83C\uDFAF",
            "\uD83D\uDCAC", "\uD83D\uDC41", "\uD83C\uDCCF", "\uD83C\uDFC1",
            "\uD83D\uDD0D", "\uD83D\uDEA6", "\u2194", "\uD83D\uDD24",
            "\uD83C\uDFB2", "\u23F1", "\u2328", "\uD83D\uDD00",
            "\uD83D\uDD17", "\u2753", "\uD83D\uDCDA", "\uD83E\uDD14"
    };

    static final int COUNT = TITLES.length;

    private Games() {}
}
