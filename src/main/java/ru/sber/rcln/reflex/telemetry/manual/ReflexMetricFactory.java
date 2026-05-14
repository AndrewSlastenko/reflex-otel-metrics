package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.GaugeMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.UpDownCounterMetric;
import ru.sber.rcln.reflex.telemetry.config.ManualMetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ResolvedManualMetricConfig;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import java.util.function.Supplier;
import lombok.NonNull;

public class ReflexMetricFactory {

    private final ManualMetricConfigResolver configResolver;
    private final Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier;
    private final AttributeValidator attributeValidator;

    public ReflexMetricFactory(
            @NonNull ManualMetricConfigResolver configResolver,
            @NonNull OtelInstrumentRegistry instrumentRegistry,
            @NonNull AttributeValidator attributeValidator) {
        this(configResolver, instrumentRegistrySupplier(instrumentRegistry), attributeValidator);
    }

    public ReflexMetricFactory(
            @NonNull ManualMetricConfigResolver configResolver,
            @NonNull Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier,
            @NonNull AttributeValidator attributeValidator) {
        this.configResolver = configResolver;
        this.instrumentRegistrySupplier = instrumentRegistrySupplier;
        this.attributeValidator = attributeValidator;
    }

    public CounterMetric counter(String metricId, MetricDefinition definition) {
        ResolvedManualMetricConfig config = configResolver.resolve(metricId, MetricKind.COUNTER, definition);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        LongCounter instrument = (LongCounter) requireInstrumentRegistry().getOrCreate(
                config.fullMetricName(),
                MetricKind.COUNTER,
                config.description(),
                config.unit());
        return new DefaultCounterMetric(config, instrument, attributeValidator);
    }

    public GaugeMetric gauge(String metricId, MetricDefinition definition) {
        ResolvedManualMetricConfig config = configResolver.resolve(metricId, MetricKind.GAUGE, definition);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        LongGauge instrument = (LongGauge) requireInstrumentRegistry().getOrCreate(
                config.fullMetricName(),
                MetricKind.GAUGE,
                config.description(),
                config.unit());
        return new DefaultGaugeMetric(config, instrument, attributeValidator);
    }

    public UpDownCounterMetric upDownCounter(String metricId, MetricDefinition definition) {
        ResolvedManualMetricConfig config = configResolver.resolve(metricId, MetricKind.UP_DOWN_COUNTER, definition);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        LongUpDownCounter instrument = (LongUpDownCounter) requireInstrumentRegistry().getOrCreate(
                config.fullMetricName(),
                MetricKind.UP_DOWN_COUNTER,
                config.description(),
                config.unit());
        return new DefaultUpDownCounterMetric(config, instrument, attributeValidator);
    }

    private OtelInstrumentRegistry requireInstrumentRegistry() {
        OtelInstrumentRegistry instrumentRegistry = instrumentRegistrySupplier.get();
        if (instrumentRegistry == null) {
            throw new IllegalStateException(
                    "OtelInstrumentRegistry is required to create enabled manual metrics. "
                            + "Enable reflex.telemetry.metrics.enabled or provide an OtelInstrumentRegistry bean.");
        }
        return instrumentRegistry;
    }

    private static Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier(
            @NonNull OtelInstrumentRegistry instrumentRegistry) {
        return () -> instrumentRegistry;
    }
}
