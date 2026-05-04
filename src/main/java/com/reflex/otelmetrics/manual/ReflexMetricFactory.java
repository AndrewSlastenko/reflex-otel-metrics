package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.api.GaugeMetric;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.UpDownCounterMetric;
import com.reflex.otelmetrics.config.ManualMetricConfigResolver;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import com.reflex.otelmetrics.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import java.util.Objects;
import java.util.function.Supplier;

public class ReflexMetricFactory {

    private final ManualMetricConfigResolver configResolver;
    private final Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier;
    private final AttributeValidator attributeValidator;

    public ReflexMetricFactory(
            ManualMetricConfigResolver configResolver,
            OtelInstrumentRegistry instrumentRegistry,
            AttributeValidator attributeValidator) {
        this(configResolver, instrumentRegistrySupplier(instrumentRegistry), attributeValidator);
    }

    public ReflexMetricFactory(
            ManualMetricConfigResolver configResolver,
            Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier,
            AttributeValidator attributeValidator) {
        this.configResolver = Objects.requireNonNull(configResolver, "configResolver must not be null");
        this.instrumentRegistrySupplier = Objects.requireNonNull(
                instrumentRegistrySupplier,
                "instrumentRegistrySupplier must not be null");
        this.attributeValidator = Objects.requireNonNull(attributeValidator, "attributeValidator must not be null");
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
                            + "Enable reflex.otel.metrics.enabled or provide an OtelInstrumentRegistry bean.");
        }
        return instrumentRegistry;
    }

    private static Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier(
            OtelInstrumentRegistry instrumentRegistry) {
        Objects.requireNonNull(instrumentRegistry, "instrumentRegistry must not be null");
        return () -> instrumentRegistry;
    }
}
