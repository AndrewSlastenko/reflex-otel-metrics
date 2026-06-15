package ru.sber.rcln.reflex.telemetry.api;

public record TraceCarrier(String traceparent, String tracestate) {

    public static TraceCarrier empty() {
        return new TraceCarrier(null, null);
    }

    public boolean isEmpty() {
        return traceparent == null || traceparent.isBlank();
    }
}
