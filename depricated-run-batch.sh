#!/usr/bin/env bash
set -euo pipefail

# Put this script in its own process group so we can kill the whole tree
if [[ "${BASH_SUBSHELL:-0}" -eq 0 ]]; then
  set -m # job control
fi

cleanup() {
  echo
  echo "==> Caught signal, stopping all running jobs..."
  # Kill the whole process group of this script (children, xargs workers, etc.)
  kill -- -$$ 2>/dev/null || true
}

trap cleanup INT TERM
LOG_LEVEL="${LOG_LEVEL:-TRACE}"

LLAMA_DIR="config-files/a-star/llama-8b"
CHATGPT_DIR="config-files/a-star/chatgpt-5-mini"

RUNNER="./run-planner.sh"

LLAMA_PARALLEL=1
CHATGPT_PARALLEL=3

LOG_DIR="planner-logs/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$LOG_DIR"

# Export vars used inside xargs subshells
export LOG_LEVEL RUNNER LOG_DIR

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing dependency: $1" >&2
    exit 1
  }
}

require jq
require xargs

# jq doesn't support comments; strip // comments first (works for your jsonc style)
get_max_len() {
  local f="$1"
  sed -E 's#//.*$##' "$f" | jq -r '.search.plan["max-length"]'
}

sorted_configs() {
  local dir="$1"
  find "$dir" -maxdepth 1 -type f -name "*.jsonc" -print0 |
    while IFS= read -r -d '' f; do
      local ml
      ml="$(get_max_len "$f")"
      if [[ -z "$ml" || "$ml" == "null" ]]; then
        echo "WARN: Could not read max-length for $f, skipping" >&2
        continue
      fi
      printf "%s\t%s\n" "$ml" "$f"
    done |
    sort -n -k1,1
}

run_one() {
  local cfg="$1"
  local base out
  base="$(basename "$cfg" .jsonc)"
  out="$LOG_DIR/${base}.log"

  echo "==> START $(date '+%F %T') | $cfg | log=$out"
  set +e
  "$RUNNER" -c "$cfg" -l "$LOG_LEVEL" >"$out" 2>&1
  local code=$?
  set -e
  echo "==> DONE  $(date '+%F %T') | $cfg | exit=$code | log=$out"
  return $code
}
export -f run_one

run_pool() {
  local pool_name="$1"
  local dir="$2"
  local parallel="$3"

  echo "---- Pool: $pool_name | dir=$dir | parallel=$parallel ----"

  sorted_configs "$dir" | cut -f2 |
    xargs -r -I{} -P "$parallel" bash -lc 'run_one "$1"' _ {}
}

# Run both pools concurrently
run_pool "llama-8b" "$LLAMA_DIR" "$LLAMA_PARALLEL" &
PID_LLAMA=$!

run_pool "chatgpt-5-mini" "$CHATGPT_DIR" "$CHATGPT_PARALLEL" &
PID_CHATGPT=$!

FAIL=0
wait "$PID_LLAMA" || FAIL=1
wait "$PID_CHATGPT" || FAIL=1

echo "Logs saved under: $LOG_DIR"
exit "$FAIL"
