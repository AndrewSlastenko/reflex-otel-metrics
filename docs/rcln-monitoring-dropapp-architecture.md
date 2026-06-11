# Архитектура rcln-monitoring с rcln-reflex-telemetry в DropApp/Kubernetes

Документ описывает целевое размещение приклада `rcln-monitoring`, который подключает библиотеку `rcln-reflex-telemetry` и запускает плановый JDBC-сбор метрик из БД. Приклад развернут в двух плечах DropApp/Kubernetes. Для каждой JDBC-метрики используется глобальная координация через общую таблицу ShedLock: в один момент времени конкретный `metric-id` выполняет только один pod из обоих плеч.

## Термины на диаграммах

| Термин | Значение |
| ------ | -------- |
| `rcln-monitoring` | Spring Boot приклад-потребитель библиотеки. |
| `rcln-reflex-telemetry` | Spring Boot starter внутри процесса `rcln-monitoring`: автоконфигурация, планировщик, JDBC-сборщик, OTel-публикатор. |
| `БД-источники метрик` | БД, к которым библиотека ходит JDBC-запросами на чтение через `data-source-ref`. |
| `Общая БД ShedLock` | Общая техническая БД/схема с таблицей `telemetry.shedlock`, доступная pod-ам обоих плеч. |
| `OTLP Collector` | Принимает OTLP-метрики/трейсы от pod-ов и передает их в бэкенд наблюдаемости. |
| `АРМ Администратор` | Будущая интеграция приклада, показана пунктиром и не является частью текущего OTLP-пайплайна. |

## Топология размещения и выполнения

```mermaid
flowchart TB
    classDef k8s fill:#eef6ff,stroke:#2f6fa7,color:#102a43
    classDef app fill:#e9f8ef,stroke:#2f855a,color:#143d2a
    classDef lib fill:#fff6df,stroke:#b7791f,color:#3d2b12
    classDef flow fill:#f7fafc,stroke:#718096,color:#1a202c
    classDef db fill:#f5eefc,stroke:#805ad5,color:#2d1b4e
    classDef external fill:#ffffff,stroke:#4a5568,color:#1a202c
    classDef future fill:#f7f7f7,stroke:#777,stroke-dasharray: 6 4,color:#333

    subgraph DROPAPP["DropApp / Kubernetes"]
        direction LR

        subgraph SHOULDER_A["Плечо A"]
            direction TB
            A_DEPLOY["Deployment: rcln-monitoring<br/>replicas: 1"]
            subgraph A_POD["Pod A: rcln-monitoring"]
                direction TB
                A_CFG["ConfigMap / Secret<br/>reflex.telemetry.*<br/>учетные данные DataSource"]
                A_APP["Spring Boot приложение<br/>rcln-monitoring"]
                A_LIB["rcln-reflex-telemetry<br/>автоконфигурация, планировщик,<br/>JDBC-сборщик, OTel-публикатор"]
                A_CFG --> A_APP --> A_LIB
            end
            A_DEPLOY --> A_POD
        end

        subgraph SHOULDER_B["Плечо B"]
            direction TB
            B_DEPLOY["Deployment: rcln-monitoring<br/>replicas: 1"]
            subgraph B_POD["Pod B: rcln-monitoring"]
                direction TB
                B_CFG["ConfigMap / Secret<br/>те же определения метрик<br/>тот же endpoint lock-таблицы"]
                B_APP["Spring Boot приложение<br/>rcln-monitoring"]
                B_LIB["rcln-reflex-telemetry<br/>автоконфигурация, планировщик,<br/>JDBC-сборщик, OTel-публикатор"]
                B_CFG --> B_APP --> B_LIB
            end
            B_DEPLOY --> B_POD
        end
    end

    RUNTIME["Одинаковая runtime-схема<br/>в pod A и pod B"]:::flow

    subgraph ROUTES["Потоки библиотеки и приклада"]
        direction LR
        JDBC_ROUTE["JDBC SELECT<br/>через data-source-ref"]
        LOCK_ROUTE["ShedLock<br/>reflex-otel-metric:{metric-id}"]
        OTLP_ROUTE["OTLP-метрики/трейсы"]
        ARM_ROUTE["Будущий API-вызов<br/>из rcln-monitoring"]
    end

    subgraph TARGETS["Целевые системы"]
        direction LR
        OBSERVED_DBS[("БД-источники метрик<br/>JDBC-доступ на чтение")]
        SHEDLOCK_DB[("Общая БД ShedLock<br/>telemetry.shedlock<br/>глобальная для обоих плеч")]
        OBS_BACKEND["Бэкенд наблюдаемости<br/>Reflex / Dynatrace / OTel backend"]
        ARM_ADMIN["АРМ Администратор<br/>будущая интеграция"]
    end

    A_LIB -.-> RUNTIME
    B_LIB -.-> RUNTIME
    RUNTIME --> JDBC_ROUTE
    RUNTIME --> LOCK_ROUTE
    RUNTIME --> OTLP_ROUTE
    RUNTIME -.-> ARM_ROUTE

    JDBC_ROUTE --> OBSERVED_DBS
    LOCK_ROUTE --> SHEDLOCK_DB
    OTLP_ROUTE --> OBS_BACKEND
    ARM_ROUTE -.-> ARM_ADMIN

    class A_DEPLOY,B_DEPLOY,A_POD,B_POD k8s
    class A_APP,B_APP app
    class A_LIB,B_LIB lib
    class RUNTIME,JDBC_ROUTE,LOCK_ROUTE,OTLP_ROUTE,ARM_ROUTE flow
    class OBSERVED_DBS,SHEDLOCK_DB db
    class OBS_BACKEND external
    class ARM_ADMIN future
```

