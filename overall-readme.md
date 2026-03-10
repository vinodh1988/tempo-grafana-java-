# Overall Observability Runbook (Tempo + Loki + Prometheus)

This guide explains how to run, verify, analyze, and debug observability in this project.

It is written for local Docker Compose usage on Windows with Docker Desktop.

## 1. What Was Fixed

The following issues were preventing useful analysis:

1. Prometheus was scraping an external VM IP, not local compose services.
2. Promtail was configured for Linux host file paths (`/var/lib/docker/containers/...`) that are unreliable on Windows setups.
3. Load generation depended on explicit API calls and could be pushed too hard.

Implemented fixes:

1. Prometheus targets now use compose DNS names (`order-service:8081`, etc.).
2. Promtail now discovers and tails containers through Docker socket service discovery.
3. Load-generator now starts moderate background load automatically with conservative defaults.
4. Load-generator has HTTP client timeouts and stricter rate/burst caps to avoid freezing your machine.

## 2. Updated Default Behavior

Load generation now starts automatically after the load-generator service boots.

Default settings in `.env`:

```dotenv
LOAD_DEFAULT_RPS=1
LOAD_MAX_RPS=10
LOAD_AUTO_ENABLED=true
LOAD_AUTO_START_DELAY_MS=12000
LOAD_BURST_MAX_COUNT=300
```

Meaning:

1. Background traffic starts ~12 seconds after startup.
2. It runs at 1 request per second by default.
3. Any manual `rps` value is capped to 10.
4. Burst requests are capped to 300.

You can still manually stop/start using `/load/stop` and `/load/start`, but you do not need to do so for normal analysis.

## 3. Start the Stack

From repo root:

```powershell
docker compose down
docker compose up --build -d
```

Check container health/status:

```powershell
docker compose ps
```

## 4. Verify Traces Are Being Generated

### 4.1 Quick check from service logs

Run:

```powershell
docker compose logs -f order-service
docker compose logs -f payment-service
docker compose logs -f inventory-service
```

You should see log lines with MDC fields:

- `trace_id=...`
- `span_id=...`

If these appear and traffic exists, trace context is flowing.

### 4.2 Tempo API check

Check Tempo is reachable:

```powershell
Invoke-RestMethod http://localhost:3200/ready
```

Expected: readiness response (HTTP 200).

### 4.3 Generate one explicit trace (optional)

Even with auto-load, this is useful as a smoke test:

```powershell
Invoke-RestMethod -Method Post http://localhost:18081/orders/ORD-SMOKE-1
```

Then inspect `order-service` logs and copy the `trace_id` to correlate.

## 5. Verify Logs Are Reaching Loki

### 5.1 Check Promtail logs

```powershell
docker compose logs -f promtail
```

You should NOT see repeated file-path errors about missing `/var/lib/docker/containers`.

### 5.2 Check Loki readiness

```powershell
Invoke-RestMethod http://localhost:3110/ready
```

Expected: readiness response.

### 5.3 Query Loki labels

```powershell
Invoke-RestMethod "http://localhost:3110/loki/api/v1/labels"
```

Expected: labels include things like `service`, `container`, `compose_project`.

If labels are empty, wait 20-40 seconds and check `promtail` logs again.

## 6. Verify Metrics Are Being Scraped

### 6.1 Prometheus targets page

Open:

- `http://localhost:19090/targets`

Expected: `order-service`, `payment-service`, `inventory-service`, and `load-generator-service` are `UP`.

### 6.2 Useful PromQL

In Prometheus UI (`http://localhost:19090`):

```promql
up{job=~"order-service|payment-service|inventory-service|load-generator-service"}
```

```promql
rate(http_server_requests_seconds_count[1m])
```

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service))
```

## 7. Analyze End-to-End (Recommended Flow)

1. Let auto-load run for 2-5 minutes.
2. Inspect service request rates in Prometheus.
3. Use Loki queries per service to inspect logs.
4. Find a `trace_id` in logs and open that trace in Tempo/Grafana.
5. Confirm span sequence:
   - `order-service`
   - `payment-service`
   - `inventory-service`
6. Compare latency spikes with log anomalies/exceptions.

## 8. Useful Local Endpoints

- Order API: `http://localhost:18081/orders/{orderId}` (POST)
- Payment API: `http://localhost:18082/payments/{orderId}` (POST)
- Inventory API: `http://localhost:18083/inventory/reserve/{orderId}` (POST)
- Load status: `http://localhost:18084/load/status` (GET)
- Tempo: `http://localhost:3200`
- Loki: `http://localhost:3110`
- Prometheus: `http://localhost:19090`

## 9. Load-Generator Controls (Now Safe by Default)

Check status:

```powershell
Invoke-RestMethod -Method Get http://localhost:18084/load/status
```

Stop background load:

```powershell
Invoke-RestMethod -Method Post http://localhost:18084/load/stop
```

Start low load manually:

```powershell
Invoke-RestMethod -Method Post "http://localhost:18084/load/start?rps=1"
```

Small burst (analysis only, not stress):

```powershell
Invoke-RestMethod -Method Post "http://localhost:18084/load/burst?count=25"
```

## 10. Troubleshooting Checklist (No Logs / No Traces)

If nothing appears, run this exact sequence:

1. `docker compose ps` and ensure all containers are running.
2. `docker compose logs --tail=200 tempo` and verify no startup errors.
3. `docker compose logs --tail=200 promtail` and verify no Docker socket/read errors.
4. `docker compose logs --tail=200 loki` and verify ingestion path is healthy.
5. `docker compose logs --tail=200 order-service` and confirm requests are actually being processed.
6. Check load-generator status endpoint and sent/failed counters:
   - `http://localhost:18084/load/status`
7. Call one direct order request and immediately inspect logs:
   - `POST /orders/ORD-MANUAL-1`
8. Verify Prometheus targets are `UP` in `http://localhost:19090/targets`.

## 11. Reset If State Is Corrupted

If you still cannot see data, do a clean reset:

```powershell
docker compose down -v
docker compose up --build -d
```

Then wait ~20 seconds and re-run sections 4, 5, and 6.

## 12. Notes About This Project

- Traces are exported via OpenTelemetry Java agent to Tempo OTLP gRPC (`tempo:4317`).
- Logs are plain container stdout logs collected by Promtail and sent to Loki.
- Trace-to-log correlation relies on MDC values (`trace_id`, `span_id`) in service log pattern.
- This setup is tuned for observability analysis, not load testing.
