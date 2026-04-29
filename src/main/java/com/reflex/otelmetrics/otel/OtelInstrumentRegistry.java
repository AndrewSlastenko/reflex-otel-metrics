package com.reflex.otelmetrics.otel;

import com.reflex.otelmetrics.api.MetricKind;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class OtelInstrumentRegistry {

    private final Meter meter;
    private final Map<String, Object> instruments = new ConcurrentHashMap<>();

    public OtelInstrumentRegistry(Meter meter) {
        this.meter = Objects.requireNonNull(meter, "meter must not be null");
    }

    public Object getOrCreate(String name, MetricKind kind) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");

        return instruments.computeIfAbsent(name, key -> switch (kind) {
            case GAUGE -> (LongGauge) meter.gaugeBuilder(name).ofLongs().build();
            case UP_DOWN_COUNTER -> (LongUpDownCounter) meter.upDownCounterBuilder(name).build();
        });
    }
}
