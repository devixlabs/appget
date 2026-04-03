#!/bin/bash
set -euo pipefail

GRADLE_VERSION="9.3.1"

if [ ! -f "gradlew" ]; then
    echo "Initializing Gradle wrapper with version ${GRADLE_VERSION}..."
    gradle wrapper --gradle-version="${GRADLE_VERSION}"
    chmod +x gradlew
fi
