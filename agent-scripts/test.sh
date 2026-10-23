#!/usr/bin/env bash
# test.sh — Drive the formver test suite. --help for usage.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=agent-scripts/lib.sh
source "$SCRIPT_DIR/lib.sh"
cd "$SCRIPT_DIR/.."

usage() {
    cat <<'EOF'
Usage:
  ./agent-scripts/test.sh [pattern]                  # conversion only — the fast loop, default
  ./agent-scripts/test.sh --verify [pattern]         # full pipeline
  ./agent-scripts/test.sh --verify-changed [pattern] # verify only where conversion output changed
  ./agent-scripts/test.sh --update-goldens [pattern] # regenerate goldens, then report what changed
  ./agent-scripts/test.sh --update-goldens --record-outcomes <pattern>
                                                     # also let verification outcomes change
  ./agent-scripts/test.sh --vacuity [pattern]        # full pipeline, probing each verified method
                                                     # with refute(false) at entry and exit

--timings <directory> writes per-declaration verification TSV reports outside goldens.
--fresh-jvm runs in a new test JVM rather than the one kept from the last run.
--stop stops this checkout's test JVMs and exits.
SNAKT_TEST_RUNNER=off runs Gradle's test tasks instead of the kept JVM.
--cold disables verification cache reads and writes.
--refresh-cache verifies cached programs and compares their outcomes.
SNAKT_VERIFICATION_CACHE selects the store directory; 'off' disables caching.

--vacuity lists every probe that failed in a declaration that otherwise
verifies: the verifier's facts there are contradictory.

A pattern can be given as the testData file is named (assign_local), as the
path to it, or as the generated test method (testAssign_local).
EOF
}

MODE=conversion
RECORD_OUTCOMES=0
FRESH_JVM=0
STOP=0
COLD=0
REFRESH=0
set_mode() {
    if [[ "$MODE" != conversion && "$MODE" != "$1" ]]; then
        echo "--verify, --verify-changed, --update-goldens and --vacuity select different runs; pass one." >&2
        exit 1
    fi
    MODE="$1"
}

while [[ "${1:-}" == -* ]]; do
    case "$1" in
        --cold) COLD=1 ;;
        --refresh-cache) REFRESH=1 ;;
        --timings)
            if [[ -z "${2:-}" || "$2" == -* ]]; then
                echo "Missing directory for --timings" >&2
                exit 1
            fi
            export SNAKT_VERIFICATION_TIMINGS="$2"
            shift
            ;;
        --verify) set_mode verify ;;
        --verify-changed) set_mode verify-changed ;;
        --update-goldens) set_mode update ;;
        --vacuity) set_mode vacuity ;;
        --record-outcomes) RECORD_OUTCOMES=1 ;;
        --fresh-jvm) FRESH_JVM=1 ;;
        --stop) STOP=1 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown flag: $1" >&2; echo >&2; usage >&2; exit 1 ;;
    esac
    shift
done

PATTERN="${1:-}"
if [[ $# -gt 1 ]]; then
    echo "Only one pattern is accepted; got: $*" >&2
    exit 1
fi

if [[ "$RECORD_OUTCOMES" -eq 1 && ( "$MODE" != update || -z "$PATTERN" ) ]]; then
    echo "--record-outcomes needs --update-goldens and a pattern." >&2
    exit 1
fi

case "$MODE" in
    conversion) COMPILER_TASK_NAME=untilConversion ;;
    verify-changed) COMPILER_TASK_NAME=update ;;
    *) COMPILER_TASK_NAME=test ;;
esac
COMPILER_TASK=:formver.compiler-plugin:$COMPILER_TASK_NAME
LOCALITY_TASK=:formver.compiler-plugin:locality:test
# Where each task's RunnerSpec task writes the persistent runner's spec and client.
COMPILER_RUNNER=formver.compiler-plugin/build/test-runner/$COMPILER_TASK_NAME
LOCALITY_RUNNER=formver.compiler-plugin/locality/build/test-runner/test

case "${SNAKT_TEST_RUNNER:-on}" in
    on) USE_RUNNER=1 ;;
    off) USE_RUNNER=0 ;;
    *) echo "SNAKT_TEST_RUNNER is '$SNAKT_TEST_RUNNER'; it takes on or off." >&2; exit 1 ;;
esac

if [[ "$STOP" -eq 1 ]]; then
    if [[ ! -x "$LOCALITY_RUNNER/client" ]]; then
        ./gradlew "$LOCALITY_TASK"RunnerSpec --daemon -q
    fi
    exec "$LOCALITY_RUNNER/client" stop --working-dir "$PWD"
