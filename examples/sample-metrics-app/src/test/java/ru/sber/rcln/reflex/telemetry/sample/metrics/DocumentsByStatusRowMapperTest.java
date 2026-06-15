package ru.sber.rcln.reflex.telemetry.sample.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;

class DocumentsByStatusRowMapperTest {

    private final DocumentsByStatusMetricSource source =
            new DocumentsByStatusMetricSource(MetricsJdbcQueryTestSupport.querySettings("documents-by-status", "documents"));

    @Test
    void queryDefinition_usesConfiguredSchema() {
        assertThat(source.queryDefinition().sql()).contains("documents.transaction_view");
    }

    @Test
    void rowMapper_mapsResultSetToMetricPoint() throws Exception {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getLong("cnt")).thenReturn(3L);
        Mockito.when(resultSet.getString("client_code")).thenReturn("A");
        Mockito.when(resultSet.getString("document_status")).thenReturn("CREATED");

        MetricPoint point = source.rowMapper().mapRow(resultSet, 0);

        assertThat(point.value()).isEqualTo(3L);
        assertThat(point.attributes()).isEqualTo(Map.of(
                "client", "A",
                "status", "CREATED"));
    }
}
