package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DefaultHistogramMetricTest {

    private final RecordingDoubleHistogram instrument = new RecordingDoubleHistogram();
    private final AttributeValidator attributeValidator = new AttributeValidator();

    @Test
    void publishesHistogramValueToDoubleHistogram() {
        DefaultHistogramMetric metric = new DefaultHistogramMetric(
                resolved(true, AttributesSchema.builder().required("route").build(), 500),
                instrument,
                attributeValidator);

        metric.record(42.5d, Map.of("route", "/orders"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(42.5d);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("route"))).isEqualTo("/orders");
    }

    @Test
    void disabledMetricIsNoOp() {
        DefaultHistogramMetric metric = new DefaultHistogramMetric(
                resolved(false, AttributesSchema.builder().required("route").build(), 500),
                instrument,
                attributeValidator);

        metric.record(10d, Map.of("route", "/orders"));

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsInvalidAttributes() {
        DefaultHistogramMetric metric = new DefaultHistogramMetric(
                resolved(true, AttributesSchema.builder().required("route").build(), 500),
                instrument,
                attributeValidator);

        metric.record(10d, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsNewSeriesAfterLimitIsReached() {
        DefaultHistogramMetric metric = new DefaultHistogramMetric(
                resolved(true, AttributesSchema.builder().required("route").build(), 1),
                instrument,
                attributeValidator);

        metric.record(10d, Map.of("route", "/orders"));
        metric.record(12d, Map.of("route", "/payments"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(10d);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("route"))).isEqualTo("/orders");
    }

    @Test
    void publishExceptionDoesNotEscape() {
        DefaultHistogramMetric metric = new DefaultHistogramMetric(
                resolved(true, AttributesSchema.empty(), 500),
                new ThrowingDoubleHistogram(),
                attributeValidator);

        assertThatCode(() -> metric.record(10d, Map.of())).doesNotThrowAnyException();
    }

    @Test
    void validatorExceptionDoesNotEscapeAndSkipsPublish() {
        DefaultHistogramMetric metric = new DefaultHistogramMetric(
                resolved(true, AttributesSchema.builder().required("route").build(), 500),
                instrument,
                new ThrowingAttributeValidator());

        assertThatCode(() -> metric.record(10d, Map.of("route", "/orders"))).doesNotThrowAnyException();

        assertThat(instrument.callCount).isZero();
    }

    private static ResolvedMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedMetricConfig(
                "request-latency",
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                enabled,
                "reflex.request.latency",
                "request.latency",
                ReflexMetricScopes.MANUAL,
                null,
                null,
                attributes,
                null,
                MetricKind.HISTOGRAM,
                null,
                null,
                null,
                null,
                maxSeries,
                SeriesOverflowPolicy.FAIL,
                java.util.List.of());
    }

    private static final class RecordingDoubleHistogram implements DoubleHistogram {
        private int callCount;
        private double value;
        private Attributes attributes;

        @Override
        public void record(double value) {
            record(value, Attributes.empty());
        }

        @Override
        public void record(double value, Attributes attributes) {
            this.callCount++;
            this.value = value;
            this.attributes = attributes;
        }

        @Override
        public void record(double value, Attributes attributes, Context context) {
            record(value, attributes);
        }
    }

    private static final class ThrowingDoubleHistogram implements DoubleHistogram {
        @Override
        public void record(double value) {
            record(value, Attributes.empty());
        }

        @Override
        public void record(double value, Attributes attributes) {
            throw new RuntimeException("publish failed");
        }

        @Override
        public void record(double value, Attributes attributes, Context context) {
            record(value, attributes);
        }
    }

    private static final class ThrowingAttributeValidator extends AttributeValidator {
        @Override
        public AttributeValidationResult validate(AttributesSchema schema, Map<String, String> attributes) {
            throw new RuntimeException("validation failed");
        }
    }
}
