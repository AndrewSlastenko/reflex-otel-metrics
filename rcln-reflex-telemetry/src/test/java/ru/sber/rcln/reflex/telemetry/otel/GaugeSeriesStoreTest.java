package ru.sber.rcln.reflex.telemetry.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GaugeSeriesStoreTest {

    private final GaugeSeriesStore store = new GaugeSeriesStore();

    @Test
    void putAndSnapshotReturnsSeries() {
        Attributes attributes = Attributes.of(AttributeKey.stringKey("status"), "created");

        store.put("documents.current", attributes, 100L);

        assertThat(store.snapshot("documents.current")).containsExactly(Map.entry(attributes, 100L));
    }

    @Test
    void replaceSnapshotReplacesEntireSeriesSet() {
        Attributes first = Attributes.of(AttributeKey.stringKey("status"), "created");
        Attributes second = Attributes.of(AttributeKey.stringKey("status"), "archived");
        store.put("documents.current", first, 10L);

        store.replaceSnapshot("documents.current", Map.of(second, 20L));

        assertThat(store.snapshot("documents.current")).containsExactly(Map.entry(second, 20L));
    }

    @Test
    void clearRemovesSeries() {
        Attributes attributes = Attributes.of(AttributeKey.stringKey("status"), "created");
        store.put("documents.current", attributes, 100L);

        store.clear("documents.current");

        assertThat(store.snapshot("documents.current")).isEmpty();
    }

    @Test
    void replaceSnapshotWithEmptyMapClearsSeries() {
        Attributes attributes = Attributes.of(AttributeKey.stringKey("status"), "created");
        store.put("documents.current", attributes, 100L);

        store.replaceSnapshot("documents.current", Map.of());

        assertThat(store.snapshot("documents.current")).isEmpty();
    }
}
