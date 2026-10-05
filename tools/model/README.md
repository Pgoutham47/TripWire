# Tactic reader: data, training, conversion, evaluation

This pipeline produces the on-device model behind `LlmTacticReader` (PRD 10.4–10.7). Every step uses the app's own code where it matters: the prompt, the JSON schema and the output parser all come from `:core`.

```
seeds.py ──build_dataset.py──▶ data/build/{train,val,test}.jsonl     (split by seed, never by message)
                                     │
             ./gradlew :core:exportTraining  (the app's TacticPrompt + target JSON)
                                     ▼
                         {split}.chat.jsonl ──train.py (LoRA)──▶ runs/<name>/merged
                                                                   │            │
                                    predict.py (HF) ◀──────────────┘            │ convert/convert.py
                                          │                                     ▼
                                          │                    runs/<name>/tactic-*.litertlm
                                          │                                     │ convert/eval_litertlm.py
                                          ▼                                     ▼  (LiteRT-LM, constrained
                                  pred-*.jsonl ──./gradlew :core:evalTactics──▶ report-*.json   decoding, as the app)
```

## Steps

These commands assume the repository root as the working directory, and `JAVA_HOME` pointing at JDK 17.

1. **Build the dataset.**
   ```bash
   python3 tools/model/data/build_dataset.py
   ```

2. **Export with the app's prompt.**
   ```bash
   for s in train val test; do ./gradlew :core:exportTraining -Psplit=$s -q; done
   ```

3. **Measure the keyword-rules baseline.**
   ```bash
   ./gradlew :core:evalTactics
   ```

4. **Train.** From `tools/model`:
   ```bash
   uv run python train.py --base Qwen/Qwen2.5-0.5B-Instruct --out runs/qwen05
   ```
   This takes about 45 minutes per epoch on an M5 Pro and uses the Apple GPU (MPS).

5. **Predict and score.** From `tools/model`:
   ```bash
   uv run python predict.py --model runs/qwen05/merged --split test --out data/build/pred-qwen05.jsonl
   ```
   Then, from the repository root:
   ```bash
   ./gradlew :core:evalTactics -Ppredictions=$PWD/tools/model/data/build/pred-qwen05.jsonl
   ```

6. **Convert.** From `tools/model/convert`:
   ```bash
   uv run python convert.py --model ../runs/qwen05/merged --out ../runs/qwen05/tactic-small.litertlm
   ```

7. **Score the converted file as the phone runs it.** From `tools/model/convert`:
   ```bash
   uv run python eval_litertlm.py --model ../runs/qwen05/tactic-small.litertlm --limit 300 --out ../data/build/pred-qwen05-litertlm.jsonl
   ```

8. **Put it on a phone.**
   ```bash
   adb push tools/model/runs/qwen05/tactic-small.litertlm /sdcard/Android/data/com.tripwire.app/files/models/
   ```
   Then tap Settings → "Look for the model file again".

9. **Human review.**
   ```bash
   python3 tools/model/review_sample.py
   ```
   A reviewer fills in `agree` and `fix` in the CSV. Then run `review_sample.py --score <csv>` to get the agreement rate.

## One command

```bash
(nohup tools/model/run_pipeline.sh qwen05 Qwen/Qwen2.5-0.5B-Instruct > tools/model/runs_qwen05.log 2>&1 &)
```

This trains, checkpointing every 150 steps and resuming automatically when rerun, then runs `evaluate_run.sh`. It is detached, so it survives the session that started it. Follow progress with `tail -f tools/model/runs_qwen05.log`.

## Gemma (the PRD's models)

PRD 10.1 names Gemma 4 E2B for tier-A phones and Gemma 3 270M for tier-B phones. Both are gated on Hugging Face. Before downloading, accept the license on each model page with your own account, then `export HF_TOKEN=...`.

