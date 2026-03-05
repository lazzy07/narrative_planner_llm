#!/usr/bin/env python3
"""
report_generator.py

Generates markdown reports from latest result JSON per domain, plus one random node file per run.

Output:
  <root>/report/<generation_datetime>/<domain>/<domain>.md

Input:
  <root>/result/<date_time>.json  (many files)
  each result JSON contains "nodeResultDirectory" which contains node JSON files

Behavior:
- Selects the latest result JSON per domain based on datetime encoded in filename.
- For each selected domain-run, picks one random node JSON from nodeResultDirectory.
- Cleans <root>/report BEFORE generating (deletes it entirely).
"""

from __future__ import annotations

import argparse
import json
import random
import re
import shutil
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


DT_RE = re.compile(
    r"(?P<dt>\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}(?:-\d{1,6})?)"
)

# Support "YYYY-MM-DD_HH-MM-SS" and "YYYY-MM-DD_HH-MM-SS-sss" / "-ffffff"
DT_FORMATS = [
    "%Y-%m-%d_%H-%M-%S",
    "%Y-%m-%d_%H-%M-%S-%f",
]


def parse_dt_from_filename(filename: str) -> Optional[datetime]:
    """
    Extract datetime from filename like:
      2026-03-05_13-24-44-133.json  (ms)
      2026-03-05_13-24-44.json
    Returns None if not parseable.
    """
    m = DT_RE.search(filename)
    if not m:
        return None
    dt_str = m.group("dt")
    for fmt in DT_FORMATS:
        try:
            return datetime.strptime(dt_str, fmt)
        except ValueError:
            continue
    return None


def read_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def safe_mkdir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def md_h1(title: str) -> str:
    return f"# {title}\n"


def md_h2(title: str) -> str:
    return f"## {title}\n"


def md_table(rows: List[Tuple[str, str]]) -> str:
    out = []
    out.append("| Field | Value |")
    out.append("|---|---|")
    for k, v in rows:
        k2 = str(k).replace("|", "\\|")
        v2 = str(v).replace("|", "\\|")
        out.append(f"| {k2} | {v2} |")
    return "\n".join(out) + "\n"


def md_codeblock(text: str, lang: str = "") -> str:
    if text is None:
        text = ""
    if not text.endswith("\n"):
        text += "\n"
    return f"```{lang}\n{text}```\n"


def try_parse_json_string(s: str) -> Optional[Any]:
    if not isinstance(s, str):
        return None
    s_strip = s.strip()
    if not s_strip:
        return None
    if not (s_strip.startswith("{") or s_strip.startswith("[")):
        return None
    try:
        return json.loads(s_strip)
    except Exception:
        return None


def pretty_json(obj: Any) -> str:
    return json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=False)


def normalize_path(root: Path, p: str) -> Path:
    """
    nodeResultDirectory might be:
      - absolute path
      - relative to root
    """
    pp = Path(p)
    if pp.is_absolute():
        return pp
    return (root / pp).resolve()


def list_json_files(dir_path: Path) -> List[Path]:
    if not dir_path.exists() or not dir_path.is_dir():
        return []
    files: List[Path] = []
    for p in dir_path.iterdir():
        if p.is_file() and p.suffix.lower() == ".json":
            files.append(p)
    return sorted(files)


def clean_report_folder(root: Path) -> None:
    """
    Deletes <root>/report entirely so each run starts fresh.
    """
    report_dir = root / "report"
    if report_dir.exists():
        shutil.rmtree(report_dir)


@dataclass
class DomainRun:
    domain: str
    dt: datetime
    result_path: Path
    data: Dict[str, Any]


def find_latest_runs_by_domain(root: Path) -> Dict[str, DomainRun]:
    result_dir = root / "result"
    if not result_dir.exists():
        raise FileNotFoundError(f"Missing result directory: {result_dir}")

    runs: Dict[str, DomainRun] = {}

    for p in result_dir.iterdir():
        if not p.is_file():
            continue
        if p.suffix.lower() != ".json":
            continue

        dt = parse_dt_from_filename(p.name)
        if dt is None:
            continue

        data = read_json(p)
        domain = data.get("domain")
        if not isinstance(domain, str) or not domain.strip():
            continue
        domain = domain.strip()

        existing = runs.get(domain)
        if existing is None or dt > existing.dt:
            runs[domain] = DomainRun(domain=domain, dt=dt, result_path=p, data=data)

    return runs


