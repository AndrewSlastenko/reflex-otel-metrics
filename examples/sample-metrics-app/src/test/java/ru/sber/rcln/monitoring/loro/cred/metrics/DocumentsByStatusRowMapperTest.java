package ru.sber.rcln.monitoring.loro.cred.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;

class DocumentsByStatusRowMapperTest {

    private final DocumentsByStatusMetricSource source =
            new DocumentsByStatusMetricSource(querySettings());

    @Test
    void queryDefinition_usesConfiguredSchema() {
        assertThat(source.queryDefinition().sql()).contains("business.transaction_view");
    }

    @Test
    void rowMapper_mapsResultSetToMetricPoint() throws Exception {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getLong("cnt")).thenReturn(3L);
        Mockito.when(resultSet.getString("client_code")).thenReturn("A");
        Mockito.when(resultSet.getString("document_status")).thenReturn("CREATED");

        MetricPoint point = source.rowMapper().mapRow(resultSet, 0);

        assertThat(point.value()).isEqualTo(3L);
        assertThat(point.attributes()).isEqualTo(Map.of(
                "client", "A",
                "status", "CREATED"));
    }

    private static JdbcMetricQuerySettings querySettings() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getService().setSystemCode("test");
        ReflexTelemetryProperties.MetricDefinitionProperties definition =
                new ReflexTelemetryProperties.MetricDefinitionProperties();
        definition.setSource(ReflexTelemetryProperties.MetricSourceType.JDBC);
        definition.setKind(MetricKind.GAUGE);
        definition.setName("test.metric");
        definition.setDataSourceRef("testDataSource");
        definition.setOverflowPolicy(SeriesOverflowPolicy.FAIL);
        definition.getQuery().setSchema("business");
        properties.getMetrics().getDefinitions().put("documents-by-status", definition);
        return new JdbcMetricQuerySettings(new MetricConfigResolver(properties));
    }
}
