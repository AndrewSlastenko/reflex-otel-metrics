package ru.sber.rcln.reflex.telemetry.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SpanSpec(String name, TraceCarrier parent, Map<String, String> attributes) {

    public SpanSpec {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        parent = parent != null ? parent : TraceCarrier.empty();
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
