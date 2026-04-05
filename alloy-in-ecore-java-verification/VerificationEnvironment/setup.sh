#!/bin/bash
# setup.sh - Compile the Verification Environment project

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SAT_DIR="$SCRIPT_DIR/../AlloyInEcore/Source/eu.modelwriter.core.alloyinecore/lib/SatSolvers"

# Ensure Kodkod native solver libs are on java.library.path (paths with spaces break JAVA_TOOL_OPTIONS parsing).
ln -sfn "$SAT_DIR" "$HOME/aie-satsolvers"
export JAVA_TOOL_OPTIONS="-Djava.library.path=$HOME/aie-satsolvers ${JAVA_TOOL_OPTIONS:-}"

echo -e "\033[1;36m==========================================\033[0m"
echo -e "\033[1;36mCompiling AlloyInEcore Verification...\033[0m"
echo -e "\033[1;36m==========================================\033[0m"

# Install the AlloyInEcore core artifact into the local Maven repo.
(cd "$SCRIPT_DIR/../AlloyInEcore/Source/eu.modelwriter.core.alloyinecore" && mvn -q -DskipTests install)

mvn clean compile
echo -e "\033[1;32mDone!\033[0m"
