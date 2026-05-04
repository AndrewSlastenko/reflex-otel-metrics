package ru.sber.rcln.reflex.telemetry.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricDefinitionTest {

    @Test
    void buildsDefinitionWithDefaults() {
        MetricDefinition definition = MetricDefinition.of("orders.created").build();

        assertThat(definition.metricSuffix()).isEqualTo("orders.created");
        assertThat(definition.scope()).isEqualTo("default");
        assertThat(definition.description()).isNull();
        assertThat(definition.unit()).isNull();
        assertThat(definition.attributes()).isEqualTo(AttributesSchema.empty());
        assertThat(definition.maxSeries()).isEqualTo(500);
        assertThat(definition.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.FAIL);
    }

    @Test
    void rejectsBlankMetricSuffix() {
        assertThatThrownBy(() -> MetricDefinition.of(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metricSuffix");
    }

    @Test
    void rejectsMaxSeriesLessThanOne() {
        assertThatThrownBy(() -> MetricDefinition.of("orders.created")
                .maxSeries(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSeries");
    }
}