fi

# --daemon: a Gradle daemon serves the next run too, and exits after idling for
# Gradle's idle timeout, three hours by default.
args=(--daemon -q)
if [[ "$MODE" == update ]]; then
    args+=(-Pkotlin.test.update.test.data=true)
fi
if [[ "$RECORD_OUTCOMES" -eq 1 ]]; then
    args+=(-Pformver.recordOutcomes=true)
fi
VACUITY_DIR="$PWD/formver.compiler-plugin/build/vacuity-probes"
if [[ "$MODE" == vacuity ]]; then
    rm -rf "$VACUITY_DIR"
    args+=(-Pformver.vacuityReport="$VACUITY_DIR")
fi
# Gradle's test tasks and the runner both take Gradle's --tests patterns.
test_filter=()
if [[ -n "$PATTERN" ]]; then
    test_filter+=(--tests "*$(gradle_filter "$PATTERN")*")
fi
runner_args=("${test_filter[@]+"${test_filter[@]}"}")
if [[ "$FRESH_JVM" -eq 1 ]]; then
    runner_args+=(--fresh-jvm)
fi

if [[ "$COLD" -eq 1 && "$REFRESH" -eq 1 ]]; then
    echo "Both --cold and --refresh-cache were selected." >&2
    exit 1
fi
CACHE_REPORT=""
if [[ "$MODE" != conversion ]]; then
    CACHE_STORE="$(verification_cache_store)"
    if [[ "$COLD" -eq 0 && "$CACHE_STORE" != off ]]; then
        python3 "$SCRIPT_DIR/verification_cache.py" prune "$CACHE_STORE"
        CACHE_REPORT="$(mktemp -d)"
        trap 'rm -rf "$CACHE_REPORT"' EXIT
        args+=(-Pformver.verificationCacheDirectory="$CACHE_STORE"
               -Pformver.verificationCacheReport="$CACHE_REPORT")
        if [[ "$REFRESH" -eq 1 ]]; then
            args+=(-Pformver.verificationCacheRefresh=true)
        fi
    fi
fi

# DumpAssertionDiffExtension only fires when this is set (see docs/agents-dev.md).
DUMP_DIR="$(dump_dir_default)"
mkdir -p "$DUMP_DIR"
rm -f "$DUMP_DIR"/test-assertion-dump-*.txt "$DUMP_DIR"/test-assertion-diff-*.txt
export SNAKT_TEST_DUMP_DIR="$DUMP_DIR"

MARKER="$(mktemp)"

matched=0
overall_status=0
total_tests=0
total_rewritten=0
total_failed=0
total_skipped=0
total_unreadable=0
# Modules that ran but whose count could not be established.
no_results=()
unreadable_results=()

# $1 is the Gradle test task, $2 the directory holding its runner spec and client.
run_task() {
    if [[ "$USE_RUNNER" -eq 1 ]]; then
        if TASK_OUT="$("$2/client" run --spec "$2/spec.properties" "${runner_args[@]+"${runner_args[@]}"}" 2>&1)"; then
            TASK_STATUS=0
        else
            TASK_STATUS=$?
        fi
        return
    fi
    # --rerun: an UP-TO-DATE task is green without executing anything.
    if TASK_OUT="$(./gradlew "$1" --rerun "${args[@]}" "${test_filter[@]+"${test_filter[@]}"}" 2>&1)"; then
        TASK_STATUS=0
    else
        TASK_STATUS=$?
    fi
}

report_compiler_failure() {
    local failure_info
    failure_info="$(report_first_xml_failure "$MARKER" || true)"
    if [[ -z "$failure_info" ]]; then
        # The task died before any test ran; Gradle's error output above says why.
        return
    fi
    if [[ "$(head -1 <<<"$failure_info")" == golden ]]; then
        echo
        echo "FAILED. Recovering the assertion diff:"
        render_dump_diffs "$DUMP_DIR" || true
    else
        echo
        echo "FAILED. Not a golden-file assertion — no diff to recover. From the test run:"
        echo
        tail -n +2 <<<"$failure_info"
    fi
}

report_locality_failure() {
    echo
    echo "FAILED. Locality has no test-fixtures on its classpath, so it has no"
    echo "dump to recover. From the test results:"
    echo
    local results=()
    if [[ -d "$LOCALITY_RESULTS_DIR" ]]; then
        mapfile -t results < <(find "$LOCALITY_RESULTS_DIR" -name 'TEST-*.xml' -newer "$MARKER" | sort)
    fi
    if [[ "${#results[@]}" -eq 0 ]]; then
        echo "The locality tests left no results."
        return
    fi
    python3 "$SCRIPT_DIR/junit_first_failure.py" "${results[@]}" | tail -n +2 || true
}

