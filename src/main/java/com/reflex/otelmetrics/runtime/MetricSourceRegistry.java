package com.reflex.otelmetrics.runtime;

import com.reflex.otelmetrics.api.MetricSource;
import java.util.List;
import lombok.NonNull;

public record MetricSourceRegistry(@NonNull List<MetricSource> sources) {

    public MetricSourceRegistry {
        sources = List.copyOf(sources);
    }
}
