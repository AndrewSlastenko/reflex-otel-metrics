package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.ResolvedManualMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DefaultGaugeMetricTest {

    private final RecordingLongGauge instrument = new RecordingLongGauge();
    private final AttributeValidator attributeValidator = new AttributeValidator();

    @Test
    void publishesGaugeValueToLongGauge() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 500),
                instrument,
                attributeValidator);

        metric.set(-7, Map.of("queue", "primary"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(-7);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("queue"))).isEqualTo("primary");
    }

    @Test
    void disabledMetricIsNoOp() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(false, AttributesSchema.builder().required("queue").build(), 500),
                instrument,
                attributeValidator);

        metric.set(1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsInvalidAttributes() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 500),
                instrument,
                attributeValidator);

        metric.set(1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsNewSeriesAfterLimitIsReached() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 1),
                instrument,
                attributeValidator);

        metric.set(1, Map.of("queue", "primary"));
        metric.set(2, Map.of("queue", "secondary"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(1);
    }

    @Test
    void publishExceptionDoesNotEscape() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.empty(), 500),
                new ThrowingLongGauge(),
                attributeValidator);

        assertThatCode(() -> metric.set(1, Map.of())).doesNotThrowAnyException();
    }

    @Test
    void validatorExceptionDoesNotEscapeAndSkipsPublish() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 500),
                instrument,
                new ThrowingAttributeValidator());

        assertThatCode(() -> metric.set(1, Map.of("queue", "primary"))).doesNotThrowAnyException();

        assertThat(instrument.callCount).isZero();
    }

    private static ResolvedManualMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedManualMetricConfig(
                "queue-depth",
                enabled,
                "reflex.queue.depth",
                "queue.depth",
                ReflexMetricScopes.MANUAL,
                MetricKind.GAUGE,
                null,
                null,
                attributes,
                maxSeries,
                SeriesOverflowPolicy.FAIL);
    }

    private static final class RecordingLongGauge implements LongGauge {
        private int callCount;
        private long value;
        private Attributes attributes;

        @Override
        public void set(long value) {
            set(value, Attributes.empty());
        }

        @Override
        public void set(long value, Attributes attributes) {
            this.callCount++;
            this.value = value;
            this.attributes = attributes;
        }

        @Override
        public void set(long value, Attributes attributes, Context context) {
            set(value, attributes);
        }
    }

    private static final class ThrowingLongGauge implements LongGauge {

        @Override
        public void set(long value) {
            set(value, Attributes.empty());
        }

        @Override
        public void set(long value, Attributes attributes) {
            throw new RuntimeException("publish failed");
        }

        @Override
        public void set(long value, Attributes attributes, Context context) {
            set(value, attributes);
        }
    }

    private static final class ThrowingAttributeValidator extends AttributeValidator {

        @Override
        public AttributeValidationResult validate(AttributesSchema schema, Map<String, String> attributes) {
            throw new RuntimeException("validation failed");
        }
    }
}
