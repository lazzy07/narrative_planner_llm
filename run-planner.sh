# File name: run-planner.sh
# Project:
# Author: Lasantha M Senanayake
# Date created: 2026-01-25 23:10:53
# Date modified: 2026-01-25 23:11:01
# ------

#!/usr/bin/env bash
set -euo pipefail

# ---- Config -------------------------------------------------

APP_MAIN="nil.lazzy07.planner.App"

PLANNER_JAR="planner/target/planner-1.0-SNAPSHOT.jar"
DOMAIN_JAR="domain/target/domain-1.0-SNAPSHOT.jar"
LLM_JAR="llm/target/llm-1.0-SNAPSHOT.jar"

# ---- Sanity checks -----------------------------------------

for jar in "$PLANNER_JAR" "$DOMAIN_JAR" "$LLM_JAR"; do
  if [[ ! -f "$jar" ]]; then
    echo "❌ Missing $jar"
    echo "   Run: ./mvnw -DskipTests package"
    exit 1
  fi
done

# ---- Classpath ---------------------------------------------

CLASSPATH="$PLANNER_JAR:$DOMAIN_JAR:$LLM_JAR"

# ---- Run ---------------------------------------------------

exec java -cp "$CLASSPATH" "$APP_MAIN" "$@"
