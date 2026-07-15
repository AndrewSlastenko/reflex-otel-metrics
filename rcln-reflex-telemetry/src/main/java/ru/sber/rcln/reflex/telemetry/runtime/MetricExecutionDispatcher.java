package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class MetricExecutionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MetricExecutionDispatcher.class);
    private static final long SKIP_LOG_INTERVAL_NANOS = Duration.ofMinutes(5).toNanos();

    private final @NonNull ExecutorService executorService;
    private final ConcurrentMap<String, AtomicBoolean> runningByMetricId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> nextSkipLogByReason = new ConcurrentHashMap<>();

    public MetricDispatchOutcome dispatch(@NonNull ResolvedMetricConfig config, @NonNull Runnable runnable) {
        AtomicBoolean running = runningByMetricId.computeIfAbsent(config.metricId(), ignored -> new AtomicBoolean());
        if (!running.compareAndSet(false, true)) {
            if (log.isDebugEnabled() && shouldLogSkip(config.metricId() + ":overlap")) {
                log.debug("Metric {} execution skipped because a previous local run is still active", config.metricId());
            }
            return MetricDispatchOutcome.LOCAL_OVERLAP_SKIPPED;
        }

        try {
            executorService.execute(() -> {
                try {
                    runnable.run();
                } finally {
                    running.set(false);
                }
            });
            return MetricDispatchOutcome.ACCEPTED;
        } catch (RejectedExecutionException exception) {
            running.set(false);
            if (executorService.isShutdown()) {
                if (log.isDebugEnabled() && shouldLogSkip(config.metricId() + ":shutdown")) {
                    log.debug("Metric {} execution rejected during JDBC runtime shutdown", config.metricId());
                }
            } else if (shouldLogSkip(config.metricId() + ":capacity")) {
                log.warn("Metric {} execution skipped because the JDBC worker pool is at capacity", config.metricId());
            }
            return MetricDispatchOutcome.CAPACITY_SKIPPED;
        }
    }

    private boolean shouldLogSkip(String key) {
        long now = System.nanoTime();
        boolean[] shouldLog = {false};
        nextSkipLogByReason.compute(key, (ignored, deadline) -> {
            if (deadline == null || now - deadline >= 0) {
                shouldLog[0] = true;
                return now + SKIP_LOG_INTERVAL_NANOS;
            }
            return deadline;
        });
        return shouldLog[0];
    }
}
