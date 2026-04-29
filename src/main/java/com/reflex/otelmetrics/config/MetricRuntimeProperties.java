package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;

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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getDataSourceRef() {
        return dataSourceRef;
    }

    public void setDataSourceRef(String dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public MetricKind getKind() {
        return kind;
    }

    public void setKind(MetricKind kind) {
        this.kind = kind;
    }

    public MetricScheduleSettings.Mode getScheduleMode() {
        return scheduleMode;
    }

    public void setScheduleMode(MetricScheduleSettings.Mode scheduleMode) {
        this.scheduleMode = scheduleMode;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getLockAtMostFor() {
        return lockAtMostFor;
    }

    public void setLockAtMostFor(Duration lockAtMostFor) {
        this.lockAtMostFor = lockAtMostFor;
    }

    public Duration getLockAtLeastFor() {
        return lockAtLeastFor;
    }

    public void setLockAtLeastFor(Duration lockAtLeastFor) {
        this.lockAtLeastFor = lockAtLeastFor;
    }

    public Integer getMaxSeries() {
        return maxSeries;
    }

    public void setMaxSeries(Integer maxSeries) {
        this.maxSeries = maxSeries;
    }

    public SeriesOverflowPolicy getOverflowPolicy() {
        return overflowPolicy;
    }

    public void setOverflowPolicy(SeriesOverflowPolicy overflowPolicy) {
        this.overflowPolicy = overflowPolicy;
    }
}
