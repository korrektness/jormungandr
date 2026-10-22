#!/usr/bin/env bash
# The gate's checked candidate is HEAD in this checkout.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=agent-scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"
cd "$SCRIPT_DIR/.."

./agent-scripts/check-testdata.sh
./agent-scripts/check-gradle-consumer.sh
store="$(verification_cache_store)"
args=()
if [[ "$store" != off ]]; then
    python3 "$SCRIPT_DIR/verification_cache.py" prune "$store"
    args+=(-Pformver.verificationCacheDirectory="$store"
           -Pformver.verificationColdSample="$(git rev-parse HEAD)")
fi
./gradlew --no-daemon detekt apiCheck :formver.compiler-plugin:locality:test \
    :formver.compiler-plugin:untilConversion :formver.compiler-plugin:test "${args[@]}"
