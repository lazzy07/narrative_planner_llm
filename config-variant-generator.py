#!/usr/bin/env python3
"""
config-variant-generator.py

Generate config variants from existing .jsonc files by changing:
- available LLMs
- search type
- heuristic
- cost
- prompt version
...and any other keys via --set path=value

No external dependencies (stdlib only). Supports JSONC (// and /* */ comments).
Optionally zips the output.

Examples:

# 1) Generate variants from all jsonc under config-files/a-star
python3 config-variant-generator.py \
  --in-dir config-files/a-star \
  --out-dir generated-configs \
  --llms llama-8b,chatgpt-5-mini \
  --search-types a-star,best-first \
  --heuristics relaxed-plan,repeated-root \
  --costs plan-cost,unit-cost \
  --prompt-versions v1,v2 \
  --zip-out generated-configs.zip

# 2) Apply a single modification across all configs (no combos)
python3 config-variant-generator.py \
  --in-dir config-files \
  --out-dir generated-configs \
  --set llm.cache.enabled=true \
  --set search.plan.maxNodes=10000

# 3) Pairwise mapping (lists aligned by index instead of full cartesian product)
python3 config-variant-generator.py \
  --in-dir config-files/a-star \
  --out-dir generated-configs \
  --pairwise \
  --llms llama-8b,chatgpt-5-mini \
  --prompt-versions v3,v4
"""

from __future__ import annotations

import argparse
import copy
import json
import os
import re
import sys
import zipfile
from dataclasses import dataclass
from itertools import product
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional


# -------------------------
# JSONC parsing (stdlib-only)
# -------------------------

_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
_LINE_COMMENT_RE = re.compile(r"(^|[^:\\])//.*?$", re.MULTILINE)

def strip_jsonc(s: str) -> str:
    """
    Remove // line comments and /* */ block comments.
    Tries to be safe-ish; it is not a perfect lexer, but works well for typical config JSONC.
    """
    s = _BLOCK_COMMENT_RE.sub("", s)
    # Remove // comments that are not part of URLs like http:// (best-effort)
    # Keeps the char before // if matched by group(1).
    s = _LINE_COMMENT_RE.sub(lambda m: (m.group(1) or ""), s)
    return s

def load_jsonc(path: Path) -> Any:
    raw = path.read_text(encoding="utf-8")
    cleaned = strip_jsonc(raw).strip()
    return json.loads(cleaned)

def dump_json(obj: Any) -> str:
    return json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


# -------------------------
# Path set helpers
# -------------------------

def parse_scalar(value: str) -> Any:
    """
    Parse CLI scalar: true/false/null/number/json-array/json-object/string
    """
    v = value.strip()

    if v.lower() == "true":
        return True
    if v.lower() == "false":
        return False
    if v.lower() in ("null", "none"):
        return None

    # number?
    if re.fullmatch(r"-?\d+", v):
        try:
            return int(v)
        except ValueError:
            pass
    if re.fullmatch(r"-?\d+\.\d+", v):
        try:
            return float(v)
        except ValueError:
            pass

    # json container?
    if (v.startswith("{") and v.endswith("}")) or (v.startswith("[") and v.endswith("]")):
        try:
            return json.loads(v)
        except Exception:
            # fall back to string
            return v

    return v

def set_by_dotted_path(obj: Dict[str, Any], dotted_path: str, value: Any) -> None:
    """
    Set obj["a"]["b"]["c"] = value for dotted_path "a.b.c".
    Creates intermediate dicts if missing.
    """
    parts = [p for p in dotted_path.split(".") if p]
    if not parts:
        raise ValueError("Empty path")

    cur: Any = obj
    for p in parts[:-1]:
        if not isinstance(cur, dict):
            raise TypeError(f"Cannot traverse into non-object at '{p}' for path '{dotted_path}'")
        if p not in cur or not isinstance(cur[p], dict):
            cur[p] = {}
        cur = cur[p]

    last = parts[-1]
    if not isinstance(cur, dict):
        raise TypeError(f"Cannot set on non-object at '{last}' for path '{dotted_path}'")
    cur[last] = value


# -------------------------
# Common schema mappings
# -------------------------

