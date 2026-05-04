package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OtelInstrumentRegistryTest {

    @Test
    void shouldCreateCounterInstrument() {
        Meter meter = mock(Meter.class);
        LongCounterBuilder counterBuilder = mock(LongCounterBuilder.class);
        LongCounter counter = mock(LongCounter.class);
        when(meter.counterBuilder("orders.created")).thenReturn(counterBuilder);
        when(counterBuilder.build()).thenReturn(counter);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        Object instrument = registry.getOrCreate("orders.created", MetricKind.COUNTER);

        assertThat(instrument).isSameAs(counter);
        verify(meter).counterBuilder("orders.created");
    }

    @Test
    void shouldApplyCounterDescriptionAndUnitBeforeBuild() {
        Meter meter = mock(Meter.class);
        LongCounterBuilder counterBuilder = mock(LongCounterBuilder.class);
        LongCounter counter = mock(LongCounter.class);
        when(meter.counterBuilder("orders.created")).thenReturn(counterBuilder);
        when(counterBuilder.setDescription("Created orders")).thenReturn(counterBuilder);
        when(counterBuilder.setUnit("1")).thenReturn(counterBuilder);
        when(counterBuilder.build()).thenReturn(counter);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        Object instrument = registry.getOrCreate("orders.created", MetricKind.COUNTER, "Created orders", "1");

        assertThat(instrument).isSameAs(counter);
        InOrder inOrder = inOrder(counterBuilder);
        inOrder.verify(counterBuilder).setDescription("Created orders");
        inOrder.verify(counterBuilder).setUnit("1");
        inOrder.verify(counterBuilder).build();
    }

    @Test
    void shouldIgnoreBlankDescriptionAndUnit() {
        Meter meter = mock(Meter.class);
        LongCounterBuilder counterBuilder = mock(LongCounterBuilder.class);
        LongCounter counter = mock(LongCounter.class);
        when(meter.counterBuilder("orders.created")).thenReturn(counterBuilder);
        when(counterBuilder.build()).thenReturn(counter);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        Object instrument = registry.getOrCreate("orders.created", MetricKind.COUNTER, "  ", "");

        assertThat(instrument).isSameAs(counter);
        verify(counterBuilder, never()).setDescription("  ");
        verify(counterBuilder, never()).setUnit("");
        verify(counterBuilder).build();
    }

    @Test
    void shouldCacheInstrumentForTheSameMetricKind() {
        Meter meter = mock(Meter.class);
        DoubleGaugeBuilder gaugeBuilder = mock(DoubleGaugeBuilder.class);
        LongGaugeBuilder longGaugeBuilder = mock(LongGaugeBuilder.class);
        LongGauge gauge = mock(LongGauge.class);
        when(meter.gaugeBuilder("documents.current")).thenReturn(gaugeBuilder);
        when(gaugeBuilder.ofLongs()).thenReturn(longGaugeBuilder);
        when(longGaugeBuilder.build()).thenReturn(gauge);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        Object first = registry.getOrCreate("documents.current", MetricKind.GAUGE);
        Object second = registry.getOrCreate("documents.current", MetricKind.GAUGE);

        assertThat(first).isSameAs(gauge);
        assertThat(second).isSameAs(gauge);
        verify(meter).gaugeBuilder("documents.current");
    }

    @Test
    void shouldFailWhenMetricNameIsReusedWithDifferentKind() {
        Meter meter = mock(Meter.class);
        DoubleGaugeBuilder gaugeBuilder = mock(DoubleGaugeBuilder.class);
        LongGaugeBuilder longGaugeBuilder = mock(LongGaugeBuilder.class);
        when(meter.gaugeBuilder("documents.current")).thenReturn(gaugeBuilder);
        when(gaugeBuilder.ofLongs()).thenReturn(longGaugeBuilder);
        when(longGaugeBuilder.build()).thenReturn(mock(LongGauge.class));

        LongUpDownCounterBuilder upDownCounterBuilder = mock(LongUpDownCounterBuilder.class);
        when(meter.upDownCounterBuilder("documents.current")).thenReturn(upDownCounterBuilder);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);
        registry.getOrCreate("documents.current", MetricKind.GAUGE);

        assertThatThrownBy(() -> registry.getOrCreate("documents.current", MetricKind.UP_DOWN_COUNTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered as GAUGE")
                .hasMessageContaining("requested as UP_DOWN_COUNTER");

        verifyNoInteractions(upDownCounterBuilder);
    }
}
