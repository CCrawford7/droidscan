#!/usr/bin/env python3
"""Builds the in-app corpus asset for :diagnostics from the raw EE datasets.

Merges top_100_most_asked and top_100_unresolved (deduped by URL; most-asked
wins so a thread's solved flag survives), tags each thread with a scope, and
emits diagnostics/src/main/assets/corpus/ee_corpus.json.

Scope semantics: a phone telemetry app can reason about "phone" threads
(cellular/mobile/SIM/roaming). It cannot diagnose a home router or a broadband
line, so "broadband" threads are retained in the asset but excluded from
matching by default.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))      # .../diagnostics/tools
MODULE = os.path.dirname(HERE)                          # .../diagnostics
ROOT = os.path.dirname(MODULE)                          # .../ (repo root)

DATASETS = [
    os.path.join(ROOT, "datasets", "top_100_most_asked_ee_queries.json"),
    os.path.join(ROOT, "datasets", "top_100_unresolved_ee_queries.json"),
]
OUT = os.path.join(MODULE, "src", "main", "assets", "corpus", "ee_corpus.json")

PHONE = {
    "Mobile Services", "Mobile Data", "Mobile Network discussions",
    "Roaming", "SIM cards & Porting", "Android Devices", "Apple Devices",
}
BROADBAND = {"Broadband & Landline", "Mobile Broadband"}

# Phone-observable symptoms filed under out-of-scope categories. Re-scope them
# to "phone" so the matcher can still surface them (see the corpus analysis in
# docs/CODEBASE_AUDIT_2026-11-01.md). Matched case-insensitively on the title.
PASSTHROUGH = {
    "connected however no internet.",
    "firewall ip addresses and ports for wifi calling",
}


def scope_for(category: str, title: str) -> str:
    if title.strip().lower() in PASSTHROUGH:
        return "phone"
    if category in PHONE:
        return "phone"
    if category in BROADBAND:
        return "broadband"
    return "other"


def main() -> None:
    threads = {}
    for path in DATASETS:
        with open(path, encoding="utf-8") as fh:
            for t in json.load(fh):
                url = t["url"]
                if url not in threads:  # most-asked first => solved flag preserved
                    threads[url] = {
                        "title": t["title"],
                        "category": t["category"],
                        "url": t["url"],
                        "isSolved": bool(t["is_solved"]),
                        "views": t["views"],
                        "replies": t["replies"],
                        "scope": scope_for(t["category"], t["title"]),
                    }

    out = {"threads": sorted(threads.values(), key=lambda t: -t["views"])}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)

    by_scope = {}
    for t in out["threads"]:
        by_scope[t["scope"]] = by_scope.get(t["scope"], 0) + 1
    print(f"wrote {len(out['threads'])} threads -> {OUT}")
    print("scope counts:", by_scope)


if __name__ == "__main__":
    main()
