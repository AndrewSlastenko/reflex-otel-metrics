# reflex-otel-metrics

Spring Boot starter for JDBC-backed OpenTelemetry metrics export.

## Prerequisites

- JDK 17

## Build

Run tests from the repository root with:

```powershell
./mvnw.cmd test
```

## Planned capabilities

- OpenTelemetry `1.60.1`
- OTLP/gRPC metrics export
- Per-metric scheduling and locking
- JDBC metric sources
- Fail-safe execution
