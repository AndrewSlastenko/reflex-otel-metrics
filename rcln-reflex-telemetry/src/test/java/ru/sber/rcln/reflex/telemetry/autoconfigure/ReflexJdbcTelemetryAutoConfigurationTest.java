package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricCollectorFactory;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricRuntimeRegistrar;
import ru.sber.rcln.reflex.telemetry.locking.LocalMetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.locking.ShedLockMetricLockManager;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import ru.sber.rcln.reflex.telemetry.runtime.MetricExecutionDispatcher;
import ru.sber.rcln.reflex.telemetry.runtime.MetricSchedulerRegistrar;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReflexJdbcTelemetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ReflexTelemetryAutoConfiguration.class,
                    ReflexJdbcTelemetryAutoConfiguration.class))
            .withBean("businessReplicaDataSource", DataSource.class, () -> mock(DataSource.class))
            .withBean(JdbcMetricSource.class, TestJdbcMetricSource::new)
            .withBean(OtelMetricPublisher.class, () -> mock(OtelMetricPublisher.class))
            .withPropertyValues(
                    "reflex.telemetry.metrics.definitions.documents-by-status.source=JDBC",
                    "reflex.telemetry.metrics.definitions.documents-by-status.kind=GAUGE",
                    "reflex.telemetry.metrics.definitions.documents-by-status.name=documents.current",
                    "reflex.telemetry.metrics.definitions.documents-by-status.data-source-ref=businessReplicaDataSource",
                    "reflex.telemetry.metrics.definitions.documents-by-status.schedule.fixed-delay=PT5M",
                    "reflex.telemetry.metrics.definitions.documents-by-status.schedule.initial-delay=PT5S");

    @Test
    void shouldConfigureJdbcRuntimeWhenJdbcMetricSourceIsPresent() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

        contextRunner
                .withBean("reflexTelemetryMetricScheduledExecutorService", ScheduledExecutorService.class, () -> executor)
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcMetricCollectorFactory.class);
                    assertThat(context).hasSingleBean(MetricSchedulerRegistrar.class);
                    assertThat(context).hasSingleBean(MetricExecutionDispatcher.class);
                    assertThat(context).hasSingleBean(JdbcMetricRuntimeRegistrar.class);
                    assertThat(context).hasBean("reflexTelemetryMetricWorkerExecutorService");
                    assertThat(context).hasSingleBean(MetricLockManager.class);
                    assertThat(context.getBean(MetricLockManager.class)).isInstanceOf(LocalMetricLockManager.class);

                    verify(executor).scheduleWithFixedDelay(
                            any(Runnable.class),
                            eq(5000L),
                            eq(300000L),
                            eq(TimeUnit.MILLISECONDS));
                });
    }

    @Test
    void shouldUseConfiguredJdbcSchedulerPoolSizeForWorkerExecutor() {
        contextRunner
                .withPropertyValues("reflex.telemetry.metrics.jdbc.scheduler.pool-size=1")
                .run(context -> {
                    ExecutorService executor = context.getBean(
                            "reflexTelemetryMetricWorkerExecutorService",
                            ExecutorService.class);
                    CountDownLatch firstStarted = new CountDownLatch(1);
                    CountDownLatch releaseFirst = new CountDownLatch(1);

                    executor.execute(() -> {
                        firstStarted.countDown();
                        await(releaseFirst);
                    });
                    assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();

                    assertThatThrownBy(() -> executor.execute(() -> { }))
                            .isInstanceOf(RejectedExecutionException.class);

                    releaseFirst.countDown();
                });
    }

    @Test
    void shouldFailWhenJdbcSchedulerPoolSizeIsInvalid() {
        contextRunner
                .withPropertyValues("reflex.telemetry.metrics.jdbc.scheduler.pool-size=0")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("reflex.telemetry.metrics.jdbc.scheduler.pool-size must be at least 1"));
    }

    @Test
    void shouldNotConfigureJdbcRuntimeWhenJdbcMetricSourceIsAbsent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ReflexTelemetryAutoConfiguration.class,
                        ReflexJdbcTelemetryAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcMetricCollectorFactory.class);
                    assertThat(context).doesNotHaveBean(MetricSchedulerRegistrar.class);
                    assertThat(context).doesNotHaveBean(JdbcMetricRuntimeRegistrar.class);
                });
    }

    @Test
    void shouldNotConfigureJdbcRuntimeWhenJdbcMetricsAreDisabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.metrics.jdbc.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcMetricCollectorFactory.class);
                    assertThat(context).doesNotHaveBean(MetricSchedulerRegistrar.class);
                    assertThat(context).doesNotHaveBean(JdbcMetricRuntimeRegistrar.class);
                });
    }

    @Test
    void shouldNotConfigureJdbcRuntimeWhenTelemetryIsDisabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcMetricCollectorFactory.class);
                    assertThat(context).doesNotHaveBean(MetricSchedulerRegistrar.class);
                    assertThat(context).doesNotHaveBean(JdbcMetricRuntimeRegistrar.class);
                });
    }

    @Test
    void shouldNotConfigureJdbcRuntimeWhenMetricsAreDisabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcMetricCollectorFactory.class);
                    assertThat(context).doesNotHaveBean(MetricSchedulerRegistrar.class);
                    assertThat(context).doesNotHaveBean(JdbcMetricRuntimeRegistrar.class);
                });
    }

    @Test
    void shouldBackOffWhenMetricLockManagerIsProvidedByApplication() {
        MetricLockManager lockManager = mock(MetricLockManager.class);

        contextRunner
                .withBean(MetricLockManager.class, () -> lockManager)
                .run(context -> assertThat(context.getBean(MetricLockManager.class)).isSameAs(lockManager));
    }

    @Test
    void shouldUseShedLockMetricLockManagerWhenSingleLockProviderIsAvailable() {
        LockProvider lockProvider = mock(LockProvider.class);

        contextRunner
                .withBean(LockProvider.class, () -> lockProvider)
                .run(context -> {
                    MetricLockManager lockManager = context.getBean(MetricLockManager.class);

                    assertThat(lockManager).isInstanceOf(ShedLockMetricLockManager.class);
                    assertThat(ReflectionTestUtils.getField(lockManager, "lockProvider")).isSameAs(lockProvider);
                });
    }

    @Test
    void shouldUseConfiguredLockProviderWhenMultipleLockProvidersAreAvailable() {
        LockProvider telemetryLockProvider = mock(LockProvider.class);
        LockProvider businessLockProvider = mock(LockProvider.class);

        contextRunner
                .withBean("telemetryLockProvider", LockProvider.class, () -> telemetryLockProvider)
                .withBean("businessLockProvider", LockProvider.class, () -> businessLockProvider)
                .withPropertyValues("reflex.telemetry.metrics.jdbc.lock-provider-ref=telemetryLockProvider")
                .run(context -> {
                    MetricLockManager lockManager = context.getBean(MetricLockManager.class);

                    assertThat(lockManager).isInstanceOf(ShedLockMetricLockManager.class);
                    assertThat(ReflectionTestUtils.getField(lockManager, "lockProvider")).isSameAs(telemetryLockProvider);
                });
    }

    @Test
    void shouldFailWhenMultipleLockProvidersAreAvailableWithoutExplicitSelection() {
        contextRunner
                .withBean("telemetryLockProvider", LockProvider.class, () -> mock(LockProvider.class))
                .withBean("businessLockProvider", LockProvider.class, () -> mock(LockProvider.class))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("Multiple LockProvider beans are available"));
    }

    @Test
    void shouldFailWhenDataSourceRefIsMissing() {
        contextRunnerWithoutDataSource()
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("Metric 'documents-by-status' references missing DataSource bean "
                                + "'businessReplicaDataSource'"));
    }

    @Test
    void shouldFailWhenDataSourceRefPointsToNonDataSourceBean() {
        contextRunnerWithoutDataSource()
                .withBean("businessReplicaDataSource", String.class, () -> "not-a-data-source")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("Metric 'documents-by-status' references bean "
                                + "'businessReplicaDataSource' that is not a DataSource"));
    }

    @Test
    void shouldNotConfigureJdbcRuntimeWhenSpringJdbcIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(JdbcTemplate.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcMetricCollectorFactory.class);
                    assertThat(context).doesNotHaveBean(MetricSchedulerRegistrar.class);
                    assertThat(context).doesNotHaveBean(JdbcMetricRuntimeRegistrar.class);
                });
    }

    @Test
    void shouldUseLocalMetricLockManagerWhenShedLockIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(LockProvider.class))
                .run(context -> assertThat(context.getBean(MetricLockManager.class))
                        .isInstanceOf(LocalMetricLockManager.class));
    }

    private static ApplicationContextRunner contextRunnerWithoutDataSource() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ReflexTelemetryAutoConfiguration.class,
                        ReflexJdbcTelemetryAutoConfiguration.class))
                .withBean(JdbcMetricSource.class, TestJdbcMetricSource::new)
                .withBean(OtelMetricPublisher.class, () -> mock(OtelMetricPublisher.class))
                .withPropertyValues(
                        "reflex.telemetry.metrics.definitions.documents-by-status.source=JDBC",
                        "reflex.telemetry.metrics.definitions.documents-by-status.kind=GAUGE",
                        "reflex.telemetry.metrics.definitions.documents-by-status.name=documents.current",
                        "reflex.telemetry.metrics.definitions.documents-by-status.data-source-ref=businessReplicaDataSource",
                        "reflex.telemetry.metrics.definitions.documents-by-status.schedule.fixed-delay=PT5M",
                        "reflex.telemetry.metrics.definitions.documents-by-status.schedule.initial-delay=PT5S");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Latch was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class TestJdbcMetricSource implements JdbcMetricSource {

        @Override
        public String metricId() {
            return "documents-by-status";
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
}
