package com.reflex.otelmetrics.autoconfigure;

import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.api.JdbcMetricSource;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricDefinitionDefaults;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricPoint;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.QueryDefinition;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.config.MetricConfigResolver;
import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.config.ManualMetricConfigResolver;
import com.reflex.otelmetrics.manual.AttributeValidator;
import com.reflex.otelmetrics.manual.ReflexMetricFactory;
import com.reflex.otelmetrics.runtime.SeriesLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReflexOtelMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReflexOtelMetricsAutoConfiguration.class));

    @Test
    void shouldCreateCoreBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("reflex.otel.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReflexOtelMetricsProperties.class);
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
                .withPropertyValues("reflex.otel.metrics.instrumentation-scope-name=custom.scope")
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
                        "reflex.otel.metrics.metric-prefix=ci054147",
                        "reflex.otel.metrics.instrumentation-scope-name=com.example.metrics",
                        "reflex.otel.metrics.otlp.export-interval=PT1M",
                        "reflex.otel.metrics.sources.documents-by-status.suffix=documents.current")
                .run(context -> {
                    ReflexOtelMetricsProperties properties = context.getBean(ReflexOtelMetricsProperties.class);
                    MetricConfigResolver resolver = context.getBean(MetricConfigResolver.class);
                    JdbcMetricSource source = context.getBean(JdbcMetricSource.class);
                    ResolvedMetricConfig resolved = resolver.resolve(source);

                    assertThat(properties.getMetricPrefix()).isEqualTo("ci054147");
                    assertThat(properties.getInstrumentationScopeName()).isEqualTo("com.example.metrics");
                    assertThat(properties.getOtlp().getExportInterval()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.getSources())
                            .containsKey("documents-by-status");
                    assertThat(properties.getSources().get("documents-by-status").getSuffix())
                            .isEqualTo("documents.current");
                    assertThat(resolved.suffix()).isEqualTo("documents.current");
                    assertThat(resolved.fullMetricName()).isEqualTo("ci054147.documents.current");
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
}
