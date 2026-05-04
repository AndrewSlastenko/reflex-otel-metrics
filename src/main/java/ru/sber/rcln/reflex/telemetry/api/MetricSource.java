package ru.sber.rcln.reflex.telemetry.api;

public interface MetricSource {

    String metricId();

    MetricDefinitionDefaults defaults();
}
