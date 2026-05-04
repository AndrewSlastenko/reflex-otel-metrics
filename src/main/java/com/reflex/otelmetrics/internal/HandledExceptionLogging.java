package com.reflex.otelmetrics.internal;

import org.slf4j.Logger;

/**
 * Reduces log volume for handled failures: one WARN/ERROR line without a stack trace,
 * full stack only at DEBUG when explicitly enabled for the logger.
 */
public final class HandledExceptionLogging {

    private HandledExceptionLogging() {
    }

    public static void warnSkippedManualPublish(Logger log, String metricId, String instrumentKind, Throwable exception) {
        log.warn("Metric {} skipped {} publish: {}", metricId, instrumentKind, oneLine(exception));
        if (log.isDebugEnabled()) {
            log.debug("Metric {} skipped {} publish", metricId, instrumentKind, exception);
        }
    }

    public static void errorCollectionFailure(Logger log, String metricId, Throwable exception) {
        log.error("Metric {} failed during collection: {}", metricId, oneLine(exception));
        if (log.isDebugEnabled()) {
            log.debug("Metric {} failed during collection", metricId, exception);
        }
    }

    private static String oneLine(Throwable exception) {
        String type = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ": " + message;
    }
}
