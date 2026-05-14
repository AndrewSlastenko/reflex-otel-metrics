package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverflowAggregationStrategy {

    public MetricPoint aggregate(MetricKind kind, List<MetricPoint> overflowPoints) {
        if (kind == MetricKind.HISTOGRAM) {
            throw new IllegalStateException(
                    "AGGREGATE_TO_OTHER is not supported for HISTOGRAM metrics. Use FAIL or TRUNCATE overflow policy.");
        }
        long value = overflowPoints.stream().mapToLong(MetricPoint::value).sum();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("bucket", "other");
        return new MetricPoint(value, attributes);
    }
}