## Плановый запуск JDBC-метрики

```mermaid
flowchart TD
    classDef pod fill:#e9f8ef,stroke:#2f855a,color:#143d2a
    classDef lib fill:#fff6df,stroke:#b7791f,color:#3d2b12
    classDef decision fill:#fff8e5,stroke:#c99400,color:#3b3322
    classDef db fill:#f5eefc,stroke:#805ad5,color:#2d1b4e
    classDef external fill:#ffffff,stroke:#4a5568,color:#1a202c
    classDef outcome fill:#eef6ff,stroke:#2f6fa7,color:#102a43

    START["pod rcln-monitoring<br/>в плече A или B"]:::pod
    SCHEDULER["MetricSchedulerRegistrar<br/>срабатывание расписания для metric-id"]:::lib
    TASK["MetricExecutionTask<br/>runOnce()"]:::lib
    LOCK_MANAGER["ShedLockMetricLockManager<br/>попытка взять глобальный lock"]:::lib
    SHEDLOCK_DB[("Общая БД ShedLock<br/>telemetry.shedlock")]:::db
    LOCK_DECISION{"Lock получен для<br/>reflex-otel-metric:{metric-id}?"}:::decision

    COORDINATOR["MetricExecutionCoordinator<br/>collect()"]:::lib
    COLLECTOR["JdbcMetricCollector<br/>SQL из JdbcMetricSource"]:::lib
    OBSERVED_DBS[("БД-источники метрик<br/>JDBC-доступ на чтение")]:::db
    LIMITER["SeriesLimiter<br/>применение max-series и overflow policy"]:::lib
    PUBLISHER["OtelMetricPublisher<br/>публикация точек метрик"]:::lib
    SDK["OpenTelemetry SDK<br/>метрические instruments внутри процесса"]:::external
    OTLP["OTLP Collector<br/>экспорт с настроенным интервалом"]:::external
    RELEASE["Освобождение ShedLock<br/>обновление lock_until"]:::lib
    SUCCESS["SUCCESS<br/>записана внутренняя telemetry"]:::outcome

    SKIPPED["SKIPPED<br/>lock удерживает другой pod<br/>SQL не выполняется, дубля batch нет"]:::outcome
    FAILED["FAILED<br/>ошибка изолирована, процесс приложения продолжает работу"]:::outcome

    START --> SCHEDULER --> TASK --> LOCK_MANAGER --> LOCK_DECISION
    LOCK_MANAGER -. "использует" .-> SHEDLOCK_DB

    LOCK_DECISION -- "да" --> COORDINATOR --> COLLECTOR --> OBSERVED_DBS --> LIMITER --> PUBLISHER --> SDK --> OTLP --> RELEASE --> SUCCESS
    LOCK_DECISION -- "нет" --> SKIPPED
    TASK -. "ошибка SQL, маппера, лимитера или публикатора" .-> FAILED
```

## Консистентность и инварианты

1. `БД-источники метрик` на обеих схемах означает только БД, из которых читаются метрики. Это не lock-БД и не бэкенд наблюдаемости.
2. `Общая БД ShedLock` на обеих схемах означает одну общую lock-таблицу для pod-ов обоих плеч. Если lock недоступен, pod не выполняет SQL и не публикует batch метрики.
3. `OTLP Collector` на обеих схемах означает приемник OTLP-метрик/трейсов. Он не участвует в JDBC-сборе и не координирует scheduler.
4. `АРМ Администратор` показан пунктиром как будущая интеграция приклада `rcln-monitoring`, а не как часть текущей библиотеки `rcln-reflex-telemetry`.
5. Внутри каждого pod работает собственный scheduler, но глобальный ShedLock превращает запуск конкретной JDBC-метрики в single-run по обоим плечам.
