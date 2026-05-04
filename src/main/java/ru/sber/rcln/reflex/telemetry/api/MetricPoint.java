package ru.sber.rcln.reflex.telemetry.api;

import java.util.Map;

public record MetricPoint(
        long value,
        Map<String, String> attributes
) {
}
