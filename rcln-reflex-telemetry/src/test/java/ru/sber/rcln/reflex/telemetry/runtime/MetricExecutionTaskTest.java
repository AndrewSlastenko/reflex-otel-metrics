package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricExecutionTaskTest {

    @Test
    void shouldPublishPointsWhenExecutionSucceeds() {
        MetricExecutionCoordinator coordinator = mock(MetricExecutionCoordinator.class);
        ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager = mock(ru.sber.rcln.reflex.telemetry.locking.MetricLockManager.class);
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
                        ReflexTelemetryProperties.MetricSourceType.JDBC,
                        true,
                        "ci054147.documents.current",
                        "documents.current",
                        "business",
                        "Documents current",
                        "1",
                        AttributesSchema.empty(),
                        "businessReplicaDataSource",
                        ru.sber.rcln.reflex.telemetry.api.MetricKind.UP_DOWN_COUNTER,
                        MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        Duration.ZERO,
                        500,
                        SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                        List.of()
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
        ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager = mock(ru.sber.rcln.reflex.telemetry.locking.MetricLockManager.class);
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
                        ReflexTelemetryProperties.MetricSourceType.JDBC,
                        true,
                        "ci054147.documents.current",
                        "documents.current",
                        "business",
                        "Documents current",
                        "1",
                        AttributesSchema.empty(),
                        "businessReplicaDataSource",
                        ru.sber.rcln.reflex.telemetry.api.MetricKind.UP_DOWN_COUNTER,
                        MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        Duration.ZERO,
                        500,
                        SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                        List.of()
                )
        );

        MetricRunOutcome outcome = task.runOnce();

        assertThat(outcome).isEqualTo(MetricRunOutcome.FAILED);
        verify(telemetryRecorder).recordFailure(any(), any());
    }

    @Test
    void shouldFailHistogramRunWhenOverflowPolicyRequiresUnsupportedAggregation() {
        MetricExecutionCoordinator coordinator = mock(MetricExecutionCoordinator.class);
        ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager = mock(ru.sber.rcln.reflex.telemetry.locking.MetricLockManager.class);
        OtelMetricPublisher publisher = mock(OtelMetricPublisher.class);
        InternalTelemetryRecorder telemetryRecorder = mock(InternalTelemetryRecorder.class);
        SeriesLimiter seriesLimiter = new SeriesLimiter(new OverflowAggregationStrategy());
        when(coordinator.collect()).thenReturn(List.of(
                MetricPoint.histogram(1.1, Map.of("status", "a")),
                MetricPoint.histogram(2.2, Map.of("status", "b")),
                MetricPoint.histogram(3.3, Map.of("status", "c"))
        ));
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
                        "documents-latency",
                        ReflexTelemetryProperties.MetricSourceType.JDBC,
                        true,
                        "ci054147.documents.latency",
                        "documents.latency",
                        "business",
                        "Documents latency",
                        "s",
                        AttributesSchema.empty(),
                        "businessReplicaDataSource",
                        MetricKind.HISTOGRAM,
                        MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        Duration.ZERO,
                        2,
                        SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                        List.of(1.0, 2.0, 5.0)
                )
        );

        MetricRunOutcome outcome = task.runOnce();

        assertThat(outcome).isEqualTo(MetricRunOutcome.FAILED);
        verify(publisher, never()).publish(any(), any());
        verify(telemetryRecorder).recordFailure(any(), any());
    }
}
