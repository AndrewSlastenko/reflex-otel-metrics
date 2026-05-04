package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;

import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtelMetricPublisher {

    private final @NonNull OtelInstrumentRegistry registry;

    public void publish(@NonNull ResolvedMetricConfig config, @NonNull List<MetricPoint> points) {

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
