package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtelMetricPublisherTest {

    @Test
    void publishesCounterPointsToLongCounter() {
        OtelInstrumentRegistry registry = mock(OtelInstrumentRegistry.class);
        LongCounter counter = mock(LongCounter.class);
        ResolvedMetricConfig config = config(MetricKind.COUNTER);
        when(registry.getOrCreate(config.fullMetricName(), MetricKind.COUNTER)).thenReturn(counter);

        new OtelMetricPublisher(registry).publish(
                config,
                List.of(new MetricPoint(7L, Map.of("status", "created")))
        );

        verify(counter).add(eq(7L), any(Attributes.class));
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
