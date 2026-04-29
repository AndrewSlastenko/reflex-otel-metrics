package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigResolverTest {

    @Test
    void beanDefaultOperationalFields() {
        MetricDefinitionDefaults definitionDefaults = new MetricDefinitionDefaults(
                true,
                MetricKind.GAUGE,
                SeriesOverflowPolicy.DROP_OLDEST
        );
        MetricScheduleDefaults scheduleDefaults = new MetricScheduleDefaults(
                Duration.ofSeconds(5),
                Duration.ZERO,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5)
        );
        QueryDefinition query = new QueryDefinition(
                "activeUsers",
                "select count(*) from users",
                MetricKind.GAUGE,
                SeriesOverflowPolicy.DROP_OLDEST
        );
        JdbcMetricSource source = new JdbcMetricSource(
                "users-metrics",
                "metricDataSource",
                definitionDefaults,
                scheduleDefaults,
                List.of(query)
        );
        MetricPoint point = new MetricPoint(
                "activeUsers",
                MetricKind.GAUGE,
                12.0,
                Map.of("env", "test"),
                Instant.parse("2026-04-29T00:00:00Z")
        );

        assertThat(source.name()).isEqualTo("users-metrics");
        assertThat(source.dataSourceBeanName()).isEqualTo("metricDataSource");
        assertThat(source.definitionDefaults().enabled()).isTrue();
        assertThat(source.definitionDefaults().kind()).isEqualTo(MetricKind.GAUGE);
        assertThat(source.definitionDefaults().seriesOverflowPolicy()).isEqualTo(SeriesOverflowPolicy.DROP_OLDEST);
        assertThat(source.scheduleDefaults().initialDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(source.scheduleDefaults().fixedDelay()).isEqualTo(Duration.ZERO);
        assertThat(source.scheduleDefaults().lockAtMostFor()).isEqualTo(Duration.ofMinutes(1));
        assertThat(source.scheduleDefaults().lockAtLeastFor()).isEqualTo(Duration.ofSeconds(5));
        assertThat(source.queries()).containsExactly(query);
        assertThat(point.name()).isEqualTo("activeUsers");
        assertThat(point.attributes()).containsEntry("env", "test");
    }
}
