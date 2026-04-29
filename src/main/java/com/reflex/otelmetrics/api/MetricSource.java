package com.reflex.otelmetrics.api;

import java.util.List;

public interface MetricSource {

    String name();

    MetricDefinitionDefaults definitionDefaults();

    MetricScheduleDefaults scheduleDefaults();

    List<QueryDefinition> queries();
}
