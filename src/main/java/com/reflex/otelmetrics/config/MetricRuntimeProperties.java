package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;

public record MetricRuntimeProperties(
        String metricSuffix,
        MetricKind metricKind,
        String scope,
        String dataSourceRef,
        MetricScheduleSettings schedule,
        Duration timeout,
        Duration lockAtMostFor,
        Duration lockAtLeastFor,
        Integer maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
