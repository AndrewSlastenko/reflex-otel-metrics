package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMetricQuerySettingsTest {

    @Test
    void returnsSchemaFromResolvedDefinition() {
        JdbcMetricQuerySettings settings = settingsWithSchema("documents-by-status", "documents");

        assertThat(settings.schema("documents-by-status")).isEqualTo("documents");
    }

    @Test
    void requireSchemaFailsWhenMissing() {
        JdbcMetricQuerySettings settings = settingsWithSchema("documents-by-status", null);

        assertThatThrownBy(() -> settings.requireSchema("documents-by-status"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("query.schema");
    }

    private static JdbcMetricQuerySettings settingsWithSchema(String metricId, String schema) {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getService().setSystemCode("ci05414726");
        ReflexTelemetryProperties.MetricDefinitionProperties definition = new ReflexTelemetryProperties.MetricDefinitionProperties();
        definition.setSource(ReflexTelemetryProperties.MetricSourceType.JDBC);
        definition.setKind(MetricKind.GAUGE);
        definition.setName("documents.by-status");
        definition.setDataSourceRef("businessReplicaDataSource");
        definition.setOverflowPolicy(SeriesOverflowPolicy.FAIL);
        if (schema != null) {
            definition.getQuery().setSchema(schema);
        }
        properties.getMetrics().getDefinitions().put(metricId, definition);
        return new JdbcMetricQuerySettings(new MetricConfigResolver(properties));
    }
}
