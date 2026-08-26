package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private ServiceProperties service = new ServiceProperties();
    private OtlpProperties otlp = new OtlpProperties();
    private MetricsProperties metrics = new MetricsProperties();
    private TraceProperties traces = new TraceProperties();

    public void setService(ServiceProperties service) {
        this.service = service != null ? service : new ServiceProperties();
    }

    public void setOtlp(OtlpProperties otlp) {
        this.otlp = otlp != null ? otlp : new OtlpProperties();
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics != null ? metrics : new MetricsProperties();
    }

    public void setTraces(TraceProperties traces) {
        this.traces = traces != null ? traces : new TraceProperties();
    }

    @Getter
    @Setter
    public static class ServiceProperties {

        private String systemCode;
        private String name;
        private String instrumentationScopeName = "ru.sber.rcln.reflex.telemetry";
    }

    @Getter
    @Setter
    public static class OtlpProperties {

        private OtlpProtocol protocol = OtlpProtocol.HTTP_PROTOBUF;
        private String endpoint = "http://localhost:4318";
        private Duration exportTimeout = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    public static class MetricsProperties {

        private boolean enabled = true;
        private String endpoint;
        private Duration exportInterval = Duration.ofMinutes(1);
        private MetricsTemporalityPreference temporalityPreference = MetricsTemporalityPreference.DELTA;
        private PayloadLoggingProperties payloadLogging = new PayloadLoggingProperties();
        private JdbcProperties jdbc = new JdbcProperties();
        private Map<String, ScopeProperties> scopes = new LinkedHashMap<>();
        @Setter(AccessLevel.NONE)
        private Map<String, MetricDefinitionProperties> definitions = new LinkedHashMap<>();

        public void setJdbc(JdbcProperties jdbc) {
            this.jdbc = jdbc != null ? jdbc : new JdbcProperties();
        }

        public void setPayloadLogging(PayloadLoggingProperties payloadLogging) {
            this.payloadLogging = payloadLogging != null ? payloadLogging : new PayloadLoggingProperties();
        }

        public void setScopes(Map<String, ScopeProperties> scopes) {
            this.scopes = scopes != null ? scopes : new LinkedHashMap<>();
        }

        public void setDefinitions(Map<String, MetricDefinitionProperties> definitions) {
            this.definitions = definitions != null ? definitions : new LinkedHashMap<>();
        }
    }

    @Getter
    @Setter
    public static class PayloadLoggingProperties {

        /**
         * Log each exported metric snapshot as OTLP JSON before the delivery exporter sends it.
         * The payload can contain metric values and attributes, so enable this only for diagnostics.
         */
        private boolean enabled;
    }

    @Getter
    @Setter
    public static class JdbcProperties {

        private boolean enabled = true;
        private String lockProviderRef;
        private SchedulerProperties scheduler = new SchedulerProperties();

        public void setScheduler(SchedulerProperties scheduler) {
            this.scheduler = scheduler != null ? scheduler : new SchedulerProperties();
        }
    }

    @Getter
    @Setter
    public static class SchedulerProperties {

        private int poolSize = 2;
    }

    @Getter
    @Setter
    public static class TraceProperties {

        private boolean enabled = true;
        private String endpoint;
        private TracePropagation propagation = TracePropagation.W3C;
    }

    @Getter
    @Setter
    public static class MetricDefinitionProperties {

        private MetricSourceType source;
        private MetricKind kind;
        private Boolean enabled;
        private String name;
        private String scope;
        private String description;
        private String unit;
        private AttributeSchemaProperties attributes = new AttributeSchemaProperties();
        private HistogramProperties histogram = new HistogramProperties();
        private String dataSourceRef;
        private QueryProperties query = new QueryProperties();
        private ScheduleProperties schedule = new ScheduleProperties();
        private Duration timeout = Duration.ofSeconds(30);
        private Duration lockAtMostFor = Duration.ofMinutes(2);
        private Duration lockAtLeastFor = Duration.ZERO;
        private Integer maxSeries = 500;
        private SeriesOverflowPolicy overflowPolicy = SeriesOverflowPolicy.FAIL;

        public void setAttributes(AttributeSchemaProperties attributes) {
            this.attributes = attributes != null ? attributes : new AttributeSchemaProperties();
        }

        public void setHistogram(HistogramProperties histogram) {
            this.histogram = histogram != null ? histogram : new HistogramProperties();
        }

        public void setQuery(QueryProperties query) {
            this.query = query != null ? query : new QueryProperties();
        }

        public void setSchedule(ScheduleProperties schedule) {
            this.schedule = schedule != null ? schedule : new ScheduleProperties();
        }
    }

    @Getter
    @Setter
    public static class QueryProperties {

        /**
         * Database schema name for JDBC SQL built by the application.
         * Read via {@link ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings#schema(String)}.
         */
        private String schema;
    }

    @Getter
    @Setter
    public static class AttributeSchemaProperties {

        private List<String> required = new ArrayList<>();
        private List<String> optional = new ArrayList<>();
        private boolean rejectUnknown = true;

        public void setRequired(List<String> required) {
            this.required = required != null ? required : new ArrayList<>();
        }

        public void setOptional(List<String> optional) {
            this.optional = optional != null ? optional : new ArrayList<>();
        }
    }

    @Getter
    @Setter
    public static class HistogramProperties {

        private List<Double> buckets = new ArrayList<>();

        public void setBuckets(List<Double> buckets) {
            this.buckets = buckets != null ? buckets : new ArrayList<>();
        }
    }

    @Getter
    @Setter
    public static class ScheduleProperties {

        private MetricScheduleSettings.Mode mode = MetricScheduleSettings.Mode.FIXED_DELAY;
        private Duration fixedDelay = Duration.ofMinutes(1);
        private String cron;
        private Duration initialDelay = Duration.ZERO;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScopeProperties {

        private boolean enabled = true;
    }

    public enum MetricSourceType {
        MANUAL,
        JDBC
    }

    public enum MetricsTemporalityPreference {
        DELTA,
        CUMULATIVE,
        LOW_MEMORY
    }

    public enum TracePropagation {
        W3C
    }

    public enum OtlpProtocol {
        GRPC,
        HTTP_PROTOBUF
    }
}
