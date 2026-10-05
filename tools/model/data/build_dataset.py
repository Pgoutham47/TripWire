#!/usr/bin/env python3
"""Builds the tactic dataset (PRD 10.5) from seeds.py.

    python3 tools/model/data/build_dataset.py [--scam 6000 --benign 9000 --seed 7]

Writes tools/model/data/build/{train,val,test}.jsonl and stats.json. Each line:
    {"id", "split", "group", "kind", "family", "lang", "app", "is_group",
     "context": [..earlier messages..], "text", "tags": [...], "aug"}

Seeds are assigned to a split before anything is composed, so no seed (and no paraphrase of
it) appears in two splits. The test split doubles as the held-out set of PRD 10.7.
"""
import argparse
import hashlib
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from seeds import BENIGN, EMOJI, FAMILIES, INJECTIONS, SCAM, SLOTS  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "build")
APPS = ["com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging"]
SPLITS = ("train", "val", "test")
FOLLOW_UPS = {
    "en": ["Ok sir", "Done", "Received, thank you", "Fine", "I will check"],
    "hi": ["ठीक है", "जी", "हो गया"],
    "hi-Latn": ["Ji sir", "Theek hai", "Ho gaya", "Ok bhai"],
}


def assign_splits(items, rng, test_share=0.15):
    """items: list of seed keys. Deterministic, stratified: at least one test and one val seed when possible.
    Benign seeds use a larger test share: false alarms on benign messages are the metric that matters most."""
    keys = list(items)
    rng.shuffle(keys)
    n = len(keys)
    n_test = 1 if n >= 2 else 0
    n_val = 1 if n >= 4 else 0
    if test_share > 0.15 and n >= 4:
        n_test = max(n_test, round(n * test_share))
    n_test += max(0, round(n * 0.15) - 1) if n >= 7 and test_share <= 0.15 else 0
    n_val += max(0, round(n * 0.15) - 1) if n >= 7 else 0
    out = {}
    for i, k in enumerate(keys):
        out[k] = "test" if i < n_test else "val" if i < n_test + n_val else "train"
    return out


def fill(template, rng):
    def repl(m):
        values = SLOTS.get(m.group(1))
        return rng.choice(values) if values else m.group(0)
    return re.sub(r"\{([a-z_]+)\}", repl, template)


