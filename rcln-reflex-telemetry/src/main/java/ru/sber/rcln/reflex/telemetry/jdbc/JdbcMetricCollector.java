package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class JdbcMetricCollector {

    private final @NonNull JdbcTemplate jdbcTemplate;

    public List<MetricPoint> collect(QueryDefinition queryDefinition, RowMapper<MetricPoint> rowMapper) {
        return jdbcTemplate.query(queryDefinition.sql(), rowMapper);
    }
}
