package ru.sber.rcln.reflex.telemetry.sample.metrics;

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
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.jdbc.JdbcMetricCollector;
import ru.sber.rcln.reflex.telemetry.sample.config.MetricsDataSourceConfig;
import ru.sber.rcln.reflex.telemetry.sample.support.MetricsItSchemaSupport;

@JdbcTest
@ActiveProfiles("metrics-it")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    MetricsDataSourceConfig.class,
    PaymentsByStateMetricSource.class,
    JdbcSliceTelemetryConfig.class
})
class PaymentsByStateMetricJdbcTest {

    @Autowired
    @Qualifier("paymentsMetricsDataSource")
    DataSource paymentsDataSource;

    @Autowired
    PaymentsByStateMetricSource source;

    @BeforeEach
    void seedPayments() {
        MetricsItSchemaSupport.ensureSchema(paymentsDataSource);
        JdbcTemplate jdbc = new JdbcTemplate(paymentsDataSource);
        jdbc.update("DELETE FROM payments.payment_view");
        jdbc.update("INSERT INTO payments.payment_view (payment_state) VALUES ('NEW')");
        jdbc.update("INSERT INTO payments.payment_view (payment_state) VALUES ('NEW')");
        jdbc.update("INSERT INTO payments.payment_view (payment_state) VALUES ('PAID')");
    }

    @Test
    void queryDefinition_usesSchemaFromTestSettings() {
        assertThat(source.queryDefinition().sql()).contains("payments.payment_view");
    }

    @Test
    void collect_readsFromPaymentsDataSource() {
        JdbcMetricCollector collector = new JdbcMetricCollector(new JdbcTemplate(paymentsDataSource));

        List<MetricPoint> points = collector.collect(source.queryDefinition(), source.rowMapper());

        assertThat(points).containsExactlyInAnyOrder(
                new MetricPoint(2L, Map.of("state", "NEW")),
                new MetricPoint(1L, Map.of("state", "PAID")));
    }
}
