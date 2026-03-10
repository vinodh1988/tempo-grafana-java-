# Prometheus Federation Setup

This folder runs a dedicated Prometheus that federates selected metrics from two remote Prometheus servers.

## Port clarification for your deployment

From this repository settings:

- Local compose Prometheus mapping is `PROMETHEUS_PORT=19090`, so local URL is `http://<host>:19090`.
- Container port remains `9090`.

From remote probing done in this session:

- `http://98.84.130.190:9090/-/ready` responded with `200`.
- `35.188.115.81:19090` timed out from this machine.

Because of that, federation config currently uses `:9090` for both remote targets.

## Files in this folder

- `prometheus.yml`: federation scrape config (`/federate` with `match[]`).
- `recording-rules.yml`: small rollup rules for debugging.
- `docker-compose.yml`: standalone federation Prometheus on `39090`.

## Step-by-step

1. Open `prometheus.yml` and verify remote targets:
   - `35.188.115.81:9090`
   - `98.84.130.190:9090`

2. If one host exposes `19090` instead, change only that target.

3. Start federator:

```powershell
cd prometheus-federation
docker compose up -d
```

4. Verify federator is running:

```powershell
Invoke-RestMethod http://localhost:39090/-/ready
```

5. Check targets in UI:

- Open `http://localhost:39090/targets`
- Ensure `federation-node-1` and `federation-node-2` are `UP`

6. Validate pulled metrics:

In `http://localhost:39090/graph`, run:

```promql
up
```

```promql
federated:targets_up
```

```promql
federated:http_rps:rate1m
```

## How metric selection works

Federation does not pull everything by default. It pulls series matching `match[]` expressions.

Current selection includes:

- Core health/internal metrics: `up`, `process_*`, `go_*`, `prometheus_*`
- Spring app latency/request metrics: `http_server_requests_seconds_*`
- JVM metrics: `jvm_*`
- Service filter: jobs matching
  - `order-service`
  - `payment-service`
  - `inventory-service`
  - `load-generator-service`

## Debugging if federation fails

1. Test source directly:

```powershell
Invoke-RestMethod "http://98.84.130.190:9090/federate?match[]=up"
```

2. Check federator logs:

```powershell
docker compose logs --tail=200 prometheus-federation
```

3. Check firewall/network rules for source Prometheus port.

4. Confirm source Prometheus has those metrics at all:

- Open source Prometheus UI and query `up` and `http_server_requests_seconds_count`.

## Optional: lock down source endpoints

For production, avoid exposing source Prometheus publicly. Put federation over private networking/VPN and protect endpoints with authentication or network ACLs.
