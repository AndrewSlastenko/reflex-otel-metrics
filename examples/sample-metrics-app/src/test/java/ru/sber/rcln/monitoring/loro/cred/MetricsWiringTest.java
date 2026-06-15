package ru.sber.rcln.monitoring.loro.cred;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.sber.rcln.monitoring.loro.cred.config.MetricsLockProperties;
import ru.sber.rcln.monitoring.loro.cred.support.TestSchemaSupport;
import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;

/**
 * Сквозная проверка wiring: бины метрик ↔ YAML, resolver, DataSource, ShedLock.
 * <p>
 * Добавление JDBC-метрики: класс {@code *MetricSource}, запись в {@code application-reflex.yml}
 * и отдельный {@code @JdbcTest} на SQL/сбор данных. Сюда правки не нужны, пока {@code metricId}
 * совпадает с ключом в YAML.
 */
@SpringBootTest
@ActiveProfiles("test")
class MetricsWiringTest {

    private static final String TELEMETRY_DATA_SOURCE_BEAN = "telemetryDataSource";

    @Autowired
    ApplicationContext context;

    @Autowired
    List<JdbcMetricSource> jdbcMetricSources;

    @Autowired
    MetricConfigResolver metricConfigResolver;

    @Autowired
    ReflexTelemetryProperties telemetryProperties;

    @Autowired
    @Qualifier(TELEMETRY_DATA_SOURCE_BEAN)
    DataSource telemetryDataSource;

    @Autowired
    LockProvider lockProvider;

    @Autowired
    MetricLockManager metricLockManager;

    @Autowired
    MetricsLockProperties metricsLockProperties;

    @BeforeEach
    void ensureSchema() {
        TestSchemaSupport.ensureSchema(telemetryDataSource, metricsLockProperties);
    }

    @Test
    void jdbcMetricBeans_matchEnabledJdbcDefinitionsInYaml() {
        Set<String> expected = enabledJdbcMetricIdsFromYaml();
        Set<String> actual = jdbcMetricSources.stream()
                .map(JdbcMetricSource::metricId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void everyJdbcMetric_resolvesAndPointsToExistingDataSource() {
        assertThat(jdbcMetricSources).isNotEmpty();

        for (JdbcMetricSource source : jdbcMetricSources) {
            ResolvedMetricConfig resolved = metricConfigResolver.resolve(source);

            assertThat(resolved.dataSourceRef()).isNotBlank();
            assertThat(resolved.querySchema()).isNotBlank();
            assertThat(context.containsBean(resolved.dataSourceRef())).isTrue();

            DataSource dataSource = context.getBean(resolved.dataSourceRef(), DataSource.class);
            assertThat(dataSource).isNotNull();
            assertThat(source.queryDefinition().sql()).contains(resolved.querySchema() + ".");
        }
    }

    @Test
    void metricAndLockDataSources_areDistinctBeans() {
        Set<String> dataSourceBeanNames = jdbcMetricSources.stream()
                .map(source -> metricConfigResolver.resolve(source).dataSourceRef())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        dataSourceBeanNames.add(TELEMETRY_DATA_SOURCE_BEAN);

        List<DataSource> pools = dataSourceBeanNames.stream()
                .map(name -> context.getBean(name, DataSource.class))
                .toList();

        assertThat(pools).doesNotHaveDuplicates();
    }

    @Test
    void shedLockInfrastructure_isWired() {
        assertThat(lockProvider).isNotNull();
        assertThat(metricLockManager).isNotNull();

        assertThatCode(() -> new JdbcTemplate(telemetryDataSource)
                .queryForObject(
                        "SELECT COUNT(*) FROM " + metricsLockProperties.tableName(), Integer.class))
                .doesNotThrowAnyException();
    }

    private Set<String> enabledJdbcMetricIdsFromYaml() {
        return telemetryProperties.getMetrics().getDefinitions().entrySet().stream()
                .filter(entry -> entry.getValue().getSource()
                        == ReflexTelemetryProperties.MetricSourceType.JDBC)
                .filter(entry -> !Boolean.FALSE.equals(entry.getValue().getEnabled()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
