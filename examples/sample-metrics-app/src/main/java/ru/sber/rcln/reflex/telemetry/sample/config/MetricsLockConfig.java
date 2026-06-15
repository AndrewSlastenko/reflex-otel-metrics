package ru.sber.rcln.reflex.telemetry.sample.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.ShedLockMetricLockManager;

@Configuration(proxyBeanMethods = false)
public class MetricsLockConfig {

    @Bean
    LockProvider lockProvider(@Qualifier("telemetryLockDataSource") DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("telemetry.shedlock")
                        .usingDbTime()
                        .build());
    }

    @Bean
    MetricLockManager metricLockManager(LockProvider lockProvider) {
        return new ShedLockMetricLockManager(lockProvider);
    }
}
