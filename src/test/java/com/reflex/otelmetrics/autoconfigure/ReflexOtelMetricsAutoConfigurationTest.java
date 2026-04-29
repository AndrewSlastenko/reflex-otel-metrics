package com.reflex.otelmetrics.autoconfigure;

import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.runtime.SeriesLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
                });
    }

    @Test
    void shouldBackOffSdkBeansWhenOpenTelemetryIsProvidedByTheApplication() {
        OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        Meter meter = mock(Meter.class);
        when(openTelemetry.getMeter("com.reflex.otelmetrics")).thenReturn(meter);

        contextRunner
                .withBean(OpenTelemetry.class, () -> openTelemetry)
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(Meter.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetrySdk.class);
                    verify(openTelemetry).getMeter("com.reflex.otelmetrics");
                });
    }
}
