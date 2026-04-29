package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import java.time.Duration;

public record MetricScheduleSettings(
        MetricScheduleDefaults.Mode mode,
        Duration fixedDelay,
        String cron,
        Duration initialDelay
) {
}
