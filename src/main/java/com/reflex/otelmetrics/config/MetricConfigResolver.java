package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.MetricSource;
import java.time.Duration;
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

        String suffix = firstNonBlank(runtime.getSuffix(), defaults.metricSuffix());
        String scope = firstNonBlank(runtime.getScope(), defaults.scope());
        String dataSourceRef = source instanceof JdbcMetricSource
                ? firstNonBlank(runtime.getDataSourceRef(), defaults.dataSourceRef())
                : null;
        MetricKind metricKind = runtime.getKind() != null ? runtime.getKind() : defaults.metricKind();
        MetricScheduleSettings schedule = resolveSchedule(defaults.schedule(), runtime);
        Duration timeout = runtime.getTimeout() != null ? runtime.getTimeout() : defaults.timeout();
        Duration lockAtMostFor = runtime.getLockAtMostFor() != null ? runtime.getLockAtMostFor() : defaults.lockAtMostFor();
        Duration lockAtLeastFor = runtime.getLockAtLeastFor() != null ? runtime.getLockAtLeastFor() : defaults.lockAtLeastFor();
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
        if (scope == null || scope.isBlank()) {
            return true;
        }

        ReflexOtelMetricsProperties.ScopeProperties scopeProperties = properties.getScopes().get(scope);
        return scopeProperties == null || scopeProperties.isEnabled();
    }

    private static MetricScheduleSettings resolveSchedule(MetricScheduleDefaults defaults, MetricRuntimeProperties runtime) {
        if (defaults == null && runtime.getScheduleMode() == null && runtime.getFixedDelay() == null
                && runtime.getCron() == null && runtime.getInitialDelay() == null) {
            return null;
        }

        MetricScheduleSettings.Mode mode = runtime.getScheduleMode() != null
                ? runtime.getScheduleMode()
                : (defaults == null ? null : MetricScheduleSettings.Mode.valueOf(defaults.mode().name()));
        if (mode == null) {
            if (runtime.getCron() != null || (defaults != null && defaults.cron() != null)) {
                mode = MetricScheduleSettings.Mode.CRON;
            } else if (runtime.getFixedDelay() != null || (defaults != null && defaults.fixedDelay() != null)) {
                mode = MetricScheduleSettings.Mode.FIXED_DELAY;
            }
        }
        Duration fixedDelay = runtime.getFixedDelay() != null
                ? runtime.getFixedDelay()
                : (defaults == null ? null : defaults.fixedDelay());
        String cron = runtime.getCron() != null ? runtime.getCron() : (defaults == null ? null : defaults.cron());
        Duration initialDelay = runtime.getInitialDelay() != null
                ? runtime.getInitialDelay()
                : (defaults == null ? null : defaults.initialDelay());
        return new MetricScheduleSettings(mode, fixedDelay, cron, initialDelay);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
