package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualMetricRuntimeProperties {

    private Boolean enabled;
    private String suffix;
    private String scope;
    private Integer maxSeries;
    private SeriesOverflowPolicy overflowPolicy;
}
