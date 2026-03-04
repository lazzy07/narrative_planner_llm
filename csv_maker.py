# File name: csv_maker.py
# Project: 
# Author: Lasantha M Senanayake
# Date created: 2026-03-04 09:38:05
# Date modified: 2026-03-04 09:40:45
# ------

#!/usr/bin/env python3
"""
Convert planner JSON run files into a single CSV.

Rules:
- Input files named like: yy-dd-mm_time.json
- Deduplicate by (domain, search, cost, heuristic).
  If duplicates exist, keep the newest file (by filename).
- All top-level JSON fields become CSV columns.
- "plan" (and any arrays/objects) are JSON-stringified.
"""

import csv
import json
import sys
from pathlib import Path
from typing import Any, Dict, Tuple, Optional

Key = Tuple[Optional[str], Optional[str], Optional[str], Optional[str]]


def stringify(v: Any) -> Any:
    if v is None:
        return ""
    if isinstance(v, (str, int, float, bool)):
        return v
    return json.dumps(v, ensure_ascii=False)


def main():
    if len(sys.argv) != 3:
        print("Usage: python3 json_runs_to_csv.py <input_dir> <output_csv>")
        sys.exit(1)

    input_dir = Path(sys.argv[1])
    output_csv = Path(sys.argv[2])

    files = sorted(input_dir.glob("*.json"), key=lambda p: p.name)
    if not files:
        print("No .json files found.")
        sys.exit(1)

    runs: Dict[Key, Dict[str, Any]] = {}
    file_map: Dict[Key, str] = {}

    # Deduplicate (newest file overwrites older one)
    for path in files:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        key: Key = (
            data.get("domain"),
            data.get("search"),
            data.get("cost"),
            data.get("heuristic"),
        )

        runs[key] = data
        file_map[key] = path.name

    # Collect all possible fields
    all_fields = set()
    for data in runs.values():
        all_fields.update(data.keys())

    fieldnames = ["file"] + sorted(all_fields)

    # Write CSV
    with open(output_csv, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        for key in sorted(runs.keys()):
            row = {"file": file_map[key]}
            data = runs[key]
            for field in all_fields:
                row[field] = stringify(data.get(field))
            writer.writerow(row)

    print(f"Wrote {len(runs)} rows to {output_csv}")


if __name__ == "__main__":
    main()
