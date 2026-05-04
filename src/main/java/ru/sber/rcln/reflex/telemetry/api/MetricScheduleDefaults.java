package ru.sber.rcln.reflex.telemetry.api;

import java.time.Duration;
import lombok.NonNull;

public record MetricScheduleDefaults(
        @NonNull Mode mode,
        Duration fixedDelay,
        String cron,
        Duration initialDelay
) {
    public MetricScheduleDefaults {
        if (mode == Mode.FIXED_DELAY) {
            if (fixedDelay == null) {
                throw new IllegalArgumentException("fixedDelay must not be null when mode is FIXED_DELAY");
            }
            if (cron != null && !cron.isBlank()) {
                throw new IllegalArgumentException("cron must be blank or null when mode is FIXED_DELAY");
            }
        } else if (mode == Mode.CRON) {
            if (fixedDelay != null) {
                throw new IllegalArgumentException("fixedDelay must be null when mode is CRON");
            }
            if (cron == null || cron.isBlank()) {
                throw new IllegalArgumentException("cron must not be blank when mode is CRON");
            }
        }
    }

    public enum Mode {
        FIXED_DELAY,
        CRON
    }
}
