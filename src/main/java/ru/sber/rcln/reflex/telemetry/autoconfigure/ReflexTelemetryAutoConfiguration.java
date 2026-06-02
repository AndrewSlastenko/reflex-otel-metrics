package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigValidator;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryNamingPolicy;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.internal.LoggingSupport;
import ru.sber.rcln.reflex.telemetry.manual.AttributeValidator;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import ru.sber.rcln.reflex.telemetry.runtime.OverflowAggregationStrategy;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import ru.sber.rcln.reflex.telemetry.tracing.DefaultTraceOperations;
import ru.sber.rcln.reflex.telemetry.tracing.NoopTraceOperations;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@EnableConfigurationProperties(ReflexTelemetryProperties.class)
public class ReflexTelemetryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ReflexTelemetryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    ReflexTelemetryNamingPolicy reflexTelemetryNamingPolicy(ReflexTelemetryProperties properties) {
        return new ReflexTelemetryNamingPolicy(properties.getService().getSystemCode());
    }

    @Bean
    @ConditionalOnMissingBean
    MetricConfigResolver metricConfigResolver(
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        return new MetricConfigResolver(properties, namingPolicy);
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
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OtlpGrpcMetricExporter.class, OpenTelemetry.class})
    OtlpGrpcMetricExporter otlpGrpcMetricExporter(ReflexTelemetryProperties properties) {
        log.info("Reflex telemetry OTLP metrics temporality preference: {}",
                properties.getMetrics().getTemporalityPreference());
        return OtlpGrpcMetricExporter.builder()
                .setEndpoint(endpoint(properties.getMetrics().getEndpoint(), properties))
                .setTimeout(properties.getOtlp().getExportTimeout())
                .setAggregationTemporalitySelector(metricsTemporalitySelector(properties))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.traces", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OpenTelemetry.class})
    OtlpGrpcSpanExporter otlpGrpcSpanExporter(ReflexTelemetryProperties properties) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint(properties.getTraces().getEndpoint(), properties))
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({SdkMeterProvider.class, OpenTelemetry.class})
    SdkMeterProvider sdkMeterProvider(
            OtlpGrpcMetricExporter exporter,
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
        applyServiceNameResource(builder, properties, namingPolicy);
        registerHistogramViews(builder, properties, namingPolicy);
        return builder.registerMetricReader(PeriodicMetricReader.builder(exporter)
                .setInterval(properties.getMetrics().getExportInterval())
                .build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.traces", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({SdkTracerProvider.class, OpenTelemetry.class})
    SdkTracerProvider sdkTracerProvider(
            OtlpGrpcSpanExporter exporter,
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        SdkTracerProviderBuilder builder = SdkTracerProvider.builder();
        applyServiceNameResource(builder, properties, namingPolicy);
        return builder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Conditional(SdkSignalProviderAvailableCondition.class)
    @ConditionalOnMissingBean({OpenTelemetrySdk.class, OpenTelemetry.class})
    OpenTelemetrySdk openTelemetrySdk(
            ObjectProvider<SdkMeterProvider> sdkMeterProvider,
            ObjectProvider<SdkTracerProvider> sdkTracerProvider) {
        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
        sdkMeterProvider.ifAvailable(builder::setMeterProvider);
        sdkTracerProvider.ifAvailable(builder::setTracerProvider);
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({OpenTelemetry.class, OpenTelemetrySdk.class})
    OpenTelemetry openTelemetry(OpenTelemetrySdk openTelemetrySdk) {
        return openTelemetrySdk;
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    Meter meter(OpenTelemetry openTelemetry, ReflexTelemetryProperties properties) {
        return openTelemetry.getMeter(properties.getService().getInstrumentationScopeName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.traces", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    Tracer tracer(OpenTelemetry openTelemetry, ReflexTelemetryProperties properties) {
        return openTelemetry.getTracer(properties.getService().getInstrumentationScopeName());
    }

    @Bean
    @ConditionalOnMissingBean
    TraceOperations traceOperations(
            ObjectProvider<Tracer> tracerProvider,
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ReflexTelemetryProperties properties) {
        if (!properties.isEnabled() || !properties.getTraces().isEnabled()) {
            return new NoopTraceOperations();
        }

        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return new NoopTraceOperations();
        }

        OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
        if (openTelemetry == null) {
            return new DefaultTraceOperations(tracer);
        }

        ContextPropagators propagators = openTelemetry.getPropagators();
        return new DefaultTraceOperations(tracer, propagators != null ? propagators : ContextPropagators.noop());
    }

    @Bean
    @ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "reflex.telemetry.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    OtelInstrumentRegistry otelInstrumentRegistry(Meter meter) {
        return new OtelInstrumentRegistry(meter);
    }

    @Bean
    @ConditionalOnMissingBean
    ReflexMetricFactory reflexMetricFactory(
            MetricConfigResolver metricConfigResolver,
            ObjectProvider<OtelInstrumentRegistry> otelInstrumentRegistry,
            AttributeValidator attributeValidator) {
        return new ReflexMetricFactory(
                metricConfigResolver,
                otelInstrumentRegistry::getIfAvailable,
                attributeValidator);
    }

    static class SdkSignalProviderAvailableCondition extends AnyNestedCondition {

        SdkSignalProviderAvailableCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(SdkMeterProvider.class)
        static class MeterProviderAvailable {
        }

        @ConditionalOnBean(SdkTracerProvider.class)
        static class TracerProviderAvailable {
        }
    }

    private static void applyServiceNameResource(
            SdkMeterProviderBuilder builder,
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        serviceNameResource(properties, namingPolicy).ifPresent(builder::addResource);
    }

    private static void applyServiceNameResource(
            SdkTracerProviderBuilder builder,
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        serviceNameResource(properties, namingPolicy).ifPresent(builder::addResource);
    }

    private static java.util.Optional<Resource> serviceNameResource(
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        String serviceName = namingPolicy.serviceName(properties.getService().getName());
        if (serviceName == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));
    }

    private static AggregationTemporalitySelector metricsTemporalitySelector(ReflexTelemetryProperties properties) {
        return switch (properties.getMetrics().getTemporalityPreference()) {
            case DELTA -> AggregationTemporalitySelector.deltaPreferred();
            case CUMULATIVE -> AggregationTemporalitySelector.alwaysCumulative();
            case LOW_MEMORY -> AggregationTemporalitySelector.lowMemory();
        };
    }

    private static String endpoint(String signalEndpoint, ReflexTelemetryProperties properties) {
        return hasText(signalEndpoint) ? signalEndpoint : properties.getOtlp().getEndpoint();
    }

    private static void registerHistogramViews(
            SdkMeterProviderBuilder builder,
            ReflexTelemetryProperties properties,
            ReflexTelemetryNamingPolicy namingPolicy) {
        properties.getMetrics().getDefinitions().values().stream()
                .filter(definition -> definition.getKind() == ru.sber.rcln.reflex.telemetry.api.MetricKind.HISTOGRAM)
                .filter(definition -> hasText(definition.getName()))
                .filter(definition -> definition.getHistogram() != null
                        && definition.getHistogram().getBuckets() != null
                        && !definition.getHistogram().getBuckets().isEmpty())
                .forEach(definition -> builder.registerView(
                        InstrumentSelector.builder()
                                .setName(namingPolicy.metricName(definition.getName()))
                                .build(),
                        View.builder()
                                .setAggregation(Aggregation.explicitBucketHistogram(
                                        definition.getHistogram().getBuckets()))
                                .build()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
