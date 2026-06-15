package ru.sber.rcln.reflex.telemetry.tracing;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class DefaultTraceOperations implements TraceOperations {

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;

    private final Tracer tracer;
    private final ContextPropagators propagators;

    public DefaultTraceOperations(Tracer tracer) {
        this(tracer, ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
    }

    public DefaultTraceOperations(Tracer tracer, ContextPropagators propagators) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
        this.propagators = Objects.requireNonNull(propagators, "propagators must not be null");
    }

    @Override
    public void inSpan(SpanSpec spec, Runnable body) {
        inSpan(spec, () -> {
            Objects.requireNonNull(body, "body must not be null").run();
            return null;
        });
    }

    @Override
    public <T> T inSpan(SpanSpec spec, Supplier<T> body) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(body, "body must not be null");

        SpanBuilder spanBuilder = tracer.spanBuilder(spec.name());
        Context parentContext = extractParent(spec.parent());
        if (parentContext != null) {
            spanBuilder.setParent(parentContext);
        }
        spanBuilder.setAllAttributes(toAttributes(spec.attributes()));

        Span span = spanBuilder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return body.get();
        } catch (RuntimeException | Error exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @Override
    public TraceCarrier captureCurrent() {
        Map<String, String> carrier = new LinkedHashMap<>();
        propagators.getTextMapPropagator().inject(Context.current(), carrier, MAP_SETTER);
        return new TraceCarrier(carrier.get("traceparent"), carrier.get("tracestate"));
    }

    private Context extractParent(TraceCarrier carrier) {
        if (carrier == null || carrier.isEmpty()) {
            return null;
        }

        Map<String, String> map = new LinkedHashMap<>();
        map.put("traceparent", carrier.traceparent());
        if (carrier.tracestate() != null && !carrier.tracestate().isBlank()) {
            map.put("tracestate", carrier.tracestate());
        }
        return propagators.getTextMapPropagator().extract(Context.current(), map, MAP_GETTER);
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                builder.put(key, value);
            }
        });
        return builder.build();
    }
}
