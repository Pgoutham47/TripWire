#!/usr/bin/env bash
# Evaluates a finished training run end to end (PRD 10.6 steps 2-3, 10.7):
#   1. Hugging Face predictions on the test split, scored by core's TacticEval
#   2. conversion to .litertlm
#   3. the converted file run through LiteRT-LM with constrained decoding, as the app runs it
#
#   tools/model/evaluate_run.sh qwen05 [litertlm-sample-size]
set -euo pipefail
RUN=${1:?run name under tools/model/runs}
SAMPLE=${2:-300}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
MODEL_DIR="$ROOT/tools/model"
DATA="$MODEL_DIR/data/build"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}

echo "== 1/4 predictions (Hugging Face, full test split)"
(cd "$MODEL_DIR" && uv run python predict.py --model "runs/$RUN/merged" --split test --out "$DATA/pred-$RUN.jsonl" | tail -1)

echo "== 2/4 score"
(cd "$ROOT" && ./gradlew :core:evalTactics -Ppredictions="$DATA/pred-$RUN.jsonl" -q)

echo "== 3/4 convert to .litertlm"
(cd "$MODEL_DIR/convert" && uv run python convert.py --model "../runs/$RUN/merged" --out "../runs/$RUN/tactic-small.litertlm" 2>&1 | tail -1)

echo "== 4/4 LiteRT-LM on $SAMPLE test messages, constrained decoding"
(cd "$MODEL_DIR/convert" && uv run python eval_litertlm.py --model "../runs/$RUN/tactic-small.litertlm" --limit "$SAMPLE" \
    --out "$DATA/pred-$RUN-litertlm.jsonl" 2>&1 | tail -1)
(cd "$ROOT" && ./gradlew :core:evalTactics -Ppredictions="$DATA/pred-$RUN-litertlm.jsonl" -q)
