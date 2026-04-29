package com.reflex.otelmetrics.api;

public enum SeriesOverflowPolicy {
    FAIL,
    TRUNCATE,
    AGGREGATE_TO_OTHER
}
