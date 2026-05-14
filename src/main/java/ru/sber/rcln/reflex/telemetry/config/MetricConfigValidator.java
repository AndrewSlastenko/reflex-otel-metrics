package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

public class MetricConfigValidator {

    public List<String> validate(ResolvedMetricConfig config) {
        List<String> errors = new ArrayList<>();

        if (config.suffix() == null || config.suffix().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires suffix");
        }

        if (config.dataSourceRef() == null || config.dataSourceRef().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires dataSourceRef");
        }

        MetricScheduleSettings schedule = config.schedule();
        if (schedule != null && schedule.mode() != null) {
            if (schedule.mode() == MetricScheduleSettings.Mode.FIXED_DELAY
                    && schedule.fixedDelay() == null) {
                errors.add("Metric '" + config.metricId() + "' requires fixedDelay for FIXED_DELAY mode");
            }

            if (schedule.mode() == MetricScheduleSettings.Mode.FIXED_DELAY
                    && schedule.cron() != null
                    && !schedule.cron().isBlank()) {
                errors.add("Metric '" + config.metricId() + "' must not set cron for FIXED_DELAY mode");
            }

            if (schedule.mode() == MetricScheduleSettings.Mode.CRON
                    && (schedule.cron() == null || schedule.cron().isBlank())) {
                errors.add("Metric '" + config.metricId() + "' requires cron for CRON mode");
            }

            if (schedule.mode() == MetricScheduleSettings.Mode.CRON
                    && schedule.fixedDelay() != null) {
                errors.add("Metric '" + config.metricId() + "' must not set fixedDelay for CRON mode");
            }
        }

        validateLockDuration("lockAtMostFor", config.metricId(), config.lockAtMostFor(), errors);
        validateLockDuration("lockAtLeastFor", config.metricId(), config.lockAtLeastFor(), errors);

        if (config.lockAtMostFor() != null
                && config.lockAtLeastFor() != null
                && !config.lockAtMostFor().isNegative()
                && !config.lockAtLeastFor().isNegative()
                && config.lockAtLeastFor().compareTo(config.lockAtMostFor()) > 0) {
            errors.add("Metric '" + config.metricId() + "' requires lockAtLeastFor to be less than or equal to lockAtMostFor");
        }

        if (config.metricKind() == MetricKind.HISTOGRAM
                && config.overflowPolicy() == SeriesOverflowPolicy.AGGREGATE_TO_OTHER) {
            errors.add("Metric '" + config.metricId()
                    + "' does not support AGGREGATE_TO_OTHER overflow policy for HISTOGRAM kind; use FAIL or TRUNCATE");
        }

        return errors;
    }

    private static void validateLockDuration(
            String fieldName,
            String metricId,
            Duration duration,
            List<String> errors
    ) {
        if (duration == null) {
            errors.add("Metric '" + metricId + "' requires " + fieldName);
            return;
        }

        if (duration.isNegative()) {
            errors.add("Metric '" + metricId + "' requires " + fieldName + " to be non-negative");
        }
    }
}
