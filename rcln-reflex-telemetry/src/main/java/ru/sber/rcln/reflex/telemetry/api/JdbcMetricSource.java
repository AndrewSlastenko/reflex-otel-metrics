package ru.sber.rcln.reflex.telemetry.api;

import org.springframework.jdbc.core.RowMapper;

public interface JdbcMetricSource extends MetricSource {

    QueryDefinition queryDefinition();

    RowMapper<MetricPoint> rowMapper();
}
