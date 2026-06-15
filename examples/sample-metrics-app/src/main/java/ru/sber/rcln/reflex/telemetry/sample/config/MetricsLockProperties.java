package ru.sber.rcln.reflex.telemetry.sample.config;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.metrics-lock")
public class MetricsLockProperties {

    private static final Pattern SCHEMA_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Database schema that holds the ShedLock table ({@code <schema>.shedlock}).
     */
    private String schema = "telemetry";

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String tableName() {
        return validatedSchema() + ".shedlock";
    }

    public String validatedSchema() {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("app.metrics-lock.schema must not be blank");
        }
        if (!SCHEMA_IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException(
                    "app.metrics-lock.schema must be a simple SQL identifier, got: " + schema);
        }
        return schema;
    }
}
