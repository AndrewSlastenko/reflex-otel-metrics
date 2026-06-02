package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricSource;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.NonNull;

public class MetricConfigResolver {

    private final @NonNull ReflexTelemetryProperties properties;
    private final @NonNull ReflexTelemetryNamingPolicy namingPolicy;
    private final MetricConfigValidator validator = new MetricConfigValidator();

    public MetricConfigResolver(@NonNull ReflexTelemetryProperties properties) {
        this(properties, new ReflexTelemetryNamingPolicy(properties.getService().getSystemCode()));
    }

    public MetricConfigResolver(
            @NonNull ReflexTelemetryProperties properties,
            @NonNull ReflexTelemetryNamingPolicy namingPolicy) {
        this.properties = properties;
        this.namingPolicy = namingPolicy;
    }

    public ResolvedMetricConfig resolve(@NonNull MetricSource source) {
        ResolvedMetricConfig config = resolve(source.metricId());
        if (config.source() != ReflexTelemetryProperties.MetricSourceType.JDBC || !(source instanceof JdbcMetricSource)) {
            throw new IllegalArgumentException("Metric '" + source.metricId() + "' must be configured with source JDBC");
        }
        return config;
    }

    public ResolvedMetricConfig resolve(String metricId) {
        if (metricId == null || metricId.isBlank()) {
            throw new IllegalArgumentException("metricId must not be blank");
        }

        ReflexTelemetryProperties.MetricDefinitionProperties definition = properties.getMetrics()
                .getDefinitions()
                .get(metricId);
        if (definition == null) {
            throw new IllegalArgumentException("Metric '" + metricId + "' is not configured under reflex.telemetry.metrics.definitions");
        }

        String suffix = definition.getSuffix();
        String scope = definition.getScope() != null ? definition.getScope() : defaultScope(definition.getSource());
        boolean enabled = properties.isEnabled()
                && properties.getMetrics().isEnabled()
                && resolveScopeEnabled(scope)
                && !Boolean.FALSE.equals(definition.getEnabled());

        ResolvedMetricConfig config = new ResolvedMetricConfig(
                metricId,
                definition.getSource(),
                enabled,
                hasText(suffix) ? namingPolicy.metricName(suffix) : null,
                suffix,
                scope,
                definition.getDescription(),
                definition.getUnit(),
                attributes(definition.getAttributes()),
                definition.getDataSourceRef(),
                definition.getKind(),
                schedule(definition.getSchedule()),
                definition.getTimeout(),
                definition.getLockAtMostFor(),
                definition.getLockAtLeastFor(),
                definition.getMaxSeries() != null ? definition.getMaxSeries() : 500,
                definition.getOverflowPolicy(),
                definition.getHistogram().getBuckets()
        );
        validate(config);
        return config;
    }

    public ResolvedMetricConfig resolveManual(String metricId, MetricKind expectedKind) {
        if (!isMetricsGloballyEnabled() && !hasDefinition(metricId)) {
            return disabledManualConfig(metricId, expectedKind);
        }

        ResolvedMetricConfig config = resolve(metricId);
        if (config.source() != ReflexTelemetryProperties.MetricSourceType.MANUAL) {
            throw new IllegalArgumentException("Metric '" + metricId + "' must be configured with source MANUAL");
        }
        if (config.metricKind() != expectedKind) {
            throw new IllegalArgumentException("Metric '" + metricId + "' is configured as "
                    + config.metricKind() + " but requested as " + expectedKind);
        }
        return config;
    }

    private boolean hasDefinition(String metricId) {
        return metricId != null
                && properties.getMetrics().getDefinitions().containsKey(metricId);
    }

    private boolean isMetricsGloballyEnabled() {
        return properties.isEnabled() && properties.getMetrics().isEnabled();
    }

    private ResolvedMetricConfig disabledManualConfig(String metricId, MetricKind expectedKind) {
        if (metricId == null || metricId.isBlank()) {
            throw new IllegalArgumentException("metricId must not be blank");
        }
        return new ResolvedMetricConfig(
                metricId,
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                false,
                metricId,
                metricId,
                ReflexMetricScopes.MANUAL,
                null,
                null,
                AttributesSchema.empty(),
                null,
                expectedKind,
                null,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO,
                1,
                SeriesOverflowPolicy.FAIL,
                List.of());
    }

    private boolean resolveScopeEnabled(String scope) {
        ReflexTelemetryProperties.ScopeProperties scopeProperties = properties.getMetrics().getScopes().get(scope);
        return scopeProperties == null || scopeProperties.isEnabled();
    }

    private static String defaultScope(ReflexTelemetryProperties.MetricSourceType source) {
        if (source == ReflexTelemetryProperties.MetricSourceType.JDBC) {
            return ReflexMetricScopes.JDBC;
        }
        return ReflexMetricScopes.MANUAL;
    }

    private static MetricScheduleSettings schedule(ReflexTelemetryProperties.ScheduleProperties properties) {
        MetricScheduleSettings.Mode mode = properties.getMode();
        return new MetricScheduleSettings(
                mode,
                mode == MetricScheduleSettings.Mode.CRON ? null : properties.getFixedDelay(),
                properties.getCron(),
                properties.getInitialDelay());
    }

    private static AttributesSchema attributes(ReflexTelemetryProperties.AttributeSchemaProperties properties) {
        return new AttributesSchema(
                new LinkedHashSet<>(properties.getRequired()),
                new LinkedHashSet<>(properties.getOptional()),
                properties.isRejectUnknown());
    }

    private void validate(ResolvedMetricConfig config) {
        var errors = validator.validate(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
