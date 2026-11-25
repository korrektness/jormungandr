#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

state="$PWD/build/production-kotlin-inputs"
mkdir -p "$state"
run=$(mktemp -d "$state/run.XXXXXXXXXX")
printf 'Kotlin input evidence: %s\n' "$run"

gradle=(./gradlew --no-daemon --no-configuration-cache --no-parallel --max-workers=1
    --console=plain -Pkotlin.compiler.execution.strategy=in-process)
env -u FORMVER_INPUTS_DIR "${gradle[@]}" "-PformverInputBootstrapDir=$run" prepareProductionKotlinInputs \
    2>&1 | tee "$run/bootstrap.log"
FORMVER_INPUTS_DIR="$run" "${gradle[@]}" --no-build-cache captureProductionKotlinInputs \
    2>&1 | tee "$run/capture.log"
python3 agent-scripts/check_kotlin_inputs.py "$run" "$PWD" --census-inputs "$run/census-inputs.tsv" > "$run/reconciled.json.tmp"
mv "$run/reconciled.json.tmp" "$run/reconciled.json"
printf 'Reconciled Kotlin inputs: %s\n' "$run/reconciled.json"
env -u FORMVER_INPUTS_DIR "${gradle[@]}" :formver.compiler-plugin:cli:sourceCensus \
    "-PformverCensusInputs=$run/census-inputs.tsv" "-PformverCensusOutput=$run/census.tsv" \
    2>&1 | tee "$run/census.log"
printf 'Kotlin source census: %s\n' "$run/census.tsv"
