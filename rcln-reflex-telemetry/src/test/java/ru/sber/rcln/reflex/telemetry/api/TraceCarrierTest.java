package ru.sber.rcln.reflex.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceCarrierTest {

    @Test
    void shouldDetectEmptyCarrier() {
        assertThat(TraceCarrier.empty().isEmpty()).isTrue();
        assertThat(new TraceCarrier(null, null).isEmpty()).isTrue();
        assertThat(new TraceCarrier("", "").isEmpty()).isTrue();
    }

    @Test
    void shouldDetectCarrierWithTraceparent() {
        TraceCarrier carrier = new TraceCarrier(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                null);

        assertThat(carrier.isEmpty()).isFalse();
    }
}
