package com.reflex.otelmetrics.api;

public enum SeriesOverflowPolicy {
    DROP_OLDEST,
    DROP_NEWEST,
    FAIL
}
