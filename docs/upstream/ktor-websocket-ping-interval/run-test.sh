#!/usr/bin/env bash
# Usage: run-test.sh <classpath: Ktor 3.6.0 JVM jars, their dependencies, JUnit 4, Hamcrest> [patched Ktor checkout]
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
cp=$1
out=$(mktemp -d "${TMPDIR:-/tmp}/bot-run.XXXXXXXXXX")
kotlin_lib=$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib
test_cp="$kotlin_lib/kotlin-test.jar:$kotlin_lib/kotlin-test-junit.jar"
if [[ $# -ge 2 ]]; then
    friend=$(tr ':' '\n' <<<"$cp" | grep '/ktor-client-core-jvm-[^/]*\.jar$') || { echo "no ktor-client-core-jvm jar on the classpath: $cp" >&2; exit 1; }
    kotlinc "$2/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/websocket/WebSockets.kt" -module-name ktor-client-core "-Xfriend-paths=$friend" -cp "$cp" -d "$out/patched" 2>&1 | { grep -v '^warning' || true; }
    cp="$out/patched:$cp"
fi
kotlinc "$here/PingIntervalOverflowTest.kt" -cp "$cp:$test_cp" -d "$out/test" 2>&1 | { grep -v '^warning' || true; }
java -Xmx512m -cp "$out/test:$cp:$kotlin_lib/kotlin-stdlib.jar:$test_cp" org.junit.runner.JUnitCore PingIntervalOverflowTest
