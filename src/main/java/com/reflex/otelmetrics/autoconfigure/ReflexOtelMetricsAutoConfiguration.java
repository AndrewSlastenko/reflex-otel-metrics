package com.reflex.otelmetrics.autoconfigure;

import com.reflex.otelmetrics.config.MetricConfigResolver;
import com.reflex.otelmetrics.config.MetricConfigValidator;
import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.internal.LoggingSupport;
import com.reflex.otelmetrics.otel.OtelInstrumentRegistry;
import com.reflex.otelmetrics.runtime.OverflowAggregationStrategy;
import com.reflex.otelmetrics.runtime.SeriesLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
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
    MetricConfigValidator metricConfigValidator() {
        return new MetricConfigValidator();
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
    @ConditionalOnMissingBean
    OtlpGrpcMetricExporter otlpGrpcMetricExporter(ReflexOtelMetricsProperties properties) {
        return OtlpGrpcMetricExporter.builder()
                .setEndpoint(properties.getOtlp().getMetricsEndpoint())
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    SdkMeterProvider sdkMeterProvider(OtlpGrpcMetricExporter exporter) {
        return SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(exporter).build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenTelemetrySdk openTelemetrySdk(SdkMeterProvider sdkMeterProvider) {
        return OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("com.reflex.otelmetrics");
    }

    @Bean
    @ConditionalOnMissingBean
    OtelInstrumentRegistry otelInstrumentRegistry(Meter meter) {
        return new OtelInstrumentRegistry(meter);
    }
}
