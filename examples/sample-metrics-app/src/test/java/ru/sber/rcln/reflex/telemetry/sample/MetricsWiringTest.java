package ru.sber.rcln.reflex.telemetry.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.locking.MetricLockManager;
import ru.sber.rcln.reflex.telemetry.sample.metrics.DocumentsByStatusMetricSource;
import ru.sber.rcln.reflex.telemetry.sample.metrics.PaymentsByStateMetricSource;
import ru.sber.rcln.reflex.telemetry.sample.support.MetricsItSchemaSupport;

@SpringBootTest
@ActiveProfiles("metrics-it")
class MetricsWiringTest {

    @Autowired
    @Qualifier("documentsMetricsDataSource")
    DataSource documentsDataSource;

    @Autowired
    @Qualifier("paymentsMetricsDataSource")
    DataSource paymentsDataSource;

    @Autowired
    @Qualifier("telemetryLockDataSource")
    DataSource telemetryLockDataSource;

    @Autowired
    List<JdbcMetricSource> jdbcMetricSources;

    @Autowired
    MetricConfigResolver metricConfigResolver;

    @Autowired
    DocumentsByStatusMetricSource documentsByStatusMetricSource;

    @Autowired
    PaymentsByStateMetricSource paymentsByStateMetricSource;

    @Autowired
    LockProvider lockProvider;

    @Autowired
    MetricLockManager metricLockManager;

    @BeforeEach
    void ensureSchema() {
        MetricsItSchemaSupport.ensureSchema(telemetryLockDataSource);
    }

    @Test
    void dataSourceBeans_areRegisteredSeparately() {
        assertThat(documentsDataSource).isNotNull();
        assertThat(paymentsDataSource).isNotNull();
        assertThat(telemetryLockDataSource).isNotNull();
        assertThat(documentsDataSource).isNotSameAs(paymentsDataSource);
        assertThat(telemetryLockDataSource).isNotSameAs(documentsDataSource);
    }

    @Test
    void jdbcMetrics_resolveToExpectedDataSourceRefsAndQuerySchemas() {
        assertThat(jdbcMetricSources).hasSize(2);

        ResolvedMetricConfig documents = metricConfigResolver.resolve(findSource("documents-by-status"));
        ResolvedMetricConfig payments = metricConfigResolver.resolve(findSource("payments-by-state"));

        assertThat(documents.dataSourceRef()).isEqualTo("documentsMetricsDataSource");
        assertThat(payments.dataSourceRef()).isEqualTo("paymentsMetricsDataSource");
        assertThat(documents.querySchema()).isEqualTo("documents");
        assertThat(payments.querySchema()).isEqualTo("payments");
    }

    @Test
    void jdbcMetricSources_buildSqlFromYamlSchema() {
        assertThat(documentsByStatusMetricSource.queryDefinition().sql())
                .contains("documents.transaction_view");
        assertThat(paymentsByStateMetricSource.queryDefinition().sql())
                .contains("payments.payment_view");
    }

    @Test
    void shedLockInfrastructure_isWired() {
        assertThat(lockProvider).isNotNull();
        assertThat(metricLockManager).isNotNull();

        assertThatCode(() -> new JdbcTemplate(telemetryLockDataSource)
                .queryForObject("SELECT COUNT(*) FROM telemetry.shedlock", Integer.class))
                .doesNotThrowAnyException();
    }

    private JdbcMetricSource findSource(String metricId) {
        return jdbcMetricSources.stream()
                .filter(source -> source.metricId().equals(metricId))
                .findFirst()
                .orElseThrow();
    }
}
