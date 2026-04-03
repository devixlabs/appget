#!/bin/bash
set -euo pipefail

echo "✓ Running API integration tests..."
./generated-server/verify.sh

echo "✓ Running HTTP endpoint tests..."
python3 tests/run-http-tests.py tests/http-tests.yaml
