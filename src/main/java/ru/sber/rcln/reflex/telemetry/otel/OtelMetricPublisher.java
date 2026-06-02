package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.Attributes;

import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtelMetricPublisher {

    private final @NonNull OtelInstrumentRegistry registry;

    public void publish(@NonNull ResolvedMetricConfig config, @NonNull List<MetricPoint> points) {

        MetricInstrumentWriter writer = registry.getOrCreateWriter(
                config.fullMetricName(),
                config.metricKind(),
                config.description(),
                config.unit());
        for (MetricPoint point : points) {
            Attributes attributes = toAttributes(point.attributes());
            writer.record(point, attributes);
        }
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        io.opentelemetry.api.common.AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }
}
