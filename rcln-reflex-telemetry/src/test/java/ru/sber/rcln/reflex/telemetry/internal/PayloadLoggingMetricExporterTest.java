package ru.sber.rcln.reflex.telemetry.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayloadLoggingMetricExporterTest {

    @Test
    void shouldLogTheSameSnapshotBeforeDelegatingExport() {
        MetricExporter delegate = mock(MetricExporter.class);
        MetricExporter payloadLogger = mock(MetricExporter.class);
        List<MetricData> metrics = List.of(mock(MetricData.class));
        CompletableResultCode delegateResult = CompletableResultCode.ofSuccess();
        when(payloadLogger.export(metrics)).thenReturn(CompletableResultCode.ofSuccess());
        when(delegate.export(metrics)).thenReturn(delegateResult);

        PayloadLoggingMetricExporter exporter = new PayloadLoggingMetricExporter(delegate, payloadLogger);

        assertThat(exporter.export(metrics)).isSameAs(delegateResult);
        InOrder order = inOrder(payloadLogger, delegate);
        order.verify(payloadLogger).export(metrics);
        order.verify(delegate).export(metrics);
    }

    @Test
    void shouldUseDeliveryExporterContractForMetricCollection() {
        MetricExporter delegate = mock(MetricExporter.class);
        MetricExporter payloadLogger = mock(MetricExporter.class);
        when(delegate.getAggregationTemporality(InstrumentType.HISTOGRAM))
                .thenReturn(AggregationTemporality.DELTA);
        when(delegate.getDefaultAggregation(InstrumentType.HISTOGRAM))
                .thenReturn(Aggregation.explicitBucketHistogram());
        when(delegate.getMemoryMode()).thenReturn(MemoryMode.REUSABLE_DATA);

        PayloadLoggingMetricExporter exporter = new PayloadLoggingMetricExporter(delegate, payloadLogger);

        assertThat(exporter.getAggregationTemporality(InstrumentType.HISTOGRAM))
                .isEqualTo(AggregationTemporality.DELTA);
        assertThat(exporter.getDefaultAggregation(InstrumentType.HISTOGRAM))
                .isEqualTo(Aggregation.explicitBucketHistogram());
        assertThat(exporter.getMemoryMode()).isEqualTo(MemoryMode.REUSABLE_DATA);
    }

    @Test
    void shouldNotFailDeliveryWhenPayloadLoggingFails() {
        MetricExporter delegate = mock(MetricExporter.class);
        MetricExporter payloadLogger = mock(MetricExporter.class);
        List<MetricData> metrics = List.of(mock(MetricData.class));
        CompletableResultCode delegateResult = CompletableResultCode.ofSuccess();
        when(payloadLogger.export(metrics)).thenReturn(CompletableResultCode.ofFailure());
        when(delegate.export(metrics)).thenReturn(delegateResult);

        PayloadLoggingMetricExporter exporter = new PayloadLoggingMetricExporter(delegate, payloadLogger);

        assertThat(exporter.export(metrics)).isSameAs(delegateResult);
        verify(delegate).export(metrics);
    }

    @Test
    void shouldWriteTheExportedSnapshotAsOtlpJson() {
        Logger logger = Logger.getLogger(OtlpJsonLoggingMetricExporter.class.getName());
        List<String> messages = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        CapturingMetricExporter deliveryExporter = new CapturingMetricExporter();
        PayloadLoggingMetricExporter exporter = new PayloadLoggingMetricExporter(
                deliveryExporter,
                OtlpJsonLoggingMetricExporter.create(AggregationTemporality.DELTA));
        SdkMeterProvider provider = SdkMeterProvider.builder()
                .setResource(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "comparison-service")))
                .registerMetricReader(PeriodicMetricReader.builder(exporter)
                        .setInterval(Duration.ofHours(1))
                        .build())
                .build();

        try {
            provider.get("payload-test")
                    .counterBuilder("comparison.counter")
                    .build()
                    .add(7, Attributes.of(AttributeKey.stringKey("state"), "NEW"));

            assertThat(provider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(deliveryExporter.exported).isNotEmpty();
            assertThat(messages).anySatisfy(message -> assertThat(message)
                    .contains("\"service.name\"", "\"comparison.counter\"", "\"state\"", "\"NEW\""));
        } finally {
            provider.shutdown().join(5, TimeUnit.SECONDS);
            logger.removeHandler(handler);
        }
    }

    private static final class CapturingMetricExporter implements MetricExporter {

        private Collection<MetricData> exported = List.of();

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            exported = metrics;
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
            return AggregationTemporality.DELTA;
        }
    }
}
