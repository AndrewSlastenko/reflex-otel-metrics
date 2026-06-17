package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricConfigResolverTest {

    @Test
    void resolvesJdbcMetricFromUnifiedDefinition() {
        ReflexTelemetryProperties properties = baseProperties();
        ReflexTelemetryProperties.MetricDefinitionProperties definition = jdbcDefinition();
        definition.getSchedule().setFixedDelay(Duration.ofMinutes(5));
        definition.getSchedule().setInitialDelay(Duration.ofSeconds(10));
        properties.getMetrics().getDefinitions().put("documents-by-status", definition);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.metricId()).isEqualTo("documents-by-status");
        assertThat(resolved.source()).isEqualTo(ReflexTelemetryProperties.MetricSourceType.JDBC);
        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.exportedMetricName()).isEqualTo("ci05414726.documents.by-status");
        assertThat(resolved.scope()).isEqualTo("business");
        assertThat(resolved.dataSourceRef()).isEqualTo("businessReplicaDataSource");
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.GAUGE);
        assertThat(resolved.schedule().fixedDelay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(resolved.schedule().initialDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(resolved.querySchema()).isNull();
    }

    @Test
    void resolvesJdbcQuerySchemaFromDefinition() {
        ReflexTelemetryProperties properties = baseProperties();
        ReflexTelemetryProperties.MetricDefinitionProperties definition = jdbcDefinition();
        definition.getQuery().setSchema("documents");
        properties.getMetrics().getDefinitions().put("documents-by-status", definition);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.querySchema()).isEqualTo("documents");
    }

    @Test
    void blankJdbcQuerySchemaIsTreatedAsAbsent() {
        ReflexTelemetryProperties properties = baseProperties();
        ReflexTelemetryProperties.MetricDefinitionProperties definition = jdbcDefinition();
        definition.getQuery().setSchema("   ");
        properties.getMetrics().getDefinitions().put("documents-by-status", definition);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.querySchema()).isNull();
    }

    @Test
    void resolvesManualHistogramWithAttributesAndBuckets() {
        ReflexTelemetryProperties properties = baseProperties();
        ReflexTelemetryProperties.MetricDefinitionProperties definition = manualHistogramDefinition();
        properties.getMetrics().getDefinitions().put("transaction-send-duration", definition);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties)
                .resolveManual("transaction-send-duration", MetricKind.HISTOGRAM);

        assertThat(resolved.source()).isEqualTo(ReflexTelemetryProperties.MetricSourceType.MANUAL);
        assertThat(resolved.exportedMetricName()).isEqualTo("ci05414726.transaction.send.duration");
        assertThat(resolved.unit()).isEqualTo("s");
        assertThat(resolved.attributes().required()).containsExactly("target_system", "result");
        assertThat(resolved.attributes().optional()).containsExactly("http_status");
        assertThat(resolved.histogramBuckets()).containsExactly(1d, 2d, 5d, 10d, 30d, 60d);
    }

    @Test
    void metricsDisabledDisablesResolvedMetric() {
        ReflexTelemetryProperties properties = baseProperties();
        properties.getMetrics().setEnabled(false);
        properties.getMetrics().getDefinitions().put("documents-by-status", jdbcDefinition());

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void metricsDisabledAllowsManualNoopWithoutDefinition() {
        ReflexTelemetryProperties properties = baseProperties();
        properties.getMetrics().setEnabled(false);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties)
                .resolveManual("orders-created", MetricKind.COUNTER);

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.source()).isEqualTo(ReflexTelemetryProperties.MetricSourceType.MANUAL);
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.COUNTER);
    }

    @Test
    void disabledScopeDisablesResolvedMetric() {
        ReflexTelemetryProperties properties = baseProperties();
        properties.getMetrics().getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(false));
        properties.getMetrics().getDefinitions().put("documents-by-status", jdbcDefinition());

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void usesDefaultScopeFromSource() {
        ReflexTelemetryProperties properties = baseProperties();
        ReflexTelemetryProperties.MetricDefinitionProperties jdbc = jdbcDefinition();
        jdbc.setScope(null);
        properties.getMetrics().getDefinitions().put("documents-by-status", jdbc);

        ResolvedMetricConfig resolved = new MetricConfigResolver(properties).resolve(new TestJdbcMetricSource());

        assertThat(resolved.scope()).isEqualTo(ReflexMetricScopes.JDBC);
    }

    @Test
    void rejectsDuplicateExportedMetricNameAcrossJdbcAndManualDefinitions() {
        ReflexTelemetryProperties properties = baseProperties();
        properties.getMetrics().getDefinitions().put("documents-by-status", jdbcDefinition());
        ReflexTelemetryProperties.MetricDefinitionProperties manual = manualHistogramDefinition();
        manual.setKind(MetricKind.COUNTER);
        manual.setName("documents.by-status");
        properties.getMetrics().getDefinitions().put("documents-by-status-manual", manual);

        MetricConfigResolver resolver = new MetricConfigResolver(properties);

        assertThatThrownBy(() -> resolver.resolve(new TestJdbcMetricSource()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exported metric name 'ci05414726.documents.by-status' is used by multiple definitions:")
                .hasMessageContaining("documents-by-status")
                .hasMessageContaining("documents-by-status-manual");
    }

    @Test
    void rejectsMissingDefinition() {
        MetricConfigResolver resolver = new MetricConfigResolver(baseProperties());

        assertThatThrownBy(() -> resolver.resolve("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric 'missing' is not configured");
    }

    @Test
    void rejectsManualFactoryRequestWhenKindDiffers() {
        ReflexTelemetryProperties properties = baseProperties();
        ReflexTelemetryProperties.MetricDefinitionProperties definition = manualHistogramDefinition();
        properties.getMetrics().getDefinitions().put("transaction-send-duration", definition);

        MetricConfigResolver resolver = new MetricConfigResolver(properties);

        assertThatThrownBy(() -> resolver.resolveManual("transaction-send-duration", MetricKind.COUNTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured as HISTOGRAM but requested as COUNTER");
    }

    @Test
    void springBootBindingBindsUnifiedTelemetryProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("reflex.telemetry.enabled", "false")
                .withProperty("reflex.telemetry.service.system-code", "ci05414726")
                .withProperty("reflex.telemetry.service.name", "contracts-api")
                .withProperty("reflex.telemetry.service.instrumentation-scope-name", "custom.scope")
                .withProperty("reflex.telemetry.otlp.endpoint", "http://collector:4317")
                .withProperty("reflex.telemetry.metrics.temporality-preference", "CUMULATIVE")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.source", "MANUAL")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.kind", "HISTOGRAM")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.name", "transaction.send.duration")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.unit", "s")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.attributes.required[0]", "target_system")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.histogram.buckets[0]", "1")
                .withProperty("reflex.telemetry.metrics.definitions.transaction-send-duration.histogram.buckets[1]", "2")
                .withProperty("reflex.telemetry.metrics.definitions.documents-by-status.source", "JDBC")
                .withProperty("reflex.telemetry.metrics.definitions.documents-by-status.kind", "GAUGE")
                .withProperty("reflex.telemetry.metrics.definitions.documents-by-status.name", "documents.by-status")
                .withProperty("reflex.telemetry.metrics.definitions.documents-by-status.query.schema", "documents")
                .withProperty("reflex.telemetry.traces.enabled", "false");

        ReflexTelemetryProperties properties = Binder.get(environment)
                .bind("reflex.telemetry", Bindable.of(ReflexTelemetryProperties.class))
                .orElseThrow(() -> new AssertionError("Expected reflex.telemetry properties to bind"));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getService().getSystemCode()).isEqualTo("ci05414726");
        assertThat(properties.getService().getName()).isEqualTo("contracts-api");
        assertThat(properties.getService().getInstrumentationScopeName()).isEqualTo("custom.scope");
        assertThat(properties.getOtlp().getEndpoint()).isEqualTo("http://collector:4317");
        assertThat(properties.getMetrics().getTemporalityPreference())
                .isEqualTo(ReflexTelemetryProperties.MetricsTemporalityPreference.CUMULATIVE);
        ReflexTelemetryProperties.MetricDefinitionProperties definition =
                properties.getMetrics().getDefinitions().get("transaction-send-duration");
        assertThat(definition.getSource()).isEqualTo(ReflexTelemetryProperties.MetricSourceType.MANUAL);
        assertThat(definition.getKind()).isEqualTo(MetricKind.HISTOGRAM);
        assertThat(definition.getHistogram().getBuckets()).containsExactly(1d, 2d);
        ReflexTelemetryProperties.MetricDefinitionProperties jdbcDefinition =
                properties.getMetrics().getDefinitions().get("documents-by-status");
        assertThat(jdbcDefinition.getQuery().getSchema()).isEqualTo("documents");
        assertThat(properties.getTraces().isEnabled()).isFalse();
    }

    private static ReflexTelemetryProperties baseProperties() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getService().setSystemCode("ci05414726");
        properties.getMetrics().getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(true));
        return properties;
    }

    static ReflexTelemetryProperties.MetricDefinitionProperties jdbcDefinition() {
        ReflexTelemetryProperties.MetricDefinitionProperties definition =
                new ReflexTelemetryProperties.MetricDefinitionProperties();
        definition.setSource(ReflexTelemetryProperties.MetricSourceType.JDBC);
        definition.setKind(MetricKind.GAUGE);
        definition.setName("documents.by-status");
        definition.setScope("business");
        definition.setDataSourceRef("businessReplicaDataSource");
        definition.setOverflowPolicy(SeriesOverflowPolicy.FAIL);
        return definition;
    }

    static ReflexTelemetryProperties.MetricDefinitionProperties manualHistogramDefinition() {
        ReflexTelemetryProperties.MetricDefinitionProperties definition =
                new ReflexTelemetryProperties.MetricDefinitionProperties();
        definition.setSource(ReflexTelemetryProperties.MetricSourceType.MANUAL);
        definition.setKind(MetricKind.HISTOGRAM);
        definition.setName("transaction.send.duration");
        definition.setScope("business");
        definition.setDescription("Transaction send duration");
        definition.setUnit("s");
        definition.getAttributes().setRequired(java.util.List.of("target_system", "result"));
        definition.getAttributes().setOptional(java.util.List.of("http_status"));
        definition.getHistogram().setBuckets(java.util.List.of(1d, 2d, 5d, 10d, 30d, 60d));
        return definition;
    }

    private static final class TestJdbcMetricSource implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "documents-by-status";
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
