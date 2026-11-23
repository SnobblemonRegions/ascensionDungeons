#!/usr/bin/env bash
#
# Ascension Dungeons has no build gate yet. This runs the one check that exists, the same one
# .github/workflows/changelog.yml runs. With a jar path it also checks the jar carries the changelog byte for byte:
#   bash validation/ci_local.sh build/libs/AscensionDungeons-<version>.jar
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"
if command -v python3 >/dev/null 2>&1; then py=python3; elif command -v python >/dev/null 2>&1; then py=python; else echo "ci_local: no python interpreter on PATH" >&2; exit 1; fi
"$py" validation/validate_changelog.py "$@"
