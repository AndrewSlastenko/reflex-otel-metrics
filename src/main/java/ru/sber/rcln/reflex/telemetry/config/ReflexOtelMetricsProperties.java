package ru.sber.rcln.reflex.telemetry.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "reflex.otel.metrics")
public class ReflexOtelMetricsProperties {

    private boolean enabled = true;
    private String metricPrefix = "reflex";
    private String instrumentationScopeName = "ru.sber.rcln.reflex.telemetry";
    private OtlpProperties otlp = new OtlpProperties();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private TraceProperties traces = new TraceProperties();
    private Map<String, ScopeProperties> scopes = new LinkedHashMap<>();
    private Map<String, MetricRuntimeProperties> sources = new LinkedHashMap<>();
    @Setter(AccessLevel.NONE)
    private Map<String, ManualMetricRuntimeProperties> manual = new LinkedHashMap<>();

    public TraceProperties getTraces() {
        return traces;
    }

    public void setTraces(TraceProperties traces) {
        this.traces = traces != null ? traces : new TraceProperties();
    }

    public void setManual(Map<String, ManualMetricRuntimeProperties> manual) {
        this.manual = manual != null ? manual : new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class OtlpProperties {

        private String metricsEndpoint = "http://localhost:4317";
        private String tracesEndpoint = "http://localhost:4317";
        private java.time.Duration exportTimeout = java.time.Duration.ofSeconds(10);
        private java.time.Duration exportInterval = java.time.Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class TraceProperties {

        private boolean enabled = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScopeProperties {

        private boolean enabled = true;
    }
}
