package ru.sber.rcln.monitoring.loro.cred.support;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sber.rcln.monitoring.loro.cred.config.MetricsLockProperties;

/** Тестовый хелпер: DDL ShedLock в H2. В проде схему создают миграции. */
public final class ShedlockSchemaSupport {

    private ShedlockSchemaSupport() {}

    public static void ensureSchema(DataSource dataSource, MetricsLockProperties properties) {
        String schema = properties.validatedSchema();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + schema
                        + ".shedlock ("
                        + "name VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "lock_until TIMESTAMP NOT NULL, "
                        + "locked_at TIMESTAMP NOT NULL, "
                        + "locked_by VARCHAR(255) NOT NULL"
                        + ")");
    }
}
