# Tempo + Loki + Prometheus Java Microservices POC

This repository is a Docker Compose POC for observability across multiple Java microservices.

It includes:
- Distributed traces in Tempo
- Logs in Loki (shipped by Promtail)
- Metrics from all Java services in Prometheus
- Grafana-ready data source configuration (Grafana install not required)

## Inter-service flow

The call chain is implemented in code:
- `order-service` receives `POST /orders/{orderId}`
- `order-service` calls `payment-service` at `POST /payments/{orderId}`
- `order-service` calls `inventory-service` at `POST /inventory/reserve/{orderId}`

This gives you one end-to-end trace that spans all services.

## What runs in Docker Compose

- `order-service`
- `payment-service`
- `inventory-service`
- `tempo`
- `loki`
- `promtail`
- `prometheus`
- `load-generator-service`

## Metrics emission (all Java services)

Each service now includes:
- Spring Boot Actuator
- `micrometer-registry-prometheus`
- `management.endpoints.web.exposure.include=health,info,prometheus`

Prometheus endpoints:
- `order-service`: `http://98.84.130.190:18081/actuator/prometheus`
- `payment-service`: `http://98.84.130.190:18082/actuator/prometheus`
- `inventory-service`: `http://98.84.130.190:18083/actuator/prometheus`
- `load-generator-service`: `http://98.84.130.190:18084/actuator/prometheus`

Prometheus scrape config file:
- `prometheus/prometheus.yml`

## Port configuration

Host ports are configurable via `.env` so they do not collide with other projects.

Current `.env` defaults in this repo:

```dotenv
ORDER_SERVICE_PORT=18081
PAYMENT_SERVICE_PORT=18082
INVENTORY_SERVICE_PORT=18083
LOAD_GENERATOR_PORT=18084
TEMPO_HTTP_PORT=3200
TEMPO_OTLP_GRPC_PORT=4317
TEMPO_OTLP_HTTP_PORT=4318
LOKI_HTTP_PORT=3110
PROMETHEUS_PORT=19090
LOAD_DEFAULT_RPS=2
```

## Start the stack

```powershell
docker compose up --build -d
```

Check services:

```powershell
docker compose ps
```

## Generate traffic manually

```powershell
Invoke-RestMethod -Method Post http://localhost:18081/orders/ORD-1001
```

## Artificial load generation service

The project now has a dedicated Java microservice: `load-generator-service`.

Load service base URL:
- `http://localhost:18084` (or `LOAD_GENERATOR_PORT` override)

APIs:
- Start continuous load: `POST /load/start?rps=5`
- Stop continuous load: `POST /load/stop`
- Run burst load: `POST /load/burst?count=200`
- Check status: `GET /load/status`
- List click-to-run scenarios: `GET /trigger/scenarios`
- Run one scenario: `POST /trigger/run?scenario=HAPPY`

Built-in UI:
- Open `http://localhost:18084/`
- This page provides one-click buttons for managed scenarios that automatically send valid/invalid inputs.
- Scenarios generate traces and log patterns for INFO, WARN, and ERROR without users needing to know request payloads.

Available scenarios:
- `HAPPY`: valid order flow with successful downstream calls (INFO logs + trace)
- `WARN`: managed warning path (WARN + INFO logs + trace)
- `ERROR`: managed failure path (ERROR logs + 5xx behavior + trace)
- `BAD_ROUTE`: intentionally invalid route call to generate client-side failure signals
- `MIXED`: runs all of the above sequentially

Examples:

```powershell
Invoke-RestMethod -Method Post "http://localhost:18084/load/start?rps=5"
Invoke-RestMethod -Method Get "http://localhost:18084/load/status"
Invoke-RestMethod -Method Post "http://localhost:18084/load/burst?count=200"
Invoke-RestMethod -Method Post "http://localhost:18084/load/stop"
Invoke-RestMethod -Method Get "http://localhost:18084/trigger/scenarios"
Invoke-RestMethod -Method Post "http://localhost:18084/trigger/run?scenario=WARN"
```

## Tempo configuration guide

File: `tempo/tempo.yaml`

Key settings:
- OTLP receiver enabled (gRPC + HTTP)
- Local block storage for traces
- 24-hour retention for POC

