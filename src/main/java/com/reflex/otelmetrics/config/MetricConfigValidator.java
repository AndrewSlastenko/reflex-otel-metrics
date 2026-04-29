package com.reflex.otelmetrics.config;

import java.util.Objects;

public class MetricConfigValidator {

    public void validate(ResolvedMetricConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        if (config.dataSourceRef() == null || config.dataSourceRef().isBlank()) {
            throw new IllegalArgumentException("dataSourceRef must not be blank");
        }
    }
}
