package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualMetricConfigResolverTest {

    @Test
    void defaultResolutionUsesJavaDefinition() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
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
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setManual(null);

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build());

        assertThat(resolved.suffix()).isEqualTo("documents.by.status");
    }

    @Test
    void yamlRuntimeOverridesAllowedOperationalFieldsOnly() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setSuffix("documents.current");
        runtime.setScope("reporting");
        runtime.setMaxSeries(10);
        runtime.setOverflowPolicy(SeriesOverflowPolicy.TRUNCATE);
        properties.getManual().put("documents-by-status", runtime);
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
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.getScopes().put("business", new ReflexOtelMetricsProperties.ScopeProperties(false));
        MetricDefinition definition = MetricDefinition.of("documents.by.status")
                .scope("business")
                .build();

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, definition);

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void runtimeDisabledDisablesManualMetric() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setEnabled(Boolean.FALSE);
        properties.getManual().put("documents-by-status", runtime);

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build());

        assertThat(resolved.enabled()).isFalse();
    }

    @Test
    void blankMetricIdIsRejected() {
        ManualMetricConfigResolver resolver = new ManualMetricConfigResolver(new ReflexOtelMetricsProperties());

        assertThatThrownBy(() -> resolver.resolve(" ", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricId must not be blank");
    }

    @Test
    void blankRuntimeSuffixIsRejected() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setSuffix(" ");
        properties.getManual().put("documents-by-status", runtime);

        assertThatThrownBy(() -> new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suffix must not be blank");
    }

    @Test
    void blankRuntimeScopeIsRejected() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setScope(" ");
        properties.getManual().put("documents-by-status", runtime);

        assertThatThrownBy(() -> new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope must not be blank");
    }

    @Test
    void zeroRuntimeMaxSeriesIsRejected() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setMaxSeries(0);
        properties.getManual().put("documents-by-status", runtime);

        assertThatThrownBy(() -> new ManualMetricConfigResolver(properties)
                .resolve("documents-by-status", MetricKind.COUNTER, MetricDefinition.of("documents.by.status").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSeries must be greater than zero");
    }

    @Test
    void springBootBindingBindsManualMetricRuntimeProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("reflex.otel.metrics.manual.orders-created.suffix", "orders.created")
                .withProperty("reflex.otel.metrics.manual.orders-created.scope", "orders")
                .withProperty("reflex.otel.metrics.manual.orders-created.max-series", "12")
                .withProperty("reflex.otel.metrics.manual.orders-created.overflow-policy", "TRUNCATE")
                .withProperty("reflex.otel.metrics.manual.orders-created.enabled", "false");

        ReflexOtelMetricsProperties properties = Binder.get(environment)
                .bind("reflex.otel.metrics", Bindable.of(ReflexOtelMetricsProperties.class))
                .orElseThrow(() -> new AssertionError("Expected reflex.otel.metrics properties to bind"));

        ManualMetricRuntimeProperties runtime = properties.getManual().get("orders-created");
        assertThat(runtime).isNotNull();
        assertThat(runtime.getSuffix()).isEqualTo("orders.created");
        assertThat(runtime.getScope()).isEqualTo("orders");
        assertThat(runtime.getMaxSeries()).isEqualTo(12);
        assertThat(runtime.getOverflowPolicy()).isEqualTo(SeriesOverflowPolicy.TRUNCATE);
        assertThat(runtime.getEnabled()).isFalse();
    }
}
