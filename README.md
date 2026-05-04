# reflex-otel-metrics

`reflex-otel-metrics` is a Spring Boot starter for JDBC-backed OpenTelemetry metric export.

The current starter targets:

- Java 17
- Spring Boot 3
- Maven Wrapper (`.\mvnw.cmd`)
- OpenTelemetry `1.60.1`
- OTLP/gRPC metric export
- aggregate technical telemetry
- fail-safe execution

## What The Starter Owns

The starter auto-configures the shared metric infrastructure:

- `ReflexOtelMetricsProperties` binding under `reflex.otel.metrics`
- OTLP/gRPC metric exporter
- OTLP/gRPC trace exporter
- OTel `OpenTelemetry`, `Meter`, `Tracer`, and instrument registry beans
- config resolution and validation helpers
- series limiting support
- aggregate technical telemetry hooks
- fail-safe execution defaults for starter-managed infrastructure

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
      instrumentation-scope-name: com.reflex.otelmetrics
      otlp:
        metrics-endpoint: http://localhost:4317
        traces-endpoint: http://localhost:4317
        export-timeout: 10s
        export-interval: 1m
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
- `instrumentation-scope-name`
- `otlp.metrics-endpoint`
- `otlp.traces-endpoint`
- `otlp.export-timeout`
- `otlp.export-interval`
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

## Export Timing

Metric collection and OTLP export are separate steps.

1. the metric source query reads values from the database on its own schedule
2. the starter writes those values into OpenTelemetry instruments
3. the OpenTelemetry SDK exports accumulated metric data on its own periodic cycle

By default, the starter exports every minute:

```yaml
reflex:
  otel:
    metrics:
      otlp:
        export-interval: 1m
```

Use this to avoid exporting too often when database polling happens more frequently than downstream consumers need.

## Instrumentation Scope

`instrumentation-scope-name` controls the OpenTelemetry instrumentation scope used for both `Meter` and `Tracer`.

```yaml
reflex:
  otel:
    metrics:
      instrumentation-scope-name: com.example.business-metrics
```

This does not change the metric name itself. Metric names still come from `metric-prefix + suffix`. The scope name identifies which library or module emitted the telemetry.

## How Metric Kinds Behave

The current starter supports `GAUGE` and `UP_DOWN_COUNTER`. They behave differently between database polls and OTLP exports.

### `GAUGE`

Use `GAUGE` for snapshots like "how many rows exist right now".

Example:

- at `10:00:00` the query returns `42`
- at `10:00:30` the query returns `45`
- at `10:01:00` the SDK exports `45`

The latest observed value wins for each attribute set.

### `UP_DOWN_COUNTER`

Use `UP_DOWN_COUNTER` only when each collection run produces a delta that should be added to the previous state.

Example:

- at `10:00:00` the query returns `+5`
- at `10:00:30` the query returns `-2`
- at `10:01:00` the SDK exports the accumulated change for the interval

Do not use `UP_DOWN_COUNTER` for full table snapshots like `select count(*) ...`, otherwise each poll adds the whole snapshot again and the exported number will drift upward or downward incorrectly.

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

## Manual Metric Beans

JDBC metrics are collected on a starter-managed schedule: the starter runs the source query, maps rows to points, and publishes them to OpenTelemetry. Manual metrics are emitted directly by application code at the point where the business event or state change happens.

For manual metrics, the Java bean declaration is the primary contract. The bean defines the metric id, kind, suffix, scope, description, unit, attribute schema, cardinality limit, and overflow policy. YAML is an optional runtime override layer for deploy-time values such as enabling a metric, changing its suffix or scope, and adjusting cardinality handling.

Low-level metric beans work well when a service only needs a single instrument:

```java
package com.example.metrics;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.manual.ReflexMetricFactory;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Configuration
class OrderMetricConfiguration {

    @Bean
    CounterMetric ordersCreatedMetric(ReflexMetricFactory factory) {
        return factory.counter(
                "orders-created",
                MetricDefinition.of("orders.created")
                        .scope("business")
                        .description("Orders created by client and channel")
                        .unit("{order}")
                        .attributes(AttributesSchema.builder()
                                .required("client")
                                .required("channel")
                                .build())
                        .maxSeries(500)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());
    }
}

@Service
class OrderService {

    private final CounterMetric ordersCreatedMetric;

    public OrderService(@Qualifier("ordersCreatedMetric") CounterMetric ordersCreatedMetric) {
        this.ordersCreatedMetric = ordersCreatedMetric;
    }

    public void createOrder(String client, String channel) {
        // business code omitted
        ordersCreatedMetric.increment(Map.of(
                "client", client,
                "channel", channel));
    }
}
```

For larger flows, prefer a domain metric bean that groups the low-level instruments and exposes business-specific methods:

```java
package com.example.metrics;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.api.GaugeMetric;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.manual.ReflexMetricFactory;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
class OrderMetrics {

    private final CounterMetric created;
    private final CounterMetric failed;
    private final GaugeMetric queueSize;

    public OrderMetrics(ReflexMetricFactory factory) {
        AttributesSchema clientChannelAttributes = AttributesSchema.builder()
                .required("client")
                .required("channel")
                .build();

        this.created = factory.counter(
                "orders-created",
                MetricDefinition.of("orders.created")
                        .scope("business")
                        .description("Orders created by client and channel")
                        .unit("{order}")
                        .attributes(clientChannelAttributes)
                        .maxSeries(500)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());
        this.failed = factory.counter(
                "orders-failed",
                MetricDefinition.of("orders.failed")
                        .scope("business")
                        .description("Orders failed by client and channel")
                        .unit("{order}")
                        .attributes(clientChannelAttributes)
                        .maxSeries(500)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());
        this.queueSize = factory.gauge(
                "orders-queue-size",
                MetricDefinition.of("orders.queue.size")
                        .scope("business")
                        .description("Current order queue size by channel")
                        .unit("{order}")
                        .attributes(AttributesSchema.builder()
                                .required("channel")
                                .build())
                        .maxSeries(100)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());
    }

    public void created(String client, String channel) {
        created.increment(Map.of("client", client, "channel", channel));
    }

    public void failed(String client, String channel) {
        failed.increment(Map.of("client", client, "channel", channel));
    }

    public void queueSize(String channel, long value) {
        queueSize.set(value, Map.of("channel", channel));
    }
}

@Service
class OrderService {

    private final OrderMetrics orderMetrics;

    public OrderService(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    public void createOrder(String client, String channel) {
        try {
            // business code omitted
            orderMetrics.created(client, channel);
        } catch (RuntimeException exception) {
            orderMetrics.failed(client, channel);
            throw exception;
        }
    }

    public void updateQueueSize(String channel, long size) {
        orderMetrics.queueSize(channel, size);
    }
}
```

Manual metric runtime overrides live under `reflex.otel.metrics.manual.<metric-id>`:

```yaml
reflex:
  otel:
    metrics:
      manual:
        orders-created:
          enabled: true
          suffix: orders.created
          scope: business
          max-series: 500
          overflow-policy: FAIL
```

Manual metric calls are fail-safe for business code. Disabled metrics return without publishing. Invalid attributes, cardinality overflow, and OpenTelemetry runtime errors are logged and skipped by the metric implementation instead of failing the application flow.

`AGGREGATE_TO_OTHER` is not supported for manual metrics in v1 because manual emission does not have a batch of overflow points to aggregate. Use `FAIL` to skip new series over the limit, or `TRUNCATE` to stop accepting additional series after the limit is reached.
