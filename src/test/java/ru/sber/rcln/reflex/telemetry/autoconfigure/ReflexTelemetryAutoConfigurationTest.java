package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinition;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ManualMetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.manual.AttributeValidator;
import ru.sber.rcln.reflex.telemetry.manual.ReflexMetricFactory;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import ru.sber.rcln.reflex.telemetry.tracing.NoopTraceOperations;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReflexTelemetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReflexTelemetryAutoConfiguration.class));

    @Test
    void shouldCreateCoreBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReflexTelemetryProperties.class);
                    assertThat(context).hasSingleBean(SeriesLimiter.class);
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(OpenTelemetrySdk.class);
                    assertThat(context).hasSingleBean(Tracer.class);
                });
    }

    @Test
    void shouldBackOffSdkBeansWhenOpenTelemetryIsProvidedByTheApplication() {
        OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        Meter meter = mock(Meter.class);
        Tracer tracer = mock(Tracer.class);
        when(openTelemetry.getMeter("custom.scope")).thenReturn(meter);
        when(openTelemetry.getTracer("custom.scope")).thenReturn(tracer);

        contextRunner
                .withPropertyValues("reflex.telemetry.instrumentation-scope-name=custom.scope")
                .withBean(OpenTelemetry.class, () -> openTelemetry)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(Meter.class);
                    assertThat(context).hasSingleBean(Tracer.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetrySdk.class);
                    verify(openTelemetry).getMeter("custom.scope");
                    verify(openTelemetry).getTracer("custom.scope");
                });
    }

    @Test
    void shouldBindStarterPropertiesAndSourceOverrides() {
        contextRunner
                .withBean(JdbcMetricSource.class, TestJdbcMetricSource::new)
                .withPropertyValues(
                        "reflex.telemetry.metrics.metric-prefix=ci054147",
                        "reflex.telemetry.instrumentation-scope-name=com.example.metrics",
                        "reflex.telemetry.service-name=contracts-api",
                        "reflex.telemetry.otlp.export-interval=PT1M",
                        "reflex.telemetry.metrics.sources.documents-by-status.suffix=documents.current")
                .run(context -> {
                    ReflexTelemetryProperties properties = context.getBean(ReflexTelemetryProperties.class);
                    MetricConfigResolver resolver = context.getBean(MetricConfigResolver.class);
                    JdbcMetricSource source = context.getBean(JdbcMetricSource.class);
                    ResolvedMetricConfig resolved = resolver.resolve(source);

                    assertThat(properties.getMetrics().getMetricPrefix()).isEqualTo("ci054147");
                    assertThat(properties.getInstrumentationScopeName()).isEqualTo("com.example.metrics");
                    assertThat(properties.getServiceName()).isEqualTo("contracts-api");
                    assertThat(properties.getOtlp().getExportInterval()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.getMetrics().getSources())
                            .containsKey("documents-by-status");
                    assertThat(properties.getMetrics().getSources().get("documents-by-status").getSuffix())
                            .isEqualTo("documents.current");
                    assertThat(resolved.suffix()).isEqualTo("documents.current");
                    assertThat(resolved.fullMetricName()).isEqualTo("ci054147.documents.current");
                });
    }

    @Test
    void shouldApplyConfiguredServiceNameToStarterSdkResources() {
        contextRunner
                .withPropertyValues("reflex.telemetry.service-name=contracts-api")
                .run(context -> {
                    assertThat(serviceName(context.getBean(SdkTracerProvider.class))).isEqualTo("contracts-api");
                    assertThat(serviceName(context.getBean(SdkMeterProvider.class))).isEqualTo("contracts-api");
                });
    }

    @Test
    void shouldKeepDefaultServiceNameResourceWhenConfiguredServiceNameIsBlank() {
        contextRunner
                .withPropertyValues("reflex.telemetry.service-name=  ")
                .run(context -> {
                    assertThat(serviceName(context.getBean(SdkTracerProvider.class))).isEqualTo(defaultServiceName());
                    assertThat(serviceName(context.getBean(SdkMeterProvider.class))).isEqualTo(defaultServiceName());
                });
    }

    @Test
    void shouldCreateManualMetricFactory() {
        contextRunner
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .run(context -> {
                    assertThat(context).hasSingleBean(ManualMetricConfigResolver.class);
                    assertThat(context).hasSingleBean(AttributeValidator.class);
                    assertThat(context).hasSingleBean(ReflexMetricFactory.class);
                });
    }

    @Test
    void shouldCreateTraceOperationsWhenEnabled() {
        contextRunner
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .run(context -> assertThat(context).hasSingleBean(TraceOperations.class));
    }

    @Test
    void shouldCreateNoopTraceOperationsWhenTracesAreDisabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.traces.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceOperations.class);
                    assertThat(context.getBean(TraceOperations.class)).isInstanceOf(NoopTraceOperations.class);
                    assertThat(context).doesNotHaveBean(OtlpGrpcSpanExporter.class);
                    assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
                    assertThat(context).doesNotHaveBean(Tracer.class);
                });
    }

    @Test
    void shouldCaptureW3cTraceparentWithAutoConfiguredTraceOperations() {
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        contextRunner
                .withBean(OpenTelemetry.class, () -> openTelemetry)
                .run(context -> {
                    TraceOperations traces = context.getBean(TraceOperations.class);

                    TraceCarrier carrier = traces.inSpan(
                            new SpanSpec("test.span", TraceCarrier.empty(), Map.of()),
                            traces::captureCurrent);

                    assertThat(carrier.traceparent())
                            .isNotBlank()
                            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
                });
    }

    @Test
    void shouldConfigureW3cPropagatorOnStarterOpenTelemetry() {
        contextRunner
                .run(context -> assertThat(context.getBean(OpenTelemetry.class)
                        .getPropagators()
                        .getTextMapPropagator()
                        .fields())
                        .contains("traceparent", "tracestate"));
    }

    @Test
    void shouldBackOffWhenTraceOperationsProvidedByApplication() {
        TraceOperations custom = mock(TraceOperations.class);

        contextRunner
                .withBean(TraceOperations.class, () -> custom)
                .run(context -> assertThat(context.getBean(TraceOperations.class)).isSameAs(custom));
    }

    @Test
    void shouldAllowMultipleManualCounterMetricBeansWithNamesAndQualifiers() {
        contextRunner
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .withUserConfiguration(ManualCounterMetricConfiguration.class)
                .run(context -> {
                    assertThat(context).hasBean("ordersCreatedMetric");
                    assertThat(context).hasBean("ordersFailedMetric");
                    assertThat(context.getBean("ordersCreatedMetric", CounterMetric.class))
                            .isNotSameAs(context.getBean("ordersFailedMetric", CounterMetric.class));
                    assertThat(context.getBeansOfType(CounterMetric.class))
                            .containsOnlyKeys("ordersCreatedMetric", "ordersFailedMetric");
                    assertThat(context.getBean(ManualCounterMetricConsumer.class).ordersCreatedMetric())
                            .isSameAs(context.getBean("ordersCreatedMetric", CounterMetric.class));
                    assertThat(context.getBean(ManualCounterMetricConsumer.class).ordersFailedMetric())
                            .isSameAs(context.getBean("ordersFailedMetric", CounterMetric.class));
                });
    }

    @Test
    void shouldKeepManualMetricBeansAvailableWhenMetricsAreDisabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReflexMetricFactory.class);
                    assertThat(context).doesNotHaveBean(OtlpGrpcMetricExporter.class);
                    assertThat(context).doesNotHaveBean(SdkMeterProvider.class);
                    assertThat(context).doesNotHaveBean(Meter.class);
                    assertThat(context).doesNotHaveBean(OtelInstrumentRegistry.class);
                });
    }

    @Test
    void shouldLetSpringLibraryUseNoopMetricsWhenApplicationTelemetryIsAbsent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TestLibraryTelemetryAutoConfiguration.class,
                        TestLibraryFallbackAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TestLibraryMetrics.class);
                    assertThat(context.getBean(TestLibraryMetrics.class)).isInstanceOf(NoopTestLibraryMetrics.class);
                    assertThat(context).doesNotHaveBean(ReflexMetricFactory.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetry.class);
                });
    }

    @Test
    void shouldLetSpringLibraryBindDomainMetricsToApplicationTelemetryRuntime() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ReflexTelemetryAutoConfiguration.class,
                        TestLibraryTelemetryAutoConfiguration.class,
                        TestLibraryFallbackAutoConfiguration.class))
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .run(context -> {
                    assertThat(context).hasSingleBean(ReflexMetricFactory.class);
                    assertThat(context).hasSingleBean(TestLibraryMetrics.class);
                    assertThat(context.getBean(TestLibraryMetrics.class))
                            .isInstanceOf(ReflexTestLibraryMetrics.class);
                    assertThat(context.getBeansOfType(OpenTelemetry.class)).hasSize(1);

                    context.getBean(TestLibraryService.class).process("sync");
                });
    }

    private static final class TestJdbcMetricSource implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "documents-by-status";
        }

        @Override
        public MetricDefinitionDefaults defaults() {
            return new MetricDefinitionDefaults(
                    "documents.by-status",
                    MetricKind.UP_DOWN_COUNTER,
                    "business",
                    "businessReplicaDataSource",
                    new MetricScheduleDefaults(
                            MetricScheduleDefaults.Mode.FIXED_DELAY,
                            Duration.ofMinutes(5),
                            null,
                            Duration.ofSeconds(30)),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(2),
                    Duration.ofSeconds(10),
                    500,
                    SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
        }

        @Override
        public QueryDefinition queryDefinition() {
            return new QueryDefinition("select 1");
        }

        @Override
        public RowMapper<MetricPoint> rowMapper() {
            return (rs, rowNum) -> new MetricPoint(1L, Map.of());
        }
    }

    private static String serviceName(SdkTracerProvider provider) {
        Object sharedState = ReflectionTestUtils.getField(provider, "sharedState");
        Resource resource = (Resource) ReflectionTestUtils.getField(sharedState, "resource");
        return serviceName(resource);
    }

    private static String serviceName(SdkMeterProvider provider) {
        Object sharedState = ReflectionTestUtils.getField(provider, "sharedState");
        Resource resource = ReflectionTestUtils.invokeMethod(sharedState, "getResource");
        return serviceName(resource);
    }

    private static String serviceName(Resource resource) {
        return resource.getAttribute(AttributeKey.stringKey("service.name"));
    }

    private static String defaultServiceName() {
        return serviceName(Resource.getDefault());
    }

    @Configuration(proxyBeanMethods = false)
    static class ManualCounterMetricConfiguration {

        @Bean
        CounterMetric ordersCreatedMetric(ReflexMetricFactory metricFactory) {
            return metricFactory.counter(
                    "orders-created",
                    MetricDefinition.of("orders.created").description("Orders created").unit("1").build());
        }

        @Bean
        CounterMetric ordersFailedMetric(ReflexMetricFactory metricFactory) {
            return metricFactory.counter(
                    "orders-failed",
                    MetricDefinition.of("orders.failed").description("Orders failed").unit("1").build());
        }

        @Bean
        ManualCounterMetricConsumer manualCounterMetricConsumer(
                @Qualifier("ordersCreatedMetric") CounterMetric ordersCreatedMetric,
                @Qualifier("ordersFailedMetric") CounterMetric ordersFailedMetric) {
            return new ManualCounterMetricConsumer(ordersCreatedMetric, ordersFailedMetric);
        }
    }

    private record ManualCounterMetricConsumer(
            CounterMetric ordersCreatedMetric,
            CounterMetric ordersFailedMetric) {
    }

    interface TestLibraryMetrics {

        void operationStarted(String type);
    }

    private static final class NoopTestLibraryMetrics implements TestLibraryMetrics {

        @Override
        public void operationStarted(String type) {
        }
    }

    private static final class ReflexTestLibraryMetrics implements TestLibraryMetrics {

        private final CounterMetric operationStarted;

        private ReflexTestLibraryMetrics(ReflexMetricFactory metricFactory) {
            this.operationStarted = metricFactory.counter(
                    "test-library-operation-started",
                    MetricDefinition.of("test.library.operation.started")
                            .scope("test-library")
                            .description("Started test library operations")
                            .unit("1")
                            .attributes(AttributesSchema.builder()
                                    .required("type")
                                    .build())
                            .build());
        }

        @Override
        public void operationStarted(String type) {
            operationStarted.increment(Map.of("type", type));
        }
    }

    private record TestLibraryService(TestLibraryMetrics metrics) {

        void process(String type) {
            metrics.operationStarted(type);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ReflexMetricFactory.class)
    @ConditionalOnBean(ReflexMetricFactory.class)
    static class TestLibraryTelemetryAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TestLibraryMetrics testLibraryMetrics(ReflexMetricFactory metricFactory) {
            return new ReflexTestLibraryMetrics(metricFactory);
        }

        @Bean
        TestLibraryService testLibraryService(TestLibraryMetrics metrics) {
            return new TestLibraryService(metrics);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(TestLibraryTelemetryAutoConfiguration.class)
    static class TestLibraryFallbackAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TestLibraryMetrics testLibraryMetrics() {
            return new NoopTestLibraryMetrics();
        }

        @Bean
        @ConditionalOnMissingBean
        TestLibraryService testLibraryService(TestLibraryMetrics metrics) {
            return new TestLibraryService(metrics);
        }
    }
}
