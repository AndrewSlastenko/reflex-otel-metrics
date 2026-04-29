package com.reflex.otelmetrics.internal;

import com.reflex.otelmetrics.config.ResolvedMetricConfig;

public interface InternalTelemetryRecorder {

    void recordSuccess(ResolvedMetricConfig config);

    void recordFailure(ResolvedMetricConfig config, Exception exception);

    void recordSkipped(ResolvedMetricConfig config);
}
