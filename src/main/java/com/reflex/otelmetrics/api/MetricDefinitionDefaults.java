package com.reflex.otelmetrics.api;

public record MetricDefinitionDefaults(
        boolean enabled,
        MetricKind kind,
        SeriesOverflowPolicy seriesOverflowPolicy
) {
}
