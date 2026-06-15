# rcln-reflex-telemetry

`ru.sber.rcln:rcln-reflex-telemetry` — Spring Boot 3 starter для OpenTelemetry telemetry в Reflex-приложениях: JDBC polling metrics, manual metrics из кода приложения и tracing helpers.

Starter рассчитан на:

- Java 17
- Spring Boot 3.5.x
- Maven Wrapper (`.\mvnw.cmd`)
- OpenTelemetry 1.60.x
- OTLP HTTP/protobuf export по умолчанию, OTLP/gRPC по настройке
- fail-safe emission: ошибки метрик не ломают бизнес-код

## Что настраивает starter

Starter поднимает общую инфраструктуру:

- binding `ReflexTelemetryProperties` под префиксом `reflex.telemetry`
- `OpenTelemetry`, `Meter`, `Tracer`, `SdkMeterProvider`, `SdkTracerProvider`, если приложение не предоставило конфликтующие bean-ы
- OTLP metric exporter с настраиваемой temporality и protocol
- OTLP trace exporter
- `ReflexMetricFactory` для ручных метрик
- JDBC runtime для планового сбора метрик и `JdbcMetricQuerySettings` для доступа из приклада к `query.*` (например, `query.schema`) из YAML
- `TraceOperations` для span lifecycle и W3C propagation
- ограничение кардинальности серий и overflow policies
- histogram bucket views из YAML

Код приложения отвечает за call-sites: где вызвать manual metric, какой SQL выполнить для JDBC metric и как замаппить строки в `MetricPoint`.

## Зависимости приложения

Базовое подключение starter-а достаточно для manual metrics и tracing helpers.

JDBC polling metrics требуют JDBC-инфраструктуру в приложении. Если приложение использует JDBC definitions или реализует `JdbcMetricSource`, в приложении должны быть доступны `spring-jdbc` и, при использовании распределённых lock-ов, ShedLock JDBC provider. Эти зависимости не подтягиваются транзитивно как обязательные, чтобы manual/tracing-потребители не получали лишние автоконфигурации и classpath side effects.

## Сборка и тесты

Репозиторий — Maven multi-module reactor:

| Модуль | Назначение |
| ------ | ---------- |
| `rcln-reflex-telemetry/` | Библиотека (starter JAR) |
| `examples/sample-metrics-app/` | Пример приклад-потребителя: несколько `DataSource`, ShedLock, JDBC metrics, тесты |

```powershell
.\mvnw.cmd test
```

Только библиотека:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry test
```

Пример приложения:

```powershell
.\mvnw.cmd -pl examples/sample-metrics-app test
```

Точечный прогон:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry -Dtest=ReflexTelemetryAutoConfigurationTest test
```

Подробности примера — в [examples/sample-metrics-app/README.md](examples/sample-metrics-app/README.md).

В тестах приложений-потребителей, где telemetry не является предметом проверки, рекомендуется явно выключать starter:

```properties
reflex.telemetry.enabled=false
```

Например, в `src/test/resources/application.properties` или в test profile. API-бины вроде `ReflexMetricFactory` и `TraceOperations` при этом остаются доступны, но работают в disabled/noop-режиме и не поднимают OTLP exporters.

## Конфигурация

Конфигурация YAML-first. Определения метрик находятся только в `reflex.telemetry.metrics.definitions.<metric-id>`.

```yaml
reflex:
  telemetry:
    enabled: true
    service:
      system-code: ci05414726
      name: contracts-api
      instrumentation-scope-name: ru.sber.rcln.reflex.telemetry
    otlp:
      protocol: http-protobuf
      endpoint: http://collector:4318
      export-timeout: 10s
    metrics:
      enabled: true
      export-interval: 1m
      temporality-preference: DELTA
      jdbc:
        enabled: true
      scopes:
        business:
          enabled: true
      definitions:
        transaction-send-duration:
          source: MANUAL
          kind: HISTOGRAM
          enabled: true
          name: transaction.send.duration
          scope: business
          description: Transaction send duration
          unit: s
          attributes:
            required: [target_system, result]
            optional: [http_status]
          histogram:
            buckets: [1, 2, 5, 10, 30, 60, 120, 300]
          max-series: 500
          overflow-policy: FAIL
        documents-by-status:
          source: JDBC
          kind: GAUGE
          enabled: true
          name: documents.by-status
          scope: business
          data-source-ref: businessReplicaDataSource
          query:
            schema: documents
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
            initial-delay: 30s
          timeout: 30s
          lock-at-most-for: 2m
          lock-at-least-for: 0s
          max-series: 500
          overflow-policy: AGGREGATE_TO_OTHER
    traces:
      enabled: true
      propagation: W3C
```

