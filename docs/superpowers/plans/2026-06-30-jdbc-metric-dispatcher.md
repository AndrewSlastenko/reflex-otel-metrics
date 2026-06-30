# JDBC Metric Dispatcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit JDBC metric dispatcher that runs different metric ids concurrently while preventing local overlap for the same metric id.

**Architecture:** Keep `MetricExecutionTask.runOnce()` as the owner of actual metric execution and distributed lock behavior. Add a dispatcher in front of it that owns local per-metric running state and submits accepted runs to a bounded worker executor with queue size `0`. Keep scheduling separate from worker execution.

**Tech Stack:** Java 17, Spring Boot 3.5, Maven, JUnit 5, AssertJ, Mockito, ApplicationContextRunner.

---

## File Structure

- Create `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricDispatchOutcome.java`: dispatcher result enum.
- Create `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionDispatcher.java`: local per-metric no-overlap guard and no-queue worker submission.
- Modify `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricSchedulerRegistrar.java`: register scheduled ticks and delegate execution to callbacks.
- Modify `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricRuntimeRegistrar.java`: register `MetricExecutionTask` through the dispatcher.
- Modify `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`: add `metrics.jdbc.scheduler.pool-size`, default `2`.
- Modify `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexJdbcTelemetryAutoConfiguration.java`: keep scheduler executor, add worker executor and dispatcher beans.
- Test `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionDispatcherTest.java`: concurrency and local-overlap behavior.
- Modify `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricSchedulerRegistrarTest.java`: scheduler delegates ticks without owning execution.
- Modify `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexJdbcTelemetryAutoConfigurationTest.java`: pool-size binding and bean wiring.
- Modify `README.md`: document scheduler pool size and local-overlap semantics.

## Task 1: Add Dispatcher Tests First

**Files:**

- Create: `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionDispatcherTest.java`

- [ ] **Step 1: Write a failing test for different metric ids running concurrently**

Create `MetricExecutionDispatcherTest` with two configs, a two-thread executor, and blocking tasks:

```java
@Test
void shouldRunDifferentMetricIdsConcurrently() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    MetricExecutionDispatcher dispatcher = new MetricExecutionDispatcher(executor);
    CountDownLatch bothStarted = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);

    dispatcher.dispatch(config("metric-a"), () -> {
        bothStarted.countDown();
        await(release);
    });
    dispatcher.dispatch(config("metric-b"), () -> {
        bothStarted.countDown();
        await(release);
    });

    assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue();

    release.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
}
```

- [ ] **Step 2: Write a failing test for same metric id local-overlap skip**

Use a single blocked first run and invoke dispatch again for the same config:

```java
@Test
void shouldSkipSameMetricIdWhenPreviousRunIsStillActive() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    MetricExecutionDispatcher dispatcher = new MetricExecutionDispatcher(executor);
    ResolvedMetricConfig config = config("metric-a");
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicInteger runs = new AtomicInteger();

    MetricDispatchOutcome firstOutcome = dispatcher.dispatch(config, () -> {
        runs.incrementAndGet();
        firstStarted.countDown();
        await(releaseFirst);
    });
    assertThat(firstOutcome).isEqualTo(MetricDispatchOutcome.ACCEPTED);
    assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

    MetricDispatchOutcome secondOutcome = dispatcher.dispatch(config, runs::incrementAndGet);

    assertThat(secondOutcome).isEqualTo(MetricDispatchOutcome.LOCAL_OVERLAP_SKIPPED);
    assertThat(runs).hasValue(1);

    releaseFirst.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
}
```

- [ ] **Step 3: Write a failing test for capacity skip with queue size zero**

Use one worker and a `SynchronousQueue` executor. Fill the only worker with metric A, then dispatch metric B:

