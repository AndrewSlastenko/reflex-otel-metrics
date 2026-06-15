package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcMetricCollectorTest {

    @Test
    void shouldCollectMetricPointsFromJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RowMapper<MetricPoint> rowMapper = (rs, rowNum) -> new MetricPoint(10L, Map.of("status", "created"));
        when(jdbcTemplate.query("select status, total from v_documents", rowMapper))
                .thenReturn(List.of(new MetricPoint(10L, Map.of("status", "created"))));

        JdbcMetricCollector collector = new JdbcMetricCollector(jdbcTemplate);

        List<MetricPoint> points = collector.collect(new QueryDefinition("select status, total from v_documents"), rowMapper);

        assertThat(points).singleElement().extracting(MetricPoint::value).isEqualTo(10L);
    }

    @Test
    void shouldCollectHistogramPointsFromJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RowMapper<MetricPoint> rowMapper = (rs, rowNum) -> MetricPoint.histogram(10.5, Map.of("bucket", "p95"));
        when(jdbcTemplate.query("select latency from v_documents", rowMapper))
                .thenReturn(List.of(MetricPoint.histogram(10.5, Map.of("bucket", "p95"))));

        JdbcMetricCollector collector = new JdbcMetricCollector(jdbcTemplate);

        List<MetricPoint> points = collector.collect(new QueryDefinition("select latency from v_documents"), rowMapper);

        assertThat(points).singleElement().extracting(MetricPoint::asDoubleValue).isEqualTo(10.5d);
    }
}
