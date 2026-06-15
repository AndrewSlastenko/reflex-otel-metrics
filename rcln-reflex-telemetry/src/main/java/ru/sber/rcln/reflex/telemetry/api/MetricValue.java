package ru.sber.rcln.reflex.telemetry.api;

public sealed interface MetricValue permits MetricValue.LongMetricValue, MetricValue.DoubleMetricValue {

    enum ValueType {
        LONG,
        DOUBLE
    }

    ValueType type();

    default long asLong() {
        throw new IllegalStateException("Metric value cannot be represented as long");
    }

    default double asDouble() {
        throw new IllegalStateException("Metric value cannot be represented as double");
    }

    static MetricValue longValue(long value) {
        return new LongMetricValue(value);
    }

    static MetricValue doubleValue(double value) {
        return new DoubleMetricValue(value);
    }

    record LongMetricValue(long value) implements MetricValue {
        @Override
        public ValueType type() {
            return ValueType.LONG;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }
    }

    record DoubleMetricValue(double value) implements MetricValue {
        @Override
        public ValueType type() {
            return ValueType.DOUBLE;
        }

        @Override
        public double asDouble() {
            return value;
        }
    }
}
