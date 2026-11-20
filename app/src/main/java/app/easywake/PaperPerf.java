package app.easywake;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The math behind the paper account's Performance page, with no Android code so it can be
 * tested on its own.
 *
 * The account never saved its value over time, so the history is rebuilt: start from what
 * you hold now, step backwards through your trades to find what you held on each past day,
 * and price each day with real closing prices.
 */
final class PaperPerf {
    static final char OPEN = 'O', RESET = 'R', DEPOSIT = 'D', BUY = 'B', SELL = 'S';

    private PaperPerf() {
    }

    /** One thing that changed the account. */
    static final class Event {
        long ms;
        char kind;
        String sym = "";
        double qty;
        double price;
        /** Cash amount for OPEN, RESET and DEPOSIT. */
        double amount;
        /** Profit or loss on a SELL. */
        double gain;

        static Event of(long ms, char kind, String sym, double qty, double price, double amount,
                        double gain) {
            Event e = new Event();
            e.ms = ms;
            e.kind = kind;
            e.sym = sym == null ? "" : sym;
            e.qty = qty;
            e.price = price;
            e.amount = amount;
            e.gain = gain;
            return e;
        }
    }

    // ------------------------------------------------------------------ old trade log

    private static final Pattern BOUGHT = Pattern.compile(
            "Bought ([0-9.,]+) (\\S+) at \\$([0-9.,]+)");
    private static final Pattern SOLD = Pattern.compile(
            "Sold ([0-9.,]+) (\\S+) at \\$([0-9.,]+).*\\(([+-])\\$([0-9.,]+)\\)");
    private static final Pattern ADDED = Pattern.compile("Added \\$([0-9.,]+) for");
    private static final Pattern OPENED = Pattern.compile("Account opened with \\$([0-9.,]+)");
    private static final Pattern RESETTO = Pattern.compile("Account reset to \\$([0-9.,]+)");

    private static double num(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Turns the old text trade log ("11/19/26 8:24 PM  Bought 5 AAPL at $230.00 = ...") into
     * events. Lines that can't be read are skipped.
     *
     * @param dateFmt the phone's short date format, the one the log was written with
     * @param timeFmt the phone's time format
     */
    static List<Event> parseHistory(List<String> lines, DateFormat dateFmt, DateFormat timeFmt) {
        List<Event> out = new ArrayList<>();
        for (String line : lines) {
            int split = line.indexOf("  ");
            if (split <= 0) continue;
            String stamp = line.substring(0, split).trim();
            String text = line.substring(split).trim();
            long ms = parseStamp(stamp, dateFmt, timeFmt);
            if (ms <= 0) continue;
            Matcher m;
            if ((m = BOUGHT.matcher(text)).find()) {
                out.add(Event.of(ms, BUY, m.group(2), num(m.group(1)), num(m.group(3)), 0, 0));
            } else if ((m = SOLD.matcher(text)).find()) {
                double g = num(m.group(5)) * ("-".equals(m.group(4)) ? -1 : 1);
                out.add(Event.of(ms, SELL, m.group(2), num(m.group(1)), num(m.group(3)), 0, g));
            } else if ((m = ADDED.matcher(text)).find()) {
                out.add(Event.of(ms, DEPOSIT, "", 0, 0, num(m.group(1)), 0));
            } else if ((m = OPENED.matcher(text)).find()) {
                out.add(Event.of(ms, OPEN, "", 0, 0, num(m.group(1)), 0));
            } else if ((m = RESETTO.matcher(text)).find()) {
                out.add(Event.of(ms, RESET, "", 0, 0, num(m.group(1)), 0));
            }
        }
        // Drop anything with numbers that didn't parse.
        List<Event> good = new ArrayList<>();
        for (Event e : out) {
            if (!Double.isNaN(e.qty) && !Double.isNaN(e.price) && !Double.isNaN(e.amount)
                    && !Double.isNaN(e.gain)) {
                good.add(e);
            }
        }
        sort(good);
        return good;
    }

    /** "11/19/26 8:24 PM": the date is everything before the first space. */
    private static long parseStamp(String stamp, DateFormat dateFmt, DateFormat timeFmt) {
        int sp = stamp.indexOf(' ');
        if (sp <= 0) return 0;
        try {
            java.util.Date d = dateFmt.parse(stamp.substring(0, sp).trim());
            java.util.Date t = timeFmt.parse(stamp.substring(sp + 1).trim());
            Calendar day = Calendar.getInstance();
            day.setTime(d);
            Calendar time = Calendar.getInstance();
            time.setTime(t);
            day.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
            day.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            return day.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    static void sort(List<Event> events) {
        // A stable sort keeps same-minute trades in the order they were written.
        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return Long.compare(a.ms, b.ms);
            }
        });
    }

