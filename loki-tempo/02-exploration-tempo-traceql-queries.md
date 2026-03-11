# Tempo Exploration Guide (TraceQL)

This guide gives many TraceQL queries to explore distributed traces in Tempo.

## 1. Find all traces and narrow down

```traceql
{}
```

```traceql
{ resource.service.name = "order-service" }
```

```traceql
{ resource.service.name =~ "order-service|payment-service|inventory-service" }
```

```traceql
{ span.name =~ "GET .*|POST .*" }
```

## 2. Error-focused tracing

```traceql
{ status = error }
```

```traceql
{ resource.service.name = "payment-service" && status = error }
```

```traceql
{ span.http.status_code >= 500 }
```

```traceql
{ span.db.system = "postgresql" && status = error }
```

## 3. Latency and long-running traces

```traceql
{ duration > 500ms }
```

```traceql
{ resource.service.name = "order-service" && duration > 1s }
```

```traceql
{ span.http.target = "/api/orders" && duration > 300ms }
```

```traceql
{ resource.service.name = "inventory-service" && duration > 750ms }
```

## 4. Endpoint and operation filters

```traceql
{ span.http.method = "GET" && span.http.route = "/api/orders/{id}" }
```

```traceql
{ span.http.method = "POST" && span.http.route = "/api/orders" }
```

```traceql
{ span.name = "InventoryClient.checkStock" }
```

```traceql
{ span.name =~ ".*Controller.*|.*Client.*" }
```

## 5. Service dependency and path exploration

```traceql
{ resource.service.name = "order-service" } >> { resource.service.name = "payment-service" }
```

```traceql
{ resource.service.name = "order-service" } >> { resource.service.name = "inventory-service" }
```

```traceql
{ resource.service.name = "load-generator-service" } >> { resource.service.name = "order-service" }
```

```traceql
{ resource.service.name = "order-service" } << { resource.service.name = "load-generator-service" }
```

## 6. Attribute-based filtering

```traceql
{ span.http.status_code = 429 }
```

```traceql
{ span.http.status_code = 404 }
```

```traceql
{ span.db.system = "mysql" }
```

```traceql
{ span.messaging.system = "kafka" }
```

```traceql
{ span.net.peer.name =~ ".*redis.*|.*postgres.*" }
```

## 7. Correlation-first queries

```traceql
{ trace:rootName = "POST /api/orders" }
```

```traceql
{ span.http.route = "/api/orders" && span.http.status_code >= 500 }
```

```traceql
{ resource.service.name = "order-service" && span.http.route = "/api/orders" && duration > 400ms }
```

```traceql
{ resource.service.name = "payment-service" && span.name =~ ".*charge.*" && status = error }
```

## 8. Troubleshooting patterns

```traceql
{ resource.service.name = "order-service" && ! (span.http.status_code >= 200 && span.http.status_code < 400) }
```

```traceql
{ resource.service.name = "inventory-service" && duration > 2s && status = unset }
```

```traceql
{ resource.service.name = "payment-service" && span.name =~ ".*retry.*|.*circuit.*" }
```

```traceql
{ duration > 3s } >> { status = error }
```

## 9. Suggested Grafana variables for Tempo panels

- `service`: services from traces
- `route`: top routes
- `minDuration`: textbox (for example `500ms`)

Example with variables:

```traceql
{ resource.service.name = "$service" && span.http.route = "$route" && duration > $minDuration }
```

## 10. Query progression workflow

1. Start broad: `{}`.
2. Add service filter.
3. Add duration or error filter.
4. Add endpoint or operation filter.
5. Use parent/child matching to isolate hop-by-hop latency.
