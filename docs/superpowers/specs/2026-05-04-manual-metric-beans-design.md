# Manual Metric Beans Design

## Status

Draft reviewed in conversation on May 4, 2026.

## Goal

Add support for application-driven metrics that can be emitted directly from business code without JDBC polling.

The primary contract must live in Java bean declarations. YAML remains an optional operational override layer, matching the existing library style where code defines safe defaults and configuration can adjust runtime behavior.

## Primary Use Case

An application declares manual metrics once in a Spring `@Configuration` class:

```java
@Bean
CounterMetric ordersCreatedMetric(ReflexMetricFactory factory) {
    return factory.counter(
            "orders-created",
            MetricDefinition.of("orders.created")
                    .scope("business")
                    .description("Created orders")
                    .unit("1")
                    .attributes(AttributesSchema.builder()
                            .required("client")
                            .required("channel")
                            .build()));
}
```

Business services inject the prepared bean and only pass values plus attributes:

```java
ordersCreatedMetric.add(1, Map.of(
        "client", client,
        "channel", channel));
```

The service code does not look up metrics by string id at call sites.

## Public API

The first version exposes these manual metric building blocks:

- `ReflexMetricFactory`
- `CounterMetric`
- `GaugeMetric`
- `UpDownCounterMetric`
- `MetricDefinition`
- `AttributesSchema`

`ReflexMetricFactory` is the only supported creation path in v1. The library will not auto-create metric beans from standalone `ManualMetricDefinition` beans in this version.

### Metric Types

`MetricKind` gains explicit `COUNTER` support in addition to the existing `GAUGE` and `UP_DOWN_COUNTER`.

The typed metric beans expose instrument-specific operations:

- `CounterMetric.add(long value, Map<String, String> attributes)`
- `CounterMetric.increment(Map<String, String> attributes)`
- `GaugeMetric.set(long value, Map<String, String> attributes)`
- `UpDownCounterMetric.add(long value, Map<String, String> attributes)`

Convenience overloads without attributes may be provided for metrics with an empty attribute schema.

### Metric Definition

`MetricDefinition` contains Java-level defaults and contract fields:

- metric suffix
- scope
- description
- unit
- attributes schema
- max series
- overflow policy

The metric id is passed separately to factory methods and remains the stable key for YAML overrides.

## Configuration Model

Manual metrics use a separate configuration section:

```yaml
reflex:
  telemetry:
    metrics:
      manual:
        orders-created:
          enabled: true
          suffix: orders.created.v2
          scope: business-v2
          max-series: 1000
          overflow-policy: REJECT
```

YAML is optional. If no override exists for a metric id, the library uses Java defaults.

Allowed YAML overrides:

- `enabled`
- `suffix`
- `scope`
- `max-series`
- `overflow-policy`

Not allowed as YAML overrides:

- metric kind
- attributes schema
- description
- unit

Metric kind, attributes schema, description, and unit are part of the application code contract.

## Naming Rules

Metric names continue to be assembled as:

```text
<metric-prefix>.<metric-suffix>
```

The Java definition provides the default suffix. YAML may override the suffix.

YAML may also override the logical scope. Scope enablement follows the existing global and scope-level rules.

## Attribute Schema

Attributes are validated on every metric call.

The schema supports:

- required attributes
- optional attributes
- reject-unknown mode
- empty schema for metrics without attributes

Defaults:

- unknown attributes are rejected
- required attributes must be present
- attribute keys must be non-blank
- attribute values must be non-null and non-blank

Invalid attributes cause the metric write to be skipped.

## Failure Behavior

Manual metric calls must never fail business code.

Runtime failures are handled as log-and-skip:

- missing required attribute
- unknown attribute
- invalid attribute value
- negative value passed to `CounterMetric`
- disabled metric
- cardinality overflow
- OpenTelemetry publication failure

Startup contract failures may fail application startup:

- duplicate metric id with incompatible definitions
- metric kind conflict for the same full metric name
- invalid Java metric definition
- invalid YAML override values

This separates application contract errors from per-call telemetry failures.

## Cardinality

Manual metrics need per-call series protection instead of the current JDBC batch-only limiting.

Each manual metric keeps a thread-safe tracker of observed attribute sets.

When a new series would exceed `max-series`:

- `REJECT` logs and skips the write
- `AGGREGATE_TO_OTHER` may rewrite the attributes to an overflow series if the existing overflow policy can support this cleanly for manual metrics

`REJECT` is the recommended default for manual metrics because hidden aggregation can make business events harder to reason about.

## Thread Safety

Manual metric beans must be safe to inject into singleton services and call from many request threads.

Implementation requirements:

- resolved metric config is immutable
- OTel instruments are obtained from the existing concurrent `OtelInstrumentRegistry`
- input attribute maps are defensively copied or converted before publication
- cardinality tracking uses concurrent data structures
- no mutable caller-owned map is retained

## Injection Patterns

The README should document two supported patterns.

### Low-Level Metric Beans

Applications may define multiple metric beans of the same type and inject them with Spring qualifiers:

```java
OrderService(
        @Qualifier("ordersCreatedMetric") CounterMetric ordersCreatedMetric,
        @Qualifier("ordersFailedMetric") CounterMetric ordersFailedMetric) {
    this.ordersCreatedMetric = ordersCreatedMetric;
    this.ordersFailedMetric = ordersFailedMetric;
}
```

This is useful for small services or small numbers of metrics.

### Domain Metric Beans

For larger domains, applications should group low-level metric beans behind a domain-specific component:

```java
@Bean
OrderMetrics orderMetrics(ReflexMetricFactory factory) {
    return new OrderMetrics(
            factory.counter("orders-created", ...),
            factory.counter("orders-failed", ...),
            factory.gauge("orders-queue-size", ...));
}
```

Business services inject the domain bean instead of several low-level metrics:

```java
orderMetrics.created(client, channel);
orderMetrics.failed(client, reason);
```

The domain bean is application code. The library only provides the low-level metric primitives and validation behavior.

## Refactoring Direction

Before adding manual metrics, split common metric metadata from JDBC-specific runtime concerns.

Common/shared concerns:

- metric id
- metric suffix
- full metric name
- metric kind
- scope
- enabled state
- max series
- overflow policy
- OTEL registry and publisher helpers

JDBC-only concerns:

- data source reference
- query definition
- row mapper
- schedule
- timeout
- distributed lock settings

Manual-only concerns:

- attributes schema
- per-call validation
- per-call cardinality tracking
- log-and-skip runtime behavior

This prevents manual metrics from depending on JDBC fields such as `dataSourceRef`, scheduler settings, or lock settings.

## Tests

The implementation should cover:

- factory creates counter, gauge, and up-down-counter beans
- `COUNTER` instruments are registered and used correctly
- YAML override applies to enabled, suffix, scope, max series, and overflow policy
- YAML cannot change manual metric kind
- invalid Java definitions fail startup
- invalid attributes are logged and skipped
- missing required attributes are logged and skipped
- unknown attributes are logged and skipped by default
- blank or null attribute values are logged and skipped
- negative counter values are logged and skipped
- disabled metrics are no-op
- cardinality overflow is logged and skipped for `REJECT`
- multiple metric beans of the same type can coexist in Spring context
- domain metric bean usage is documented and covered by an application-context style test

## Non-Goals

- No annotation processor in v1
- No generated `OrdersCreatedMetric` interfaces in v1
- No automatic bean creation from definition-only beans in v1
- No string lookup API as the primary user-facing path
- No YAML-defined attributes schema in v1
