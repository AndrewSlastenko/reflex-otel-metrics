package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigValidatorTest {

    @Test
    void shouldRejectJdbcMetricWithoutDataSourceRef() {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.JDBC, MetricKind.GAUGE);

        config = new ResolvedMetricConfig(
                config.metricId(),
                config.source(),
                config.enabled(),
                config.exportedMetricName(),
                config.name(),
                config.scope(),
                config.description(),
                config.unit(),
                config.attributes(),
                null,
                config.metricKind(),
                config.schedule(),
                config.timeout(),
                config.lockAtMostFor(),
                config.lockAtLeastFor(),
                config.maxSeries(),
                config.overflowPolicy(),
                config.histogramBuckets());

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires dataSourceRef for JDBC source");
    }

    @Test
    void shouldRejectManualMetricWithDataSourceRef() {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.MANUAL, MetricKind.COUNTER);

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' must not set dataSourceRef for MANUAL source");
    }

    @Test
    void shouldRejectManualMetricWithAggregateToOtherOverflowPolicy() {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.MANUAL, MetricKind.COUNTER);
        config = new ResolvedMetricConfig(
                config.metricId(), config.source(), config.enabled(), config.exportedMetricName(), config.name(),
                config.scope(), config.description(), config.unit(), config.attributes(), null,
                config.metricKind(), config.schedule(), config.timeout(), config.lockAtMostFor(), config.lockAtLeastFor(),
                config.maxSeries(), SeriesOverflowPolicy.AGGREGATE_TO_OTHER, config.histogramBuckets());

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-by-status' does not support AGGREGATE_TO_OTHER overflow policy for MANUAL source; use FAIL or TRUNCATE"
                );
    }

    @Test
    void shouldRejectCronScheduleWithFixedDelay() {
        ResolvedMetricConfig config = withSchedule(new MetricScheduleSettings(
                MetricScheduleSettings.Mode.CRON,
                Duration.ofMinutes(5),
                "0 * * * *",
                Duration.ofSeconds(30)
        ));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' must not set fixedDelay for CRON mode");
    }

    @Test
    void shouldRejectFixedDelayScheduleWithCron() {
        ResolvedMetricConfig config = withSchedule(new MetricScheduleSettings(
                MetricScheduleSettings.Mode.FIXED_DELAY,
                Duration.ofMinutes(5),
                "0 * * * *",
                Duration.ofSeconds(30)
        ));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' must not set cron for FIXED_DELAY mode");
    }

    @Test
    void shouldRejectLockAtLeastForGreaterThanLockAtMostFor() {
        ResolvedMetricConfig config = withLocks(Duration.ofSeconds(1), Duration.ofSeconds(2));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-by-status' requires lockAtLeastFor to be less than or equal to lockAtMostFor"
                );
    }

    @Test
    void shouldRejectNonPositiveJdbcTimeout() {
        ResolvedMetricConfig config = withTimeout(Duration.ZERO);

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires timeout to be positive");
    }

    @Test
    void shouldRejectHistogramAggregateOverflowPolicy() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-latency",
                ReflexTelemetryProperties.MetricSourceType.JDBC,
                true,
                "ci05414726.documents.latency",
                "documents.latency",
                "business",
                null,
                "ms",
                AttributesSchema.empty(),
                "businessReplicaDataSource",
                MetricKind.HISTOGRAM,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(1), Duration.ZERO),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                List.of());

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-latency' does not support AGGREGATE_TO_OTHER overflow policy for HISTOGRAM kind; use FAIL or TRUNCATE"
                );
    }

    @Test
    void shouldRejectNonIncreasingHistogramBuckets() {
        ResolvedMetricConfig config = withHistogramBuckets(List.of(1d, 5d, 5d));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires histogram buckets to be strictly increasing");
    }

    @Test
    void shouldRejectHistogramBucketsOnNonHistogramMetric() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                ReflexTelemetryProperties.MetricSourceType.JDBC,
                true,
                "ci05414726.documents.by-status",
                "documents.by-status",
                "business",
                null,
                "1",
                AttributesSchema.empty(),
                "businessReplicaDataSource",
                MetricKind.GAUGE,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.FAIL,
                List.of(1d, 2d));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' must not set histogram buckets for GAUGE kind");
    }

    private static ResolvedMetricConfig baseConfig(
            ReflexTelemetryProperties.MetricSourceType source,
            MetricKind kind) {
        return new ResolvedMetricConfig(
                "documents-by-status",
                source,
                true,
                "ci05414726.documents.by-status",
                "documents.by-status",
                "business",
                null,
                "1",
                AttributesSchema.empty(),
                "businessReplicaDataSource",
                kind,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.FAIL,
                List.of());
    }

    private static ResolvedMetricConfig withSchedule(MetricScheduleSettings schedule) {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.JDBC, MetricKind.GAUGE);
        return new ResolvedMetricConfig(
                config.metricId(), config.source(), config.enabled(), config.exportedMetricName(), config.name(),
                config.scope(), config.description(), config.unit(), config.attributes(), config.dataSourceRef(),
                config.metricKind(), schedule, config.timeout(), config.lockAtMostFor(), config.lockAtLeastFor(),
                config.maxSeries(), config.overflowPolicy(), config.histogramBuckets());
    }

    private static ResolvedMetricConfig withLocks(Duration lockAtMostFor, Duration lockAtLeastFor) {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.JDBC, MetricKind.GAUGE);
        return new ResolvedMetricConfig(
                config.metricId(), config.source(), config.enabled(), config.exportedMetricName(), config.name(),
                config.scope(), config.description(), config.unit(), config.attributes(), config.dataSourceRef(),
                config.metricKind(), config.schedule(), config.timeout(), lockAtMostFor, lockAtLeastFor,
                config.maxSeries(), config.overflowPolicy(), config.histogramBuckets());
    }

    private static ResolvedMetricConfig withTimeout(Duration timeout) {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.JDBC, MetricKind.GAUGE);
        return new ResolvedMetricConfig(
                config.metricId(), config.source(), config.enabled(), config.exportedMetricName(), config.name(),
                config.scope(), config.description(), config.unit(), config.attributes(), config.dataSourceRef(),
                config.metricKind(), config.schedule(), timeout, config.lockAtMostFor(), config.lockAtLeastFor(),
                config.maxSeries(), config.overflowPolicy(), config.histogramBuckets());
    }

    private static ResolvedMetricConfig withHistogramBuckets(List<Double> buckets) {
        ResolvedMetricConfig config = baseConfig(ReflexTelemetryProperties.MetricSourceType.JDBC, MetricKind.HISTOGRAM);
        return new ResolvedMetricConfig(
                config.metricId(), config.source(), config.enabled(), config.exportedMetricName(), config.name(),
                config.scope(), config.description(), config.unit(), config.attributes(), config.dataSourceRef(),
                config.metricKind(), config.schedule(), config.timeout(), config.lockAtMostFor(), config.lockAtLeastFor(),
                config.maxSeries(), config.overflowPolicy(), buckets);
    }
}
