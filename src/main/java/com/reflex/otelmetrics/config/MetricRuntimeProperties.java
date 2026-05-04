package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MetricRuntimeProperties {

    private Boolean enabled;
    private String suffix;
    private String scope;
    private String dataSourceRef;
    private MetricKind kind;
    private MetricScheduleSettings.Mode scheduleMode;
    private Duration fixedDelay;
    private String cron;
    private Duration initialDelay;
    private Duration timeout;
    private Duration lockAtMostFor;
    private Duration lockAtLeastFor;
    private Integer maxSeries;
    private SeriesOverflowPolicy overflowPolicy;
}