- **Tier B:** `uv run python train.py --base google/gemma-3-270m-it --out runs/gemma270m`, then convert it to `tactic-small.litertlm`.
- **Tier A:** `uv run python train.py --base google/gemma-4-E2B-it --out runs/gemma4e2b --batch 2 --accum 8`, then convert it to `tactic-full.litertlm`. Check the exact model id on Hugging Face first. A model this size may need QLoRA or a CUDA GPU; measure memory first.
- **NPU build for the iQOO 15:** add `--aot-backend qualcomm --aot-soc SM8850` to `convert.py`. Check the exact SoC id against LiteRT's supported list.

The Qwen 2.5 0.5B runs here are a stand-in, used because the model is openly licensed (Apache 2.0). They prove the pipeline end to end; they are not the PRD's chosen models.

## Results so far (Qwen 2.5 0.5B stand-in)

These are test-set results on partly synthetic data (PRD 16.5), split by seed. The test set holds 2,306 messages from unseen seed phrasings. Runs v2 and v3 share the same test messages; v3 relabels the authority tag.

| Tagger | Macro F1 | Hindi F1 | Benign with a high-risk tag | Valid JSON |
| --- | --- | --- | --- | --- |
| Keyword rules (pack v2) | 0.573 | 0.323 | 7.9% | 100% |
| v2 model, as deployed | 0.665 | 0.543 | 15.9% | 100% |
| v3 model alone (`authority_claim`) | 0.611 | 0.594 | 11.6% | 99.8% |
| **v3 as deployed** (plus always-on rules, remote-access rule fixed) | **0.652** | **0.596** | **11.7%** | 100% |
| v3 LiteRT-LM int8, constrained, as deployed (300-message sample) | 0.673 | 0.562 | 14.8%\* | 100% |

\*About 175 benign messages in the sample, so this figure is noisy.

**Product level** (`./gradlew :core:scenarioModelEval`, all 16 scenarios replayed with the model tagging):

| | Rules | v2 | v3 |
| --- | --- | --- | --- |
| Scams detected before the payment request | 5/6 | 6/6 | 6/6 |
| Benign chats with a full-screen warning | 0 | 0 | 0 |
| Benign chats with a quiet notice | 0 | 1 | 0 |

**What changed in v3.**
- **`authority_impersonation` became `authority_claim`.** The tag now marks a claim, not a verdict, and is no longer high-risk. The engine weighs the claim less and the threat more (digital arrest: claim 1.2, legal threat 2.5). The genuine broker RM no longer gets a notice.
- **The always-on remote-access rule now fires only on tool names** (AnyDesk, TeamViewer…). Plain "share your screen" is ordinary in meetings, so it is a fallback used only when the model is unavailable.

**What limits it now.**
- **The tag-level false-alarm rate is still far above 3%.** It comes from a few benign seeds repeated many times. Examples: "शेयर बाज़ार" read as screen *share*; a "never share your OTP" warning read as a credential request; police tenant verification read as a legal threat.
- **These need more unique, real benign phrasings, and a bigger model** (Gemma). The scenario set is small, and some scenario lines resemble seed phrasings, so the product-level result is optimistic.
- **v3's validation loss rose slightly after step 200** (0.097 to 0.116). Stopping around 0.4 epochs is worth trying.

Run v1 (dataset v1, LoRA rank 16) memorised seed phrasings: macro F1 0.694 on its test set, below the keyword rules' 0.802 there. That led to seed batch 2, a larger benign held-out share and LoRA rank 8.

## Known issues

- **Converter needs TensorFlow's schema.** `litert-torch` 0.9.4 imports a flatbuffer schema from TensorFlow, and installing TensorFlow beside it crashes. `convert/shims/` maps that import to LiteRT's own copy.
- **Old conversion route doesn't run.** The older `convert_to_litert` example route produced files that LiteRT-LM 0.17 rejects ("prefill work group size exceeds available state entries"). `convert.py` uses `export_hf` instead.
- **Training memory.** Vocabularies of 150k–262k tokens make full-sequence logits dominate memory. `train.py` left-pads batches and computes logits only for the answer tail.
- **Results are on partly synthetic data.** Report them as test-set results, never as real-world rates (PRD 16.5).
