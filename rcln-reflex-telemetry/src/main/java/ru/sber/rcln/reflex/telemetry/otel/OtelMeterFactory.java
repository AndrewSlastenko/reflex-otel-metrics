package ru.sber.rcln.reflex.telemetry.otel;

import io.opentelemetry.api.metrics.Meter;

public interface OtelMeterFactory {

    Meter create();
}
