package com.reflex.otelmetrics.otel;

import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OtelMetricPublisher {

    private final OtelInstrumentRegistry registry;

    public OtelMetricPublisher(OtelInstrumentRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public void publish(ResolvedMetricConfig config, List<MetricPoint> points) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(points, "points must not be null");

        Object instrument = registry.getOrCreate(config.fullMetricName(), config.metricKind());
        for (MetricPoint point : points) {
            Attributes attributes = toAttributes(point.attributes());
            if (instrument instanceof LongCounter counter) {
                counter.add(point.value(), attributes);
            } else if (instrument instanceof LongGauge gauge) {
                gauge.set(point.value(), attributes);
            } else if (instrument instanceof LongUpDownCounter counter) {
                counter.add(point.value(), attributes);
            } else {
                throw new IllegalStateException("Unsupported instrument type: " + instrument.getClass().getName());
            }
        }
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        io.opentelemetry.api.common.AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }
}
