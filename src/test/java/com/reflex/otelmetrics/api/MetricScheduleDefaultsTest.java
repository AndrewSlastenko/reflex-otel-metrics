package com.reflex.otelmetrics.api;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricScheduleDefaultsTest {

    @Test
    void allowsFixedDelaySchedules() {
        assertThatCode(() -> new MetricScheduleDefaults(
                MetricScheduleDefaults.Mode.FIXED_DELAY,
                Duration.ofMinutes(5),
                null,
                Duration.ofSeconds(30)
        )).doesNotThrowAnyException();
    }

    @Test
    void allowsCronSchedules() {
        assertThatCode(() -> new MetricScheduleDefaults(
                MetricScheduleDefaults.Mode.CRON,
                null,
                "0 */5 * * * *",
                Duration.ofSeconds(30)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsFixedDelaySchedulesWithoutDelay() {
        assertThatThrownBy(() -> new MetricScheduleDefaults(
                MetricScheduleDefaults.Mode.FIXED_DELAY,
                null,
                null,
                Duration.ofSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedDelay");
    }

    @Test
    void rejectsCronSchedulesWithoutCronExpression() {
        assertThatThrownBy(() -> new MetricScheduleDefaults(
                MetricScheduleDefaults.Mode.CRON,
                null,
                null,
                Duration.ofSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron");

        assertThatThrownBy(() -> new MetricScheduleDefaults(
                MetricScheduleDefaults.Mode.CRON,
                null,
                "   ",
                Duration.ofSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron");
    }

    @Test
    void rejectsMixedDelayAndCronState() {
        assertThatThrownBy(() -> new MetricScheduleDefaults(
                MetricScheduleDefaults.Mode.CRON,
                Duration.ofMinutes(5),
                "0 */5 * * * *",
                Duration.ofSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedDelay");
    }
}
