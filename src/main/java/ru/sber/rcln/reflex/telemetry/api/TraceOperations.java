package ru.sber.rcln.reflex.telemetry.api;

import java.util.function.Supplier;

public interface TraceOperations {

    void inSpan(SpanSpec spec, Runnable body);

    <T> T inSpan(SpanSpec spec, Supplier<T> body);

    TraceCarrier captureCurrent();
}
