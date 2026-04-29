package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.QueryDefinition;
import org.springframework.jdbc.core.RowMapper;

public record TestJdbcMetricSource(
        String metricId,
        MetricDefinitionDefaults defaults,
        QueryDefinition queryDefinition,
        RowMapper<MetricPoint> rowMapper
) implements JdbcMetricSource {
}
