package com.reflex.otelmetrics.api;

import java.util.List;

public record JdbcMetricSource(
        String name,
        String dataSourceBeanName,
        MetricDefinitionDefaults definitionDefaults,
        MetricScheduleDefaults scheduleDefaults,
        List<QueryDefinition> queries
) implements MetricSource {
}
