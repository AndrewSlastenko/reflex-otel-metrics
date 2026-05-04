package com.reflex.otelmetrics.api;

import java.util.Map;

public interface GaugeMetric {

    void set(long value, Map<String, String> attributes);

    default void set(long value) {
        set(value, Map.of());
    }
}
