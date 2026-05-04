package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OtelInstrumentRegistry {

    private final @NonNull Meter meter;
    private final Map<String, RegisteredInstrument> instruments = new ConcurrentHashMap<>();

    public Object getOrCreate(String name, MetricKind kind) {
        return getOrCreate(name, kind, null, null);
    }

    public Object getOrCreate(@NonNull String name, @NonNull MetricKind kind, String description, String unit) {
        return instruments.compute(name, (key, existing) -> {
            if (existing != null) {
                if (existing.kind() != kind) {
                    throw new IllegalStateException(
                            "Metric '" + name + "' is already registered as " + existing.kind() + " but requested as " + kind
                    );
                }

                return existing;
            }

            return new RegisteredInstrument(kind, switch (kind) {
                case COUNTER -> createCounter(name, description, unit);
                case GAUGE -> createGauge(name, description, unit);
                case UP_DOWN_COUNTER -> createUpDownCounter(name, description, unit);
            });
        }).instrument();
    }

    private LongCounter createCounter(String name, String description, String unit) {
        LongCounterBuilder builder = meter.counterBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        return builder.build();
    }

    private LongGauge createGauge(String name, String description, String unit) {
        DoubleGaugeBuilder builder = meter.gaugeBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        return builder.ofLongs().build();
    }

    private LongUpDownCounter createUpDownCounter(String name, String description, String unit) {
        LongUpDownCounterBuilder builder = meter.upDownCounterBuilder(name);
        if (hasText(description)) {
            builder = builder.setDescription(description);
        }
        if (hasText(unit)) {
            builder = builder.setUnit(unit);
        }
        return builder.build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RegisteredInstrument(MetricKind kind, Object instrument) {
    }
}
