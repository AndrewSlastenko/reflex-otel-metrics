package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SeriesLimiter {

    private final @NonNull OverflowAggregationStrategy overflowAggregationStrategy;

    public List<MetricPoint> apply(List<MetricPoint> points, int maxSeries, SeriesOverflowPolicy policy) {
        if (maxSeries <= 0) {
            throw new IllegalArgumentException("maxSeries must be greater than 0");
        }
        if (points.size() <= maxSeries) {
            return points;
        }
        return switch (policy) {
            case TRUNCATE -> new ArrayList<>(points.subList(0, maxSeries));
            case AGGREGATE_TO_OTHER -> {
                List<MetricPoint> head = new ArrayList<>(points.subList(0, maxSeries - 1));
                head.add(overflowAggregationStrategy.aggregate(points.subList(maxSeries - 1, points.size())));
                yield head;
            }
            case FAIL -> throw new IllegalStateException("Metric produced " + points.size() + " series, max allowed is " + maxSeries);
        };
    }
}