```java
@Test
void shouldSkipWhenNoWorkerIsAvailableAndQueueSizeIsZero() throws Exception {
    ThreadPoolExecutor executor = noQueueExecutor(1);
    MetricExecutionDispatcher dispatcher = new MetricExecutionDispatcher(executor);
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicInteger secondRuns = new AtomicInteger();

    assertThat(dispatcher.dispatch(config("metric-a"), () -> {
        firstStarted.countDown();
        await(releaseFirst);
    })).isEqualTo(MetricDispatchOutcome.ACCEPTED);
    assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

    assertThat(dispatcher.dispatch(config("metric-b"), secondRuns::incrementAndGet))
            .isEqualTo(MetricDispatchOutcome.CAPACITY_SKIPPED);
    assertThat(secondRuns).hasValue(0);

    releaseFirst.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
}
```

- [ ] **Step 4: Write a failing test that capacity skip releases local running state**

After a capacity skip for metric B, release metric A and dispatch metric B again:

```java
@Test
void shouldReleaseMetricStateAfterCapacitySkip() throws Exception {
    ThreadPoolExecutor executor = noQueueExecutor(1);
    MetricExecutionDispatcher dispatcher = new MetricExecutionDispatcher(executor);
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch firstFinished = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);

    dispatcher.dispatch(config("metric-a"), () -> {
        try {
            firstStarted.countDown();
            await(releaseFirst);
        } finally {
            firstFinished.countDown();
        }
    });
    assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

    assertThat(dispatcher.dispatch(config("metric-b"), secondStarted::countDown))
            .isEqualTo(MetricDispatchOutcome.CAPACITY_SKIPPED);

    releaseFirst.countDown();
    assertThat(firstFinished.await(1, TimeUnit.SECONDS)).isTrue();

    assertThat(dispatcher.dispatch(config("metric-b"), secondStarted::countDown))
            .isEqualTo(MetricDispatchOutcome.ACCEPTED);
    assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();

    executor.shutdown();
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
}
```

- [ ] **Step 5: Write helper methods in the test**

Use the same `ResolvedMetricConfig` construction style already present in `MetricExecutionTaskTest`:

```java
private static ResolvedMetricConfig config(String metricId) {
    return new ResolvedMetricConfig(
            metricId,
            ReflexTelemetryProperties.MetricSourceType.JDBC,
            true,
            "ci054147." + metricId,
            metricId,
            "jdbc",
            "Test metric",
            "1",
            AttributesSchema.empty(),
            "businessReplicaDataSource",
            MetricKind.GAUGE,
            MetricScheduleSettings.fixedDelay(Duration.ofMinutes(1), Duration.ZERO),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ZERO,
            500,
            SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
            List.of());
}

private static void await(CountDownLatch latch) {
    try {
        if (!latch.await(1, TimeUnit.SECONDS)) {
            throw new AssertionError("Latch was not released");
        }
    } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AssertionError(exception);
    }
}

private static ThreadPoolExecutor noQueueExecutor(int poolSize) {
    return new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0L,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.AbortPolicy());
}
```

- [ ] **Step 6: Run the failing dispatcher tests**

Run:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry -Dtest=MetricExecutionDispatcherTest test
```

Expected: compilation fails because `MetricExecutionDispatcher` does not exist.

## Task 2: Implement MetricDispatchOutcome and MetricExecutionDispatcher

**Files:**

- Create: `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricDispatchOutcome.java`
- Create: `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionDispatcher.java`
- Test: `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionDispatcherTest.java`

- [ ] **Step 1: Add the outcome enum**

Create:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

public enum MetricDispatchOutcome {
    ACCEPTED,
    LOCAL_OVERLAP_SKIPPED,
    CAPACITY_SKIPPED
}
```

- [ ] **Step 2: Add the dispatcher implementation**

Create:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricExecutionDispatcher {

    private final @NonNull ExecutorService executorService;
    private final ConcurrentMap<String, AtomicBoolean> runningByMetricId = new ConcurrentHashMap<>();

    public MetricDispatchOutcome dispatch(@NonNull ResolvedMetricConfig config, @NonNull Runnable runnable) {
        AtomicBoolean running = runningByMetricId.computeIfAbsent(config.metricId(), ignored -> new AtomicBoolean());
        if (!running.compareAndSet(false, true)) {
            return MetricDispatchOutcome.LOCAL_OVERLAP_SKIPPED;
        }

        try {
            executorService.execute(() -> {
                try {
                    runnable.run();
                } finally {
                    running.set(false);
                }
            });
            return MetricDispatchOutcome.ACCEPTED;
        } catch (RejectedExecutionException exception) {
            running.set(false);
            return MetricDispatchOutcome.CAPACITY_SKIPPED;
        }
    }
}
```

- [ ] **Step 3: Run dispatcher tests**

Run:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry -Dtest=MetricExecutionDispatcherTest test
```

