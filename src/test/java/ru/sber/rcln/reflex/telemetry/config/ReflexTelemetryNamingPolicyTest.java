package ru.sber.rcln.reflex.telemetry.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReflexTelemetryNamingPolicyTest {

    @Test
    void shouldPrefixMetricNameWithSystemCodeAndDot() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy("ci05414726");

        assertThat(policy.metricName("documents.current"))
                .isEqualTo("ci05414726.documents.current");
    }

    @Test
    void shouldNotPrefixMetricNameTwice() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy("ci05414726");

        assertThat(policy.metricName("ci05414726.documents.current"))
                .isEqualTo("ci05414726.documents.current");
    }

    @Test
    void shouldLeaveMetricNameUnchangedWhenSystemCodeIsBlank() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy(" ");

        assertThat(policy.metricName("documents.current"))
                .isEqualTo("documents.current");
    }

    @Test
    void shouldPrefixServiceNameWithSystemCodeAndUnderscore() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy("ci05414726");

        assertThat(policy.serviceName("contracts-api"))
                .isEqualTo("ci05414726_contracts-api");
    }

    @Test
    void shouldNotPrefixServiceNameTwice() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy("ci05414726");

        assertThat(policy.serviceName("ci05414726_contracts-api"))
                .isEqualTo("ci05414726_contracts-api");
    }

    @Test
    void shouldTrimInputs() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy(" ci05414726 ");

        assertThat(policy.metricName(" documents.current "))
                .isEqualTo("ci05414726.documents.current");
        assertThat(policy.serviceName(" contracts-api "))
                .isEqualTo("ci05414726_contracts-api");
    }

    @Test
    void shouldReturnBlankServiceNameAsNull() {
        ReflexTelemetryNamingPolicy policy = new ReflexTelemetryNamingPolicy("ci05414726");

        assertThat(policy.serviceName(" ")).isNull();
        assertThat(policy.serviceName(null)).isNull();
    }
}
