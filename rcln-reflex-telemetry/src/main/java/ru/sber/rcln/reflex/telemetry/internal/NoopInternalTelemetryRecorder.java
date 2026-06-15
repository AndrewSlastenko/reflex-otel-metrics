package ru.sber.rcln.reflex.telemetry.internal;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

public class NoopInternalTelemetryRecorder implements InternalTelemetryRecorder {

    @Override
    public void recordSuccess(ResolvedMetricConfig config) {
    }

    @Override
    public void recordFailure(ResolvedMetricConfig config, Exception exception) {
    }

    @Override
    public void recordSkipped(ResolvedMetricConfig config) {
    }
}
