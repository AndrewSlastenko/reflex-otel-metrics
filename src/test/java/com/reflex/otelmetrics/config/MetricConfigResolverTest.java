package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

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
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
        properties.getScopes().put("business", new ReflexOtelMetricsProperties.ScopeProperties(true));

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setEnabled(Boolean.FALSE);
        runtimeProperties.setSuffix("documents.current");
        runtimeProperties.setDataSourceRef("overrideDataSource");
        properties.getSources().put("documents-by-status", runtimeProperties);

        MetricConfigResolver resolver = new MetricConfigResolver(properties);
        ResolvedMetricConfig resolved = resolver.resolve(new TestJdbcMetricSource());

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.fullMetricName()).isEqualTo("ci054147.documents.current");
        assertThat(resolved.dataSourceRef()).isEqualTo("overrideDataSource");
    }

    @Test
    void partialFixedDelayOverrideShouldBeHonoredWithoutRuntimeScheduleMode() {
        ReflexOtelMetricsProperties properties = baseProperties();

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setFixedDelay(Duration.ofMinutes(2));
        properties.getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.FIXED_DELAY);
        assertThat(resolved.schedule().fixedDelay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(resolved.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void partialInitialDelayOverrideShouldBeHonoredWithoutRuntimeScheduleMode() {
        ReflexOtelMetricsProperties properties = baseProperties();

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setInitialDelay(Duration.ofSeconds(45));
        properties.getSources().put("documents-by-status", runtimeProperties);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.FIXED_DELAY);
        assertThat(resolved.schedule().fixedDelay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(resolved.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(45));
    }

    private static ReflexOtelMetricsProperties baseProperties() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
        properties.getScopes().put("business", new ReflexOtelMetricsProperties.ScopeProperties(true));
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
}
