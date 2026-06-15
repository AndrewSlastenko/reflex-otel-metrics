package ru.sber.rcln.reflex.telemetry.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTraceOperationsTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private DefaultTraceOperations traces;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        Tracer tracer = openTelemetry.getTracer("test.scope");
        traces = new DefaultTraceOperations(tracer, openTelemetry.getPropagators());
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void shouldCreateSpanWithAttributes() {
        traces.inSpan(
                new SpanSpec(
                        "workflow.action.GetContractAction",
                        TraceCarrier.empty(),
                        Map.of("workflow.action.name", "GetContractAction")),
                () -> {
                });

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        assertThat(exporter.getFinishedSpanItems().get(0).getName())
                .isEqualTo("workflow.action.GetContractAction");
        assertThat(exporter.getFinishedSpanItems().get(0).getAttributes()
                .get(AttributeKey.stringKey("workflow.action.name")))
                .isEqualTo("GetContractAction");
    }

    @Test
    void shouldCaptureCurrentCarrierInsideSpan() {
        TraceCarrier carrier = traces.inSpan(
                new SpanSpec("workflow.action.CreateProcessAction", TraceCarrier.empty(), Map.of()),
                traces::captureCurrent);

        assertThat(carrier.traceparent()).isNotBlank();
        assertThat(carrier.traceparent()).startsWith("00-");
    }

    @Test
    void shouldContinueSpanFromCarrier() {
        TraceCarrier parentCarrier = traces.inSpan(
                new SpanSpec("parent.action", TraceCarrier.empty(), Map.of()),
                traces::captureCurrent);

        traces.inSpan(
                new SpanSpec("child.action", parentCarrier, Map.of()),
                () -> {
                });

        assertThat(exporter.getFinishedSpanItems()).hasSize(2);
        var parent = exporter.getFinishedSpanItems().get(0);
        var child = exporter.getFinishedSpanItems().get(1);
        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
    }

    @Test
    void shouldRecordExceptionAndRethrow() {
        RuntimeException failure = new RuntimeException("boom");

        assertThatThrownBy(() -> traces.inSpan(
                new SpanSpec("failing.action", TraceCarrier.empty(), Map.of()),
                () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        assertThat(exporter.getFinishedSpanItems().get(0).getStatus().getStatusCode().name())
                .isEqualTo("ERROR");
        assertThat(exporter.getFinishedSpanItems().get(0).getEvents())
                .anySatisfy(event -> assertThat(event.getName()).isEqualTo("exception"));
    }
}
