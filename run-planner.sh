# File name: run-planner.sh
# Project:
# Author: Lasantha M Senanayake
# Date created: 2026-01-25 23:10:53
# Date modified: 2026-01-27 01:09:53
# ------

#!/usr/bin/env bash
set -euo pipefail

# Run from anywhere: resolve repo root relative to this script
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "${SCRIPT_DIR}" && pwd)"

PLANNER_JAR="${ROOT_DIR}/planner/target/planner-1.0-SNAPSHOT.jar"

# If the jar doesn't exist, build it (comment out if you don't want auto-build)
if [[ ! -f "$PLANNER_JAR" ]]; then
  echo "ℹ️  Missing ${PLANNER_JAR}"
  echo "   Building (shade/fat jar)..."
  (cd "$ROOT_DIR" && ./mvnw -q -DskipTests -pl planner -am package)
fi

# Sanity check
if [[ ! -f "$PLANNER_JAR" ]]; then
  echo "❌ Still missing ${PLANNER_JAR}"
  echo "   Try: (cd \"$ROOT_DIR\" && ./mvnw -DskipTests -pl planner -am package)"
  exit 1
fi

# Run fat jar
exec java -jar "$PLANNER_JAR" "$@"