def build(n_scam, n_benign, seed):
    rng = random.Random(seed)

    # Index seeds: key -> (tags, text, lang, tactic_or_category)
    scam_seeds, benign_seeds = {}, {}
    for tactic, by_lang in SCAM.items():
        for lang, items in by_lang.items():
            for i, (tags, text) in enumerate(items):
                scam_seeds[f"s/{tactic}/{lang}/{i}"] = (tags, text, lang, tactic)
    for cat, by_lang in BENIGN.items():
        for lang, items in by_lang.items():
            for i, (tags, text) in enumerate(items):
                benign_seeds[f"b/{cat}/{lang}/{i}"] = (tags, text, lang, cat)

    split_of = {}
    for tactic, by_lang in SCAM.items():
        for lang in by_lang:
            split_of.update(assign_splits([k for k in scam_seeds if k.startswith(f"s/{tactic}/{lang}/")], rng))
    for cat, by_lang in BENIGN.items():
        for lang in by_lang:
            split_of.update(assign_splits([k for k in benign_seeds if k.startswith(f"b/{cat}/{lang}/")], rng, test_share=0.3))

    def pick(seeds, prefix, split, lang, exclude=()):
        pool = [k for k in seeds if k.startswith(prefix) and split_of[k] == split and seeds[k][2] == lang and k not in exclude]
        return rng.choice(pool) if pool else None

    records = {s: [] for s in SPLITS}
    seen = set()
    langs = ["en", "hi", "hi-Latn"]
    lang_weights = [0.45, 0.25, 0.30]

    def emit(split, kind, family, lang, groups, context, text, tags, is_group, app, aug=None, base=None):
        # De-duplicate on the text before augmentation, so noise never manufactures new records.
        key = (base or text, tuple(context))
        if key in seen:
            return False
        seen.add(key)
        rid = hashlib.sha1(f"{split}|{text}|{context}".encode()).hexdigest()[:12]
        records[split].append({
            "id": rid, "split": split, "group": "+".join(sorted(set(groups))), "kind": kind, "family": family,
            "lang": lang, "app": app, "is_group": is_group, "context": context, "text": text,
            "tags": sorted(set(tags)), "aug": aug,
        })
        return True

    def augment(text, rng):
        r = rng.random()
        if r < 0.35:
            return text + " " + rng.choice(INJECTIONS), "injection"
        if r < 0.6:
            return " ".join(w + (rng.choice(EMOJI) if rng.random() < 0.15 else "") for w in text.split()), "emoji"
        if r < 0.8:
            return text.upper() if rng.random() < 0.5 else text.lower(), "case"
        # typo: swap two letters in a long word
        words = text.split()
        long = [i for i, w in enumerate(words) if len(w) > 5 and w.isascii()]
        if long:
            i = rng.choice(long)
            w = list(words[i]); j = rng.randrange(1, len(w) - 2); w[j], w[j + 1] = w[j + 1], w[j]
            words[i] = "".join(w)
        return " ".join(words), "typo"

    targets = {"train": 0.7, "val": 0.15, "test": 0.15}

    # ---- scam messages
    for split in SPLITS:
        want = int(n_scam * targets[split])
        tries = 0
        made = 0
        while made < want and tries < want * 40:
            tries += 1
            family = rng.choice(list(FAMILIES))
            lang = rng.choices(langs, lang_weights)[0]
            app = rng.choice(APPS)
            k = rng.choices([1, 2, 3], [0.45, 0.4, 0.15])[0]
            tactics = rng.sample(FAMILIES[family], k)
            parts, tags, groups = [], [], []
            for t in tactics:
                key = pick(scam_seeds, f"s/{t}/", split, lang)
                if key is None:
                    continue
                stags, stext, _, _ = scam_seeds[key]
                parts.append(fill(stext, rng)); tags += stags; groups.append(key)
            if not parts:
                continue
            context = []
            if rng.random() < 0.35:
                for t in rng.sample(FAMILIES[family], rng.randint(1, 3)):
                    key = pick(scam_seeds, f"s/{t}/", split, lang, exclude=groups)
                    if key:
                        context.append(fill(scam_seeds[key][1], rng)); groups.append(key)
            text = base = " ".join(parts)
            aug = None
            if rng.random() < 0.08:
                text, aug = augment(text, rng)
            is_group = family == "fake_investment" and rng.random() < 0.4
            if emit(split, "scam", family, lang, groups, context, text, tags, is_group, app, aug, base):
                made += 1
            # Follow-up with no tactic of its own, after scam context: teaches "label only the new message".
            if context and rng.random() < 0.12:
                emit(split, "scam_followup", family, lang, groups, context + [text], rng.choice(FOLLOW_UPS[lang]), [], False, app)

    # ---- benign messages
    for split in SPLITS:
        want = int(n_benign * targets[split])
        tries = 0
        made = 0
        cats = list(BENIGN)
        while made < want and tries < want * 40:
            tries += 1
            cat = rng.choice(cats)
            lang = rng.choice(list(BENIGN[cat]))
            key = pick(benign_seeds, f"b/{cat}/", split, lang)
            if key is None:
                continue
            tags, text, _, _ = benign_seeds[key]
            text = fill(text, rng)
            groups = [key]
            all_tags = list(tags)
            if rng.random() < 0.3:
                key2 = pick(benign_seeds, f"b/{cat}/", split, lang)
                if key2 and key2 != key:
                    tags2, text2, _, _ = benign_seeds[key2]
                    text = text + " " + fill(text2, rng); all_tags += tags2; groups.append(key2)
            context = []
            if rng.random() < 0.3:
                # Context is an earlier, different message (review batch 001 found identical pairs),
                # from any everyday category: a chat's earlier messages can be about anything.
                key3 = pick(benign_seeds, "b/", split, lang, exclude=groups)
                if key3:
                    context.append(fill(benign_seeds[key3][1], rng)); groups.append(key3)
            base = text
            aug = None
            if rng.random() < 0.05:
                text, aug = augment(text, rng)
            is_group = cat in ("investing_chat", "school_society") and rng.random() < 0.6
            if emit(split, "benign", cat, lang, groups, context, text, all_tags, is_group, rng.choice(APPS), aug, base):
                made += 1
    return records


# authority_claim is not high-risk: genuine brokers, banks and couriers make the same claim.
HIGH_RISK = {"guaranteed_returns", "legal_threat", "secrecy",
             "remote_access_request", "credential_request", "fee_to_withdraw"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scam", type=int, default=6000)
    ap.add_argument("--benign", type=int, default=9000)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    records = build(a.scam, a.benign, a.seed)

    # Guard: a benign record must never carry a high-risk tag (labeling guide rule 2).
    for split, rs in records.items():
        for r in rs:
            if r["kind"] == "benign" and HIGH_RISK & set(r["tags"]):
                sys.exit(f"benign record with high-risk tag: {r}")

    # Guard: no seed group crosses splits.
    owner = {}
    for split, rs in records.items():
        for r in rs:
            for g in r["group"].split("+"):
                if owner.setdefault(g, split) != split:
                    sys.exit(f"seed {g} appears in {owner[g]} and {split}")

    os.makedirs(OUT, exist_ok=True)
    stats = {"seed": a.seed, "splits": {}}
    for split, rs in records.items():
        with open(os.path.join(OUT, f"{split}.jsonl"), "w", encoding="utf-8") as fh:
            for r in rs:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
        tag_counts = {}
        for r in rs:
            for t in r["tags"]:
                tag_counts[t] = tag_counts.get(t, 0) + 1
        stats["splits"][split] = {
            "records": len(rs),
            "scam": sum(r["kind"] == "scam" for r in rs),
            "followup": sum(r["kind"] == "scam_followup" for r in rs),
            "benign": sum(r["kind"] == "benign" for r in rs),
            "augmented": sum(r["aug"] is not None for r in rs),
            "by_lang": {l: sum(r["lang"] == l for r in rs) for l in ("en", "hi", "hi-Latn")},
            "tags": dict(sorted(tag_counts.items())),
        }
    with open(os.path.join(OUT, "stats.json"), "w") as fh:
        json.dump(stats, fh, indent=1, ensure_ascii=False)
    for split, st in stats["splits"].items():
        print(f"{split:5s} {st['records']:5d} records  scam={st['scam']} followup={st['followup']} benign={st['benign']} aug={st['augmented']} langs={st['by_lang']}")


if __name__ == "__main__":
    main()
