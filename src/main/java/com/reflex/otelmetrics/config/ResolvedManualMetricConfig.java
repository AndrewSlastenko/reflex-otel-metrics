package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;

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
