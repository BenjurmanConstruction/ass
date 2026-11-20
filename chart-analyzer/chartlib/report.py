"""Draw the annotated chart and write a self-contained HTML report."""
from __future__ import annotations

import base64
import html
import io
from datetime import datetime
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import matplotlib.ticker  # noqa: E402,F401

from .indicators import BEAR, BULL, Analysis  # noqa: E402

DISCLAIMER = ("Technical readings describe past price behaviour. They are information only, "
              "not financial advice or a recommendation to buy or sell.")
COLORS = {BULL: "#1a7f4b", BEAR: "#c0392b", "neutral": "#6b7280"}

CSS = """
body{font:15px/1.5 system-ui,Segoe UI,Arial,sans-serif;max-width:980px;margin:32px auto;padding:0 16px;color:#1f2937}
h1{font-size:22px;margin-bottom:4px} .sub{color:#6b7280;margin-top:0}
.summary{background:#f3f4f6;border-radius:8px;padding:12px 16px;font-weight:600}
img{max-width:100%;border:1px solid #e5e7eb;border-radius:8px}
table{border-collapse:collapse;width:100%;margin:16px 0}
td,th{text-align:left;padding:8px 10px;border-bottom:1px solid #e5e7eb;vertical-align:top}
.tag{display:inline-block;padding:1px 8px;border-radius:10px;color:#fff;font-size:12px}
pre{white-space:pre-wrap;font:inherit;background:#f9fafb;border-radius:8px;padding:14px 16px}
.note{color:#6b7280;font-size:13px}
"""


def _png_b64(fig) -> str:
    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=110, bbox_inches="tight")
    plt.close(fig)
    return base64.b64encode(buf.getvalue()).decode()


def draw_chart(a: Analysis, bars: int = 180) -> str:
    d = a.df.tail(bars)
    has_vol = d["volume"].notna().any()
    ratios = [3, 1, 1, 1] if has_vol else [3, 1, 1]
    fig, axes = plt.subplots(len(ratios), 1, figsize=(11, 8.5 if has_vol else 7.5), sharex=True,
                             gridspec_kw={"height_ratios": ratios, "hspace": 0.06})
    ax = axes[0]
    ax.plot(d.index, d["close"], color="#111827", lw=1.4, label="Close")
    for n, col in ((20, "#2563eb"), (50, "#d97706"), (200, "#7c3aed")):
        if d[f"sma{n}"].notna().any():
            ax.plot(d.index, d[f"sma{n}"], color=col, lw=1, label=f"{n}-day avg")
    if d["bb_upper"].notna().any():
        ax.fill_between(d.index, d["bb_lower"], d["bb_upper"], color="#2563eb", alpha=0.07,
                        label="Bollinger Bands")
    for word, levels, col in (("support", a.supports[:2], COLORS[BULL]),
                              ("resistance", a.resistances[:2], COLORS[BEAR])):
        for k, lvl in enumerate(levels):   # alternate sides so close levels don't overlap
            ax.axhline(lvl, color=col, ls="--", lw=0.9)
            ax.annotate(f"{word} {lvl:,.2f}", (d.index[0] if k == 0 else d.index[-1], lvl),
                        fontsize=8, color=col, va="bottom", ha="left" if k == 0 else "right")
    ax.set_title(a.name, loc="left", fontweight="bold")
    ax.legend(loc="upper left", fontsize=8, ncol=5, frameon=False)
    ax.set_ylabel("Price")

    i = 1
    if has_vol:
        up = d["close"] >= d["close"].shift().fillna(d["close"])
        width = (d.index[-1] - d.index[0]) / max(len(d), 1) * 0.8
        axes[i].bar(d.index, d["volume"], width=width,
                    color=[COLORS[BULL] if u else COLORS[BEAR] for u in up])
        axes[i].set_ylabel("Volume")
        axes[i].ticklabel_format(axis="y", style="plain")
        axes[i].yaxis.set_major_formatter(matplotlib.ticker.EngFormatter(sep=""))
        i += 1

    axes[i].plot(d.index, d["rsi"], color="#7c3aed", lw=1.1)
    for y, c in ((70, COLORS[BEAR]), (30, COLORS[BULL])):
        axes[i].axhline(y, color=c, ls=":", lw=0.9)
    axes[i].set_ylim(0, 100)
    axes[i].set_ylabel("RSI")
    i += 1

    axes[i].plot(d.index, d["macd"], color="#2563eb", lw=1.1, label="MACD")
    axes[i].plot(d.index, d["macd_signal"], color="#d97706", lw=1.1, label="Signal")
    hist = d["macd_hist"]
    width = (d.index[-1] - d.index[0]) / max(len(d), 1) * 0.8
    axes[i].bar(d.index, hist, width=width,
                color=[COLORS[BULL] if h >= 0 else COLORS[BEAR] for h in hist.fillna(0)], alpha=0.5)
    axes[i].axhline(0, color="#9ca3af", lw=0.7)
    axes[i].set_ylabel("MACD")
    axes[i].legend(loc="upper left", fontsize=8, ncol=2, frameon=False)

    for x in axes:
        x.grid(alpha=0.25)
        x.spines[["top", "right"]].set_visible(False)
    fig.autofmt_xdate()
    return _png_b64(fig)


def _page(title: str, body: str) -> str:
    stamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    return (f"<!doctype html><html><head><meta charset='utf-8'><title>{html.escape(title)}</title>"
            f"<style>{CSS}</style></head><body>{body}"
            f"<p class='note'>Generated {stamp}. {DISCLAIMER}</p></body></html>")


def write_data_report(a: Analysis, out_dir: Path) -> Path:
    d = a.df
    rows = "".join(
        f"<tr><td><b>{html.escape(s.name)}</b></td><td>{html.escape(s.reading)}</td>"
        f"<td><span class='tag' style='background:{COLORS[s.bias]}'>{s.bias}</span></td></tr>"
        for s in a.signals
    )
    body = (
        f"<h1>{html.escape(a.name)}: technical analysis</h1>"
        f"<p class='sub'>{len(d)} bars, {d.index[0]:%Y-%m-%d} to {d.index[-1]:%Y-%m-%d}</p>"
        f"<p class='summary'>{html.escape(a.summary)}</p>"
        f"<img src='data:image/png;base64,{draw_chart(a)}' alt='chart'>"
        f"<table><tr><th>Indicator</th><th>Reading</th><th>Lean</th></tr>{rows}</table>"
    )
    out = out_dir / f"{_safe(a.name)}.html"
    out.write_text(_page(a.name, body), encoding="utf-8")
    return out


def write_image_report(image: Path, text: str, out_dir: Path) -> Path:
    mime = "image/png" if image.suffix.lower() == ".png" else "image/jpeg"
    b64 = base64.b64encode(image.read_bytes()).decode()
    body = (
        f"<h1>{html.escape(image.name)}: chart read</h1>"
        "<p class='sub'>Visual read of a screenshot by an AI model. Levels are approximate; "
        "for exact numbers, analyze the price data instead.</p>"
        f"<img src='data:{mime};base64,{b64}' alt='chart screenshot'>"
        f"<pre>{html.escape(text)}</pre>"
    )
    out = out_dir / f"{_safe(image.stem)}.html"
    out.write_text(_page(image.name, body), encoding="utf-8")
    return out


def _safe(name: str) -> str:
    return "".join(ch if ch.isalnum() or ch in "-_." else "_" for ch in name)
