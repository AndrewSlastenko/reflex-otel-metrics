package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MetricExecutionDispatcherTest {

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
        awaitIdle(executor);

        assertThat(dispatchUntilAccepted(dispatcher, config("metric-b"), secondStarted::countDown))
                .isEqualTo(MetricDispatchOutcome.ACCEPTED);
        assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

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

    private static void awaitIdle(ThreadPoolExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (executor.getActiveCount() == 0) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Executor did not become idle");
    }

    private static MetricDispatchOutcome dispatchUntilAccepted(
            MetricExecutionDispatcher dispatcher,
            ResolvedMetricConfig config,
            Runnable runnable) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        MetricDispatchOutcome outcome;
        do {
            outcome = dispatcher.dispatch(config, runnable);
            if (outcome == MetricDispatchOutcome.ACCEPTED) {
                return outcome;
            }
            Thread.sleep(10L);
        } while (System.nanoTime() < deadline);
        return outcome;
    }
}