# Returns 1 when the module's counts could not be established.
tally() {
    local module="$1" counts status ran rewritten failed skipped unreadable
    counts="$(count_xml_results "$2" "$MARKER")" && status=0 || status=$?
    case "$status" in
        1) no_results+=("$module"); return 1 ;;
        2) unreadable_results+=("$module"); return 1 ;;
    esac
    read -r ran rewritten failed skipped unreadable <<<"$counts"
    total_tests=$((total_tests + ran))
    total_rewritten=$((total_rewritten + rewritten))
    total_failed=$((total_failed + failed))
    total_skipped=$((total_skipped + skipped))
    total_unreadable=$((total_unreadable + unreadable))
}

# Gradle's closing advice is about Gradle, not about the failure.
print_task_output() {
    echo "$TASK_OUT" | grep -v '^\* Try:\|^> Run with \|^> Get more help ' || true
}

# In --update-goldens mode a matching test is expected to fail: assertEqualsToFile
# writes the golden and then fails. Only "no tests found" means anything there,
# and a failed task that left no results, which is a build failure.
run_module() {
    local module="$1" task="$2" runner="$3" results_dir="$4" on_failure="$5"
    run_task "$task" "$runner"
    if [[ -n "$PATTERN" && "$TASK_OUT" == *"No tests found for given includes"* ]]; then
        return
    fi
    matched=1
    local tallied=0
    tally "$module" "$results_dir" || tallied=1
    if [[ "$MODE" == update ]]; then
        if [[ "$TASK_STATUS" -ne 0 && "$tallied" -eq 1 ]]; then
            overall_status=1
            echo
            print_task_output
        fi
        return
    fi
    if [[ "$TASK_STATUS" -eq 0 ]]; then
        return
    fi
    overall_status=1
    print_task_output
    "$on_failure"
}

# The runner's server runs what Gradle has compiled, so Gradle compiles first and
# writes the specs, with the properties that select this run.
if [[ "$USE_RUNNER" -eq 1 ]]; then
    spec_tasks=("$COMPILER_TASK"RunnerSpec)
    if [[ "$MODE" != vacuity ]]; then
        spec_tasks+=("$LOCALITY_TASK"RunnerSpec)
    fi
    if ! TASK_OUT="$(./gradlew "${spec_tasks[@]}" "${args[@]}" 2>&1)"; then
        print_task_output
        rm -f "$MARKER"
        exit 1
    fi
fi

run_module compiler "$COMPILER_TASK" "$COMPILER_RUNNER" "$COMPILER_RESULTS_DIR" report_compiler_failure
if [[ "$MODE" != vacuity ]]; then
    run_module locality "$LOCALITY_TASK" "$LOCALITY_RUNNER" "$LOCALITY_RESULTS_DIR" report_locality_failure
fi
# In update mode these are the tests that wrote nothing, such as those refusing
# to change a verification outcome.
UNWRITTEN=""
if [[ "$MODE" == update ]]; then
    UNWRITTEN="$(report_other_xml_failures "$MARKER")" && unwritten_status=0 || unwritten_status=$?
    if [[ "$unwritten_status" -gt 1 ]]; then
        UNWRITTEN="The test results could not be read, so tests that wrote nothing are not listed."
    fi
fi
rm -f "$MARKER"

if [[ "$matched" -eq 0 ]]; then
    echo "No test matches '$PATTERN'."
    exit 1
fi

summary() {
    local failed=$((total_rewritten + total_failed)) line
    local passed=$((total_tests - failed - total_skipped))
    if [[ "$total_tests" -gt 0 ]]; then
        if [[ "$MODE" == update ]]; then
            line="Ran $total_tests tests, $total_rewritten golden(s) rewritten"
            if [[ "$total_failed" -gt 0 ]]; then
                line+=", $total_failed failed for other reasons"
            fi
        else
            line="Ran $total_tests tests, $passed passed, $failed failed"
        fi
        if [[ "$total_skipped" -gt 0 ]]; then
            line+=", $total_skipped skipped"
        fi
        echo "$line."
    fi
    local module
    for module in "${no_results[@]+"${no_results[@]}"}"; do
        echo "The $module module produced no test results; see its output above."
    done
    for module in "${unreadable_results[@]+"${unreadable_results[@]}"}"; do
        echo "The $module module's test results could not be read; it is not counted above."
    done
    if [[ "$total_unreadable" -gt 0 ]]; then
        echo "$total_unreadable result file(s) were unparseable, so the count is a lower bound."
    fi
}

