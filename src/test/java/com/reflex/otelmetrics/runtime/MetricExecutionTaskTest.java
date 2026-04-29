package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.config.MetricScheduleSettings;
import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import com.reflex.otelmetrics.internal.InternalTelemetryRecorder;
import com.reflex.otelmetrics.otel.OtelMetricPublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricExecutionTaskTest {

    @Test
    void shouldPublishPointsWhenExecutionSucceeds() {
        MetricExecutionCoordinator coordinator = mock(MetricExecutionCoordinator.class);
        com.reflex.otelmetrics.locking.MetricLockManager lockManager = mock(com.reflex.otelmetrics.locking.MetricLockManager.class);
        OtelMetricPublisher publisher = mock(OtelMetricPublisher.class);
        InternalTelemetryRecorder telemetryRecorder = mock(InternalTelemetryRecorder.class);
        SeriesLimiter seriesLimiter = new SeriesLimiter(new OverflowAggregationStrategy());
        when(coordinator.collect()).thenReturn(List.of(new MetricPoint(10L, Map.of("status", "created"))));
        when(lockManager.executeWithLock(any(), any())).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        });

        MetricExecutionTask task = new MetricExecutionTask(
                coordinator,
                lockManager,
                publisher,
                telemetryRecorder,
                seriesLimiter,
                new ResolvedMetricConfig(
                        "documents-by-status",
                        true,
                        "ci054147.documents.current",
                        "documents.current",
                        "business",
                        "businessReplicaDataSource",
                        com.reflex.otelmetrics.api.MetricKind.UP_DOWN_COUNTER,
                        MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        Duration.ZERO,
                        500,
                        SeriesOverflowPolicy.AGGREGATE_TO_OTHER
                )
        );

        MetricRunOutcome outcome = task.runOnce();

        assertThat(outcome).isEqualTo(MetricRunOutcome.SUCCESS);
        verify(publisher).publish(any(), any());
        verify(telemetryRecorder).recordSuccess(any());
    }

    @Test
    void shouldRecordFailureWithoutThrowing() {
        MetricExecutionCoordinator coordinator = mock(MetricExecutionCoordinator.class);
        com.reflex.otelmetrics.locking.MetricLockManager lockManager = mock(com.reflex.otelmetrics.locking.MetricLockManager.class);
        OtelMetricPublisher publisher = mock(OtelMetricPublisher.class);
        InternalTelemetryRecorder telemetryRecorder = mock(InternalTelemetryRecorder.class);
        SeriesLimiter seriesLimiter = new SeriesLimiter(new OverflowAggregationStrategy());
        when(coordinator.collect()).thenThrow(new IllegalStateException("boom"));
        when(lockManager.executeWithLock(any(), any())).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        });

        MetricExecutionTask task = new MetricExecutionTask(
                coordinator,
                lockManager,
                publisher,
                telemetryRecorder,
                seriesLimiter,
                new ResolvedMetricConfig(
                        "documents-by-status",
                        true,
                        "ci054147.documents.current",
                        "documents.current",
                        "business",
                        "businessReplicaDataSource",
                        com.reflex.otelmetrics.api.MetricKind.UP_DOWN_COUNTER,
                        MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        Duration.ZERO,
                        500,
                        SeriesOverflowPolicy.AGGREGATE_TO_OTHER
                )
        );

        MetricRunOutcome outcome = task.runOnce();

        assertThat(outcome).isEqualTo(MetricRunOutcome.FAILED);
        verify(telemetryRecorder).recordFailure(any(), any());
    }
}
