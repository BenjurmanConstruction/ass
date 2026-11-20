# Chart Analyzer

Put charts in, get the technicals out. It takes three kinds of input:

| You give it | What happens | Needs |
|---|---|---|
| A ticker symbol (`AAPL`) | Downloads prices, calculates indicators, draws an annotated chart | internet |
| A price file (`.csv`) | Same, from your own data (TradingView or Yahoo exports work) | nothing extra |
| A chart screenshot (`.png`, `.jpg`) | An AI model reads the picture and describes the technicals | an Anthropic API key |

Each chart gets an HTML report in the `reports/` folder. Open it in any browser.

## Set up (once)

1. Install [Python](https://www.python.org/downloads/) 3.10 or newer.
2. Download this folder (green **Code** button on GitHub, then **Download ZIP**) and open a
   terminal inside `chart-analyzer`.
3. Install the libraries:

   ```
   pip install -r requirements.txt
   ```

## Use it

Drop files into the `charts/` folder and run:

```
python analyze.py
```

Or name things directly:

```
python analyze.py AAPL TSLA                  ticker symbols
python analyze.py examples/sample_prices.csv a price file
python analyze.py my_screenshot.png          a chart picture
python analyze.py BTC-USD --interval 1wk     weekly bars
python analyze.py AAPL --open                open the report when done
```

Try `python analyze.py examples/sample_prices.csv` first; it works with no internet and no key.

## What it checks (price data)

- **Trend**: price against the 20, 50 and 200-day moving averages; golden and death crosses
- **Momentum**: RSI (14) and MACD (12, 26, 9), including recent crossovers
- **Volatility**: Bollinger Bands, band squeezes, ATR
- **Volume**: latest bar against its 20-bar average
- **Levels**: nearest support and resistance, found from repeated swing highs and lows
- **Range**: where price sits between its one-year high and low

Each reading is tagged bullish, bearish or neutral, with a one-line summary on top.

## Screenshots

Screenshots are read by Claude through the Anthropic API, which is a paid service billed to
your own key (a fraction of a cent to a few cents per chart).

1. Create a key at <https://console.anthropic.com>.
2. Set it in your terminal before running:
   - Windows: `setx ANTHROPIC_API_KEY "your-key"` then open a new terminal
   - Mac/Linux: `export ANTHROPIC_API_KEY="your-key"`

Never paste the key into a file in this repo; the repo is public.

A screenshot has no numbers behind it, so the read is visual and levels are approximate. When
you can, use a ticker or CSV instead: those numbers are calculated exactly.

To use a different model, set `CHART_MODEL` (default `claude-sonnet-5-5`).

## CSV format

A header row with a `date` (or `time`) column and a `close` column. `open`, `high`, `low` and
`volume` are used when present. At least 30 rows; 200+ gives the full set of indicators.

## Notes

Technical readings describe past price behaviour. They are information only, not financial
advice or a recommendation to buy or sell.

Tests: `python -m unittest discover tests`
