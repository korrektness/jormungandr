#!/usr/bin/env bash
# The gate's checked candidate is HEAD in this checkout.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=agent-scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"
cd "$SCRIPT_DIR/.."

if [[ "${SNAKT_GATE_MEASURING:-}" != 1 ]]; then
    export SNAKT_GATE_ARTIFACTS
    SNAKT_GATE_ARTIFACTS="$(mktemp -d "${SNAKT_GATE_ARTIFACTS:-${TMPDIR:-/tmp}}/snakt-gate.XXXXXX")"
    echo "Gate artifacts: $SNAKT_GATE_ARTIFACTS" >&2
    export SNAKT_GATE_MEASURING=1
    exec python3 "$SCRIPT_DIR/gate_measure.py" gate.total bash "$SCRIPT_DIR/gate-test.sh" "$@"
fi
export SNAKT_VERIFICATION_TIMINGS="${SNAKT_VERIFICATION_TIMINGS:-$SNAKT_GATE_ARTIFACTS/declarations}"
measure=(python3 "$SCRIPT_DIR/gate_measure.py")
"${measure[@]}" gate.testdata ./agent-scripts/check-testdata.sh

# Fixture conversion and the handwritten unit/small integration tiers run here.
# Fixture verification is selected by testData changes; check-all covers every tier.
base="$(git merge-base HEAD origin/HEAD)"
# A substitution, not <( ), so that a failing git stops the gate.
stems="$(git diff --name-only --diff-filter=d "$base" HEAD -- formver.compiler-plugin/testData/diagnostics \
    | sed -E 's#.*/##; s#\..*##' | tr '-' '_' | sort -u)"
verify=()
for stem in $stems; do
    verify+=(--tests "*.test$(printf '%s' "${stem:0:1}" | tr '[:lower:]' '[:upper:]')${stem:1}")
done
if [[ "${#verify[@]}" -gt 0 ]]; then
    store="$(verification_cache_store)"
    if [[ "$store" != off ]]; then
        "${measure[@]}" gate.cache-prune python3 "$SCRIPT_DIR/verification_cache.py" prune "$store"
        verify+=(-Pformver.verificationCacheDirectory="$store"
                 -Pformver.verificationColdSample="$(git rev-parse HEAD)"
                 -Pformver.verificationCacheReport="$SNAKT_GATE_ARTIFACTS/cache")
    fi
    verify=(:formver.compiler-plugin:test "${verify[@]}")
fi
"${measure[@]}" gate.gradle ./gradlew --no-daemon detekt apiCheck :formver.compiler-plugin:locality:test \
    :formver.compiler-plugin:core:unitTest :formver.compiler-plugin:uniqueness:unitTest \
    :formver.compiler-plugin:unitTest :formver.compiler-plugin:integrationTest \
    :formver.compiler-plugin:untilConversion :formver.test-runner:integrationTest "${verify[@]+"${verify[@]}"}"
