package com.reflex.otelmetrics.api;

import java.util.Map;

public interface CounterMetric {

    void add(long value, Map<String, String> attributes);

    default void increment(Map<String, String> attributes) {
        add(1, attributes);
    }

    default void add(long value) {
        add(value, Map.of());
    }

    default void increment() {
        increment(Map.of());
    }
}
