# File name: setup-run-batch.sh
# Project:
# Author: Lasantha M Senanayake
# Date created: 2026-03-06 22:25:30
# Date modified: 2026-03-06 22:25:50
# ------

#!/usr/bin/env bash
set -e

python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

echo "Environment ready."
echo "Run with:"
echo "source .venv/bin/activate"
