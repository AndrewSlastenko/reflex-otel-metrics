# System Code Naming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ambiguous metric-only prefixes with one `reflex.telemetry.system-code` source of truth for metric names and OpenTelemetry `service.name`.

**Architecture:** Add a focused naming policy that derives metric names and effective OTel `service.name` from `system-code`. Wire current JDBC/manual metric resolvers and SDK resource creation through that policy. Remove `reflex.telemetry.metrics.metric-prefix` completely because there are no users yet and keeping both properties would create precedence ambiguity.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Maven, OpenTelemetry API/SDK 1.60.x, JUnit 5, AssertJ, ApplicationContextRunner.

---

## Scope

This plan only introduces the unified `system-code | name` contract for the current library behavior:

- existing JDBC polling metrics;
- existing manual metrics from `ReflexMetricFactory`;
- OpenTelemetry SDK resource attribute `service.name`;
- README configuration examples and property list.

This plan does not add HTTP/JDBC/Kafka standard instrumentation dependencies, toggles, or SDK metric views. Those belong to `docs/superpowers/plans/2026-05-14-managed-standard-instrumentation.md`.

## File Structure

- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`: add `systemCode`, remove `metrics.metricPrefix`.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicy.java`: derive prefixed metric names and effective service names.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolver.java`: use `ReflexTelemetryNamingPolicy` for JDBC metric names.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/ManualMetricConfigResolver.java`: use `ReflexTelemetryNamingPolicy` for manual metric names.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`: expose naming policy bean, wire resolvers with it, and apply policy to SDK resource `service.name`.
- Add `src/test/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicyTest.java`: focused unit coverage.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java`: update expected metric names.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/config/ManualMetricConfigResolverTest.java`: update expected metric names.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`: effective service-name and bean wiring coverage.
- Modify `README.md`: document the single public configuration contract under `reflex.telemetry.*`.

---

### Task 1: Naming Policy Unit

**Files:**
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicy.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicyTest.java`

- [ ] **Step 1: Write failing naming policy tests**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicyTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryNamingPolicyTest test
```

Expected: compilation fails because `ReflexTelemetryNamingPolicy` does not exist.

- [ ] **Step 3: Add minimal naming policy**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicy.java`:

```java
package ru.sber.rcln.reflex.telemetry.config;

import lombok.NonNull;

public class ReflexTelemetryNamingPolicy {

    private final String systemCode;

    public ReflexTelemetryNamingPolicy(String systemCode) {
        this.systemCode = normalize(systemCode);
    }

    public String metricName(@NonNull String name) {
        String normalizedName = name.trim();
        if (systemCode == null || normalizedName.startsWith(systemCode + ".")) {
            return normalizedName;
        }
        return systemCode + "." + normalizedName;
    }

