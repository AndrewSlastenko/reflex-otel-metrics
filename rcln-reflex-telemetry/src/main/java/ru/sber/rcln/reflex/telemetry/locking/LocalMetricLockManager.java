package ru.sber.rcln.reflex.telemetry.locking;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import lombok.NonNull;

public class LocalMetricLockManager implements MetricLockManager {

    @Override
    public boolean executeWithLock(@NonNull ResolvedMetricConfig config, @NonNull Runnable runnable) {
        runnable.run();
        return true;
    }
}
