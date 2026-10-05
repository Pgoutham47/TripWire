#!/usr/bin/env python3
"""Runs a model over a split and writes raw answers for the Kotlin evaluator (PRD 10.7).

    uv run python predict.py --model runs/qwen05/merged --split test --out data/build/pred-qwen05.jsonl
    cd ../.. && ./gradlew :core:evalTactics -Ppredictions=tools/model/data/build/pred-qwen05.jsonl

Greedy decoding, the app's exact prompt (from <split>.chat.jsonl). Scoring, including JSON
validity, is done by core's TacticEval with the same parser the app uses.
"""
import argparse
import json
import os
import time

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--split", default="test")
    ap.add_argument("--out", required=True)
    ap.add_argument("--limit", type=int, default=None)
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--max-new", type=int, default=120)
    a = ap.parse_args()

    dev = "cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu"
    tok = AutoTokenizer.from_pretrained(a.model)
    tok.padding_side = "left"
    if tok.pad_token is None:
        tok.pad_token = tok.eos_token
    model = AutoModelForCausalLM.from_pretrained(a.model, dtype=torch.bfloat16 if dev != "cpu" else torch.float32).to(dev).eval()

    rows = [json.loads(l) for l in open(os.path.join(HERE, "data", "build", f"{a.split}.chat.jsonl"), encoding="utf-8")]
    if a.limit:
        rows = rows[:a.limit]
    with open(a.out, "w", encoding="utf-8") as out:
        for i in range(0, len(rows), a.batch):
            chunk = rows[i:i + a.batch]
            prompts = [tok.apply_chat_template(r["messages"][:-1], tokenize=False, add_generation_prompt=True) for r in chunk]
            enc = tok(prompts, return_tensors="pt", padding=True, add_special_tokens=False).to(dev)
            t0 = time.time()
            with torch.no_grad():
                gen = model.generate(**enc, max_new_tokens=a.max_new, do_sample=False, pad_token_id=tok.pad_token_id)
            ms = (time.time() - t0) * 1000 / len(chunk)
            for r, g in zip(chunk, gen):
                raw = tok.decode(g[enc["input_ids"].shape[1]:], skip_special_tokens=True)
                out.write(json.dumps({"id": r["id"], "raw": raw, "ms": ms}, ensure_ascii=False) + "\n")
            print(f"{min(i + a.batch, len(rows))}/{len(rows)}", flush=True)
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
