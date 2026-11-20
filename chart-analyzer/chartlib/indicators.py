"""Technical indicators and the plain-English signals read from them.

Everything works on a DataFrame with a DatetimeIndex and the columns
open, high, low, close, volume (only close is strictly required).
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd

BULL, BEAR, NEUTRAL = "bullish", "bearish", "neutral"


@dataclass
class Signal:
    name: str
    reading: str
    bias: str  # bullish / bearish / neutral


@dataclass
class Analysis:
    name: str
    df: pd.DataFrame                 # prices plus indicator columns
    signals: list[Signal] = field(default_factory=list)
    supports: list[float] = field(default_factory=list)
    resistances: list[float] = field(default_factory=list)
    trend: str = "sideways"
    summary: str = ""

    @property
    def last(self) -> pd.Series:
        return self.df.iloc[-1]

    def tally(self) -> dict[str, int]:
        out = {BULL: 0, BEAR: 0, NEUTRAL: 0}
        for s in self.signals:
            out[s.bias] += 1
        return out


# ---------------------------------------------------------------- indicators

def sma(s: pd.Series, n: int) -> pd.Series:
    return s.rolling(n).mean()


def ema(s: pd.Series, n: int) -> pd.Series:
    return s.ewm(span=n, adjust=False).mean()


def rsi(close: pd.Series, n: int = 14) -> pd.Series:
    """Wilder's RSI."""
    delta = close.diff()
    gain = delta.clip(lower=0).ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    loss = (-delta.clip(upper=0)).ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    rs = gain / loss.replace(0, np.nan)
    out = 100 - 100 / (1 + rs)
    return out.where(loss != 0, 100.0).where(gain.notna())


def macd(close: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9):
    line = ema(close, fast) - ema(close, slow)
    sig = ema(line, signal)
    return line, sig, line - sig


def bollinger(close: pd.Series, n: int = 20, k: float = 2.0):
    mid = sma(close, n)
    sd = close.rolling(n).std(ddof=0)
    return mid + k * sd, mid, mid - k * sd


def atr(df: pd.DataFrame, n: int = 14) -> pd.Series:
    prev = df["close"].shift()
    tr = pd.concat(
        [df["high"] - df["low"], (df["high"] - prev).abs(), (df["low"] - prev).abs()],
        axis=1,
    ).max(axis=1)
    return tr.ewm(alpha=1 / n, adjust=False, min_periods=n).mean()


