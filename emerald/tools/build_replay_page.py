#!/usr/bin/env python3
"""Embeds simulation-core/build/replay.json into tools/replay-viewer.html -> build/emerald-replay.html."""
import sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
data = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / "simulation-core/build/replay.json"
out = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else root / "build/emerald-replay.html"
page = (root / "tools/replay-viewer.html").read_text()
assert "/*REPLAY_DATA*/null" in page
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(page.replace("/*REPLAY_DATA*/null", data.read_text().replace("</", "<\\/")))
print("wrote", out, out.stat().st_size // 1024, "KiB")
