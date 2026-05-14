# System Code Managed Instrumentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ambiguous metric-only prefixes with a single `reflex.telemetry.system-code` source of truth and prepare managed HTTP/JDBC/Kafka instrumentation to use the same OpenTelemetry SDK, OTLP endpoints, resource identity, and metric naming policy.

**Architecture:** Add a small naming policy component that derives metric names and effective OTel `service.name` from `system-code`. Wire current JDBC/manual metrics and SDK resource creation through that policy first. Then add standard instrumentation configuration as a controlled extension point; standard metrics are prefixed through OpenTelemetry SDK views, while traces keep semantic span names and use prefixed `service.name`.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Maven, OpenTelemetry API/SDK 1.60.x, OpenTelemetry Spring Boot starter/instrumentation BOM 2.x, JUnit 5, AssertJ, ApplicationContextRunner.

---

## Scope

This plan intentionally removes the old `reflex.telemetry.metrics.metric-prefix` property because there are no consumers yet and keeping both `metric-prefix` and `system-code` would create precedence ambiguity.

The plan is split into two milestones:

1. `system-code` for existing runtime: current JDBC/manual metrics and SDK `service.name`.
2. Managed standard instrumentation: HTTP/JDBC/Kafka toggles and metric-prefixing policy for metrics emitted by OTel instrumentation.

## File Structure

- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`: add `systemCode`, remove `metrics.metricPrefix`, add `instrumentation` property tree.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicy.java`: one responsibility, derive prefixed metric names and effective service names.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolver.java`: use `TelemetryNamingPolicy` for current JDBC metric names.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/ManualMetricConfigResolver.java`: use `TelemetryNamingPolicy` for manual metric names.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`: expose naming policy bean, wire resolvers with it, apply policy to SDK resource `service.name`, and later expose SDK metric views for standard instrumentation metrics.
- Add `src/test/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicyTest.java`: focused unit coverage.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java`: update expected metric names.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/config/ManualMetricConfigResolverTest.java`: update expected metric names.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`: effective service-name and bean wiring coverage.
- Modify `pom.xml`: add OpenTelemetry instrumentation BOM and Spring Boot starter only in the managed instrumentation milestone.
- Modify `README.md`: document the single public configuration contract under `reflex.telemetry.*`.

---

### Task 1: Naming Policy Unit

**Files:**
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicy.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicyTest.java`

- [ ] **Step 1: Write failing naming policy tests**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicyTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelemetryNamingPolicyTest {

    @Test
    void shouldPrefixMetricNameWithSystemCodeAndDot() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy("ci05414726");

        assertThat(policy.metricName("documents.current"))
                .isEqualTo("ci05414726.documents.current");
    }

    @Test
    void shouldNotPrefixMetricNameTwice() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy("ci05414726");

        assertThat(policy.metricName("ci05414726.documents.current"))
                .isEqualTo("ci05414726.documents.current");
    }

    @Test
    void shouldLeaveMetricNameUnchangedWhenSystemCodeIsBlank() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy(" ");

        assertThat(policy.metricName("documents.current"))
                .isEqualTo("documents.current");
    }

    @Test
    void shouldPrefixServiceNameWithSystemCodeAndUnderscore() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy("ci05414726");

        assertThat(policy.serviceName("contracts-api"))
                .isEqualTo("ci05414726_contracts-api");
    }

    @Test
    void shouldNotPrefixServiceNameTwice() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy("ci05414726");

        assertThat(policy.serviceName("ci05414726_contracts-api"))
                .isEqualTo("ci05414726_contracts-api");
    }

    @Test
    void shouldTrimInputs() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy(" ci05414726 ");

        assertThat(policy.metricName(" documents.current "))
                .isEqualTo("ci05414726.documents.current");
        assertThat(policy.serviceName(" contracts-api "))
                .isEqualTo("ci05414726_contracts-api");
    }

    @Test
    void shouldReturnBlankServiceNameAsNull() {
        TelemetryNamingPolicy policy = new TelemetryNamingPolicy("ci05414726");

        assertThat(policy.serviceName(" ")).isNull();
        assertThat(policy.serviceName(null)).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=TelemetryNamingPolicyTest test
```

Expected: compilation fails because `TelemetryNamingPolicy` does not exist.

- [ ] **Step 3: Add minimal naming policy**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicy.java`:

```java
package ru.sber.rcln.reflex.telemetry.config;

import lombok.NonNull;

public class TelemetryNamingPolicy {

    private final String systemCode;

