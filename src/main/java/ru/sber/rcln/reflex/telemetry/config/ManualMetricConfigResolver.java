package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ManualMetricConfigResolver {

    private final @NonNull ReflexTelemetryProperties properties;

    public ResolvedManualMetricConfig resolve(
            String metricId,
            @NonNull MetricKind kind,
            @NonNull MetricDefinition definition) {
        if (metricId == null || metricId.isBlank()) {
            throw new IllegalArgumentException("metricId must not be blank");
        }

        ManualMetricRuntimeProperties runtime = properties.getMetrics()
                .getManual()
                .getOrDefault(metricId, new ManualMetricRuntimeProperties());

        String suffix = runtime.getSuffix() != null ? runtime.getSuffix() : definition.metricSuffix();
        String scope = runtime.getScope() != null ? runtime.getScope() : definition.scope();
        int maxSeries = runtime.getMaxSeries() != null ? runtime.getMaxSeries() : definition.maxSeries();
        validateResolvedConfig(suffix, scope, maxSeries);
        var overflowPolicy = runtime.getOverflowPolicy() != null
                ? runtime.getOverflowPolicy()
                : definition.overflowPolicy();
        boolean enabled = properties.isEnabled()
                && properties.getMetrics().isEnabled()
                && resolveScopeEnabled(scope)
                && !Boolean.FALSE.equals(runtime.getEnabled());

        return new ResolvedManualMetricConfig(
                metricId,
                enabled,
                properties.getMetrics().getMetricPrefix() + "." + suffix,
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
        ReflexTelemetryProperties.ScopeProperties scopeProperties = properties.getMetrics().getScopes().get(scope);
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
