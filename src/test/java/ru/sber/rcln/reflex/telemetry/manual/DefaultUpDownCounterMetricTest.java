package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DefaultUpDownCounterMetricTest {

    private final RecordingLongUpDownCounter instrument = new RecordingLongUpDownCounter();
    private final AttributeValidator attributeValidator = new AttributeValidator();

    @Test
    void publishesPositiveValueToLongUpDownCounter() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(true, AttributesSchema.builder().required("worker").build(), 500),
                instrument,
                attributeValidator);

        metric.add(3, Map.of("worker", "A"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(3);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("worker"))).isEqualTo("A");
    }

    @Test
    void publishesNegativeValueToLongUpDownCounter() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(true, AttributesSchema.builder().required("worker").build(), 500),
                instrument,
                attributeValidator);

        metric.add(-2, Map.of("worker", "A"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(-2);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("worker"))).isEqualTo("A");
    }

    @Test
    void skipsInvalidAttributes() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(true, AttributesSchema.builder().required("worker").build(), 500),
                instrument,
                attributeValidator);

        metric.add(1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void disabledMetricIsNoOp() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(false, AttributesSchema.builder().required("worker").build(), 500),
                instrument,
                attributeValidator);

        metric.add(1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsNewSeriesAfterLimitIsReached() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(true, AttributesSchema.builder().required("client").build(), 1),
                instrument,
                attributeValidator);

        metric.add(1, Map.of("client", "A"));
        metric.add(2, Map.of("client", "B"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(1);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("client"))).isEqualTo("A");
    }

    @Test
    void publishExceptionDoesNotEscape() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(true, AttributesSchema.empty(), 500),
                new ThrowingLongUpDownCounter(),
                attributeValidator);

        assertThatCode(() -> metric.add(1, Map.of())).doesNotThrowAnyException();
    }

    @Test
    void validatorExceptionDoesNotEscapeAndSkipsPublish() {
        DefaultUpDownCounterMetric metric = new DefaultUpDownCounterMetric(
                resolved(true, AttributesSchema.builder().required("worker").build(), 500),
                instrument,
                new ThrowingAttributeValidator());

        assertThatCode(() -> metric.add(1, Map.of("worker", "A"))).doesNotThrowAnyException();

        assertThat(instrument.callCount).isZero();
    }

    private static ResolvedMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedMetricConfig(
                "workers-active",
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                enabled,
                "reflex.workers.active",
                "workers.active",
                ReflexMetricScopes.MANUAL,
                null,
                null,
                attributes,
                null,
                MetricKind.UP_DOWN_COUNTER,
                null,
                null,
                null,
                null,
                maxSeries,
                SeriesOverflowPolicy.FAIL,
                java.util.List.of());
    }

    private static final class RecordingLongUpDownCounter implements LongUpDownCounter {
        private int callCount;
        private long value;
        private Attributes attributes;

        @Override
        public void add(long value) {
            add(value, Attributes.empty());
        }

        @Override
        public void add(long value, Attributes attributes) {
            this.callCount++;
            this.value = value;
            this.attributes = attributes;
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
            add(value, attributes);
        }
    }

    private static final class ThrowingLongUpDownCounter implements LongUpDownCounter {

        @Override
        public void add(long value) {
            add(value, Attributes.empty());
        }

        @Override
        public void add(long value, Attributes attributes) {
            throw new RuntimeException("publish failed");
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
            add(value, attributes);
        }
    }

    private static final class ThrowingAttributeValidator extends AttributeValidator {

        @Override
        public AttributeValidationResult validate(AttributesSchema schema, Map<String, String> attributes) {
            throw new RuntimeException("validation failed");
        }
    }
}
