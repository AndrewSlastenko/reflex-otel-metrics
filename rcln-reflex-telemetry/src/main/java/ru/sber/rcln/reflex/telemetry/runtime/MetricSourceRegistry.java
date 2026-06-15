package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricSource;
import java.util.List;
import lombok.NonNull;

public record MetricSourceRegistry(@NonNull List<MetricSource> sources) {

    public MetricSourceRegistry {
        sources = List.copyOf(sources);
    }
}
