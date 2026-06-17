package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.GaugeMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.HandledExceptionLogging;
import ru.sber.rcln.reflex.telemetry.otel.MetricInstrumentWriter;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultGaugeMetric implements GaugeMetric {

    private static final Logger log = LoggerFactory.getLogger(DefaultGaugeMetric.class);

    private final ResolvedMetricConfig config;
    private final MetricInstrumentWriter writer;
    private final AttributeValidator attributeValidator;
    private final ManualSeriesTracker seriesTracker;

    public DefaultGaugeMetric(
            @NonNull ResolvedMetricConfig config,
            @NonNull MetricInstrumentWriter writer,
            @NonNull AttributeValidator attributeValidator) {
        this.config = config;
        this.writer = writer;
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

            Attributes otelAttributes = toAttributes(tracked.attributes());
            writer.record(new MetricPoint(value, tracked.attributes()), otelAttributes);
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
