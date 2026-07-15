package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class MetricExecutionTask {

    private static final Logger log = LoggerFactory.getLogger(MetricExecutionTask.class);
    private static final long OVERFLOW_LOG_INTERVAL_NANOS = Duration.ofMinutes(5).toNanos();

    private final @NonNull MetricExecutionCoordinator coordinator;
    private final @NonNull MetricLockManager lockManager;
    private final @NonNull OtelMetricPublisher publisher;
    private final @NonNull InternalTelemetryRecorder telemetryRecorder;
    private final @NonNull SeriesLimiter seriesLimiter;
    private final @NonNull ResolvedMetricConfig config;
    private long nextOverflowLogNanos;

    public MetricRunOutcome runOnce() {
        if (!config.enabled()) {
            log.debug("Metric {} JDBC execution skipped because the metric is disabled", config.metricId());
            telemetryRecorder.recordSkipped(config);
            return MetricRunOutcome.SKIPPED;
        }

        long executionStartedAt = System.nanoTime();
        log.debug("Metric {} JDBC execution started: name={}, kind={}",
                config.metricId(), config.exportedMetricName(), config.metricKind());
        try {
            long lockStartedAt = System.nanoTime();
            boolean executed = lockManager.executeWithLock(config, () -> {
                log.debug("Metric {} execution lock acquired: durationMs={}",
                        config.metricId(), elapsedMillis(lockStartedAt));
                long collectionStartedAt = System.nanoTime();
                List<MetricPoint> points = coordinator.collect();
                log.debug("Metric {} JDBC collection completed: producedSeries={}, durationMs={}",
                        config.metricId(), points.size(), elapsedMillis(collectionStartedAt));
                List<MetricPoint> limited = seriesLimiter.apply(
                        points,
                        config.maxSeries(),
                        config.overflowPolicy(),
                        config.metricKind());
                logSeriesOverflow(points.size(), limited.size());
                long publicationStartedAt = System.nanoTime();
                publisher.publish(config, limited);
                log.debug("Metric {} publication completed: series={}, durationMs={}",
                        config.metricId(), limited.size(), elapsedMillis(publicationStartedAt));
            });

            if (!executed) {
                publisher.clear(config);
                log.debug("Metric {} JDBC execution skipped because the distributed lock was not acquired; "
                                + "gaugeSnapshotCleared={}, durationMs={}",
                        config.metricId(), config.metricKind() == MetricKind.GAUGE, elapsedMillis(executionStartedAt));
                telemetryRecorder.recordSkipped(config);
                return MetricRunOutcome.SKIPPED;
            }

            telemetryRecorder.recordSuccess(config);
            log.debug("Metric {} JDBC execution completed: durationMs={}",
                    config.metricId(), elapsedMillis(executionStartedAt));
            return MetricRunOutcome.SUCCESS;
        } catch (Exception exception) {
            log.debug("Metric {} JDBC execution failed: durationMs={}",
                    config.metricId(), elapsedMillis(executionStartedAt), exception);
            telemetryRecorder.recordFailure(config, exception);
            return MetricRunOutcome.FAILED;
        }
    }

    private synchronized void logSeriesOverflow(int producedSeries, int retainedSeries) {
        if (producedSeries <= config.maxSeries()
                || config.overflowPolicy() == SeriesOverflowPolicy.FAIL) {
            return;
        }

        long now = System.nanoTime();
        if (nextOverflowLogNanos != 0 && now - nextOverflowLogNanos < 0) {
            return;
        }
        nextOverflowLogNanos = now + OVERFLOW_LOG_INTERVAL_NANOS;
        log.warn("Metric {} exceeded its series limit: produced={}, max={}, policy={}, retained={}",
                config.metricId(), producedSeries, config.maxSeries(), config.overflowPolicy(), retainedSeries);
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
