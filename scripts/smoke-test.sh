#!/usr/bin/env bash
#
# Post-deployment smoke test. Reads the stack outputs, then checks that the
# backend is healthy and the frontend is being served.
#
# Usage:  ./scripts/smoke-test.sh [dev|prod]
set -euo pipefail

ENV_NAME="${1:-dev}"
PROJECT="${LISTENSPEAK_PROJECT:-listenspeak}"
REGION="${AWS_REGION:-ca-central-1}"
STACK="${PROJECT}-${ENV_NAME}"

if ! command -v aws >/dev/null 2>&1; then
  echo "smoke-test: the AWS CLI is required" >&2
  exit 1
fi

output() {
  aws cloudformation describe-stacks \
    --stack-name "$STACK" \
    --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" \
    --output text 2>/dev/null
}

API_URL=$(output ApiUrl)
SITE_URL=$(output SiteUrl)

FAILED=0

check() {
  local name="$1" url="$2" expected="$3"
  if [ -z "$url" ] || [ "$url" = "None" ]; then
    echo "SKIP  $name (stack output missing)"
    return
  fi
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$url" || echo 000)
  if [ "$status" = "$expected" ]; then
    echo "PASS  $name ($url -> $status)"
  else
    echo "FAIL  $name ($url -> $status, expected $expected)"
    FAILED=1
  fi
}

echo "Smoke testing stack $STACK in $REGION"
check "backend health" "${API_URL%/}/actuator/health" 200
check "frontend" "$SITE_URL" 200
# Unauthenticated API calls must be rejected, not served.
check "api requires auth" "${API_URL%/}/api/v1/config" 401

if [ "$FAILED" -ne 0 ]; then
  echo "smoke-test: FAILED"
  exit 1
fi

echo "smoke-test: all checks passed"
