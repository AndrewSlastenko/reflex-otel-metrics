package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricSource;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import lombok.NonNull;

public class MetricConfigResolver {

    private final @NonNull ReflexTelemetryProperties properties;
    private final @NonNull ReflexTelemetryNamingPolicy namingPolicy;

    public MetricConfigResolver(@NonNull ReflexTelemetryProperties properties) {
        this(properties, new ReflexTelemetryNamingPolicy(properties.getSystemCode()));
    }

    public MetricConfigResolver(
            @NonNull ReflexTelemetryProperties properties,
            @NonNull ReflexTelemetryNamingPolicy namingPolicy) {
        this.properties = properties;
        this.namingPolicy = namingPolicy;
    }

    public ResolvedMetricConfig resolve(@NonNull MetricSource source) {

        MetricDefinitionDefaults defaults = source.defaults();
        MetricRuntimeProperties runtime = properties.getMetrics()
                .getSources()
                .getOrDefault(source.metricId(), new MetricRuntimeProperties());

        String suffix = runtime.getSuffix() != null ? runtime.getSuffix() : defaults.metricSuffix();
        String scope = runtime.getScope() != null ? runtime.getScope() : defaultScope(source, defaults);
        String dataSourceRef = source instanceof JdbcMetricSource
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
                && properties.getMetrics().isEnabled()
                && resolveScopeEnabled(scope)
                && !Boolean.FALSE.equals(runtime.getEnabled());

        return new ResolvedMetricConfig(
                source.metricId(),
                enabled,
                namingPolicy.metricName(suffix),
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

    private static String defaultScope(MetricSource source, MetricDefinitionDefaults defaults) {
        String scope = defaults.scope();
        if (scope != null && !scope.isBlank()) {
            return scope;
        }
        if (source instanceof JdbcMetricSource) {
            return ReflexMetricScopes.JDBC;
        }
        return scope;
    }

    private boolean resolveScopeEnabled(String scope) {
        ReflexTelemetryProperties.ScopeProperties scopeProperties = properties.getMetrics().getScopes().get(scope);
        return scopeProperties == null || scopeProperties.isEnabled();
    }

    private static MetricScheduleSettings resolveSchedule(MetricScheduleDefaults defaults, MetricRuntimeProperties runtime) {
        MetricScheduleSettings.Mode defaultMode = MetricScheduleSettings.Mode.valueOf(defaults.mode().name());
        MetricScheduleSettings.Mode runtimeMode = runtime.getScheduleMode();

        if (runtimeMode == null || runtimeMode == defaultMode) {
            MetricScheduleSettings.Mode mode = runtimeMode != null ? runtimeMode : defaultMode;
            return new MetricScheduleSettings(
                    mode,
                    runtime.getFixedDelay() != null ? runtime.getFixedDelay() : defaults.fixedDelay(),
                    runtime.getCron() != null ? runtime.getCron() : defaults.cron(),
                    runtime.getInitialDelay() != null ? runtime.getInitialDelay() : defaults.initialDelay()
            );
        }

        if (runtimeMode == MetricScheduleSettings.Mode.CRON) {
            return new MetricScheduleSettings(
                    MetricScheduleSettings.Mode.CRON,
                    null,
                    runtime.getCron(),
                    runtime.getInitialDelay() != null ? runtime.getInitialDelay() : defaults.initialDelay()
            );
        }

        return new MetricScheduleSettings(
                MetricScheduleSettings.Mode.FIXED_DELAY,
                runtime.getFixedDelay(),
                null,
                runtime.getInitialDelay() != null ? runtime.getInitialDelay() : defaults.initialDelay()
        );
    }
}
