package com.reflex.otelmetrics.internal;

import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSupport {

    private static final Logger log = LoggerFactory.getLogger(LoggingSupport.class);

    public void startupValidationFailure(String metricId, String message) {
        log.error("Metric {} disabled during startup validation: {}", metricId, message);
    }

    public void runtimeFailure(ResolvedMetricConfig config, Exception exception) {
        log.error("Metric {} failed during collection", config.metricId(), exception);
    }
}
