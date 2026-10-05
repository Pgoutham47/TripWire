#!/usr/bin/env python3
"""Converts a merged fine-tuned model to a LiteRT-LM file the app can load (PRD 10.6 steps 2-3).

    uv run python convert.py --model ../runs/qwen05/merged --out ../runs/qwen05/tactic-small.litertlm
    uv run python convert.py --model ../runs/gemma270m/merged --out ../runs/gemma270m/tactic-small.litertlm
    # NPU ahead-of-time build for the reference phone (Snapdragon 8 Elite Gen 5):
    uv run python convert.py --model ... --out ... --aot-backend qualcomm --aot-soc SM8850

Uses litert-torch's Hugging Face exporter, which bundles the model's own Jinja chat template, so
LiteRT-LM formats prompts exactly as in training. Quantisation defaults to dynamic int8 weights;
int4 recipes are smaller and faster and must be checked for quality with eval_litertlm.py.
"""
import argparse
import os
import shutil
import sys
import tempfile

# litert-torch imports one flatbuffer schema from TensorFlow without declaring the dependency.
# Installing TensorFlow next to LiteRT's converter crashes (both bundle LLVM), so a shim maps
# that import to LiteRT's own copy of the schema. See shims/README.md.
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "shims"))

from litert_torch.generative.export_hf import export as export_hf  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="merged HF model directory from train.py")
    ap.add_argument("--out", required=True, help="output .litertlm path")
    ap.add_argument("--quantize", default="dynamic_wi8_afp32",
                    help="ai-edge-quantizer recipe: dynamic_wi8_afp32, dynamic_wi8_emb4_afp32, dynamic_wi4b32_afp32, none")
    # The app sends a ~450-token prompt (system instruction plus up to six earlier messages)
    # and expects a short JSON answer, so prefill stops at 1024 and the cache at 1280.
    ap.add_argument("--cache", type=int, default=1280)
    ap.add_argument("--prefill", type=int, nargs="+", default=[128, 512, 1024])
    ap.add_argument("--aot-backend", default=None, help="NPU backend for ahead-of-time compilation, e.g. qualcomm")
    ap.add_argument("--aot-soc", default=None, help="SoC model for AOT compilation, e.g. SM8850")
    a = ap.parse_args()

    with tempfile.TemporaryDirectory() as work:
        export_hf.export(
            model=os.path.abspath(a.model),
            output_dir=work,
            prefill_lengths=a.prefill,
            cache_length=a.cache,
            quantization_recipe=None if a.quantize == "none" else a.quantize,
            bundle_litert_lm=True,
            use_jinja_template=True,
            aot_backend=a.aot_backend,
            aot_soc_model=a.aot_soc,
        )
        produced = [os.path.join(root, f) for root, _, files in os.walk(work) for f in files if f.endswith(".litertlm")]
        if not produced:
            raise SystemExit(f"no .litertlm produced; files: {[f for _, _, fs in os.walk(work) for f in fs]}")
        os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
        shutil.move(max(produced, key=os.path.getsize), a.out)
    print(f"wrote {a.out} ({os.path.getsize(a.out) / 1e6:.0f} MB, {a.quantize})")


if __name__ == "__main__":
    main()
