package com.reflex.otelmetrics.config;

import java.time.Duration;
import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
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
    void runtimeCronSwitchShouldValidateAgainstFixedDelayDefault() {
        ReflexOtelMetricsProperties properties = baseProperties();
        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.CRON);
        runtimeProperties.setCron("0 * * * *");
        properties.getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new FixedDelayMetricSource());

        assertThat(new MetricConfigValidator().validate(resolved)).isEmpty();
    }

    @Test
    void runtimeFixedDelaySwitchShouldValidateAgainstCronDefault() {
        ReflexOtelMetricsProperties properties = baseProperties();
        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.FIXED_DELAY);
        runtimeProperties.setFixedDelay(Duration.ofMinutes(2));
        properties.getSources().put("cron-metric", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new CronMetricSource());

        assertThat(new MetricConfigValidator().validate(resolved)).isEmpty();
    }

    private static ResolvedMetricConfig baseConfig(Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci054147.documents.current",
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

    private static ReflexOtelMetricsProperties baseProperties() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
        properties.getScopes().put("business", new ReflexOtelMetricsProperties.ScopeProperties(true));
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
