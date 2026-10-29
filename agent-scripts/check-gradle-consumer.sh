#!/usr/bin/env bash
# Published-plugin end-to-end driver. Fixture layout and commands:
# docs/developing.md#published-plugin-consumers
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
measure=(python3 "$ROOT/agent-scripts/gate_measure.py")
fixtures="$ROOT/formver.gradle-plugin/testData/consumer"

z3="${Z3_EXE:-}"
if [[ -z "$z3" ]]; then
    z3="$(command -v z3 || true)"
fi
if [[ -z "$z3" ]]; then
    z3="$(ls "${GRADLE_USER_HOME:-$HOME/.gradle}"/formver/z3/*/bin/z3 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$z3" || ! -x "$z3" ]]; then
    echo "check-gradle-consumer.sh: no executable Z3 found (Z3_EXE=$z3)" >&2
    exit 2
fi
export Z3_EXE="$z3"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
repo="$work/repo"
consumer="$work/consumer"

# A private repository keeps the shared ~/.m2 untouched.
"${measure[@]}" consumer.publish ./gradlew --no-daemon -Dmaven.repo.local="$repo" \
    :formver.gradle-plugin:publishToMavenLocal \
    :formver.compiler-plugin:publishToMavenLocal \
    :formver.annotations:publishToMavenLocal \
    :formver.common:publishToMavenLocal

prepare_consumer() {
    local scenario="$1" destination="$2"
    cp -R "$fixtures/$scenario" "$destination"
    mkdir -p "$destination/gradle"
    # Use this build's daemon JVM criteria with the repository wrapper.
    cp "$ROOT/gradle/gradle-daemon-jvm.properties" "$destination/gradle/"
}
batch_consumer="$work/production-batch"
prepare_consumer production-batch "$batch_consumer"
python3 "$ROOT/agent-scripts/production_batch_consumer.py" "$ROOT" "$batch_consumer" "$repo" "$work"

prepare_consumer external-specifications-jvm "$consumer"
mkdir -p "$consumer/src/bfs/kotlin"

# The full positive BFS runs only in the initial published-plugin build, whose
# trust report checks its bundled dependencies. Other variants keep the small
# callers in Main.kt. scanSkipLast runs only in the badClaim build.
# Both program fixtures have their diagnostic markup stripped.
bfs_tests="$ROOT/formver.compiler-plugin/testData/programs/algorithms/bfs"
sed -E 's/<!([A-Z_]*!)?>//g' "$bfs_tests/arraydeque_bfs.kt" >"$consumer/src/bfs/kotlin/Bfs.kt"
{
    echo 'import org.jetbrains.kotlin.formver.plugin.*'
    echo
    sed -n '/^fun <!VIPER_TEXT!>scanSkipLast</,/^fun <!VIPER_TEXT!>bfsWithoutVertexBound</p' \
        "$bfs_tests/arraydeque_bfs_negative.kt" | sed '$d'
} | sed -E 's/<!([A-Z_]*!)?>//g' >"$consumer/src/badClaim/kotlin/BadScan.kt"
scan_return="$(grep -n '^    return f$' "$consumer/src/badClaim/kotlin/BadScan.kt" | cut -d: -f1)"
if [[ -z "$scan_return" || "$(wc -l <<<"$scan_return")" -ne 1 ]]; then
    echo "check-gradle-consumer.sh: BadScan.kt does not contain exactly one return of scanSkipLast" >&2
    exit 1
fi

# The consumer is not part of this build, so the repository's own gradle
# invocation does not apply; the wrapper is reused for its Gradle version.
build_consumer() {
    local stage="$1"
    shift
    "${measure[@]}" "consumer.$stage" "$ROOT/gradlew" --no-daemon -p "$consumer" -Dformver.repo="file://$repo" "$@" compileKotlin
}

log="$work/consumer.log"
if ! build_consumer initial -Pbfs >"$log" 2>&1; then
    cat "$log" >&2
    echo "check-gradle-consumer.sh: consumer build failed" >&2
    exit 1
fi
if grep -q "^w: .*\(Main\|Bfs\).kt" "$log"; then
    grep "^w: .*\(Main\|Bfs\).kt" "$log" >&2
    echo "check-gradle-consumer.sh: the consumer's claims did not verify" >&2
    exit 1
