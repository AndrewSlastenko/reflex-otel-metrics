package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;

public record ResolvedManualMetricConfig(
        String metricId,
        boolean enabled,
        String fullMetricName,
        String suffix,
        String scope,
        MetricKind metricKind,
        String description,
        String unit,
        AttributesSchema attributes,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
