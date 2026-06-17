package ru.sber.rcln.reflex.telemetry.otel;

import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GaugeSeriesStore {

    private final ConcurrentHashMap<String, ConcurrentHashMap<Attributes, Long>> seriesByMetric = new ConcurrentHashMap<>();

    public void put(String metricName, Attributes attributes, long value) {
        seriesByMetric.computeIfAbsent(metricName, ignored -> new ConcurrentHashMap<>())
                .put(attributes, value);
    }

    public void replaceSnapshot(String metricName, Map<Attributes, Long> snapshot) {
        if (snapshot.isEmpty()) {
            seriesByMetric.remove(metricName);
            return;
        }
        seriesByMetric.put(metricName, new ConcurrentHashMap<>(snapshot));
    }

    public void clear(String metricName) {
        seriesByMetric.remove(metricName);
    }

    public Map<Attributes, Long> snapshot(String metricName) {
        ConcurrentHashMap<Attributes, Long> series = seriesByMetric.get(metricName);
        if (series == null || series.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(series);
    }
}
