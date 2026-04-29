package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigResolverTest {

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

        MetricDefinitionDefaults defaults = new MetricDefinitionDefaults(
                "documents-by-status",
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
        TestJdbcMetricSource source = new TestJdbcMetricSource(
                "documents-by-status",
                defaults,
                new QueryDefinition("select 1"),
                (rs, rowNum) -> new MetricPoint(0L, java.util.Map.of())
        );

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(source);

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.fullMetricName()).isEqualTo("ci054147.documents.current");
        assertThat(resolved.dataSourceRef()).isEqualTo("overrideDataSource");
    }

    private static final class TestJdbcMetricSource implements JdbcMetricSource {

        private final String metricId;
        private final MetricDefinitionDefaults defaults;
        private final QueryDefinition queryDefinition;
        private final RowMapper<MetricPoint> rowMapper;

        private TestJdbcMetricSource(
                String metricId,
                MetricDefinitionDefaults defaults,
                QueryDefinition queryDefinition,
                RowMapper<MetricPoint> rowMapper
        ) {
            this.metricId = metricId;
            this.defaults = defaults;
            this.queryDefinition = queryDefinition;
            this.rowMapper = rowMapper;
        }

        @Override
        public String metricId() {
            return metricId;
        }

        @Override
        public MetricDefinitionDefaults defaults() {
            return defaults;
        }

        @Override
        public QueryDefinition queryDefinition() {
            return queryDefinition;
        }

        @Override
        public RowMapper<MetricPoint> rowMapper() {
            return rowMapper;
        }
    }
}