echo
summary
if [[ "$MODE" != conversion ]]; then
    if [[ -n "$CACHE_REPORT" ]]; then
        python3 "$SCRIPT_DIR/verification_cache.py" summary "$CACHE_REPORT"
    else
        echo "Verification: cache off"
    fi
fi

if [[ "$MODE" == vacuity ]]; then
    if compgen -G "$VACUITY_DIR/*.tsv" >/dev/null; then
        echo
        echo "=== vacuous contexts (test file, declaration, method, probe) ==="
        sort "$VACUITY_DIR"/*.tsv
        exit 1
    fi
    if [[ "$overall_status" -eq 0 ]]; then
        echo "No probe found a contradictory context."
    fi
fi

if [[ "$MODE" != update || "$overall_status" -ne 0 ]]; then
    exit "$overall_status"
fi

# --no-renames, so a rename arrives as a delete plus an add rather than as one
# "old -> new" entry that is not a readable path. Paths gone from disk are
# dropped for the same reason.
changed() {
    local file
    while IFS= read -r file; do
        if [[ -f "$file" ]]; then
            printf '%s\n' "$file"
        fi
    done < <(git status --porcelain --no-renames -- "$@" | sed -E 's/^.{3}//')
}

# What the golden now says, not just that it changed: going to look at the file
# is the step that gets skipped. Diffs are against HEAD so staged and unstaged
# changes are both covered. An untracked golden has no diff, so its whole body
# is the new content.
show() {
    local cap="$1" file="$2" body
    if git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
        # Drop git's header lines; the path is printed above.
        body="$(git diff HEAD --no-prefix -- "$file" | sed -n '/^@@/,$p')"
    else
        body="$(sed 's/^/+/' "$file")"
    fi
    if [[ "$(wc -l <<<"$body")" -gt "$cap" ]]; then
        head -"$cap" <<<"$body" | sed 's/^/    /'
        echo "    ... truncated, read $file"
    else
        sed 's/^/    /' <<<"$body"
    fi
}

# $2 caps how many lines of each file to show; 0 lists paths only.
report() {
    local header="$1" cap="$2"; shift 2
    local files file
    files="$(changed "$@")"
    if [[ -z "$files" ]]; then
        return 0
    fi
    echo
    echo "$header"
    while IFS= read -r file; do
        echo "  $file"
        if [[ "$cap" -gt 0 ]]; then
            show "$cap" "$file"
        fi
    done <<<"$files"
}

# One line per file: how many lines the golden gained and lost.
report_stat() {
    local header="$1"; shift
    local files file added removed
    files="$(changed "$@")"
    if [[ -z "$files" ]]; then
        return 0
    fi
    echo
    echo "$header"
    while IFS= read -r file; do
        if git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
            read -r added removed _ < <(git diff HEAD --numstat -- "$file")
        else
            added="$(wc -l <"$file")"
            removed=0
        fi
        echo "  $file +$added -$removed"
    done <<<"$files"
}

echo
echo "=== golden changes ==="
# Verification diagnostics are short and are what gets recorded by accident,
# so they are shown whole.
report "verification produced diagnostics for these; confirm that is intended:" 40 \
    '*.viper.diag.txt'
# Conversion diagnostics are what a reviewer reads; the Viper text is bulk.
report "conversion diagnostics changed:" 100000 \
    '*.fir.diag.txt'
report_stat "Viper text changed (lines added/removed):" '*.viper.txt'
# Under --record-outcomes, outcome changes are among these, so nothing is cut.
if [[ "$RECORD_OUTCOMES" -eq 1 ]]; then
    marker_cap=100000
else
    marker_cap=40
fi
report "diagnostic markers changed:" "$marker_cap" \
    'formver.compiler-plugin/testData/*.kt' \
    'formver.compiler-plugin/locality/testData/*.kt'
report "regenerated test registration; commit as-is:" 0 \
    '*TestGenerated.java'

echo
echo "=== check-testdata.sh ==="
"$SCRIPT_DIR/check-testdata.sh" || true

cat <<'EOF'

What is above is what these tests now assert: read it and confirm it is what
you meant.
EOF

if [[ -n "$UNWRITTEN" ]]; then
    echo
    echo "=== not regenerated ==="
    echo "$UNWRITTEN"
    exit 1
fi