    /** Only the events since the account was last opened or reset matter. */
    static List<Event> sinceStart(List<Event> all) {
        int from = 0;
        for (int i = 0; i < all.size(); i++) {
            char k = all.get(i).kind;
            if (k == OPEN || k == RESET) from = i;
        }
        return new ArrayList<>(all.subList(from, all.size()));
    }

    // ------------------------------------------------------------------ price history

    /** Daily closes for one symbol (times are each bar's start, epoch ms). */
    static final class Prices {
        long[] times = new long[0];
        double[] closes = new double[0];
        /** Latest price, and yesterday's close, for today's change. */
        double last = Double.NaN;
        double prevClose = Double.NaN;

        /** The last close on or before time t, or NaN if there is none. */
        double at(long t) {
            double v = Double.NaN;
            for (int i = 0; i < times.length; i++) {
                if (times[i] <= t) v = closes[i];
                else break;
            }
            return v;
        }
    }

    /** A regular session ends six and a half hours after the daily bar's start time. */
    static final long CLOSE_AFTER = (long) (6.5 * 3600 * 1000);

    // ------------------------------------------------------------------ the rebuild

    static final class History {
        /** One point per trading day, then a last point for right now. */
        long[] times = new long[0];
        double[] value = new double[0];
        /** The same money put into the S&P 500 on the same days. */
        double[] bench = new double[0];
        /** Money put in by that point. */
        double[] deposited = new double[0];
        /** True if the trade log reaches back to when the account was opened. */
        boolean complete;
    }

    /**
     * Rebuilds the account's value over time.
     *
     * @param cash      cash now
     * @param depositedNow money put in so far, as the account records it
     * @param holdings  shares held now, by symbol
     * @param events    every saved event, oldest first
     * @param px        daily closes (and latest price) for every symbol ever traded
     * @param spx       daily closes for the S&P 500 fund used to compare against
     * @param now       the time now (epoch ms)
     */
    static History rebuild(double cash, double depositedNow, Map<String, Double> holdings,
                           List<Event> events,
                           Map<String, Prices> px, Prices spx, long now) {
        History h = new History();
        List<Event> ev = sinceStart(events);
        if (ev.isEmpty()) {
            // No readable trade log: show just today.
            h.times = new long[]{now};
            h.value = new double[]{cash + valueNow(holdings, px, new HashMap<String, Double>())};
            h.bench = new double[]{depositedNow};
            h.deposited = new double[]{depositedNow};
            return h;
        }
        h.complete = ev.get(0).kind == OPEN || ev.get(0).kind == RESET;

        // Work backwards from now to what the account looked like before its first event.
        double c = cash;
        Map<String, Double> q = new HashMap<>(holdings);
        for (int i = ev.size() - 1; i >= 0; i--) {
            Event e = ev.get(i);
            switch (e.kind) {
                case BUY:
                    q.put(e.sym, get(q, e.sym) - e.qty);
                    c += e.qty * e.price;
                    break;
                case SELL:
                    q.put(e.sym, get(q, e.sym) + e.qty);
                    c -= e.qty * e.price;
                    break;
                case DEPOSIT:
                    c -= e.amount;
                    break;
                default:
                    break;
            }
        }
        // Before an OPEN or RESET there was nothing; start from its amount instead.
        if (h.complete) {
            c = 0;
            q.clear();
        }
        double dep = 0;
        if (!h.complete) {
            // The log is cut off: count what was there at the start as money put in.
            dep = c + valueAt(q, px, ev.get(0).ms, null);
        }

        // Trading days to plot: the S&P fund's days from the first event to now.
        long start = ev.get(0).ms;
        List<Long> days = new ArrayList<>();
        for (long t : spx.times) {
            if (t + CLOSE_AFTER >= start && t + CLOSE_AFTER <= now) days.add(t);
        }
        int n = days.size() + 1;
        h.times = new long[n];
        h.value = new double[n];
        h.bench = new double[n];
        h.deposited = new double[n];

        // Last trade price per symbol: a fallback when a day's close is missing.
        Map<String, Double> lastTrade = new HashMap<>();
        double units = 0;
        if (!h.complete && dep > 0) {
            double p0 = spx.at(start);
            if (p0 > 0) units = dep / p0;
        }
        int k = 0;
        for (int d = 0; d < n; d++) {
            boolean live = d == n - 1;
            long close = live ? now : days.get(d) + CLOSE_AFTER;
            while (k < ev.size() && ev.get(k).ms <= close) {
                Event e = ev.get(k++);
                double sp = live || Double.isNaN(spx.at(e.ms)) ? spx.last : spx.at(e.ms);
                switch (e.kind) {
                    case OPEN:
                    case RESET:
                        c = e.amount;
                        q.clear();
                        dep = e.amount;
                        units = sp > 0 ? e.amount / sp : 0;
                        break;
                    case DEPOSIT:
                        c += e.amount;
                        dep += e.amount;
                        if (sp > 0) units += e.amount / sp;
                        break;
                    case BUY:
                        q.put(e.sym, get(q, e.sym) + e.qty);
                        c -= e.qty * e.price;
                        lastTrade.put(e.sym, e.price);
                        break;
                    case SELL:
                        q.put(e.sym, get(q, e.sym) - e.qty);
                        c += e.qty * e.price;
                        lastTrade.put(e.sym, e.price);
                        break;
                    default:
                        break;
                }
            }
            h.times[d] = close;
            h.value[d] = c + (live ? valueNow(q, px, lastTrade) : valueAt(q, px, close,
                    lastTrade));
            double spxPrice = live ? spx.last : spx.at(close);
            h.bench[d] = Double.isNaN(spxPrice) ? (d > 0 ? h.bench[d - 1] : dep)
                    : units * spxPrice;
            h.deposited[d] = dep;
        }
        // The last point is exactly what the account holds now, whatever the log says.
        Map<String, Double> none = new HashMap<>();
        h.value[n - 1] = cash + valueNow(holdings, px, lastTrade.isEmpty() ? none : lastTrade);
        if (h.complete) h.deposited[n - 1] = depositedNow;
        return h;
    }

