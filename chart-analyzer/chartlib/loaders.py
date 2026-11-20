"""Get price data into one standard shape: DatetimeIndex + open/high/low/close/volume."""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

ALIASES = {
    "date": ("date", "time", "datetime", "timestamp"),
    "open": ("open",),
    "high": ("high",),
    "low": ("low",),
    "close": ("close", "adj close", "adj_close", "last", "price"),
    "volume": ("volume", "vol"),
}


def _standardize(df: pd.DataFrame, source: str) -> pd.DataFrame:
    cols = {str(c).strip().lower(): c for c in df.columns}
    out = pd.DataFrame()
    for want, names in ALIASES.items():
        hit = next((cols[n] for n in names if n in cols), None)
        if hit is not None:
            out[want] = df[hit]
    if "close" not in out:
        raise ValueError(f"{source}: no 'close' column found (columns: {list(df.columns)})")
    if "date" not in out:
        raise ValueError(f"{source}: no 'date' or 'time' column found (columns: {list(df.columns)})")

    d = out.pop("date")
    if pd.api.types.is_numeric_dtype(d):          # TradingView exports unix time
        unit = "ms" if d.iloc[0] > 1e11 else "s"
        idx = pd.to_datetime(d, unit=unit)
    else:
        idx = pd.to_datetime(d, errors="coerce", utc=True).dt.tz_localize(None)
    out.index = pd.DatetimeIndex(idx, name="date")

    for c in list(out.columns):
        if out[c].dtype == object:
            out[c] = out[c].astype(str).str.replace(r"[,$]", "", regex=True)
        out[c] = pd.to_numeric(out[c], errors="coerce")
    for c in ("open", "high", "low"):             # close-only data still works
        if c not in out:
            out[c] = out["close"]
    if "volume" not in out:
        out["volume"] = np.nan

    out = out[out.index.notna()].dropna(subset=["close"]).sort_index()
    out = out[~out.index.duplicated(keep="last")]
    if len(out) < 30:
        raise ValueError(f"{source}: only {len(out)} usable rows; need at least 30")
    return out[["open", "high", "low", "close", "volume"]]


def load_csv(path: str | Path) -> pd.DataFrame:
    path = Path(path)
    sep = "\t" if path.suffix.lower() == ".tsv" else None
    df = pd.read_csv(path, sep=sep, engine="python")
    return _standardize(df, path.name)


def load_ticker(symbol: str, period: str = "2y", interval: str = "1d") -> pd.DataFrame:
    try:
        import yfinance as yf
    except ImportError as e:
        raise RuntimeError("Ticker lookups need yfinance. Run: pip install yfinance") from e
    df = yf.download(symbol, period=period, interval=interval, auto_adjust=True, progress=False)
    if df is None or df.empty:
        raise ValueError(f"No price data came back for '{symbol}'. Check the symbol.")
    if isinstance(df.columns, pd.MultiIndex):     # newer yfinance: (field, ticker)
        df.columns = df.columns.get_level_values(0)
    return _standardize(df.reset_index(), symbol)
