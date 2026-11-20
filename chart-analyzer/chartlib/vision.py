"""Read a chart screenshot with Claude's vision and describe the technicals.

Needs an Anthropic API key in the ANTHROPIC_API_KEY environment variable.
A screenshot has no raw numbers behind it, so this is a visual read: less exact
than the calculated analysis you get from price data.
"""
from __future__ import annotations

import base64
import os
from pathlib import Path

MEDIA = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
         ".webp": "image/webp", ".gif": "image/gif"}
DEFAULT_MODEL = "claude-sonnet-5-5"

PROMPT = """You are reading a screenshot of a financial price chart. Describe its technicals.

Use only what is visible in the image. If something cannot be read (ticker, timeframe,
an indicator value), say so instead of guessing. Give price levels as approximate.

Answer in plain text with these headings, each on its own line followed by short bullet lines:
WHAT THIS CHART SHOWS  (ticker, timeframe, chart type, indicators on screen)
TREND  (direction and strength, higher highs/lows or lower highs/lows)
SUPPORT AND RESISTANCE  (approximate levels and why)
PATTERNS  (chart or candlestick patterns, only if reasonably clear)
INDICATORS  (what any visible moving averages, RSI, MACD, volume etc. are showing)
WHAT TO WATCH  (levels whose break would change the picture, bullish and bearish cases)

This is a description of the chart, not advice to buy or sell. Do not recommend trades."""


def analyze_image(path: str | Path, model: str | None = None) -> str:
    path = Path(path)
    media = MEDIA.get(path.suffix.lower())
    if not media:
        raise ValueError(f"{path.name}: unsupported image type")
    if not os.environ.get("ANTHROPIC_API_KEY"):
        raise RuntimeError(
            "Screenshots need an Anthropic API key. Get one at https://console.anthropic.com, "
            "then set it as ANTHROPIC_API_KEY (see README)."
        )
    try:
        import anthropic
    except ImportError as e:
        raise RuntimeError("Screenshots need the anthropic package. Run: pip install anthropic") from e

    data = base64.standard_b64encode(path.read_bytes()).decode()
    client = anthropic.Anthropic()
    msg = client.messages.create(
        model=model or os.environ.get("CHART_MODEL", DEFAULT_MODEL),
        max_tokens=1500,
        messages=[{
            "role": "user",
            "content": [
                {"type": "image", "source": {"type": "base64", "media_type": media, "data": data}},
                {"type": "text", "text": PROMPT},
            ],
        }],
    )
    return "".join(b.text for b in msg.content if getattr(b, "type", "") == "text").strip()
