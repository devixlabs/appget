#!/usr/bin/env python3
"""
Generic HTTP test runner — reads a YAML test spec and executes tests via curl.

Usage:
    python3 tests/run-http-tests.py [test-spec.yaml]
    python3 tests/run-http-tests.py                    # defaults to tests/http-tests.yaml

Exit codes:
    0 = all tests passed
    1 = one or more tests failed
    2 = configuration error (missing file, server not running, etc.)
"""

import json
import subprocess
import sys
import os

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML is required. Install with: pip3 install pyyaml", file=sys.stderr)
    sys.exit(2)

# ANSI colors
GREEN = "\033[0;32m"
RED = "\033[0;31m"
BLUE = "\033[0;34m"
RESET = "\033[0m"


def load_spec(path):
    with open(path) as f:
        return yaml.safe_load(f)


def check_server(base_url):
    """Verify the server is reachable."""
    try:
        result = subprocess.run(
            ["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", base_url],
            capture_output=True, text=True, timeout=5
        )
        code = result.stdout.strip()
        if code == "000":
            return False
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return False
    return True


def run_test(test, config):
    """Run a single test and return (passed, details)."""
    base_url = config.get("base_url", "http://localhost:8080")
    default_headers = config.get("default_headers", {})

    url = test["url"]
    if not url.startswith("http"):
        url = base_url + url

    method = test.get("method", "GET")
    headers = {**default_headers, **test.get("headers", {})}
    body = test.get("body")
    expect = test.get("expect", {})
    expected_status = expect.get("status", 200)

    # Build curl command
    cmd = ["curl", "-s", "-w", "\n%{http_code}", "-X", method]
    for key, val in headers.items():
        cmd.extend(["-H", f"{key}: {val}"])
    if body:
        cmd.extend(["-d", body])
    cmd.append(url)

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        output = result.stdout
    except subprocess.TimeoutExpired:
        return False, "TIMEOUT after 30s"

    # Parse: last line is status code, everything before is body
    lines = output.rsplit("\n", 1)
    if len(lines) == 2:
        response_body = lines[0]
        actual_status = int(lines[1]) if lines[1].strip().isdigit() else 0
    else:
        response_body = ""
        actual_status = 0

    # Check status
    if actual_status != expected_status:
        return False, f"expected {expected_status}, got {actual_status}\n  Response: {response_body[:500]}"

    # Check contains
    for needle in expect.get("contains", []):
        if needle not in response_body:
            return False, f"response missing: {needle}\n  Response: {response_body[:500]}"

    # Check not_contains
    for needle in expect.get("not_contains", []):
        if needle in response_body:
            return False, f"response should not contain: {needle}\n  Response: {response_body[:500]}"

    return True, f"{method} {test['url']} -> {actual_status}"


def main():
    spec_path = sys.argv[1] if len(sys.argv) > 1 else "tests/http-tests.yaml"

    if not os.path.exists(spec_path):
        print(f"ERROR: Test spec not found: {spec_path}", file=sys.stderr)
        sys.exit(2)

    spec = load_spec(spec_path)
    config = spec.get("config", {})
    tests = spec.get("tests", [])

    if not tests:
        print("No tests found in spec file.", file=sys.stderr)
        sys.exit(2)

    base_url = config.get("base_url", "http://localhost:8080")
    print(f"{BLUE}Running {len(tests)} tests against {base_url}{RESET}\n")

    if not check_server(base_url):
        print(f"{RED}ERROR: Server not reachable at {base_url}{RESET}", file=sys.stderr)
        print("Start the server first: make run-server", file=sys.stderr)
        sys.exit(2)

    passed = 0
    failed = 0
    failures = []

    for test in tests:
        name = test.get("name", "unnamed")
        ok, detail = run_test(test, config)
        if ok:
            passed += 1
            print(f"{GREEN}PASS{RESET}: {name} ({detail})")
        else:
            failed += 1
            failures.append((name, detail))
            print(f"{RED}FAIL{RESET}: {name} ({detail})")

    print()
    if failed == 0:
        print(f"{GREEN}All {passed} tests passed.{RESET}")
    else:
        print(f"{RED}{failed} failed{RESET}, {passed} passed out of {passed + failed} tests.")
        print(f"\n{RED}Failures:{RESET}")
        for name, detail in failures:
            print(f"  - {name}: {detail}")

    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
