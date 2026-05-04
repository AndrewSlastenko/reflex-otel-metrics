package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualSeriesTrackerTest {

    @Test
    void allowsExistingSeriesAfterLimitIsReached() {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.FAIL);

        assertThat(tracker.apply(Map.of("client", "A")).accepted()).isTrue();
        assertThat(tracker.apply(Map.of("client", "A")).accepted()).isTrue();
    }

    @Test
    void rejectsNewSeriesAfterLimitIsReachedForFailPolicy() {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.FAIL);

        tracker.apply(Map.of("client", "A"));
        ManualSeriesTracker.Result result = tracker.apply(Map.of("client", "B"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("max series limit 1 exceeded");
    }

    @Test
    void rejectsNewSeriesAfterLimitIsReachedForTruncatePolicy() {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.TRUNCATE);

        tracker.apply(Map.of("client", "A"));
        ManualSeriesTracker.Result result = tracker.apply(Map.of("client", "B"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("max series limit 1 exceeded");
    }

    @Test
    void rejectsAggregateToOtherPolicy() {
        assertThatThrownBy(() -> new ManualSeriesTracker(1, SeriesOverflowPolicy.AGGREGATE_TO_OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AGGREGATE_TO_OTHER is not supported for manual metrics");
    }

    @Test
    void copiesAttributesIntoImmutableSeriesKey() {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.FAIL);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("client", "A");

        ManualSeriesTracker.Result result = tracker.apply(attributes);
        attributes.put("client", "B");

        assertThat(result.attributes()).containsEntry("client", "A");
        assertThatThrownBy(() -> result.attributes().put("region", "RU"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void doesNotAcceptMoreThanMaxSeriesConcurrently() throws Exception {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.FAIL);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ManualSeriesTracker.Result>> futures = new ArrayList<>();

        for (int i = 0; i < 16; i++) {
            String client = "client-" + i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return tracker.apply(Map.of("client", client));
            }));
        }

        ready.await();
        start.countDown();

        int accepted = 0;
        for (Future<ManualSeriesTracker.Result> future : futures) {
            if (future.get().accepted()) {
                accepted++;
            }
        }
        executor.shutdownNow();

        assertThat(accepted).isEqualTo(1);
    }

    @Test
    void rejectsInvalidMaxSeries() {
        assertThatThrownBy(() -> new ManualSeriesTracker(0, SeriesOverflowPolicy.FAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullOverflowPolicy() {
        assertThatThrownBy(() -> new ManualSeriesTracker(1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
