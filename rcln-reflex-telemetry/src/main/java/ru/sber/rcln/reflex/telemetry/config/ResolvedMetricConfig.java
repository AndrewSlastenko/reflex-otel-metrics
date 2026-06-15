package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.List;

public record ResolvedMetricConfig(
        String metricId,
        ReflexTelemetryProperties.MetricSourceType source,
        boolean enabled,
        String exportedMetricName,
        String name,
        String scope,
        String description,
        String unit,
        AttributesSchema attributes,
        String dataSourceRef,
        MetricKind metricKind,
        MetricScheduleSettings schedule,
        Duration timeout,
        Duration lockAtMostFor,
        Duration lockAtLeastFor,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy,
        List<Double> histogramBuckets,
        String querySchema
) {
    public ResolvedMetricConfig {
        histogramBuckets = histogramBuckets == null ? List.of() : List.copyOf(histogramBuckets);
        querySchema = (querySchema == null || querySchema.isBlank()) ? null : querySchema;
    }

    /**
     * Back-compat secondary constructor; defaults {@code querySchema} to {@code null}.
     * Used by existing callers (mostly tests) that pre-date the YAML-driven query parameters.
     */
    public ResolvedMetricConfig(
            String metricId,
            ReflexTelemetryProperties.MetricSourceType source,
            boolean enabled,
            String exportedMetricName,
            String name,
            String scope,
            String description,
            String unit,
            AttributesSchema attributes,
            String dataSourceRef,
            MetricKind metricKind,
            MetricScheduleSettings schedule,
            Duration timeout,
            Duration lockAtMostFor,
            Duration lockAtLeastFor,
            int maxSeries,
            SeriesOverflowPolicy overflowPolicy,
            List<Double> histogramBuckets
    ) {
        this(
                metricId,
                source,
                enabled,
                exportedMetricName,
                name,
                scope,
                description,
                unit,
                attributes,
                dataSourceRef,
                metricKind,
                schedule,
                timeout,
                lockAtMostFor,
                lockAtLeastFor,
                maxSeries,
                overflowPolicy,
                histogramBuckets,
                null);
    }
}
