#!/bin/bash
set -euo pipefail

echo "✓ Starting application server..."
cd generated-server
../gradlew bootRun
