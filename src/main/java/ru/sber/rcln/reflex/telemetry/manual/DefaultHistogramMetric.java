package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.HistogramMetric;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.HandledExceptionLogging;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHistogramMetric implements HistogramMetric {

    private static final Logger log = LoggerFactory.getLogger(DefaultHistogramMetric.class);

    private final ResolvedMetricConfig config;
    private final DoubleHistogram instrument;
    private final AttributeValidator attributeValidator;
    private final ManualSeriesTracker seriesTracker;

    public DefaultHistogramMetric(
            @NonNull ResolvedMetricConfig config,
            @NonNull DoubleHistogram instrument,
            @NonNull AttributeValidator attributeValidator) {
        this.config = config;
        this.instrument = instrument;
        this.attributeValidator = attributeValidator;
        this.seriesTracker = new ManualSeriesTracker(config.maxSeries(), config.overflowPolicy());
    }

    @Override
    public void record(double value, Map<String, String> attributes) {
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

            instrument.record(value, toAttributes(tracked.attributes()));
        } catch (RuntimeException exception) {
            HandledExceptionLogging.warnSkippedManualPublish(log, config.metricId(), "histogram", exception);
        }
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach(builder::put);
        return builder.build();
    }
}
