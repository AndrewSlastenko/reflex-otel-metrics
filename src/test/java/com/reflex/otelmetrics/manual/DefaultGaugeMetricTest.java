package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGaugeMetricTest {

    @Test
    void publishesGaugeValueToLongGauge() {
        RecordingLongGauge instrument = new RecordingLongGauge();
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                new ResolvedManualMetricConfig(
                        "queue-depth",
                        true,
                        "reflex.queue.depth",
                        "queue.depth",
                        "default",
                        MetricKind.GAUGE,
                        null,
                        null,
                        AttributesSchema.builder().required("queue").build(),
                        500,
                        SeriesOverflowPolicy.FAIL),
                instrument,
                new AttributeValidator());

        metric.set(-7, Map.of("queue", "primary"));

        assertThat(instrument.callCount).isEqualTo(1);
        assertThat(instrument.value).isEqualTo(-7);
        assertThat(instrument.attributes.get(AttributeKey.stringKey("queue"))).isEqualTo("primary");
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
}
