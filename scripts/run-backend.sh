#!/usr/bin/env bash
#
# Builds (if needed) and runs the backend on :8080 in SEED mode, which is what
# the e2e suite talks to. No provider key required, no spend.
#
# Usage:  ./scripts/run-backend.sh [--no-build]
#
# The jar is rebuilt by default. Skipping that is how a stale jar silently gets
# tested, which is a confusing failure to debug; the incremental build is quick.
set -euo pipefail

cd "$(dirname "$0")/../backend"

# `java_home -v 21` means "21 or newer" on macOS, so check Homebrew paths first.
if [ -z "${JAVA_HOME:-}" ] || ! "${JAVA_HOME}/bin/javac" -version 2>&1 | grep -q ' 21\.'; then
  for candidate in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 \
                   /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home; do
    if [ -x "$candidate/bin/javac" ]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

JAR=$(ls target/backend-*.jar 2>/dev/null | head -1 || true)
if [ "${1:-}" != "--no-build" ] || [ -z "$JAR" ]; then
  ./mvnw -B -q -DskipTests package
  JAR=$(ls target/backend-*.jar | head -1)
fi

export APP_CONTENT_MODE="${APP_CONTENT_MODE:-SEED}"
export APP_AUTH_MODE="${APP_AUTH_MODE:-LOCAL_STUB}"
export APP_STORAGE_MODE="${APP_STORAGE_MODE:-LOCAL}"

echo "Starting $JAR (content=$APP_CONTENT_MODE auth=$APP_AUTH_MODE storage=$APP_STORAGE_MODE)"
exec java -jar "$JAR"
