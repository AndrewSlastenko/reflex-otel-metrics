package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import com.reflex.otelmetrics.internal.HandledExceptionLogging;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCounterMetric implements CounterMetric {

    private static final Logger log = LoggerFactory.getLogger(DefaultCounterMetric.class);

    private final ResolvedManualMetricConfig config;
    private final LongCounter instrument;
    private final AttributeValidator attributeValidator;
    private final ManualSeriesTracker seriesTracker;

    public DefaultCounterMetric(
            @NonNull ResolvedManualMetricConfig config,
            @NonNull LongCounter instrument,
            @NonNull AttributeValidator attributeValidator) {
        this.config = config;
        this.instrument = instrument;
        this.attributeValidator = attributeValidator;
        this.seriesTracker = new ManualSeriesTracker(config.maxSeries(), config.overflowPolicy());
    }

    @Override
    public void add(long value, Map<String, String> attributes) {
        if (!config.enabled()) {
            return;
        }
        if (value < 0) {
            log.warn("Metric {} skipped negative counter value {}", config.metricId(), value);
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
            HandledExceptionLogging.warnSkippedManualPublish(log, config.metricId(), "counter", exception);
        }
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }
}
