# JDBC Metric Dispatcher Design

## Status

Draft reviewed in conversation on June 30, 2026.

## Goal

Replace the implicit single-thread JDBC polling runtime with an explicit dispatcher that can run different metrics concurrently while preserving the no-overlap contract for each individual metric id.

The first stage focuses on execution semantics, not on advanced runtime observability.

## Problem

The current JDBC runtime creates one `ScheduledExecutorService` thread for all metrics. Each scheduled task performs the whole run synchronously in that scheduling thread: local enablement check, ShedLock acquisition, JDBC collection, series limiting, and OpenTelemetry publication.

This creates local head-of-line blocking. One slow JDBC metric can delay all other metrics in the same JVM, including gauge cleanup runs on pods that did not acquire the distributed lock.

Increasing the scheduled executor pool directly is not enough as a design contract. The library must explicitly define what happens when a new tick for the same metric arrives while the previous local run is still active.

## Target Contract

The dispatcher separates scheduling from execution:

- scheduling generates ticks for configured metric schedules;
- worker execution runs metric tasks in a bounded pool with no waiting queue;
- different metric ids may run concurrently up to the configured pool size;
- ticks above worker capacity are skipped immediately instead of queued;
- one metric id must never run concurrently with itself in the same JVM;
- local overlap is skipped before ShedLock, JDBC, series limiting, publishing, or gauge clearing;
- distributed lock skip keeps the current behavior and clears local gauge snapshot for non-leader pods.

The most important invariant:

```text
tick -> local running guard
     -> already running: local-overlap skip, do not call runOnce()
     -> not running: try submit to worker
          -> worker available: run runOnce()
          -> worker unavailable: capacity skip, clear local running flag
```

`MetricExecutionTask.runOnce()` remains responsible for actual metric execution outcomes. The dispatcher owns only local scheduling and local overlap decisions.

## Local Overlap vs Distributed Lock Skip

These two skip reasons must stay distinct.

Local overlap skip means the same JVM already has a running task for the same metric id. This skip must not attempt ShedLock and must not call `publisher.clear(config)`. Clearing in this path can erase a valid in-process gauge snapshot while the previous local run is still collecting or publishing it.

Distributed lock skip means this JVM attempted the real metric run but another pod owns the lock. For gauge metrics, the current `MetricExecutionTask` behavior is still correct: clear the local snapshot so a non-leader pod does not export stale gauge series.

## Configuration

Add a small scheduler configuration block:

```yaml
reflex:
  telemetry:
    metrics:
      jdbc:
        scheduler:
          pool-size: 2
```

`pool-size` controls how many JDBC metric runs may execute concurrently in one JVM. The default is `2` so the library improves production behavior out of the box while still protecting each metric id from local overlap.

`pool-size` must be at least `1`. Applications that need strict legacy local serialization can set it to `1`.

Stage one intentionally uses queue size `0`. There is no public `queue-size` property. If all workers are busy when a new eligible tick arrives, the dispatcher performs a capacity skip, clears the local running flag for that metric id, and does not call `MetricExecutionTask.runOnce()`.

The README must explain that `pool-size` should be sized together with the metrics `DataSource` pool limits. The runtime cannot safely execute more concurrent JDBC work than the application's telemetry pools can sustain.

## Runtime Shape

Introduce a small dispatcher component in `runtime`, for example `MetricExecutionDispatcher`.

Responsibilities:

- keep per-metric local execution state keyed by `ResolvedMetricConfig.metricId()`;
- accept ticks from `MetricSchedulerRegistrar`;
- submit eligible runs to a worker executor;
- skip local overlap ticks without invoking `MetricExecutionTask.runOnce()`;
- skip capacity overflow ticks without invoking `MetricExecutionTask.runOnce()`;
- clear the local running flag in `finally` after worker execution.

`MetricSchedulerRegistrar` should remain focused on schedule calculation. It should call a tick callback instead of owning metric execution semantics.

The scheduling executor may stay single-threaded because it no longer performs JDBC work. Worker executor size is controlled by `pool-size`. The worker executor should use `SynchronousQueue` and an abort rejection policy so there is no hidden unbounded queue.

## Cron and Fixed Delay Semantics

Fixed-delay schedules should continue to avoid piling up repeated local executions for the same metric id. With a dispatcher, a fixed-delay tick can be generated by the scheduler and accepted or locally skipped by the dispatcher.

Cron schedules should not create an unbounded backlog if a metric run takes longer than the cron interval. If a cron tick fires while the same metric is still running, the dispatcher performs a local-overlap skip and waits for the next scheduled cron tick.

The stage-one design intentionally chooses skip over queueing for both local overlap and worker capacity exhaustion. Queueing one pending run can be added in a later design if there is a strong use case, but no-queue execution is simpler, bounded, and safer for JDBC polling.

## Autoconfiguration

Keep the existing custom bean escape hatch for applications that already provide `reflexTelemetryMetricScheduledExecutorService`.

Add a separate worker executor bean for metric execution, for example `reflexTelemetryMetricWorkerExecutorService`. It should be daemon-threaded, bounded by `pool-size`, and shut down with the Spring context.

Use clear thread names:

- scheduler: `reflex-telemetry-metrics-scheduler`
- workers: `reflex-telemetry-metrics-worker-N`

## Testing

The implementation must cover these behaviors:

- two different metric ids can execute concurrently when `pool-size >= 2`;
- a second tick for the same metric id while the first run is active is skipped locally;
- local-overlap skip does not call `MetricExecutionTask.runOnce()`;
- local-overlap skip does not attempt ShedLock;
- local-overlap skip does not call `publisher.clear(config)`;
- capacity skip happens when all workers are busy and does not call `MetricExecutionTask.runOnce()`;
- capacity skip releases the local running flag so a later tick for the same metric id can be accepted;
- distributed lock skip still clears gauge snapshot through `MetricExecutionTask`;
- configured `pool-size` is bound from properties;
- `pool-size <= 0` fails validation/startup.

## Out of Scope for Stage One

Stage one does not add a full internal observability model. It may add a small local-overlap hook if needed for tests or future telemetry, but queue-delay metrics, active-worker gauges, per-DataSource concurrency limits, and configurable overlap policies are separate follow-up work.
