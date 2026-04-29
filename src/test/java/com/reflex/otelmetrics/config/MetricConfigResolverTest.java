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

    @Test
    void propertiesShouldOverrideBeanDefaults() {
        MetricDefinitionDefaults beanDefaults = new MetricDefinitionDefaults(
                "documents.by.status",
                MetricKind.GAUGE,
                "business",
                "primaryDataSource",
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
        MetricRuntimeProperties properties = new MetricRuntimeProperties(
                "documents.by.status.override",
                null,
                "finance",
                "reportingDataSource",
                null,
                Duration.ofSeconds(15),
                Duration.ofMinutes(12),
                null,
                250,
                null
        );

        ResolvedMetricConfig resolved = new MetricConfigResolver().resolve(beanDefaults, properties);

        assertThat(resolved.metricSuffix()).isEqualTo("documents.by.status.override");
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.GAUGE);
        assertThat(resolved.scope()).isEqualTo("finance");
        assertThat(resolved.dataSourceRef()).isEqualTo("reportingDataSource");
        assertThat(resolved.schedule()).isEqualTo(beanDefaults.schedule());
        assertThat(resolved.timeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(resolved.lockAtMostFor()).isEqualTo(Duration.ofMinutes(12));
        assertThat(resolved.lockAtLeastFor()).isEqualTo(Duration.ZERO);
        assertThat(resolved.maxSeries()).isEqualTo(250);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
    }
}
