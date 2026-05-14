package ru.sber.rcln.reflex.telemetry.api;

import lombok.Builder;

@Builder(builderClassName = "Builder", builderMethodName = "")
public record MetricDefinition(
        String metricSuffix,
        String scope,
        String description,
        String unit,
        AttributesSchema attributes,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
    private static final String DEFAULT_SCOPE = ReflexMetricScopes.MANUAL;
    private static final int DEFAULT_MAX_SERIES = 500;

    public MetricDefinition {
        if (metricSuffix == null || metricSuffix.isBlank()) {
            throw new IllegalArgumentException("metricSuffix cannot be null or blank");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope cannot be null or blank");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes cannot be null");
        }
        if (maxSeries < 1) {
            throw new IllegalArgumentException("maxSeries must be at least 1");
        }
        if (overflowPolicy == null) {
            throw new IllegalArgumentException("overflowPolicy cannot be null");
        }
    }

    public static Builder of(String metricSuffix) {
        return new Builder()
                .metricSuffix(metricSuffix)
                .scope(DEFAULT_SCOPE)
                .attributes(AttributesSchema.empty())
                .maxSeries(DEFAULT_MAX_SERIES)
                .overflowPolicy(SeriesOverflowPolicy.FAIL);
    }
}
