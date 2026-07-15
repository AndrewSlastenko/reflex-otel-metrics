package ru.sber.rcln.reflex.telemetry.internal;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LoggingSupportTest {

    @Test
    void shouldThrottleRepeatedFailuresAcrossSuccessfulRuns(CapturedOutput output) {
        LoggingSupport loggingSupport = new LoggingSupport();
        ResolvedMetricConfig config = config();

        loggingSupport.recordFailure(config, new IllegalStateException("database unavailable"));
        loggingSupport.recordFailure(config, new IllegalStateException("database unavailable"));

        String message = "Metric test-metric failed during JDBC execution: "
                + "IllegalStateException: database unavailable";
        assertThat(output.getAll()).containsOnlyOnce(message);

        loggingSupport.recordSuccess(config);
        loggingSupport.recordFailure(config, new IllegalStateException("database unavailable"));

        assertThat(output.getAll()).containsOnlyOnce(message);
    }

    @Test
    void shouldKeepFailureMessageOnOneLine(CapturedOutput output) {
        LoggingSupport loggingSupport = new LoggingSupport();

        loggingSupport.recordFailure(config(), new IllegalStateException("first line\r\nsecond line"));

        assertThat(output.getAll()).contains("IllegalStateException: first line  second line");
    }

    private static ResolvedMetricConfig config() {
        return new ResolvedMetricConfig(
                "test-metric",
                ReflexTelemetryProperties.MetricSourceType.JDBC,
                true,
                "test.metric",
                "metric",
                "jdbc",
                "Test metric",
                "1",
                AttributesSchema.empty(),
                "dataSource",
                MetricKind.GAUGE,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(1), Duration.ZERO),
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.TRUNCATE,
                List.of());
    }
}