fi

classes="$consumer/build/classes/kotlin/main"
for file in MainKt.class BfsKt.class; do
    if [[ ! -f "$classes/$file" ]]; then
        echo "check-gradle-consumer.sh: consumer build produced no $file" >&2
        exit 1
    fi
done
# Spec packages, files and declarations, both the consumer's and the bundled.
spec_names=(-e stand/specs -e stand.specs -e CounterSpecs -e CounterModel
    -e createModel -e createSpec -e twiceModel -e twiceSpec -e formver/external -e IntAbs -e absModel -e absSpecification
    -e VerifiedSpecs -e halfModel -e halfSpec -e BoxModel)
leaked="$(find "$classes" -type f -exec grep -l "${spec_names[@]}" {} + || true)"
if [[ -n "$leaked" ]]; then
    echo "check-gradle-consumer.sh: an external spec reached the compiled output:" >&2
    echo "$leaked" >&2
    exit 1
fi

# A default build shows the trust summary, and the report it points to lists
# what each declaration trusted.
report="$consumer/build/formver/compileKotlin/trust-report.txt"
if ! grep -q "^SnaKt verified .* Details: $report$" "$log"; then
    cat "$log" >&2
    echo "check-gradle-consumer.sh: the build did not show the trust summary" >&2
    exit 1
fi
for entry in "user spec: stand.lib.twice(Int): Int" "bundled spec: kotlin.math.abs(Int): Int" \
    "bundled spec: kotlin.collections.ArrayDeque.ArrayDeque(): ArrayDeque<E>" \
    "bundled spec: kotlin.collections.ArrayDeque.addLast(E): Unit" \
    "bundled spec: kotlin.collections.ArrayDeque.removeFirst(): E" \
    "bundled spec: kotlin.collections.ArrayDeque.isEmpty(): Boolean" \
    "rebinds verified: stand.vlib.half(Int): Int" "rebinds verified: stand.vlib.Box"; do
    if ! grep -qF "    $entry" "$report"; then
        cat "$report" >&2
        echo "check-gradle-consumer.sh: the trust report does not list $entry" >&2
        exit 1
    fi
done

# A spec binding to a declaration that the library verified is a warning on
# the model, and the binding still applies.
for warning in "5:.*stand.vlib.half," "14:.*stand.vlib.Box," "16:.*stand.vlib.Box.size,"; do
    line="${warning%%:*}"
    if ! grep -q "^w: .*VerifiedSpecs.kt:$warning" "$log"; then
        cat "$log" >&2
        echo "check-gradle-consumer.sh: the binding on VerifiedSpecs.kt line $line to a verified declaration was not reported" >&2
        exit 1
    fi
done

# The spec was analysed: the reference resolves and is then rejected, rather
# than failing to resolve.
bad_log="$work/consumer-bad-reference.log"
if build_consumer bad-reference -PbadReference >"$bad_log" 2>&1; then
    echo "check-gradle-consumer.sh: a reference to the external spec compiled" >&2
    exit 1
fi
if ! grep -q "^e: .*BadReference.kt.*'stand.specs.twiceModel'" "$bad_log"; then
    cat "$bad_log" >&2
    echo "check-gradle-consumer.sh: the consumer with a spec reference failed for another reason" >&2
    exit 1
fi

# Each binding to a missing library declaration is an error on its model.
binding_log="$work/consumer-bad-binding.log"
if build_consumer bad-binding -PbadBinding >"$binding_log" 2>&1; then
    echo "check-gradle-consumer.sh: a spec binding to a missing library declaration compiled" >&2
    exit 1
fi
for line in 5 8; do
    if ! grep -q "^e: .*BadBinding.kt:$line:" "$binding_log"; then
        cat "$binding_log" >&2
        echo "check-gradle-consumer.sh: the binding on BadBinding.kt line $line was not rejected" >&2
        exit 1
    fi
done

# A contradictory spec is an error on its model, though no code calls it.
vacuous_log="$work/consumer-vacuous-spec.log"
if build_consumer vacuous-spec -PvacuousSpec >"$vacuous_log" 2>&1; then
    echo "check-gradle-consumer.sh: a contradictory spec compiled" >&2
    exit 1
