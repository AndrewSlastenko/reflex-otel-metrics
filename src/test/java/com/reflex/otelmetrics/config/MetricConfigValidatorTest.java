package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigValidatorTest {

    @Test
    void reportsMissingDataSourceRef() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "reflex.documents-by-status",
                "documents-by-status",
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
    void reportsMissingSuffix() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "reflex.",
                null,
                "business",
                "primaryDataSource",
                MetricKind.GAUGE,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires suffix");
    }

    @Test
    void reportsMissingFixedDelayForFixedDelaySchedule() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "reflex.documents-by-status",
                "documents-by-status",
                "business",
                "primaryDataSource",
                MetricKind.GAUGE,
                new MetricScheduleSettings(MetricScheduleSettings.Mode.FIXED_DELAY, null, null, Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires fixedDelay for FIXED_DELAY schedule mode");
    }

    @Test
    void reportsMissingCronForCronSchedule() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "reflex.documents-by-status",
                "documents-by-status",
                "business",
                "primaryDataSource",
                MetricKind.GAUGE,
                new MetricScheduleSettings(MetricScheduleSettings.Mode.CRON, null, null, Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires cron for CRON schedule mode");
    }
}
