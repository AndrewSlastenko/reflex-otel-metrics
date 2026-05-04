package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.UpDownCounterMetric;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultUpDownCounterMetric implements UpDownCounterMetric {

    private static final Logger log = LoggerFactory.getLogger(DefaultUpDownCounterMetric.class);

    private final ResolvedManualMetricConfig config;
    private final LongUpDownCounter instrument;
    private final AttributeValidator attributeValidator;
    private final ManualSeriesTracker seriesTracker;

    public DefaultUpDownCounterMetric(
            ResolvedManualMetricConfig config,
            LongUpDownCounter instrument,
            AttributeValidator attributeValidator) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.instrument = Objects.requireNonNull(instrument, "instrument must not be null");
        this.attributeValidator = Objects.requireNonNull(attributeValidator, "attributeValidator must not be null");
        this.seriesTracker = new ManualSeriesTracker(config.maxSeries(), config.overflowPolicy());
    }

    @Override
    public void add(long value, Map<String, String> attributes) {
        if (!config.enabled()) {
            return;
        }

        try {
            AttributeValidationResult validation = attributeValidator.validate(config.attributes(), attributes);
            if (!validation.valid()) {
                log.warn("Metric {} skipped invalid attributes: {}", config.metricId(), validation.message());
                return;
            }

            ManualSeriesTracker.Result tracked = seriesTracker.apply(validation.attributes());
            if (!tracked.accepted()) {
                log.warn("Metric {} skipped series: {}", config.metricId(), tracked.message());
                return;
            }

            instrument.add(value, toAttributes(tracked.attributes()));
        } catch (RuntimeException exception) {
            log.warn("Metric {} skipped up-down counter publish", config.metricId(), exception);
        }
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }
}
