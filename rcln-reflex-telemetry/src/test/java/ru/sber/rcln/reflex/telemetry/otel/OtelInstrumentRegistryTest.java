package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OtelInstrumentRegistryTest {

    @Test
    void shouldCreateCounterWriter() {
        Meter meter = mock(Meter.class);
        LongCounterBuilder counterBuilder = mock(LongCounterBuilder.class);
        LongCounter counter = mock(LongCounter.class);
        when(meter.counterBuilder("orders.created")).thenReturn(counterBuilder);
        when(counterBuilder.build()).thenReturn(counter);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        MetricInstrumentWriter writer = registry.getOrCreateWriter("orders.created", MetricKind.COUNTER);
        writer.record(new MetricPoint(7L, java.util.Map.of()), io.opentelemetry.api.common.Attributes.empty());

        assertThat(writer).isNotNull();
        verify(meter).counterBuilder("orders.created");
        verify(counter).add(eq(7L), eq(io.opentelemetry.api.common.Attributes.empty()));
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

        MetricInstrumentWriter writer = registry.getOrCreateWriter("orders.created", MetricKind.COUNTER, "Created orders", "1");

        assertThat(writer).isNotNull();
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

        MetricInstrumentWriter writer = registry.getOrCreateWriter("orders.created", MetricKind.COUNTER, "  ", "");

        assertThat(writer).isNotNull();
        verify(counterBuilder, never()).setDescription("  ");
        verify(counterBuilder, never()).setUnit("");
        verify(counterBuilder).build();
    }

    @Test
    void shouldCacheWriterForTheSameMetricKind() {
        Meter meter = mock(Meter.class);
        DoubleGaugeBuilder gaugeBuilder = mock(DoubleGaugeBuilder.class);
        LongGaugeBuilder longGaugeBuilder = mock(LongGaugeBuilder.class);
        when(meter.gaugeBuilder("documents.current")).thenReturn(gaugeBuilder);
        when(gaugeBuilder.ofLongs()).thenReturn(longGaugeBuilder);
        when(longGaugeBuilder.buildWithCallback(any())).thenReturn(mock(io.opentelemetry.api.metrics.ObservableLongGauge.class));

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        MetricInstrumentWriter first = registry.getOrCreateWriter("documents.current", MetricKind.GAUGE);
        MetricInstrumentWriter second = registry.getOrCreateWriter("documents.current", MetricKind.GAUGE);

        assertThat(first).isSameAs(second);
        verify(meter).gaugeBuilder("documents.current");
        verify(longGaugeBuilder).buildWithCallback(any());
    }

    @Test
    void shouldRejectGetOrCreateForGauge() {
        Meter meter = mock(Meter.class);
        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        assertThatThrownBy(() -> registry.getOrCreate("documents.current", MetricKind.GAUGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gauge instruments are observable; use getOrCreateWriter(...)");
    }

    @Test
    void shouldFailWhenMetricNameIsReusedWithDifferentKind() {
        Meter meter = mock(Meter.class);
        DoubleGaugeBuilder gaugeBuilder = mock(DoubleGaugeBuilder.class);
        LongGaugeBuilder longGaugeBuilder = mock(LongGaugeBuilder.class);
        when(meter.gaugeBuilder("documents.current")).thenReturn(gaugeBuilder);
        when(gaugeBuilder.ofLongs()).thenReturn(longGaugeBuilder);
        when(longGaugeBuilder.buildWithCallback(any())).thenReturn(mock(io.opentelemetry.api.metrics.ObservableLongGauge.class));

        LongUpDownCounterBuilder upDownCounterBuilder = mock(LongUpDownCounterBuilder.class);
        when(meter.upDownCounterBuilder("documents.current")).thenReturn(upDownCounterBuilder);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);
        registry.getOrCreateWriter("documents.current", MetricKind.GAUGE);

        assertThatThrownBy(() -> registry.getOrCreate("documents.current", MetricKind.UP_DOWN_COUNTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered as GAUGE")
                .hasMessageContaining("requested as UP_DOWN_COUNTER");

        verifyNoInteractions(upDownCounterBuilder);
    }

    @Test
    void shouldCreateHistogramWriter() {
        Meter meter = mock(Meter.class);
        DoubleHistogramBuilder histogramBuilder = mock(DoubleHistogramBuilder.class);
        DoubleHistogram histogram = mock(DoubleHistogram.class);
        when(meter.histogramBuilder("orders.duration")).thenReturn(histogramBuilder);
        when(histogramBuilder.build()).thenReturn(histogram);

        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

        MetricInstrumentWriter writer = registry.getOrCreateWriter("orders.duration", MetricKind.HISTOGRAM);
        writer.record(MetricPoint.histogram(12.5d, java.util.Map.<String, String>of()), io.opentelemetry.api.common.Attributes.empty());

        assertThat(writer).isNotNull();
        verify(histogram).record(eq(12.5d), eq(io.opentelemetry.api.common.Attributes.empty()));
    }
}
