package app.easywake;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extra things the helper understands: unit conversions, job-site sums, tips, dates,
 * timers, coin flips and a few words of small talk. Everything here works offline.
 */
final class Helper2 {
    private static final Random RND = new Random();
    private static final String NUM = "([0-9][0-9,]*\\.?[0-9]*|\\.[0-9]+)";

    private Helper2() {}

    /** Unit name to {kind, size in the kind's base unit}. */
    private static final Map<String, double[]> UNITS = new HashMap<>();
    private static final Map<String, String> UNIT_LABEL = new HashMap<>();

    private static void unit(int kind, double size, String label, String... names) {
        for (String n : names) {
            UNITS.put(n, new double[]{kind, size});
            UNIT_LABEL.put(n, label);
        }
    }

    static {
        // Length, in meters.
        unit(0, 0.0254, "inches", "in", "inch", "inches", "\"");
        unit(0, 0.3048, "feet", "ft", "foot", "feet", "'");
        unit(0, 0.9144, "yards", "yd", "yard", "yards");
        unit(0, 1609.344, "miles", "mi", "mile", "miles");
        unit(0, 0.001, "millimeters", "mm", "millimeter", "millimeters");
        unit(0, 0.01, "centimeters", "cm", "centimeter", "centimeters");
        unit(0, 1, "meters", "m", "meter", "meters");
        unit(0, 1000, "kilometers", "km", "kilometer", "kilometers");
        // Weight, in grams.
        unit(1, 28.349523, "ounces", "oz", "ounce", "ounces");
        unit(1, 453.59237, "pounds", "lb", "lbs", "pound", "pounds");
        unit(1, 1, "grams", "g", "gram", "grams");
        unit(1, 1000, "kilograms", "kg", "kilo", "kilos", "kilogram", "kilograms");
        unit(1, 907184.74, "tons", "ton", "tons");
        // Volume, in milliliters (US measures).
        unit(2, 4.92892, "teaspoons", "tsp", "teaspoon", "teaspoons");
        unit(2, 14.7868, "tablespoons", "tbsp", "tablespoon", "tablespoons");
        unit(2, 29.5735, "fluid ounces", "floz", "fl oz", "fluid ounce", "fluid ounces");
        unit(2, 236.588, "cups", "cup", "cups");
        unit(2, 473.176, "pints", "pt", "pint", "pints");
        unit(2, 946.353, "quarts", "qt", "quart", "quarts");
        unit(2, 3785.41, "gallons", "gal", "gallon", "gallons");
        unit(2, 1, "milliliters", "ml", "milliliter", "milliliters");
        unit(2, 1000, "liters", "l", "liter", "liters", "litre", "litres");
        unit(2, 28316.8, "cubic feet", "cubic feet", "cubic foot", "cu ft");
        unit(2, 764555, "cubic yards", "cubic yards", "cubic yard", "cu yd", "yards of concrete");
        // Area, in square meters.
        unit(3, 0.092903, "square feet", "sq ft", "sqft", "square feet", "square foot");
        unit(3, 1, "square meters", "sq m", "sqm", "square meters", "square meter");
        unit(3, 4046.86, "acres", "acre", "acres");
        unit(3, 0.836127, "square yards", "sq yd", "square yards", "square yard");
        // Speed, in meters per second.
        unit(4, 0.44704, "miles an hour", "mph", "miles per hour", "miles an hour");
        unit(4, 0.277778, "kilometers an hour", "kph", "kmh", "km/h", "kilometers per hour");
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static String f(double v) {
        if (Math.abs(v) >= 100) return String.format(Locale.US, "%,.1f", v).replace(".0", "");
        if (Math.abs(v) >= 1) return String.format(Locale.US, "%,.2f", v)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
        return String.format(Locale.US, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Returns an answer, or null if this message isn't one of these kinds of request. */
    static String answer(Context c, String raw) {
        String t = raw.toLowerCase(Locale.US).trim().replaceAll("[?!]+$", "").trim();
        String out;
        if ((out = timer(c, t, raw)) != null) return out;
        if ((out = temperature(t)) != null) return out;
        if ((out = convert(t)) != null) return out;
        if ((out = jobSite(t)) != null) return out;
        if ((out = money(t)) != null) return out;
        if ((out = dates(t)) != null) return out;
        if ((out = chance(t)) != null) return out;
        if ((out = progress(c, t)) != null) return out;
        return smallTalk(c, t);
    }

    // ---------- timers ----------

    private static String timer(Context c, String t, String raw) {
        Matcher m = Pattern.compile("^(?:set (?:a )?)?timer (?:for )?" + NUM
                + " ?(sec|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours)"
                + "(?: (?:to|for) (.+))?$").matcher(t);
        Matcher r = Pattern.compile("^remind me in " + NUM
                + " ?(min|mins|minute|minutes|hr|hrs|hour|hours)(?: (?:to|that) (.+))?$")
                .matcher(t);
        Matcher hit = m.find() ? m : (r.find() ? r : null);
        if (hit == null) return null;
        double amount = num(hit.group(1));
        String u = hit.group(2);
        if (u.startsWith("sec")) {
            return "I can only time whole minutes, because the reminder arrives as a "
                    + "notification. Try \"timer 2 minutes\".";
        }
        long minutes = Math.round(u.startsWith("h") ? amount * 60 : amount);
        if (minutes < 1 || minutes > 24 * 60 - 1) {
            return "I can set a timer from 1 minute up to just under 24 hours.";
        }
        Calendar when = Calendar.getInstance();
        when.add(Calendar.MINUTE, (int) minutes);
        String what = hit.group(3);
        Reminders.Item it = new Reminders.Item();
        it.id = Reminders.newId(c);
        it.text = what == null || what.trim().isEmpty() ? "Timer finished (" + minutes
                + " min)" : what.trim().substring(0, 1).toUpperCase(Locale.US)
                + what.trim().substring(1);
        it.hour = when.get(Calendar.HOUR_OF_DAY);
        it.minute = when.get(Calendar.MINUTE);
        it.days = 0;
        it.on = true;
        List<Reminders.Item> all = Reminders.load(c);
        all.add(it);
        Reminders.save(c, all);
        Reminders.scheduleAll(c);
        return "Done. I'll send a notification at " + new SimpleDateFormat("h:mm a",
                Locale.US).format(when.getTime()) + ": \"" + it.text + "\". It goes off "
                + "on the minute, so it can be up to a minute early.";
    }

    // ---------- conversions ----------

    private static String temperature(String t) {
        Matcher m = Pattern.compile("(-?" + NUM + ") ?(?:degrees? ?)?(f|fahrenheit|c|celsius)"
                + " (?:to|in|into|as) (?:degrees? ?)?(f|fahrenheit|c|celsius)$").matcher(t);
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        boolean fromF = m.group(3).startsWith("f");
        boolean toF = m.group(4).startsWith("f");
        if (fromF == toF) return f(v) + " degrees. Same scale on both sides.";
        double out = fromF ? (v - 32) * 5 / 9 : v * 9 / 5 + 32;
        return f(v) + (fromF ? " °F" : " °C") + " = " + f(out)
                + (toF ? " °F" : " °C");
    }

    private static String convert(String t) {
        String s = t.replaceAll("^(convert|what is|what's|how many|how much is) ", "");
        // "how many feet in 3 miles" reads backwards from "3 miles in feet".
        Matcher back = Pattern.compile("^([a-z\"' /]+?) (?:are )?(?:in|is) (?:a |an )?" + NUM
                + "? ?([a-z\"' /]+)$").matcher(s);
        Matcher m = Pattern.compile("^" + NUM + " ?([a-z\"' /]+?) (?:to|in|into|as|=) "
                + "([a-z\"' /]+)$").matcher(s);
        double amount;
        String from;
        String to;
        if (m.find()) {
            amount = num(m.group(1));
            from = m.group(2).trim();
            to = m.group(3).trim();
        } else if (t.startsWith("how many") && back.find()) {
            amount = back.group(2) == null ? 1 : num(back.group(2));
            from = back.group(3).trim();
            to = back.group(1).trim();
        } else {
            return null;
        }
        double[] a = UNITS.get(from);
        double[] b = UNITS.get(to);
        if (a == null || b == null) return null;
        if (a[0] != b[0]) {
            return "Those measure different things, so I can't convert " + UNIT_LABEL.get(from)
                    + " to " + UNIT_LABEL.get(to) + ".";
        }
        double out = amount * a[1] / b[1];
        String extra = "";
        if (to.startsWith("f") && a[0] == 0 && out != Math.floor(out)) {
            // Feet read better as feet and inches on a tape measure.
            int feet = (int) Math.floor(out);
            extra = "  (" + feet + " ft " + f((out - feet) * 12) + " in)";
        }
        return f(amount) + " " + UNIT_LABEL.get(from) + " = " + f(out) + " "
                + UNIT_LABEL.get(to) + extra;
    }

    // ---------- job-site sums ----------

    private static String jobSite(String t) {
        String x = "\\s*(?:x|by|\\*)\\s*";
        Matcher m = Pattern.compile("concrete.*?" + NUM + x + NUM + x + NUM).matcher(t);
        if (m.find()) {
            double len = num(m.group(1));
            double wid = num(m.group(2));
            double thick = num(m.group(3));
            double cuFt = len * wid * thick / 12;
            double yards = cuFt / 27;
            return "A slab " + f(len) + " ft by " + f(wid) + " ft, " + f(thick)
                    + " inches thick:\n• " + f(cuFt) + " cubic feet\n• " + f(yards)
                    + " cubic yards (order about " + f(Math.ceil(yards * 1.1 * 4) / 4)
                    + " with 10% extra)\n• or about " + (int) Math.ceil(cuFt / 0.6)
                    + " bags of 80 lb mix, at roughly 0.6 cubic feet a bag\n"
                    + "I read that as feet, feet, then inches of thickness.";
        }
        m = Pattern.compile("board (?:feet|foot|ft).*?" + NUM + x + NUM + x + NUM).matcher(t);
        if (m.find()) {
            double bf = num(m.group(1)) * num(m.group(2)) * num(m.group(3)) / 12;
            return f(bf) + " board feet. That's thickness in inches times width in inches "
                    + "times length in feet, divided by 12.";
        }
        m = Pattern.compile("(?:drywall|sheetrock|plywood).*?" + NUM
                + "(?:" + x + NUM + ")?").matcher(t);
        if (m.find()) {
            double area = num(m.group(1)) * (m.group(2) == null ? 1 : num(m.group(2)));
            int sheets = (int) Math.ceil(area / 32);
            return f(area) + " square feet takes " + sheets + " sheets of 4 by 8 ("
                    + (int) Math.ceil(area * 1.1 / 32) + " with 10% for waste and cuts).";
        }
        m = Pattern.compile("paint.*?" + NUM + "(?:" + x + NUM + ")?").matcher(t);
        if (m.find() && t.contains("paint")) {
            double area = num(m.group(1)) * (m.group(2) == null ? 1 : num(m.group(2)));
            double gallons = area / 350;
            return f(area) + " square feet needs about " + f(gallons) + " gallons a coat, "
                    + "at roughly 350 square feet a gallon. Two coats: " + f(gallons * 2)
                    + " gallons. Check the can, since coverage varies.";
        }
        m = Pattern.compile("stairs?.*?" + NUM).matcher(t);
        if (m.find() && t.matches(".*stair.*")) {
            double rise = num(m.group(1));
            if (rise < 8) return "Give me the total rise in inches, like \"stairs 108\".";
            int risers = (int) Math.ceil(rise / 7.75);
            double each = rise / risers;
            return "For a total rise of " + f(rise) + " inches:\n• " + risers
                    + " risers of " + f(each) + " inches each\n• " + (risers - 1)
                    + " treads; at 10 inches each that's a run of " + f((risers - 1) * 10)
                    + " inches\nThat keeps each riser at or under 7 3/4 inches, a common "
                    + "residential limit. Codes differ, so check yours.";
        }
        m = Pattern.compile("(?:square (?:feet|footage)|sq ?ft|area).*?" + NUM + x + NUM)
                .matcher(t);
        if (m.find()) {
            double area = num(m.group(1)) * num(m.group(2));
            return f(num(m.group(1))) + " by " + f(num(m.group(2))) + " is " + f(area)
                    + " square feet (" + f(area / 9) + " square yards).";
        }
        return null;
    }

    // ---------- money ----------

    private static String money(String t) {
        Matcher m = Pattern.compile("(?:" + NUM + " ?(?:%|percent) )?tip (?:on|for) \\$?" + NUM)
                .matcher(t);
        if (m.find()) {
            double bill = num(m.group(2));
            if (m.group(1) != null) {
                double pct = num(m.group(1));
                return f(pct) + "% tip on $" + money2(bill) + " is $" + money2(bill * pct / 100)
                        + ". Total $" + money2(bill * (1 + pct / 100)) + ".";
            }
            return "On a $" + money2(bill) + " bill:\n• 15%: $" + money2(bill * 0.15)
                    + " (total $" + money2(bill * 1.15) + ")\n• 18%: $"
                    + money2(bill * 0.18) + " (total $" + money2(bill * 1.18)
                    + ")\n• 20%: $" + money2(bill * 0.20) + " (total $"
                    + money2(bill * 1.20) + ")";
        }
        m = Pattern.compile("(?:percent|%) (?:change|gain|loss|difference) (?:from )?\\$?" + NUM
                + " to \\$?" + NUM).matcher(t);
        if (m.find()) {
            double a = num(m.group(1));
            double b = num(m.group(2));
            if (a == 0) return "I can't work out a percent change from zero.";
            double pct = (b - a) / a * 100;
            return "From " + f(a) + " to " + f(b) + " is " + (pct >= 0 ? "up " : "down ")
                    + f(Math.abs(pct)) + "%.";
        }
        m = Pattern.compile("split \\$?" + NUM + " (?:between |among |by |with )?" + NUM)
                .matcher(t);
        if (m.find()) {
            double people = num(m.group(2));
            if (people < 1) return null;
            return "$" + f(num(m.group(1))) + " split " + f(people) + " ways is $"
                    + money2(num(m.group(1)) / people) + " each.";
        }
        return null;
    }

    private static String money2(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    // ---------- dates ----------

    private static String dates(String t) {
        Calendar now = Calendar.getInstance();
        if (t.matches("(what(?:'s| is) the time|what time is it|time|current time)( now)?")) {
            return "It's " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date()) + ".";
        }
        if (t.matches("(what(?:'s| is) (?:the |today's )?date|what day is it|today's date|"
                + "what(?:'s| is) today|date)( today)?")) {
            return "Today is " + new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
                    .format(new Date()) + ".";
        }
        Matcher m = Pattern.compile("(?:how many |how long |days? )(?:days? )?(?:until|till|to|"
                + "before) (.+)$").matcher(t);
        if (!m.find()) return null;
        String what = m.group(1).trim();
        int month;
        int day;
        String label;
        if (what.contains("christmas")) {
            month = 11;
            day = 25;
            label = "Christmas";
        } else if (what.contains("new year")) {
            month = 0;
            day = 1;
            label = "New Year's Day";
        } else if (what.contains("halloween")) {
            month = 9;
            day = 31;
            label = "Halloween";
        } else if (what.contains("july 4") || what.contains("fourth of july")
                || what.contains("independence")) {
            month = 6;
            day = 4;
            label = "the Fourth of July";
        } else if (what.contains("valentine")) {
            month = 1;
            day = 14;
            label = "Valentine's Day";
        } else {
            String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
                    "oct", "nov", "dec"};
            Matcher d = Pattern.compile("([a-z]{3})[a-z]* (\\d{1,2})").matcher(what);
            month = -1;
            day = 0;
            if (d.find()) {
                for (int i = 0; i < 12; i++) {
                    if (months[i].equals(d.group(1))) month = i;
                }
                day = Integer.parseInt(d.group(2));
            }
            if (month < 0 || day < 1 || day > 31) return null;
            label = what.substring(0, 1).toUpperCase(Locale.US) + what.substring(1);
        }
        Calendar target = Calendar.getInstance();
        target.set(now.get(Calendar.YEAR), month, day, 0, 0, 0);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        if (target.getTimeInMillis() < today.getTimeInMillis() - 1000) {
            target.add(Calendar.YEAR, 1);
        }
        long days = Math.round((target.getTimeInMillis() - today.getTimeInMillis())
                / 86400000.0);
        if (days == 0) return "That's today.";
        return days + (days == 1 ? " day" : " days") + " until " + label + " ("
                + new SimpleDateFormat("EEEE, MMM d", Locale.US).format(target.getTime())
                + ").";
    }

    // ---------- coin, dice, random ----------

    private static String chance(String t) {
        if (t.matches(".*\\b(flip|toss)\\b.*\\bcoin\\b.*|heads or tails")) {
            return RND.nextBoolean() ? "Heads." : "Tails.";
        }
        Matcher m = Pattern.compile("roll (?:a |an |the )?(\\d+)? ?(?:dice|die|d6)").matcher(t);
        if (m.find()) {
            int n = m.group(1) == null ? 1 : Math.max(1, Math.min(6,
                    Integer.parseInt(m.group(1))));
            StringBuilder sb = new StringBuilder();
            int sum = 0;
            for (int i = 0; i < n; i++) {
                int v = 1 + RND.nextInt(6);
                sum += v;
                sb.append(i > 0 ? ", " : "").append(v);
            }
            return n == 1 ? "You rolled a " + sb + "." : "You rolled " + sb + ". Total "
                    + sum + ".";
        }
        m = Pattern.compile("(?:pick|choose|random|give me) (?:a )?(?:random )?number "
                + "(?:between|from) (\\d+) (?:and|to) (\\d+)").matcher(t);
        if (m.find()) {
            long a = Long.parseLong(m.group(1));
            long b = Long.parseLong(m.group(2));
            long lo = Math.min(a, b);
            long hi = Math.max(a, b);
            return String.valueOf(lo + (long) (RND.nextDouble() * (hi - lo + 1)));
        }
        m = Pattern.compile("^(?:pick|choose)(?: one)?(?: between| from)?:? (.+ or .+)$")
                .matcher(t);
        if (m.find()) {
            String[] options = m.group(1).split("\\s*,\\s*|\\s+or\\s+");
            String pick = options[RND.nextInt(options.length)].trim();
            if (!pick.isEmpty()) return "I pick: " + pick + ".";
        }
        return null;
    }

    // ---------- where you stand in the app ----------

    private static String progress(Context c, String t) {
        if (t.matches(".*(what can i (unlock|afford|buy)|what should i unlock|"
                + "anything to unlock|next unlock).*")) {
            StringBuilder can = new StringBuilder();
            StringBuilder soon = new StringBuilder();
            int kp = Scores.points(c);
            for (Tree.Node n : Tree.NODES) {
                if (n.kind == Tree.SOON || Tree.owned(c, n.id)) continue;
                String why = Tree.blocker(c, n);
                if (why == null) {
                    can.append("\n• ").append(n.name).append(" (").append(n.cost)
                            .append(")");
                } else if (soon.length() < 400 && why.startsWith("You need")) {
                    soon.append("\n• ").append(n.name).append(": ")
                            .append(n.cost - kp).append(" more points");
                }
            }
            if (can.length() == 0 && soon.length() == 0) {
                return "Nothing is within reach right now. Play a few tests to earn points "
                        + "and levels, then ask again.";
            }
            return "You have " + kp + " Knowledge Points."
                    + (can.length() > 0 ? "\n\nYou can unlock now:" + can : "")
                    + (soon.length() > 0 ? "\n\nClose:" + soon : "")
                    + "\n\nSay \"open skill tree\" to go there.";
        }
        if (t.matches(".*(my level|what level|next level|level up).*")) {
            int level = Progress.level(c);
            int need = Progress.threshold(level + 1) - Progress.xp(c);
            return "You're level " + level + ", " + Progress.title(level) + ". "
                    + Math.max(0, need) + " more experience to reach level " + (level + 1)
                    + ". Every test you finish adds some.";
        }
        if (t.matches(".*(what'?s new|whats new|new features|latest update).*")) {
            return "Newest things in the app:\n• Roulette, video poker and a five-reel slot\n"
                    + "• Over 1,000 trivia questions and 1,000 word meanings\n"
                    + "• A Rumble control for sleep sounds\n"
                    + "• Sudoku, in the Brain gym\n• Peg Pop "
                    + "on the skill tree\n• Blackjack and Texas Hold'em\n• A fuller "
                    + "economic calendar with live numbers\n• Four kinds of Number grid "
                    + "race\n• My profile, with your stats\n• I can now convert "
                    + "units, work out concrete and drywall, set timers and flip coins.";
        }
        return null;
    }

    // ---------- small talk ----------

    private static String smallTalk(Context c, String t) {
        String name = Prefs.getString(c, "playerName");
        String you = name == null || name.isEmpty() ? "" : ", " + name;
        if (t.matches("(hi|hello|hey|yo|sup|howdy|hiya)( there| helper| buddy| man)?")) {
            return "Hey" + you + ". What do you need?";
        }
        if (t.matches("(thanks|thank you|thx|ty|appreciate it|cheers)( a lot| so much| man)?")) {
            return "Any time" + you + ".";
        }
        if (t.matches("(how are you|how's it going|hows it going|how you doing|what's up|"
                + "whats up)( today)?")) {
            return "Running smoothly. I don't sleep, which is ironic for an alarm clock. "
                    + "What can I do for you?";
        }
        if (t.matches("(who are you|what are you|what's your name|whats your name|"
                + "are you (an )?ai|are you real|are you a robot).*")) {
            return "I'm the helper built into this app. I'm not an AI: I match what you "
                    + "type against things I know how to do, and it all runs on your "
                    + "phone. Say \"help\" to see the list.";
        }
        if (t.matches("(good ?night|night|night night|going to bed|bedtime)( helper)?")) {
            return "Good night" + you + ". Say \"next alarm\" if you want to double-check "
                    + "it, or \"play the fan\" for some noise.";
        }
        if (t.matches("(good morning|morning)( helper)?")) {
            return "Morning" + you + ". Say \"how's the market\", \"weather\" or \"tell me "
                    + "a joke\" to get going.";
        }
        if (t.matches("(i'?m bored|im bored|bored|entertain me|what should i do).*")) {
            return "Try the daily challenge in the Brain gym for double points, a Sudoku, "
                    + "or say \"tell me a joke\" or \"crazy fact\".";
        }
        if (t.matches(".*(i love you|you'?re (the best|great|awesome)|good (bot|job)).*")) {
            return "Appreciated" + you + ". I'll be here.";
        }
        if (t.matches(".*(you suck|you'?re (dumb|stupid|useless)|bad bot).*")) {
            return "Fair. I'm a simple helper. Say \"help\" to see what I'm good at.";
        }
        return null;
    }
}
