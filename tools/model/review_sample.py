#!/usr/bin/env python3
"""Draws a stratified sample of the dataset for human review (PRD 10.5: "A person reviews a
sample of every generated batch"). Writes a CSV with an empty `agree` column and a `fix` column.

    python3 review_sample.py --per-cell 4 --out review/batch-001.csv
    python3 review_sample.py --score review/batch-001.csv      # after review: agreement per tag

Cells are (kind, language, tag), so every tactic in every language gets looked at, plus benign
messages and the hard negatives that carry no tag. See docs/labeling-guide.md.
"""
import argparse
import csv
import json
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data", "build")


def sample(per_cell, seed):
    rows = [json.loads(l) for split in ("train", "val", "test") for l in open(os.path.join(DATA, f"{split}.jsonl"), encoding="utf-8")]
    rng = random.Random(seed)
    cells = {}
    for r in rows:
        keys = [(r["kind"], r["lang"], t) for t in r["tags"]] or [(r["kind"], r["lang"], "(none)")]
        for k in keys:
            cells.setdefault(k, []).append(r)
    picked, seen = [], set()
    for k in sorted(cells):
        for r in rng.sample(cells[k], min(per_cell, len(cells[k]))):
            if r["id"] not in seen:
                seen.add(r["id"])
                picked.append(r)
    return picked


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-cell", type=int, default=4)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--out", default=os.path.join(HERE, "review", "batch-001.csv"))
    ap.add_argument("--score", help="a reviewed CSV to summarise")
    a = ap.parse_args()

    if a.score:
        rows = list(csv.DictReader(open(a.score, encoding="utf-8")))
        done = [r for r in rows if r["agree"].strip().lower() in ("y", "n", "yes", "no")]
        ok = [r for r in done if r["agree"].strip().lower().startswith("y")]
        print(f"reviewed {len(done)}/{len(rows)}, agreement {len(ok) / max(1, len(done)):.1%}")
        for r in done:
            if not r["agree"].strip().lower().startswith("y"):
                print(f"  {r['id']} [{r['tags']}] {r['text'][:90]} -> {r['fix']}")
        return

    picked = sample(a.per_cell, a.seed)
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    with open(a.out, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["id", "split", "kind", "lang", "context", "text", "tags", "agree", "fix"])
        for r in picked:
            w.writerow([r["id"], r["split"], r["kind"], r["lang"], " | ".join(r["context"]), r["text"], " ".join(r["tags"]), "", ""])
    print(f"wrote {len(picked)} rows to {a.out}; fill `agree` with y/n and `fix` with the right tags")


if __name__ == "__main__":
    main()
