package ru.sber.rcln.monitoring.loro.cred.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;

/**
 * Подключает {@link JdbcMetricQuerySettings} для {@code @JdbcTest}-слайсов из {@code application-reflex.yml},
 * чтобы {@code query.schema} не дублировать в тестовом коде.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReflexTelemetryProperties.class)
public class JdbcSliceTelemetryConfig {

    @Bean
    MetricConfigResolver metricConfigResolver(ReflexTelemetryProperties properties) {
        return new MetricConfigResolver(properties);
    }

    @Bean
    JdbcMetricQuerySettings jdbcMetricQuerySettings(MetricConfigResolver resolver) {
        return new JdbcMetricQuerySettings(resolver);
    }
}
