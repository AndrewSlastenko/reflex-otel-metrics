package com.reflex.otelmetrics.api;

public record MetricDefinition(
        String metricSuffix,
        String scope,
        String description,
        String unit,
        AttributesSchema attributes,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
    private static final String DEFAULT_SCOPE = "default";
    private static final int DEFAULT_MAX_SERIES = 500;

    public MetricDefinition {
        if (metricSuffix == null || metricSuffix.isBlank()) {
            throw new IllegalArgumentException("metricSuffix cannot be null or blank");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope cannot be null or blank");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes cannot be null");
        }
        if (maxSeries < 1) {
            throw new IllegalArgumentException("maxSeries must be at least 1");
        }
        if (overflowPolicy == null) {
            throw new IllegalArgumentException("overflowPolicy cannot be null");
        }
    }

    public static Builder of(String metricSuffix) {
        return new Builder(metricSuffix);
    }

    public static final class Builder {
        private final String metricSuffix;
        private String scope = DEFAULT_SCOPE;
        private String description;
        private String unit;
        private AttributesSchema attributes = AttributesSchema.empty();
        private int maxSeries = DEFAULT_MAX_SERIES;
        private SeriesOverflowPolicy overflowPolicy = SeriesOverflowPolicy.FAIL;

        private Builder(String metricSuffix) {
            this.metricSuffix = metricSuffix;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder attributes(AttributesSchema attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder maxSeries(int maxSeries) {
            this.maxSeries = maxSeries;
            return this;
        }

        public Builder overflowPolicy(SeriesOverflowPolicy overflowPolicy) {
            this.overflowPolicy = overflowPolicy;
            return this;
        }

        public MetricDefinition build() {
            return new MetricDefinition(
                    metricSuffix,
                    scope,
                    description,
                    unit,
                    attributes,
                    maxSeries,
                    overflowPolicy
            );
        }
    }
}
