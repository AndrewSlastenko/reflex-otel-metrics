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
        List<Double> histogramBuckets
) {
    public ResolvedMetricConfig {
        histogramBuckets = histogramBuckets == null ? List.of() : List.copyOf(histogramBuckets);
    }
}
