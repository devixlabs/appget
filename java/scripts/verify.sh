#!/bin/bash
set -euo pipefail

if [ ! -x ./generated-server/verify.sh ]; then
    echo "✗ generated-server/verify.sh missing — run 'make run-server' (or 'make all') first" >&2
    exit 1
fi

echo "✓ Running API integration tests..."
./generated-server/verify.sh

echo "✓ Running HTTP endpoint tests..."
python3 tests/run-http-tests.py tests/http-tests.yaml

echo "✓ Running live HTML structural diff tests (PageRenderer vs golden)..."
./gradlew testLive

echo "✓ Running HTML navigation checks (links resolve to server routes)..."
scripts/check-html-nav.sh
