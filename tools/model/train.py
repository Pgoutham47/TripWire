#!/usr/bin/env python3
"""LoRA fine-tuning of the tactic reader (PRD 10.6 step 1).

    uv run python train.py --base Qwen/Qwen2.5-0.5B-Instruct --out runs/qwen05
    uv run python train.py --base google/gemma-3-270m-it --out runs/gemma270m     # needs Gemma access
    uv run python train.py --base google/gemma-4-e2b-it --out runs/gemma4e2b     # tier A model

Input is data/build/train.chat.jsonl and val.chat.jsonl, written by `./gradlew :core:exportTraining`
with the app's own prompt. The loss covers only the assistant's JSON answer. The adapter is
merged into the base and saved as a full model for conversion (convert.py).
"""
import argparse
import json
import math
import os
import random
import time

import torch
from peft import LoraConfig, get_peft_model
from transformers import AutoModelForCausalLM, AutoTokenizer

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data", "build")


def device():
    if torch.cuda.is_available():
        return "cuda"
    if torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def load_chat(path, limit=None, seed=0):
    rows = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    if limit and len(rows) > limit:
        random.Random(seed).shuffle(rows)
        rows = rows[:limit]
    return rows


def encode(tok, row, max_len):
    """Prompt tokens are masked with -100; only the answer and end-of-turn are learned."""
    msgs = row["messages"]
    prompt = tok.apply_chat_template(msgs[:-1], tokenize=False, add_generation_prompt=True)
    answer = msgs[-1]["content"] + (tok.eos_token or "")
    p_ids = tok(prompt, add_special_tokens=False)["input_ids"]
    a_ids = tok(answer, add_special_tokens=False)["input_ids"]
    ids = (p_ids + a_ids)[:max_len]
    labels = ([-100] * len(p_ids) + a_ids)[:max_len]
    return ids, labels


def batches(examples, size, pad_id, shuffle, seed):
    order = list(range(len(examples)))
    if shuffle:
        random.Random(seed).shuffle(order)
        # Length-grouped batching: sort within windows of 50 batches so each batch pads little,
        # then shuffle the batches. Same data, far fewer wasted tokens.
        window = size * 50
        order = [i for w in range(0, len(order), window)
                 for i in sorted(order[w:w + window], key=lambda j: len(examples[j][0]))]
        chunks = [order[i:i + size] for i in range(0, len(order), size)]
        random.Random(seed + 1).shuffle(chunks)
        order = [i for c in chunks for i in c]
    for i in range(0, len(order), size):
        chunk = [examples[j] for j in order[i:i + size]]
        # Left padding: every answer ends at the last position, so only the tail needs logits.
        n = max(len(ids) for ids, _ in chunk)
        ids = torch.full((len(chunk), n), pad_id, dtype=torch.long)
        lab = torch.full((len(chunk), n), -100, dtype=torch.long)
        att = torch.zeros((len(chunk), n), dtype=torch.long)
        for k, (x, y) in enumerate(chunk):
            ids[k, n - len(x):] = torch.tensor(x)
            lab[k, n - len(y):] = torch.tensor(y)
            att[k, n - len(x):] = 1
        yield ids, lab, att