# These are "best guess" defaults that match many planner config shapes.
# If your schema differs, just use --set for exact paths.
COMMON_FIELDS = {
    "llm": [
        "llm.model.name",       # e.g. chatgpt-5-mini / llama-8b
        "llm.model",            # fallback
        "llm.name",             # fallback
    ],
    "search_type": [
        "search.type.name",
        "search.type",
        "search.name",
    ],
    "heuristic": [
        "search.heuristic.type",
        "search.heuristic.name",
        "search.heuristic",
        "heuristic.type",
        "heuristic.name",
        "heuristic",
    ],
    "cost": [
        "search.cost.type",
        "search.cost.name",
        "search.cost",
        "cost.type",
        "cost.name",
        "cost",
    ],
    "prompt_version": [
        "llm.prompt.version",
        "prompt.version",
        "promptVersion",
    ],
}

def apply_first_existing_path(cfg: Dict[str, Any], candidate_paths: List[str], value: Any) -> bool:
    """
    Try setting the first path that looks compatible.
    Returns True if we set something, False otherwise.
    """
    # We’ll attempt to set paths; if intermediate objects are missing, we create them.
    # But we prefer paths that already exist to reduce schema drift.
    for p in candidate_paths:
        if path_exists(cfg, p):
            set_by_dotted_path(cfg, p, value)
            return True
    # If none exist, set the first candidate path (creates objects)
    if candidate_paths:
        set_by_dotted_path(cfg, candidate_paths[0], value)
        return True
    return False

def path_exists(cfg: Dict[str, Any], dotted_path: str) -> bool:
    parts = [p for p in dotted_path.split(".") if p]
    cur: Any = cfg
    for p in parts:
        if not isinstance(cur, dict) or p not in cur:
            return False
        cur = cur[p]
    return True


# -------------------------
# Variant generation
# -------------------------

@dataclass(frozen=True)
class VariantChoice:
    llm: Optional[str] = None
    search_type: Optional[str] = None
    heuristic: Optional[str] = None
    cost: Optional[str] = None
    prompt_version: Optional[str] = None

def parse_csv_list(s: Optional[str]) -> List[str]:
    if not s:
        return []
    items = [x.strip() for x in s.split(",")]
    return [x for x in items if x]

def make_choices(
    llms: List[str],
    search_types: List[str],
    heuristics: List[str],
    costs: List[str],
    prompt_versions: List[str],
    pairwise: bool,
) -> List[VariantChoice]:
    # If all empty => single “do nothing” choice
    if not any([llms, search_types, heuristics, costs, prompt_versions]):
        return [VariantChoice()]

    if pairwise:
        # Align by index; missing lists treated as length-1 with None
        lists = [
            llms or [None],
            search_types or [None],
            heuristics or [None],
            costs or [None],
            prompt_versions or [None],
        ]
        L = max(len(x) for x in lists)
        def get(lst, i):
            return lst[i] if i < len(lst) else lst[-1]
        out = []
        for i in range(L):
            out.append(VariantChoice(
                llm=get(lists[0], i),
                search_type=get(lists[1], i),
                heuristic=get(lists[2], i),
                cost=get(lists[3], i),
                prompt_version=get(lists[4], i),
            ))
        return out

    # Cartesian product (default)
    return [
        VariantChoice(llm=a, search_type=b, heuristic=c, cost=d, prompt_version=e)
        for a, b, c, d, e in product(
            llms or [None],
            search_types or [None],
            heuristics or [None],
            costs or [None],
            prompt_versions or [None],
        )
    ]


def sanitize_for_filename(s: str) -> str:
    return re.sub(r"[^A-Za-z0-9._=-]+", "_", s)

def variant_suffix(v: VariantChoice) -> str:
    parts = []
    if v.llm is not None:
        parts.append(f"llm={sanitize_for_filename(v.llm)}")
    if v.search_type is not None:
        parts.append(f"search={sanitize_for_filename(v.search_type)}")
    if v.heuristic is not None:
        parts.append(f"heur={sanitize_for_filename(v.heuristic)}")
    if v.cost is not None:
        parts.append(f"cost={sanitize_for_filename(v.cost)}")
    if v.prompt_version is not None:
        parts.append(f"prompt={sanitize_for_filename(v.prompt_version)}")
    return "__" + "__".join(parts) if parts else ""

def apply_variant(cfg: Dict[str, Any], v: VariantChoice) -> None:
    if v.llm is not None:
        apply_first_existing_path(cfg, COMMON_FIELDS["llm"], v.llm)
    if v.search_type is not None:
        apply_first_existing_path(cfg, COMMON_FIELDS["search_type"], v.search_type)
    if v.heuristic is not None:
        apply_first_existing_path(cfg, COMMON_FIELDS["heuristic"], v.heuristic)
    if v.cost is not None:
        apply_first_existing_path(cfg, COMMON_FIELDS["cost"], v.cost)
    if v.prompt_version is not None:
        apply_first_existing_path(cfg, COMMON_FIELDS["prompt_version"], v.prompt_version)


