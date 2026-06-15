package ru.sber.rcln.reflex.telemetry.sample.config;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Seeds the in-memory H2 demo database for {@code local} profile only. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDemoSchemaConfig {

  @Bean
  DataSourceScriptDatabaseInitializer demoSchemaInitializer(
      @Qualifier("documentsMetricsDataSource") DataSource dataSource) {
    DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
    settings.setSchemaLocations(List.of("classpath:db/local/demo-schema.sql"));
    settings.setMode(DatabaseInitializationMode.ALWAYS);
    return new DataSourceScriptDatabaseInitializer(dataSource, settings);
  }
}
