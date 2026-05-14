# rcln-reflex-telemetry

`ru.sber.rcln:rcln-reflex-telemetry` — Spring Boot starter для экспорта OpenTelemetry-метрик, собираемых из JDBC.

Текущий starter рассчитан на:

- Java 17
- Spring Boot 3
- Maven Wrapper (`.\mvnw.cmd`)
- OpenTelemetry `1.60.1`
- экспорт метрик по OTLP/gRPC
- агрегированную техническую телеметрию
- fail-safe выполнение

## За что отвечает starter

Starter автоматически настраивает общую инфраструктуру телеметрии:

- binding `ReflexTelemetryProperties` под префиксом `reflex.telemetry`
- exporter метрик OTLP/gRPC
- exporter трейсов OTLP/gRPC
- `TraceOperations` для общего жизненного цикла span-ов и W3C propagation helpers (no-op, когда `reflex.telemetry.enabled` или `reflex.telemetry.traces.enabled` равны `false`, либо когда нет bean-а `Tracer`)
- OTel bean-ы `OpenTelemetry`, `Meter`, `Tracer` и registry инструментов
- helpers для резолва и валидации конфигурации
- поддержку ограничения количества серий
- hooks агрегированной технической телеметрии
- fail-safe defaults для инфраструктуры, которой управляет starter

Код приложения должен предоставлять bean-ы источников метрик и JDBC-маппинг.

## Сборка и тесты

Запустить полный набор тестов из корня репозитория:

```powershell
.\mvnw.cmd test
```

Запустить только тесты автоконфигурации starter-а:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryAutoConfigurationTest test
```

## Контракт конфигурации

Starter читает свойства из `reflex.telemetry`.

Глобальные свойства:

```yaml
reflex:
  telemetry:
    enabled: true
    system-code: ci05414726
    service-name: contracts-api
    instrumentation-scope-name: ru.sber.rcln.reflex.telemetry
    otlp:
      metrics-endpoint: http://localhost:4317
      traces-endpoint: http://localhost:4317
      export-timeout: 10s
      export-interval: 1m
    traces:
      enabled: true
    metrics:
      enabled: true
      scopes:
        jdbc:
          enabled: true
        manual:
          enabled: true
```

Runtime overrides для отдельных источников находятся в `reflex.telemetry.metrics.sources.<metric-id>`:

```yaml
reflex:
  telemetry:
    metrics:
      sources:
        documents-by-status:
          enabled: true
          suffix: documents.current
          scope: jdbc
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

Эти ключи напрямую соответствуют текущей модели `ReflexTelemetryProperties` и `MetricRuntimeProperties`:

- `reflex.telemetry.enabled`
- `system-code`
- `service-name`
- `instrumentation-scope-name`
- `otlp.metrics-endpoint`
- `otlp.traces-endpoint`
- `otlp.export-timeout`
- `otlp.export-interval`
- `reflex.telemetry.traces.enabled`
- `metrics.enabled`
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

Runtime-конфигурация резолвится в таком порядке:

1. defaults starter-а
2. defaults, возвращенные bean-ом источника метрик
3. overrides из `application.yml` или `application.properties`

## Service name

`service-name` управляет OpenTelemetry resource attribute `service.name` для SDK, который создает starter.

```yaml
reflex:
  telemetry:
    service-name: contracts-api
```

Это имя приклада/сервиса в backend-е трассировки и метрик. Если приложение само предоставляет `OpenTelemetry`, `OpenTelemetrySdk`, `SdkTracerProvider` или `SdkMeterProvider`, starter не переопределяет resource — в этом случае `service.name` задается на стороне приложения.

## System code and naming

`reflex.telemetry.system-code` is the single source of truth for platform prefixes.

For metrics, the starter exports names as:

```text
<system-code>.<metric-suffix>
```

For OpenTelemetry resource identity, the starter exports `service.name` as:

```text
<system-code>_<service-name>
```

Application configuration should keep `service-name` unprefixed. The starter prevents double-prefixing when a value is already prefixed.

## Metric scopes

Metric scopes are Reflex logical groups for enabling or disabling sets of metrics. They are not OpenTelemetry instrumentation scopes.

The starter owns these default scopes:

| Scope | Applies to |
| ----- | ---------- |
| `jdbc` | Reflex JDBC polling metrics |
| `manual` | Metrics created through `ReflexMetricFactory` |

Use `reflex.telemetry.metrics.scopes.<scope>.enabled` to disable a group. A metric can still override scope explicitly through its Java definition or runtime YAML override when a narrower deployment group is needed.

## Частота экспорта

Сбор метрик и OTLP-экспорт — разные шаги.

1. query источника метрик читает значения из базы по своему расписанию
2. starter записывает эти значения в OpenTelemetry instruments
3. OpenTelemetry SDK экспортирует накопленные данные по своему периодическому циклу

По умолчанию starter экспортирует раз в минуту:

```yaml
reflex:
  telemetry:
    otlp:
      export-interval: 1m
```

Используйте это, чтобы не экспортировать слишком часто, когда polling базы происходит чаще, чем нужно downstream-потребителям.

