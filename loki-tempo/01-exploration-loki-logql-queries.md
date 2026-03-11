# Loki Exploration Guide (LogQL)

This guide gives many ready-to-use Loki queries for exploring logs from microservices.

## 1. Start with broad log streams

```logql
{job=~".*"}
```

```logql
{service_name=~"order-service|payment-service|inventory-service|load-generator-service"}
```

```logql
{container=~".*order.*|.*payment.*|.*inventory.*"}
```

## 2. Filter by text and log level

```logql
{service_name="order-service"} |= "ERROR"
```

```logql
{service_name="payment-service"} |= "WARN"
```

```logql
{service_name="inventory-service"} |= "Exception"
```

```logql
{service_name="order-service"} |= "timeout" |= "http"
```

```logql
{service_name="load-generator-service"} !~ "health|actuator"
```

## 3. Parse JSON logs

```logql
{service_name="order-service"} | json
```

```logql
{service_name="order-service"} | json | level="ERROR"
```

```logql
{service_name="payment-service"} | json | status=~"5.."
```

```logql
{service_name="inventory-service"} | json | path="/api/inventory"
```

```logql
{service_name="order-service"} | json | traceId!=""
```

## 4. Extract fields from plain text logs

```logql
{service_name="order-service"} | pattern "<ts> <level> <msg>"
```

```logql
{service_name="payment-service"} | regexp "orderId=(?P<orderId>[a-zA-Z0-9-]+)"
```

```logql
{service_name="inventory-service"} | regexp "latencyMs=(?P<latency>[0-9]+)"
```

## 5. Metrics from logs (counts/rates)

```logql
count_over_time({service_name="order-service"} |= "ERROR" [5m])
```

```logql
sum by (service_name) (
  rate({service_name=~"order-service|payment-service|inventory-service"} |= "ERROR" [1m])
)
```

```logql
sum by (service_name) (
  count_over_time({service_name=~"order-service|payment-service|inventory-service"} |= "Exception" [10m])
)
```

```logql
topk(5, sum by (path) (count_over_time({service_name="order-service"} | json | __error__="" [15m])))
```

## 6. Latency-style analysis from logs

```logql
quantile_over_time(0.95, {service_name="inventory-service"} | regexp "latencyMs=(?P<latency>[0-9]+)" | unwrap latency [5m])
```

```logql
avg_over_time({service_name="payment-service"} | regexp "duration=(?P<duration>[0-9]+)" | unwrap duration [5m])
```

```logql
max_over_time({service_name="order-service"} | regexp "took=(?P<took>[0-9]+)ms" | unwrap took [10m])
```

## 7. Investigate error bursts

```logql
sum by (service_name) (count_over_time({service_name=~".*service"} |= "ERROR" [1m]))
```

```logql
sum by (service_name) (count_over_time({service_name=~".*service"} |= "CircuitBreaker" [5m]))
```

```logql
sum by (service_name) (count_over_time({service_name=~".*service"} |= "Retry" [5m]))
```

## 8. Correlate by trace and request IDs

```logql
{service_name="order-service"} | json | traceId="${traceId}"
```

```logql
{service_name=~"order-service|payment-service|inventory-service"} | json | requestId="${requestId}"
```

```logql
{service_name=~".*service"} | json | orderId="${orderId}"
```

## 9. High-value production shortcuts

```logql
sum by (service_name) (rate({service_name=~".*service"} |= " 500 " [1m]))
```

```logql
sum by (service_name) (rate({service_name=~".*service"} |= "OutOfMemoryError" [5m]))
```

```logql
sum by (service_name) (rate({service_name=~".*service"} |= "connection refused" [5m]))
```

```logql
topk(10, sum by (service_name, pod) (count_over_time({service_name=~".*service"} |= "ERROR" [15m])))
```

## 10. Suggested Grafana variables

- `service`: `label_values({service_name=~".*service"}, service_name)`
- `level`: custom values like `ERROR,WARN,INFO,DEBUG`
- `traceId`: textbox variable
- `requestId`: textbox variable

Then use them in queries:

```logql
{service_name="$service"} |= "$level"
```

```logql
{service_name="$service"} | json | traceId="$traceId"
```

```logql
{service_name="$service"} | json | requestId="$requestId"
```
