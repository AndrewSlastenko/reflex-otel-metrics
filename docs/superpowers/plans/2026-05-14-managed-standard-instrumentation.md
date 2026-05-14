# Managed Standard Instrumentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional, centrally controlled HTTP/JDBC/Kafka OpenTelemetry instrumentation under `reflex.telemetry.instrumentation.*` without scattering user-facing configuration across `otel.*` properties.

**Architecture:** Keep `reflex.telemetry.*` as the public control surface. Add instrumentation dependencies and toggles only after the unified `system-code` naming plan has landed. Standard instrumentation writes to the same OpenTelemetry SDK and OTLP exporters as existing Reflex metrics/traces; standard metric streams are prefixed with `system-code` through SDK views, while span names remain semantic and `service.name` carries the system code.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Maven, OpenTelemetry API/SDK 1.60.x, OpenTelemetry Spring Boot starter/instrumentation BOM 2.x, JUnit 5, AssertJ, ApplicationContextRunner.

---

## Prerequisite

Complete `docs/superpowers/plans/2026-05-14-system-code-naming.md` first. This plan assumes:

- `ReflexTelemetryProperties.systemCode` exists;
- `ReflexTelemetryNamingPolicy` exists;
- current business metric names use `ReflexTelemetryNamingPolicy.metricName(...)`;
- SDK resource `service.name` uses `ReflexTelemetryNamingPolicy.serviceName(...)`;
- `reflex.telemetry.metrics.metric-prefix` has been removed.

## OneAgent Compatibility Decision

Managed standard instrumentation must remain opt-in:

```yaml
reflex:
  telemetry:
    instrumentation:
      enabled: false
```

When Dynatrace OneAgent is the primary APM on a JVM, the recommended configuration is to keep Reflex standard HTTP/JDBC/Kafka instrumentation disabled and use Reflex only for business metrics/manual metrics and explicitly coded spans. Running OneAgent and OTel standard instrumentation over the same HTTP/JDBC/Kafka technologies can create duplicate spans, additional overhead, or confusing trace graphs.

## File Structure

- Modify `pom.xml`: add OpenTelemetry instrumentation BOM and optional Spring Boot starter dependency.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`: add `instrumentation` property tree.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurer.java`: register SDK metric views that prefix standard instrumentation metric streams.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`: expose metric view configurer and apply it to `SdkMeterProvider`.
- Test `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`: property binding and bean wiring.
- Add `src/test/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurerTest.java`: SDK view configurer coverage.
- Modify `README.md`: document managed instrumentation as opt-in and OneAgent compatibility guidance.

---

### Task 1: Add Managed Instrumentation Configuration Contract

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

Default `instrumentation.enabled=false` keeps standard instrumentation disabled unless explicitly requested.

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

### Task 2: Add OpenTelemetry Instrumentation Starter Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Verify current compile**

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

Use `optional=true` so this library can expose the integration without forcing standard instrumentation into every consumer immediately.

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

### Task 3: Prefix Standard Instrumentation Metrics Through SDK Views

**Files:**
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurer.java`
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurerTest.java`

- [ ] **Step 1: Write metric view configurer test**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/otel/MetricViewConfigurerTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.otel;

import static org.assertj.core.api.Assertions.assertThatCode;

import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryNamingPolicy;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import org.junit.jupiter.api.Test;

class MetricViewConfigurerTest {

    @Test
    void shouldRegisterPrefixingViewsWithoutFailingProviderBuild() {
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
        MetricViewConfigurer configurer = new MetricViewConfigurer(new ReflexTelemetryNamingPolicy("ci05414726"));

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

import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryNamingPolicy;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricViewConfigurer {

    private final @NonNull ReflexTelemetryNamingPolicy namingPolicy;

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

If `View.Builder#setNameTemplate` is unavailable in the pinned OTel SDK version, replace this task with an explicit OTel SDK version bump task. Do not implement ad hoc name rewriting outside SDK views.

- [ ] **Step 4: Wire configurer into SDK meter provider**

In `ReflexTelemetryAutoConfiguration`, add bean:

```java
@Bean
@ConditionalOnMissingBean
MetricViewConfigurer metricViewConfigurer(ReflexTelemetryNamingPolicy namingPolicy) {
    return new MetricViewConfigurer(namingPolicy);
}
```

Change `sdkMeterProvider` signature:

```java
SdkMeterProvider sdkMeterProvider(
        OtlpGrpcMetricExporter exporter,
        ReflexTelemetryProperties properties,
        ReflexTelemetryNamingPolicy namingPolicy,
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

### Task 4: Document Managed Standard Instrumentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add managed instrumentation config example**

Add:

```yaml
reflex:
  telemetry:
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

- [ ] **Step 2: Add property list entries**

Add:

```markdown
- `instrumentation.enabled`
- `instrumentation.http.server.enabled`
- `instrumentation.http.client.enabled`
- `instrumentation.jdbc.enabled`
- `instrumentation.kafka.enabled`
```

- [ ] **Step 3: Add behavior section**

Add:

````markdown
## Managed standard instrumentation

`reflex.telemetry.instrumentation.*` is the public control surface for optional standard HTTP/JDBC/Kafka instrumentation. Applications should not need to configure `otel.*` properties directly for endpoints, service identity, or metric prefixes.

When managed instrumentation is enabled, standard OTel spans and metrics use the same SDK and OTLP endpoints as Reflex business metrics:

- standard traces use `reflex.telemetry.otlp.traces-endpoint`
- standard metrics use `reflex.telemetry.otlp.metrics-endpoint`
- standard metric names are prefixed with `reflex.telemetry.system-code`
- span names are not prefixed; `service.name` carries the system code

The default is disabled because environments that already run a vendor APM agent, such as Dynatrace OneAgent, may already instrument HTTP/JDBC/messaging. In those environments, keep standard instrumentation disabled unless the platform team explicitly approves dual instrumentation.
````

- [ ] **Step 4: Run docs-adjacent tests**

Run:

```powershell
.\mvnw.cmd -Dtest=MetricViewConfigurerTest,ReflexTelemetryAutoConfigurationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add README.md
git commit -m "docs: document managed standard instrumentation"
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

- [ ] **Step 2: Check configuration ownership references**

Run:

```powershell
rg -n "instrumentation|OneAgent|otel\\.\\*" README.md src pom.xml
```

Expected:

- README documents `reflex.telemetry.instrumentation.*`.
- README warns that OneAgent environments should keep standard instrumentation disabled unless approved.
- No README examples require users to configure `otel.*` for endpoints, service identity, or metric prefixes.

- [ ] **Step 3: Check git status**

Run:

```powershell
git status --short
```

Expected: no uncommitted changes.

---

## Self-Review

- Spec coverage: the plan covers opt-in HTTP/JDBC/Kafka instrumentation settings, same SDK/OTLP endpoints, metric prefixing via `system-code`, and OneAgent compatibility guidance.
- Scope boundary: this plan does not alter current business metric naming; that is handled by the prerequisite system-code naming plan.
- Risk: OTel SDK view API must support name templating in the pinned SDK. If not, bump the OTel SDK/BOM intentionally instead of implementing name rewriting outside SDK views.
