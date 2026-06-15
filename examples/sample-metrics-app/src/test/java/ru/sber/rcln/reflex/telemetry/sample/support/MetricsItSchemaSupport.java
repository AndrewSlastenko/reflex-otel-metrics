package ru.sber.rcln.reflex.telemetry.sample.support;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class MetricsItSchemaSupport {

    private MetricsItSchemaSupport() {
    }

    public static void ensureSchema(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("sql/metrics-it-schema.sql"));
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }
}
