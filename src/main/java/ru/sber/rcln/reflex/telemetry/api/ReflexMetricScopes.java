package ru.sber.rcln.reflex.telemetry.api;

/**
 * Logical Reflex metric scope names for grouping and enablement. These are not OpenTelemetry
 * instrumentation scopes; see {@code reflex.telemetry.instrumentation-scope-name} for OTel scope.
 */
public final class ReflexMetricScopes {

    public static final String JDBC = "jdbc";
    public static final String MANUAL = "manual";

    private ReflexMetricScopes() {
    }
}
