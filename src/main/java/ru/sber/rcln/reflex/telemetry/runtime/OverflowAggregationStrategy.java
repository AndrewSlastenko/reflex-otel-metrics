package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverflowAggregationStrategy {

    public MetricPoint aggregate(List<MetricPoint> overflowPoints) {
        long value = overflowPoints.stream().mapToLong(MetricPoint::value).sum();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("bucket", "other");
        return new MetricPoint(value, attributes);
    }
}
