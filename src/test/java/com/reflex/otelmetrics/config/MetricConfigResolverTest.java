package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigResolverTest {

    @Test
    void beanDefaultsShouldExposeAllOperationalFields() {
        MetricDefinitionDefaults definitionDefaults = new MetricDefinitionDefaults(
                "metrics",
                MetricKind.GAUGE,
                "application",
                "metricDataSource",
                new MetricScheduleDefaults(
                        MetricScheduleDefaults.Mode.FIXED_DELAY,
                        Duration.ofSeconds(5),
                        null,
                        Duration.ofSeconds(1)
                ),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofSeconds(10),
                100,
                SeriesOverflowPolicy.TRUNCATE
        );

        assertThat(definitionDefaults.metricSuffix()).isEqualTo("metrics");
        assertThat(definitionDefaults.metricKind()).isEqualTo(MetricKind.GAUGE);
        assertThat(definitionDefaults.scope()).isEqualTo("application");
        assertThat(definitionDefaults.dataSourceRef()).isEqualTo("metricDataSource");
        assertThat(definitionDefaults.schedule().mode()).isEqualTo(MetricScheduleDefaults.Mode.FIXED_DELAY);
        assertThat(definitionDefaults.schedule().fixedDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(definitionDefaults.schedule().cron()).isNull();
        assertThat(definitionDefaults.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(definitionDefaults.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(definitionDefaults.lockAtMostFor()).isEqualTo(Duration.ofMinutes(1));
        assertThat(definitionDefaults.lockAtLeastFor()).isEqualTo(Duration.ofSeconds(10));
        assertThat(definitionDefaults.maxSeries()).isEqualTo(100);
        assertThat(definitionDefaults.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.TRUNCATE);
    }
}