    private static double get(Map<String, Double> m, String k) {
        Double v = m.get(k);
        return v == null ? 0 : v;
    }

    private static double valueAt(Map<String, Double> q, Map<String, Prices> px, long t,
                                  Map<String, Double> fallback) {
        double v = 0;
        for (Map.Entry<String, Double> e : q.entrySet()) {
            if (Math.abs(e.getValue()) < 1e-9) continue;
            Prices p = px.get(e.getKey());
            double price = p == null ? Double.NaN : p.at(t);
            if (Double.isNaN(price) && fallback != null && fallback.get(e.getKey()) != null) {
                price = fallback.get(e.getKey());
            }
            if (!Double.isNaN(price)) v += e.getValue() * price;
        }
        return v;
    }

    private static double valueNow(Map<String, Double> q, Map<String, Prices> px,
                                   Map<String, Double> fallback) {
        double v = 0;
        for (Map.Entry<String, Double> e : q.entrySet()) {
            if (Math.abs(e.getValue()) < 1e-9) continue;
            Prices p = px.get(e.getKey());
            double price = p == null ? Double.NaN : p.last;
            if (Double.isNaN(price) && p != null && p.closes.length > 0) {
                price = p.closes[p.closes.length - 1];
            }
            if (Double.isNaN(price) && fallback.get(e.getKey()) != null) {
                price = fallback.get(e.getKey());
            }
            if (!Double.isNaN(price)) v += e.getValue() * price;
        }
        return v;
    }

    // ------------------------------------------------------------------ the scoreboard

    static final class Stats {
        int sells;
        int wins;
        double realized;
        double avgWin;
        double avgLoss;
        double best = Double.NaN;
        String bestSym = "";
        double worst = Double.NaN;
        String worstSym = "";
        /** Deepest fall from a high, as a percent of money put in. */
        double maxDip;
        int buys;
    }

    static Stats stats(List<Event> events, History h) {
        Stats s = new Stats();
        double winSum = 0;
        double lossSum = 0;
        int losses = 0;
        for (Event e : sinceStart(events)) {
            if (e.kind == BUY) s.buys++;
            if (e.kind != SELL) continue;
            s.sells++;
            s.realized += e.gain;
            if (e.gain > 0) {
                s.wins++;
                winSum += e.gain;
            } else if (e.gain < 0) {
                losses++;
                lossSum += e.gain;
            }
            if (Double.isNaN(s.best) || e.gain > s.best) {
                s.best = e.gain;
                s.bestSym = e.sym;
            }
            if (Double.isNaN(s.worst) || e.gain < s.worst) {
                s.worst = e.gain;
                s.worstSym = e.sym;
            }
        }
        s.avgWin = s.wins > 0 ? winSum / s.wins : 0;
        s.avgLoss = losses > 0 ? lossSum / losses : 0;
        // Worst dip: measured on value per dollar put in, so deposits don't count as gains.
        double peak = Double.NaN;
        for (int i = 0; i < h.value.length; i++) {
            if (h.deposited[i] <= 0) continue;
            double r = h.value[i] / h.deposited[i];
            if (Double.isNaN(peak) || r > peak) peak = r;
            double dip = (peak - r) / peak * 100;
            if (dip > s.maxDip) s.maxDip = dip;
        }
        return s;
    }

    /** Every symbol in the events or held now: all need price history. */
    static Set<String> symbols(List<Event> events, Map<String, Double> holdings) {
        Set<String> out = new HashSet<>(holdings.keySet());
        for (Event e : sinceStart(events)) {
            if (!e.sym.isEmpty()) out.add(e.sym);
        }
        return out;
    }
}
