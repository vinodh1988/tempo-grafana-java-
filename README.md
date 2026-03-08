# Tempo + Loki Java Microservices POC (Docker Compose)

This repository contains a full local POC for distributed tracing and log analysis using:
- 3 Java microservices (`order-service`, `payment-service`, `inventory-service`)
- Grafana Tempo for traces
- Grafana Loki for logs
- Promtail for Docker log shipping
- Docker Compose for end-to-end local execution (no Kubernetes)

## Why this design fits your machine

Your shared environment details:
- Java: `11.0.28`
- Maven: `3.3.9` (old)

This POC avoids host Maven constraints by building each service inside Docker with:
- `maven:3.9.9-eclipse-temurin-11` for build stage
- `eclipse-temurin:11-jre` for runtime

So you can run with Docker Compose directly, even if local Maven is outdated.

## Architecture

1. Client calls `order-service` at `POST /orders/{orderId}`.
2. `order-service` calls:
- `payment-service` at `POST /payments/{orderId}`
- `inventory-service` at `POST /inventory/reserve/{orderId}`
3. OpenTelemetry Java agent auto-instruments HTTP calls and exports traces to Tempo.
4. Services log with MDC trace keys (`trace_id`, `span_id`).
5. Promtail reads Docker container logs and pushes them to Loki.
6. Grafana links logs to traces via derived field.

## Project structure

```text
.
|-- docker-compose.yml
|-- tempo/tempo.yaml
|-- loki/loki-config.yaml
|-- promtail/promtail-config.yaml
|-- order-service/
|-- payment-service/
`-- inventory-service/
```

## Start the stack

From repository root:

```powershell
docker compose up --build -d
```

### Optional: avoid host port conflicts

If another project already uses ports (for example Loki on `3100`), create a `.env` file in the repository root and override host ports:

```dotenv
ORDER_SERVICE_PORT=8081
PAYMENT_SERVICE_PORT=8082
INVENTORY_SERVICE_PORT=8083
TEMPO_HTTP_PORT=3210
TEMPO_OTLP_GRPC_PORT=4327
TEMPO_OTLP_HTTP_PORT=4328
LOKI_HTTP_PORT=3110
```

Then restart:

```powershell
docker compose down
docker compose up --build -d
```

Check running containers:

```powershell
docker compose ps
```

## Generate trace + logs

Call the order endpoint:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/orders/ORD-1001
```

Expected behavior:
- One distributed trace across all three services
- Correlated logs from all services containing `trace_id` and `span_id`

## Tempo configuration guide

Config file: `tempo/tempo.yaml`

Current key settings:
- OTLP receiver enabled on gRPC (`4317`) and HTTP (`4318`)
- Local trace storage at `/tmp/tempo/traces`
- 24h local retention

Compose exposure:
- `3200` query API for Grafana datasource
- `4317` OTLP gRPC ingest
- `4318` OTLP HTTP ingest

## Loki configuration guide

Config file: `loki/loki-config.yaml`

Current key settings:
- Single-binary local mode
- Local filesystem storage under `/loki`
- TSDB schema (`v13`)
- No auth (`auth_enabled: false`) for local POC

Compose exposure:
- `3100` HTTP API for Grafana datasource and Promtail push

## Promtail configuration guide

Config file: `promtail/promtail-config.yaml`

Current key settings:
- Reads Docker JSON log files from `/var/lib/docker/containers/*/*-json.log`
- Uses `docker` pipeline stage to parse Docker log format
- Pushes to `http://loki:3100/loki/api/v1/push`

## Grafana configuration (Grafana already installed)

Add 2 data sources:

1) Tempo
- Type: Tempo
- URL: `http://localhost:3200` (or your `TEMPO_HTTP_PORT` override)

2) Loki
- Type: Loki
- URL: `http://localhost:3100` (or your `LOKI_HTTP_PORT` override)

### Logs to traces linking

In Loki datasource settings, add a Derived field:
- Name: `TraceID`
- Regex: `trace_id=(\w+)`
- Data source: Tempo

This enables click-through from a log line to the corresponding Tempo trace.

## Suggested analysis workflow (case study)

1. Load generation
- Trigger several order requests with different IDs.

```powershell
1..5 | ForEach-Object { Invoke-RestMethod -Method Post http://localhost:8081/orders/ORD-$_ }
```

2. Trace analysis in Grafana Tempo
- Filter by service name `order-service`
- Open a trace and verify child spans for payment and inventory calls
- Compare span durations to identify slow downstreams

3. Log analysis in Grafana Loki
- Query service logs, for example:
- `{container="order-service"}`
- `{container="payment-service"}`
- `{container="inventory-service"}`
- Find error or slow logs and click `TraceID` derived field to open full distributed trace

4. Root cause pattern
- Start from a failed/slow order log in Loki
- Jump to Tempo trace
- Identify whether delay/failure occurred in payment or inventory span

## Useful operational commands

View logs:

```powershell
docker compose logs -f order-service
docker compose logs -f payment-service
docker compose logs -f inventory-service
docker compose logs -f tempo
docker compose logs -f loki
docker compose logs -f promtail
```

Stop stack:

```powershell
docker compose down
```

Stop and remove volumes:

```powershell
docker compose down -v
```

## Notes for your Java/Maven setup

- If you run services directly on host later, prefer Maven Wrapper (`mvnw`) over your installed Maven 3.3.9.
- For Spring Boot 3.x in future, upgrade host to Java 17 and newer Maven.
