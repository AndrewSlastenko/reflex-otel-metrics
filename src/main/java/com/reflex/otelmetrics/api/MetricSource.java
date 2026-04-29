package com.reflex.otelmetrics.api;

public interface MetricSource {

    String metricId();

    MetricDefinitionDefaults defaults();
}
