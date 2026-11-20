#!/usr/bin/env python3
"""Chart Analyzer: put charts in, get the technicals out.

  python analyze.py                 analyze everything in the charts/ folder
  python analyze.py AAPL TSLA       look up ticker symbols
  python analyze.py my_data.csv     analyze a price file
  python analyze.py screenshot.png  read a chart screenshot (needs an API key)

Reports are written to the reports/ folder as HTML files you open in a browser.
"""
from __future__ import annotations

import argparse
import sys
import webbrowser
from pathlib import Path

from chartlib import loaders, report
from chartlib.indicators import analyze
from chartlib.vision import MEDIA, analyze_image

HERE = Path(__file__).resolve().parent
DATA_EXT = {".csv", ".tsv", ".txt"}


def handle(item: str, out_dir: Path, period: str, interval: str) -> Path:
    path = Path(item)
    if path.is_file():
        ext = path.suffix.lower()
        if ext in MEDIA:
            print(f"  reading screenshot {path.name} ...")
            text = analyze_image(path)
            print("\n" + text + "\n")
            return report.write_image_report(path, text, out_dir)
        if ext in DATA_EXT:
            result = analyze(loaders.load_csv(path), name=path.stem)
        else:
            raise ValueError(f"{path.name}: not a price file (.csv) or an image (.png, .jpg)")
    elif path.suffix or "/" in item or "\\" in item:
        raise FileNotFoundError(f"File not found: {item}")
    else:
        print(f"  downloading prices for {item.upper()} ...")
        result = analyze(loaders.load_ticker(item, period, interval), name=item.upper())

    print("\n" + result.summary)
    for s in result.signals:
        print(f"  [{s.bias:^7}] {s.name}: {s.reading}")
    print()
    return report.write_data_report(result, out_dir)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("items", nargs="*", help="ticker symbols, CSV files or chart screenshots")
    p.add_argument("--period", default="2y", help="history to download for tickers (default 2y)")
    p.add_argument("--interval", default="1d", help="bar size for tickers: 1d, 1wk, 1h ... (default 1d)")
    p.add_argument("--out", default=str(HERE / "reports"), help="folder for reports")
    p.add_argument("--open", action="store_true", help="open each report in your browser")
    args = p.parse_args()

    items = args.items
    if not items:
        inbox = HERE / "charts"
        items = sorted(str(f) for f in inbox.iterdir()
                       if f.suffix.lower() in DATA_EXT | set(MEDIA))
        if not items:
            print(f"Nothing to analyze. Put CSV files or chart screenshots in {inbox}\n"
                  "or name a ticker:  python analyze.py AAPL")
            return 1

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    failed = 0
    for item in items:
        print(f"== {Path(item).name} ==")
        try:
            out = handle(item, out_dir, args.period, args.interval)
            print(f"  report: {out}")
            if args.open:
                webbrowser.open(out.resolve().as_uri())
        except Exception as e:  # keep going with the other charts
            failed += 1
            print(f"  could not analyze: {e}")
    print(f"\n{report.DISCLAIMER}")
    return 1 if failed == len(items) else 0


if __name__ == "__main__":
    sys.exit(main())
