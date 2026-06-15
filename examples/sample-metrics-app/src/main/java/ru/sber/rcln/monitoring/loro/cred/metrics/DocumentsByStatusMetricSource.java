package ru.sber.rcln.monitoring.loro.cred.metrics;

import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.jdbc.AbstractJdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;

@Component
public class DocumentsByStatusMetricSource extends AbstractJdbcMetricSource {

    public DocumentsByStatusMetricSource(JdbcMetricQuerySettings querySettings) {
        super("documents-by-status", querySettings);
    }

    @Override
    protected QueryDefinition buildQuery(String schema) {
        return new QueryDefinition("""
                select client_code, document_status, count(*) as cnt
                from %s.transaction_view
                group by client_code, document_status
                """.formatted(schema).strip());
    }

    @Override
    public RowMapper<MetricPoint> rowMapper() {
        return (rs, rowNum) -> new MetricPoint(
                rs.getLong("cnt"),
                Map.of(
                        "client", rs.getString("client_code"),
                        "status", rs.getString("document_status")));
    }
}
