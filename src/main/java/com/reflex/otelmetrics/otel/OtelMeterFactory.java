package com.reflex.otelmetrics.otel;

import io.opentelemetry.api.metrics.Meter;

public interface OtelMeterFactory {

    Meter create();
}
