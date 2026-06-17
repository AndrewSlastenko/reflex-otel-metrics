package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.GaugeMetric;
import ru.sber.rcln.reflex.telemetry.api.HistogramMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.UpDownCounterMetric;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.otel.MetricInstrumentWriter;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import java.util.function.Supplier;
import lombok.NonNull;

public class ReflexMetricFactory {

    private final MetricConfigResolver configResolver;
    private final Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier;
    private final AttributeValidator attributeValidator;

    public ReflexMetricFactory(
            @NonNull MetricConfigResolver configResolver,
            @NonNull OtelInstrumentRegistry instrumentRegistry,
            @NonNull AttributeValidator attributeValidator) {
        this(configResolver, instrumentRegistrySupplier(instrumentRegistry), attributeValidator);
    }

    public ReflexMetricFactory(
            @NonNull MetricConfigResolver configResolver,
            @NonNull Supplier<OtelInstrumentRegistry> instrumentRegistrySupplier,
            @NonNull AttributeValidator attributeValidator) {
        this.configResolver = configResolver;
        this.instrumentRegistrySupplier = instrumentRegistrySupplier;
        this.attributeValidator = attributeValidator;
    }

    public CounterMetric counter(String metricId) {
        ResolvedMetricConfig config = configResolver.resolveManual(metricId, MetricKind.COUNTER);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        LongCounter instrument = (LongCounter) requireInstrumentRegistry().getOrCreate(
                config.exportedMetricName(),
                MetricKind.COUNTER,
                config.description(),
                config.unit());
        return new DefaultCounterMetric(config, instrument, attributeValidator);
    }

    public GaugeMetric gauge(String metricId) {
        ResolvedMetricConfig config = configResolver.resolveManual(metricId, MetricKind.GAUGE);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        MetricInstrumentWriter writer = requireInstrumentRegistry().getOrCreateWriter(
                config.exportedMetricName(),
                MetricKind.GAUGE,
                config.description(),
                config.unit());
        return new DefaultGaugeMetric(config, writer, attributeValidator);
    }

    public UpDownCounterMetric upDownCounter(String metricId) {
        ResolvedMetricConfig config = configResolver.resolveManual(metricId, MetricKind.UP_DOWN_COUNTER);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        LongUpDownCounter instrument = (LongUpDownCounter) requireInstrumentRegistry().getOrCreate(
                config.exportedMetricName(),
                MetricKind.UP_DOWN_COUNTER,
                config.description(),
                config.unit());
        return new DefaultUpDownCounterMetric(config, instrument, attributeValidator);
    }

    public HistogramMetric histogram(String metricId) {
        ResolvedMetricConfig config = configResolver.resolveManual(metricId, MetricKind.HISTOGRAM);
        if (!config.enabled()) {
            return (value, attributes) -> {
            };
        }

        DoubleHistogram instrument = (DoubleHistogram) requireInstrumentRegistry().getOrCreate(
                config.exportedMetricName(),
                MetricKind.HISTOGRAM,
                config.description(),
                config.unit());
        return new DefaultHistogramMetric(config, instrument, attributeValidator);
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
