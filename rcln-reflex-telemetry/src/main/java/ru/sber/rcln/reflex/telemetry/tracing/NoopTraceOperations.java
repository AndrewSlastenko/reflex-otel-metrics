package ru.sber.rcln.reflex.telemetry.tracing;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import java.util.Objects;
import java.util.function.Supplier;

public class NoopTraceOperations implements TraceOperations {

    @Override
    public void inSpan(SpanSpec spec, Runnable body) {
        Objects.requireNonNull(body, "body must not be null").run();
    }

    @Override
    public <T> T inSpan(SpanSpec spec, Supplier<T> body) {
        return Objects.requireNonNull(body, "body must not be null").get();
    }

    @Override
    public TraceCarrier captureCurrent() {
        return TraceCarrier.empty();
    }
}
