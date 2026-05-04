package ru.sber.rcln.reflex.telemetry.config;

import java.time.Duration;

public record MetricScheduleSettings(
        Mode mode,
        Duration fixedDelay,
        String cron,
        Duration initialDelay
) {

    public enum Mode {
        FIXED_DELAY,
        CRON
    }

    public static MetricScheduleSettings fixedDelay(Duration fixedDelay, Duration initialDelay) {
        return new MetricScheduleSettings(Mode.FIXED_DELAY, fixedDelay, null, initialDelay);
    }
}
