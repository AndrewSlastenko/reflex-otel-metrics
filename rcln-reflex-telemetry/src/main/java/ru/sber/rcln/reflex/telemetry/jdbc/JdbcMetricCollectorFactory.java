package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcMetricCollectorFactory {

    public JdbcMetricCollector create(DataSource dataSource) {
        return create(dataSource, (Duration) null);
    }

    public JdbcMetricCollector create(DataSource dataSource, ResolvedMetricConfig config) {
        return create(dataSource, config.timeout());
    }

    public JdbcMetricCollector create(DataSource dataSource, Duration timeout) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        if (timeout != null) {
            jdbcTemplate.setQueryTimeout(queryTimeoutSeconds(timeout));
        }
        return new JdbcMetricCollector(jdbcTemplate);
    }

    private static int queryTimeoutSeconds(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("query timeout must be positive");
        }
        long millis = timeout.toMillis();
        long seconds = millis / 1000L + (millis % 1000L == 0L ? 0L : 1L);
        if (seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("query timeout is too large");
        }
        return (int) seconds;
    }
}
