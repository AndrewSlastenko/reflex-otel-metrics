package com.reflex.otelmetrics.config;

import java.util.ArrayList;
import java.util.List;

public class MetricConfigValidator {

    public List<String> validate(ResolvedMetricConfig config) {
        List<String> errors = new ArrayList<>();

        if (config == null) {
            errors.add("config must not be null");
            return errors;
        }

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
                errors.add("Metric '" + config.metricId() + "' requires fixedDelay for FIXED_DELAY schedule mode");
            }

            if (schedule.mode() == MetricScheduleSettings.Mode.CRON
                    && (schedule.cron() == null || schedule.cron().isBlank())) {
                errors.add("Metric '" + config.metricId() + "' requires cron for CRON schedule mode");
            }
        }

        return errors;
    }
}
