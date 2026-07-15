package ru.sber.rcln.reflex.telemetry.internal;

import org.slf4j.Logger;

/**
 * Reduces log volume for handled failures with a concise WARN/ERROR line.
 * Callers can add a full stack trace at DEBUG when more runtime context is available.
 */
public final class HandledExceptionLogging {

    private static final int MAX_EXCEPTION_MESSAGE_LENGTH = 500;

    private HandledExceptionLogging() {
    }

    public static void warnSkippedManualPublish(Logger log, String metricId, String instrumentKind, Throwable exception) {
        log.warn("Metric {} skipped {} publish: {}", metricId, instrumentKind, oneLine(exception));
        if (log.isDebugEnabled()) {
            log.debug("Metric {} skipped {} publish", metricId, instrumentKind, exception);
        }
    }

    public static void errorJdbcExecutionFailure(Logger log, String metricId, Throwable exception) {
        log.error("Metric {} failed during JDBC execution: {}", metricId, oneLine(exception));
    }

    private static String oneLine(Throwable exception) {
        String type = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        if (message.length() > MAX_EXCEPTION_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_EXCEPTION_MESSAGE_LENGTH) + "...";
        }
        return type + ": " + message;
    }
}
