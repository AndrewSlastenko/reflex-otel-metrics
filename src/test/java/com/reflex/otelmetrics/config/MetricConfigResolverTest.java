package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.MetricSource;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigResolverTest {

    @Test
    void propertiesShouldOverrideBeanDefaults() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("reflex");

        ReflexOtelMetricsProperties.ScopeProperties scopeProperties = new ReflexOtelMetricsProperties.ScopeProperties();
        scopeProperties.setEnabled(Boolean.TRUE);
        properties.getScopes().put("business", scopeProperties);

        MetricRuntimeProperties runtimeProperties = new MetricRuntimeProperties();
        runtimeProperties.setEnabled(Boolean.FALSE);
        runtimeProperties.setSuffix("documents-by-status-override");
        runtimeProperties.setScope("business");
        runtimeProperties.setDataSourceRef("reportingDataSource");
        runtimeProperties.setKind(MetricKind.UP_DOWN_COUNTER);
        runtimeProperties.setScheduleMode(MetricScheduleSettings.Mode.CRON);
        runtimeProperties.setCron("0 */5 * * * *");
        runtimeProperties.setInitialDelay(Duration.ofSeconds(15));
        runtimeProperties.setTimeout(Duration.ofSeconds(20));
        runtimeProperties.setLockAtMostFor(Duration.ofMinutes(3));
        runtimeProperties.setLockAtLeastFor(Duration.ofSeconds(10));
        runtimeProperties.setMaxSeries(250);
        runtimeProperties.setOverflowPolicy(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
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
                SeriesOverflowPolicy.TRUNCATE
        );
        TestJdbcMetricSource source = new TestJdbcMetricSource(
                "documents-by-status",
                defaults,
                new QueryDefinition("select 1"),
                (rs, rowNum) -> new MetricPoint(0L, Map.of())
        );

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(source);

        assertThat(resolved.metricId()).isEqualTo("documents-by-status");
        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.fullMetricName()).isEqualTo("reflex.documents-by-status-override");
        assertThat(resolved.suffix()).isEqualTo("documents-by-status-override");
        assertThat(resolved.scope()).isEqualTo("business");
        assertThat(resolved.dataSourceRef()).isEqualTo("reportingDataSource");
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.UP_DOWN_COUNTER);
        assertThat(resolved.schedule().mode()).isEqualTo(MetricScheduleSettings.Mode.CRON);
        assertThat(resolved.schedule().cron()).isEqualTo("0 */5 * * * *");
        assertThat(resolved.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(15));
        assertThat(resolved.timeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(resolved.lockAtMostFor()).isEqualTo(Duration.ofMinutes(3));
        assertThat(resolved.lockAtLeastFor()).isEqualTo(Duration.ofSeconds(10));
        assertThat(resolved.maxSeries()).isEqualTo(250);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
    }

    @Test
    void nonJdbcSourcesDoNotResolveDataSourceRef() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
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
                SeriesOverflowPolicy.TRUNCATE
        );

        MetricSource source = new MetricSource() {
            @Override
            public String metricId() {
                return "documents-by-status";
            }

            @Override
            public MetricDefinitionDefaults defaults() {
                return defaults;
            }
        };

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(source);

        assertThat(resolved.dataSourceRef()).isNull();
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
