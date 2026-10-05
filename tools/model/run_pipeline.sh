#!/usr/bin/env bash
# Trains (resuming from the last checkpoint if one exists) and then evaluates a run end to end.
# Detached on purpose, so it survives the terminal or agent session that started it:
#   (nohup tools/model/run_pipeline.sh qwen05 Qwen/Qwen2.5-0.5B-Instruct > tools/model/runs_qwen05.log 2>&1 &)
set -euo pipefail
RUN=${1:?run name}
BASE=${2:?base model id or path}
HERE=$(cd "$(dirname "$0")" && pwd)
cd "$HERE"
echo "== train $RUN from $BASE  $(date)"
uv run python train.py --base "$BASE" --out "runs/$RUN" --epochs 1 --batch 8 --accum 2 --rank ${RANK:-16} --resume
echo "== evaluate $RUN  $(date)"
./evaluate_run.sh "$RUN" 300
echo "== done $RUN  $(date)"
