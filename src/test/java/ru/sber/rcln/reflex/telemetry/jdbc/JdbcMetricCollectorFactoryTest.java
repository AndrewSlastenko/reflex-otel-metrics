package ru.sber.rcln.reflex.telemetry.jdbc;

import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JdbcMetricCollectorFactoryTest {

    private final JdbcMetricCollectorFactory factory = new JdbcMetricCollectorFactory();

    @Test
    void shouldApplyConfiguredQueryTimeout() {
        JdbcMetricCollector collector = factory.create(mock(DataSource.class), Duration.ofSeconds(45));

        assertThat(jdbcTemplate(collector).getQueryTimeout()).isEqualTo(45);
    }

    @Test
    void shouldRoundQueryTimeoutUpToSeconds() {
        JdbcMetricCollector collector = factory.create(mock(DataSource.class), Duration.ofMillis(1500));

        assertThat(jdbcTemplate(collector).getQueryTimeout()).isEqualTo(2);
    }

    @Test
    void shouldRejectNonPositiveQueryTimeout() {
        DataSource dataSource = mock(DataSource.class);

        assertThatThrownBy(() -> factory.create(dataSource, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query timeout must be positive");
    }

    private static JdbcTemplate jdbcTemplate(JdbcMetricCollector collector) {
        return (JdbcTemplate) ReflectionTestUtils.getField(collector, "jdbcTemplate");
    }
}