Если `metrics.endpoint` или `traces.endpoint` не заданы, используется общий `otlp.endpoint`.
Для `grpc` он используется как есть. Для `http-protobuf` к общему endpoint автоматически добавляются signal paths: `/v1/metrics` и `/v1/traces`.

### Основные блоки

| Property | Default | Назначение |
| -------- | ------- | ---------- |
| `reflex.telemetry.enabled` | `true` | Глобальный выключатель starter-а |
| `service.system-code` | empty | Префикс имен метрик |
| `service.name` | empty | `service.name` resource attribute |
| `service.instrumentation-scope-name` | `ru.sber.rcln.reflex.telemetry` | OTel scope для `Meter` и `Tracer` |
| `otlp.protocol` | `HTTP_PROTOBUF` | OTLP transport: `http-protobuf` или `grpc` |
| `otlp.endpoint` | `http://localhost:4318` | Общий endpoint по умолчанию |
| `otlp.export-timeout` | `10s` | Timeout OTLP exporters |
| `metrics.enabled` | `true` | Выключатель метрик |
| `metrics.endpoint` | empty | Endpoint только для метрик |
| `metrics.export-interval` | `1m` | Период OTLP export |
| `metrics.temporality-preference` | `DELTA` | Temporality selector для OTLP metrics |
| `metrics.jdbc.enabled` | `true` | Выключатель JDBC polling runtime |
| `metrics.jdbc.lock-provider-ref` | empty | Имя `LockProvider` bean-а для JDBC polling, если в контексте несколько ShedLock providers |
| `traces.enabled` | `true` | Выключатель tracing |
| `traces.endpoint` | empty | Endpoint только для traces |
| `traces.propagation` | `W3C` | Propagation mode |

## OTLP protocol

По умолчанию starter использует OTLP HTTP/protobuf:

```yaml
reflex:
  telemetry:
    otlp:
      protocol: http-protobuf
      endpoint: http://collector:4318
```

При общем `otlp.endpoint` starter сам отправит метрики на `http://collector:4318/v1/metrics`, а traces на `http://collector:4318/v1/traces`.

Если окружение явно поддерживает gRPC/HTTP2 до collector-а, можно переключить exporter на OTLP/gRPC:

```yaml
reflex:
  telemetry:
    otlp:
      protocol: grpc
      endpoint: http://collector:4317
```

Signal-specific endpoint-ы используются без изменения. Если задаете их отдельно, указывайте полный OTLP HTTP путь:

```yaml
reflex:
  telemetry:
    otlp:
      protocol: http-protobuf
    metrics:
      endpoint: http://collector:4318/v1/metrics
    traces:
      endpoint: http://collector:4318/v1/traces
```

### Metric definition

| Property | Обязательно | Назначение |
| -------- | ----------- | ---------- |
| `source` | да | `MANUAL` или `JDBC` |
| `kind` | да | `COUNTER`, `GAUGE`, `UP_DOWN_COUNTER`, `HISTOGRAM` |
| `name` | да | Имя метрики без платформенного префикса; итоговое имя будет `<system-code>.<name>` |
| `enabled` | нет | Выключатель конкретной метрики |
| `scope` | нет | Логическая группа для `metrics.scopes.<scope>.enabled` |
| `description` | нет | Description OTel instrument |
| `unit` | нет | Unit OTel instrument, например `s`, `ms`, `{request}` |
| `attributes.required` | нет | Обязательные атрибуты |
| `attributes.optional` | нет | Разрешенные необязательные атрибуты |
| `attributes.reject-unknown` | нет | По умолчанию `true` |
| `histogram.buckets` | для custom buckets | Explicit buckets только для `HISTOGRAM` |
| `data-source-ref` | для JDBC | Имя Spring `DataSource` bean-а |
| `query.schema` | нет (JDBC) | Имя БД-схемы. Читается приложением через `JdbcMetricQuerySettings#schema(metricId)` или `AbstractJdbcMetricSource`. Валидируется как простой SQL identifier `[A-Za-z_][A-Za-z0-9_]*`. На MANUAL запрещено. |
| `schedule` | для JDBC | Расписание polling |
| `timeout` | для JDBC | JDBC query timeout одного запуска |
| `lock-at-most-for` | для JDBC | ShedLock lock-at-most |
| `lock-at-least-for` | для JDBC | ShedLock lock-at-least |
| `max-series` | нет | Лимит кардинальности |
| `overflow-policy` | нет | `FAIL`, `TRUNCATE`, `AGGREGATE_TO_OTHER` |

