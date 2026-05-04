package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MetricSchedulerRegistrarTest {

    @Test
    void fixedDelayRegistrationDelegatesToScheduleWithFixedDelay() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        MetricSchedulerRegistrar registrar = new MetricSchedulerRegistrar(executor);
        Runnable runnable = mock(Runnable.class);

        registrar.register(fixedDelayConfig(), runnable);

        verify(executor).scheduleWithFixedDelay(runnable, 30_000L, 300_000L, TimeUnit.MILLISECONDS);
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void cronRegistrationSchedulesAndReschedulesAgain() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        MetricSchedulerRegistrar registrar = new MetricSchedulerRegistrar(executor);
        Runnable runnable = mock(Runnable.class);

        registrar.register(cronConfig(), runnable);

        var captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(captor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        captor.getValue().run();

        verify(runnable).run();
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(executor, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
    }

    private static ResolvedMetricConfig fixedDelayConfig() {
        return new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci054147.documents.current",
                "documents.current",
                "business",
                "businessReplicaDataSource",
                MetricKind.UP_DOWN_COUNTER,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );
    }

    private static ResolvedMetricConfig cronConfig() {
        return new ResolvedMetricConfig(
                "cron-metric",
                true,
                "ci054147.cron.metric",
                "cron.metric",
                "business",
                "businessReplicaDataSource",
                MetricKind.GAUGE,
                new MetricScheduleSettings(
                        MetricScheduleSettings.Mode.CRON,
                        null,
                        "0 * * * *",
                        Duration.ofSeconds(15)
                ),
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );
    }
}
