package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.SeriesOverflowPolicy;

public class ManualMetricRuntimeProperties {

    private Boolean enabled;
    private String suffix;
    private String scope;
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
