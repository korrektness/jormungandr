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
"${measure[@]}" gate.consumer ./agent-scripts/check-gradle-consumer.sh
store="$(verification_cache_store)"
args=()
if [[ "$store" != off ]]; then
    "${measure[@]}" gate.cache-prune python3 "$SCRIPT_DIR/verification_cache.py" prune "$store"
    args+=(-Pformver.verificationCacheDirectory="$store"
           -Pformver.verificationColdSample="$(git rev-parse HEAD)"
           -Pformver.verificationCacheReport="$SNAKT_GATE_ARTIFACTS/cache")
fi
"${measure[@]}" gate.gradle ./gradlew --no-daemon detekt apiCheck :formver.compiler-plugin:locality:test \
    :formver.compiler-plugin:untilConversion :formver.compiler-plugin:test \
    :formver.test-runner:test "${args[@]}"
