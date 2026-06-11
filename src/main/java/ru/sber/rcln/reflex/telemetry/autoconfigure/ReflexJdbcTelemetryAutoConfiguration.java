package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.internal.NoopInternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricCollectorFactory;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricRuntimeRegistrar;
import ru.sber.rcln.reflex.telemetry.locking.LocalMetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.ShedLockMetricLockManager;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import ru.sber.rcln.reflex.telemetry.runtime.MetricSchedulerRegistrar;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.sql.DataSource;

@AutoConfiguration(after = ReflexTelemetryAutoConfiguration.class)
@ConditionalOnClass({JdbcTemplate.class, DataSource.class})
@ConditionalOnBean(JdbcMetricSource.class)
@ConditionalOnProperty(prefix = "reflex.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "reflex.telemetry.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "reflex.telemetry.metrics.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReflexJdbcTelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JdbcMetricCollectorFactory jdbcMetricCollectorFactory() {
        return new JdbcMetricCollectorFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    OtelMetricPublisher otelMetricPublisher(OtelInstrumentRegistry registry) {
        return new OtelMetricPublisher(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    InternalTelemetryRecorder internalTelemetryRecorder() {
        return new NoopInternalTelemetryRecorder();
    }

    @Bean(name = "reflexTelemetryMetricScheduledExecutorService", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "reflexTelemetryMetricScheduledExecutorService", value = MetricSchedulerRegistrar.class)
    ScheduledExecutorService reflexTelemetryMetricScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor(new ReflexTelemetryThreadFactory());
    }

    @Bean
    @ConditionalOnMissingBean
    MetricSchedulerRegistrar metricSchedulerRegistrar(
            @Qualifier("reflexTelemetryMetricScheduledExecutorService")
            ScheduledExecutorService scheduledExecutorService) {
        return new MetricSchedulerRegistrar(scheduledExecutorService);
    }

    @Bean
    @ConditionalOnMissingBean
    JdbcMetricRuntimeRegistrar jdbcMetricRuntimeRegistrar(
            List<JdbcMetricSource> sources,
            BeanFactory beanFactory,
            MetricConfigResolver configResolver,
            JdbcMetricCollectorFactory collectorFactory,
            MetricLockManager lockManager,
            OtelMetricPublisher publisher,
            InternalTelemetryRecorder telemetryRecorder,
            SeriesLimiter seriesLimiter,
            MetricSchedulerRegistrar schedulerRegistrar) {
        return new JdbcMetricRuntimeRegistrar(
                sources,
                beanFactory,
                configResolver,
                collectorFactory,
                lockManager,
                publisher,
                telemetryRecorder,
                seriesLimiter,
                schedulerRegistrar);
    }

    private static final class ReflexTelemetryThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "reflex-telemetry-metrics");
            thread.setDaemon(true);
            return thread;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(LockProvider.class)
    static class ShedLockAvailableConfiguration {

        @Bean
        @ConditionalOnMissingBean
        MetricLockManager metricLockManager(
                ObjectProvider<LockProvider> lockProviders,
                ConfigurableBeanFactory beanFactory,
                ReflexTelemetryProperties properties) {
            String lockProviderRef = properties.getMetrics().getJdbc().getLockProviderRef();
            if (hasText(lockProviderRef)) {
                return new ShedLockMetricLockManager(beanFactory.getBean(lockProviderRef, LockProvider.class));
            }

            List<LockProvider> available = lockProviders.stream().toList();
            if (available.isEmpty()) {
                return new LocalMetricLockManager();
            }
            if (available.size() == 1) {
                return new ShedLockMetricLockManager(available.get(0));
            }

            throw new IllegalStateException("Multiple LockProvider beans are available; set "
                    + "reflex.telemetry.metrics.jdbc.lock-provider-ref or define a MetricLockManager bean");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("net.javacrumbs.shedlock.core.LockProvider")
    static class ShedLockMissingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        MetricLockManager metricLockManager() {
            return new LocalMetricLockManager();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
