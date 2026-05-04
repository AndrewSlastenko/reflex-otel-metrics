package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.GaugeMetric;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import com.reflex.otelmetrics.internal.HandledExceptionLogging;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultGaugeMetric implements GaugeMetric {

    private static final Logger log = LoggerFactory.getLogger(DefaultGaugeMetric.class);

    private final ResolvedManualMetricConfig config;
    private final LongGauge instrument;
    private final AttributeValidator attributeValidator;
    private final ManualSeriesTracker seriesTracker;

    public DefaultGaugeMetric(
            @NonNull ResolvedManualMetricConfig config,
            @NonNull LongGauge instrument,
            @NonNull AttributeValidator attributeValidator) {
        this.config = config;
        this.instrument = instrument;
        this.attributeValidator = attributeValidator;
        this.seriesTracker = new ManualSeriesTracker(config.maxSeries(), config.overflowPolicy());
    }

    @Override
    public void set(long value, Map<String, String> attributes) {
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

            instrument.set(value, toAttributes(tracked.attributes()));
        } catch (RuntimeException exception) {
            HandledExceptionLogging.warnSkippedManualPublish(log, config.metricId(), "gauge", exception);
        }
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }
}
