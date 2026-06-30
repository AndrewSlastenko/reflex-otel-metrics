package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricExecutionDispatcher {

    private final @NonNull ExecutorService executorService;
    private final ConcurrentMap<String, AtomicBoolean> runningByMetricId = new ConcurrentHashMap<>();

    public MetricDispatchOutcome dispatch(@NonNull ResolvedMetricConfig config, @NonNull Runnable runnable) {
        AtomicBoolean running = runningByMetricId.computeIfAbsent(config.metricId(), ignored -> new AtomicBoolean());
        if (!running.compareAndSet(false, true)) {
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
            return MetricDispatchOutcome.CAPACITY_SKIPPED;
        }
    }
}
