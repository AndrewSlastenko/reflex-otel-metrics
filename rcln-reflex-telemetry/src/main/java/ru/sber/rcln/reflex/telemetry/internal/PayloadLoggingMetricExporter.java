package ru.sber.rcln.reflex.telemetry.internal;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public final class PayloadLoggingMetricExporter implements MetricExporter {

    private static final Logger log = LoggerFactory.getLogger(PayloadLoggingMetricExporter.class);

    private final MetricExporter delegate;
    private final MetricExporter payloadLogger;

    public PayloadLoggingMetricExporter(MetricExporter delegate, MetricExporter payloadLogger) {
        this.delegate = delegate;
        this.payloadLogger = payloadLogger;
    }

    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        try {
            CompletableResultCode loggingResult = payloadLogger.export(metrics);
            loggingResult.whenComplete(() -> {
                if (!loggingResult.isSuccess()) {
                    log.warn("Failed to log OTLP metrics payload");
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Failed to log OTLP metrics payload", exception);
        }
        return delegate.export(metrics);
    }

    @Override
    public CompletableResultCode flush() {
        payloadLogger.flush();
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        payloadLogger.shutdown();
        return delegate.shutdown();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return delegate.getAggregationTemporality(instrumentType);
    }

    @Override
    public Aggregation getDefaultAggregation(InstrumentType instrumentType) {
        return delegate.getDefaultAggregation(instrumentType);
    }

    @Override
    public MemoryMode getMemoryMode() {
        return delegate.getMemoryMode();
    }
}
