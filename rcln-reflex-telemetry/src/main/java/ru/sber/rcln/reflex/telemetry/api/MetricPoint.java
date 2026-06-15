package ru.sber.rcln.reflex.telemetry.api;

import java.util.Map;
import java.util.Objects;

public final class MetricPoint {

    private final MetricValue metricValue;
    private final Map<String, String> attributes;

    public MetricPoint(MetricValue metricValue, Map<String, String> attributes) {
        this.metricValue = Objects.requireNonNull(metricValue, "metricValue must not be null");
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
    }

    public MetricPoint(long value, Map<String, String> attributes) {
        this(MetricValue.longValue(value), attributes);
    }

    public static MetricPoint histogram(double value, Map<String, String> attributes) {
        return new MetricPoint(MetricValue.doubleValue(value), attributes);
    }

    /**
     * Legacy accessor kept for backward compatibility with existing long-based call sites.
     */
    public long value() {
        return metricValue.asLong();
    }

    public MetricValue metricValue() {
        return metricValue;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public double asDoubleValue() {
        return metricValue.asDouble();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MetricPoint that)) {
            return false;
        }
        return metricValue.equals(that.metricValue) && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricValue, attributes);
    }

    @Override
    public String toString() {
        return "MetricPoint[metricValue=" + metricValue + ", attributes=" + attributes + "]";
    }
}
