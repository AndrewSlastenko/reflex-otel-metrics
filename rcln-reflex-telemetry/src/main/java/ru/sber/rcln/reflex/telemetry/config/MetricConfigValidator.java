package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MetricConfigValidator {

    /**
     * Conservative SQL identifier pattern used to validate YAML-provided schema names.
     * Schema values are read by the application through {@code JdbcMetricQuerySettings} and embedded into SQL,
     * so we reject anything outside ASCII letters/digits/underscore to keep that path safe by default.
     */
    private static final Pattern SCHEMA_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public List<String> validate(ResolvedMetricConfig config) {
        List<String> errors = new ArrayList<>();

        if (config.source() == null) {
            errors.add("Metric '" + config.metricId() + "' requires source");
        }
        if (config.metricKind() == null) {
            errors.add("Metric '" + config.metricId() + "' requires kind");
        }
        if (config.name() == null || config.name().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires name");
        }
        if (config.scope() == null || config.scope().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires scope");
        }
        if (config.maxSeries() < 1) {
            errors.add("Metric '" + config.metricId() + "' requires maxSeries to be greater than zero");
        }

        if (config.source() == ReflexTelemetryProperties.MetricSourceType.JDBC) {
            validateJdbc(config, errors);
        }

        if (config.source() == ReflexTelemetryProperties.MetricSourceType.MANUAL
                && config.dataSourceRef() != null
                && !config.dataSourceRef().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' must not set dataSourceRef for MANUAL source");
        }

        if (config.source() == ReflexTelemetryProperties.MetricSourceType.MANUAL
                && config.querySchema() != null) {
            errors.add("Metric '" + config.metricId() + "' must not set query.schema for MANUAL source");
        }

        if (config.source() == ReflexTelemetryProperties.MetricSourceType.MANUAL
                && config.overflowPolicy() == SeriesOverflowPolicy.AGGREGATE_TO_OTHER) {
            errors.add("Metric '" + config.metricId()
                    + "' does not support AGGREGATE_TO_OTHER overflow policy for MANUAL source; use FAIL or TRUNCATE");
        }

        if (config.metricKind() == MetricKind.HISTOGRAM) {
            validateHistogramBuckets(config, errors);
        } else if (!config.histogramBuckets().isEmpty()) {
            errors.add("Metric '" + config.metricId() + "' must not set histogram buckets for " + config.metricKind() + " kind");
        }

        if (config.metricKind() == MetricKind.HISTOGRAM
                && config.overflowPolicy() == SeriesOverflowPolicy.AGGREGATE_TO_OTHER) {
            errors.add("Metric '" + config.metricId()
                    + "' does not support AGGREGATE_TO_OTHER overflow policy for HISTOGRAM kind; use FAIL or TRUNCATE");
        }

        return errors;
    }

    public List<String> validateDuplicateExportedNames(Map<String, List<String>> exportedNameToMetricIds) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : exportedNameToMetricIds.entrySet()) {
            List<String> metricIds = entry.getValue();
            if (metricIds.size() > 1) {
                errors.add("Exported metric name '" + entry.getKey() + "' is used by multiple definitions: "
                        + String.join(", ", metricIds));
            }
        }
        return errors;
    }

    private static void validateJdbc(ResolvedMetricConfig config, List<String> errors) {
        if (config.dataSourceRef() == null || config.dataSourceRef().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires dataSourceRef for JDBC source");
        }

        if (config.querySchema() != null && !SCHEMA_IDENTIFIER.matcher(config.querySchema()).matches()) {
            errors.add("Metric '" + config.metricId() + "' requires query.schema to be a simple SQL identifier "
                    + "(letters, digits, underscore; not starting with a digit)");
        }

        MetricScheduleSettings schedule = config.schedule();
        if (schedule == null || schedule.mode() == null) {
            errors.add("Metric '" + config.metricId() + "' requires schedule mode for JDBC source");
            return;
        }

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

        validateTimeout(config, errors);
        validateLockDuration("lockAtMostFor", config.metricId(), config.lockAtMostFor(), errors);
        validateLockDuration("lockAtLeastFor", config.metricId(), config.lockAtLeastFor(), errors);

        if (config.lockAtMostFor() != null
                && config.lockAtLeastFor() != null
                && !config.lockAtMostFor().isNegative()
                && !config.lockAtLeastFor().isNegative()
                && config.lockAtLeastFor().compareTo(config.lockAtMostFor()) > 0) {
            errors.add("Metric '" + config.metricId() + "' requires lockAtLeastFor to be less than or equal to lockAtMostFor");
        }
    }

    private static void validateTimeout(ResolvedMetricConfig config, List<String> errors) {
        if (config.timeout() == null) {
            errors.add("Metric '" + config.metricId() + "' requires timeout");
            return;
        }

        if (config.timeout().isZero() || config.timeout().isNegative()) {
            errors.add("Metric '" + config.metricId() + "' requires timeout to be positive");
        }
    }

    private static void validateHistogramBuckets(ResolvedMetricConfig config, List<String> errors) {
        double previous = 0d;
        boolean first = true;
        for (Double bucket : config.histogramBuckets()) {
            if (bucket == null || !Double.isFinite(bucket) || bucket <= 0d) {
                errors.add("Metric '" + config.metricId() + "' requires histogram buckets to be positive finite numbers");
                return;
            }
            if (!first && bucket <= previous) {
                errors.add("Metric '" + config.metricId() + "' requires histogram buckets to be strictly increasing");
                return;
            }
            previous = bucket;
            first = false;
        }
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
