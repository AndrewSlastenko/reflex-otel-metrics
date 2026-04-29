package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricPoint;

import java.util.List;

public interface MetricExecutionCoordinator {

    List<MetricPoint> collect();
}