def tail_loss(model, ids, lab, att):
    """Cross-entropy over the answer tail only. The vocabulary is large (~150k for Qwen, 262k for
    Gemma), so full-sequence logits would dominate memory; the answer is the last few dozen tokens."""
    answer_len = int((lab != -100).sum(dim=1).max().item())
    k = answer_len + 1
    out = model(input_ids=ids, attention_mask=att, logits_to_keep=k)
    logits = out.logits[:, :-1, :].float()            # positions n-k .. n-2 predict tokens n-k+1 .. n-1
    target = lab[:, -(k - 1):]
    return torch.nn.functional.cross_entropy(logits.reshape(-1, logits.size(-1)), target.reshape(-1), ignore_index=-100)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="Qwen/Qwen2.5-0.5B-Instruct")
    ap.add_argument("--out", default=os.path.join(HERE, "runs", "qwen05"))
    ap.add_argument("--epochs", type=float, default=1.0)
    ap.add_argument("--limit", type=int, default=None, help="train on a random subset (dry runs)")
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--accum", type=int, default=2)
    ap.add_argument("--eval-size", type=int, default=300)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--rank", type=int, default=16)
    ap.add_argument("--max-len", type=int, default=1024)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--save-every", type=int, default=150, help="checkpoint the adapter every N optimizer steps")
    ap.add_argument("--resume", action="store_true", help="continue from <out>/checkpoint")
    a = ap.parse_args()

    torch.manual_seed(a.seed)
    dev = device()
    dtype = torch.bfloat16 if dev in ("cuda", "mps") else torch.float32
    print(f"device={dev} dtype={dtype} base={a.base}")

    tok = AutoTokenizer.from_pretrained(a.base)
    if tok.pad_token is None:
        tok.pad_token = tok.eos_token
    model = AutoModelForCausalLM.from_pretrained(a.base, dtype=dtype).to(dev)
    model.config.use_cache = False

    lora = LoraConfig(
        r=a.rank, lora_alpha=2 * a.rank, lora_dropout=0.05, bias="none", task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )
    # Gradient checkpointing trades a little compute for much less activation memory.
    model.gradient_checkpointing_enable(gradient_checkpointing_kwargs={"use_reentrant": False})
    model.enable_input_require_grads()
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    train = [encode(tok, r, a.max_len) for r in load_chat(os.path.join(DATA, "train.chat.jsonl"), a.limit, a.seed)]
    val = [encode(tok, r, a.max_len) for r in load_chat(os.path.join(DATA, "val.chat.jsonl"), a.eval_size, a.seed)]
    lens = sorted(len(x) for x, _ in train)
    print(f"train={len(train)} val={len(val)} tokens/example median={lens[len(lens) // 2]} max={lens[-1]}", flush=True)

    opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=a.lr, weight_decay=0.0)
    steps_per_epoch = math.ceil(len(train) / a.batch / a.accum)
    total = max(1, int(steps_per_epoch * a.epochs))
    warmup = max(1, total // 20)
    sched = torch.optim.lr_scheduler.LambdaLR(
        opt, lambda s: min(1.0, (s + 1) / warmup) * max(0.05, 0.5 * (1 + math.cos(math.pi * min(1.0, s / total)))),
    )

    def evaluate():
        model.eval()
        loss, n = 0.0, 0
        with torch.no_grad():
            for ids, lab, att in batches(val, a.batch, tok.pad_token_id, False, 0):
                loss += tail_loss(model, ids.to(dev), lab.to(dev), att.to(dev)).item() * ids.size(0)
                n += ids.size(0)
        model.train()
        return loss / max(1, n)

    ckpt = os.path.join(a.out, "checkpoint")
    step, seen, started = 0, 0, time.time()
    epoch = 0
    skip = 0  # micro-batches already consumed in the resumed epoch
    if a.resume and os.path.exists(os.path.join(ckpt, "state.pt")):
        state = torch.load(os.path.join(ckpt, "state.pt"), map_location="cpu")
        from peft import set_peft_model_state_dict
        set_peft_model_state_dict(model, state["adapter"])
        opt.load_state_dict(state["opt"])
        sched.load_state_dict(state["sched"])
        step, epoch, skip = state["step"], state["epoch"], state["micro"]
        print(f"resumed at step {step}/{total} (epoch {epoch}, micro-batch {skip})", flush=True)
    else:
        print(f"val loss before training {evaluate():.4f}", flush=True)

    def save_checkpoint(micro):
        from peft import get_peft_model_state_dict
        os.makedirs(ckpt, exist_ok=True)
        tmp = os.path.join(ckpt, "state.pt.tmp")
        torch.save({"adapter": get_peft_model_state_dict(model), "opt": opt.state_dict(), "sched": sched.state_dict(),
                    "step": step, "epoch": epoch, "micro": micro}, tmp)
        os.replace(tmp, os.path.join(ckpt, "state.pt"))  # atomic: an interruption never leaves half a file

    model.train()
    while step < total:
        for i, (ids, lab, att) in enumerate(batches(train, a.batch, tok.pad_token_id, True, a.seed + epoch)):
            if i < skip:
                continue
            loss = tail_loss(model, ids.to(dev), lab.to(dev), att.to(dev))
            (loss / a.accum).backward()
            seen += ids.size(0)
            if (i + 1) % a.accum == 0:
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                opt.step(); sched.step(); opt.zero_grad()
                step += 1
                if step % 20 == 0 or step == total or step <= 3:
                    rate = seen / (time.time() - started)
                    print(f"step {step}/{total} loss {loss.item():.4f} lr {sched.get_last_lr()[0]:.2e} {rate:.1f} ex/s", flush=True)
                if step % 200 == 0:
                    print(f"  val loss {evaluate():.4f}", flush=True)
                if step % a.save_every == 0:
                    save_checkpoint(i + 1)
                if step >= total:
                    break
        epoch += 1
        skip = 0
    print(f"val loss after training {evaluate():.4f}  ({(time.time() - started) / 60:.1f} min)", flush=True)

    os.makedirs(a.out, exist_ok=True)
    model.save_pretrained(os.path.join(a.out, "adapter"))
    merged = model.merge_and_unload()
    merged.config.use_cache = True
    merged.save_pretrained(os.path.join(a.out, "merged"), safe_serialization=True)
    tok.save_pretrained(os.path.join(a.out, "merged"))
    with open(os.path.join(a.out, "train_args.json"), "w") as fh:
        json.dump(vars(a), fh, indent=1)
    print(f"saved {a.out}/merged")


if __name__ == "__main__":
    main()
