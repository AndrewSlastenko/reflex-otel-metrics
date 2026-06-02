# rcln-reflex-telemetry

`ru.sber.rcln:rcln-reflex-telemetry` — Spring Boot 3 starter для OpenTelemetry telemetry в Reflex-приложениях: JDBC polling metrics, manual metrics из кода приложения и tracing helpers.

Starter рассчитан на:

- Java 17
- Spring Boot 3.5.x
- Maven Wrapper (`.\mvnw.cmd`)
- OpenTelemetry 1.60.x
- OTLP/gRPC export
- fail-safe emission: ошибки метрик не ломают бизнес-код

## Что настраивает starter

Starter поднимает общую инфраструктуру:

- binding `ReflexTelemetryProperties` под префиксом `reflex.telemetry`
- `OpenTelemetry`, `Meter`, `Tracer`, `SdkMeterProvider`, `SdkTracerProvider`, если приложение не предоставило свои bean-ы
- OTLP metric exporter с настраиваемой temporality
- OTLP trace exporter
- `ReflexMetricFactory` для ручных метрик
- JDBC runtime для планового сбора метрик
- `TraceOperations` для span lifecycle и W3C propagation
- ограничение кардинальности серий и overflow policies
- histogram bucket views из YAML

Код приложения отвечает за call-sites: где вызвать manual metric, какой SQL выполнить для JDBC metric и как замаппить строки в `MetricPoint`.

## Сборка и тесты

```powershell
.\mvnw.cmd test
```

Точечный прогон:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryAutoConfigurationTest test
```

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
      endpoint: http://collector:4317
      export-timeout: 10s
    metrics:
      enabled: true
      endpoint: http://collector:4317
      export-interval: 1m
      temporality-preference: DELTA
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
      endpoint: http://collector:4317
      propagation: W3C
```

Если `metrics.endpoint` или `traces.endpoint` не заданы, используется общий `otlp.endpoint`.

### Основные блоки

| Property | Default | Назначение |
| -------- | ------- | ---------- |
| `reflex.telemetry.enabled` | `true` | Глобальный выключатель starter-а |
| `service.system-code` | empty | Префикс имен метрик |
| `service.name` | empty | `service.name` resource attribute |
| `service.instrumentation-scope-name` | `ru.sber.rcln.reflex.telemetry` | OTel scope для `Meter` и `Tracer` |
| `otlp.endpoint` | `http://localhost:4317` | Общий endpoint по умолчанию |
| `otlp.export-timeout` | `10s` | Timeout OTLP exporters |
| `metrics.enabled` | `true` | Выключатель метрик |
| `metrics.endpoint` | empty | Endpoint только для метрик |
| `metrics.export-interval` | `1m` | Период OTLP export |
| `metrics.temporality-preference` | `DELTA` | Temporality selector для OTLP metrics |
| `traces.enabled` | `true` | Выключатель tracing |
| `traces.endpoint` | empty | Endpoint только для traces |
| `traces.propagation` | `W3C` | Propagation mode |

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
| `schedule` | для JDBC | Расписание polling |
| `timeout` | для JDBC | Timeout одного запуска |
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

Starter создает `OtlpGrpcMetricExporter` внутри приложения и по умолчанию ставит delta temporality:

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

Настройка применяется к exporter-у, который создает эта библиотека. Если приложение само предоставляет `OpenTelemetry`, `SdkMeterProvider` или `OtlpGrpcMetricExporter`, библиотека отступает, и temporality нужно настроить в приложении или стандартной OTel autoconfigure.

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

- YAML definition: имя, kind, datasource, schedule, attributes, limits.
- Java `JdbcMetricSource`: `metricId`, SQL и `RowMapper<MetricPoint>`.

```java
package com.example.metrics;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
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
          attributes:
            required: [client, status]
          schedule:
            mode: FIXED_DELAY
            fixed-delay: 5m
            initial-delay: 30s
```

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

Ошибки логируются и emission пропускается. JDBC runtime также изолирует ошибку одного запуска и пишет internal telemetry.

Для manual metrics используйте `FAIL` или `TRUNCATE` как `overflow-policy`. `AGGREGATE_TO_OTHER` поддержан только в JDBC batch pipeline для non-histogram метрик.

## Debug checklist

1. Проверьте, что definition существует в `reflex.telemetry.metrics.definitions.<metric-id>`.
2. Проверьте `source` и `kind`: Java `factory.histogram("id")` требует `source: MANUAL` и `kind: HISTOGRAM`.
3. Проверьте итоговое имя: `<service.system-code>.<name>`.
4. Проверьте лог `Reflex telemetry OTLP metrics temporality preference: ...`.
5. Для histogram проверьте buckets в YAML и unit (`ms`, `s`, `{item}`).
6. На collector/debug exporter проверьте temporality и наличие нужных attributes.
