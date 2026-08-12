#!/usr/bin/env bash
#
# Cheap, dependency-free secret scan for tracked files. It is a safety net, not
# a replacement for a real scanner - CI additionally runs gitleaks.
#
# Written for bash 3.2 so it works on a stock macOS shell.
#
# Usage:  ./scripts/secret-scan.sh [--staged]
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "${1:-}" = "--staged" ]; then
  FILE_LIST=$(git diff --cached --name-only --diff-filter=ACM)
else
  FILE_LIST=$(git ls-files)
fi

if [ -z "$FILE_LIST" ]; then
  echo "secret-scan: nothing to scan"
  exit 0
fi

# Files that legitimately contain the *names* of secrets.
EXCLUDE_RE='^(\.env\.example|docs/.*\.md|scripts/secret-scan\.sh|README\.md|DECISIONS\.md)$'

PATTERNS='sk-[A-Za-z0-9_-]{20,}
AKIA[0-9A-Z]{16}
ASIA[0-9A-Z]{16}
aws_secret_access_key[[:space:]]*=[[:space:]]*[A-Za-z0-9/+=]{20,}
-----BEGIN[[:space:]].*PRIVATE KEY-----
xox[baprs]-[A-Za-z0-9-]{10,}
ghp_[A-Za-z0-9]{30,}'

FOUND=0
COUNT=0

while IFS= read -r file; do
  [ -f "$file" ] || continue
  if echo "$file" | grep -qE "$EXCLUDE_RE"; then continue; fi
  # Skip binaries.
  grep -Iq . "$file" 2>/dev/null || continue
  COUNT=$((COUNT + 1))

  while IFS= read -r pattern; do
    [ -n "$pattern" ] || continue
    if matches=$(grep -nE "$pattern" "$file" 2>/dev/null); then
      echo "POSSIBLE SECRET in $file"
      # Print line numbers only - never echo the matched value.
      echo "$matches" | cut -d: -f1 | sed 's/^/  line /'
      FOUND=1
    fi
  done <<EOF
$PATTERNS
EOF
done <<EOF
$FILE_LIST
EOF

# A committed .env is always a mistake.
if git ls-files --error-unmatch .env >/dev/null 2>&1; then
  echo "POSSIBLE SECRET: .env is tracked by git"
  FOUND=1
fi

if [ "$FOUND" -eq 1 ]; then
  echo
  echo "secret-scan: FAILED. Remove the value, rotate it, and use an environment"
  echo "variable or AWS Secrets Manager instead."
  exit 1
fi

echo "secret-scan: clean ($COUNT text files)"
