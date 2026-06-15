package ru.sber.rcln.reflex.telemetry.locking;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

public interface MetricLockManager {

    boolean executeWithLock(ResolvedMetricConfig config, Runnable runnable);
}
