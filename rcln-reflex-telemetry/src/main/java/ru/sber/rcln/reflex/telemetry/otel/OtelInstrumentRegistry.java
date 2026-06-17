package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtelInstrumentRegistry {

    private final @NonNull Meter meter;
    private final @NonNull GaugeSeriesStore gaugeSeriesStore;
    private final Map<String, RegisteredInstrument> instruments = new ConcurrentHashMap<>();

    public OtelInstrumentRegistry(@NonNull Meter meter) {
        this(meter, new GaugeSeriesStore());
    }

    public void clearGauge(@NonNull String metricName) {
        gaugeSeriesStore.clear(metricName);
    }

    public void replaceGaugeSnapshot(@NonNull String metricName, @NonNull List<MetricPoint> points) {
        Map<Attributes, Long> snapshot = new HashMap<>();
        for (MetricPoint point : points) {
            snapshot.put(toAttributes(point.attributes()), point.value());
        }
        gaugeSeriesStore.replaceSnapshot(metricName, snapshot);
    }

    public Object getOrCreate(String name, MetricKind kind) {
        return getOrCreate(name, kind, null, null);
    }

    public Object getOrCreate(@NonNull String name, @NonNull MetricKind kind, String description, String unit) {
        if (kind == MetricKind.GAUGE) {
            throw new IllegalStateException("Gauge instruments are observable; use getOrCreateWriter(...)");
        }
        return getOrCreateRegistered(name, kind, description, unit).instrument();
    }

    public MetricInstrumentWriter getOrCreateWriter(String name, MetricKind kind) {
        return getOrCreateWriter(name, kind, null, null);
    }

    public MetricInstrumentWriter getOrCreateWriter(
            @NonNull String name,
            @NonNull MetricKind kind,
            String description,
            String unit) {
        return getOrCreateRegistered(name, kind, description, unit).writer();
    }

    private RegisteredInstrument getOrCreateRegistered(
            @NonNull String name,
            @NonNull MetricKind kind,
            String description,
            String unit) {
        return instruments.compute(name, (key, existing) -> {
            if (existing != null) {
                if (existing.kind() != kind) {
                    throw new IllegalStateException(
                            "Metric '" + name + "' is already registered as " + existing.kind() + " but requested as " + kind
                    );
                }

                return existing;
            }

            return switch (kind) {
                case COUNTER -> registerCounter(name, description, unit);
                case GAUGE -> registerGauge(name, description, unit);
                case UP_DOWN_COUNTER -> registerUpDownCounter(name, description, unit);
                case HISTOGRAM -> registerHistogram(name, description, unit);
            };
        });
    }

    private RegisteredInstrument registerCounter(String name, String description, String unit) {
        LongCounterBuilder builder = meter.counterBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        LongCounter counter = builder.build();
        return new RegisteredInstrument(
                MetricKind.COUNTER,
                counter,
                (point, attributes) -> counter.add(point.value(), attributes));
    }

    private RegisteredInstrument registerGauge(String name, String description, String unit) {
        DoubleGaugeBuilder builder = meter.gaugeBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        builder.ofLongs().buildWithCallback(measurement -> gaugeSeriesStore.snapshot(name)
                .forEach((attributes, value) -> measurement.record(value, attributes)));
        return new RegisteredInstrument(
                MetricKind.GAUGE,
                null,
                (point, attributes) -> gaugeSeriesStore.put(name, attributes, point.value()));
    }

    private RegisteredInstrument registerUpDownCounter(String name, String description, String unit) {
        LongUpDownCounterBuilder builder = meter.upDownCounterBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        LongUpDownCounter counter = builder.build();
        return new RegisteredInstrument(
                MetricKind.UP_DOWN_COUNTER,
                counter,
                (point, attributes) -> counter.add(point.value(), attributes));
    }

    private RegisteredInstrument registerHistogram(String name, String description, String unit) {
        DoubleHistogramBuilder builder = meter.histogramBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        DoubleHistogram histogram = builder.build();
        return new RegisteredInstrument(
                MetricKind.HISTOGRAM,
                histogram,
                (point, attributes) -> histogram.record(point.asDoubleValue(), attributes));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        io.opentelemetry.api.common.AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }

    private record RegisteredInstrument(MetricKind kind, Object instrument, MetricInstrumentWriter writer) {
    }
}
