package ru.sber.rcln.reflex.telemetry.sample.config;

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
    @ConfigurationProperties("app.metrics-datasources.documents")
    DataSourceProperties documentsMetricsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("documentsMetricsDataSource")
    @ConfigurationProperties("app.metrics-datasources.documents.hikari")
    DataSource documentsMetricsDataSource(
            @Qualifier("documentsMetricsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.metrics-datasources.payments")
    DataSourceProperties paymentsMetricsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("paymentsMetricsDataSource")
    @ConfigurationProperties("app.metrics-datasources.payments.hikari")
    DataSource paymentsMetricsDataSource(
            @Qualifier("paymentsMetricsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.metrics-datasources.telemetry-lock")
    DataSourceProperties telemetryLockDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("telemetryLockDataSource")
    @ConfigurationProperties("app.metrics-datasources.telemetry-lock.hikari")
    DataSource telemetryLockDataSource(
            @Qualifier("telemetryLockDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
