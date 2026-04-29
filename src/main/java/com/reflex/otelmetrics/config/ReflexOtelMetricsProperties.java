package com.reflex.otelmetrics.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reflex.otel.metrics")
public class ReflexOtelMetricsProperties {

    private boolean enabled = true;
    private String metricPrefix = "reflex";
    private OtlpProperties otlp = new OtlpProperties();
    private Map<String, ScopeProperties> scopes = new LinkedHashMap<>();
    private Map<String, MetricRuntimeProperties> sources = new LinkedHashMap<>();

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

    public static class OtlpProperties {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ScopeProperties {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
