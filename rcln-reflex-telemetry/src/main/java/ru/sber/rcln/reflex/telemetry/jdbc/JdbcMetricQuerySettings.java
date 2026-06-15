package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import lombok.NonNull;

/**
 * Read-only access to JDBC query parameters from {@code reflex.telemetry.metrics.definitions.*.query}.
 * Intended for injection into application {@link ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource} beans.
 */
public final class JdbcMetricQuerySettings {

    private final @NonNull MetricConfigResolver configResolver;

    public JdbcMetricQuerySettings(@NonNull MetricConfigResolver configResolver) {
        this.configResolver = configResolver;
    }

    /**
     * Returns {@code query.schema} for the metric, or {@code null} when not configured.
     */
    public String schema(@NonNull String metricId) {
        return configResolver.resolve(metricId).querySchema();
    }

    /**
     * Returns {@code query.schema} for the metric, failing fast when it is missing.
     */
    public String requireSchema(@NonNull String metricId) {
        String schema = schema(metricId);
        if (schema == null) {
            throw new IllegalStateException(
                    "Metric '" + metricId + "' requires reflex.telemetry.metrics.definitions."
                            + metricId + ".query.schema");
        }
        return schema;
    }
}
