package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.CounterMetric;
import ru.sber.rcln.reflex.telemetry.api.GaugeMetric;
import ru.sber.rcln.reflex.telemetry.api.HistogramMetric;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.api.UpDownCounterMetric;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryNamingPolicy;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.otel.MetricInstrumentWriter;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflexMetricFactoryTest {

    private final RecordingConfigResolver configResolver = new RecordingConfigResolver();
    private final RecordingInstrumentRegistry instrumentRegistry = new RecordingInstrumentRegistry();
    private final AttributeValidator attributeValidator = new AttributeValidator();
    private final ReflexMetricFactory factory = new ReflexMetricFactory(
            configResolver,
            instrumentRegistry,
            attributeValidator);

    @Test
    void createsCounterMetricFromResolvedConfigAndRegistryInstrument() {
        configResolver.nextConfig = resolved(MetricKind.COUNTER, "Created orders", "1", SeriesOverflowPolicy.FAIL);

        CounterMetric metric = factory.counter("orders-created");

        assertThat(metric).isInstanceOf(DefaultCounterMetric.class);
        assertThat(configResolver.metricId).isEqualTo("orders-created");
        assertThat(configResolver.kind).isEqualTo(MetricKind.COUNTER);
        assertThat(instrumentRegistry.name).isEqualTo("reflex.orders.created");
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.COUNTER);
        assertThat(instrumentRegistry.description).isEqualTo("Created orders");
        assertThat(instrumentRegistry.unit).isEqualTo("1");
    }

    @Test
    void createsGaugeMetricFromResolvedConfigAndRegistryInstrument() {
        configResolver.nextConfig = resolved(MetricKind.GAUGE, null, null, SeriesOverflowPolicy.FAIL);

        GaugeMetric metric = factory.gauge("queue-depth");

        assertThat(metric).isInstanceOf(DefaultGaugeMetric.class);
        assertThat(configResolver.metricId).isEqualTo("queue-depth");
        assertThat(configResolver.kind).isEqualTo(MetricKind.GAUGE);
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.GAUGE);
    }

    @Test
    void createsUpDownCounterMetricFromResolvedConfigAndRegistryInstrument() {
        configResolver.nextConfig = resolved(MetricKind.UP_DOWN_COUNTER, null, null, SeriesOverflowPolicy.FAIL);

        UpDownCounterMetric metric = factory.upDownCounter("workers-active");

        assertThat(metric).isInstanceOf(DefaultUpDownCounterMetric.class);
        assertThat(configResolver.metricId).isEqualTo("workers-active");
        assertThat(configResolver.kind).isEqualTo(MetricKind.UP_DOWN_COUNTER);
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.UP_DOWN_COUNTER);
    }

    @Test
    void createsHistogramMetricFromResolvedConfigAndRegistryInstrument() {
        configResolver.nextConfig = resolved(MetricKind.HISTOGRAM, "Request latency", "ms", SeriesOverflowPolicy.FAIL);

        HistogramMetric metric = factory.histogram("request-latency");

        assertThat(metric).isInstanceOf(DefaultHistogramMetric.class);
        assertThat(configResolver.metricId).isEqualTo("request-latency");
        assertThat(configResolver.kind).isEqualTo(MetricKind.HISTOGRAM);
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.HISTOGRAM);
        assertThat(instrumentRegistry.description).isEqualTo("Request latency");
        assertThat(instrumentRegistry.unit).isEqualTo("ms");
    }

    @Test
    void disabledMetricsDoNotRequireInstrumentRegistry() {
        ReflexMetricFactory factory = new ReflexMetricFactory(
                configResolver,
                () -> null,
                attributeValidator);

        configResolver.nextConfig = disabled(MetricKind.COUNTER);
        CounterMetric counter = factory.counter("orders-created");
        counter.add(1);
        counter.increment();

        configResolver.nextConfig = disabled(MetricKind.GAUGE);
        GaugeMetric gauge = factory.gauge("queue-depth");
        gauge.set(12);

        configResolver.nextConfig = disabled(MetricKind.UP_DOWN_COUNTER);
        UpDownCounterMetric upDownCounter = factory.upDownCounter("workers-active");
        upDownCounter.add(-1);

        configResolver.nextConfig = disabled(MetricKind.HISTOGRAM);
        HistogramMetric histogram = factory.histogram("request-latency");
        histogram.record(12.5d);
    }

    private static ResolvedMetricConfig resolved(
            MetricKind kind,
            String description,
            String unit,
            SeriesOverflowPolicy overflowPolicy) {
        return new ResolvedMetricConfig(
                "orders-created",
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                true,
                "reflex.orders.created",
                "orders.created",
                ReflexMetricScopes.MANUAL,
                description,
                unit,
                AttributesSchema.empty(),
                null,
                kind,
                null,
                null,
                null,
                null,
                500,
                overflowPolicy,
                List.of());
    }

    private static ResolvedMetricConfig disabled(MetricKind kind) {
        return new ResolvedMetricConfig(
                "orders-created",
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                false,
                "reflex.orders.created",
                "orders.created",
                ReflexMetricScopes.MANUAL,
                null,
                null,
                AttributesSchema.empty(),
                null,
                kind,
                null,
                null,
                null,
                null,
                500,
                SeriesOverflowPolicy.FAIL,
                List.of());
    }

    private static final class RecordingConfigResolver extends MetricConfigResolver {
        private ResolvedMetricConfig nextConfig;
        private String metricId;
        private MetricKind kind;

        private RecordingConfigResolver() {
            super(new ReflexTelemetryProperties(), new ReflexTelemetryNamingPolicy(null));
        }

        @Override
        public ResolvedMetricConfig resolveManual(String metricId, MetricKind expectedKind) {
            this.metricId = metricId;
            this.kind = expectedKind;
            return nextConfig;
        }
    }

    private static final class RecordingInstrumentRegistry extends OtelInstrumentRegistry {
        private String name;
        private MetricKind kind;
        private String description;
        private String unit;

        private RecordingInstrumentRegistry() {
            super(OpenTelemetry.noop().getMeter("test"));
        }

        @Override
        public MetricInstrumentWriter getOrCreateWriter(String name, MetricKind kind, String description, String unit) {
            this.name = name;
            this.kind = kind;
            this.description = description;
            this.unit = unit;
            return (point, attributes) -> {
            };
        }

        @Override
        public Object getOrCreate(String name, MetricKind kind, String description, String unit) {
            this.name = name;
            this.kind = kind;
            this.description = description;
            this.unit = unit;
            return switch (kind) {
                case COUNTER -> new NoopLongCounter();
                case GAUGE -> new NoopLongGauge();
                case UP_DOWN_COUNTER -> new NoopLongUpDownCounter();
                case HISTOGRAM -> new NoopDoubleHistogram();
            };
        }
    }

    private static final class NoopLongCounter implements LongCounter {
        @Override
        public void add(long value) {
        }

        @Override
        public void add(long value, Attributes attributes) {
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
        }
    }

    private static final class NoopLongGauge implements LongGauge {
        @Override
        public void set(long value) {
        }

        @Override
        public void set(long value, Attributes attributes) {
        }

        @Override
        public void set(long value, Attributes attributes, Context context) {
        }
    }

    private static final class NoopLongUpDownCounter implements LongUpDownCounter {
        @Override
        public void add(long value) {
        }

        @Override
        public void add(long value, Attributes attributes) {
        }

        @Override
        public void add(long value, Attributes attributes, Context context) {
        }
    }

    private static final class NoopDoubleHistogram implements DoubleHistogram {
        @Override
        public void record(double value) {
        }

        @Override
        public void record(double value, Attributes attributes) {
        }

        @Override
        public void record(double value, Attributes attributes, Context context) {
        }
    }
}
