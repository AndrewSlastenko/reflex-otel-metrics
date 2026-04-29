package com.reflex.otelmetrics.api;

import java.time.Duration;

public record MetricDefinitionDefaults(
        String metricSuffix,
        MetricKind metricKind,
        String scope,
        String dataSourceRef,
        MetricScheduleDefaults schedule,
        Duration timeout,
        Duration lockAtMostFor,
        Duration lockAtLeastFor,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
