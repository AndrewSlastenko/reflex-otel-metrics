package ru.sber.rcln.reflex.telemetry.config;

import java.time.Duration;
import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigValidatorTest {

    @Test
    void shouldRejectJdbcMetricWithoutDataSourceRef() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci05414726.documents.current",
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
                "ci05414726.documents.current",
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
                "ci05414726.documents.current",
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

    @Test
    void shouldRejectNullLockDurations() {
        ResolvedMetricConfig config = baseConfig(null, null);

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-by-status' requires lockAtMostFor",
                        "Metric 'documents-by-status' requires lockAtLeastFor"
                );
    }

    @Test
    void shouldRejectNegativeLockDurations() {
        ResolvedMetricConfig config = baseConfig(Duration.ofSeconds(-1), Duration.ofSeconds(-2));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-by-status' requires lockAtMostFor to be non-negative",
                        "Metric 'documents-by-status' requires lockAtLeastFor to be non-negative"
                );
    }

    @Test
    void shouldRejectLockAtLeastForGreaterThanLockAtMostFor() {
        ResolvedMetricConfig config = baseConfig(Duration.ofSeconds(1), Duration.ofSeconds(2));

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-by-status' requires lockAtLeastFor to be less than or equal to lockAtMostFor"
                );
    }

    @Test
    void shouldRejectHistogramAggregateOverflowPolicy() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-latency",
                true,
                "ci05414726.documents.latency",
                "documents.latency",
                "business",
                "businessReplicaDataSource",
                MetricKind.HISTOGRAM,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(1), Duration.ZERO),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly(
                        "Metric 'documents-latency' does not support AGGREGATE_TO_OTHER overflow policy for HISTOGRAM kind; use FAIL or TRUNCATE"
                );
    }

    @Test
    void runtimeCronSwitchShouldValidateAgainstFixedDelayDefault() {
        ReflexTelemetryProperties properties = baseProperties();
        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.CRON);
        runtimeProperties.setCron("0 * * * *");
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new FixedDelayMetricSource());

        assertThat(new MetricConfigValidator().validate(resolved)).isEmpty();
    }

    @Test
    void runtimeFixedDelaySwitchShouldValidateAgainstCronDefault() {
        ReflexTelemetryProperties properties = baseProperties();
        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.FIXED_DELAY);
        runtimeProperties.setFixedDelay(Duration.ofMinutes(2));
        properties.getMetrics().getSources().put("cron-metric", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new CronMetricSource());

        assertThat(new MetricConfigValidator().validate(resolved)).isEmpty();
    }

    private static ResolvedMetricConfig baseConfig(Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci05414726.documents.current",
                "documents.current",
                "business",
                "businessReplicaDataSource",
                MetricKind.GAUGE,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                lockAtMostFor,
                lockAtLeastFor,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );
    }

    private static ReflexTelemetryProperties baseProperties() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.setSystemCode("ci05414726");
        properties.getMetrics().getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(true));
        return properties;
    }

    private static final class FixedDelayMetricSource implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "documents-by-status";
        }

        @Override
        public MetricDefinitionDefaults defaults() {
            return new MetricDefinitionDefaults(
                    "documents.by.status",
                    MetricKind.GAUGE,
                    "business",
                    "businessReplicaDataSource",
                    new MetricScheduleDefaults(
                            MetricScheduleDefaults.Mode.FIXED_DELAY,
                            Duration.ofMinutes(5),
                            null,
                            Duration.ofSeconds(10)
                    ),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(10),
                    Duration.ZERO,
                    500,
                    SeriesOverflowPolicy.AGGREGATE_TO_OTHER
            );
        }

        @Override
        public QueryDefinition queryDefinition() {
            return new QueryDefinition("select 1");
        }

        @Override
        public RowMapper<MetricPoint> rowMapper() {
            return (rs, rowNum) -> new MetricPoint(1L, Map.of());
        }
    }

    private static final class CronMetricSource implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "cron-metric";
        }

        @Override
        public MetricDefinitionDefaults defaults() {
            return new MetricDefinitionDefaults(
                    "cron.metric",
                    MetricKind.GAUGE,
                    "business",
                    "businessReplicaDataSource",
                    new MetricScheduleDefaults(
                            MetricScheduleDefaults.Mode.CRON,
                            null,
                            "0 0 * * *",
                            Duration.ofSeconds(10)
                    ),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(10),
                    Duration.ZERO,
                    500,
                    SeriesOverflowPolicy.AGGREGATE_TO_OTHER
            );
        }

        @Override
        public QueryDefinition queryDefinition() {
            return new QueryDefinition("select 1");
        }

        @Override
        public RowMapper<MetricPoint> rowMapper() {
            return (rs, rowNum) -> new MetricPoint(1L, Map.of());
        }
    }
}