`AGGREGATE_TO_OTHER` нельзя использовать для `MANUAL` source и для `HISTOGRAM`: manual emission не имеет batch-а overflow points, а histogram observations нельзя корректно склеить в synthetic `other` series без искажения распределения.

## Naming

`service.system-code` — единственный платформенный префикс для имен метрик.

```text
<system-code>.<name>
```

Пример:

```yaml
service:
  system-code: ci05414726
metrics:
  definitions:
    orders-created:
      name: orders.created
```

Итоговое имя метрики: `ci05414726.orders.created`.

`service.name` — это OTel resource attribute `service.name`. Он не участвует в имени метрики.

## Metric scopes

Scopes — логические группы Reflex, а не OpenTelemetry instrumentation scope.

```yaml
reflex:
  telemetry:
    metrics:
      scopes:
        business:
          enabled: false
```

Если scope выключен, все метрики этого scope становятся no-op или не планируются. Если `scope` у метрики не задан, default зависит от `source`: `manual` для `MANUAL`, `jdbc` для `JDBC`.

## Temporality

Starter создает metric exporter внутри приложения и по умолчанию ставит delta temporality:

```yaml
reflex:
  telemetry:
    metrics:
      temporality-preference: DELTA
```

Поддерживаемые значения:

- `DELTA` — значения за интервал экспорта; рекомендуемый режим для общего адреса нескольких collector-инстансов.
- `CUMULATIVE` — значения с начала жизни процесса; использовать только если downstream явно этого требует.
- `LOW_MEMORY` — low-memory selector OpenTelemetry SDK.

Настройка применяется к exporter-у, который создает эта библиотека. Если приложение само предоставляет `Meter`, `SdkMeterProvider` или `MetricExporter`, библиотека отступает от своей metrics pipeline, и temporality нужно настроить в приложении или стандартной OTel autoconfigure. Один только пользовательский `OpenTelemetry` bean не выключает metrics pipeline starter-а.

При старте логируется выбранный режим:

```text
Reflex telemetry OTLP metrics temporality preference: DELTA
```

Для проверки включите debug exporter в collector и проверьте OTLP payload: у counter/histogram должна быть `AGGREGATION_TEMPORALITY_DELTA`.

## Histogram buckets

Buckets настраиваются на уровне приложения в definition конкретной `HISTOGRAM`-метрики:

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        transaction-send-duration:
          source: MANUAL
          kind: HISTOGRAM
          name: transaction.send.duration
          unit: s
          histogram:
            buckets: [1, 2, 5, 10, 30, 60, 120, 300]
