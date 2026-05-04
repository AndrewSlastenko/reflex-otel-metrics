package com.reflex.otelmetrics.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reflex.otel.metrics")
public class ReflexOtelMetricsProperties {

    private boolean enabled = true;
    private String metricPrefix = "reflex";
    private String instrumentationScopeName = "com.reflex.otelmetrics";
    private OtlpProperties otlp = new OtlpProperties();
    private Map<String, ScopeProperties> scopes = new LinkedHashMap<>();
    private Map<String, MetricRuntimeProperties> sources = new LinkedHashMap<>();
    private Map<String, ManualMetricRuntimeProperties> manual = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMetricPrefix() {
        return metricPrefix;
    }

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
    }

    public String getInstrumentationScopeName() {
        return instrumentationScopeName;
    }

    public void setInstrumentationScopeName(String instrumentationScopeName) {
        this.instrumentationScopeName = instrumentationScopeName;
    }

    public OtlpProperties getOtlp() {
        return otlp;
    }

    public void setOtlp(OtlpProperties otlp) {
        this.otlp = otlp;
    }

    public Map<String, ScopeProperties> getScopes() {
        return scopes;
    }

    public void setScopes(Map<String, ScopeProperties> scopes) {
        this.scopes = scopes;
    }

    public Map<String, MetricRuntimeProperties> getSources() {
        return sources;
    }

    public void setSources(Map<String, MetricRuntimeProperties> sources) {
        this.sources = sources;
    }

    public Map<String, ManualMetricRuntimeProperties> getManual() {
        return manual;
    }

    public void setManual(Map<String, ManualMetricRuntimeProperties> manual) {
        this.manual = manual != null ? manual : new LinkedHashMap<>();
    }

    public static class OtlpProperties {

        private String metricsEndpoint = "http://localhost:4317";
        private String tracesEndpoint = "http://localhost:4317";
        private java.time.Duration exportTimeout = java.time.Duration.ofSeconds(10);
        private java.time.Duration exportInterval = java.time.Duration.ofMinutes(1);

        public String getMetricsEndpoint() {
            return metricsEndpoint;
        }

        public void setMetricsEndpoint(String metricsEndpoint) {
            this.metricsEndpoint = metricsEndpoint;
        }

        public String getTracesEndpoint() {
            return tracesEndpoint;
        }

        public void setTracesEndpoint(String tracesEndpoint) {
            this.tracesEndpoint = tracesEndpoint;
        }

        public java.time.Duration getExportTimeout() {
            return exportTimeout;
        }

        public void setExportTimeout(java.time.Duration exportTimeout) {
            this.exportTimeout = exportTimeout;
        }

        public java.time.Duration getExportInterval() {
            return exportInterval;
        }

        public void setExportInterval(java.time.Duration exportInterval) {
            this.exportInterval = exportInterval;
        }
    }

    public static class ScopeProperties {

        private boolean enabled = true;

        public ScopeProperties() {
        }

        public ScopeProperties(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
