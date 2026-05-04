package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import java.util.Objects;

public class ManualMetricConfigResolver {

    private final ReflexOtelMetricsProperties properties;

    public ManualMetricConfigResolver(ReflexOtelMetricsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public ResolvedManualMetricConfig resolve(String metricId, MetricKind kind, MetricDefinition definition) {
        if (metricId == null || metricId.isBlank()) {
            throw new IllegalArgumentException("metricId must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        ManualMetricRuntimeProperties runtime = properties.getManual()
                .getOrDefault(metricId, new ManualMetricRuntimeProperties());

        String suffix = runtime.getSuffix() != null ? runtime.getSuffix() : definition.metricSuffix();
        String scope = runtime.getScope() != null ? runtime.getScope() : definition.scope();
        int maxSeries = runtime.getMaxSeries() != null ? runtime.getMaxSeries() : definition.maxSeries();
        validateResolvedConfig(suffix, scope, maxSeries);
        var overflowPolicy = runtime.getOverflowPolicy() != null
                ? runtime.getOverflowPolicy()
                : definition.overflowPolicy();
        boolean enabled = properties.isEnabled()
                && resolveScopeEnabled(scope)
                && !Boolean.FALSE.equals(runtime.getEnabled());

        return new ResolvedManualMetricConfig(
                metricId,
                enabled,
                properties.getMetricPrefix() + "." + suffix,
                suffix,
                scope,
                kind,
                definition.description(),
                definition.unit(),
                definition.attributes(),
                maxSeries,
                overflowPolicy
        );
    }

    private boolean resolveScopeEnabled(String scope) {
        ReflexOtelMetricsProperties.ScopeProperties scopeProperties = properties.getScopes().get(scope);
        return scopeProperties == null || scopeProperties.isEnabled();
    }

    private void validateResolvedConfig(String suffix, String scope, int maxSeries) {
        if (suffix == null || suffix.isBlank()) {
            throw new IllegalArgumentException("suffix must not be blank");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        if (maxSeries < 1) {
            throw new IllegalArgumentException("maxSeries must be greater than zero");
        }
    }
}
