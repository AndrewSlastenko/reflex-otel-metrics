package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeriesLimiterTest {

    @Test
    void shouldTruncateWhenConfigured() {
        SeriesLimiter limiter = new SeriesLimiter(new OverflowAggregationStrategy());
        List<MetricPoint> limited = limiter.apply(
                List.of(
                        new MetricPoint(1, Map.of("status", "a")),
                        new MetricPoint(2, Map.of("status", "b")),
                        new MetricPoint(3, Map.of("status", "c"))
                ),
                2,
                SeriesOverflowPolicy.TRUNCATE,
                MetricKind.GAUGE
        );

        assertThat(limited).hasSize(2);
    }

    @Test
    void shouldAggregateRemainderIntoOther() {
        SeriesLimiter limiter = new SeriesLimiter(new OverflowAggregationStrategy());
        List<MetricPoint> limited = limiter.apply(
                List.of(
                        new MetricPoint(1, Map.of("status", "a")),
                        new MetricPoint(2, Map.of("status", "b")),
                        new MetricPoint(3, Map.of("status", "c"))
                ),
                2,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                MetricKind.GAUGE
        );

        assertThat(limited).hasSize(2);
        assertThat(limited.get(0).value()).isEqualTo(1);
        assertThat(limited.get(1).value()).isEqualTo(5);
        assertThat(limited.get(1).attributes()).containsEntry("bucket", "other");
    }

    @Test
    void shouldRejectHistogramAggregateOverflowPolicy() {
        SeriesLimiter limiter = new SeriesLimiter(new OverflowAggregationStrategy());

        assertThatThrownBy(() -> limiter.apply(
                List.of(
                        MetricPoint.histogram(1.1, Map.of("status", "a")),
                        MetricPoint.histogram(1.2, Map.of("status", "b")),
                        MetricPoint.histogram(1.3, Map.of("status", "c"))
                ),
                2,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                MetricKind.HISTOGRAM
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AGGREGATE_TO_OTHER is not supported for HISTOGRAM metrics. Use FAIL or TRUNCATE overflow policy.");
    }

    @Test
    void shouldRejectNonPositiveMaxSeries() {
        SeriesLimiter limiter = new SeriesLimiter(new OverflowAggregationStrategy());

        assertThatThrownBy(() -> limiter.apply(
                List.of(new MetricPoint(1, Map.of("status", "a"))),
                0,
                SeriesOverflowPolicy.TRUNCATE,
                MetricKind.GAUGE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxSeries must be greater than 0");
    }
}
