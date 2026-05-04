package ru.sber.rcln.reflex.telemetry.jdbc;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcMetricCollectorFactory {

    public JdbcMetricCollector create(DataSource dataSource) {
        return new JdbcMetricCollector(new JdbcTemplate(dataSource));
    }
}
