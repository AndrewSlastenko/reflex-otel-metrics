package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import ru.sber.rcln.reflex.telemetry.config.ManualMetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigValidator;
import ru.sber.rcln.reflex.telemetry.config.ReflexOtelMetricsProperties;
import ru.sber.rcln.reflex.telemetry.internal.LoggingSupport;
import ru.sber.rcln.reflex.telemetry.manual.AttributeValidator;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import ru.sber.rcln.reflex.telemetry.runtime.OverflowAggregationStrategy;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import ru.sber.rcln.reflex.telemetry.tracing.DefaultTraceOperations;
import ru.sber.rcln.reflex.telemetry.tracing.NoopTraceOperations;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ReflexOtelMetricsProperties.class)
public class ReflexOtelMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MetricConfigResolver metricConfigResolver(ReflexOtelMetricsProperties properties) {
        return new MetricConfigResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ManualMetricConfigResolver manualMetricConfigResolver(ReflexOtelMetricsProperties properties) {
        return new ManualMetricConfigResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    MetricConfigValidator metricConfigValidator() {
        return new MetricConfigValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    AttributeValidator attributeValidator() {
        return new AttributeValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    OverflowAggregationStrategy overflowAggregationStrategy() {
        return new OverflowAggregationStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    SeriesLimiter seriesLimiter(OverflowAggregationStrategy overflowAggregationStrategy) {
        return new SeriesLimiter(overflowAggregationStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    LoggingSupport loggingSupport() {
        return new LoggingSupport();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OtlpGrpcMetricExporter.class, OpenTelemetry.class})
    OtlpGrpcMetricExporter otlpGrpcMetricExporter(ReflexOtelMetricsProperties properties) {
        return OtlpGrpcMetricExporter.builder()
                .setEndpoint(properties.getOtlp().getMetricsEndpoint())
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OpenTelemetry.class})
    OtlpGrpcSpanExporter otlpGrpcSpanExporter(ReflexOtelMetricsProperties properties) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(properties.getOtlp().getTracesEndpoint())
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({SdkMeterProvider.class, OpenTelemetry.class})
    SdkMeterProvider sdkMeterProvider(OtlpGrpcMetricExporter exporter, ReflexOtelMetricsProperties properties) {
        return SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(exporter)
                        .setInterval(properties.getOtlp().getExportInterval())
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({SdkTracerProvider.class, OpenTelemetry.class})
    SdkTracerProvider sdkTracerProvider(OtlpGrpcSpanExporter exporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OpenTelemetrySdk.class, OpenTelemetry.class})
    OpenTelemetrySdk openTelemetrySdk(SdkMeterProvider sdkMeterProvider, SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .setTracerProvider(sdkTracerProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OpenTelemetry.class, OpenTelemetrySdk.class})
    OpenTelemetry openTelemetry(OpenTelemetrySdk openTelemetrySdk) {
        return openTelemetrySdk;
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    Meter meter(OpenTelemetry openTelemetry, ReflexOtelMetricsProperties properties) {
        return openTelemetry.getMeter(properties.getInstrumentationScopeName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    Tracer tracer(OpenTelemetry openTelemetry, ReflexOtelMetricsProperties properties) {
        return openTelemetry.getTracer(properties.getInstrumentationScopeName());
    }

    @Bean
    @ConditionalOnMissingBean
    TraceOperations traceOperations(ObjectProvider<Tracer> tracerProvider, ReflexOtelMetricsProperties properties) {
        if (!properties.getTraces().isEnabled()) {
            return new NoopTraceOperations();
        }

        Tracer tracer = tracerProvider.getIfAvailable();
        return tracer != null ? new DefaultTraceOperations(tracer) : new NoopTraceOperations();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    OtelInstrumentRegistry otelInstrumentRegistry(Meter meter) {
        return new OtelInstrumentRegistry(meter);
    }

    @Bean
    @ConditionalOnMissingBean
    ReflexMetricFactory reflexMetricFactory(
            ManualMetricConfigResolver manualMetricConfigResolver,
            ObjectProvider<OtelInstrumentRegistry> otelInstrumentRegistry,
            AttributeValidator attributeValidator) {
        return new ReflexMetricFactory(
                manualMetricConfigResolver,
                otelInstrumentRegistry::getIfAvailable,
                attributeValidator);
    }
}
