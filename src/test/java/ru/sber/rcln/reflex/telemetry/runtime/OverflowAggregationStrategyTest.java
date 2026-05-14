package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverflowAggregationStrategyTest {

    @Test
    void shouldAggregateOverflowValuesForLongBasedKinds() {
        OverflowAggregationStrategy strategy = new OverflowAggregationStrategy();

        MetricPoint aggregated = strategy.aggregate(
                MetricKind.GAUGE,
                List.of(
                        new MetricPoint(2, Map.of("status", "b")),
                        new MetricPoint(3, Map.of("status", "c"))
                )
        );

        assertThat(aggregated.value()).isEqualTo(5);
        assertThat(aggregated.attributes()).containsEntry("bucket", "other");
    }

    @Test
    void shouldRejectHistogramAggregation() {
        OverflowAggregationStrategy strategy = new OverflowAggregationStrategy();

        assertThatThrownBy(() -> strategy.aggregate(
                MetricKind.HISTOGRAM,
                List.of(
                        MetricPoint.histogram(2.1, Map.of("status", "b")),
                        MetricPoint.histogram(3.2, Map.of("status", "c"))
                )
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AGGREGATE_TO_OTHER is not supported for HISTOGRAM metrics. Use FAIL or TRUNCATE overflow policy.");
    }
}
