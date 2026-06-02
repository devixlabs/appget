#!/bin/bash
# Live HTML navigation check: GETs the content-negotiation pages with
# Accept: text/html and asserts the nav/action links are SERVER ROUTES
# (not the old file-relative paths that 404/500 in a browser). Mirrors the
# Playwright walk used to verify GAP-0F3. Requires a server on port 8080.
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
META=(-H 'X-Sso-Authenticated: true'
      -H 'X-Roles-Role-Level: 10'
      -H 'X-Roles-Is-Admin: true'
      -H 'X-Api-Is-Active: true')
ID="nav-check-1"
fail=0

# Seed a deterministic user so the detail/edit pages have data (idempotent:
# 201 fresh, 409 already present — both fine).
seed_code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/users" \
    "${META[@]}" -H 'Content-Type: application/json' \
    -d "{\"id\":\"$ID\",\"username\":\"navuser\",\"email\":\"nav@x.com\",\"displayName\":\"Nav\",\"bio\":\"b\",\"isVerified\":true,\"followerCount\":0,\"isActive\":true}")
case "$seed_code" in
    201|200|409) ;;
    *) echo "  ✗ seed user: unexpected status $seed_code"; fail=1 ;;
esac

# check <desc> <path> <expected-status> [+must-contain ...] [-must-not-contain ...]
check() {
    local desc="$1" path="$2" want="$3"; shift 3
    local resp code body ok=1 n
    resp=$(curl -s --max-time 10 --retry 2 -w $'\n%{http_code}' -H 'Accept: text/html' "$BASE$path")
    code=$(printf '%s' "$resp" | tail -n1)
    body=$(printf '%s' "$resp" | sed '$d')
    if [ "$code" != "$want" ]; then
        echo "  ✗ $desc: expected $want got $code  ($path)"; fail=1; return
    fi
    for n in "$@"; do
        if [ "${n:0:1}" = "+" ]; then
            if ! printf '%s' "$body" | grep -qF -- "${n:1}"; then
                echo "  ✗ $desc: missing '${n:1}'"; ok=0; fail=1
            fi
        else
            if printf '%s' "$body" | grep -qF -- "${n:1}"; then
                echo "  ✗ $desc: must NOT contain '${n:1}'"; ok=0; fail=1
            fi
        fi
    done
    if [ "$ok" -eq 1 ]; then
        echo "  ✓ $desc"
    fi
    return 0  # never abort the run on a failed assertion — the summary below decides
}

# Root index serves at / and links to server routes
check "GET / (root index)" "/" 200 '+href="/users"' '+href="/views/user-role"'

# List page: Home=/ , Create=/users?action=create ; NO file-relative links
check "GET /users (list)" "/users" 200 \
    '+<table>' '+href="/"' '+href="/users?action=create"' \
    '-../index.html' '-href="create.html"' '-href="index.html"'

# Create form reachable via ?action=create
check "GET /users?action=create" "/users?action=create" 200 '+<form' '+action="/users"'

# Detail page (the View-link target resolves) + Back-to-List=/users
check "GET /users/$ID (detail)" "/users/$ID" 200 \
    '+<dl>' '+href="/users"' '-../index.html' '-href="index.html"'

# Edit form pre-filled, _method=PUT present
check "GET /users/$ID?action=edit" "/users/$ID?action=edit" 200 '+<form' '+_method'

if [ "$fail" -ne 0 ]; then
    echo "✗ HTML navigation check FAILED" >&2
    exit 1
fi
echo "✓ HTML navigation links resolve to server routes"
