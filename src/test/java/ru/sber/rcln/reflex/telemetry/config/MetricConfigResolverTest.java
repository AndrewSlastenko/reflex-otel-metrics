package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                        Duration.ofSeconds(10)
                ),
                Duration.ofSeconds(30),
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
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.setSystemCode("ci05414726");
        properties.getMetrics().getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(true));

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setEnabled(Boolean.FALSE);
        runtimeProperties.setSuffix("documents.current");
        runtimeProperties.setDataSourceRef("overrideDataSource");
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        MetricConfigResolver resolver = new MetricConfigResolver(properties);
        ResolvedMetricConfig resolved = resolver.resolve(new TestJdbcMetricSource());

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.fullMetricName()).isEqualTo("ci05414726.documents.current");
        assertThat(resolved.dataSourceRef()).isEqualTo("overrideDataSource");
    }

    @Test
    void partialFixedDelayOverrideShouldBeHonoredWithoutRuntimeScheduleMode() {
        ReflexTelemetryProperties properties = baseProperties();

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setFixedDelay(Duration.ofMinutes(2));
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.FIXED_DELAY);
        assertThat(resolved.schedule().fixedDelay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(resolved.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void metricsDisabledShouldDisableJdbcMetric() {
        ReflexTelemetryProperties properties = baseProperties();
        properties.getMetrics().setEnabled(false);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void partialInitialDelayOverrideShouldBeHonoredWithoutRuntimeScheduleMode() {
        ReflexTelemetryProperties properties = baseProperties();

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setInitialDelay(Duration.ofSeconds(45));
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.FIXED_DELAY);
        assertThat(resolved.schedule().fixedDelay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(resolved.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void runtimeCronShouldNotInheritFixedDelayWhenDefaultIsFixedDelay() {
        ReflexTelemetryProperties properties = baseProperties();

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.CRON);
        runtimeProperties.setCron("0 * * * *");
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.CRON);
        assertThat(resolved.schedule().cron()).isEqualTo("0 * * * *");
        assertThat(resolved.schedule().fixedDelay()).isNull();
    }

    @Test
    void runtimeFixedDelayShouldNotInheritCronWhenDefaultIsCron() {
        ReflexTelemetryProperties properties = baseProperties();

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.FIXED_DELAY);
        runtimeProperties.setFixedDelay(Duration.ofMinutes(2));
        properties.getMetrics().getSources().put("cron-metric", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new CronMetricSource());

        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.FIXED_DELAY);
        assertThat(resolved.schedule().fixedDelay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(resolved.schedule().cron()).isNull();
    }

    @Test
    void shouldUseJdbcScopeWhenJdbcSourceDoesNotSpecifyScope() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties, new ReflexTelemetryNamingPolicy(null))
                .resolve(new JdbcMetricSourceWithoutScope());

        assertThat(resolved.scope()).isEqualTo(ReflexMetricScopes.JDBC);
    }

    @Test
    void shouldResolveHistogramKindAndExplicitOverflowOverride() {
        ReflexTelemetryProperties properties = baseProperties();
        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setKind(MetricKind.HISTOGRAM);
        runtimeProperties.setOverflowPolicy(SeriesOverflowPolicy.TRUNCATE);
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.metricKind()).isEqualTo(MetricKind.HISTOGRAM);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.TRUNCATE);
    }

    @Test
    void shouldFailFastForHistogramWithAggregateToOtherOverflowPolicy() {
        ReflexTelemetryProperties properties = baseProperties();
        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setKind(MetricKind.HISTOGRAM);
        runtimeProperties.setOverflowPolicy(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
        properties.getMetrics().getSources().put("documents-by-status", runtimeProperties);

        MetricConfigResolver resolver = new MetricConfigResolver(properties);

        assertThatThrownBy(() -> resolver.resolve(new TestJdbcMetricSource()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric 'documents-by-status' does not support AGGREGATE_TO_OTHER overflow policy for HISTOGRAM kind; use FAIL or TRUNCATE");
    }

    private static ReflexTelemetryProperties baseProperties() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.setSystemCode("ci05414726");
        properties.getMetrics().getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(true));
        return properties;
    }

    private static final class TestJdbcMetricSource implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "documents-by-status";
        }

        @Override
        public MetricDefinitionDefaults defaults() {
            return new MetricDefinitionDefaults(
                    "documents.by.status",
                    MetricKind.UP_DOWN_COUNTER,
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

    private static final class JdbcMetricSourceWithoutScope implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "documents-by-status";
        }

        @Override
        public MetricDefinitionDefaults defaults() {
            return new MetricDefinitionDefaults(
                    "documents.current",
                    MetricKind.GAUGE,
                    null,
                    "dataSource",
                    new MetricScheduleDefaults(
                            MetricScheduleDefaults.Mode.FIXED_DELAY,
                            Duration.ofMinutes(1),
                            null,
                            Duration.ZERO),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(2),
                    Duration.ZERO,
                    500,
                    SeriesOverflowPolicy.FAIL);
        }

        @Override
        public QueryDefinition queryDefinition() {
            return new QueryDefinition("select 1");
        }

        @Override
        public RowMapper<MetricPoint> rowMapper() {
            return (rs, rowNum) -> new MetricPoint(1, Map.of());
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
