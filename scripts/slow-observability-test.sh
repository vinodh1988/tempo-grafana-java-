#!/usr/bin/env bash
set -u

# Slow, analysis-focused API exerciser with inline assertions and final summary.
# Usage:
#   bash scripts/slow-observability-test.sh
# Optional env overrides:
#   REQUEST_COUNT=8 REQUEST_DELAY_SEC=3 USE_LOAD_GENERATOR=false bash scripts/slow-observability-test.sh

ORDER_BASE_URL="${ORDER_BASE_URL:-http://localhost:18081}"
PAYMENT_BASE_URL="${PAYMENT_BASE_URL:-http://localhost:18082}"
INVENTORY_BASE_URL="${INVENTORY_BASE_URL:-http://localhost:18083}"
LOADGEN_BASE_URL="${LOADGEN_BASE_URL:-http://localhost:18084}"
TEMPO_BASE_URL="${TEMPO_BASE_URL:-http://localhost:3200}"
LOKI_BASE_URL="${LOKI_BASE_URL:-http://localhost:3110}"
PROM_BASE_URL="${PROM_BASE_URL:-http://localhost:19090}"

REQUEST_COUNT="${REQUEST_COUNT:-6}"
REQUEST_DELAY_SEC="${REQUEST_DELAY_SEC:-2}"
CURL_TIMEOUT_SEC="${CURL_TIMEOUT_SEC:-12}"
USE_LOAD_GENERATOR="${USE_LOAD_GENERATOR:-true}"

TOTAL=0
PASSED=0
FAILED=0
FAIL_LOG=()

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required but was not found in PATH."
  exit 1
fi

is_http_ok() {
  local code="$1"
  [[ "$code" =~ ^2[0-9][0-9]$ ]]
}

record_result() {
  local name="$1"
  local ok="$2"
  local detail="$3"
  TOTAL=$((TOTAL + 1))
  if [[ "$ok" == "true" ]]; then
    PASSED=$((PASSED + 1))
    printf "[PASS] %s\n" "$name"
  else
    FAILED=$((FAILED + 1))
    FAIL_LOG+=("$name :: $detail")
    printf "[FAIL] %s :: %s\n" "$name" "$detail"
  fi
}

request() {
  local method="$1"
  local url="$2"
  local body_file="$3"
  local code_file="$4"

  local code
  code="$(curl -sS -X "$method" "$url" -m "$CURL_TIMEOUT_SEC" -o "$body_file" -w "%{http_code}" || true)"
  echo "$code" > "$code_file"
}

check_endpoint_reachable() {
  local name="$1"
  local method="$2"
  local url="$3"

  local body_file code_file code
  body_file="$(mktemp)"
  code_file="$(mktemp)"

  request "$method" "$url" "$body_file" "$code_file"
  code="$(cat "$code_file")"

  if is_http_ok "$code"; then
    record_result "$name" "true" "HTTP $code"
  else
    record_result "$name" "false" "HTTP $code body=$(cat "$body_file")"
  fi

  rm -f "$body_file" "$code_file"
}

run_order_flow_checks() {
  local idx="$1"
  local order_id="SLOW-${idx}-$(date +%s)"
  local url="$ORDER_BASE_URL/orders/$order_id"

  local body_file code_file code body
  body_file="$(mktemp)"
  code_file="$(mktemp)"

  request "POST" "$url" "$body_file" "$code_file"
  code="$(cat "$code_file")"
  body="$(cat "$body_file")"

  if [[ "$code" != "200" ]]; then
    record_result "order-flow-$idx-http" "false" "HTTP $code body=$body"
    rm -f "$body_file" "$code_file"
    return
  fi
  record_result "order-flow-$idx-http" "true" "HTTP 200"

  if grep -q '"status"[[:space:]]*:[[:space:]]*"PLACED"' "$body_file"; then
    record_result "order-flow-$idx-status" "true" "status=PLACED"
  else
    record_result "order-flow-$idx-status" "false" "missing status=PLACED body=$body"
  fi

  if grep -q '"paymentStatus"[[:space:]]*:[[:space:]]*"CONFIRMED"' "$body_file"; then
    record_result "order-flow-$idx-payment" "true" "paymentStatus=CONFIRMED"
  else
    record_result "order-flow-$idx-payment" "false" "missing paymentStatus=CONFIRMED body=$body"
  fi

  if grep -q '"inventoryStatus"[[:space:]]*:[[:space:]]*"RESERVED"' "$body_file"; then
    record_result "order-flow-$idx-inventory" "true" "inventoryStatus=RESERVED"
  else
    record_result "order-flow-$idx-inventory" "false" "missing inventoryStatus=RESERVED body=$body"
  fi

  rm -f "$body_file" "$code_file"
}

