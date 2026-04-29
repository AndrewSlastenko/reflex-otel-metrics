package com.reflex.otelmetrics.config;

import java.time.Duration;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigValidatorTest {

    @Test
    void shouldRejectJdbcMetricWithoutDataSourceRef() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci054147.documents.current",
                "documents.current",
                "business",
                null,
                MetricKind.GAUGE,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires dataSourceRef");
    }

    @Test
    void shouldRejectCronScheduleWithFixedDelay() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci054147.documents.current",
                "documents.current",
                "business",
                "businessReplicaDataSource",
                MetricKind.GAUGE,
                new MetricScheduleSettings(
                        MetricScheduleSettings.Mode.CRON,
                        Duration.ofMinutes(5),
                        "0 * * * *",
                        Duration.ofSeconds(30)
                ),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' must not set fixedDelay for CRON mode");
    }

    @Test
    void shouldRejectFixedDelayScheduleWithCron() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci054147.documents.current",
                "documents.current",
                "business",
                "businessReplicaDataSource",
                MetricKind.GAUGE,
                new MetricScheduleSettings(
                        MetricScheduleSettings.Mode.FIXED_DELAY,
                        Duration.ofMinutes(5),
                        "0 * * * *",
                        Duration.ofSeconds(30)
                ),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' must not set cron for FIXED_DELAY mode");
    }
}
