package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.api.GaugeMetric;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import com.reflex.otelmetrics.api.UpDownCounterMetric;
import com.reflex.otelmetrics.config.ManualMetricConfigResolver;
import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.config.ResolvedManualMetricConfig;
import com.reflex.otelmetrics.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        MetricDefinition definition = MetricDefinition.of("orders.created").build();
        configResolver.nextConfig = resolved(MetricKind.COUNTER, SeriesOverflowPolicy.FAIL);

        CounterMetric metric = factory.counter("orders-created", definition);

        assertThat(metric).isInstanceOf(DefaultCounterMetric.class);
        assertThat(configResolver.metricId).isEqualTo("orders-created");
        assertThat(configResolver.kind).isEqualTo(MetricKind.COUNTER);
        assertThat(configResolver.definition).isSameAs(definition);
        assertThat(instrumentRegistry.name).isEqualTo("reflex.orders.created");
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.COUNTER);
    }

    @Test
    void createsGaugeMetricFromResolvedConfigAndRegistryInstrument() {
        MetricDefinition definition = MetricDefinition.of("queue.depth").build();
        configResolver.nextConfig = resolved(MetricKind.GAUGE, SeriesOverflowPolicy.FAIL);

        GaugeMetric metric = factory.gauge("queue-depth", definition);

        assertThat(metric).isInstanceOf(DefaultGaugeMetric.class);
        assertThat(configResolver.metricId).isEqualTo("queue-depth");
        assertThat(configResolver.kind).isEqualTo(MetricKind.GAUGE);
        assertThat(configResolver.definition).isSameAs(definition);
        assertThat(instrumentRegistry.name).isEqualTo("reflex.orders.created");
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.GAUGE);
    }

    @Test
    void createsUpDownCounterMetricFromResolvedConfigAndRegistryInstrument() {
        MetricDefinition definition = MetricDefinition.of("workers.active").build();
        configResolver.nextConfig = resolved(MetricKind.UP_DOWN_COUNTER, SeriesOverflowPolicy.FAIL);

        UpDownCounterMetric metric = factory.upDownCounter("workers-active", definition);

        assertThat(metric).isInstanceOf(DefaultUpDownCounterMetric.class);
        assertThat(configResolver.metricId).isEqualTo("workers-active");
        assertThat(configResolver.kind).isEqualTo(MetricKind.UP_DOWN_COUNTER);
        assertThat(configResolver.definition).isSameAs(definition);
        assertThat(instrumentRegistry.name).isEqualTo("reflex.orders.created");
        assertThat(instrumentRegistry.kind).isEqualTo(MetricKind.UP_DOWN_COUNTER);
    }

    @Test
    void failsFastWhenResolvedConfigUsesAggregateToOtherOverflowPolicy() {
        MetricDefinition definition = MetricDefinition.of("orders.created")
                .overflowPolicy(SeriesOverflowPolicy.AGGREGATE_TO_OTHER)
                .build();
        configResolver.nextConfig = resolved(MetricKind.COUNTER, SeriesOverflowPolicy.AGGREGATE_TO_OTHER);

        assertThatThrownBy(() -> factory.counter("orders-created", definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AGGREGATE_TO_OTHER is not supported for manual metrics");
    }

    private static ResolvedManualMetricConfig resolved(MetricKind kind, SeriesOverflowPolicy overflowPolicy) {
        return new ResolvedManualMetricConfig(
                "orders-created",
                true,
                "reflex.orders.created",
                "orders.created",
                "default",
                kind,
                null,
                null,
                MetricDefinition.of("orders.created").build().attributes(),
                500,
                overflowPolicy);
    }

    private static final class RecordingConfigResolver extends ManualMetricConfigResolver {
        private ResolvedManualMetricConfig nextConfig;
        private String metricId;
        private MetricKind kind;
        private MetricDefinition definition;

        private RecordingConfigResolver() {
            super(new ReflexOtelMetricsProperties());
        }

        @Override
        public ResolvedManualMetricConfig resolve(String metricId, MetricKind kind, MetricDefinition definition) {
            this.metricId = metricId;
            this.kind = kind;
            this.definition = definition;
            return nextConfig;
        }
    }

    private static final class RecordingInstrumentRegistry extends OtelInstrumentRegistry {
        private String name;
        private MetricKind kind;

        private RecordingInstrumentRegistry() {
            super(OpenTelemetry.noop().getMeter("test"));
        }

        @Override
        public Object getOrCreate(String name, MetricKind kind) {
            this.name = name;
            this.kind = kind;
            return switch (kind) {
                case COUNTER -> new NoopLongCounter();
                case GAUGE -> new NoopLongGauge();
                case UP_DOWN_COUNTER -> new NoopLongUpDownCounter();
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
}
