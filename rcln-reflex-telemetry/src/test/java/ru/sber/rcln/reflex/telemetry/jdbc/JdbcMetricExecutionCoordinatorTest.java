package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcMetricExecutionCoordinatorTest {

    @Test
    void collectsUsingSourceQueryDefinition() {
        JdbcMetricCollector collector = mock(JdbcMetricCollector.class);
        JdbcMetricSource source = mock(JdbcMetricSource.class);
        QueryDefinition query = new QueryDefinition("select 1");
        RowMapper<MetricPoint> rowMapper = (rs, rowNum) -> new MetricPoint(1L, Map.of());
        when(source.queryDefinition()).thenReturn(query);
        when(source.rowMapper()).thenReturn(rowMapper);
        when(collector.collect(query, rowMapper)).thenReturn(List.of(new MetricPoint(1L, Map.of())));

        JdbcMetricExecutionCoordinator coordinator = new JdbcMetricExecutionCoordinator(source, collector);

        List<MetricPoint> points = coordinator.collect();

        assertThat(points).hasSize(1);
        verify(source).queryDefinition();
        verify(collector).collect(query, rowMapper);
    }
}
