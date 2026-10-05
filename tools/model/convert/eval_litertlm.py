#!/usr/bin/env python3
"""Runs a converted .litertlm tactic reader exactly as the app does, and writes raw answers
for the Kotlin evaluator (PRD 10.6 step 3, 10.7).

    uv run python eval_litertlm.py --model ../runs/qwen05/tactic-small.litertlm --limit 300 \\
        --out ../data/build/pred-qwen05-litertlm.jsonl
    cd ../../.. && ./gradlew :core:evalTactics -Ppredictions=tools/model/data/build/pred-qwen05-litertlm.jsonl

Same system instruction, same user prompt, greedy sampling and the same JSON-schema constrained
decoding as LlmTacticReader. Runs on the CPU backend here; phones use NPU or GPU.
"""
import argparse
import json
import os
import time

import litert_lm

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "data", "build")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--split", default="test")
    ap.add_argument("--limit", type=int, default=None)
    ap.add_argument("--out", required=True)
    ap.add_argument("--no-constraint", action="store_true", help="measure the model without constrained decoding")
    a = ap.parse_args()

    schema = open(os.path.join(DATA, "tactic_schema.json")).read()
    rows = [json.loads(l) for l in open(os.path.join(DATA, f"{a.split}.chat.jsonl"), encoding="utf-8")]
    if a.limit and a.limit < len(rows):
        # A seeded random sample: the split file is ordered scam-first, so its head is not representative.
        import random
        rows = random.Random(13).sample(rows, a.limit)
    litert_lm.set_min_log_severity(litert_lm.LogSeverity.INFO if os.environ.get("LITERT_VERBOSE") else litert_lm.LogSeverity.ERROR)
    os.makedirs(os.path.join(HERE, ".cache"), exist_ok=True)
    engine = litert_lm.Engine(a.model, backend=litert_lm.Backend.CPU(), cache_dir=os.path.join(HERE, ".cache"))
    fmt = None if a.no_constraint else litert_lm.ResponseFormat.json(schema)
    with open(a.out, "w", encoding="utf-8") as out:
        for i, r in enumerate(rows):
            system, user = r["messages"][0]["content"], r["messages"][1]["content"]
            conv = engine.create_conversation(
                system_message=system,
                sampler_config=litert_lm.SamplerConfig(top_k=1, top_p=1.0, temperature=0.0),
                max_output_tokens=200,
                constrained_decoding_config=None if a.no_constraint else litert_lm.ConstrainedDecodingConfig(
                    enable=True, provider=litert_lm.LiteRtLmConstraintProviderType.LL_GUIDANCE),
            )
            t0 = time.time()
            reply = conv.send_message(user, response_format=fmt)
            ms = (time.time() - t0) * 1000
            text = "".join(c.get("text", "") for c in reply.get("content", []) if isinstance(c, dict)) \
                if isinstance(reply.get("content"), list) else str(reply.get("content", ""))
            out.write(json.dumps({"id": r["id"], "raw": text, "ms": ms}, ensure_ascii=False) + "\n")
            if (i + 1) % 25 == 0:
                print(f"{i + 1}/{len(rows)}  last {ms:.0f} ms", flush=True)
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
