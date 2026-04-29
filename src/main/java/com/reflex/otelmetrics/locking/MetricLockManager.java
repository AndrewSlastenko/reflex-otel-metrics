package com.reflex.otelmetrics.locking;

import com.reflex.otelmetrics.config.ResolvedMetricConfig;

public interface MetricLockManager {

    boolean executeWithLock(ResolvedMetricConfig config, Runnable runnable);
}