    public TelemetryNamingPolicy(String systemCode) {
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
.\mvnw.cmd -Dtest=TelemetryNamingPolicyTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicy.java src/test/java/ru/sber/rcln/reflex/telemetry/config/TelemetryNamingPolicyTest.java
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

In `MetricConfigResolverTest`, change setup that previously used `properties.getMetrics().setMetricPrefix(...)` to:

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
                assertThat(context).hasSingleBean(TelemetryNamingPolicy.class);
                assertThat(context.getBean(TelemetryNamingPolicy.class).metricName("orders.created"))
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
TelemetryNamingPolicy telemetryNamingPolicy(ReflexTelemetryProperties properties) {
    return new TelemetryNamingPolicy(properties.getSystemCode());
}
```

Change resolver bean methods to:

```java
@Bean
@ConditionalOnMissingBean
MetricConfigResolver metricConfigResolver(
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
    return new MetricConfigResolver(properties, namingPolicy);
}

@Bean
@ConditionalOnMissingBean
ManualMetricConfigResolver manualMetricConfigResolver(
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
    return new ManualMetricConfigResolver(properties, namingPolicy);
}
```

- [ ] **Step 5: Update resolvers**

Change `MetricConfigResolver` fields and constructor to Lombok `@RequiredArgsConstructor` with:

```java
private final @NonNull ReflexTelemetryProperties properties;
private final @NonNull TelemetryNamingPolicy namingPolicy;
```

Change full metric name construction to:

```java
namingPolicy.metricName(suffix),
```

Change `ManualMetricConfigResolver` the same way:

```java
private final @NonNull ReflexTelemetryProperties properties;
private final @NonNull TelemetryNamingPolicy namingPolicy;
```

and:

```java
namingPolicy.metricName(suffix),
```

- [ ] **Step 6: Fix direct test constructor calls**

Where tests construct resolvers directly, use:

```java
TelemetryNamingPolicy namingPolicy = new TelemetryNamingPolicy(properties.getSystemCode());
ResolvedMetricConfig resolved = new MetricConfigResolver(properties, namingPolicy)
        .resolve(new TestJdbcMetricSource());
```

For manual metrics:

```java
TelemetryNamingPolicy namingPolicy = new TelemetryNamingPolicy(properties.getSystemCode());
ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties, namingPolicy)
        .resolve("orders-created", MetricKind.COUNTER, definition);
```

- [ ] **Step 7: Run tests**

Run:

```powershell
.\mvnw.cmd -Dtest=TelemetryNamingPolicyTest,MetricConfigResolverTest,ManualMetricConfigResolverTest,ReflexTelemetryAutoConfigurationTest test
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

Change `applyServiceNameResource` calls to accept `TelemetryNamingPolicy`:

```java
private static void applyServiceNameResource(
        SdkMeterProviderBuilder builder,
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
    serviceNameResource(properties, namingPolicy).ifPresent(builder::addResource);
}

private static void applyServiceNameResource(
        SdkTracerProviderBuilder builder,
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
    serviceNameResource(properties, namingPolicy).ifPresent(builder::addResource);
}
```

Change provider bean signatures:

```java
SdkMeterProvider sdkMeterProvider(
        OtlpGrpcMetricExporter exporter,
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
    SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
    applyServiceNameResource(builder, properties, namingPolicy);
    ...
}

SdkTracerProvider sdkTracerProvider(
        OtlpGrpcSpanExporter exporter,
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
    SdkTracerProviderBuilder builder = SdkTracerProvider.builder();
    applyServiceNameResource(builder, properties, namingPolicy);
    ...
}
```

Replace `serviceNameResource` with:

```java
private static java.util.Optional<Resource> serviceNameResource(
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy) {
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

### Task 4: Add Managed Instrumentation Configuration Contract

**Files:**
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`

- [ ] **Step 1: Add property binding test**

In `ReflexTelemetryAutoConfigurationTest`, add:

```java
@Test
void shouldBindManagedInstrumentationProperties() {
    contextRunner
            .withPropertyValues(
                    "reflex.telemetry.instrumentation.enabled=true",
                    "reflex.telemetry.instrumentation.http.server.enabled=false",
                    "reflex.telemetry.instrumentation.http.client.enabled=true",
                    "reflex.telemetry.instrumentation.jdbc.enabled=false",
                    "reflex.telemetry.instrumentation.kafka.enabled=true")
            .run(context -> {
                ReflexTelemetryProperties properties = context.getBean(ReflexTelemetryProperties.class);

                assertThat(properties.getInstrumentation().isEnabled()).isTrue();
                assertThat(properties.getInstrumentation().getHttp().getServer().isEnabled()).isFalse();
                assertThat(properties.getInstrumentation().getHttp().getClient().isEnabled()).isTrue();
                assertThat(properties.getInstrumentation().getJdbc().isEnabled()).isFalse();
                assertThat(properties.getInstrumentation().getKafka().isEnabled()).isTrue();
            });
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryAutoConfigurationTest#shouldBindManagedInstrumentationProperties test
```

Expected: compilation fails because `getInstrumentation()` does not exist.

- [ ] **Step 3: Add properties**

In `ReflexTelemetryProperties`, add:

```java
private InstrumentationProperties instrumentation = new InstrumentationProperties();

public void setInstrumentation(InstrumentationProperties instrumentation) {
    this.instrumentation = instrumentation != null ? instrumentation : new InstrumentationProperties();
}
```

Add nested classes:

```java
@Getter
@Setter
public static class InstrumentationProperties {

    private boolean enabled = false;
    private HttpInstrumentationProperties http = new HttpInstrumentationProperties();
    private ToggleProperties jdbc = new ToggleProperties();
    private ToggleProperties kafka = new ToggleProperties();

    public void setHttp(HttpInstrumentationProperties http) {
        this.http = http != null ? http : new HttpInstrumentationProperties();
    }

    public void setJdbc(ToggleProperties jdbc) {
        this.jdbc = jdbc != null ? jdbc : new ToggleProperties();
    }

    public void setKafka(ToggleProperties kafka) {
        this.kafka = kafka != null ? kafka : new ToggleProperties();
    }
}

@Getter
@Setter
public static class HttpInstrumentationProperties {

    private ToggleProperties server = new ToggleProperties();
    private ToggleProperties client = new ToggleProperties();

    public void setServer(ToggleProperties server) {
        this.server = server != null ? server : new ToggleProperties();
    }

    public void setClient(ToggleProperties client) {
        this.client = client != null ? client : new ToggleProperties();
    }
}

@Getter
@Setter
public static class ToggleProperties {

    private boolean enabled = true;
}
```

Default `instrumentation.enabled=false` keeps this change non-invasive until standard instrumentation dependencies and wiring are added.

- [ ] **Step 4: Run binding test**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexTelemetryAutoConfigurationTest#shouldBindManagedInstrumentationProperties test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java
git commit -m "feat: add managed instrumentation properties"
```

---

### Task 5: Add OpenTelemetry Instrumentation Starter Dependencies

**Files:**
- Modify: `pom.xml`
- Test: `pom.xml`

- [ ] **Step 1: Add dependency management test by compile**

Run before editing:

```powershell
.\mvnw.cmd -DskipTests compile
```

Expected: PASS on the current project.

- [ ] **Step 2: Add instrumentation BOM property**

In `pom.xml` properties, add:

```xml
<opentelemetry.instrumentation.version>2.27.0</opentelemetry.instrumentation.version>
```

- [ ] **Step 3: Import instrumentation BOM before Spring Boot BOM**

In `dependencyManagement.dependencies`, place this before `spring-boot-dependencies`:

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-instrumentation-bom</artifactId>
    <version>${opentelemetry.instrumentation.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Keep the existing `io.opentelemetry:opentelemetry-bom` import.

- [ ] **Step 4: Add optional starter dependency**

Add:

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <optional>true</optional>
</dependency>
```

Use `optional=true` so this library can expose managed integration without forcing standard instrumentation into every consumer immediately.

- [ ] **Step 5: Compile**

Run:

```powershell
.\mvnw.cmd -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add pom.xml
git commit -m "build: add optional otel spring instrumentation starter"
```

---

### Task 6: Prefix Standard Instrumentation Metrics Through SDK Views

**Files:**
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurer.java`
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurerTest.java`

- [ ] **Step 1: Write metric view configurer test**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurerTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.otel;

import static org.assertj.core.api.Assertions.assertThatCode;

import ru.sber.rcln.reflex.telemetry.config.TelemetryNamingPolicy;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import org.junit.jupiter.api.Test;

class MetricViewConfigurerTest {

