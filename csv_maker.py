#!/usr/bin/env python3

import csv
import json
import sys
from pathlib import Path
from typing import Any, Dict, Tuple

# Now includes llm too:
# (domain, search, cost, heuristic, llm)
Key = Tuple[str, str, str, str, str]


def stringify(v: Any) -> Any:
    if v is None:
        return ""
    if isinstance(v, (str, int, float, bool)):
        return v
    return json.dumps(v, ensure_ascii=False)


def get_key(data: Dict[str, Any]) -> Key:
    # Normalize missing values to empty string
    return (
        str(data.get("domain") or ""),
        str(data.get("search") or ""),
        str(data.get("cost") or ""),
        str(data.get("heuristic") or ""),
        str(data.get("llm") or ""),
    )


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: python3 csv_maker.py <input_dir> <output_csv>", file=sys.stderr)
        return 2

    input_dir = Path(sys.argv[1]).expanduser().resolve()
    output_csv = Path(sys.argv[2]).expanduser().resolve()

    files = sorted(input_dir.glob("*.json"), key=lambda p: p.name)
    if not files:
        print(f"No .json files found in {input_dir}", file=sys.stderr)
        return 2

    # Keep newest by filename: iterate ascending and overwrite older
    newest_by_key: Dict[Key, Dict[str, Any]] = {}
    newest_file_by_key: Dict[Key, str] = {}

    all_fields = set()

    for path in files:
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)

        key = get_key(data)
        newest_by_key[key] = data
        newest_file_by_key[key] = path.name
        all_fields.update(data.keys())

    # Write rows in filename order of the kept (newest) files
    kept_keys = sorted(newest_by_key.keys(), key=lambda k: newest_file_by_key[k])

    fieldnames = ["file"] + sorted(all_fields)

    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        for key in kept_keys:
            data = newest_by_key[key]
            row = {"file": newest_file_by_key[key]}
            for field in all_fields:
                row[field] = stringify(data.get(field))
            writer.writerow(row)

    removed = len(files) - len(kept_keys)
    print(
        f"Wrote {len(kept_keys)} rows to {output_csv} "
        f"(from {len(files)} files; {removed} duplicates removed)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
