#!/bin/bash
set -euo pipefail

echo "✓ Starting application server (runs in foreground)..."
echo "  → Listening on http://localhost:8080"
echo "  → Watch for Spring's \"Started Application in N seconds\" line — that means it's up."
echo "  → Press Ctrl-C to stop. (Run 'make verify' from a second terminal.)"
echo

cd generated-server
# --console=plain disables Gradle's live progress bar. bootRun is a long-running
# task that never "finishes", so the bar would otherwise park at "80% EXECUTING
# :bootRun" forever and read as a stuck build. Plain console streams the real
# Spring Boot startup logs instead, so server-up state is obvious.
exec ../gradlew bootRun --console=plain
