package ru.sber.rcln.reflex.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReflexMetricScopesTest {

    @Test
    void shouldExposeReflexOwnedDefaultScopes() {
        assertThat(ReflexMetricScopes.JDBC).isEqualTo("jdbc");
        assertThat(ReflexMetricScopes.MANUAL).isEqualTo("manual");
    }
}
