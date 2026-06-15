package ru.sber.rcln.reflex.telemetry.api;

import java.util.Map;

public interface HistogramMetric {

    void record(double value, Map<String, String> attributes);

    default void record(double value) {
        record(value, Map.of());
    }
}
