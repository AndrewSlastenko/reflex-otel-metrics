package ru.sber.rcln.reflex.telemetry.api;

import java.util.Map;

public interface UpDownCounterMetric {

    void add(long value, Map<String, String> attributes);

    default void add(long value) {
        add(value, Map.of());
    }
}