def add_indicators(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    c = df["close"]
    for n in (20, 50, 200):
        df[f"sma{n}"] = sma(c, n)
    df["rsi"] = rsi(c)
    df["macd"], df["macd_signal"], df["macd_hist"] = macd(c)
    df["bb_upper"], df["bb_mid"], df["bb_lower"] = bollinger(c)
    df["atr"] = atr(df)
    if df["volume"].notna().any():
        df["vol_avg20"] = sma(df["volume"], 20)
    return df


# ------------------------------------------------------ support / resistance

def find_levels(df: pd.DataFrame, lookback: int = 250, window: int = 5,
                tolerance: float = 0.015) -> list[tuple[float, int]]:
    """Swing highs/lows clustered into price levels.

    Returns (price, touches) pairs, strongest first.
    """
    d = df.tail(lookback)
    span = 2 * window + 1
    highs = d["high"][d["high"] == d["high"].rolling(span, center=True).max()]
    lows = d["low"][d["low"] == d["low"].rolling(span, center=True).min()]
    pivots = sorted(pd.concat([highs, lows]).dropna().tolist())

    clusters: list[list[float]] = []
    for p in pivots:
        if clusters and abs(p - np.mean(clusters[-1])) / np.mean(clusters[-1]) <= tolerance:
            clusters[-1].append(p)
        else:
            clusters.append([p])
    levels = [(float(np.mean(c)), len(c)) for c in clusters]
    return sorted(levels, key=lambda x: -x[1])


# ------------------------------------------------------------------- signals

def _crossed(a: pd.Series, b: pd.Series, bars: int = 10) -> str | None:
    """'up' / 'down' if a crossed b within the last `bars` bars."""
    diff = (a - b).dropna().tail(bars + 1)
    if len(diff) < 2:
        return None
    sign = np.sign(diff)
    changes = sign[sign.diff().fillna(0) != 0]
    if changes.empty:
        return None
    return "up" if changes.iloc[-1] > 0 else "down"


def _fmt(x: float) -> str:
    return f"{x:,.2f}" if abs(x) >= 1 else f"{x:.4f}"


def analyze(df: pd.DataFrame, name: str = "chart") -> Analysis:
    df = add_indicators(df)
    a = Analysis(name=name, df=df)
    last, price = df.iloc[-1], float(df["close"].iloc[-1])
    sig = a.signals
    have = lambda col: col in df and pd.notna(last[col])  # noqa: E731

    # --- trend from moving averages
    above = [n for n in (20, 50, 200) if have(f"sma{n}") and price > last[f"sma{n}"]]
    known = [n for n in (20, 50, 200) if have(f"sma{n}")]
    if known:
        if len(above) == len(known):
            a.trend, bias = "uptrend", BULL
        elif not above:
            a.trend, bias = "downtrend", BEAR
        else:
            a.trend, bias = "mixed / sideways", NEUTRAL
        desc = ", ".join(
            f"{'above' if n in above else 'below'} {n}-day ({_fmt(last[f'sma{n}'])})"
            for n in known
        )
        sig.append(Signal("Trend (moving averages)", f"Price {_fmt(price)} is {desc}", bias))

    if have("sma50") and have("sma200"):
        cross = _crossed(df["sma50"], df["sma200"], bars=20)
        if cross == "up":
            sig.append(Signal("Golden cross", "50-day crossed above the 200-day in the last 20 bars", BULL))
        elif cross == "down":
            sig.append(Signal("Death cross", "50-day crossed below the 200-day in the last 20 bars", BEAR))
        else:
            up = last["sma50"] > last["sma200"]
            sig.append(Signal("50 vs 200-day", f"50-day is {'above' if up else 'below'} the 200-day",
                              BULL if up else BEAR))

    # --- momentum
    if have("rsi"):
        r = float(last["rsi"])
        if r >= 70:
            sig.append(Signal("RSI (14)", f"{r:.1f}: overbought (above 70)", BEAR))
        elif r <= 30:
            sig.append(Signal("RSI (14)", f"{r:.1f}: oversold (below 30)", BULL))
        else:
            lean = BULL if r > 55 else BEAR if r < 45 else NEUTRAL
            sig.append(Signal("RSI (14)", f"{r:.1f}: in the normal 30-70 range", lean))

    if have("macd") and have("macd_signal"):
        up = last["macd"] > last["macd_signal"]
        cross = _crossed(df["macd"], df["macd_signal"], bars=5)
        text = f"MACD {last['macd']:.3f} is {'above' if up else 'below'} its signal line {last['macd_signal']:.3f}"
        if cross:
            text += f"; crossed {cross} within the last 5 bars"
        hist = df["macd_hist"].dropna().tail(3)
        if len(hist) == 3 and hist.is_monotonic_increasing and not hist.is_monotonic_decreasing:
            text += "; histogram rising"
        elif len(hist) == 3 and hist.is_monotonic_decreasing and not hist.is_monotonic_increasing:
            text += "; histogram falling"
        sig.append(Signal("MACD (12, 26, 9)", text, BULL if up else BEAR))

    # --- volatility
    if have("bb_upper") and have("bb_lower"):
        width = last["bb_upper"] - last["bb_lower"]
        pos = (price - last["bb_lower"]) / width if width else 0.5
        if price > last["bb_upper"]:
            sig.append(Signal("Bollinger Bands", "Price closed above the upper band (stretched)", BEAR))
        elif price < last["bb_lower"]:
            sig.append(Signal("Bollinger Bands", "Price closed below the lower band (stretched)", BULL))
        else:
            sig.append(Signal("Bollinger Bands", f"Price sits {pos:.0%} of the way from lower to upper band", NEUTRAL))
        widths = ((df["bb_upper"] - df["bb_lower"]) / df["bb_mid"]).dropna().tail(120)
        if len(widths) >= 60 and widths.iloc[-1] <= widths.quantile(0.1):
            sig.append(Signal("Bollinger squeeze", "Bands are the tightest they have been in ~6 months; "
                              "a bigger move often follows, direction unknown", NEUTRAL))

    if have("atr"):
        sig.append(Signal("ATR (14)", f"{_fmt(last['atr'])} per bar, about {last['atr'] / price:.1%} of price", NEUTRAL))

    # --- volume
    if have("vol_avg20") and last["vol_avg20"] > 0:
        ratio = last["volume"] / last["vol_avg20"]
        day_up = len(df) > 1 and price >= df["close"].iloc[-2]
        if ratio >= 1.5:
            sig.append(Signal("Volume", f"{ratio:.1f}x the 20-bar average on an {'up' if day_up else 'down'} bar",
                              BULL if day_up else BEAR))
        else:
            sig.append(Signal("Volume", f"{ratio:.1f}x the 20-bar average", NEUTRAL))

    # --- range
    year = df.tail(252)
    hi, lo = float(year["high"].max()), float(year["low"].min())
    if hi > lo:
        sig.append(Signal("Range position", f"{(price - lo) / (hi - lo):.0%} of the way between the "
                          f"{len(year)}-bar low {_fmt(lo)} and high {_fmt(hi)}", NEUTRAL))

    # --- support / resistance
    levels = find_levels(df)
    a.supports = sorted([p for p, _ in levels if p < price * 0.995], reverse=True)[:3]
    a.resistances = sorted([p for p, _ in levels if p > price * 1.005])[:3]
    if a.supports:
        s = a.supports[0]
        sig.append(Signal("Nearest support", f"{_fmt(s)} ({(price - s) / price:.1%} below price)", NEUTRAL))
    if a.resistances:
        r = a.resistances[0]
        sig.append(Signal("Nearest resistance", f"{_fmt(r)} ({(r - price) / price:.1%} above price)", NEUTRAL))

    t = a.tally()
    lean = "leans bullish" if t[BULL] > t[BEAR] + 1 else "leans bearish" if t[BEAR] > t[BULL] + 1 else "is mixed"
    a.summary = (f"{name}: last close {_fmt(price)}, {a.trend}. "
                 f"{t[BULL]} bullish, {t[BEAR]} bearish and {t[NEUTRAL]} neutral readings; "
                 f"the overall picture {lean}.")
    return a
