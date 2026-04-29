package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;

public record ResolvedMetricConfig(
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