## Instrumentation Scope

`instrumentation-scope-name` управляет OpenTelemetry instrumentation scope, который используется и для `Meter`, и для `Tracer`.

```yaml
reflex:
  telemetry:
    instrumentation-scope-name: com.example.business-metrics
```

Это не меняет имя самой метрики. Имена метрик формируются как `<system-code>.<suffix>`: префикс задаёт `reflex.telemetry.system-code`, суффикс — политика именования метрики (definition/YAML). OpenTelemetry instrumentation scope здесь не совпадает с логическим Reflex metric scope (`jdbc`, `manual` и опциональные переопределения на метрике).
Значение `instrumentation-scope-name` показывает, какая библиотека или модуль выпустили телеметрию.
Это не `service.name`: имя сервиса задается отдельным свойством `reflex.telemetry.service-name`.

## Trace operations

`TraceOperations` можно инжектить как любой другой bean starter-а. Используйте `SpanSpec` для имени span-а, опционального parent `TraceCarrier` и строковых атрибутов. Span-ы экспортируются, когда глобальная телеметрия и трассировка включены, а OTLP traces endpoint настроен (см. `reflex.telemetry.otlp.traces-endpoint` выше).

Имена в примере ниже — placeholders для типов приложения и workflow-слоя:

```java
import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import java.util.Map;

// TraceCarrier parent = read from incoming context (opaque traceparent/tracestate strings)
TraceCarrier parent = new TraceCarrier(traceparent, tracestate);

traces.inSpan(
        new SpanSpec(
                "workflow.action.GetContractAction",
                parent,
                Map.of(
                        "workflow.process.name", processName,
                        "workflow.action.name", "GetContractAction",
                        "workflow.action.class", GetContractAction.class.getName(),
                        "workflow.business_service_id", businessServiceId)),
        () -> action.execute(context));
```

### Правила propagation

- Храните `traceparent` и `tracestate` как opaque strings.
- Не храните span-ы в базе данных.
- Не парсите `tracestate` в коде приложения.
- Вызывайте `captureCurrent()` только внутри активного `inSpan(...)`.
- Передавайте captured carrier в контекст следующего процесса, queue headers, HTTP headers или другой transport.
- Используйте span attributes для бизнес-идентификаторов и workflow names; используйте `traceparent` через carrier для связки trace-ов.

### Многошаговые workflow

Типовой паттерн маппит execution contexts так:

- **inParams** — initial trace carrier от предыдущего процесса.
- **params** — текущий runtime trace carrier, обновляемый между actions.
- **outParams** — trace carrier, передаваемый в следующий процесс.

При fan-out в другой процесс запишите строки из `captureCurrent()` в его context. Рекомендуемые ключи:

```text
_otel.traceparent
_otel.tracestate
```

## Как ведут себя виды метрик

Текущий starter поддерживает `GAUGE` и `UP_DOWN_COUNTER`. Они по-разному ведут себя между database polls и OTLP exports.

### `GAUGE`

Используйте `GAUGE` для snapshot-ов вроде "сколько строк существует прямо сейчас".

Пример:

- в `10:00:00` query возвращает `42`
- в `10:00:30` query возвращает `45`
- в `10:01:00` SDK экспортирует `45`

Для каждого набора атрибутов побеждает последнее увиденное значение.

### `UP_DOWN_COUNTER`

Используйте `UP_DOWN_COUNTER` только тогда, когда каждый collection run возвращает delta, которую нужно добавить к предыдущему состоянию.

Пример:

- в `10:00:00` query возвращает `+5`
- в `10:00:30` query возвращает `-2`
- в `10:01:00` SDK экспортирует накопленное изменение за интервал

Не используйте `UP_DOWN_COUNTER` для полных snapshot-ов таблицы вроде `select count(*) ...`, иначе каждый poll снова добавит весь snapshot, и экспортируемое значение будет некорректно расти или снижаться.

## Контракт источника метрик

Каждый источник метрик — Spring bean со стабильным `metricId()` и code-level defaults.

Для JDBC-метрик реализуйте `JdbcMetricSource`:

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
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
                "jdbc",
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

В примере выше оператор может переопределить только deploy-time значения, если это нужно:

```properties
reflex.telemetry.system-code=ci054147
reflex.telemetry.metrics.sources.documents-by-status.suffix=documents.current
reflex.telemetry.metrics.sources.documents-by-status.fixed-delay=PT2M
```

## Manual Metric Beans

JDBC-метрики собираются по расписанию, которым управляет starter: starter запускает query источника, маппит rows в points и публикует их в OpenTelemetry. Manual metrics эмитятся напрямую кодом приложения в момент бизнес-события или изменения состояния.

Для manual metrics Java bean declaration — основной контракт. Bean определяет metric id, kind, suffix, scope (по умолчанию логический scope `manual`), description, unit, attribute schema, cardinality limit и overflow policy. YAML — опциональный runtime override layer для deploy-time значений: включение метрики, изменение suffix или scope, настройка cardinality handling.

