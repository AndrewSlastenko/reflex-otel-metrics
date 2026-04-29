package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.MetricScheduleDefaults;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricConfigValidatorTest {

    @Test
    void rejectsMissingDataSourceRef() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents.by.status",
                MetricKind.GAUGE,
                "business",
                null,
                new MetricScheduleDefaults(
                        MetricScheduleDefaults.Mode.FIXED_DELAY,
                        Duration.ofMinutes(5),
                        null,
                        Duration.ofSeconds(30)
                ),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThatThrownBy(() -> new MetricConfigValidator().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSourceRef");
    }
}
