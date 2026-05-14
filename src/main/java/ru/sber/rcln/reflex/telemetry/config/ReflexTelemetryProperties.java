package ru.sber.rcln.reflex.telemetry.config;

import java.time.Duration;
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
@ConfigurationProperties(prefix = "reflex.telemetry")
public class ReflexTelemetryProperties {

    private boolean enabled = true;
    private String instrumentationScopeName = "ru.sber.rcln.reflex.telemetry";
    private String serviceName;
    private String systemCode;
    private OtlpProperties otlp = new OtlpProperties();
    private MetricsProperties metrics = new MetricsProperties();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private TraceProperties traces = new TraceProperties();

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics != null ? metrics : new MetricsProperties();
    }

    public void setOtlp(OtlpProperties otlp) {
        this.otlp = otlp != null ? otlp : new OtlpProperties();
    }

    public TraceProperties getTraces() {
        return traces;
    }

    public void setTraces(TraceProperties traces) {
        this.traces = traces != null ? traces : new TraceProperties();
    }

    @Getter
    @Setter
    public static class MetricsProperties {

        private boolean enabled = true;
        private Map<String, ScopeProperties> scopes = new LinkedHashMap<>();
        private Map<String, MetricRuntimeProperties> sources = new LinkedHashMap<>();
        @Setter(AccessLevel.NONE)
        private Map<String, ManualMetricRuntimeProperties> manual = new LinkedHashMap<>();

        public void setScopes(Map<String, ScopeProperties> scopes) {
            this.scopes = scopes != null ? scopes : new LinkedHashMap<>();
        }

        public void setSources(Map<String, MetricRuntimeProperties> sources) {
            this.sources = sources != null ? sources : new LinkedHashMap<>();
        }

        public void setManual(Map<String, ManualMetricRuntimeProperties> manual) {
            this.manual = manual != null ? manual : new LinkedHashMap<>();
        }
    }

    @Getter
    @Setter
    public static class OtlpProperties {

        private String metricsEndpoint = "http://localhost:4317";
        private String tracesEndpoint = "http://localhost:4317";
        private Duration exportTimeout = Duration.ofSeconds(10);
        private Duration exportInterval = Duration.ofMinutes(1);
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
