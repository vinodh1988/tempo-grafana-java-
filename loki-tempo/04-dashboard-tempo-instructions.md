# Dashboard Creation Instructions: Tempo (Traces)

Use these steps to create a Tempo-focused tracing dashboard in Grafana.

## 1. Create dashboard shell

1. Create new dashboard and name it `Tempo Traces - Microservices`.
2. Add tags: `tempo`, `traces`, `distributed-tracing`.
3. Save immediately.

## 2. Add variables

1. `service` (Custom or query-backed): `order-service,payment-service,inventory-service,load-generator-service`
2. `route` (Text box): default `/api/orders`
3. `minDuration` (Text box): default `500ms`
4. `statusCode` (Custom): `200,400,404,429,500,502,503`

## 3. Core trace panels

1. Panel: `Trace Search by Service`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "$service" }
```

2. Panel: `Slow Traces`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "$service" && duration > $minDuration }
```

3. Panel: `Error Traces`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "$service" && status = error }
```

4. Panel: `Route-specific Traces`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "$service" && span.http.route = "$route" }
```

5. Panel: `HTTP Status Focus`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "$service" && span.http.status_code = $statusCode }
```

6. Panel: `Order -> Payment Hops`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "order-service" } >> { resource.service.name = "payment-service" }
```

7. Panel: `Order -> Inventory Hops`
- Visualization: `Traces`
- Query:
```traceql
{ resource.service.name = "order-service" } >> { resource.service.name = "inventory-service" }
```

## 4. Complement with metrics-style panels

If Grafana supports trace metrics from Tempo, add:

1. `Trace request rate by service`
2. `Error rate by service`
3. `p95 trace duration`

If trace metrics are not enabled, add links to Prometheus metrics dashboard.

## 5. Correlation and drill-down flow

1. Start from Loki dashboard error logs.
2. Copy `traceId` and open Tempo Explore.
3. Inspect root span duration and children.
4. Validate whether slow path is `payment` or `inventory` call.
5. Return to logs for matching `requestId` timeline.

## 6. Useful TraceQL snippets for panels and Explore

```traceql
{ status = error }
```

```traceql
{ duration > 1s }
```

```traceql
{ span.http.status_code >= 500 }
```

```traceql
{ span.http.method = "POST" && span.http.route = "/api/orders" }
```

```traceql
{ resource.service.name = "payment-service" && span.name =~ ".*charge.*" }
```

```traceql
{ resource.service.name = "inventory-service" && duration > 800ms }
```

## 7. Dashboard UX checklist

- Use 30m and 6h time quick-ranges.
- Add panel links to Loki logs dashboard.
- Keep a top row with `Slow`, `Error`, and `5xx` trace searches.
- Use consistent service variable names across dashboards.

## 8. Definition of done

- You can isolate slow traces in less than 3 clicks.
- Error traces are filterable by service and route.
- Cross-navigation to logs exists.
- Parent-child dependency paths are visible for critical flows.
