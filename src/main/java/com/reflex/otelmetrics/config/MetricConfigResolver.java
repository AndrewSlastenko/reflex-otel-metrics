package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.MetricSource;
import java.util.Objects;

public class MetricConfigResolver {

    private final ReflexOtelMetricsProperties properties;

    public MetricConfigResolver(ReflexOtelMetricsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public ResolvedMetricConfig resolve(MetricSource source) {
        Objects.requireNonNull(source, "source must not be null");

        MetricDefinitionDefaults defaults = source.defaults();
        MetricRuntimeProperties runtime = properties.getSources().getOrDefault(source.metricId(), new MetricRuntimeProperties());

        String suffix = runtime.getSuffix() != null ? runtime.getSuffix() : defaults.metricSuffix();
        String scope = runtime.getScope() != null ? runtime.getScope() : defaults.scope();
        String dataSourceRef = source instanceof com.reflex.otelmetrics.api.JdbcMetricSource
                ? (runtime.getDataSourceRef() != null ? runtime.getDataSourceRef() : defaults.dataSourceRef())
                : null;
        MetricScheduleSettings schedule = resolveSchedule(defaults.schedule(), runtime);
        var metricKind = runtime.getKind() != null ? runtime.getKind() : defaults.metricKind();
        var timeout = runtime.getTimeout() != null ? runtime.getTimeout() : defaults.timeout();
        var lockAtMostFor = runtime.getLockAtMostFor() != null ? runtime.getLockAtMostFor() : defaults.lockAtMostFor();
        var lockAtLeastFor = runtime.getLockAtLeastFor() != null ? runtime.getLockAtLeastFor() : defaults.lockAtLeastFor();
        int maxSeries = runtime.getMaxSeries() != null ? runtime.getMaxSeries() : defaults.maxSeries();
        var overflowPolicy = runtime.getOverflowPolicy() != null ? runtime.getOverflowPolicy() : defaults.overflowPolicy();

        boolean enabled = properties.isEnabled()
                && resolveScopeEnabled(scope)
                && !Boolean.FALSE.equals(runtime.getEnabled());

        return new ResolvedMetricConfig(
                source.metricId(),
                enabled,
                properties.getMetricPrefix() + "." + suffix,
                suffix,
                scope,
                dataSourceRef,
                metricKind,
                schedule,
                timeout,
                lockAtMostFor,
                lockAtLeastFor,
                maxSeries,
                overflowPolicy
        );
    }

    private boolean resolveScopeEnabled(String scope) {
        ReflexOtelMetricsProperties.ScopeProperties scopeProperties = properties.getScopes().get(scope);
        return scopeProperties == null || scopeProperties.isEnabled();
    }

    private static MetricScheduleSettings resolveSchedule(MetricScheduleDefaults defaults, MetricRuntimeProperties runtime) {
        if (runtime.getScheduleMode() != null) {
            return new MetricScheduleSettings(
                    runtime.getScheduleMode(),
                    runtime.getFixedDelay(),
                    runtime.getCron(),
                    runtime.getInitialDelay()
            );
        }

        return new MetricScheduleSettings(
                MetricScheduleSettings.Mode.valueOf(defaults.mode().name()),
                defaults.fixedDelay(),
                defaults.cron(),
                defaults.initialDelay()
        );
    }
}
