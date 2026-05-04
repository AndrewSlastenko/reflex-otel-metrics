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

public class ReflexMetricFactory {

    private final ManualMetricConfigResolver configResolver;
    private final OtelInstrumentRegistry instrumentRegistry;
    private final AttributeValidator attributeValidator;

    public ReflexMetricFactory(
            ManualMetricConfigResolver configResolver,
            OtelInstrumentRegistry instrumentRegistry,
            AttributeValidator attributeValidator) {
        this.configResolver = Objects.requireNonNull(configResolver, "configResolver must not be null");
        this.instrumentRegistry = Objects.requireNonNull(instrumentRegistry, "instrumentRegistry must not be null");
        this.attributeValidator = Objects.requireNonNull(attributeValidator, "attributeValidator must not be null");
    }

    public CounterMetric counter(String metricId, MetricDefinition definition) {
        ResolvedManualMetricConfig config = configResolver.resolve(metricId, MetricKind.COUNTER, definition);
        LongCounter instrument = (LongCounter) instrumentRegistry.getOrCreate(config.fullMetricName(), MetricKind.COUNTER);
        return new DefaultCounterMetric(config, instrument, attributeValidator);
    }

    public GaugeMetric gauge(String metricId, MetricDefinition definition) {
        ResolvedManualMetricConfig config = configResolver.resolve(metricId, MetricKind.GAUGE, definition);
        LongGauge instrument = (LongGauge) instrumentRegistry.getOrCreate(config.fullMetricName(), MetricKind.GAUGE);
        return new DefaultGaugeMetric(config, instrument, attributeValidator);
    }

    public UpDownCounterMetric upDownCounter(String metricId, MetricDefinition definition) {
        ResolvedManualMetricConfig config = configResolver.resolve(metricId, MetricKind.UP_DOWN_COUNTER, definition);
        LongUpDownCounter instrument = (LongUpDownCounter) instrumentRegistry.getOrCreate(
                config.fullMetricName(),
                MetricKind.UP_DOWN_COUNTER);
        return new DefaultUpDownCounterMetric(config, instrument, attributeValidator);
    }
}
