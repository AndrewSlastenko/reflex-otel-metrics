# Reflex Telemetry Library Design

## Status

Draft reviewed in conversation on April 29, 2026.

## Goal

Build a reusable Spring Boot starter for Java 17 services that centralizes OpenTelemetry metrics collection and export. The primary use case is business snapshot metrics collected from databases and exported to an OTEL collector over OTLP/gRPC.

The library must let application services define metric sources with minimal boilerplate while the library owns scheduling, locking, OTEL SDK setup, exporter wiring, metric naming, and operational telemetry.

## Fixed Constraints

- Java 17
- Spring Boot 3
- Maven
- OpenTelemetry version pinned to `1.60.1`
- Export protocol: `OTLP/gRPC`
- Signals in scope:
  - Metrics: required in v1
  - Traces: not actively used in v1, but architecture should leave room for later support
- Application availability is more important than metrics availability
- Dynatrace consumers rely on MQL, so emitted metrics must be usable without DQL post-processing

## Primary Use Case

Application services expose business metrics derived from database state. A typical source queries a SQL view or SQL statement and returns rows like:

- `client=A, status=created, value=10`
- `client=B, status=created, value=10`
- `client=B, status=processing, value=20`

The library turns these rows into a single OTEL metric with:

- one metric name
- one point per row
- arbitrary attributes derived from the row
- `long` values only in v1

The service decides what data to query and how to map rows to metric points. The library handles everything else.

## Non-Goals

- No logs signal support in v1
- No Actuator `HealthIndicator` in v1
- No automatic business semantics inference
- No requirement to support non-JDBC data sources in v1
- No per-metric traces logic in v1

## Architecture Overview

The library ships as a Spring Boot starter and owns the infrastructure layer:

- OTEL SDK setup for metrics
- OTLP/gRPC exporter configuration
- metric scheduler
- distributed locking integration
- metric name construction using a configured prefix
- publication of business metrics
- internal technical metrics and logs

Application services own the business layer:

- declaring metric source beans
- defining how data is collected
- mapping source rows to library metric points
- optionally overriding defaults through configuration

## Public API Model

### Core Contracts

#### `MetricSource`

Represents one logical metric definition.

One source produces:

- one technical `metricId`
- one OTEL metric name suffix
- one metric kind
- one execution configuration
- one set of points per run

Expected responsibilities:

- provide a stable `metricId`
- provide bean-level defaults
- identify the logical `scope`
- identify the `metric kind`
- collect or describe how to collect metric points

#### `JdbcMetricSource`

Specialized contract for the main v1 use case.

Additional responsibilities:

- declare `dataSourceRef`
- provide a query definition, initially focused on SQL or SQL view usage
- provide row mapping from `ResultSet` to `MetricPoint`

### Value Objects

#### `MetricPoint`

- `long value`
- `Map<String, String> attributes`

#### `MetricKind`

Supported in v1:

- `GAUGE`
- `UP_DOWN_COUNTER`

Application code must set this explicitly. The library does not infer metric kind.

#### `MetricDefinitionDefaults`

Bean-level defaults that can be overridden by application properties. Expected fields:

- `metricSuffix`
- `metricKind`
- `scope`
- `dataSourceRef` for JDBC sources
- scheduling defaults
- timeout defaults
- lock defaults
- series limit defaults
- overflow policy defaults

#### `MetricSchedule`

Supported modes:

- `FIXED_DELAY`
- `CRON`

`FIXED_DELAY` is the recommended default for snapshot database metrics.

#### `SeriesOverflowPolicy`

Supported policies in v1:

- `FAIL`
- `TRUNCATE`
- `AGGREGATE_TO_OTHER`

## Configuration Model

The effective runtime configuration for each metric is resolved as:

1. library defaults
2. bean-provided defaults
3. `application.yml` or `application.properties` overrides

Missing properties are not treated as errors when a valid default exists in code.

### Global Configuration

Expected settings:

- library enabled flag
- global metric prefix
- OTLP metrics endpoint
- OTLP traces endpoint
- exporter timeout and batching settings
- global internal telemetry toggles

### Scope Configuration

Scopes are logical groups only. They do not resolve `DataSource` instances.

Typical examples:

- `business`
- `workflow`

Primary use:

- grouped enable/disable
- grouped operational control

### Per-Metric Configuration

Each metric is keyed by `metricId`.

Expected overridable fields:

- `enabled`
- `suffix`
- `kind`
- `scope`
- `data-source-ref`
- `schedule.mode`
- `schedule.fixed-delay`
- `schedule.cron`
- `schedule.initial-delay`
- `timeout`
- `lock.at-most-for`
- `lock.at-least-for`
- `max-series`
- `overflow-policy`
- `other-attributes` for aggregation into `other`

## DataSource Strategy

The library never creates or owns application `DataSource` pools.

For JDBC metrics:

- `dataSourceRef` is mandatory
- it points to an existing Spring `DataSource` bean declared by the application
- the library resolves that bean by name or qualifier and uses it for query execution

