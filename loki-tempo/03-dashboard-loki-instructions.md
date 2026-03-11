# Dashboard Creation Instructions: Loki (Logs)

Use these steps to create a production-friendly log dashboard in Grafana.

## 1. Create dashboard shell

1. Open Grafana, click `Dashboards` -> `New` -> `New Dashboard`.
2. Set dashboard title to `Loki Logs - Microservices`.
3. Add tags: `loki`, `logs`, `microservices`.
4. Save early to avoid losing panel edits.

## 2. Add variables

Create these variables:

1. `service` (Query):
```text
label_values({service_name=~".*service"}, service_name)
```
2. `level` (Custom):
```text
ERROR,WARN,INFO,DEBUG
```
3. `traceId` (Text box)
4. `requestId` (Text box)
5. `interval` (Custom):
```text
1m,5m,10m,15m
```

## 3. Build core panels

1. Panel: `Live Logs ($service)`
- Visualization: `Logs`
- Query:
```logql
{service_name="$service"}
```

2. Panel: `Errors by Service`
- Visualization: `Time series`
- Query:
```logql
sum by (service_name) (rate({service_name=~".*service"} |= "ERROR" [$interval]))
```

3. Panel: `Warnings by Service`
- Visualization: `Time series`
- Query:
```logql
sum by (service_name) (rate({service_name=~".*service"} |= "WARN" [$interval]))
```

4. Panel: `Top Error Messages`
- Visualization: `Bar chart`
- Query:
```logql
topk(10, sum by (message) (count_over_time({service_name="$service"} | json | level="ERROR" | line_format "{{.message}}" [15m])))
```

5. Panel: `Trace Correlation Logs`
- Visualization: `Logs`
- Query:
```logql
{service_name="$service"} | json | traceId="$traceId"
```

6. Panel: `Request Correlation Logs`
- Visualization: `Logs`
- Query:
```logql
{service_name=~".*service"} | json | requestId="$requestId"
```

7. Panel: `HTTP 5xx Log Rate`
- Visualization: `Stat`
- Query:
```logql
sum(rate({service_name=~".*service"} |= " 5" [$interval]))
```

## 4. Improve readability and triage speed

1. For log panels, enable `Wrap lines` and `Prettify JSON` if available.
2. Add field extraction to highlight `traceId`, `requestId`, `orderId`, `status`.
3. Use color mappings:
- `ERROR` red
- `WARN` orange
- `INFO` blue
4. Set panel links from logs to Tempo traces when `traceId` exists.

## 5. Add dashboard links

1. Add a link to your Tempo dashboard.
2. Add a link to JVM metrics dashboard.
3. Add a link to runbook documentation.

## 6. Alert-ready panels

Create alert conditions on:

1. Error rate spikes:
```logql
sum(rate({service_name=~".*service"} |= "ERROR" [5m])) > 1
```
2. Repeated connection failures:
```logql
sum(rate({service_name=~".*service"} |= "connection refused" [5m])) > 0.2
```
3. Timeout bursts:
```logql
sum(rate({service_name=~".*service"} |= "timeout" [5m])) > 0.5
```

## 7. Recommended final layout

1. Row 1: Global health stats (`Errors`, `Warnings`, `5xx`).
2. Row 2: Trends per service.
3. Row 3: Live logs and correlation logs.
4. Row 4: Top errors and noisy endpoints.

## 8. Definition of done

- Variables work for all services.
- Logs can be filtered by `traceId` and `requestId`.
- Error spikes are visible within 1 minute.
- At least one panel directly supports alerting.
