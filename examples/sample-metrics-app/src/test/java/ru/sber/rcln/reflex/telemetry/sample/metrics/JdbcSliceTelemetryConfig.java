package ru.sber.rcln.reflex.telemetry.sample.metrics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricQuerySettings;

/**
 * Provides {@link JdbcMetricQuerySettings} for {@code @JdbcTest} slices using the real
 * {@code reflex.telemetry.*} configuration from {@code application-reflex.yml}, so {@code query.schema}
 * is not duplicated in test code.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReflexTelemetryProperties.class)
class JdbcSliceTelemetryConfig {

    @Bean
    MetricConfigResolver metricConfigResolver(ReflexTelemetryProperties properties) {
        return new MetricConfigResolver(properties);
    }

    @Bean
    JdbcMetricQuerySettings jdbcMetricQuerySettings(MetricConfigResolver resolver) {
        return new JdbcMetricQuerySettings(resolver);
    }
}
