package com.reflex.otelmetrics.api;

import java.time.Instant;
import java.util.Map;

public record MetricPoint(
        String name,
        MetricKind kind,
        double value,
        Map<String, String> attributes,
        Instant timestamp
) {
}
