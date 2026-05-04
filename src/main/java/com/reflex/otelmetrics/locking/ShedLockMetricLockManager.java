package com.reflex.otelmetrics.locking;

import com.reflex.otelmetrics.config.ResolvedMetricConfig;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;

import java.time.Instant;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ShedLockMetricLockManager implements MetricLockManager {

    private final @NonNull LockProvider lockProvider;

    @Override
    public boolean executeWithLock(@NonNull ResolvedMetricConfig config, @NonNull Runnable runnable) {

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