`scope` is not used for `DataSource` selection.

This keeps database routing explicit and avoids hidden fallback rules.

## Naming Rules

Metric names are assembled by the library as:

- `<global-prefix>.<metric-suffix>`

The service configures the global prefix through properties. Individual sources define only the suffix.

This ensures all emitted metrics comply with required naming conventions while keeping business metric declarations concise.

## Scheduling and Concurrency

Each metric has its own schedule and execution controls.

Rules:

- the library supports both `FIXED_DELAY` and `CRON`
- `FIXED_DELAY` is the recommended default
- one metric must never run in parallel with itself inside one application instance
- cross-pod duplication is prevented through distributed locking

Distributed lock granularity is per metric. A blocked or slow metric must not prevent other metrics from being collected.

The expected first implementation is ShedLock with JDBC provider, wrapped behind a library abstraction to avoid hard-wiring all internals to one locking implementation.

## Runtime Flow

### Startup

On application startup the library:

1. initializes Spring Boot auto-configuration
2. creates OTEL metrics infrastructure and OTLP/gRPC exporter
3. initializes traces-related wiring only to the extent needed for future expansion
4. discovers all `MetricSource` beans
5. resolves the effective configuration for each source
6. validates the resolved configuration
7. disables only invalid metrics and logs startup errors
8. registers execution pipelines for valid enabled metrics

Invalid metric configuration must not fail overall application startup.

### One Metric Execution Cycle

1. scheduler triggers execution
2. global, scope, and metric enable flags are checked
3. distributed lock is acquired for that `metricId`
4. if lock acquisition fails, the run is skipped
5. the source executes with the configured timeout
6. points are collected
7. series count and overflow policy are applied
8. the full metric name is constructed
9. points are published through the configured OTEL instrument
10. internal telemetry and logs are updated
11. lock is released

### Failure Behavior

On any execution failure, including:

- SQL error
- timeout
- mapper error
- lock provider error
- exporter error

the library must:

- keep the application alive
- mark the metric cycle as failed
- avoid publishing synthetic zeros
- emit technical signals if the telemetry path is available
- write logs describing the failure

If business metric collection fails, the expected visible effect is a missing update rather than an incorrect value.

## Attributes and Cardinality

Application services may emit arbitrary metric attributes. The library must not hard-code a fixed attribute schema.

At the same time, the library needs safety controls because OTEL attributes become metric dimensions in Dynatrace and can cause cardinality explosions.

The v1 policy is:

- allow arbitrary attributes
- enforce per-metric limits on number of produced series per run
- support configurable overflow handling
- optionally warn on obviously risky attribute keys in logs

The library does not attempt to perfectly predict backend cardinality limits, since those are enforced in Dynatrace over time and across unique dimension tuples.

## Internal Technical Telemetry

The first version needs aggregate technical telemetry rather than deep per-metric introspection.

Suggested aggregate signals:

- collection cycles attempted
- collection cycles succeeded
- collection cycles failed
- collection cycles skipped
- aggregate execution duration
- overflow or truncation events

Structured logs remain the primary troubleshooting surface for specific metric failures.

No Actuator health endpoint is required in v1.

## Enable and Disable Hierarchy

Three layers of enablement are supported:

- global library level
- scope level
- metric level

Higher-level disable flags win over lower-level enables.

This allows operations teams to:

- disable all metrics
- disable one logical group such as `workflow`
- disable one problematic metric without removing its bean

## Recommended Package Structure

The implementation can reasonably separate into:

- `autoconfigure`
- `config`
- `api`
- `jdbc`
- `runtime`
- `locking`
- `otel`
- `internal`

Exact package names are an implementation detail, but the boundaries should follow these responsibilities.

## Risks and Design Notes

### OTEL Instrument Semantics

Because the library must support multiple metric semantics over time, the application explicitly declares `MetricKind`. This avoids incorrect library-side guessing and keeps the API extensible.

### Dynatrace Compatibility

Metrics must be modeled so that standard MQL dashboards can consume them directly. The library should avoid designs that require downstream parsing or reconstruction of dimensions.

### Operational Safety

Metrics collection is intentionally fail-safe. The application remains the priority workload, and monitoring problems must not degrade core business processing beyond the minimal overhead of the failing metric pipeline.

## Recommended v1 Approach

Implement the library as:

- a Spring Boot starter
- with explicit `MetricSource` and `JdbcMetricSource` contracts
- with bean-defined defaults plus property overrides
- with per-metric scheduling and locking
- with explicit `dataSourceRef`
- with aggregate technical telemetry

This gives a stable v1 for database-backed business metrics without overfitting the library to a single service.

## Open Points Deferred Beyond v1

- richer non-JDBC source types
- support for `double` metric values
- deeper traces integration
- detailed per-metric health state exposure
- more advanced cardinality heuristics
- richer query definition types beyond initial SQL and SQL view support
