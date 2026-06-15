# sample-metrics-app

Reference consumer application for `rcln-reflex-telemetry`: multiple metric `DataSource` beans, ShedLock, JDBC metric sources (`AbstractJdbcMetricSource` + `query.schema`) and layered tests.

## Layout

| Package / path | Purpose |
| -------------- | ------- |
| `config/MetricsDataSourceConfig` | `documentsMetricsDataSource`, `paymentsMetricsDataSource`, `telemetryLockDataSource` |
| `config/MetricsLockConfig` | ShedLock `LockProvider` on `telemetry.shedlock` |
| `metrics/*MetricSource` | `AbstractJdbcMetricSource` + `JdbcMetricQuerySettings` |
| `application.yml` | DataSource URLs and pool settings (`app.metrics-datasources.*`) |
| `application-reflex.yml` | Metric definitions including `query.schema` |

## Run locally

```powershell
..\..\mvnw.cmd -pl examples/sample-metrics-app -am spring-boot:run
```

H2 in-memory database is pre-seeded via `schema.sql`. OTLP export targets `http://localhost:4318` (collector optional).

## Tests

```powershell
..\..\mvnw.cmd -pl examples/sample-metrics-app -am test
```

Use `-am` (also-make) so the reactor builds `rcln-reflex-telemetry` first.

| Test | Scope |
| ---- | ----- |
| `DocumentsByStatusRowMapperTest` | Unit: `rowMapper()` + `queryDefinition()` schema |
| `DocumentsByStatusMetricJdbcTest` | `@JdbcTest`: SQL + `documentsMetricsDataSource` |
| `PaymentsByStateMetricJdbcTest` | `@JdbcTest`: SQL + `paymentsMetricsDataSource` |
| `MetricsWiringTest` | `@SpringBootTest` + `metrics-it`: beans, `data-source-ref`, `query.schema`, ShedLock |
| `SampleMetricsApplicationTest` | Default test profile: `reflex.telemetry.enabled=false` |

Test classes use the `*Test` suffix so Maven Surefire picks them up in a plain `mvn test` run (`*IT` requires Failsafe).

Integration tests use profile `metrics-it` (`application-metrics-it.yml`): same bean names, `data-source-ref` and `query.schema` as production; H2 URLs and idle-friendly schedules. `@JdbcTest` slices use `MetricsJdbcQueryTestSupport` to supply `JdbcMetricQuerySettings` without starting the full scheduler.
