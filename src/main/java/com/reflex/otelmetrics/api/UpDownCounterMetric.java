package com.reflex.otelmetrics.api;

import java.util.Map;

public interface UpDownCounterMetric {

    void add(long value, Map<String, String> attributes);

    default void add(long value) {
        add(value, Map.of());
    }
}
