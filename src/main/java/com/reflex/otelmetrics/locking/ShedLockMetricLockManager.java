package com.reflex.otelmetrics.locking;

import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;

import java.time.Instant;
import java.util.Objects;

public class ShedLockMetricLockManager implements MetricLockManager {

    private final LockProvider lockProvider;

    public ShedLockMetricLockManager(LockProvider lockProvider) {
        this.lockProvider = Objects.requireNonNull(lockProvider, "lockProvider must not be null");
    }

    @Override
    public boolean executeWithLock(ResolvedMetricConfig config, Runnable runnable) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(runnable, "runnable must not be null");

        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(),
                "reflex-otel-metric:" + config.metricId(),
                config.lockAtMostFor(),
                config.lockAtLeastFor()
        );

        return lockProvider.lock(lockConfiguration)
                .map(lock -> {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        lock.unlock();
                    }
                })
                .orElse(false);
    }
}