Expected: tests pass.

## Task 3: Wire Dispatcher Into JDBC Runtime

**Files:**

- Modify: `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricRuntimeRegistrar.java`
- Modify: `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexJdbcTelemetryAutoConfiguration.java`
- Modify: `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexJdbcTelemetryAutoConfigurationTest.java`

- [ ] **Step 1: Inject dispatcher into `JdbcMetricRuntimeRegistrar`**

Add a `MetricExecutionDispatcher` field and constructor parameter. Change registration from direct task scheduling:

```java
schedulerRegistrar.register(config, task::runOnce);
```

to dispatcher-backed scheduling:

```java
schedulerRegistrar.register(config, () -> dispatcher.dispatch(config, task::runOnce));
```

The returned `MetricDispatchOutcome` is intentionally ignored in stage one. `LOCAL_OVERLAP_SKIPPED` and `CAPACITY_SKIPPED` both mean `runOnce()` was not called.

- [ ] **Step 2: Add worker executor and dispatcher beans**

In `ReflexJdbcTelemetryAutoConfiguration`, add:

```java
@Bean(name = "reflexTelemetryMetricWorkerExecutorService", destroyMethod = "shutdown")
@ConditionalOnMissingBean(name = "reflexTelemetryMetricWorkerExecutorService")
ExecutorService reflexTelemetryMetricWorkerExecutorService(ReflexTelemetryProperties properties) {
    int poolSize = properties.getMetrics().getJdbc().getScheduler().getPoolSize();
    if (poolSize < 1) {
        throw new IllegalArgumentException("reflex.telemetry.metrics.jdbc.scheduler.pool-size must be at least 1");
    }
    return new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0L,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            new ReflexTelemetryWorkerThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());
}

@Bean
@ConditionalOnMissingBean
MetricExecutionDispatcher metricExecutionDispatcher(
        @Qualifier("reflexTelemetryMetricWorkerExecutorService") ExecutorService executorService) {
    return new MetricExecutionDispatcher(executorService);
}
```

Add `ExecutorService`, `ThreadPoolExecutor`, `SynchronousQueue`, and `TimeUnit` imports, plus a worker thread factory with names `reflex-telemetry-metrics-worker-N`.

- [ ] **Step 3: Update registrar bean method**

Add `MetricExecutionDispatcher dispatcher` to the `jdbcMetricRuntimeRegistrar(...)` bean method and pass it to the constructor.

- [ ] **Step 4: Update autoconfiguration test wiring**

In `shouldConfigureJdbcRuntimeWhenJdbcMetricSourceIsPresent`, keep the mocked scheduler executor. Assert that the context has a `MetricExecutionDispatcher` bean and a worker executor bean.

- [ ] **Step 5: Run targeted autoconfiguration tests**

Run:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry -Dtest=ReflexJdbcTelemetryAutoConfigurationTest test
```

Expected: tests pass after wiring updates.

## Task 4: Add Scheduler Pool Properties

**Files:**

- Modify: `rcln-reflex-telemetry/src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`
- Modify: `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexJdbcTelemetryAutoConfigurationTest.java`

- [ ] **Step 1: Add nested properties**

In `JdbcProperties`, add:

```java
private SchedulerProperties scheduler = new SchedulerProperties();

public void setScheduler(SchedulerProperties scheduler) {
    this.scheduler = scheduler != null ? scheduler : new SchedulerProperties();
}
```

Add nested class:

```java
@Getter
@Setter
public static class SchedulerProperties {

