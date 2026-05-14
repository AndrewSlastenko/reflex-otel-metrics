package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualMetricConfigResolverTest {

    @Test
    void defaultResolutionUsesJavaDefinition() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getMetrics().setMetricPrefix("ci054147");
        AttributesSchema attributes = AttributesSchema.builder().required("status").optional("region").build();
        MetricDefinition definition = MetricDefinition.of("documents.by.status")
                .scope("business")
                .description("Documents by status")
                .unit("1")
                .attributes(attributes)
                .maxSeries(250)
                .overflowPolicy(SeriesOverflowPolicy.AGGREGATE_TO_OTHER)
                .build();

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, definition);

        assertThat(resolved.metricId()).isEqualTo("documents-by-status");
        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.fullMetricName()).isEqualTo("ci054147.documents.by.status");
        assertThat(resolved.suffix()).isEqualTo("documents.by.status");
        assertThat(resolved.scope()).isEqualTo("business");
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.COUNTER);
        assertThat(resolved.description()).isEqualTo("Documents by status");
        assertThat(resolved.unit()).isEqualTo("1");
        assertThat(resolved.attributes()).isSameAs(attributes);
        assertThat(resolved.maxSeries()).isEqualTo(250);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
    }

    @Test
    void nullManualMapStillResolvesFromJavaDefinition() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getMetrics().setManual(null);

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build());

        assertThat(resolved.suffix()).isEqualTo("documents.by.status");
    }

    @Test
    void yamlRuntimeOverridesAllowedOperationalFieldsOnly() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getMetrics().setMetricPrefix("ci054147");
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setSuffix("documents.current");
        runtime.setScope("reporting");
        runtime.setMaxSeries(10);
        runtime.setOverflowPolicy(SeriesOverflowPolicy.TRUNCATE);
        properties.getMetrics().getManual().put("documents-by-status", runtime);
        MetricDefinition definition = MetricDefinition.of("documents.by.status")
                .scope("business")
                .description("Documents by status")
                .unit("1")
                .maxSeries(250)
                .overflowPolicy(SeriesOverflowPolicy.AGGREGATE_TO_OTHER)
                .build();

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.GAUGE, definition);

        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.fullMetricName()).isEqualTo("ci054147.documents.current");
        assertThat(resolved.suffix()).isEqualTo("documents.current");
        assertThat(resolved.scope()).isEqualTo("reporting");
        assertThat(resolved.maxSeries()).isEqualTo(10);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.TRUNCATE);
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.GAUGE);
        assertThat(resolved.description()).isEqualTo("Documents by status");
        assertThat(resolved.unit()).isEqualTo("1");
        assertThat(resolved.attributes()).isEqualTo(AttributesSchema.empty());
    }

    @Test
    void scopeDisabledDisablesManualMetric() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getMetrics().getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(false));
        MetricDefinition definition = MetricDefinition.of("documents.by.status")
                .scope("business")
                .build();

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, definition);

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void runtimeDisabledDisablesManualMetric() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setEnabled(Boolean.FALSE);
        properties.getMetrics().getManual().put("documents-by-status", runtime);

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build());

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void metricsDisabledDisablesManualMetric() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        properties.getMetrics().setEnabled(false);

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build());

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void blankMetricIdIsRejected() {
        ManualMetricConfigResolver resolver = new ManualMetricConfigResolver(new ReflexTelemetryProperties());

        assertThatThrownBy(() -> resolver.resolve(" ", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricId must not be blank");
    }

    @Test
    void blankRuntimeSuffixIsRejected() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setSuffix(" ");
        properties.getMetrics().getManual().put("documents-by-status", runtime);

        assertThatThrownBy(() -> new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suffix must not be blank");
    }

    @Test
    void blankRuntimeScopeIsRejected() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setScope(" ");
        properties.getMetrics().getManual().put("documents-by-status", runtime);

        assertThatThrownBy(() -> new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope must not be blank");
    }

    @Test
    void zeroRuntimeMaxSeriesIsRejected() {
        ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setMaxSeries(0);
        properties.getMetrics().getManual().put("documents-by-status", runtime);

        assertThatThrownBy(() -> new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSeries must be greater than zero");
    }

    @Test
    void springBootBindingBindsManualMetricRuntimeProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("reflex.telemetry.metrics.manual.orders-created.suffix", "orders.created")
                .withProperty("reflex.telemetry.metrics.manual.orders-created.scope", "orders")
                .withProperty("reflex.telemetry.metrics.manual.orders-created.max-series", "12")
                .withProperty("reflex.telemetry.metrics.manual.orders-created.overflow-policy", "TRUNCATE")
                .withProperty("reflex.telemetry.metrics.manual.orders-created.enabled", "false");

        ReflexTelemetryProperties properties = Binder.get(environment)
                .bind("reflex.telemetry", Bindable.of(ReflexTelemetryProperties.class))
                .orElseThrow(() -> new AssertionError("Expected reflex.telemetry properties to bind"));

        ManualMetricRuntimeProperties runtime = properties.getMetrics().getManual().get("orders-created");
        assertThat(runtime).isNotNull();
        assertThat(runtime.getSuffix()).isEqualTo("orders.created");
        assertThat(runtime.getScope()).isEqualTo("orders");
        assertThat(runtime.getMaxSeries()).isEqualTo(12);
        assertThat(runtime.getOverflowPolicy()).isEqualTo(SeriesOverflowPolicy.TRUNCATE);
        assertThat(runtime.getEnabled()).isFalse();
    }

    @Test
    void springBootBindingBindsRootMetricsAndTraceBranches() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("reflex.telemetry.enabled", "false")
                .withProperty("reflex.telemetry.instrumentation-scope-name", "custom.scope")
                .withProperty("reflex.telemetry.otlp.traces-endpoint", "http://collector:4317")
                .withProperty("reflex.telemetry.metrics.enabled", "false")
                .withProperty("reflex.telemetry.metrics.metric-prefix", "ci054147")
                .withProperty("reflex.telemetry.metrics.manual.orders-created.suffix", "orders.created")
                .withProperty("reflex.telemetry.traces.enabled", "false");

        ReflexTelemetryProperties properties = Binder.get(environment)
                .bind("reflex.telemetry", Bindable.of(ReflexTelemetryProperties.class))
                .orElseThrow(() -> new AssertionError("Expected reflex.telemetry properties to bind"));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getInstrumentationScopeName()).isEqualTo("custom.scope");
        assertThat(properties.getOtlp().getTracesEndpoint()).isEqualTo("http://collector:4317");
        assertThat(properties.getMetrics().isEnabled()).isFalse();
        assertThat(properties.getMetrics().getMetricPrefix()).isEqualTo("ci054147");
        assertThat(properties.getMetrics().getManual().get("orders-created").getSuffix()).isEqualTo("orders.created");
        assertThat(properties.getTraces().isEnabled()).isFalse();
    }
}
