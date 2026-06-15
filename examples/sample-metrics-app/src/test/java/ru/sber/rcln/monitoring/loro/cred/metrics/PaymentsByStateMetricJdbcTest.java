package ru.sber.rcln.monitoring.loro.cred.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sber.rcln.monitoring.loro.cred.config.JdbcSliceTelemetryConfig;
import ru.sber.rcln.monitoring.loro.cred.config.MetricsDataSourceConfig;
import ru.sber.rcln.monitoring.loro.cred.support.TestSchemaSupport;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricCollector;

@JdbcTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    MetricsDataSourceConfig.class,
    PaymentsByStateMetricSource.class,
    JdbcSliceTelemetryConfig.class
})
class PaymentsByStateMetricJdbcTest {

    @Autowired
    @Qualifier("workflowMetricsDataSource")
    DataSource workflowDataSource;

    @Autowired
    PaymentsByStateMetricSource source;

    @BeforeEach
    void seedPayments() {
        TestSchemaSupport.ensureSchema(workflowDataSource);
        JdbcTemplate jdbc = new JdbcTemplate(workflowDataSource);
        jdbc.update("DELETE FROM workflow.payment_view");
        jdbc.update("INSERT INTO workflow.payment_view (payment_state) VALUES ('NEW')");
        jdbc.update("INSERT INTO workflow.payment_view (payment_state) VALUES ('NEW')");
        jdbc.update("INSERT INTO workflow.payment_view (payment_state) VALUES ('PAID')");
    }

    @Test
    void queryDefinition_usesSchemaFromYaml() {
        assertThat(source.queryDefinition().sql()).contains("workflow.payment_view");
    }

    @Test
    void collect_readsFromWorkflowDataSource() {
        JdbcMetricCollector collector = new JdbcMetricCollector(new JdbcTemplate(workflowDataSource));

        List<MetricPoint> points = collector.collect(source.queryDefinition(), source.rowMapper());

        assertThat(points).containsExactlyInAnyOrder(
                new MetricPoint(2L, Map.of("state", "NEW")),
                new MetricPoint(1L, Map.of("state", "PAID")));
    }
}