Низкоуровневые metric beans удобны, когда сервису нужен один instrument:

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
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

Для больших flow предпочитайте domain metric bean, который группирует низкоуровневые instruments и предоставляет business-specific методы:

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.GaugeMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
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
                        .description("Orders created by client and channel")
                        .unit("{order}")
                        .attributes(clientChannelAttributes)
                        .maxSeries(500)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());
        this.failed = factory.counter(
                "orders-failed",
                MetricDefinition.of("orders.failed")
                        .description("Orders failed by client and channel")
                        .unit("{order}")
                        .attributes(clientChannelAttributes)
                        .maxSeries(500)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());
        this.queueSize = factory.gauge(
                "orders-queue-size",
                MetricDefinition.of("orders.queue.size")
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

## Интеграция Spring-библиотек

Spring-библиотеки могут публиковать свои manual metrics без запуска отдельного telemetry runtime. Библиотека должна держать метрики за domain interface, иметь no-op fallback и опционально биндинговать этот interface к `ReflexMetricFactory`, когда приложение включило этот starter.

Код библиотеки вызывает собственный interface, а не OpenTelemetry или `ReflexMetricFactory` напрямую:

```java
package com.example.library;

public interface DocumentLibraryMetrics {

    void syncStarted(String source);
}

final class NoopDocumentLibraryMetrics implements DocumentLibraryMetrics {

    @Override
    public void syncStarted(String source) {
    }
}
```

Reflex-backed реализация может жить в том же library artifact:

```java
package com.example.library;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import java.util.Map;

public final class ReflexDocumentLibraryMetrics implements DocumentLibraryMetrics {

    private final CounterMetric syncStarted;

    public ReflexDocumentLibraryMetrics(ReflexMetricFactory factory) {
        this.syncStarted = factory.counter(
                "document-library-sync-started",
                MetricDefinition.of("document.library.sync.started")
                        .scope("document-library")
                        .description("Started document library sync operations")
                        .unit("1")
                        .attributes(AttributesSchema.builder()
                                .required("source")
                                .build())
                        .build());
    }

    @Override
    public void syncStarted(String source) {
        syncStarted.increment(Map.of("source", source));
    }
}
```

Автоконфигурация библиотеки должна создавать Reflex-backed bean только тогда, когда application context уже содержит runtime этого starter-а:

```java
package com.example.library.autoconfigure;

import com.example.library.DocumentLibraryMetrics;
import com.example.library.ReflexDocumentLibraryMetrics;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ReflexMetricFactory.class)
@ConditionalOnBean(ReflexMetricFactory.class)
class DocumentLibraryTelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentLibraryMetrics documentLibraryMetrics(ReflexMetricFactory factory) {
        return new ReflexDocumentLibraryMetrics(factory);
    }
}

@AutoConfiguration
@AutoConfigureAfter(DocumentLibraryTelemetryAutoConfiguration.class)
class DocumentLibraryFallbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentLibraryMetrics documentLibraryMetrics() {
        return source -> {
        };
    }
}
```

Зарегистрируйте обе автоконфигурации в библиотечном `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```text
com.example.library.autoconfigure.DocumentLibraryTelemetryAutoConfiguration
com.example.library.autoconfigure.DocumentLibraryFallbackAutoConfiguration
```

Если библиотека включает compile-time dependency на этот starter только для опциональной интеграции, объявляйте ее как optional, чтобы consumers не получали telemetry runtime транзитивно:

```xml
<dependency>
    <groupId>ru.sber.rcln</groupId>
    <artifactId>rcln-reflex-telemetry</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <optional>true</optional>
</dependency>
```

Приложение-потребитель явно добавляет `rcln-reflex-telemetry`, когда хочет экспортировать метрики. В этом случае по-прежнему существует только один application `ApplicationContext`, одна `ReflexTelemetryAutoConfiguration` и один OpenTelemetry runtime. Автоконфигурации библиотек только создают свои domain metric beans и используют application `ReflexMetricFactory`.

Operational overrides используют тот же раздел manual metrics:

```yaml
reflex:
  telemetry:
    metrics:
      manual:
        document-library-sync-started:
          enabled: true
          max-series: 100
          overflow-policy: FAIL
```

Runtime overrides для manual metrics находятся в `reflex.telemetry.metrics.manual.<metric-id>`:

```yaml
reflex:
  telemetry:
    metrics:
      manual:
        orders-created:
          enabled: true
          suffix: orders.created
          max-series: 500
          overflow-policy: FAIL
```

Вызовы manual metrics fail-safe для бизнес-кода. Disabled metrics возвращаются без публикации. Invalid attributes, cardinality overflow и OpenTelemetry runtime errors логируются и пропускаются реализацией метрики, а не ломают application flow.

`AGGREGATE_TO_OTHER` не поддерживается для manual metrics в v1, потому что manual emission не имеет batch-а overflow points, которые можно агрегировать. Используйте `FAIL`, чтобы пропускать новые series сверх лимита, или `TRUNCATE`, чтобы прекратить принимать дополнительные series после достижения лимита.
