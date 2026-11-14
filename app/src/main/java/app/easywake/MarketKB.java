package app.easywake;

/**
 * Plain-English answers to common market questions, for the helper.
 * Each row: keywords separated by | (any one triggers it), then the answer.
 * More specific topics come first so they win over general ones.
 */
final class MarketKB {
    private MarketKB() {}

    static final String[][] ENTRIES = {
            {"support and resistance|find support|find resistance|support level|resistance level"
                    + "|support|resistance",
                    "Support is a price where a falling market has repeatedly stopped and "
                            + "bounced; resistance is where rallies have repeatedly stalled. "
                            + "To find them, open a chart on 6M or 1Y and look for prices the "
                            + "line has turned at two or three times. Old highs, old lows and "
                            + "round numbers often act as levels. Treat them as zones, not "
                            + "exact prices, and remember that a broken resistance level "
                            + "often becomes support. Levels fail regularly; they show where "
                            + "others may act, not what will happen."},
            {"trend line|trendline|uptrend|downtrend|find the trend|what is a trend",
                    "An uptrend is higher highs and higher lows; a downtrend is lower highs "
                            + "and lower lows. Draw an uptrend line by connecting the lows, a "
                            + "downtrend line by connecting the highs. Two points make the "
                            + "line and a third touch confirms it. The trend is considered "
                            + "intact until price clearly breaks the line and can't get back. "
                            + "Always check a longer range before judging."},
            {"moving average|ma 20|ma 50|ma20|ma50|golden cross|death cross",
                    "A moving average is the average price over the last so many points, "
                            + "drawn as a smooth line. Price above a rising average suggests "
                            + "an uptrend. In uptrends, pullbacks often stop near the "
                            + "average. A faster average crossing above a slower one is read "
                            + "as strengthening momentum (a 50-day crossing above the 200-day "
                            + "is called a golden cross; the reverse is a death cross). "
                            + "Averages lag, and they give false signals in sideways markets."},
            {"rsi|overbought|oversold",
                    "RSI (relative strength index) runs from 0 to 100 and compares recent "
                            + "gains with recent losses. Above 70 is commonly called "
                            + "overbought and below 30 oversold. That doesn't mean a reversal "
                            + "is due: in strong trends RSI can stay above 70 for weeks. It's "
                            + "more useful for spotting divergence, when price makes a new "
                            + "high but RSI doesn't."},
            {"macd",
                    "MACD subtracts a slow moving average (26) from a fast one (12). When the "
                            + "MACD line is above zero, short-term momentum is stronger than "
                            + "long-term. Traders also watch it cross its own 9-period "
                            + "average, the signal line: above is read as bullish, below as "
                            + "bearish. The bars show the gap between the two. Like all "
                            + "average-based tools, it lags."},
            {"bollinger",
                    "Bollinger Bands draw a band two standard deviations above and below a "
                            + "20-period moving average. The band widens when prices swing "
                            + "more and narrows when things go quiet. A very narrow band (a "
                            + "squeeze) often comes before a bigger move, though it doesn't "
                            + "say which direction. Price touching the upper band isn't a "
                            + "sell signal by itself; in strong trends price rides the band."},
            {"vwap",
                    "VWAP is the average price paid so far today, weighted by how many "
                            + "shares traded at each price. Many day traders treat it as the "
                            + "day's fair value: price above VWAP means buyers have been in "
                            + "control, below means sellers. Big institutions use it to judge "
                            + "whether they got a good fill. It resets every day and needs "
                            + "volume, so it works on stocks, ETFs and futures, not on the "
                            + "index numbers themselves."},
            {"candlestick|candle|wick|doji",
                    "Each candle covers one period. The thick body runs from the open to the "
                            + "close: green if it closed higher, red if lower. The thin wicks "
                            + "show the high and low. A long body means one side dominated. "
                            + "A small body with long wicks (a doji) shows indecision. A long "
                            + "lower wick means sellers pushed down and buyers pushed back. "
                            + "Candles mean more at support or resistance and on high volume."},
            {"trading volume|what is volume|volume mean|high volume|low volume",
                    "Volume is how many shares changed hands. A price move on high volume has "
                            + "more conviction behind it than the same move on thin volume. "
                            + "Breakouts through resistance are more trusted when volume "
                            + "jumps. Falling volume during a rally can hint that buyers are "
                            + "running out of steam."},
            {"stop loss|stop-loss|stop order|trailing stop",
                    "A stop-loss is an order to sell automatically if the price falls to a "
                            + "level you choose, to cap the loss. A trailing stop moves up as "
                            + "the price rises. Stops aren't guaranteed: if the price gaps "
                            + "down overnight, the order fills at the next available price, "
                            + "which can be well below your stop."},
            {"limit order|market order|order type",
                    "A market order buys or sells right now at whatever price is available; "
                            + "you're sure to trade but not sure of the price. A limit order "
                            + "sets the worst price you'll accept; you control the price but "
                            + "the order may never fill. Limit orders matter most for "
                            + "thinly traded stocks and outside regular hours, where prices "
                            + "can jump."},
            {"position siz|how much to risk|risk management|risk per trade",
                    "A common rule of thumb is to risk no more than 1% to 2% of your account "
                            + "on one idea. Risk means what you'd lose if wrong, not what you "
                            + "invest: buying at $50 with an exit at $46 risks $4 a share. "
                            + "With $10,000 and a 1% limit, that's 25 shares. A 50% loss "
                            + "needs a 100% gain to recover, which is why limiting losses "
                            + "matters so much. This is general education, not advice."},
            {"p/e|pe ratio|price to earnings|price-to-earnings|valuation",
                    "The P/E ratio is the share price divided by a year's earnings per "
                            + "share. A P/E of 20 means investors pay $20 for each $1 of "
                            + "yearly profit. High P/Es usually mean investors expect fast "
                            + "growth; low ones can mean slow growth or trouble. It's best "
                            + "compared with the company's own history and its competitors, "
                            + "not across unrelated industries."},
            {"dividend|yield",
                    "A dividend is a share of profits a company pays its shareholders, "
                            + "usually every quarter. Dividend yield is the yearly dividend "
                            + "divided by the share price. A very high yield can be a "
                            + "warning: it often means the price has fallen because the "
                            + "market doubts the dividend will last. For bonds, yield is the "
                            + "return you earn, and it moves opposite to the bond's price."},
            {"etf|index fund|mutual fund",
                    "An index fund buys everything in an index, such as the S&P 500, instead "
                            + "of picking stocks. An ETF is a fund that trades on the "
                            + "exchange like a stock; many ETFs are index funds. Both give "
                            + "broad diversification at low cost. A mutual fund is priced "
                            + "once a day rather than trading all day, and actively managed "
                            + "ones charge more."},
            {"diversif",
                    "Diversification means owning many different things so one bad outcome "
                            + "can't sink you. A single company can go to zero; a fund "
                            + "holding hundreds essentially can't. It removes the risk of "
                            + "being wrong about one company, but not market risk: when "
                            + "everything falls together, a diversified portfolio falls too."},
            {"dollar cost|dollar-cost|dca",
                    "Dollar-cost averaging means investing a fixed amount on a regular "
                            + "schedule, whatever the price. You automatically buy more "
                            + "shares when prices are low and fewer when they're high, and "
                            + "you avoid the trap of trying to time the market. It doesn't "
                            + "guarantee a profit, but it takes emotion out of the decision."},
            {"short sell|shorting|short a stock|short squeeze",
                    "Short selling is betting a stock will fall: you borrow shares, sell "
                            + "them, and hope to buy them back cheaper later. The danger is "
                            + "that losses are unlimited, because a stock can keep rising. A "
                            + "short squeeze happens when a heavily shorted stock rises and "
                            + "short sellers rush to buy back, pushing it up even faster."},
            {"options|call option|put option|calls and puts|what is an option|option contract",
                    "An option is a contract giving the right to buy (a call) or sell (a put) "
                            + "a stock at a set price before a set date. Buyers pay a premium "
                            + "for that right and can lose all of it if the stock doesn't "
                            + "move enough in time. Options magnify both gains and losses and "
                            + "lose value as the expiry date approaches. They're considerably "
                            + "riskier than owning the stock."},
            {"margin|leverage",
                    "Margin is borrowing from your broker to buy more than you could with "
                            + "your own cash. It magnifies gains and losses alike. If your "
                            + "holdings fall far enough, the broker issues a margin call and "
                            + "can sell your positions without asking. You can lose more "
                            + "than you put in."},
            {"market cap|large cap|small cap",
                    "Market capitalization is the share price times the number of shares: "
                            + "what the whole company is valued at. Large caps (roughly $10 "
                            + "billion and up) tend to be steadier; small caps (under about "
                            + "$2 billion) can grow faster but swing more. Share price alone "
                            + "says nothing about size: a $20 stock can be a bigger company "
                            + "than a $500 one."},
            {"bull market|bear market|bullish|bearish|correction",
                    "A bull market is a sustained rise; a bear market is commonly defined as "
                            + "a fall of 20% or more from a recent high. A drop of 10% to 20% "
                            + "is called a correction. Corrections are frequent and usually "
                            + "recover within months; bear markets are rarer and last longer."},
            {"vix|volatility|fear gauge",
                    "Volatility is how much prices swing. The VIX measures how much "
                            + "movement traders expect in the S&P 500 over the next month, "
                            + "based on option prices. It usually jumps when stocks fall, "
                            + "which is why it's nicknamed the fear gauge. Readings under "
                            + "about 15 are calm; above 30 signals real stress."},
            {"futures|pre-market|premarket|after hours|after-hours|extended hours",
                    "Futures are contracts that track an index or commodity and trade "
                            + "almost around the clock, so S&P and Nasdaq futures show where "
                            + "the market is leaning before it opens. Pre-market and "
                            + "after-hours sessions let stocks trade outside 9:30 to 4:00 "
                            + "Eastern, but with far fewer traders, so prices can jump on "
                            + "small orders. Turn on Extended hours on a stock chart to see "
                            + "them."},
            {"earnings|eps|guidance",
                    "Public companies report earnings every quarter. The headline numbers "
                            + "are revenue and earnings per share (EPS), compared with what "
                            + "analysts expected. Stocks often move more on guidance, the "
                            + "company's forecast for coming quarters, than on the results "
                            + "themselves. A stock can fall after good earnings if "
                            + "expectations were even higher."},
            {"ipo|go public|going public",
                    "An IPO (initial public offering) is the first time a company sells "
                            + "shares to the public. Prices are often very volatile in the "
                            + "first days and weeks, and insiders are usually barred from "
                            + "selling for a few months; when that lock-up ends, extra "
                            + "selling can weigh on the price."},
            {"interest rate|the fed|federal reserve|rate cut|rate hike",
                    "The Federal Reserve sets a short-term interest rate that ripples "
                            + "through the economy. Higher rates make borrowing costlier, "
                            + "which cools spending and inflation, and they tend to weigh on "
                            + "stock prices, especially fast-growing companies. Lower rates "
                            + "do the reverse. Markets often move on what the Fed is expected "
                            + "to do, well before it does it."},
            {"inflation|cpi",
                    "Inflation is prices rising over time, so each dollar buys less. In the "
                            + "US it's most often measured by the Consumer Price Index (CPI), "
                            + "released monthly. Higher-than-expected inflation usually "
                            + "pushes interest rate expectations up, which tends to hurt both "
                            + "stocks and bonds in the short run."},
            {"bonds|what is a bond|treasury|10-year|10 year",
                    "A bond is a loan to a government or company that pays interest and "
                            + "returns your money at the end. Bond prices move opposite to "
                            + "interest rates: when rates rise, existing bonds fall in value. "
                            + "The 10-year Treasury yield is watched closely because "
                            + "mortgage rates and stock valuations key off it."},
            {"recession|gdp",
                    "A recession is a significant, widespread decline in economic activity "
                            + "lasting more than a few months; a common shorthand is two "
                            + "straight quarters of shrinking GDP. Stock markets usually "
                            + "start falling before a recession is official and often start "
                            + "recovering before it ends."},
            {"paper trad",
                    "Paper trading is practicing with pretend money. Unlock it in the skill "
                            + "tree (Market branch), then use it to test ideas with no risk. "
                            + "It's a useful rehearsal, but real trading adds delays, "
                            + "spreads, fees and the stress of real losses, so results don't "
                            + "carry over exactly."},
            {"compound|rule of 72",
                    "Compound growth is earning returns on your earlier returns, which makes "
                            + "growth speed up over time. The Rule of 72 gives a quick "
                            + "estimate: divide 72 by the yearly rate to get the years to "
                            + "double. At 7% that's about 10 years. It works the same way "
                            + "against you with debt."},
            {"ticker|symbol",
                    "A ticker is the short code a stock trades under, like AAPL for Apple. "
                            + "Indexes start with a caret here (^GSPC is the S&P 500), "
                            + "futures end in =F (ES=F), and crypto pairs look like BTC-USD. "
                            + "Use Look up on any chart to search by company name."},
            {"breakout|breakdown",
                    "A breakout is price moving above a resistance level it had been stuck "
                            + "under; a breakdown is falling below support. Traders trust "
                            + "them more on high volume and when price stays beyond the level "
                            + "for a day or more. Many breakouts fail and reverse, which is "
                            + "called a false breakout."},
            {"gap up|gap down|price gap|what is a gap",
                    "A gap is when a stock opens noticeably higher or lower than it closed, "
                            + "leaving empty space on the chart. Gaps usually come from news "
                            + "released while the market was shut, such as earnings. Some "
                            + "gaps get 'filled' as price drifts back, but strong gaps on "
                            + "heavy volume often mark the start of a new move."},
    };

    /** Returns an answer if the question mentions a known topic, otherwise null. */
    static String answer(String question) {
        String q = " " + question.toLowerCase(java.util.Locale.US) + " ";
        for (String[] e : ENTRIES) {
            for (String key : e[0].split("\\|")) {
                String k = key.trim();
                if (k.isEmpty()) continue;
                // Short keys must match as whole words so "put" doesn't fire on "computer".
                boolean hit = k.length() <= 4
                        ? q.matches("(?s).*[^a-z0-9]" + java.util.regex.Pattern.quote(k)
                        + "s?[^a-z0-9].*")
                        : q.contains(k);
                if (hit) return e[1];
            }
        }
        return null;
    }
}
