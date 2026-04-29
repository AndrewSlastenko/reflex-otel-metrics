package com.reflex.otelmetrics.api;

import java.time.Duration;

public record MetricScheduleDefaults(
        Duration initialDelay,
        Duration fixedDelay,
        Duration lockAtMostFor,
        Duration lockAtLeastFor
) {
}