check_loadgen_status() {
  local body_file code_file code body
  body_file="$(mktemp)"
  code_file="$(mktemp)"

  request "GET" "$LOADGEN_BASE_URL/load/status" "$body_file" "$code_file"
  code="$(cat "$code_file")"
  body="$(cat "$body_file")"

  if [[ "$code" != "200" ]]; then
    record_result "loadgen-status-http" "false" "HTTP $code body=$body"
    rm -f "$body_file" "$code_file"
    return
  fi
  record_result "loadgen-status-http" "true" "HTTP 200"

  if grep -q '"sent"' "$body_file" && grep -q '"failed"' "$body_file"; then
    record_result "loadgen-status-fields" "true" "sent/failed present"
  else
    record_result "loadgen-status-fields" "false" "missing sent/failed body=$body"
  fi

  rm -f "$body_file" "$code_file"
}

maybe_start_loadgen() {
  if [[ "$USE_LOAD_GENERATOR" != "true" ]]; then
    echo "Skipping load-generator start (USE_LOAD_GENERATOR=$USE_LOAD_GENERATOR)."
    return
  fi

  local body_file code_file code
  body_file="$(mktemp)"
  code_file="$(mktemp)"

  request "POST" "$LOADGEN_BASE_URL/load/start?rps=1" "$body_file" "$code_file"
  code="$(cat "$code_file")"

  if [[ "$code" == "200" ]]; then
    record_result "loadgen-start" "true" "HTTP 200"
  else
    record_result "loadgen-start" "false" "HTTP $code body=$(cat "$body_file")"
  fi

  rm -f "$body_file" "$code_file"
}

maybe_stop_loadgen() {
  if [[ "$USE_LOAD_GENERATOR" != "true" ]]; then
    return
  fi

  local body_file code_file code
  body_file="$(mktemp)"
  code_file="$(mktemp)"

  request "POST" "$LOADGEN_BASE_URL/load/stop" "$body_file" "$code_file"
  code="$(cat "$code_file")"

  if [[ "$code" == "200" ]]; then
    record_result "loadgen-stop" "true" "HTTP 200"
  else
    record_result "loadgen-stop" "false" "HTTP $code body=$(cat "$body_file")"
  fi

  rm -f "$body_file" "$code_file"
}

print_header() {
  cat <<EOF
============================================================
SLOW OBSERVABILITY TEST RUN
Started: $(date)
ORDER_BASE_URL=$ORDER_BASE_URL
REQUEST_COUNT=$REQUEST_COUNT REQUEST_DELAY_SEC=$REQUEST_DELAY_SEC
USE_LOAD_GENERATOR=$USE_LOAD_GENERATOR
============================================================
EOF
}

print_summary() {
  local percent=0
  if [[ "$TOTAL" -gt 0 ]]; then
    percent=$((PASSED * 100 / TOTAL))
  fi

  echo
  echo "==================== OVERALL TEST REPORT =================="
  echo "Finished: $(date)"
  echo "Total Checks : $TOTAL"
  echo "Passed       : $PASSED"
  echo "Failed       : $FAILED"
  echo "Pass Rate    : ${percent}%"

  if [[ "$FAILED" -gt 0 ]]; then
    echo ""
    echo "Failed Checks Detail:"
    local i
    for i in "${FAIL_LOG[@]}"; do
      echo " - $i"
    done
  else
    echo ""
    echo "All checks passed."
  fi
  echo "============================================================"
}

main() {
  print_header

  check_endpoint_reachable "order-health" "GET" "$ORDER_BASE_URL/actuator/health"
  check_endpoint_reachable "payment-health" "GET" "$PAYMENT_BASE_URL/actuator/health"
  check_endpoint_reachable "inventory-health" "GET" "$INVENTORY_BASE_URL/actuator/health"
  check_endpoint_reachable "loadgen-health" "GET" "$LOADGEN_BASE_URL/actuator/health"
  check_endpoint_reachable "tempo-ready" "GET" "$TEMPO_BASE_URL/ready"
  check_endpoint_reachable "loki-ready" "GET" "$LOKI_BASE_URL/ready"
  check_endpoint_reachable "prometheus-ready" "GET" "$PROM_BASE_URL/-/ready"

  maybe_start_loadgen

  local i
  for ((i = 1; i <= REQUEST_COUNT; i++)); do
    echo "Running slow order flow request $i/$REQUEST_COUNT ..."
    run_order_flow_checks "$i"
    if [[ "$i" -lt "$REQUEST_COUNT" ]]; then
      sleep "$REQUEST_DELAY_SEC"
    fi
  done

  check_loadgen_status
  maybe_stop_loadgen
  print_summary

  if [[ "$FAILED" -gt 0 ]]; then
    exit 1
  fi
}

main
