package com.reflex.otelmetrics.jdbc;

import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.QueryDefinition;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcMetricCollector {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMetricCollector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MetricPoint> collect(QueryDefinition queryDefinition, RowMapper<MetricPoint> rowMapper) {
        return jdbcTemplate.query(queryDefinition.sql(), rowMapper);
    }
}
