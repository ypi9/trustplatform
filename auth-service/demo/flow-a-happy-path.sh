#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-demo-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123}"
USER_PASSWORD="${USER_PASSWORD:-password123}"
USER_EMAIL="${USER_EMAIL:-demo-user+$(date +%s)@example.com}"
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

print_step "1" "Checking /health"
request_json GET /health
expect_status 200
printf '%s\n' "$RESPONSE_BODY" | jq .

print_step "2" "Registering admin $ADMIN_EMAIL"
request_json POST /auth/signup "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
if [[ "$RESPONSE_STATUS" != "201" && "$RESPONSE_STATUS" != "409" ]]; then
  echo "$RESPONSE_BODY" >&2
  exit 1
fi
printf '%s\n' "$RESPONSE_BODY" | jq .

print_step "3" "Logging in admin"
request_json POST /auth/login "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
expect_status 200
ADMIN_TOKEN="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.accessToken')"

print_step "4" "Registering user $USER_EMAIL"
request_json POST /auth/signup "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}"
expect_status 201
printf '%s\n' "$RESPONSE_BODY" | jq .

print_step "5" "Logging in user"
request_json POST /auth/login "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}"
expect_status 200
USER_TOKEN="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.accessToken')"

print_step "6" "Uploading demo document"
request_upload "$USER_TOKEN"
expect_status 200
printf '%s\n' "$RESPONSE_BODY" | jq .
DOCUMENT_KEY="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.objectKey')"
UPLOAD_REQUEST_ID="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.requestId')"

print_step "7" "Submitting verification"
request_json POST /verification/submit "{\"documentKey\":\"$DOCUMENT_KEY\",\"requestId\":\"$UPLOAD_REQUEST_ID\"}" "$USER_TOKEN"
expect_status 200
printf '%s\n' "$RESPONSE_BODY" | jq .
VERIFICATION_REQUEST_ID="$(printf '%s\n' "$RESPONSE_BODY" | jq -r '.requestId')"

print_step "8" "Approving verification as admin"
request_json POST /verification/review "{\"requestId\":\"$VERIFICATION_REQUEST_ID\",\"decision\":\"APPROVED\",\"reviewNotes\":\"Demo approval\"}" "$ADMIN_TOKEN"
expect_status 200
printf '%s\n' "$RESPONSE_BODY" | jq .

print_step "9" "Checking user verification status"
request_json GET /verification/status "" "$USER_TOKEN"
expect_status 200
printf '%s\n' "$RESPONSE_BODY" | jq .

print_step "10" "Checking user profile"
request_json GET /auth/me "" "$USER_TOKEN"
expect_status 200
printf '%s\n' "$RESPONSE_BODY" | jq .

printf '\nFlow A complete for %s\n' "$USER_EMAIL"
