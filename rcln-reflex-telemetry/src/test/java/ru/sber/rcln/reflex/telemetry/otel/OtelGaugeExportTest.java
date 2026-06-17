package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtelGaugeExportTest {

    @Test
    void clearRemovesGaugePointsFromExport() {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meterProvider.get("test"));
        OtelMetricPublisher publisher = new OtelMetricPublisher(registry);
        ResolvedMetricConfig config = gaugeConfig();

        publisher.publish(config, List.of(new MetricPoint(100L, Map.of("status", "created"))));
        reader.collectAllMetrics();

        publisher.clear(config);
        Collection<MetricData> metrics = reader.collectAllMetrics();

        assertThat(longGaugePoints(metrics, "ci054147.documents.current")).isEmpty();
    }

    @Test
    void replaceSnapshotDropsSeriesMissingFromNewBatch() {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meterProvider.get("test"));
        OtelMetricPublisher publisher = new OtelMetricPublisher(registry);
        ResolvedMetricConfig config = gaugeConfig();

        publisher.publish(config, List.of(
                new MetricPoint(10L, Map.of("status", "created")),
                new MetricPoint(20L, Map.of("status", "archived"))));
        reader.collectAllMetrics();

        publisher.publish(config, List.of(new MetricPoint(30L, Map.of("status", "archived"))));
        Collection<MetricData> metrics = reader.collectAllMetrics();

        List<LongPointData> points = longGaugePoints(metrics, "ci054147.documents.current");
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualTo(30L);
        assertThat(points.get(0).getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("status")))
                .isEqualTo("archived");
    }

    private static List<LongPointData> longGaugePoints(Collection<MetricData> metrics, String name) {
        return metrics.stream()
                .filter(metric -> metric.getName().equals(name))
                .flatMap(metric -> metric.getLongGaugeData().getPoints().stream())
                .toList();
    }

    private static ResolvedMetricConfig gaugeConfig() {
        return new ResolvedMetricConfig(
                "documents-by-status",
                ReflexTelemetryProperties.MetricSourceType.JDBC,
                true,
                "ci054147.documents.current",
                "documents.current",
                "business",
                "Documents current",
                "1",
                AttributesSchema.empty(),
                "businessReplicaDataSource",
                MetricKind.GAUGE,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER,
                List.of());
    }
}
