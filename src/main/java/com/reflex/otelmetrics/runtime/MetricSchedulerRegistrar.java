package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.config.MetricScheduleSettings;
import com.reflex.otelmetrics.config.ResolvedMetricConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricSchedulerRegistrar {

    private final ScheduledExecutorService scheduledExecutorService;

    public MetricSchedulerRegistrar(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService, "scheduledExecutorService must not be null");
    }

    public void register(ResolvedMetricConfig config, Runnable runnable) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(runnable, "runnable must not be null");

        if (config.schedule().mode() == MetricScheduleSettings.Mode.FIXED_DELAY) {
            Duration initialDelay = config.schedule().initialDelay() == null ? Duration.ZERO : config.schedule().initialDelay();
            scheduledExecutorService.scheduleWithFixedDelay(
                    runnable,
                    initialDelay.toMillis(),
                    config.schedule().fixedDelay().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }
    }
}
