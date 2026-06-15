package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DefaultCounterMetricTest {

    private final RecordingLongCounter instrument = new RecordingLongCounter();
    private final AttributeValidator attributeValidator = new AttributeValidator();

    @Test
    void publishesValidCounterValueToLongCounter() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.builder().required("client").build(), 500),
                instrument,
                attributeValidator);

        metric.add(7, Map.of("client", "A"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(7);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("client"))).isEqualTo("A");
    }

    @Test
    void skipsNegativeCounterValue() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.empty(), 500),
                instrument,
                attributeValidator);

        metric.add(-1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsInvalidAttributes() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.builder().required("client").build(), 500),
                instrument,
                attributeValidator);

        metric.add(1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void disabledMetricIsNoOp() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(false, AttributesSchema.builder().required("client").build(), 500),
                instrument,
                attributeValidator);

        metric.add(1, Map.of());

        assertThat(instrument.callCount).isZero();
    }

    @Test
    void skipsNewSeriesAfterLimitIsReached() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.builder().required("client").build(), 1),
                instrument,
                attributeValidator);

        metric.add(1, Map.of("client", "A"));
        metric.add(2, Map.of("client", "B"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(1);
    }

    @Test
    void doesNotRetainCallerOwnedAttributeMap() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.builder().required("client").build(), 500),
                instrument,
                attributeValidator);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("client", "A");

        metric.add(1, attributes);
        attributes.put("client", "B");

        assertThat(instrument.attributes.get(AttributeKey.stringKey("client"))).isEqualTo("A");
    }

    @Test
    void publishExceptionDoesNotEscape() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.empty(), 500),
                new ThrowingLongCounter(),
                attributeValidator);

        assertThatCode(() -> metric.add(1, Map.of())).doesNotThrowAnyException();
    }

    @Test
    void validatorExceptionDoesNotEscapeAndSkipsPublish() {
        DefaultCounterMetric metric = new DefaultCounterMetric(
                resolved(true, AttributesSchema.builder().required("client").build(), 500),
                instrument,
                new ThrowingAttributeValidator());

        assertThatCode(() -> metric.add(1, Map.of("client", "A"))).doesNotThrowAnyException();

        assertThat(instrument.callCount).isZero();
    }

    private static ResolvedMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedMetricConfig(
                "orders-created",
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                enabled,
                "reflex.orders.created",
                "orders.created",
                ReflexMetricScopes.MANUAL,
                null,
                null,
                attributes,
                null,
                MetricKind.COUNTER,
                null,
                null,
                null,
                null,
                maxSeries,
                SeriesOverflowPolicy.FAIL,
                java.util.List.of());
    }

    private static final class RecordingLongCounter implements LongCounter {
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

    private static final class ThrowingLongCounter implements LongCounter {

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
