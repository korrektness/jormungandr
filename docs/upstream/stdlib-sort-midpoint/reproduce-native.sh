#!/usr/bin/env bash
# Usage: reproduce-native.sh <kotlin-native-prebuilt-dir> <konan-data-dir> [dependency-dir...]
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
native=$(realpath "$1"); data=$(realpath -m "$2"); shift 2
mkdir -p "$data/dependencies"
for dep in "$@"; do ln -sfn "$(realpath "$dep")" "$data/dependencies/$(basename "$dep")"; done
export KONAN_DATA_DIR=$data
out=$(mktemp -d /tmp/bot-run.XXXXXXXXXX)
"$native/bin/kotlinc-native" "$here/ReproduceNative.kt" -o "$out/repro" 2>&1 | { grep -v '^warning' || true; }
"$out/repro.kexe"
