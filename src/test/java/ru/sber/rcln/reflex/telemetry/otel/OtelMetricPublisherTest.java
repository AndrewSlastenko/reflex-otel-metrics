package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtelMetricPublisherTest {

    @Test
    void publishesPointsViaKindAwareWriter() {
        OtelInstrumentRegistry registry = mock(OtelInstrumentRegistry.class);
        MetricInstrumentWriter writer = mock(MetricInstrumentWriter.class);
        ResolvedMetricConfig config = config(MetricKind.COUNTER);
        when(registry.getOrCreateWriter(config.fullMetricName(), MetricKind.COUNTER)).thenReturn(writer);

        new OtelMetricPublisher(registry).publish(
                config,
                List.of(new MetricPoint(7L, Map.of("status", "created")))
        );

        verify(writer).record(eq(new MetricPoint(7L, Map.of("status", "created"))), eq(io.opentelemetry.api.common.Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("status"), "created"
        )));
    }

    @Test
    void publishesHistogramPointsViaWriter() {
        OtelInstrumentRegistry registry = mock(OtelInstrumentRegistry.class);
        MetricInstrumentWriter writer = mock(MetricInstrumentWriter.class);
        ResolvedMetricConfig config = config(MetricKind.HISTOGRAM);
        MetricPoint point = MetricPoint.histogram(9.75d, Map.of("scope", "business"));
        when(registry.getOrCreateWriter(config.fullMetricName(), MetricKind.HISTOGRAM)).thenReturn(writer);

        new OtelMetricPublisher(registry).publish(config, List.of(point));

        verify(writer).record(eq(point), eq(io.opentelemetry.api.common.Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("scope"), "business"
        )));
    }

    private static ResolvedMetricConfig config(MetricKind metricKind) {
        return new ResolvedMetricConfig(
                "orders-created",
                true,
                "ci054147.orders.created",
                "orders.created",
                "business",
                "businessReplicaDataSource",
                metricKind,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.FAIL
        );
    }
}
