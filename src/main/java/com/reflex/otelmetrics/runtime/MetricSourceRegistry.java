package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricSource;

import java.util.List;
import java.util.Objects;

public record MetricSourceRegistry(List<MetricSource> sources) {

    public MetricSourceRegistry {
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
    }
}
