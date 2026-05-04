package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
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

    private static ResolvedManualMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedManualMetricConfig(
                "orders-created",
                enabled,
                "reflex.orders.created",
                "orders.created",
                "default",
                MetricKind.COUNTER,
                null,
                null,
                attributes,
                maxSeries,
                SeriesOverflowPolicy.FAIL);
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
}
