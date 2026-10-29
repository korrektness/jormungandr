#!/usr/bin/env bash
# check-testdata.sh — Structural checks on testData. Takes no arguments and
# needs no build.

set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"

# shellcheck source=lib.sh
source agent-scripts/lib.sh

TEST_DATA_DIRS=(
    formver.compiler-plugin/testData
    formver.compiler-plugin/locality/testData
)
RUNNABLE_DIRS=(
    formver.compiler-plugin/testData/diagnostics
    formver.compiler-plugin/testData/programs
    formver.compiler-plugin/locality/testData/diagnostics
)

status=0

# The process substitutions below discard find's status, so a missing directory
# would silently narrow the checks. Caught here instead.
for dir in "${TEST_DATA_DIRS[@]}" "${RUNNABLE_DIRS[@]}"; do
    if [ ! -d "$dir" ]; then
        echo "testData directory is missing: $dir" >&2
        exit 1
    fi
done

golden_files() {
    find "${TEST_DATA_DIRS[@]}" \
        \( -name "*.fir.diag.txt" -o -name "*.viper.diag.txt" -o -name "*.viper.txt" \)
}

# Golden files are keyed to a .kt of the same stem. Renaming or deleting the
# source leaves the golden behind, asserted against nothing.
while read -r f; do
    src="${f%.fir.diag.txt}"
    src="${src%.viper.diag.txt}"
    src="${src%.viper.txt}"
    if [ ! -f "$src.kt" ]; then
        echo "golden file with no .kt source: $f"
        status=1
    fi
    runnable=0
    for dir in "${RUNNABLE_DIRS[@]}"; do
        if [[ "$f" == "$dir/"* ]]; then
            runnable=1
            break
        fi
    done
    if [ "$runnable" -eq 0 ]; then
        echo "golden file outside runnable discovery: $f"
        status=1
    fi
done < <(golden_files)

# DiagnosticsCollector writes an empty file rather than deleting it when the
# diagnostics it recorded go away.
while read -r f; do
    if [ ! -s "$f" ]; then
        echo "empty golden file: $f"
        status=1
    fi
done < <(golden_files)

while read -r f; do
    if identity_hash_lines "$f"; then
        echo "  ^ identity hash in golden file: render a stable description instead"
        status=1
    fi
done < <(golden_files)

if [ "$status" -eq 0 ]; then
    echo "testData checks passed"
fi
exit "$status"
