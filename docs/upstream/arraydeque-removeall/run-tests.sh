#!/usr/bin/env bash
# Compile the applied upstream patch and run its complete ArrayDequeTest class.
set -euo pipefail
checkout=$(realpath "$1")
output=$(realpath -m "$2")
junit=$(realpath "$3")
hamcrest=$(realpath "$4")
base=${5:-HEAD}
kotlin_home=${KOTLIN_HOME:-/usr/share/kotlin}
mkdir -p "$output"
stdlib="$kotlin_home/lib/kotlin-stdlib.jar"
test_cp="$kotlin_home/lib/kotlin-test.jar:$kotlin_home/lib/kotlin-test-junit.jar:$junit:$hamcrest"
source="$checkout/libraries/stdlib"
flags=(-Xallow-kotlin-package -Xreturn-value-checker=full "-Xfriend-paths=$stdlib")
kotlinc "$source/src/kotlin/collections/ArrayDeque.kt" \
    "$source/test/collections/ArrayDequeTest.kt" \
    "$source/test/collections/CollectionBehaviors.kt" \
    "$source/test/collections/ComparisonDSL.kt" \
    "$source/test/testUtils.kt" "$source/common/test/testUtils.kt" \
    "$source/jvm/test/testUtilsJVM.kt" \
    -Xmulti-platform \
    "-Xcommon-sources=$source/test/testUtils.kt,$source/common/test/testUtils.kt" \
    "${flags[@]}" -cp "$test_cp" -d "$output/tests.jar"
cp="$output/tests.jar:$stdlib:$test_cp"
java -cp "$cp" org.junit.runner.JUnitCore test.collections.ArrayDequeTest

# Negative sibling: the identical tests must reject the unpatched public API.
git -C "$checkout" show "$base:libraries/stdlib/src/kotlin/collections/ArrayDeque.kt" > "$output/ArrayDeque-baseline.kt"
kotlinc "$output/ArrayDeque-baseline.kt" "${flags[@]}" -d "$output/baseline.jar"
set +e
java -cp "$output/baseline.jar:$cp" org.junit.runner.JUnitCore test.collections.ArrayDequeTest > "$output/negative.log" 2>&1
result=$?
set -e
if [[ $result != 1 ]] || ! grep -q 'bulkFilterContainsThrows(test.collections.ArrayDequeTest)' "$output/negative.log"; then
    printf 'Baseline test result: exit=%s log=%s\n' "$result" "$output/negative.log" >&2
    exit 1
fi
printf 'Unpatched negative sibling rejected; log=%s\n' "$output/negative.log"
