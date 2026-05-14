package ru.sber.rcln.reflex.telemetry.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricPointTest {

    @Test
    void keepsLegacyLongValueAccess() {
        MetricPoint point = new MetricPoint(42L, Map.of("scope", "orders"));

        assertThat(point.value()).isEqualTo(42L);
        assertThat(point.metricValue().type()).isEqualTo(MetricValue.ValueType.LONG);
        assertThat(point.metricValue().asDouble()).isEqualTo(42.0d);
    }

    @Test
    void createsHistogramPointWithDoubleValue() {
        MetricPoint point = MetricPoint.histogram(12.5d, Map.of("scope", "orders"));

        assertThat(point.metricValue().type()).isEqualTo(MetricValue.ValueType.DOUBLE);
        assertThat(point.asDoubleValue()).isEqualTo(12.5d);
        assertThatThrownBy(point::value)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be represented as long");
    }

    @Test
    void preservesValueBasedEqualityForExistingConsumers() {
        MetricPoint left = new MetricPoint(7L, Map.of("status", "created"));
        MetricPoint right = new MetricPoint(7L, Map.of("status", "created"));

        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }
}
