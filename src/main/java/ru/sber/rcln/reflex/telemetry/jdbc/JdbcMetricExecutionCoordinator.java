package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.runtime.MetricExecutionCoordinator;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JdbcMetricExecutionCoordinator implements MetricExecutionCoordinator {

    private final @NonNull JdbcMetricSource source;
    private final @NonNull JdbcMetricCollector collector;

    @Override
    public List<MetricPoint> collect() {
        return collector.collect(source.queryDefinition(), source.rowMapper());
    }
}
