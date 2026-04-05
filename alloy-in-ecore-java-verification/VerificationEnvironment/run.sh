#!/bin/bash
# run.sh - AlloyInEcore Verification Pipeline
# Usage: ./run.sh -i <instance.json>

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SAT_DIR="$SCRIPT_DIR/../AlloyInEcore/Source/eu.modelwriter.core.alloyinecore/lib/SatSolvers"

# Ensure Kodkod native solver libs are on java.library.path.
ln -sfn "$SAT_DIR" "$HOME/aie-satsolvers"
export JAVA_TOOL_OPTIONS="-Djava.library.path=$HOME/aie-satsolvers ${JAVA_TOOL_OPTIONS:-}"

cd "$(dirname "$0")" || exit 1

if [ $# -eq 0 ]; then
    mvn -q exec:java -Dexec.args="--help"
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
