#!/usr/bin/env bash
# Usage: verify-patched-jvm.sh <UArraySorting.kt>
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
scratch=$(mktemp -d /tmp/bot-run.XXXXXXXXXX)
sed -e 's/^package kotlin.collections/package copied/' -e 's/^private fun/fun/' "$1" > "$scratch/Copied.kt"
kotlinc "$scratch/Copied.kt" "$here/ReproduceFixed.kt" -opt-in=kotlin.ExperimentalUnsignedTypes -d "$scratch/out.jar" 2>&1 | { grep -v '^warning' || true; }
stdlib=$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar
java -Xmx3g -cp "$scratch/out.jar:$stdlib" ReproduceFixedKt
