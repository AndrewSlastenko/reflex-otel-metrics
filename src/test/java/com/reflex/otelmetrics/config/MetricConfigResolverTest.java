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
                "documents.by.status",
                MetricKind.UP_DOWN_COUNTER,
                "business",
                "businessReplicaDataSource",
                new MetricScheduleDefaults(
                        MetricScheduleDefaults.Mode.FIXED_DELAY,
                        Duration.ofMinutes(5),
                        null,
                        Duration.ofSeconds(30)
                ),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(definitionDefaults.metricSuffix()).isEqualTo("documents.by.status");
        assertThat(definitionDefaults.metricKind()).isEqualTo(MetricKind.UP_DOWN_COUNTER);
        assertThat(definitionDefaults.scope()).isEqualTo("business");
        assertThat(definitionDefaults.dataSourceRef()).isEqualTo("businessReplicaDataSource");
        assertThat(definitionDefaults.maxSeries()).isEqualTo(500);
    }
}