    public String serviceName(String serviceName) {
        String normalizedServiceName = normalize(serviceName);
        if (normalizedServiceName == null) {
            return null;
        }
        if (systemCode == null || normalizedServiceName.startsWith(systemCode + "_")) {
            return normalizedServiceName;
        }
        return systemCode + "_" + normalizedServiceName;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryNamingPolicyTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicy.java src/test/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryNamingPolicyTest.java
git commit -m "feat: add telemetry naming policy"
```

---

### Task 2: Replace Metric Prefix with System Code

**Files:**
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolver.java`
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ManualMetricConfigResolver.java`
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/config/ManualMetricConfigResolverTest.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`

- [ ] **Step 1: Update resolver tests for system-code naming**

In `MetricConfigResolverTest`, change any setup that previously used `properties.getMetrics().setMetricPrefix(...)` to:

```java
ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
properties.setSystemCode("ci05414726");
```

Update expected metric names to:

```java
assertThat(resolved.fullMetricName()).isEqualTo("ci05414726.documents.current");
```

In `ManualMetricConfigResolverTest`, use the same property setup:

```java
ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
properties.setSystemCode("ci05414726");
```

Update expected manual metric names to:

```java
assertThat(resolved.fullMetricName()).isEqualTo("ci05414726.orders.created");
```

In `ReflexTelemetryAutoConfigurationTest`, add a bean wiring assertion:

```java
@Test
void shouldCreateNamingPolicyFromSystemCode() {
    contextRunner
            .withPropertyValues("reflex.telemetry.system-code=ci05414726")
            .run(context -> {
                assertThat(context).hasSingleBean(ReflexTelemetryNamingPolicy.class);
                assertThat(context.getBean(ReflexTelemetryNamingPolicy.class).metricName("orders.created"))
                        .isEqualTo("ci05414726.orders.created");
            });
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=MetricConfigResolverTest,ManualMetricConfigResolverTest,ReflexTelemetryAutoConfigurationTest test
```

Expected: compilation fails because `systemCode` property and resolver constructors are not updated.

- [ ] **Step 3: Update properties**

In `ReflexTelemetryProperties`, add the top-level field:

```java
private String systemCode;
```

Remove this field from `MetricsProperties`:

```java
private String metricPrefix = "reflex";
```

Do not add a replacement under `metrics`; `systemCode` is the single source of truth.

- [ ] **Step 4: Wire naming policy bean**

In `ReflexTelemetryAutoConfiguration`, add:

```java
@Bean
@ConditionalOnMissingBean
ReflexTelemetryNamingPolicy reflexTelemetryNamingPolicy(ReflexTelemetryProperties properties) {
    return new ReflexTelemetryNamingPolicy(properties.getSystemCode());
}
```

Change resolver bean methods to:

```java
@Bean
@ConditionalOnMissingBean
MetricConfigResolver metricConfigResolver(
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    return new MetricConfigResolver(properties, namingPolicy);
}

@Bean
@ConditionalOnMissingBean
ManualMetricConfigResolver manualMetricConfigResolver(
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    return new ManualMetricConfigResolver(properties, namingPolicy);
}
```

- [ ] **Step 5: Update resolvers**

Change `MetricConfigResolver` fields to:

```java
private final @NonNull ReflexTelemetryProperties properties;
private final @NonNull ReflexTelemetryNamingPolicy namingPolicy;
```

Change full metric name construction to:

```java
namingPolicy.metricName(suffix),
```

Change `ManualMetricConfigResolver` fields to:

```java
private final @NonNull ReflexTelemetryProperties properties;
private final @NonNull ReflexTelemetryNamingPolicy namingPolicy;
```

and full metric name construction to:

```java
namingPolicy.metricName(suffix),
```

- [ ] **Step 6: Fix direct test constructor calls**

Where tests construct resolvers directly, use:

```java
ReflexTelemetryNamingPolicy namingPolicy = new ReflexTelemetryNamingPolicy(properties.getSystemCode());
ResolvedMetricConfig resolved = new MetricConfigResolver(properties, namingPolicy)
        .resolve(new TestJdbcMetricSource());
```

For manual metrics:

```java
ReflexTelemetryNamingPolicy namingPolicy = new ReflexTelemetryNamingPolicy(properties.getSystemCode());
ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties, namingPolicy)
        .resolve("orders-created", MetricKind.COUNTER, definition);
```

- [ ] **Step 7: Run tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryNamingPolicyTest,MetricConfigResolverTest,ManualMetricConfigResolverTest,ReflexTelemetryAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/config src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java src/test/java/ru/sber/rcln/reflex/telemetry/config src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java
git commit -m "feat: use system code for metric names"
```

---

### Task 3: Apply System Code to OpenTelemetry service.name

**Files:**
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`

- [ ] **Step 1: Update service-name tests**

Change existing service-name test expectations from:

```java
assertThat(serviceName(context.getBean(SdkTracerProvider.class))).isEqualTo("contracts-api");
assertThat(serviceName(context.getBean(SdkMeterProvider.class))).isEqualTo("contracts-api");
```

to:

```java
assertThat(serviceName(context.getBean(SdkTracerProvider.class))).isEqualTo("ci05414726_contracts-api");
assertThat(serviceName(context.getBean(SdkMeterProvider.class))).isEqualTo("ci05414726_contracts-api");
```

Ensure the context runner includes:

```java
.withPropertyValues(
        "reflex.telemetry.system-code=ci05414726",
        "reflex.telemetry.service-name=contracts-api")
```

Add a no-double-prefix test:

```java
@Test
void shouldNotPrefixServiceNameTwice() {
    contextRunner
            .withPropertyValues(
                    "reflex.telemetry.system-code=ci05414726",
                    "reflex.telemetry.service-name=ci05414726_contracts-api")
            .run(context -> {
                assertThat(serviceName(context.getBean(SdkTracerProvider.class)))
                        .isEqualTo("ci05414726_contracts-api");
                assertThat(serviceName(context.getBean(SdkMeterProvider.class)))
                        .isEqualTo("ci05414726_contracts-api");
            });
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryAutoConfigurationTest test
```

Expected: service-name assertions fail because resource still uses raw `service-name`.

- [ ] **Step 3: Apply naming policy to resource creation**

Change `applyServiceNameResource` calls to accept `ReflexTelemetryNamingPolicy`:

```java
private static void applyServiceNameResource(
        SdkMeterProviderBuilder builder,
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    serviceNameResource(properties, namingPolicy).ifPresent(builder::addResource);
}

private static void applyServiceNameResource(
        SdkTracerProviderBuilder builder,
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    serviceNameResource(properties, namingPolicy).ifPresent(builder::addResource);
}
```

Change provider bean signatures:

```java
SdkMeterProvider sdkMeterProvider(
        OtlpGrpcMetricExporter exporter,
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
    applyServiceNameResource(builder, properties, namingPolicy);
    ...
}

SdkTracerProvider sdkTracerProvider(
        OtlpGrpcSpanExporter exporter,
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    SdkTracerProviderBuilder builder = SdkTracerProvider.builder();
    applyServiceNameResource(builder, properties, namingPolicy);
    ...
}
```

Replace `serviceNameResource` with:

```java
private static java.util.Optional<Resource> serviceNameResource(
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy) {
    String serviceName = namingPolicy.serviceName(properties.getServiceName());
    if (serviceName == null) {
        return java.util.Optional.empty();
    }

    return java.util.Optional.of(Resource.create(
            Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java
git commit -m "feat: prefix otel service name with system code"
```

---

### Task 4: Document Unified System Code Configuration

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update global configuration example**

Replace the global example with:

```yaml
reflex:
  telemetry:
    enabled: true
    system-code: ci05414726
    service-name: contracts-api
    instrumentation-scope-name: ru.sber.rcln.reflex.telemetry
    otlp:
      metrics-endpoint: http://localhost:4317
      traces-endpoint: http://localhost:4317
      export-timeout: 10s
      export-interval: 1m
    traces:
      enabled: true
    metrics:
      enabled: true
      scopes:
        business:
          enabled: true
```

- [ ] **Step 2: Update property list**

Remove `metric-prefix` from the list.

Add:

```markdown
- `system-code`
```

- [ ] **Step 3: Add naming policy section**

Add after `Service name`:

````markdown
## System code and naming

`reflex.telemetry.system-code` is the single source of truth for platform prefixes.

For metrics, the starter exports names as:

```text
<system-code>.<metric-suffix>
```

For OpenTelemetry resource identity, the starter exports `service.name` as:

```text
<system-code>_<service-name>
```

Application configuration should keep `service-name` unprefixed. The starter prevents double-prefixing when a value is already prefixed.
````

- [ ] **Step 4: Run docs-adjacent tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryNamingPolicyTest,MetricConfigResolverTest,ManualMetricConfigResolverTest,ReflexTelemetryAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add README.md
git commit -m "docs: document system code telemetry configuration"
```

---

### Task 5: Full Verification

**Files:**
- No file edits.

- [ ] **Step 1: Run full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Check for removed property references**

Run:

```powershell
rg -n "metric-prefix|metricPrefix|trace-service-name-prefix|system-code" README.md src pom.xml
```

Expected:

- No `metric-prefix` or `metricPrefix` references.
- No `trace-service-name-prefix` references.
- `system-code` appears in README and tests/properties.

- [ ] **Step 3: Check git status**

Run:

```powershell
git status --short
```

Expected: no uncommitted changes.

---

## Self-Review

- Spec coverage: the plan covers one public `system-code`, current JDBC/manual metric names, OTel `service.name`, and centralized `reflex.telemetry.*` configuration for existing behavior.
- Scope boundary: no standard HTTP/JDBC/Kafka instrumentation is introduced here.
- Ambiguity removed: `metrics.metric-prefix` and any trace-specific prefix property are removed rather than deprecated.
