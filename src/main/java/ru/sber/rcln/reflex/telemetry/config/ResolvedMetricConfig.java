package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;

public record ResolvedMetricConfig(
        String metricId,
        boolean enabled,
        String fullMetricName,
        String suffix,
        String scope,
        String dataSourceRef,
        MetricKind metricKind,
        MetricScheduleSettings schedule,
        Duration timeout,
        Duration lockAtMostFor,
        Duration lockAtLeastFor,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