```

Это ответственность приложения, потому что только приклад знает масштаб измерения: миллисекунды, секунды или минуты. Collector обычно принимает уже агрегированную histogram data; он не обязан и часто не может восстановить нужные bucket boundaries после SDK.

Рекомендации:

- Для latency в миллисекундах используйте `unit: ms` и buckets вроде `[5, 10, 25, 50, 100, 250, 500, 1000]`.
- Для процессов в секундах/минутах используйте `unit: s` и buckets вроде `[1, 2, 5, 10, 30, 60, 120, 300]`.
- Не считайте P95 в коде приложения. Пишите отдельные observations через `HistogramMetric.record(...)`; P95, average, count и bucket rates считает backend по histogram stream.

## JDBC metrics

JDBC metric состоит из двух частей:

- YAML definition: имя, kind, datasource, schedule, attributes, limits, плюс опциональный блок `query` для параметров SQL (сейчас — только `schema`).
- Java `JdbcMetricSource`: `metricId`, SQL и `RowMapper<MetricPoint>`. Для параметров из YAML удобнее наследовать `AbstractJdbcMetricSource` и инжектить `JdbcMetricQuerySettings`: `metricId` задаётся один раз в конструкторе, `query.schema` читается из resolved definition при сборке SQL.

### Настройка JDBC DataSource и ShedLock в приложении

`data-source-ref` — это не JDBC URL. Это имя Spring bean-а типа `DataSource`, через который библиотека должна выполнить SQL конкретной JDBC-метрики. URL, credentials и pool-настройки задаются в конфигурации приложения обычным Spring Boot способом, а metric definition только ссылается на готовый bean.

В примерах ниже `app.metrics-datasources.documents` — это property prefix для настройки подключения, а не `metricId` и не имя metric bean-а. Имя `DataSource` bean-а задается в Java-конфигурации через `@Bean("documentsMetricsDataSource")`, и именно это имя указывается в `data-source-ref`.

Пример нескольких источников данных для метрик:

```yaml
app:
  metrics-datasources:
    documents:
      url: jdbc:postgresql://db-docs:5432/docs
      driver-class-name: org.postgresql.Driver
      hikari:
        pool-name: documents-metrics-pool
        maximum-pool-size: 3
        minimum-idle: 0
        connection-timeout: 10000           # 10s
        idle-timeout: 300000                # 5m
        max-lifetime: 1800000               # 30m
        keepalive-time: 60000               # 1m
        leak-detection-threshold: 60000     # 1m
        read-only: true
        auto-commit: true
    payments:
      url: jdbc:postgresql://db-payments:5432/payments
      driver-class-name: org.postgresql.Driver
      hikari:
        pool-name: payments-metrics-pool
        maximum-pool-size: 3
        minimum-idle: 0
        connection-timeout: 10000
        idle-timeout: 300000
        max-lifetime: 1800000
        keepalive-time: 60000
        leak-detection-threshold: 60000
        read-only: true
        auto-commit: true
    telemetry-lock:
      url: jdbc:postgresql://db-common:5432/telemetry
      driver-class-name: org.postgresql.Driver
      hikari:
        pool-name: telemetry-lock-pool
        maximum-pool-size: 2
        minimum-idle: 0
        connection-timeout: 5000            # 5s
        idle-timeout: 300000
        max-lifetime: 1800000
        read-only: false
        auto-commit: true
