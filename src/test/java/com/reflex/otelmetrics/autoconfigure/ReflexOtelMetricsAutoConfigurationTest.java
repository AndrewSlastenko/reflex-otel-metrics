package com.reflex.otelmetrics.autoconfigure;

import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.runtime.SeriesLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
}
