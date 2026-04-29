package com.reflex.otelmetrics.api;

public record QueryDefinition(
        String name,
        String sql,
        MetricKind kind,
        SeriesOverflowPolicy seriesOverflowPolicy
) {
}
