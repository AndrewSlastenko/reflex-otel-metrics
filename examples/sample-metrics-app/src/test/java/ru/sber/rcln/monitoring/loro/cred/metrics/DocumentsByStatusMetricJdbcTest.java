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
    DocumentsByStatusMetricSource.class,
    JdbcSliceTelemetryConfig.class
})
class DocumentsByStatusMetricJdbcTest {

    @Autowired
    @Qualifier("businessMetricsDataSource")
    DataSource businessDataSource;

    @Autowired
    DocumentsByStatusMetricSource source;

    @BeforeEach
    void seedDocuments() {
        TestSchemaSupport.ensureSchema(businessDataSource);
        JdbcTemplate jdbc = new JdbcTemplate(businessDataSource);
        jdbc.update("DELETE FROM business.transaction_view");
        jdbc.update("INSERT INTO business.transaction_view (client_code, document_status) VALUES ('A', 'CREATED')");
        jdbc.update("INSERT INTO business.transaction_view (client_code, document_status) VALUES ('A', 'CREATED')");
        jdbc.update("INSERT INTO business.transaction_view (client_code, document_status) VALUES ('B', 'SENT')");
    }

    @Test
    void queryDefinition_usesSchemaFromYaml() {
        assertThat(source.queryDefinition().sql()).contains("business.transaction_view");
    }

    @Test
    void collect_readsFromBusinessDataSource() {
        JdbcMetricCollector collector = new JdbcMetricCollector(new JdbcTemplate(businessDataSource));

        List<MetricPoint> points = collector.collect(source.queryDefinition(), source.rowMapper());

        assertThat(points).containsExactlyInAnyOrder(
                new MetricPoint(2L, Map.of("client", "A", "status", "CREATED")),
                new MetricPoint(1L, Map.of("client", "B", "status", "SENT")));
    }
}
