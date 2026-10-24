#!/usr/bin/env bash
# test.sh — the nightly run (docs/agents-dev.md, "Landing and nightly").
set -euo pipefail
cd "$(dirname "$0")"
exec ./agent-scripts/check-all.sh --rerun
