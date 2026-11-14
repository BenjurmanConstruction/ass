package app.easywake;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small calculator for the helper: + - x / ^, brackets, and percentages. */
final class Calc {
    private final String s;
    private int at;

    private Calc(String s) {
        this.s = s;
    }

    /**
     * Works out typed arithmetic such as "12.5 x 8", "(40 + 15) / 5" or "15% of 240".
     * Returns the answer as text, or null if the message isn't arithmetic.
     */
    static String answer(String raw) {
        String t = raw.toLowerCase(Locale.US).trim()
                .replaceAll("^(what is|what's|whats|calculate|calc|how much is|solve)\\s+", "")
                .replaceAll("[?=]+\\s*$", "").trim();

        Matcher of = Pattern.compile("^([\\d.,]+)\\s*(%|percent)\\s*of\\s*\\$?([\\d.,]+)$")
                .matcher(t);
        if (of.find()) {
            try {
                double pct = Double.parseDouble(of.group(1).replace(",", ""));
                double base = Double.parseDouble(of.group(3).replace(",", ""));
                return of.group(1) + "% of " + of.group(3) + " = " + fmt(pct * base / 100);
            } catch (Exception e) {
                return null;
            }
        }

        t = t.replace("plus", "+").replace("minus", "-").replace("times", "*")
                .replace("multiplied by", "*").replace("divided by", "/")
                .replace("over", "/").replace("\u00D7", "*").replace("\u00F7", "/")
                .replace("x", "*").replace("$", "").replace(",", "").replace(" ", "");
        if (!t.matches("[0-9.+\\-*/^()%]+")) return null;
        // Needs at least one number and one operator to count as a sum.
        if (!t.matches(".*\\d.*") || !t.matches(".*\\d.*[+\\-*/^%].*")) return null;
        try {
            Calc c = new Calc(t);
            double v = c.sum();
            if (c.at != t.length()) return null;
            if (Double.isNaN(v) || Double.isInfinite(v)) return "You can't divide by zero.";
            return pretty(t) + " = " + fmt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static String pretty(String t) {
        return t.replace("*", " \u00D7 ").replace("/", " \u00F7 ").replace("+", " + ")
                .replace("-", " \u2212 ").replace("  ", " ").trim();
    }

    static String fmt(double v) {
        if (Math.abs(v) >= 1e15) return String.valueOf(v);
        return new DecimalFormat("#,##0.######").format(v);
    }

    private double sum() {
        double v = product();
        while (at < s.length()) {
            char ch = s.charAt(at);
            if (ch == '+') {
                at++;
                v += product();
            } else if (ch == '-') {
                at++;
                v -= product();
            } else {
                break;
            }
        }
        return v;
    }

    private double product() {
        double v = power();
        while (at < s.length()) {
            char ch = s.charAt(at);
            if (ch == '*') {
                at++;
                v *= power();
            } else if (ch == '/') {
                at++;
                v /= power();
            } else {
                break;
            }
        }
        return v;
    }

    private double power() {
        double v = unary();
        if (at < s.length() && s.charAt(at) == '^') {
            at++;
            return Math.pow(v, power());
        }
        return v;
    }

    private double unary() {
        if (at < s.length() && s.charAt(at) == '-') {
            at++;
            return -unary();
        }
        if (at < s.length() && s.charAt(at) == '+') {
            at++;
            return unary();
        }
        double v;
        if (at < s.length() && s.charAt(at) == '(') {
            at++;
            v = sum();
            if (at >= s.length() || s.charAt(at) != ')') throw new IllegalStateException();
            at++;
        } else {
            int start = at;
            while (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) {
                at++;
            }
            if (start == at) throw new IllegalStateException();
            v = Double.parseDouble(s.substring(start, at));
        }
        // A trailing percent sign means "divided by 100".
        while (at < s.length() && s.charAt(at) == '%') {
            at++;
            v /= 100;
        }
        return v;
    }
}
