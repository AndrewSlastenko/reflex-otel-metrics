package com.reflex.otelmetrics.api;

import java.util.Map;

public record MetricPoint(
        long value,
        Map<String, String> attributes
) {
}
