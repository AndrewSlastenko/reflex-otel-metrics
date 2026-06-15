package ru.sber.rcln.reflex.telemetry.sample.metrics;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;

final class MetricsJdbcQueryTestSupport {

    private MetricsJdbcQueryTestSupport() {
    }

    static JdbcMetricQuerySettings querySettings(String metricId, String schema) {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getService().setSystemCode("test");
        ReflexTelemetryProperties.MetricDefinitionProperties definition = jdbcDefinition();
        definition.getQuery().setSchema(schema);
        properties.getMetrics().getDefinitions().put(metricId, definition);
        return new JdbcMetricQuerySettings(new MetricConfigResolver(properties));
    }

    private static ReflexTelemetryProperties.MetricDefinitionProperties jdbcDefinition() {
        ReflexTelemetryProperties.MetricDefinitionProperties definition =
                new ReflexTelemetryProperties.MetricDefinitionProperties();
        definition.setSource(ReflexTelemetryProperties.MetricSourceType.JDBC);
        definition.setKind(MetricKind.GAUGE);
        definition.setName("test.metric");
        definition.setDataSourceRef("testDataSource");
        definition.setOverflowPolicy(SeriesOverflowPolicy.FAIL);
        return definition;
    }

    @TestConfiguration
    static class DocumentsQuerySettingsConfig {

        @Bean
        JdbcMetricQuerySettings jdbcMetricQuerySettings() {
            return querySettings("documents-by-status", "documents");
        }
    }

    @TestConfiguration
    static class PaymentsQuerySettingsConfig {

        @Bean
        JdbcMetricQuerySettings jdbcMetricQuerySettings() {
            return querySettings("payments-by-state", "payments");
        }
    }
}
