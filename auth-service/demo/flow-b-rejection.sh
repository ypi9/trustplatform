#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-demo-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123}"
REJECT_USER_PASSWORD="${REJECT_USER_PASSWORD:-password123}"
REJECT_USER_EMAIL="${REJECT_USER_EMAIL:-demo-reject+$(date +%s)@example.com}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAMPLE_FILE="${SAMPLE_FILE:-$DEMO_DIR/sample-document.pdf}"

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require curl
require jq

print_step() {
  printf '\n[%s] %s\n' "$1" "$2"
}

print_body() {
  if printf '%s' "$RESPONSE_BODY" | jq empty >/dev/null 2>&1; then
    printf '%s\n' "$RESPONSE_BODY" | jq .
  else
    printf '%s\n' "$RESPONSE_BODY"
  fi
}

check_health() {
  print_step "0" "Checking /health"
  request_json GET /health
  expect_status 200
  print_body
  if printf '%s' "$RESPONSE_BODY" | jq empty >/dev/null 2>&1; then
    if [[ "$(printf '%s' "$RESPONSE_BODY" | jq -r '.s3 // empty')" == "DOWN" ]]; then
      echo "S3 is DOWN at $BASE_URL. This verification demo needs a configured S3-backed environment." >&2
      echo "Point BASE_URL to a cloud deployment or configure AWS locally, then rerun." >&2
      exit 1
    fi
  fi
}

request_json() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local token="${4:-}"
  local response

  if [[ -n "$body" && -n "$token" ]]; then
    response="$(curl -sS -w '\n%{http_code}' -X "$method" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $token" \
      -d "$body" "$BASE_URL$path")"
  elif [[ -n "$body" ]]; then
    response="$(curl -sS -w '\n%{http_code}' -X "$method" \
      -H "Content-Type: application/json" \
      -d "$body" "$BASE_URL$path")"
  elif [[ -n "$token" ]]; then
    response="$(curl -sS -w '\n%{http_code}' -X "$method" \
      -H "Authorization: Bearer $token" \
      "$BASE_URL$path")"
  else
    response="$(curl -sS -w '\n%{http_code}' -X "$method" "$BASE_URL$path")"
  fi

  RESPONSE_BODY="$(printf '%s\n' "$response" | sed '$d')"
  RESPONSE_STATUS="$(printf '%s\n' "$response" | tail -n1)"
}

request_upload() {
  local token="$1"
  local response

  response="$(curl -sS -w '\n%{http_code}' -X POST \
    -H "Authorization: Bearer $token" \
    -F "file=@$SAMPLE_FILE" \
    "$BASE_URL/files/upload")"

  RESPONSE_BODY="$(printf '%s\n' "$response" | sed '$d')"
  RESPONSE_STATUS="$(printf '%s\n' "$response" | tail -n1)"
}

expect_status() {
  local expected="$1"
  if [[ "$RESPONSE_STATUS" != "$expected" ]]; then
    echo "Expected HTTP $expected but got $RESPONSE_STATUS" >&2
    echo "$RESPONSE_BODY" >&2
    exit 1
  fi
}

check_health

print_step "1" "Logging in admin"
request_json POST /auth/login "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
expect_status 200
ADMIN_TOKEN="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.accessToken')"

print_step "2" "Registering rejection-flow user $REJECT_USER_EMAIL"
request_json POST /auth/signup "{\"email\":\"$REJECT_USER_EMAIL\",\"password\":\"$REJECT_USER_PASSWORD\"}"
expect_status 201
print_body

print_step "3" "Logging in rejection-flow user"
request_json POST /auth/login "{\"email\":\"$REJECT_USER_EMAIL\",\"password\":\"$REJECT_USER_PASSWORD\"}"
expect_status 200
USER_TOKEN="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.accessToken')"

print_step "4" "Uploading demo document"
request_upload "$USER_TOKEN"
expect_status 200
print_body
DOCUMENT_KEY="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.objectKey')"
UPLOAD_REQUEST_ID="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.requestId')"

print_step "5" "Submitting verification"
request_json POST /verification/submit "{\"documentKey\":\"$DOCUMENT_KEY\",\"requestId\":\"$UPLOAD_REQUEST_ID\"}" "$USER_TOKEN"
expect_status 200
print_body
VERIFICATION_REQUEST_ID="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.requestId')"

print_step "6" "Rejecting verification as admin"
request_json POST /verification/review "{\"requestId\":\"$VERIFICATION_REQUEST_ID\",\"decision\":\"REJECTED\",\"reviewNotes\":\"Demo rejection\"}" "$ADMIN_TOKEN"
expect_status 200
print_body

print_step "7" "Checking user verification status"
request_json GET /verification/status "" "$USER_TOKEN"
expect_status 200
print_body

print_step "8" "Checking user profile"
request_json GET /auth/me "" "$USER_TOKEN"
expect_status 200
print_body

printf '\nFlow B complete for %s\n' "$REJECT_USER_EMAIL"
