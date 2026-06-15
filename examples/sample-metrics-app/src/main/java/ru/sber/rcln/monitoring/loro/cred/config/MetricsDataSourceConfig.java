package ru.sber.rcln.monitoring.loro.cred.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.metrics-datasources.business")
    DataSourceProperties businessMetricsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("businessMetricsDataSource")
    @ConfigurationProperties("app.metrics-datasources.business.hikari")
    DataSource businessMetricsDataSource(
            @Qualifier("businessMetricsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.metrics-datasources.workflow")
    DataSourceProperties workflowMetricsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("workflowMetricsDataSource")
    @ConfigurationProperties("app.metrics-datasources.workflow.hikari")
    DataSource workflowMetricsDataSource(
            @Qualifier("workflowMetricsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.metrics-datasources.telemetry")
    DataSourceProperties telemetryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("telemetryDataSource")
    @ConfigurationProperties("app.metrics-datasources.telemetry.hikari")
    DataSource telemetryDataSource(
            @Qualifier("telemetryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
