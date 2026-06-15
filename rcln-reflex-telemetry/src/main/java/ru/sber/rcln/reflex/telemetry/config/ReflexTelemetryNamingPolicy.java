package ru.sber.rcln.reflex.telemetry.config;

import lombok.NonNull;

public class ReflexTelemetryNamingPolicy {

    private final String systemCode;

    public ReflexTelemetryNamingPolicy(String systemCode) {
        this.systemCode = normalize(systemCode);
    }

    public String metricName(@NonNull String name) {
        String normalizedName = name.trim();
        if (systemCode == null || normalizedName.startsWith(systemCode + ".")) {
            return normalizedName;
        }
        return systemCode + "." + normalizedName;
    }

    public String serviceName(String serviceName) {
        String normalizedServiceName = normalize(serviceName);
        if (normalizedServiceName == null) {
            return null;
        }
        if (systemCode == null || normalizedServiceName.startsWith(systemCode + "_")) {
            return normalizedServiceName;
        }
        return systemCode + "_" + normalizedServiceName;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