def find_jsonc_files(in_dir: Path) -> List[Path]:
    return sorted([p for p in in_dir.rglob("*.jsonc") if p.is_file()])


def write_zip(zip_path: Path, folder: Path) -> None:
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for p in folder.rglob("*"):
            if p.is_file():
                zf.write(p, arcname=str(p.relative_to(folder)))


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate .jsonc config variants and optionally zip them.")
    ap.add_argument("--in-dir", required=True, help="Input directory containing .jsonc configs (searched recursively).")
    ap.add_argument("--out-dir", required=True, help="Output directory for generated configs.")
    ap.add_argument("--zip-out", default=None, help="Optional path to write a zip of --out-dir.")
    ap.add_argument("--pairwise", action="store_true", help="Align list options by index (instead of cartesian product).")

    ap.add_argument("--llms", default=None, help="Comma-separated LLM names.")
    ap.add_argument("--search-types", default=None, help="Comma-separated search types.")
    ap.add_argument("--heuristics", default=None, help="Comma-separated heuristics.")
    ap.add_argument("--costs", default=None, help="Comma-separated costs.")
    ap.add_argument("--prompt-versions", default=None, help="Comma-separated prompt versions.")

    ap.add_argument(
        "--set",
        action="append",
        default=[],
        help="Extra overrides: dotted.path=value (repeatable). Example: --set llm.cache.enabled=true",
    )

    ap.add_argument(
        "--flat",
        action="store_true",
        help="Write all generated files directly into out-dir (no subfolders mirroring input).",
    )

    ap.add_argument(
        "--keep-name",
        action="store_true",
        help="Do not append variant suffix to filename; last writer wins (useful with single choice + --set).",
    )

    args = ap.parse_args()

    in_dir = Path(args.in_dir).resolve()
    out_dir = Path(args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    files = find_jsonc_files(in_dir)
    if not files:
        print(f"[error] No .jsonc files found under: {in_dir}", file=sys.stderr)
        return 2

    llms = parse_csv_list(args.llms)
    search_types = parse_csv_list(args.search_types)
    heuristics = parse_csv_list(args.heuristics)
    costs = parse_csv_list(args.costs)
    prompt_versions = parse_csv_list(args.prompt_versions)

    choices = make_choices(llms, search_types, heuristics, costs, prompt_versions, args.pairwise)

    # Parse --set overrides
    extra_sets: List[Tuple[str, Any]] = []
    for item in args.set:
        if "=" not in item:
            print(f"[error] Invalid --set '{item}'. Use dotted.path=value", file=sys.stderr)
            return 2
        path, val = item.split("=", 1)
        extra_sets.append((path.strip(), parse_scalar(val)))

    written = 0

    for src in files:
        try:
            base_cfg = load_jsonc(src)
        except Exception as e:
            print(f"[error] Failed to parse {src}: {e}", file=sys.stderr)
            return 2

        if not isinstance(base_cfg, dict):
            print(f"[error] Expected top-level JSON object in {src}", file=sys.stderr)
            return 2

        rel = src.relative_to(in_dir)
        rel_parent = rel.parent

        stem = src.stem  # without .jsonc
        for v in choices:
            cfg = copy.deepcopy(base_cfg)

            # Apply variant knobs
            apply_variant(cfg, v)

            # Apply generic overrides
            for path, val in extra_sets:
                set_by_dotted_path(cfg, path, val)

            suffix = "" if args.keep_name else variant_suffix(v)
            out_name = f"{stem}{suffix}.jsonc"

            if args.flat:
                dst = out_dir / out_name
            else:
                (out_dir / rel_parent).mkdir(parents=True, exist_ok=True)
                dst = out_dir / rel_parent / out_name

            dst.write_text(dump_json(cfg), encoding="utf-8")
            written += 1

    print(f"[ok] Wrote {written} config file(s) into: {out_dir}")

    if args.zip_out:
        zip_path = Path(args.zip_out).resolve()
        # If user gave a directory, choose a default file name inside it
        if zip_path.exists() and zip_path.is_dir():
            zip_path = zip_path / "configs.zip"
        zip_path.parent.mkdir(parents=True, exist_ok=True)
        write_zip(zip_path, out_dir)
        print(f"[ok] Zipped to: {zip_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
