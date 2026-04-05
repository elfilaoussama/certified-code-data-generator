#!/bin/bash
set -euo pipefail

echo "======================================"
echo " pipeline-orchestrator setup (Linux)  "
echo "======================================"

mvn -q clean compile

echo "Done. Try: ./run.sh --help"