    @Test
    void shouldRegisterPrefixingViewsWithoutFailingProviderBuild() {
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
        MetricViewConfigurer configurer = new MetricViewConfigurer(new TelemetryNamingPolicy("ci05414726"));

        configurer.configure(builder);

        assertThatCode(builder::build).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=MetricViewConfigurerTest test
```

Expected: compilation fails because `MetricViewConfigurer` does not exist.

- [ ] **Step 3: Add metric view configurer**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurer.java`:

```java
package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.config.TelemetryNamingPolicy;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricViewConfigurer {

    private final @NonNull TelemetryNamingPolicy namingPolicy;

    public void configure(@NonNull SdkMeterProviderBuilder builder) {
        registerPrefixingView(builder, InstrumentType.COUNTER);
        registerPrefixingView(builder, InstrumentType.UP_DOWN_COUNTER);
        registerPrefixingView(builder, InstrumentType.HISTOGRAM);
        registerPrefixingView(builder, InstrumentType.OBSERVABLE_COUNTER);
        registerPrefixingView(builder, InstrumentType.OBSERVABLE_UP_DOWN_COUNTER);
        registerPrefixingView(builder, InstrumentType.OBSERVABLE_GAUGE);
    }

    private void registerPrefixingView(SdkMeterProviderBuilder builder, InstrumentType type) {
        builder.registerView(
                InstrumentSelector.builder()
                        .setType(type)
                        .setName("*")
                        .build(),
                View.builder()
                        .setNameTemplate(namingPolicy.metricName("%s"))
                        .build());
    }
}
```

If `View.Builder#setNameTemplate` is unavailable in the pinned OTel SDK version, replace this task with an explicit OTel SDK version bump task; do not implement ad hoc name rewriting outside SDK views.

- [ ] **Step 4: Wire configurer into SDK meter provider**

In `ReflexTelemetryAutoConfiguration`, add bean:

```java
@Bean
@ConditionalOnMissingBean
MetricViewConfigurer metricViewConfigurer(TelemetryNamingPolicy namingPolicy) {
    return new MetricViewConfigurer(namingPolicy);
}
```

Change `sdkMeterProvider` signature:

```java
SdkMeterProvider sdkMeterProvider(
        OtlpGrpcMetricExporter exporter,
        ReflexTelemetryProperties properties,
        TelemetryNamingPolicy namingPolicy,
        MetricViewConfigurer metricViewConfigurer) {
```

Before `registerMetricReader`, call:

```java
metricViewConfigurer.configure(builder);
```

- [ ] **Step 5: Run tests**

Run:

```powershell
.\mvnw.cmd -Dtest=MetricViewConfigurerTest,ReflexTelemetryAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurer.java src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java src/test/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurerTest.java
git commit -m "feat: prefix sdk metric streams with system code"
```

---

### Task 7: Document Unified Configuration

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
    instrumentation:
      enabled: false
      http:
        server:
          enabled: true
        client:
          enabled: true
      jdbc:
        enabled: true
      kafka:
        enabled: true
```

- [ ] **Step 2: Update property list**

Remove `metric-prefix` from the list.

Add:

```markdown
- `system-code`
- `instrumentation.enabled`
- `instrumentation.http.server.enabled`
- `instrumentation.http.client.enabled`
- `instrumentation.jdbc.enabled`
- `instrumentation.kafka.enabled`
```

- [ ] **Step 3: Add naming policy section**

Add after `Service name`:

```markdown
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
```

- [ ] **Step 4: Document managed instrumentation boundary**

Add:

```markdown
## Managed standard instrumentation

`reflex.telemetry.instrumentation.*` is the public control surface for standard HTTP/JDBC/Kafka instrumentation. Applications should not need to configure `otel.*` properties directly for endpoints, service identity, or metric prefixes.

When managed instrumentation is enabled, standard OTel spans and metrics use the same SDK and OTLP endpoints as Reflex business metrics:

- standard traces use `reflex.telemetry.otlp.traces-endpoint`
- standard metrics use `reflex.telemetry.otlp.metrics-endpoint`
- standard metric names are prefixed with `reflex.telemetry.system-code`
- span names are not prefixed; `service.name` carries the system code
```

- [ ] **Step 5: Run docs-adjacent tests**

Run:

```powershell
.\mvnw.cmd -Dtest=TelemetryNamingPolicyTest,MetricConfigResolverTest,ManualMetricConfigResolverTest,ReflexTelemetryAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add README.md
git commit -m "docs: document system code telemetry configuration"
```

---

### Task 8: Full Verification

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

- Spec coverage: the plan covers one public `system-code`, current JDBC/manual metric names, OTel `service.name`, centralized `reflex.telemetry.*` configuration, standard instrumentation toggles, and shared OTLP endpoints/SDK.
- Scope boundary: full HTTP/JDBC/Kafka behavior validation may require sample applications or integration tests in a follow-up plan after dependency wiring is confirmed. This plan creates the central contract and SDK-level naming/routing hooks first.
- Ambiguity removed: `metrics.metric-prefix` and any trace-specific prefix property are removed rather than deprecated.
- Risk: OTel SDK view API must support name templating in the pinned SDK. If not, bump the OTel SDK/BOM intentionally instead of implementing name rewriting outside OTel SDK views.
