package ru.sber.rcln.monitoring.loro.cred.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.ShedLockMetricLockManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MetricsLockProperties.class)
public class MetricsLockConfig {

    @Bean
    LockProvider lockProvider(
            @Qualifier("telemetryDataSource") DataSource dataSource,
            MetricsLockProperties properties) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName(properties.tableName())
                        .usingDbTime()
                        .build());
    }

    @Bean
    MetricLockManager metricLockManager(LockProvider lockProvider) {
        return new ShedLockMetricLockManager(lockProvider);
    }
}
