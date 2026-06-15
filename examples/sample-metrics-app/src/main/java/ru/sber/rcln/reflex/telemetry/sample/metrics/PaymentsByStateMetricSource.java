package ru.sber.rcln.reflex.telemetry.sample.metrics;

import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.jdbc.AbstractJdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;

@Component
public class PaymentsByStateMetricSource extends AbstractJdbcMetricSource {

    public PaymentsByStateMetricSource(JdbcMetricQuerySettings querySettings) {
        super("payments-by-state", querySettings);
    }

    @Override
    protected QueryDefinition buildQuery(String schema) {
        return new QueryDefinition("""
                select payment_state, count(*) as cnt
                from %s.payment_view
                group by payment_state
                """.formatted(schema).strip());
    }

    @Override
    public RowMapper<MetricPoint> rowMapper() {
        return (rs, rowNum) -> new MetricPoint(
                rs.getLong("cnt"),
                Map.of("state", rs.getString("payment_state")));
    }
}
