#!/usr/bin/env bash
set -euo pipefail

# Resolve repo root relative to this script
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"

PLANNER_JAR="${ROOT_DIR}/planner/target/planner-1.0-SNAPSHOT.jar"

# Build if missing
if [[ ! -f "$PLANNER_JAR" ]]; then
  echo "ℹ️  Missing $PLANNER_JAR"
  echo "   Building (shade/fat jar)..."
  (cd "$ROOT_DIR" && ./mvnw -q -DskipTests -pl planner -am package)
fi

# Sanity check
if [[ ! -f "$PLANNER_JAR" ]]; then
  echo "❌ Still missing $PLANNER_JAR"
  echo "   Try: cd \"$ROOT_DIR\" && ./mvnw -DskipTests -pl planner -am package"
  exit 1
fi

exec java -jar "$PLANNER_JAR" "$@"
