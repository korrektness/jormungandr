#!/usr/bin/env bash
# Usage: reproduce-jvm.sh [kotlin-stdlib.jar]  (default: the installed kotlinc's stdlib)
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
scratch=$(mktemp -d /tmp/bot-run.XXXXXXXXXX)
kotlinc -version 2>&1
stdlib=${1:-$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar}
kotlinc "$here/Reproduce.kt" -d "$scratch/repro.jar" 2>&1 | { grep -v '^warning' || true; }
java -Xmx3g -cp "$scratch/repro.jar:$stdlib" ReproduceKt
