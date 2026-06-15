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
    DocumentsByStatusMetricSource.class,
    JdbcSliceTelemetryConfig.class
})
class DocumentsByStatusMetricJdbcTest {

    @Autowired
    @Qualifier("documentsMetricsDataSource")
    DataSource documentsDataSource;

    @Autowired
    DocumentsByStatusMetricSource source;

    @BeforeEach
    void seedDocuments() {
        MetricsItSchemaSupport.ensureSchema(documentsDataSource);
        JdbcTemplate jdbc = new JdbcTemplate(documentsDataSource);
        jdbc.update("DELETE FROM documents.transaction_view");
        jdbc.update("INSERT INTO documents.transaction_view (client_code, document_status) VALUES ('A', 'CREATED')");
        jdbc.update("INSERT INTO documents.transaction_view (client_code, document_status) VALUES ('A', 'CREATED')");
        jdbc.update("INSERT INTO documents.transaction_view (client_code, document_status) VALUES ('B', 'SENT')");
    }

    @Test
    void queryDefinition_usesSchemaFromTestSettings() {
        assertThat(source.queryDefinition().sql()).contains("documents.transaction_view");
    }

    @Test
    void collect_readsFromDocumentsDataSource() {
        JdbcMetricCollector collector = new JdbcMetricCollector(new JdbcTemplate(documentsDataSource));

        List<MetricPoint> points = collector.collect(source.queryDefinition(), source.rowMapper());

        assertThat(points).containsExactlyInAnyOrder(
                new MetricPoint(2L, Map.of("client", "A", "status", "CREATED")),
                new MetricPoint(1L, Map.of("client", "B", "status", "SENT")));
    }
}
