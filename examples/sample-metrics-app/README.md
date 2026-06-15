# sample-metrics-app

Reference consumer for `rcln-reflex-telemetry`: local in-memory demo, layered tests, and wiring patterns to copy into real services.

Пакет приложения: `ru.sber.rcln.monitoring.loro.cred`.

This module is **not** a production deployment artifact. Kubernetes (or any real environment) supplies its own `ConfigMap` / env vars; the JAR only ships shared defaults and the `local` demo profile.

## Profiles

| Profile | Purpose |
| ------- | ------- |
| `local` (default) | In-memory H2, demo DDL via `LocalDemoSchemaConfig` |
| `test` | Integration tests only (`src/test/resources`) |

`application.yml` sets `spring.profiles.default: local` so `spring-boot:run` and `java -jar` work without extra flags.

**Deployment:** set `SPRING_PROFILES_ACTIVE` to your environment profile (anything except `local`) and provide `app.metrics-datasources.*`, `reflex.telemetry.*`, etc. from ConfigMap. Demo DDL runs only when `local` is active.

## Layout

| Package / path | Purpose |
| -------------- | ------- |
| `config/MetricsDataSourceConfig` | `businessMetricsDataSource`, `workflowMetricsDataSource`, `telemetryDataSource` |
| `config/MetricsLockConfig` | ShedLock on `{app.metrics-lock.schema}.shedlock` (table must exist; no runtime DDL on prod) |
| `config/MetricsLockProperties` | `app.metrics-lock.schema` (default `telemetry`) |
| `config/LocalDemoSchemaConfig` | `DataSourceScriptDatabaseInitializer` for H2 demo (`local` profile only) |
| `metrics/*MetricSource` | `AbstractJdbcMetricSource` + `JdbcMetricQuerySettings` |
| `application.yml` | App name, default profile `local`, `spring.sql.init.mode: never` |
| `application-local.yml` | H2 DataSource URLs and pools (all three pools share one `jdbc:h2:mem:sample-metrics` DB) |
| `application-reflex.yml` | Metric definitions including `query.schema` |
| `db/local/demo-schema.sql` | Demo DDL + seed data |

`LocalDemoSchemaConfig` seeds via `businessMetricsDataSource` only. That is enough for the demo because all three pools point at the same in-memory database. If each pool used a different JDBC URL, you would need one initializer per distinct database.

## Run locally

```powershell
..\..\mvnw.cmd -pl examples/sample-metrics-app -am spring-boot:run
```

H2 is pre-seeded via `db/local/demo-schema.sql`. OTLP export targets `http://localhost:4318` (collector optional).

## Tests

```powershell
..\..\mvnw.cmd -pl examples/sample-metrics-app -am test
```

Use `-am` (also-make) so the reactor builds `rcln-reflex-telemetry` first.

| Test | Scope |
| ---- | ----- |
| `DocumentsByStatusRowMapperTest` | Unit: `rowMapper()` + `queryDefinition()` schema |
| `DocumentsByStatusMetricJdbcTest` | `@JdbcTest` + `test` |
| `PaymentsByStateMetricJdbcTest` | `@JdbcTest` + `test` |
| `MetricsWiringTest` | `@SpringBootTest` + `test`: бины ↔ YAML, resolver, DataSource, ShedLock (без per-metric SQL) |
| `SampleMetricsApplicationTest` | Context load; default `local` profile, telemetry disabled in test properties |

Integration tests use profile `test` (`application-test.yml`): same bean names and `query.schema` as the demo; H2 URLs and idle-friendly schedules. `@JdbcTest` slices import `config.JdbcSliceTelemetryConfig`, which loads `reflex.telemetry.*` from `application-reflex.yml`.

**Разделение тестов:** `MetricsWiringTest` проверяет только сквозной wiring (каждый `JdbcMetricSource`-бин есть в YAML, resolver отдаёт `data-source-ref` и `query.schema`, бины пулов различны). SQL, маппинг строк и сбор точек — в отдельных `*MetricJdbcTest` / `*RowMapperTest` на метрику; при 20–30 метриках в wiring-тест ничего дописывать не нужно.
