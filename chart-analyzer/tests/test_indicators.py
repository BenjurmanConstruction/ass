"""Run with:  python -m unittest discover tests   (from the chart-analyzer folder)"""
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from chartlib import indicators as ind, loaders, report  # noqa: E402


def make(close):
    close = pd.Series(close, dtype=float,
                      index=pd.date_range("2024-01-01", periods=len(close), freq="B"))
    return pd.DataFrame({"open": close, "high": close * 1.01, "low": close * 0.99,
                         "close": close, "volume": 1000.0})


class Indicators(unittest.TestCase):
    def test_rsi_warmup_and_bounds(self):
        c = [44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08,
             45.89, 46.03, 45.61, 46.28, 46.28, 46.00, 46.03, 46.41, 46.22, 45.64]
        r = ind.rsi(pd.Series(c))
        self.assertTrue(r.iloc[:13].isna().all())
        self.assertTrue(((r.dropna() >= 0) & (r.dropna() <= 100)).all())

    def test_rsi_extremes(self):
        self.assertAlmostEqual(ind.rsi(pd.Series(np.arange(1, 60.0))).iloc[-1], 100)
        self.assertAlmostEqual(ind.rsi(pd.Series(np.arange(60, 1, -1.0))).iloc[-1], 0)

    def test_sma_and_macd(self):
        s = pd.Series(np.arange(1, 101.0))
        self.assertAlmostEqual(ind.sma(s, 20).iloc[-1], 90.5)
        line, sig, hist = ind.macd(s)
        self.assertGreater(line.iloc[-1], 0)
        self.assertAlmostEqual(hist.iloc[-1], line.iloc[-1] - sig.iloc[-1])

    def test_uptrend_and_downtrend(self):
        up = ind.analyze(make(np.linspace(50, 150, 300)), "up")
        down = ind.analyze(make(np.linspace(150, 50, 300)), "down")
        self.assertEqual(up.trend, "uptrend")
        self.assertEqual(down.trend, "downtrend")
        self.assertGreater(up.tally()["bullish"], 0)

    def test_levels_bracket_price(self):
        x = np.arange(300)
        a = ind.analyze(make(100 + 10 * np.sin(x / 12)), "wave")
        price = a.df["close"].iloc[-1]
        self.assertTrue(all(s < price for s in a.supports))
        self.assertTrue(all(r > price for r in a.resistances))

    def test_csv_formats_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            df = make(100 + np.cumsum(np.random.default_rng(1).normal(0, 1, 260)))
            yahoo = df.rename(columns=str.title).rename_axis("Date").reset_index()
            yahoo.to_csv(tmp / "yahoo.csv", index=False)
            tv = df.drop(columns="volume").reset_index()
            tv["time"] = tv.pop("index").astype("datetime64[s]").astype("int64")
            tv.to_csv(tmp / "tv.csv", index=False)
            for f in ("yahoo.csv", "tv.csv"):
                got = loaders.load_csv(tmp / f)
                self.assertEqual(len(got), 260)
                self.assertEqual(got.index[0], df.index[0])
                out = report.write_data_report(ind.analyze(got, f), tmp)
                self.assertIn("<img", out.read_text())


if __name__ == "__main__":
    unittest.main()
