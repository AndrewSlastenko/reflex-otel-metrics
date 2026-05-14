package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricExecutionTask {

    private final @NonNull MetricExecutionCoordinator coordinator;
    private final @NonNull MetricLockManager lockManager;
    private final @NonNull OtelMetricPublisher publisher;
    private final @NonNull InternalTelemetryRecorder telemetryRecorder;
    private final @NonNull SeriesLimiter seriesLimiter;
    private final @NonNull ResolvedMetricConfig config;

    public MetricRunOutcome runOnce() {
        if (!config.enabled()) {
            telemetryRecorder.recordSkipped(config);
            return MetricRunOutcome.SKIPPED;
        }

        try {
            boolean executed = lockManager.executeWithLock(config, () -> {
                List<MetricPoint> points = coordinator.collect();
                List<MetricPoint> limited = seriesLimiter.apply(
                        points,
                        config.maxSeries(),
                        config.overflowPolicy(),
                        config.metricKind());
                publisher.publish(config, limited);
            });

            if (!executed) {
                telemetryRecorder.recordSkipped(config);
                return MetricRunOutcome.SKIPPED;
            }

            telemetryRecorder.recordSuccess(config);
            return MetricRunOutcome.SUCCESS;
        } catch (Exception exception) {
            telemetryRecorder.recordFailure(config, exception);
            return MetricRunOutcome.FAILED;
        }
    }
}
