package com.reflex.otelmetrics.autoconfigure;

import com.reflex.otelmetrics.config.ManualMetricConfigResolver;
import com.reflex.otelmetrics.config.MetricConfigResolver;
import com.reflex.otelmetrics.config.MetricConfigValidator;
import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.internal.LoggingSupport;
import com.reflex.otelmetrics.manual.AttributeValidator;
import com.reflex.otelmetrics.manual.ReflexMetricFactory;
import com.reflex.otelmetrics.otel.OtelInstrumentRegistry;
import com.reflex.otelmetrics.runtime.OverflowAggregationStrategy;
import com.reflex.otelmetrics.runtime.SeriesLimiter;
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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ReflexOtelMetricsProperties.class)
@ConditionalOnProperty(prefix = "reflex.otel.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnMissingBean({OtlpGrpcMetricExporter.class, OpenTelemetry.class})
    OtlpGrpcMetricExporter otlpGrpcMetricExporter(ReflexOtelMetricsProperties properties) {
        return OtlpGrpcMetricExporter.builder()
                .setEndpoint(properties.getOtlp().getMetricsEndpoint())
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OpenTelemetry.class})
    OtlpGrpcSpanExporter otlpGrpcSpanExporter(ReflexOtelMetricsProperties properties) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(properties.getOtlp().getTracesEndpoint())
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean({SdkMeterProvider.class, OpenTelemetry.class})
    SdkMeterProvider sdkMeterProvider(OtlpGrpcMetricExporter exporter, ReflexOtelMetricsProperties properties) {
        return SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(exporter)
                        .setInterval(properties.getOtlp().getExportInterval())
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean({SdkTracerProvider.class, OpenTelemetry.class})
    SdkTracerProvider sdkTracerProvider(OtlpGrpcSpanExporter exporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean({OpenTelemetrySdk.class, OpenTelemetry.class})
    OpenTelemetrySdk openTelemetrySdk(SdkMeterProvider sdkMeterProvider, SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .setTracerProvider(sdkTracerProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean({OpenTelemetry.class, OpenTelemetrySdk.class})
    OpenTelemetry openTelemetry(OpenTelemetrySdk openTelemetrySdk) {
        return openTelemetrySdk;
    }

    @Bean
    @ConditionalOnMissingBean
    Meter meter(OpenTelemetry openTelemetry, ReflexOtelMetricsProperties properties) {
        return openTelemetry.getMeter(properties.getInstrumentationScopeName());
    }

    @Bean
    @ConditionalOnMissingBean
    Tracer tracer(OpenTelemetry openTelemetry, ReflexOtelMetricsProperties properties) {
        return openTelemetry.getTracer(properties.getInstrumentationScopeName());
    }

    @Bean
    @ConditionalOnMissingBean
    OtelInstrumentRegistry otelInstrumentRegistry(Meter meter) {
        return new OtelInstrumentRegistry(meter);
    }

    @Bean
    @ConditionalOnMissingBean
    ReflexMetricFactory reflexMetricFactory(
            ManualMetricConfigResolver manualMetricConfigResolver,
            OtelInstrumentRegistry otelInstrumentRegistry,
            AttributeValidator attributeValidator) {
        return new ReflexMetricFactory(manualMetricConfigResolver, otelInstrumentRegistry, attributeValidator);
    }
}
