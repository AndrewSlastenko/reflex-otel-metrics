package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import java.time.Duration;
import java.util.Objects;

public class MetricConfigResolver {

    public ResolvedMetricConfig resolve(MetricDefinitionDefaults beanDefaults, MetricRuntimeProperties properties) {
        Objects.requireNonNull(beanDefaults, "beanDefaults must not be null");

        return new ResolvedMetricConfig(
                choose(properties == null ? null : properties.metricSuffix(), beanDefaults.metricSuffix()),
                choose(properties == null ? null : properties.metricKind(), beanDefaults.metricKind()),
                choose(properties == null ? null : properties.scope(), beanDefaults.scope()),
                choose(properties == null ? null : properties.dataSourceRef(), beanDefaults.dataSourceRef()),
                resolveSchedule(beanDefaults.schedule(), properties == null ? null : properties.schedule()),
                choose(properties == null ? null : properties.timeout(), beanDefaults.timeout()),
                choose(properties == null ? null : properties.lockAtMostFor(), beanDefaults.lockAtMostFor()),
                choose(properties == null ? null : properties.lockAtLeastFor(), beanDefaults.lockAtLeastFor()),
                choose(properties == null ? null : properties.maxSeries(), beanDefaults.maxSeries()),
                choose(properties == null ? null : properties.overflowPolicy(), beanDefaults.overflowPolicy())
        );
    }

    private static MetricScheduleDefaults resolveSchedule(MetricScheduleDefaults beanDefaults, MetricScheduleSettings properties) {
        if (beanDefaults == null && properties == null) {
            return null;
        }

        if (properties == null) {
            return beanDefaults;
        }

        if (beanDefaults == null) {
            return new MetricScheduleDefaults(
                    properties.mode(),
                    properties.fixedDelay(),
                    properties.cron(),
                    properties.initialDelay()
            );
        }

        return new MetricScheduleDefaults(
                choose(properties.mode(), beanDefaults.mode()),
                choose(properties.fixedDelay(), beanDefaults.fixedDelay()),
                choose(properties.cron(), beanDefaults.cron()),
                choose(properties.initialDelay(), beanDefaults.initialDelay())
        );
    }

    private static <T> T choose(T override, T fallback) {
        return override != null ? override : fallback;
    }

    private static int choose(Integer override, int fallback) {
        return override != null ? override : fallback;
    }

    private static Duration choose(Duration override, Duration fallback) {
        return override != null ? override : fallback;
    }
}
