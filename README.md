# reflex-otel-metrics

`reflex-otel-metrics` is a Spring Boot starter for JDBC-backed OpenTelemetry metric export.

The current starter targets:

- Java 17
- Spring Boot 3
- Maven Wrapper (`.\mvnw.cmd`)
- OpenTelemetry `1.60.1`
- OTLP/gRPC metric export

## What The Starter Owns

The starter auto-configures the shared metric infrastructure:

- `ReflexOtelMetricsProperties` binding under `reflex.otel.metrics`
- OTLP/gRPC metric exporter
- OTel `OpenTelemetry`, `Meter`, and instrument registry beans
- config resolution and validation helpers
- series limiting support

Application code is expected to contribute metric source beans and their JDBC mapping logic.

## Build And Test

Run the full test suite from the repository root with:

```powershell
.\mvnw.cmd test
```

Run only the starter auto-configuration tests with:

```powershell
.\mvnw.cmd -Dtest=ReflexOtelMetricsAutoConfigurationTest test
```

## Configuration Contract

The starter binds properties from `reflex.otel.metrics`.

Global properties:

```yaml
reflex:
  otel:
    metrics:
      enabled: true
      metric-prefix: ci054147
      otlp:
        metrics-endpoint: http://localhost:4317
        traces-endpoint: http://localhost:4317
        export-timeout: 10s
      scopes:
        business:
          enabled: true
```

Per-source runtime overrides live under `reflex.otel.metrics.sources.<metric-id>`:

```yaml
reflex:
  otel:
    metrics:
      sources:
        documents-by-status:
          enabled: true
          suffix: documents.current
          scope: business
          data-source-ref: businessReplicaDataSource
          kind: UP_DOWN_COUNTER
          schedule-mode: FIXED_DELAY
          fixed-delay: 5m
          initial-delay: 30s
          timeout: 30s
          lock-at-most-for: 2m
          lock-at-least-for: 10s
          max-series: 500
          overflow-policy: AGGREGATE_TO_OTHER
```

These keys map directly to the current `ReflexOtelMetricsProperties` and `MetricRuntimeProperties` model:

- `metric-prefix`
- `otlp.metrics-endpoint`
- `otlp.traces-endpoint`
- `otlp.export-timeout`
- `scopes.<scope>.enabled`
- `sources.<metric-id>.enabled`
- `sources.<metric-id>.suffix`
- `sources.<metric-id>.scope`
- `sources.<metric-id>.data-source-ref`
- `sources.<metric-id>.kind`
- `sources.<metric-id>.schedule-mode`
- `sources.<metric-id>.fixed-delay`
- `sources.<metric-id>.cron`
- `sources.<metric-id>.initial-delay`
- `sources.<metric-id>.timeout`
- `sources.<metric-id>.lock-at-most-for`
- `sources.<metric-id>.lock-at-least-for`
- `sources.<metric-id>.max-series`
- `sources.<metric-id>.overflow-policy`

Runtime configuration is resolved as:

1. starter defaults
2. defaults returned by the metric source bean
3. property overrides from `application.yml` or `application.properties`

## Metric Source Contract

Each metric source is a Spring bean with a stable `metricId()` and code-level defaults.

For JDBC-backed metrics, implement `JdbcMetricSource`:

```java
package com.example.metrics;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DocumentsByStatusMetricSource implements JdbcMetricSource {

    @Override
    public String metricId() {
        return "documents-by-status";
    }

    @Override
    public MetricDefinitionDefaults defaults() {
        return new MetricDefinitionDefaults(
                "documents.by-status",
                MetricKind.UP_DOWN_COUNTER,
                "business",
                "businessReplicaDataSource",
                new MetricScheduleDefaults(
                        MetricScheduleDefaults.Mode.FIXED_DELAY,
                        Duration.ofMinutes(5),
                        null,
                        Duration.ofSeconds(30)),
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofSeconds(10),
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
    }

    @Override
    public QueryDefinition queryDefinition() {
        return new QueryDefinition("""
                select client_code, document_status, count(*) as value
                from transaction_view
                group by client_code, document_status
                """);
    }

    @Override
    public RowMapper<MetricPoint> rowMapper() {
        return (rs, rowNum) -> new MetricPoint(
                rs.getLong("value"),
                Map.of(
                        "client", rs.getString("client_code"),
                        "status", rs.getString("document_status")));
    }
}
```

With the example above, an operator can override only the deploy-time values when needed, for example:

```properties
reflex.otel.metrics.metric-prefix=ci054147
reflex.otel.metrics.sources.documents-by-status.suffix=documents.current
reflex.otel.metrics.sources.documents-by-status.fixed-delay=PT2M
```
