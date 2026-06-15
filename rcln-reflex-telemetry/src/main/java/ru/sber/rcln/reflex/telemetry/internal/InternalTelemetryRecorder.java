package ru.sber.rcln.reflex.telemetry.internal;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

public interface InternalTelemetryRecorder {

    void recordSuccess(ResolvedMetricConfig config);

    void recordFailure(ResolvedMetricConfig config, Exception exception);

    void recordSkipped(ResolvedMetricConfig config);
}
