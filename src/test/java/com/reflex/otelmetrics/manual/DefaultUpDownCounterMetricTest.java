package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static ResolvedManualMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedManualMetricConfig(
                "workers-active",
                enabled,
                "reflex.workers.active",
                "workers.active",
                "default",
                MetricKind.UP_DOWN_COUNTER,
                null,
                null,
                attributes,
                maxSeries,
                SeriesOverflowPolicy.FAIL);
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
}
