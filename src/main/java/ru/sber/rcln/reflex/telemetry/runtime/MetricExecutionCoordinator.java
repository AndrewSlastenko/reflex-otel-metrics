package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;

import java.util.List;

public interface MetricExecutionCoordinator {

    List<MetricPoint> collect();
}