def render_report_markdown(
    domain_run: DomainRun,
    root: Path,
    generation_dt: datetime,
    node_sample_path: Optional[Path],
    node_sample: Optional[Dict[str, Any]],
) -> str:
    d = domain_run.data

    title = f"Domain Report: {domain_run.domain}"
    out: List[str] = []
    out.append(md_h1(title))

    # Relative result file path when possible
    try:
        rel_result_path = domain_run.result_path.relative_to(root)
    except ValueError:
        rel_result_path = domain_run.result_path

    out.append(md_table([
        ("Generated", generation_dt.strftime("%Y-%m-%d %H:%M:%S")),
        ("Domain", str(d.get("domain", ""))),
        ("Result file", f"`{rel_result_path}`"),
        ("Result datetime", domain_run.dt.strftime("%Y-%m-%d %H:%M:%S")),
        ("Search", str(d.get("search", ""))),
        ("Cost", str(d.get("cost", ""))),
        ("Heuristic", str(d.get("heuristic", ""))),
        ("LLM", str(d.get("llm", ""))),
        ("Prompt version", str(d.get("promptVersion", ""))),
        ("Use cache", str(d.get("useCache", ""))),
        ("Plan found", str(d.get("planFound", ""))),
        ("Nodes visited", str(d.get("nodesVisited", ""))),
        ("Nodes expanded", str(d.get("nodesExpanded", ""))),
        ("Max plan length", str(d.get("maxPlanLength", ""))),
        ("Max nodes", str(d.get("maxNodes", ""))),
        ("Utility", str(d.get("utility", ""))),
        ("Save node results", str(d.get("saveNodeResults", ""))),
        ("Node result directory", str(d.get("nodeResultDirectory", ""))),
    ]))

    plan = d.get("plan")
    out.append(md_h2("Current plan (plan so far)"))
    if isinstance(plan, list) and plan:
        lines = "\n".join(f"{i+1}. {str(a)}" for i, a in enumerate(plan))
        out.append(lines + "\n")
    else:
        out.append("_No plan recorded._\n")

    out.append(md_h2("Random node sample"))
    if node_sample_path is None or node_sample is None:
        out.append("_No node sample available (missing directory or no .json files)._\n")
        return "".join(out)

    try:
        rel_node_path = node_sample_path.relative_to(root)
    except ValueError:
        rel_node_path = node_sample_path

    out.append(md_table([
        ("Node file", f"`{rel_node_path}`"),
        ("nodeId", str(node_sample.get("nodeId", ""))),
    ]))

    prompt = node_sample.get("prompt", "")
    out.append(md_h2("Prompt"))
    if isinstance(prompt, str) and prompt.strip():
        out.append(md_codeblock(prompt, lang="text"))
    else:
        out.append("_No prompt found._\n")

    available = node_sample.get("availableActions")
    out.append(md_h2("Available actions"))
    if isinstance(available, list) and available:
        # Numbered list
        out.append("\n".join(f"{i+1}. {str(a)}" for i, a in enumerate(available)) + "\n")
    else:
        out.append("_No availableActions found._\n")

    llm_resp = node_sample.get("llmResponse", "")
    out.append(md_h2("LLM response"))
    if isinstance(llm_resp, str) and llm_resp.strip():
        parsed = try_parse_json_string(llm_resp)
        if parsed is not None:
            out.append(md_codeblock(pretty_json(parsed), lang="json"))
        else:
            out.append(md_codeblock(llm_resp, lang="text"))
    else:
        out.append("_No llmResponse found._\n")

    return "".join(out)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "root_folder",
        nargs="?",
        default=".",
        help="Project root folder (defaults to current directory). Must contain ./result",
    )
    ap.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Optional RNG seed for reproducible random node selection.",
    )
    ap.add_argument(
        "--domains",
        nargs="*",
        default=None,
        help="Optional domain filter list. Example: --domains bribery fantasy_any",
    )
    args = ap.parse_args()

    root = Path(args.root_folder).resolve()
    if args.seed is not None:
        random.seed(args.seed)

    runs = find_latest_runs_by_domain(root)
    if args.domains:
        wanted = set(args.domains)
        runs = {k: v for k, v in runs.items() if k in wanted}

    if not runs:
        print(f"No valid result JSON files found under: {root / 'result'}")
        return

    # Clean <root>/report before generating
    clean_report_folder(root)

    generation_dt = datetime.now()
    generation_folder = generation_dt.strftime("%Y-%m-%d_%H-%M-%S")

    report_root = root / "report" / generation_folder
    safe_mkdir(report_root)

    for domain in sorted(runs.keys()):
        run = runs[domain]
        node_dir_str = run.data.get("nodeResultDirectory", "")
        node_dir = normalize_path(root, node_dir_str) if isinstance(node_dir_str, str) else None

        node_sample_path: Optional[Path] = None
        node_sample: Optional[Dict[str, Any]] = None

        if node_dir and node_dir.exists() and node_dir.is_dir():
            node_files = list_json_files(node_dir)
            if node_files:
                node_sample_path = random.choice(node_files)
                try:
                    node_sample = read_json(node_sample_path)
                except Exception:
                    node_sample_path = None
                    node_sample = None

        md = render_report_markdown(
            domain_run=run,
            root=root,
            generation_dt=generation_dt,
            node_sample_path=node_sample_path,
            node_sample=node_sample,
        )

        out_dir = report_root / domain
        safe_mkdir(out_dir)

        out_path = out_dir / f"{domain}.md"
        out_path.write_text(md, encoding="utf-8")

        print(f"Wrote: {out_path}")


if __name__ == "__main__":
    main()
