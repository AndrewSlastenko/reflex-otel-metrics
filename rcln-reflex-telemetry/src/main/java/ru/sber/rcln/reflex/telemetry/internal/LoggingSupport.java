package ru.sber.rcln.reflex.telemetry.internal;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LoggingSupport implements InternalTelemetryRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingSupport.class);
    private static final long FAILURE_LOG_INTERVAL_NANOS = Duration.ofMinutes(5).toNanos();

    private final ConcurrentMap<String, Long> nextFailureLogByMetricId = new ConcurrentHashMap<>();

    public void startupValidationFailure(String metricId, String message) {
        log.error("Metric {} disabled during startup validation: {}", metricId, message);
    }

    @Override
    public void recordSuccess(ResolvedMetricConfig config) {
    }

    @Override
    public void recordFailure(ResolvedMetricConfig config, Exception exception) {
        runtimeFailure(config, exception);
    }

    @Override
    public void recordSkipped(ResolvedMetricConfig config) {
    }

    public void runtimeFailure(ResolvedMetricConfig config, Exception exception) {
        if (shouldLogFailure(config.metricId())) {
            HandledExceptionLogging.errorJdbcExecutionFailure(log, config.metricId(), exception);
        }
    }

    private boolean shouldLogFailure(String metricId) {
        long now = System.nanoTime();
        boolean[] shouldLog = {false};
        nextFailureLogByMetricId.compute(metricId, (ignored, deadline) -> {
            if (deadline == null || now - deadline >= 0) {
                shouldLog[0] = true;
                return now + FAILURE_LOG_INTERVAL_NANOS;
            }
            return deadline;
        });
        return shouldLog[0];
    }
}
