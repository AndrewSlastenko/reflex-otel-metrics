package com.reflex.otelmetrics.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reflex.otelmetrics")
public class ReflexOtelMetricsProperties {

    private MetricRuntimeProperties defaults = new MetricRuntimeProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    private Map<String, MetricRuntimeProperties> metrics = new LinkedHashMap<>();

    public MetricRuntimeProperties getDefaults() {
        return defaults;
    }

    public void setDefaults(MetricRuntimeProperties defaults) {
        this.defaults = defaults;
    }

    public Map<String, MetricRuntimeProperties> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, MetricRuntimeProperties> metrics) {
        this.metrics = metrics;
    }
}
