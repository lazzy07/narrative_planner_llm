# File name: run-batch.sh
# Project:
# Author: Lasantha M Senanayake
# Date created: 2026-03-04 03:23:14
# Date modified: 2026-03-04 03:23:19
# ------

#!/usr/bin/env bash
set -euo pipefail

LOG_LEVEL="${LOG_LEVEL:-TRACE}"

LLAMA_DIR="config-files/a-star/llama-8b"
CHATGPT_DIR="config-files/a-star/chatgpt-5-mini"

RUNNER="./run-planner.sh"

LLAMA_PARALLEL=1
CHATGPT_PARALLEL=3

LOG_DIR="planner-logs/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$LOG_DIR"

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing dependency: $1" >&2
    exit 1
  }
}

require jq
require xargs

# Extract max-length from jsonc by letting jq handle it.
# jq doesn't support comments directly, but it WILL parse if we strip // comments safely.
# We'll do a simple line-based removal of //... (good enough for your config style).
get_max_len() {
  local f="$1"
  # Remove // comments, then parse
  sed -E 's#//.*$##' "$f" | jq -r '.search.plan["max-length"]'
}

# Build "maxlen<TAB>filepath" list and sort by maxlen asc
sorted_configs() {
  local dir="$1"
  find "$dir" -maxdepth 1 -type f -name "*.jsonc" -print0 |
    while IFS= read -r -d '' f; do
      ml="$(get_max_len "$f")"
      if [[ "$ml" == "null" || -z "$ml" ]]; then
        echo "WARN: Could not read max-length for $f, skipping" >&2
        continue
      fi
      printf "%s\t%s\n" "$ml" "$f"
    done |
    sort -n -k1,1
}

run_one() {
  local cfg="$1"
  local base
  base="$(basename "$cfg" .jsonc)"
  local out="$LOG_DIR/${base}.log"

  echo "==> START $(date '+%F %T') | $cfg | log=$out"
  set +e
  "$RUNNER" -c "$cfg" -l "$LOG_LEVEL" >"$out" 2>&1
  local code=$?
  set -e
  echo "==> DONE  $(date '+%F %T') | $cfg | exit=$code | log=$out"
  return $code
}

# Run a list with limited parallelism
run_pool() {
  local pool_name="$1"
  local dir="$2"
  local parallel="$3"

  echo "---- Pool: $pool_name | dir=$dir | parallel=$parallel ----"

  # sorted list -> just the filepaths
  sorted_configs "$dir" | cut -f2 |
    xargs -I{} -P "$parallel" bash -lc '
        set -euo pipefail
        cfg="$1"
        '"$(declare -f run_one)"'
        run_one "$cfg"
      ' _ {}
}

# Kick both pools concurrently
# - llama: 1 at a time
# - chatgpt: 3 at a time
run_pool "llama-8b" "$LLAMA_DIR" "$LLAMA_PARALLEL" &
PID_LLAMA=$!

run_pool "chatgpt-5-mini" "$CHATGPT_DIR" "$CHATGPT_PARALLEL" &
PID_CHATGPT=$!

# Wait for both pools
FAIL=0
wait "$PID_LLAMA" || FAIL=1
wait "$PID_CHATGPT" || FAIL=1

echo "Logs saved under: $LOG_DIR"
exit "$FAIL"
