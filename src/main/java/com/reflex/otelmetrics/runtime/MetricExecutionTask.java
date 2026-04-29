package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import com.reflex.otelmetrics.internal.InternalTelemetryRecorder;
import com.reflex.otelmetrics.locking.MetricLockManager;
import com.reflex.otelmetrics.otel.OtelMetricPublisher;

import java.util.List;
import java.util.Objects;

public class MetricExecutionTask {

    private final MetricExecutionCoordinator coordinator;
    private final MetricLockManager lockManager;
    private final OtelMetricPublisher publisher;
    private final InternalTelemetryRecorder telemetryRecorder;
    private final SeriesLimiter seriesLimiter;
    private final ResolvedMetricConfig config;

    public MetricExecutionTask(
            MetricExecutionCoordinator coordinator,
            MetricLockManager lockManager,
            OtelMetricPublisher publisher,
            InternalTelemetryRecorder telemetryRecorder,
            SeriesLimiter seriesLimiter,
            ResolvedMetricConfig config
    ) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.telemetryRecorder = Objects.requireNonNull(telemetryRecorder, "telemetryRecorder must not be null");
        this.seriesLimiter = Objects.requireNonNull(seriesLimiter, "seriesLimiter must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public MetricRunOutcome runOnce() {
        if (!config.enabled()) {
            telemetryRecorder.recordSkipped(config);
            return MetricRunOutcome.SKIPPED;
        }

        try {
            boolean executed = lockManager.executeWithLock(config, () -> {
                List<MetricPoint> points = coordinator.collect();
                List<MetricPoint> limited = seriesLimiter.apply(points, config.maxSeries(), config.overflowPolicy());
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
