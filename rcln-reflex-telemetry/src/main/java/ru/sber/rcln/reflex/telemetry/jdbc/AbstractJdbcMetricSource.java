package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import java.util.Objects;

/**
 * Base {@link JdbcMetricSource} that resolves {@code query.schema} from YAML once per bean
 * via injected {@link JdbcMetricQuerySettings}, so subclasses declare {@code metricId} only once.
 */
public abstract class AbstractJdbcMetricSource implements JdbcMetricSource {

    private final String metricId;
    private final JdbcMetricQuerySettings querySettings;

    protected AbstractJdbcMetricSource(String metricId, JdbcMetricQuerySettings querySettings) {
        this.metricId = Objects.requireNonNull(metricId, "metricId");
        this.querySettings = Objects.requireNonNull(querySettings, "querySettings");
        if (metricId.isBlank()) {
            throw new IllegalArgumentException("metricId must not be blank");
        }
    }

    @Override
    public final String metricId() {
        return metricId;
    }

    protected final String schema() {
        return querySettings.schema(metricId);
    }

    protected final String requireSchema() {
        return querySettings.requireSchema(metricId);
    }

    @Override
    public final QueryDefinition queryDefinition() {
        return buildQuery(requireSchema());
    }

    protected abstract QueryDefinition buildQuery(String schema);
}
