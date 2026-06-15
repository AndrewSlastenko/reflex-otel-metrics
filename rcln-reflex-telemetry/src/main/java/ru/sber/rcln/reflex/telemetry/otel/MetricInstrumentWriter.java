package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import io.opentelemetry.api.common.Attributes;

@FunctionalInterface
public interface MetricInstrumentWriter {

    void record(MetricPoint point, Attributes attributes);
}
