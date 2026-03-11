# JVM Grafana Dashboard Guide

This guide provides a prebuilt Grafana dashboard for JVM observability across these services:

- `order-service`
- `payment-service`
- `inventory-service`
- `load-generator-service`

The dashboard is designed for your current stack where Prometheus scrapes each service at `/actuator/prometheus`.

## Files

- Dashboard JSON: `jvm-grafana-dashboard-guide/dashboard-jvm-microservices.json`
- This guide: `jvm-grafana-dashboard-guide/README.md`

## What Metrics Are Covered

The dashboard focuses on JVM health signals you can use during normal traffic and load-driven analysis.

1. Heap memory used by service (`jvm_memory_used_bytes{area="heap"}`)
2. Heap utilization percentage (`used / max`)
3. Non-heap memory used (`jvm_memory_used_bytes{area="nonheap"}`)
4. Metaspace usage (`jvm_memory_used_bytes{id="Metaspace"}`)
5. GC pause p95 (`jvm_gc_pause_seconds_bucket`)
6. GC events rate (`jvm_gc_pause_seconds_count`)
7. Live threads (`jvm_threads_live_threads`)
8. Thread states by state (`jvm_threads_states_threads`)
9. Process/system CPU usage (`process_cpu_usage`, `system_cpu_usage`)
10. Loaded classes (`jvm_classes_loaded_classes`)
11. Process uptime (`process_uptime_seconds`)

The dashboard filters by Prometheus `job` label, which maps directly to your service names.

## Import In Grafana

1. Open Grafana.
2. Go to `Dashboards` -> `New` -> `Import`.
3. Upload `jvm-grafana-dashboard-guide/dashboard-jvm-microservices.json`.
4. When prompted, map `prometheus_ds` to your Prometheus data source.
5. Save.

## Suggested Analysis Plan

Use this sequence when investigating JVM behavior across services.

1. Start with **Heap Used by Service** and **Heap Utilization %**.
2. If memory rises steadily, check **GC Pause p95** and **GC Event Rate**.
3. If latency issues appear, compare **CPU Usage** and **Live Threads** around the same time window.
4. If thread pressure appears, use **Thread States** to identify blocked/waiting patterns.
5. Use **Metaspace** and **Loaded Classes** to detect classloader growth or unusual initialization behavior.
6. Confirm service restarts/resets with **Process Uptime**.

## Practical JVM Notes

- High heap usage alone is not always bad. Rising heap with stable GC pauses can still be healthy.
- Frequent or long GC pauses combined with throughput drop is a stronger stress signal.
- Sudden thread count growth may indicate contention, blocked downstream calls, or unbounded executors.
- CPU saturation with normal heap can still degrade latency due to application-level work.
- Metaspace growth should usually stabilize; continuous growth can indicate classloader leaks.

## Scope In This Project

This dashboard only covers JVM runtime metrics from Micrometer/Actuator for:

- `order-service`
- `payment-service`
- `inventory-service`
- `load-generator-service`

For end-to-end correlation, combine this dashboard with existing dashboards and use logs/traces in Loki/Tempo.