fi
if ! grep -q "^e: .*VacuousSpecs.kt:5:.*stand.specs.vacuous.thriceModel is contradictory" "$vacuous_log"; then
    cat "$vacuous_log" >&2
    echo "check-gradle-consumer.sh: the contradictory spec failed for another reason" >&2
    exit 1
fi

# Each false claim fails verification on its own line: a stand-in library
# postcondition, the bundled precondition of abs, and a stand-in library
# precondition.
claim_log="$work/consumer-bad-claim.log"
if build_consumer bad-claim -PbadClaim >"$claim_log" 2>&1; then
    echo "check-gradle-consumer.sh: the consumer with false claims compiled" >&2
    exit 1
fi
for line in 6 9 11; do
    if ! grep -q "^e: .*BadClaim.kt:$line:" "$claim_log"; then
        cat "$claim_log" >&2
        echo "check-gradle-consumer.sh: the false claim on BadClaim.kt line $line verified" >&2
        exit 1
    fi
done
# The postcondition of the successor scan fails where the scan returns.
if ! grep -q "^e: .*BadScan.kt:$scan_return:" "$claim_log"; then
    cat "$claim_log" >&2
    echo "check-gradle-consumer.sh: the false closure claim on BadScan.kt line $scan_return verified" >&2
    exit 1
fi
# Warnings are reported next to the errors.
if ! grep -q "^w: .*BadClaim.kt:21:" "$claim_log"; then
    cat "$claim_log" >&2
    echo "check-gradle-consumer.sh: a failing build dropped its warnings" >&2
    exit 1
fi

kmp="$work/kmp"
prepare_consumer bundled-specifications-multiplatform "$kmp"
kmp_log="$work/kmp.log"
if ! "${measure[@]}" consumer.multiplatform "$ROOT/gradlew" --no-daemon -p "$kmp" -Dformver.repo="file://$repo" \
    compileKotlinJvm compileTestKotlinJvm >"$kmp_log" 2>&1; then
    cat "$kmp_log" >&2
    echo "check-gradle-consumer.sh: multiplatform consumer build failed" >&2
    exit 1
fi
if grep -q "^w: .*\(common\|jvm\|jvmTest\).kt" "$kmp_log"; then
    grep "^w: " "$kmp_log" >&2
    echo "check-gradle-consumer.sh: the multiplatform consumer's claims did not verify" >&2
    exit 1
fi
kmp_leaked="$(find "$kmp/build/classes" -type f -exec grep -l "${spec_names[@]}" {} + || true)"
if [[ -n "$kmp_leaked" ]]; then
    echo "check-gradle-consumer.sh: a bundled spec reached the multiplatform compiled output:" >&2
    echo "$kmp_leaked" >&2
    exit 1
fi

# Editing a spec re-verifies its callers: the small consumer builds clean, then
# only the spec of twice changes so that Main.kt's claim about it fails.
# Caller sources and source-set selection stay unchanged across these builds.
rebuild_log="$work/consumer-rebuild.log"
if ! build_consumer rebuild >"$rebuild_log" 2>&1; then
    cat "$rebuild_log" >&2
    echo "check-gradle-consumer.sh: the consumer did not build again" >&2
    exit 1
fi
if grep -q "^w: .*Main.kt" "$rebuild_log"; then
    cat "$rebuild_log" >&2
    echo "check-gradle-consumer.sh: the rebuilt consumer's claims did not verify" >&2
    exit 1
fi
sed -i 's/result -> result == x \* 2 }/result -> result == x * 2 + 1 }/' "$consumer/specs/CounterSpecs.kt"
if ! grep -q 'result == x \* 2 + 1' "$consumer/specs/CounterSpecs.kt"; then
    echo "check-gradle-consumer.sh: the spec edit did not apply" >&2
    exit 1
fi
edited_log="$work/consumer-edited-spec.log"
if build_consumer edited-spec >"$edited_log" 2>&1; then
    echo "check-gradle-consumer.sh: the consumer stayed verified after its spec changed" >&2
    exit 1
fi
if ! grep -q "^e: .*Main.kt:29:" "$edited_log"; then
    cat "$edited_log" >&2
    echo "check-gradle-consumer.sh: the consumer with an edited spec failed for another reason" >&2
    exit 1
fi

echo "check-gradle-consumer.sh: consumer build passed"