```

Hikari-значения биндятся напрямую на setter-ы `HikariDataSource`, которые принимают `long` в миллисекундах. Spring Boot `Duration` (`10s`, `30m`) при таком прямом биндинге не парсится — поэтому в YAML здесь стоят целые числа в ms. Стандартные `spring.datasource.hikari.*` поддерживают `Duration` через специальную обёртку, но при изолированных metric-DataSource-ах вы получаете bean напрямую.

Параметры выше задают именно те оси, которые важны для metric-pipeline:

- `pool-name` — попадает в логи Hikari и в имена JMX/observability метрик. Разные имена для documents/payments/lock делают видимым, какой пул реально нагружен.
- `maximum-pool-size` / `minimum-idle` — метрики не должны конкурировать с бизнес-нагрузкой; держите пулы маленькими, `minimum-idle: 0` отдаёт connection-ы обратно в БД, когда метрика молчит.
- `connection-timeout` — ожидание свободного connection в пуле, не связано с `JdbcTemplate` query timeout. Query timeout настраивается отдельно через `reflex.telemetry.metrics.definitions.<id>.timeout`.
- `idle-timeout` / `max-lifetime` / `keepalive-time` — против stale connection (например, если PgBouncer/балансер режет idle через какое-то время).
- `leak-detection-threshold` — на metric-DS полезно: SQL метрик короткий, любое долгое удержание подключения — повод посмотреть на источник.
- `read-only: true` для metric-DS, если пользователь действительно read-only (и роли в БД это поддерживают). `read-only: false` для lock-DS, потому что ShedLock делает INSERT/UPDATE в `shedlock`.

```java
package com.example.metrics;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.metrics-datasources.documents")
    DataSourceProperties documentsMetricsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("documentsMetricsDataSource")
    @ConfigurationProperties("app.metrics-datasources.documents.hikari")
    DataSource documentsMetricsDataSource(
            @Qualifier("documentsMetricsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.metrics-datasources.payments")
    DataSourceProperties paymentsMetricsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("paymentsMetricsDataSource")
    @ConfigurationProperties("app.metrics-datasources.payments.hikari")
    DataSource paymentsMetricsDataSource(
            @Qualifier("paymentsMetricsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.metrics-datasources.telemetry-lock")
    DataSourceProperties telemetryLockDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("telemetryLockDataSource")
    @ConfigurationProperties("app.metrics-datasources.telemetry-lock.hikari")
    DataSource telemetryLockDataSource(
            @Qualifier("telemetryLockDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
```

После этого JDBC metric definitions ссылаются на имена этих bean-ов:

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        documents-by-status:
          source: JDBC
          kind: GAUGE
          name: documents.by-status
          data-source-ref: documentsMetricsDataSource
          query:
            schema: documents
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
        payments-by-state:
          source: JDBC
          kind: GAUGE
          name: payments.by-state
          data-source-ref: paymentsMetricsDataSource
          query:
            schema: payments
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
```

Несколько JDBC-метрик могут использовать один и тот же `DataSource` bean, если они читают из одной базы:

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        documents-by-status:
          source: JDBC
          kind: GAUGE
          name: documents.by-status
          data-source-ref: documentsMetricsDataSource
          query:
            schema: documents
        documents-by-type:
          source: JDBC
          kind: GAUGE
          name: documents.by-type
          data-source-ref: documentsMetricsDataSource
          query:
            schema: documents
```

Если метрики читаются из той же бизнесовой базы, что и приложение, есть два рабочих варианта:

- использовать уже существующий business `DataSource` bean и указать его имя в `data-source-ref`;
- завести отдельный metrics `DataSource` на тот же JDBC URL, но с отдельным маленьким пулом и, по возможности, read-only пользователем.

Отдельный metrics `DataSource` обычно предпочтительнее для production: JDBC polling не конкурирует с бизнесовыми запросами за один и тот же Hikari pool, а лимиты пула, credentials и права доступа можно настроить отдельно. Цена такого решения — дополнительные подключения к той же базе, поэтому pool для metrics обычно держат небольшим.

Для JDBC-метрики `timeout` применяется как `JdbcTemplate` query timeout. Значение задается на metric definition, например `timeout: 30s`; если указаны миллисекунды, они округляются вверх до секунд, потому что JDBC query timeout работает в секундах. Ожидание свободного connection в пуле регулируется отдельно настройкой Hikari `connection-timeout` на соответствующем `DataSource`.

JDBC runtime включается только если на classpath есть `spring-jdbc`, в контексте есть хотя бы один `JdbcMetricSource`, метрики включены глобально и `reflex.telemetry.metrics.jdbc.enabled=true`. Starter не создает `DataSource`: он берет уже готовый Spring bean по имени из `data-source-ref`.

ShedLock используется только если приложение уже предоставило `LockProvider`. Если `LockProvider` нет, JDBC polling выполняется локально без распределенного lock-а. Если `LockProvider` несколько, задайте `reflex.telemetry.metrics.jdbc.lock-provider-ref` или объявите свой `MetricLockManager`, иначе starter не будет угадывать нужный provider.

Если credentials приходят из Secman/Vault, не храните username/password в `application.yml` или `application-reflex.yml`. Разделите конфигурацию на не-секретную topology и secret properties:

- `application-reflex.yml`: какие metric DataSource-ы нужны, их URL, pool-настройки, `reflex.telemetry.metrics.definitions.*`.
- Secman/Vault-generated `.properties`: username/password для тех же property prefixes.

Например, не-секретный файл `application-reflex.yml`:

```yaml
app:
  metrics-datasources:
    documents:
      url: jdbc:postgresql://db-docs:5432/docs
      driver-class-name: org.postgresql.Driver
      hikari:
        pool-name: documents-metrics-pool
        maximum-pool-size: 3
        minimum-idle: 0
        connection-timeout: 10000           # ms
        idle-timeout: 300000
        max-lifetime: 1800000
        keepalive-time: 60000
        leak-detection-threshold: 60000
        read-only: true
        auto-commit: true
    payments:
      url: jdbc:postgresql://db-payments:5432/payments
      driver-class-name: org.postgresql.Driver
      hikari:
        pool-name: payments-metrics-pool
        maximum-pool-size: 3
        minimum-idle: 0
        connection-timeout: 10000
        idle-timeout: 300000
        max-lifetime: 1800000
        keepalive-time: 60000
        leak-detection-threshold: 60000
        read-only: true
        auto-commit: true
    telemetry-lock:
      url: jdbc:postgresql://db-common:5432/telemetry
      driver-class-name: org.postgresql.Driver
      hikari:
        pool-name: telemetry-lock-pool
        maximum-pool-size: 2
        minimum-idle: 0
        connection-timeout: 5000
        idle-timeout: 300000
        max-lifetime: 1800000
        read-only: false
        auto-commit: true

reflex:
  telemetry:
    metrics:
      definitions:
        documents-by-status:
          source: JDBC
          kind: GAUGE
          name: documents.by-status
          data-source-ref: documentsMetricsDataSource
          query:
            schema: documents
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
        payments-by-state:
          source: JDBC
          kind: GAUGE
          name: payments.by-state
          data-source-ref: paymentsMetricsDataSource
          query:
            schema: payments
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
```

Secman/Vault template может сгенерировать отдельный mounted файл, например `/mnt/secrets/reflex-metrics-secrets.properties`:

```properties
app.metrics-datasources.documents.username=${DOCUMENTS_DB_USER}
app.metrics-datasources.documents.password=${DOCUMENTS_DB_PASSWORD}
app.metrics-datasources.payments.username=${PAYMENTS_DB_USER}
app.metrics-datasources.payments.password=${PAYMENTS_DB_PASSWORD}
app.metrics-datasources.telemetry-lock.username=${TELEMETRY_LOCK_DB_USER}
app.metrics-datasources.telemetry-lock.password=${TELEMETRY_LOCK_DB_PASSWORD}
```

Отдельный файл не обязателен. Если в приложении уже монтируется общий workflow secret properties, можно добавить эти строки туда же рядом с существующими `spring.datasource.*.username/password`:

```properties
spring.datasource.username=...
spring.datasource.password=...
spring.datasource.primary.username=...
spring.datasource.primary.password=...

app.metrics-datasources.documents.username=...
app.metrics-datasources.documents.password=...
app.metrics-datasources.payments.username=...
app.metrics-datasources.payments.password=...
app.metrics-datasources.telemetry-lock.username=...
app.metrics-datasources.telemetry-lock.password=...
```

В реальном Vault Agent template вместо `${...}` или `...` будут значения из `secret .Data`, как в существующих `spring.datasource.*.username/password` templates. Важно, чтобы ключи совпадали с prefixes, на которые подписаны `DataSourceProperties`: `app.metrics-datasources.documents.*`, `app.metrics-datasources.payments.*`, `app.metrics-datasources.telemetry-lock.*`.

Подключить mounted файлы можно через Config Data import в основном `application.yml`:

```yaml
spring:
  config:
    import:
      - optional:file:/mnt/config/application-reflex.yml
      - optional:file:/mnt/secrets/reflex-metrics-secrets.properties
```

Альтернатива для Kubernetes deployment — передать external locations через environment/args, не меняя основной `application.yml`:

```yaml
env:
  - name: SPRING_CONFIG_ADDITIONAL_LOCATION
    value: optional:file:/mnt/config/application-reflex.yml,optional:file:/mnt/secrets/reflex-metrics-secrets.properties
```

Для нескольких баз правило одинаковое: на каждый источник метрик заводится свой prefix, свой `DataSourceProperties` bean и свой `DataSource` bean; в metric definition указывается только имя нужного `DataSource` bean-а. Lock DataSource настраивается отдельно и должен быть общим для всех pod-ов/плеч, которые должны видеть один и тот же ShedLock.

ShedLock использует отдельный `LockProvider`. Его DataSource может совпадать с одним из metric DataSource-ов, но в production обычно удобнее держать lock-таблицу в общей технической БД, доступной всем pod-ам/плечам, которые конкурируют за запуск одной и той же метрики.

```java
package com.example.metrics;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.ShedLockMetricLockManager;

@Configuration(proxyBeanMethods = false)
public class MetricsLockConfig {

    @Bean
    LockProvider lockProvider(@Qualifier("telemetryLockDataSource") DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("telemetry.shedlock")
                        .usingDbTime()
                        .build());
    }

    @Bean
    MetricLockManager metricLockManager(LockProvider lockProvider) {
        return new ShedLockMetricLockManager(lockProvider);
    }
}
```

Таблица ShedLock создается миграцией приложения, сам ShedLock ее не разворачивает:

```sql
CREATE TABLE telemetry.shedlock (
    name VARCHAR(255) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

Для JDBC-метрик lock name формируется как `reflex-otel-metric:<metric-id>`. На одну метрику создается одна строка, а при следующих запусках ShedLock обновляет `lock_until`, `locked_at` и `locked_by`. Если другой pod видит, что `lock_until` еще в будущем, он не выполняет сбор этой метрики. Не удаляйте строки из `shedlock` вручную во время работы приложения: ShedLock кэширует известные lock-и в памяти.

Проверочный список для приложения:

1. В classpath есть `spring-jdbc`.
2. Для распределенных запусков есть `shedlock-provider-jdbc-template`.
3. Все `data-source-ref` указывают на реальные `DataSource` bean-ы.
4. Есть bean `LockProvider`.
5. Есть bean `MetricLockManager`.
6. Таблица `shedlock` создана в общей lock-БД.
7. После старта появляются строки вида `reflex-otel-metric:documents-by-status`.
8. Все pod-ы/плечи, которые должны конкурировать, используют одну и ту же lock-таблицу.

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.jdbc.AbstractJdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DocumentsByStatusMetricSource extends AbstractJdbcMetricSource {

    public DocumentsByStatusMetricSource(JdbcMetricQuerySettings querySettings) {
        super("documents-by-status", querySettings);
    }

    @Override
    protected QueryDefinition buildQuery(String schema) {
        return new QueryDefinition("""
                select client_code, document_status, count(*) as value
                from %s.transaction_view
                group by client_code, document_status
                """.formatted(schema));
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

Соответствующий YAML:

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        documents-by-status:
          source: JDBC
          kind: GAUGE
          name: documents.by-status
          data-source-ref: businessReplicaDataSource
          query:
            schema: documents
          attributes:
            required: [client, status]
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
            initial-delay: 30s
```

`metricId` в Java и ключ definition в YAML должны совпадать. Схема задаётся только в YAML; источник читает её через `JdbcMetricQuerySettings`, без дублирования id в сообщениях об ошибках.

Если SQL не зависит от внешней схемы, можно реализовать `JdbcMetricSource` напрямую и не задавать `query.schema`.

Для JDBC histogram возвращайте `MetricPoint.histogram(...)`:

```java
@Override
public RowMapper<MetricPoint> rowMapper() {
    return (rs, rowNum) -> MetricPoint.histogram(
            rs.getDouble("duration_seconds"),
            Map.of("target_system", rs.getString("target_system")));
}
```

## Manual metrics

Manual metric объявляется в YAML и создается в Java по `metricId`.

`ReflexMetricFactory` валидирует definition при создании metric handle. Отсутствующий `metricId`, несовпадение `source`/`kind` или некорректная конфигурация считаются ошибкой конфигурации и могут сломать старт приложения.

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        orders-created:
          source: MANUAL
          kind: COUNTER
          name: orders.created
          description: Orders created by client and channel
          unit: "{order}"
          attributes:
            required: [client, channel]
          max-series: 500
          overflow-policy: FAIL
```

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final CounterMetric ordersCreated;

    public OrderMetrics(ReflexMetricFactory factory) {
        this.ordersCreated = factory.counter("orders-created");
    }

    public void created(String client, String channel) {
        ordersCreated.increment(Map.of(
                "client", client,
                "channel", channel));
    }
}
```

Для duration после отправки во внешнюю систему используйте manual histogram:

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        transaction-send-duration:
          source: MANUAL
          kind: HISTOGRAM
          name: transaction.send.duration
          description: Duration from transaction creation to external system response
          unit: s
          attributes:
            required: [target_system, result]
            optional: [http_status]
          histogram:
            buckets: [1, 2, 5, 10, 30, 60, 120, 300]
```

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.HistogramMetric;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TransactionMetrics {

    private final HistogramMetric sendDuration;

    public TransactionMetrics(ReflexMetricFactory factory) {
        this.sendDuration = factory.histogram("transaction-send-duration");
    }

    public void sent(String targetSystem, Instant createdAt, int httpStatus) {
        double seconds = Duration.between(createdAt, Instant.now()).toMillis() / 1000.0d;
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("target_system", targetSystem);
        attributes.put("result", httpStatus >= 200 && httpStatus < 300 ? "success" : "fail");
        attributes.put("http_status", Integer.toString(httpStatus));
        sendDuration.record(seconds, attributes);
    }
}
```

На уровне приложения не нужно считать P95, percentile или average. Код пишет observation (`duration`), SDK экспортирует histogram, Dynatrace/Reflex считает percentile и средние по bucket data.

## Как выбирать kind

| Kind | Когда использовать |
| ---- | ------------------ |
| `COUNTER` | События и неотрицательные increments: создано, отправлено, ошибка |
| `GAUGE` | Snapshot текущего состояния: очередь сейчас, строк сейчас |
| `UP_DOWN_COUNTER` | Delta, которая может быть положительной или отрицательной |
| `HISTOGRAM` | Распределения: duration, latency, size |

Не используйте `UP_DOWN_COUNTER` для `select count(*) ...`, если SQL возвращает полный snapshot. Для snapshot нужен `GAUGE`.

## Trace operations

`TraceOperations` можно инжектить как обычный bean. Span-ы экспортируются, когда включены `reflex.telemetry.enabled` и `reflex.telemetry.traces.enabled`.

```yaml
reflex:
  telemetry:
    traces:
      enabled: true
      endpoint: http://collector:4317
      propagation: W3C
```

```java
import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import java.util.Map;

TraceCarrier parent = new TraceCarrier(traceparent, tracestate);

traces.inSpan(
        new SpanSpec(
                "workflow.action.GetContractAction",
                parent,
                Map.of(
                        "workflow.process.name", processName,
                        "workflow.action.name", "GetContractAction")),
        () -> action.execute(context));
```

Propagation правила:

- храните `traceparent` и `tracestate` как opaque strings;
- не храните span objects в базе;
- не парсите `tracestate`;
- вызывайте `captureCurrent()` только внутри активного `inSpan(...)`;
- передавайте carrier в следующий процесс, queue headers, HTTP headers или другой transport.

## Интеграция Spring-библиотек

Библиотекам лучше держать собственный domain interface и опциональную Reflex-backed реализацию. Код библиотеки вызывает свой interface, а не `ReflexMetricFactory` напрямую.

```java
package com.example.library;

public interface DocumentLibraryMetrics {

    void syncStarted(String source);
}
```

```java
package com.example.library;

import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import java.util.Map;

public final class ReflexDocumentLibraryMetrics implements DocumentLibraryMetrics {

    private final CounterMetric syncStarted;

    public ReflexDocumentLibraryMetrics(ReflexMetricFactory factory) {
        this.syncStarted = factory.counter("document-library-sync-started");
    }

    @Override
    public void syncStarted(String source) {
        syncStarted.increment(Map.of("source", source));
    }
}
```

Приложение объявляет definition:

```yaml
reflex:
  telemetry:
    metrics:
      definitions:
        document-library-sync-started:
          source: MANUAL
          kind: COUNTER
          name: document.library.sync.started
          scope: document-library
          attributes:
            required: [source]
```

Автоконфигурация библиотеки должна создавать Reflex-backed bean только при наличии `ReflexMetricFactory`, а иначе давать no-op fallback.

## Fail-safe behavior

Manual metrics не бросают ошибки в бизнес-код при:

- выключенной метрике;
- неверных attributes;
- превышении `max-series`;
- ошибке OpenTelemetry instrument.

Fail-safe поведение применяется к публикации уже созданной метрики. Ошибки логируются и emission пропускается. JDBC runtime также изолирует ошибку одного запуска и пишет internal telemetry.

Для manual metrics используйте `FAIL` или `TRUNCATE` как `overflow-policy`. `AGGREGATE_TO_OTHER` поддержан только в JDBC batch pipeline для non-histogram метрик.

## Debug checklist

1. Проверьте, что definition существует в `reflex.telemetry.metrics.definitions.<metric-id>`.
2. Проверьте `source` и `kind`: Java `factory.histogram("id")` требует `source: MANUAL` и `kind: HISTOGRAM`.
3. Проверьте итоговое имя: `<service.system-code>.<name>`.
4. Проверьте `reflex.telemetry.otlp.protocol`: дефолт `http-protobuf`; `grpc` включайте явно только если весь маршрут поддерживает gRPC/HTTP2.
5. Проверьте endpoint: для `http-protobuf` общий `otlp.endpoint` дополняется `/v1/metrics`, а `metrics.endpoint` должен быть полным URL.
6. Проверьте лог `Reflex telemetry OTLP metrics temporality preference: ...`.
7. Для histogram проверьте buckets в YAML и unit (`ms`, `s`, `{item}`).
8. На collector/debug exporter проверьте temporality и наличие нужных attributes.
