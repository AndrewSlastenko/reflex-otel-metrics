package com.reflex.otelmetrics.api;

import java.time.Duration;

public record MetricScheduleDefaults(
        Mode mode,
        Duration fixedDelay,
        String cron,
        Duration initialDelay
) {
    public enum Mode {
        FIXED_DELAY,
        CRON
    }
}
