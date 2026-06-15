package ru.sber.rcln.monitoring.loro.cred.support;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import ru.sber.rcln.monitoring.loro.cred.config.MetricsLockProperties;

public final class TestSchemaSupport {

    private TestSchemaSupport() {}

    public static void ensureSchema(DataSource dataSource) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("sql/test-schema.sql"));
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }

    public static void ensureSchema(DataSource dataSource, MetricsLockProperties lockProperties) {
        ensureSchema(dataSource);
        ShedlockSchemaSupport.ensureSchema(dataSource, lockProperties);
    }
}
