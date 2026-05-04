package ru.sber.rcln.reflex.telemetry.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoopTraceOperationsTest {

    private final NoopTraceOperations traces = new NoopTraceOperations();

    @Test
    void shouldRunRunnableBody() {
        boolean[] called = {false};

        traces.inSpan(new SpanSpec("test.span", TraceCarrier.empty(), Map.of()), () -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    @Test
    void shouldRunSupplierBody() {
        String result = traces.inSpan(
                new SpanSpec("test.span", TraceCarrier.empty(), Map.of()),
                () -> "value");

        assertThat(result).isEqualTo("value");
    }

    @Test
    void shouldReturnEmptyCarrier() {
        assertThat(traces.captureCurrent().isEmpty()).isTrue();
    }
}
