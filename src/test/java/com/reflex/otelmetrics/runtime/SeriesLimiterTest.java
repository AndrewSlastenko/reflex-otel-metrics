package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
                SeriesOverflowPolicy.TRUNCATE
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
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(limited).hasSize(2);
        assertThat(limited.get(0).value()).isEqualTo(1);
        assertThat(limited.get(1).value()).isEqualTo(5);
        assertThat(limited.get(1).attributes()).containsEntry("bucket", "other");
    }
}
