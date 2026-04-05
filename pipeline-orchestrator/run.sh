#!/bin/bash
set -euo pipefail

# Linux-friendly wrapper for the pipeline orchestrator

if [ "$#" -eq 0 ]; then
  echo "Usage: ./run.sh <command> [args...]"
  echo "Try:   ./run.sh --help"
  exit 1
fi

# Build an argument string for exec-maven-plugin.
# The plugin splits on whitespace but honors quotes, so we quote each arg and
# escape embedded backslashes/double-quotes.
args=""
for a in "$@"; do
  escaped="$a"
  escaped="${escaped//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  args+="\"$escaped\" "
done

mvn -q exec:java -Dexec.args="$args"