Grafana Tempo datasource URL:
- `http://localhost:3200`
- Or `http://localhost:${TEMPO_HTTP_PORT}` if overridden

## Loki configuration guide

File: `loki/loki-config.yaml`

Key settings:
- Single-node local mode
- Filesystem-backed TSDB
- No auth for local POC

Grafana Loki datasource URL:
- `http://localhost:3110`
- Or `http://localhost:${LOKI_HTTP_PORT}` if overridden

## Promtail configuration guide

File: `promtail/promtail-config.yaml`

Key settings:
- Reads Docker JSON logs
- Parses Docker log format using `docker` pipeline stage
- Pushes to Loki at `http://loki:3100/loki/api/v1/push`

## Prometheus configuration guide

File: `prometheus/prometheus.yml`

Key settings:
- Scrape interval: 5s
- Scrapes all 4 Java services at `/actuator/prometheus` via VM public IP

Current configured targets in `prometheus/prometheus.yml`:
- `98.84.130.190:9090` (Prometheus)
- `98.84.130.190:18081` (order-service)
- `98.84.130.190:18082` (payment-service)
- `98.84.130.190:18083` (inventory-service)
- `98.84.130.190:18084` (load-generator-service)

Prometheus UI:
- `http://98.84.130.190:9090` (VM-hosted Prometheus)
- For Docker Compose-hosted Prometheus in this repo: `http://localhost:19090`

Useful sample PromQL:
- `up{job=~"order-service|payment-service|inventory-service|load-generator-service"}`
- `rate(http_server_requests_seconds_count[1m])`
- `jvm_memory_used_bytes`

## Grafana data source configuration

Configure 3 data sources in Grafana:

1. Tempo
- Type: Tempo
- URL: `http://localhost:3200` (or overridden Tempo port)

2. Loki
- Type: Loki
- URL: `http://localhost:3110` (or overridden Loki port)

3. Prometheus
- Type: Prometheus
- URL (VM-hosted Prometheus): `http://98.84.130.190:9090`
- URL (Docker Compose-hosted Prometheus): `http://localhost:19090`

Loki derived field for log-to-trace navigation:
- Name: `TraceID`
- Regex: `trace_id=(\w+)`
- Data source: Tempo

## Prebuilt dashboard (JSON)

Dashboard file included in repo:
- `grafana/dashboard-observability-poc.json`
- `grafana/dashboard-order-slo.json`

Import steps in Grafana:
1. Open Grafana -> Dashboards -> New -> Import.
2. Upload `grafana/dashboard-observability-poc.json`.
3. Map data sources when prompted:
- `prometheus_ds` -> your Prometheus datasource
- `loki_ds` -> your Loki datasource
- `tempo_ds` -> your Tempo datasource
4. Save dashboard.

Dashboard panels include:
- Request rate by service (Prometheus)
- HTTP p95 latency by service (Prometheus)
- JVM heap usage (Prometheus)
- Service logs with trace IDs (Loki)
- Recent traces by service (Tempo)

Second dashboard (`grafana/dashboard-order-slo.json`) is SLO-focused for `order-service`:
- Throughput (RPS)
- Error rate (5xx)
- Latency p95
- Availability percentage
- Order logs and recent traces for fast triage

If your Grafana version does not support the `traces` panel type, use Explore with Tempo datasource for trace search and keep the rest of the dashboard panels as-is.

## End-to-end case study workflow

1. Start stack and trigger continuous load with `load-generator-service`.
2. Open Grafana Explore.
3. In Tempo, search traces for `order-service`.
4. Open one trace and inspect spans for payment/inventory downstream calls.
5. In Loki, filter logs for `order-service`, then click `TraceID` to jump to trace.
6. In Prometheus, validate request rate and latency metrics during load.
7. Correlate slow traces with log lines and request-rate spikes.

## Useful commands

```powershell
docker compose logs -f order-service
docker compose logs -f payment-service
docker compose logs -f inventory-service
docker compose logs -f tempo
docker compose logs -f loki
docker compose logs -f promtail
docker compose logs -f prometheus
docker compose logs -f load-generator-service
```

```powershell
docker compose down
docker compose down -v
```
