package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.ReflexMetricScopes;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.otel.MetricInstrumentWriter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DefaultGaugeMetricTest {

    private final RecordingGaugeWriter writer = new RecordingGaugeWriter();
    private final AttributeValidator attributeValidator = new AttributeValidator();

    @Test
    void publishesGaugeValueToLongGauge() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 500),
                writer,
                attributeValidator);

        metric.set(-7, Map.of("queue", "primary"));

        assertThat(writer.callCount).isEqualTo(1);
        assertThat(writer.point.value()).isEqualTo(-7);
        assertThat(writer.attributes.get(AttributeKey.stringKey("queue"))).isEqualTo("primary");
    }

    @Test
    void disabledMetricIsNoOp() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(false, AttributesSchema.builder().required("queue").build(), 500),
                writer,
                attributeValidator);

        metric.set(1, Map.of());

        assertThat(writer.callCount).isZero();
    }

    @Test
    void skipsInvalidAttributes() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 500),
                writer,
                attributeValidator);

        metric.set(1, Map.of());

        assertThat(writer.callCount).isZero();
    }

    @Test
    void skipsNewSeriesAfterLimitIsReached() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 1),
                writer,
                attributeValidator);

        metric.set(1, Map.of("queue", "primary"));
        metric.set(2, Map.of("queue", "secondary"));

        assertThat(writer.callCount).isEqualTo(1);
        assertThat(writer.point.value()).isEqualTo(1);
    }

    @Test
    void publishExceptionDoesNotEscape() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.empty(), 500),
                new ThrowingGaugeWriter(),
                attributeValidator);

        assertThatCode(() -> metric.set(1, Map.of())).doesNotThrowAnyException();
    }

    @Test
    void validatorExceptionDoesNotEscapeAndSkipsPublish() {
        DefaultGaugeMetric metric = new DefaultGaugeMetric(
                resolved(true, AttributesSchema.builder().required("queue").build(), 500),
                writer,
                new ThrowingAttributeValidator());

        assertThatCode(() -> metric.set(1, Map.of("queue", "primary"))).doesNotThrowAnyException();

        assertThat(writer.callCount).isZero();
    }

    private static ResolvedMetricConfig resolved(boolean enabled, AttributesSchema attributes, int maxSeries) {
        return new ResolvedMetricConfig(
                "queue-depth",
                ReflexTelemetryProperties.MetricSourceType.MANUAL,
                enabled,
                "reflex.queue.depth",
                "queue.depth",
                ReflexMetricScopes.MANUAL,
                null,
                null,
                attributes,
                null,
                MetricKind.GAUGE,
                null,
                null,
                null,
                null,
                maxSeries,
                SeriesOverflowPolicy.FAIL,
                java.util.List.of());
    }

    private static final class RecordingGaugeWriter implements MetricInstrumentWriter {
        private int callCount;
        private MetricPoint point;
        private Attributes attributes;

        @Override
        public void record(MetricPoint point, Attributes attributes) {
            this.callCount++;
            this.point = point;
            this.attributes = attributes;
        }
    }

    private static final class ThrowingGaugeWriter implements MetricInstrumentWriter {

        @Override
        public void record(MetricPoint point, Attributes attributes) {
            throw new RuntimeException("publish failed");
        }
    }

    private static final class ThrowingAttributeValidator extends AttributeValidator {

        @Override
        public AttributeValidationResult validate(AttributesSchema schema, Map<String, String> attributes) {
            throw new RuntimeException("validation failed");
        }
    }
}
