package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ManualSeriesTracker {

    private final int maxSeries;
    private final SeriesOverflowPolicy overflowPolicy;
    private final Set<Map<String, String>> observedSeries = ConcurrentHashMap.newKeySet();

    public ManualSeriesTracker(int maxSeries, SeriesOverflowPolicy overflowPolicy) {
        if (maxSeries <= 0) {
            throw new IllegalArgumentException("maxSeries must be greater than 0");
        }
        this.maxSeries = maxSeries;
        this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy must not be null");
    }

    public synchronized Result apply(Map<String, String> attributes) {
        Map<String, String> series = immutableCopy(Objects.requireNonNull(attributes, "attributes must not be null"));
        if (observedSeries.contains(series)) {
            return Result.accepted(series);
        }

        if (observedSeries.size() >= maxSeries) {
            return rejectOverflow();
        }

        observedSeries.add(series);
        return Result.accepted(series);
    }

    private Result rejectOverflow() {
        return switch (overflowPolicy) {
            case FAIL, TRUNCATE, AGGREGATE_TO_OTHER -> Result.rejected("max series limit " + maxSeries + " exceeded");
        };
    }

    private static Map<String, String> immutableCopy(Map<String, String> attributes) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public record Result(boolean accepted, Map<String, String> attributes, String message) {

        private static Result accepted(Map<String, String> attributes) {
            return new Result(true, attributes, null);
        }

        private static Result rejected(String message) {
            return new Result(false, Map.of(), message);
        }
    }
}
