package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import ru.sber.rcln.reflex.telemetry.runtime.MetricExecutionDispatcher;
import ru.sber.rcln.reflex.telemetry.runtime.MetricExecutionTask;
import ru.sber.rcln.reflex.telemetry.runtime.MetricSchedulerRegistrar;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import javax.sql.DataSource;
import lombok.NonNull;

public class JdbcMetricRuntimeRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(JdbcMetricRuntimeRegistrar.class);

    private final List<JdbcMetricSource> sources;
    private final BeanFactory beanFactory;
    private final MetricConfigResolver configResolver;
    private final JdbcMetricCollectorFactory collectorFactory;
    private final MetricLockManager lockManager;
    private final OtelMetricPublisher publisher;
    private final InternalTelemetryRecorder telemetryRecorder;
    private final SeriesLimiter seriesLimiter;
    private final MetricSchedulerRegistrar schedulerRegistrar;
    private final MetricExecutionDispatcher dispatcher;

    public JdbcMetricRuntimeRegistrar(
            @NonNull List<JdbcMetricSource> sources,
            @NonNull BeanFactory beanFactory,
            @NonNull MetricConfigResolver configResolver,
            @NonNull JdbcMetricCollectorFactory collectorFactory,
            @NonNull MetricLockManager lockManager,
            @NonNull OtelMetricPublisher publisher,
            @NonNull InternalTelemetryRecorder telemetryRecorder,
            @NonNull SeriesLimiter seriesLimiter,
            @NonNull MetricSchedulerRegistrar schedulerRegistrar,
            @NonNull MetricExecutionDispatcher dispatcher) {
        this.sources = List.copyOf(sources);
        this.beanFactory = beanFactory;
        this.configResolver = configResolver;
        this.collectorFactory = collectorFactory;
        this.lockManager = lockManager;
        this.publisher = publisher;
        this.telemetryRecorder = telemetryRecorder;
        this.seriesLimiter = seriesLimiter;
        this.schedulerRegistrar = schedulerRegistrar;
        this.dispatcher = dispatcher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int enabled = 0;
        for (JdbcMetricSource source : sources) {
            if (register(source).enabled()) {
                enabled++;
            }
        }
        log.info("Reflex JDBC metric runtime initialized: sources={}, enabled={}, disabled={}, lockManager={}",
                sources.size(), enabled, sources.size() - enabled, lockManager.getClass().getSimpleName());
    }

    private ResolvedMetricConfig register(JdbcMetricSource source) {
        ResolvedMetricConfig config = configResolver.resolve(source);
        DataSource dataSource = dataSource(config);
        JdbcMetricCollector collector = collectorFactory.create(dataSource, config);
        JdbcMetricExecutionCoordinator coordinator = new JdbcMetricExecutionCoordinator(source, collector);
        MetricExecutionTask task = new MetricExecutionTask(
                coordinator,
                lockManager,
                publisher,
                telemetryRecorder,
                seriesLimiter,
                config);
        schedulerRegistrar.register(config, () -> dispatcher.dispatch(config, task::runOnce));
        log.debug("Metric {} registered for JDBC collection: name={}, enabled={}, kind={}, dataSource={}, "
                        + "schedule={}, timeout={}, maxSeries={}, overflowPolicy={}",
                config.metricId(), config.exportedMetricName(), config.enabled(), config.metricKind(),
                config.dataSourceRef(), config.schedule(), config.timeout(), config.maxSeries(), config.overflowPolicy());
        return config;
    }

    private DataSource dataSource(ResolvedMetricConfig config) {
        try {
            return beanFactory.getBean(config.dataSourceRef(), DataSource.class);
        } catch (NoSuchBeanDefinitionException exception) {
            throw new IllegalStateException("Metric '" + config.metricId()
                    + "' references missing DataSource bean '" + config.dataSourceRef() + "'", exception);
        } catch (BeanNotOfRequiredTypeException exception) {
            throw new IllegalStateException("Metric '" + config.metricId()
                    + "' references bean '" + config.dataSourceRef() + "' that is not a DataSource", exception);
        }
    }
}
