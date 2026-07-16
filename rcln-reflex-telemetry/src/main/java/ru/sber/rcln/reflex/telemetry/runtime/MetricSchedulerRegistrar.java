package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricSchedulerRegistrar {

    private final @NonNull ScheduledExecutorService scheduledExecutorService;

    public void register(@NonNull ResolvedMetricConfig config, @NonNull Runnable runnable) {

        if (config.schedule().mode() == MetricScheduleSettings.Mode.FIXED_DELAY) {
            scheduleWithFixedDelay(config, runnable);
            return;
        }

        if (config.schedule().mode() == MetricScheduleSettings.Mode.CRON) {
            scheduleCron(config, runnable);
        }
    }

    private void scheduleWithFixedDelay(ResolvedMetricConfig config, Runnable runnable) {
        Duration initialDelay = config.schedule().initialDelay() == null ? Duration.ZERO : config.schedule().initialDelay();
        scheduledExecutorService.scheduleWithFixedDelay(
                runnable,
                initialDelay.toMillis(),
                config.schedule().fixedDelay().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    private void scheduleCron(ResolvedMetricConfig config, Runnable runnable) {
        CronExpression cronExpression = CronExpression.parse(normalizeCronExpression(config.schedule().cron()));
        Duration initialDelay = config.schedule().initialDelay() == null
                ? nextDelay(cronExpression)
                : config.schedule().initialDelay();
        scheduleCronRun(cronExpression, runnable, initialDelay);
    }

    private void scheduleCronRun(CronExpression cronExpression, Runnable runnable, Duration delay) {
        scheduledExecutorService.schedule(
                () -> {
                    try {
                        runnable.run();
                    } finally {
                        scheduleCronRun(cronExpression, runnable, nextDelay(cronExpression));
                    }
                },
                Math.max(0L, delay.toNanos()),
                TimeUnit.NANOSECONDS
        );
    }

    private Duration nextDelay(CronExpression cronExpression) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextExecution = cronExpression.next(now);
        if (nextExecution == null) {
            throw new IllegalStateException("cron expression did not produce a next execution time");
        }
        return Duration.between(now, nextExecution);
    }

    private static String normalizeCronExpression(String cronExpression) {
        String[] fields = cronExpression.trim().split("\\s+");
        if (fields.length == 5) {
            return "0 " + cronExpression.trim();
        }
        if (fields.length == 6) {
            return cronExpression.trim();
        }
        throw new IllegalArgumentException("cron expression must have 5 or 6 fields");
    }
}
