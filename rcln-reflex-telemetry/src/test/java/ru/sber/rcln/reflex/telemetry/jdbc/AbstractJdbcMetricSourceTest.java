package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractJdbcMetricSourceTest {

    @Test
    void buildsQueryFromYamlSchema() {
        TestMetricSource source = new TestMetricSource(
                "documents-by-status",
                querySettings("documents-by-status", "documents"));

        assertThat(source.metricId()).isEqualTo("documents-by-status");
        assertThat(source.queryDefinition().sql()).isEqualTo("select * from documents.transaction_view");
    }

    private static JdbcMetricQuerySettings querySettings(String metricId, String schema) {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getService().setSystemCode("ci05414726");
        ReflexTelemetryProperties.MetricDefinitionProperties definition = new ReflexTelemetryProperties.MetricDefinitionProperties();
        definition.setSource(ReflexTelemetryProperties.MetricSourceType.JDBC);
        definition.setKind(MetricKind.GAUGE);
        definition.setName("documents.by-status");
        definition.setDataSourceRef("businessReplicaDataSource");
        definition.setOverflowPolicy(SeriesOverflowPolicy.FAIL);
        definition.getQuery().setSchema(schema);
        properties.getMetrics().getDefinitions().put(metricId, definition);
        return new JdbcMetricQuerySettings(new MetricConfigResolver(properties));
    }

    private static final class TestMetricSource extends AbstractJdbcMetricSource {

        TestMetricSource(String metricId, JdbcMetricQuerySettings querySettings) {
            super(metricId, querySettings);
        }

        @Override
        protected QueryDefinition buildQuery(String schema) {
            return new QueryDefinition("select * from " + schema + ".transaction_view");
        }

        @Override
        public RowMapper<MetricPoint> rowMapper() {
            return (rs, rowNum) -> new MetricPoint(1L, Map.of());
        }
    }
}