    private int poolSize = 2;
}
```

- [ ] **Step 2: Add autoconfiguration test for configured pool size**

Use `ReflectionTestUtils` or executor type inspection only if stable. Prefer a small behavioral test: configure `reflex.telemetry.metrics.jdbc.scheduler.pool-size=1`, get the worker executor bean, submit one blocking task and assert a second direct `execute(...)` is rejected while the first task is running.

- [ ] **Step 3: Add startup failure test for invalid pool size**

Run context with:

```text
reflex.telemetry.metrics.jdbc.scheduler.pool-size=0
```

Assert startup failure contains:

```text
reflex.telemetry.metrics.jdbc.scheduler.pool-size must be at least 1
```

- [ ] **Step 4: Run targeted tests**

Run:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry -Dtest=ReflexJdbcTelemetryAutoConfigurationTest test
```

Expected: tests pass.

## Task 5: Preserve Gauge Clear Semantics With Tests

**Files:**

- Modify: `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionDispatcherTest.java`
- Existing coverage: `rcln-reflex-telemetry/src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTaskTest.java`

- [ ] **Step 1: Add a local-overlap test around a mocked task**

Use a `Runnable` mock or `AtomicInteger` to prove second local overlap does not call the task at all. This is the stage-one proxy for "does not attempt lock and does not clear gauge", because lock and clear are inside `MetricExecutionTask.runOnce()`.

```java
@Test
void shouldNotInvokeTaskForLocalOverlap() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    MetricExecutionDispatcher dispatcher = new MetricExecutionDispatcher(executor);
    ResolvedMetricConfig config = config("metric-a");
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    Runnable secondTask = mock(Runnable.class);

    dispatcher.dispatch(config, () -> {
        firstStarted.countDown();
        await(releaseFirst);
    });
    assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

    assertThat(dispatcher.dispatch(config, secondTask))
            .isEqualTo(MetricDispatchOutcome.LOCAL_OVERLAP_SKIPPED);

    verifyNoInteractions(secondTask);

    releaseFirst.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
}
```

- [ ] **Step 2: Keep existing distributed lock skip test unchanged**

Verify `MetricExecutionTaskTest.shouldClearGaugeWhenLockIsNotAcquired` still passes. This confirms only distributed lock skip clears gauge.

- [ ] **Step 3: Run runtime tests**

Run:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry -Dtest=MetricExecutionDispatcherTest,MetricExecutionTaskTest test
```

Expected: tests pass.

## Task 6: Update README

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Add configuration table row**

Near the existing `metrics.jdbc.enabled` and `metrics.jdbc.lock-provider-ref` rows, add:

```markdown
| `metrics.jdbc.scheduler.pool-size` | `2` | Number of concurrent JDBC metric runs per JVM; worker queue size is `0`, and one metric id is still protected from local overlap |
```

- [ ] **Step 2: Add runtime semantics paragraph**

Near the JDBC runtime section, document:

```markdown
JDBC polling uses a scheduler plus a bounded worker pool with no waiting queue. Different metric ids may run concurrently up to `reflex.telemetry.metrics.jdbc.scheduler.pool-size`, but the same metric id is never executed concurrently in the same JVM. If a tick arrives while the previous local run for that metric id is still active, the tick is skipped locally before ShedLock and before gauge clearing. If all workers are busy, the tick is skipped by capacity before `MetricExecutionTask.runOnce()`. Gauge clearing is still performed when the real run starts but this pod does not acquire the distributed ShedLock.
```

- [ ] **Step 3: Add pool sizing note**

Add:

```markdown
Keep `metrics.jdbc.scheduler.pool-size` aligned with telemetry `DataSource` pool sizes. The worker queue size is `0`, so a tick is not queued when all workers are busy; it is skipped and the next schedule tick can try again.
```

## Task 7: Final Verification

**Files:**

- All modified Java and README files.

- [ ] **Step 1: Run targeted module tests**

Run:

```powershell
.\mvnw.cmd -pl rcln-reflex-telemetry test
```

Expected: build passes.

- [ ] **Step 2: Check worktree**

Run:

```powershell
git status --short
```

Expected: only planned source, test, README, spec, and plan files are modified.
