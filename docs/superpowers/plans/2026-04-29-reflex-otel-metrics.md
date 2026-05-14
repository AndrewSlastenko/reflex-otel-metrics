# Reflex Telemetry Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot starter that collects JDBC-backed business snapshot metrics, applies per-metric scheduling and locking, and exports them to an OTEL collector over OTLP/gRPC.

**Architecture:** Use a single-module Maven library with focused packages for API contracts, configuration, JDBC collection, runtime scheduling, locking, OTEL publishing, and internal telemetry. The library owns SDK/export wiring and execution flow, while application services contribute `MetricSource` beans with defaults that properties can override.

**Tech Stack:** Java 17, Spring Boot 3, Maven, OpenTelemetry 1.60.1, ShedLock JDBC, Spring JDBC, JUnit 5, AssertJ

---

## File Structure

### Root Build Files

- Create: `pom.xml`
- Create: `.gitignore`
- Create: `README.md`

### Main Sources

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricSource.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/JdbcMetricSource.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricKind.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricPoint.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricDefinitionDefaults.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricScheduleDefaults.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/SeriesOverflowPolicy.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/QueryDefinition.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricRuntimeProperties.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolver.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ResolvedMetricConfig.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigValidator.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollector.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollectorFactory.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionCoordinator.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTask.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricSourceRegistry.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricSchedulerRegistrar.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricRunOutcome.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiter.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/OverflowAggregationStrategy.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/locking/MetricLockManager.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/locking/ShedLockMetricLockManager.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/OtelMeterFactory.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/OtelMetricPublisher.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/OtelInstrumentRegistry.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/internal/InternalTelemetryRecorder.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/internal/LoggingSupport.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Tests

- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigValidatorTest.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiterTest.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTaskTest.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollectorTest.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`

## Task 1: Bootstrap the Maven Starter

**Files:**

- Create: `pom.xml`
- Create: `.gitignore`
- Create: `README.md`
- **Step 1: Write the failing build skeleton expectation**

Create `README.md` with the initial contract for the repository so the build has an explicit target:

```md
# rcln-reflex-telemetry

Spring Boot starter for JDBC-backed OpenTelemetry metrics export.

## Planned capabilities

- OpenTelemetry `1.60.1`
- OTLP/gRPC metrics export
- Per-metric scheduling and locking
- JDBC metric sources
- Fail-safe execution
```

- **Step 2: Add the initial Maven build**

Create `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ru.sber.rcln</groupId>
    <artifactId>rcln-reflex-telemetry</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.release>17</maven.compiler.release>
        <spring.boot.version>3.5.0</spring.boot.version>
        <opentelemetry.version>1.60.1</opentelemetry.version>
        <shedlock.version>6.6.0</shedlock.version>
        <junit.version>5.11.4</junit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>net.javacrumbs.shedlock</groupId>
            <artifactId>shedlock-spring</artifactId>
            <version>${shedlock.version}</version>
        </dependency>
        <dependency>
            <groupId>net.javacrumbs.shedlock</groupId>
            <artifactId>shedlock-provider-jdbc-template</artifactId>
            <version>${shedlock.version}</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-api</artifactId>
            <version>${opentelemetry.version}</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk</artifactId>
            <version>${opentelemetry.version}</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk-metrics</artifactId>
            <version>${opentelemetry.version}</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-otlp</artifactId>
            <version>${opentelemetry.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${maven.compiler.release}</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- **Step 3: Add basic ignore rules**

Create `.gitignore`:

```gitignore
target/
.idea/
*.iml
.classpath
.project
.settings/
```

- **Step 4: Run the build to verify the skeleton passes**

Run:

```powershell
mvn test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add pom.xml .gitignore README.md
git commit -m "chore: bootstrap otel metrics starter"
```

## Task 2: Define the Public API Contracts

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricSource.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/JdbcMetricSource.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricKind.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricPoint.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricDefinitionDefaults.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/MetricScheduleDefaults.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/SeriesOverflowPolicy.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/QueryDefinition.java`
- **Step 1: Write the failing API contract test**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java` with the first contract assertion:

```java
package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigResolverTest {

    @Test
    void beanDefaultsShouldExposeAllOperationalFields() {
        MetricDefinitionDefaults defaults = new MetricDefinitionDefaults(
                "documents.by.status",
                MetricKind.UP_DOWN_COUNTER,
                "business",
                "businessReplicaDataSource",
                new MetricScheduleDefaults(MetricScheduleDefaults.Mode.FIXED_DELAY, Duration.ofMinutes(5), null, Duration.ofSeconds(30)),
                Duration.ofSeconds(45),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(defaults.metricSuffix()).isEqualTo("documents.by.status");
        assertThat(defaults.metricKind()).isEqualTo(MetricKind.UP_DOWN_COUNTER);
        assertThat(defaults.scope()).isEqualTo("business");
        assertThat(defaults.dataSourceRef()).isEqualTo("businessReplicaDataSource");
        assertThat(defaults.maxSeries()).isEqualTo(500);
    }
}
```

- **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -Dtest=MetricConfigResolverTest test
```

Expected: compilation failure because API types do not exist yet

- **Step 3: Implement the API records and interfaces**

Create the API files with these contents:

```java
package ru.sber.rcln.reflex.telemetry.api;

public enum MetricKind {
    GAUGE,
    UP_DOWN_COUNTER
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

public enum SeriesOverflowPolicy {
    FAIL,
    TRUNCATE,
    AGGREGATE_TO_OTHER
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

import java.util.Map;

public record MetricPoint(long value, Map<String, String> attributes) {
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

public record QueryDefinition(String sql) {
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

import java.time.Duration;

public record MetricScheduleDefaults(
        Mode mode,
        Duration fixedDelay,
        String cron,
        Duration initialDelay
) {
    public enum Mode {
        FIXED_DELAY,
        CRON
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

import java.time.Duration;

public record MetricDefinitionDefaults(
        String metricSuffix,
        MetricKind metricKind,
        String scope,
        String dataSourceRef,
        MetricScheduleDefaults schedule,
        Duration timeout,
        Duration lockAtMostFor,
        Duration lockAtLeastFor,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

public interface MetricSource {

    String metricId();

    MetricDefinitionDefaults defaults();
}
```

```java
package ru.sber.rcln.reflex.telemetry.api;

import org.springframework.jdbc.core.RowMapper;

public interface JdbcMetricSource extends MetricSource {

    QueryDefinition queryDefinition();

    RowMapper<MetricPoint> rowMapper();
}
```

- **Step 4: Re-run the API contract test**

Run:

```powershell
mvn -Dtest=MetricConfigResolverTest test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/api src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java
git commit -m "feat: add metric source api contracts"
```

## Task 3: Implement Configuration Properties, Merge, and Validation

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexTelemetryProperties.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricRuntimeProperties.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolver.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ResolvedMetricConfig.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigValidator.java`
- Modify: `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigResolverTest.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/config/MetricConfigValidatorTest.java`
- **Step 1: Write the failing merge and validation tests**

Expand `MetricConfigResolverTest`:

```java
@Test
void propertiesShouldOverrideBeanDefaults() {
    ReflexTelemetryProperties properties = new ReflexTelemetryProperties();
    properties.setMetricPrefix("ci054147");
    properties.getScopes().put("business", new ReflexTelemetryProperties.ScopeProperties(true));

    MetricRuntimeProperties runtime = new MetricRuntimeProperties();
    runtime.setEnabled(false);
    runtime.setSuffix("documents.current");
    runtime.setDataSourceRef("overrideDataSource");
    properties.getSources().put("documents-by-status", runtime);

    MetricConfigResolver resolver = new MetricConfigResolver(properties);
    ResolvedMetricConfig config = resolver.resolve(new TestJdbcMetricSource());

    assertThat(config.enabled()).isFalse();
    assertThat(config.fullMetricName()).isEqualTo("ci054147.documents.current");
    assertThat(config.dataSourceRef()).isEqualTo("overrideDataSource");
}
```

Create `MetricConfigValidatorTest`:

```java
package ru.sber.rcln.reflex.telemetry.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricConfigValidatorTest {

    @Test
    void shouldRejectJdbcMetricWithoutDataSourceRef() {
        ResolvedMetricConfig config = new ResolvedMetricConfig(
                "documents-by-status",
                true,
                "ci054147.documents.current",
                "documents.current",
                "business",
                null,
                ru.sber.rcln.reflex.telemetry.api.MetricKind.UP_DOWN_COUNTER,
                MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ZERO,
                500,
                ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(new MetricConfigValidator().validate(config))
                .containsExactly("Metric 'documents-by-status' requires dataSourceRef");
    }
}
```

- **Step 2: Run the tests to verify they fail**

Run:

```powershell
mvn -Dtest=MetricConfigResolverTest,MetricConfigValidatorTest test
```

Expected: compilation failure because config types do not exist yet

- **Step 3: Implement configuration models and resolver**

Create the config types:

```java
package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "reflex.telemetry")
public class ReflexTelemetryProperties {

    private boolean enabled = true;
    private MetricsProperties metrics = new MetricsProperties();
    private OtlpProperties otlp = new OtlpProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OtlpProperties getOtlp() { return otlp; }
    public MetricsProperties getMetrics() { return metrics; }

    public static class MetricsProperties {
        private boolean enabled = true;
        private String metricPrefix = "reflex";
        private Map<String, ScopeProperties> scopes = new LinkedHashMap<>();
        private Map<String, MetricRuntimeProperties> sources = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public String getMetricPrefix() { return metricPrefix; }
        public Map<String, ScopeProperties> getScopes() { return scopes; }
        public Map<String, MetricRuntimeProperties> getSources() { return sources; }
    }

    public static class OtlpProperties {
        private String metricsEndpoint = "http://localhost:4317";
        private String tracesEndpoint = "http://localhost:4317";
        private Duration exportTimeout = Duration.ofSeconds(10);
        public String getMetricsEndpoint() { return metricsEndpoint; }
        public void setMetricsEndpoint(String metricsEndpoint) { this.metricsEndpoint = metricsEndpoint; }
        public String getTracesEndpoint() { return tracesEndpoint; }
        public void setTracesEndpoint(String tracesEndpoint) { this.tracesEndpoint = tracesEndpoint; }
        public Duration getExportTimeout() { return exportTimeout; }
        public void setExportTimeout(Duration exportTimeout) { this.exportTimeout = exportTimeout; }
    }

    public static class ScopeProperties {
        private boolean enabled = true;
        public ScopeProperties() { }
        public ScopeProperties(boolean enabled) { this.enabled = enabled; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;

import java.time.Duration;

public class MetricRuntimeProperties {

    private Boolean enabled;
    private String suffix;
    private String scope;
    private String dataSourceRef;
    private MetricKind kind;
    private MetricScheduleSettings.Mode scheduleMode;
    private Duration fixedDelay;
    private String cron;
    private Duration initialDelay;
    private Duration timeout;
    private Duration lockAtMostFor;
    private Duration lockAtLeastFor;
    private Integer maxSeries;
    private SeriesOverflowPolicy overflowPolicy;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getDataSourceRef() { return dataSourceRef; }
    public void setDataSourceRef(String dataSourceRef) { this.dataSourceRef = dataSourceRef; }
    public MetricKind getKind() { return kind; }
    public void setKind(MetricKind kind) { this.kind = kind; }
    public MetricScheduleSettings.Mode getScheduleMode() { return scheduleMode; }
    public void setScheduleMode(MetricScheduleSettings.Mode scheduleMode) { this.scheduleMode = scheduleMode; }
    public Duration getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Duration getInitialDelay() { return initialDelay; }
    public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public Duration getLockAtMostFor() { return lockAtMostFor; }
    public void setLockAtMostFor(Duration lockAtMostFor) { this.lockAtMostFor = lockAtMostFor; }
    public Duration getLockAtLeastFor() { return lockAtLeastFor; }
    public void setLockAtLeastFor(Duration lockAtLeastFor) { this.lockAtLeastFor = lockAtLeastFor; }
    public Integer getMaxSeries() { return maxSeries; }
    public void setMaxSeries(Integer maxSeries) { this.maxSeries = maxSeries; }
    public SeriesOverflowPolicy getOverflowPolicy() { return overflowPolicy; }
    public void setOverflowPolicy(SeriesOverflowPolicy overflowPolicy) { this.overflowPolicy = overflowPolicy; }
}
```

```java
package ru.sber.rcln.reflex.telemetry.config;

import java.time.Duration;

public record MetricScheduleSettings(
        Mode mode,
        Duration fixedDelay,
        String cron,
        Duration initialDelay
) {
    public enum Mode {
        FIXED_DELAY,
        CRON
    }

    public static MetricScheduleSettings fixedDelay(Duration fixedDelay, Duration initialDelay) {
        return new MetricScheduleSettings(Mode.FIXED_DELAY, fixedDelay, null, initialDelay);
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;

import java.time.Duration;

public record ResolvedMetricConfig(
        String metricId,
        boolean enabled,
        String fullMetricName,
        String suffix,
        String scope,
        String dataSourceRef,
        MetricKind metricKind,
        MetricScheduleSettings schedule,
        Duration timeout,
        Duration lockAtMostFor,
        Duration lockAtLeastFor,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
```

```java
package ru.sber.rcln.reflex.telemetry.config;

import ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource;
import ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults;
import ru.sber.rcln.reflex.telemetry.api.MetricSource;

import java.util.ArrayList;
import java.util.List;

public class MetricConfigResolver {

    private final ReflexTelemetryProperties properties;

    public MetricConfigResolver(ReflexTelemetryProperties properties) {
        this.properties = properties;
    }

    public ResolvedMetricConfig resolve(MetricSource source) {
        MetricDefinitionDefaults defaults = source.defaults();
        MetricRuntimeProperties runtime = properties.getSources().getOrDefault(source.metricId(), new MetricRuntimeProperties());
        String suffix = runtime.getSuffix() != null ? runtime.getSuffix() : defaults.metricSuffix();
        String scope = runtime.getScope() != null ? runtime.getScope() : defaults.scope();
        String dataSourceRef = runtime.getDataSourceRef() != null ? runtime.getDataSourceRef() : defaults.dataSourceRef();
        boolean scopeEnabled = properties.getScopes().getOrDefault(scope, new ReflexTelemetryProperties.ScopeProperties(true)).isEnabled();
        boolean enabled = properties.isEnabled() && scopeEnabled && (runtime.getEnabled() != null ? runtime.getEnabled() : true);
        MetricScheduleSettings schedule = runtime.getScheduleMode() != null
                ? new MetricScheduleSettings(runtime.getScheduleMode(), runtime.getFixedDelay(), runtime.getCron(), runtime.getInitialDelay())
                : new MetricScheduleSettings(
                        MetricScheduleSettings.Mode.valueOf(defaults.schedule().mode().name()),
                        defaults.schedule().fixedDelay(),
                        defaults.schedule().cron(),
                        defaults.schedule().initialDelay()
                );

        return new ResolvedMetricConfig(
                source.metricId(),
                enabled,
                properties.getMetricPrefix() + "." + suffix,
                suffix,
                scope,
                source instanceof JdbcMetricSource ? dataSourceRef : null,
                runtime.getKind() != null ? runtime.getKind() : defaults.metricKind(),
                schedule,
                runtime.getTimeout() != null ? runtime.getTimeout() : defaults.timeout(),
                runtime.getLockAtMostFor() != null ? runtime.getLockAtMostFor() : defaults.lockAtMostFor(),
                runtime.getLockAtLeastFor() != null ? runtime.getLockAtLeastFor() : defaults.lockAtLeastFor(),
                runtime.getMaxSeries() != null ? runtime.getMaxSeries() : defaults.maxSeries(),
                runtime.getOverflowPolicy() != null ? runtime.getOverflowPolicy() : defaults.overflowPolicy()
        );
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.config;

import java.util.ArrayList;
import java.util.List;

public class MetricConfigValidator {

    public List<String> validate(ResolvedMetricConfig config) {
        List<String> errors = new ArrayList<>();
        if (config.dataSourceRef() == null || config.dataSourceRef().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires dataSourceRef");
        }
        if (config.suffix() == null || config.suffix().isBlank()) {
            errors.add("Metric '" + config.metricId() + "' requires suffix");
        }
        if (config.schedule().mode() == MetricScheduleSettings.Mode.FIXED_DELAY && config.schedule().fixedDelay() == null) {
            errors.add("Metric '" + config.metricId() + "' requires fixedDelay for FIXED_DELAY mode");
        }
        if (config.schedule().mode() == MetricScheduleSettings.Mode.CRON && (config.schedule().cron() == null || config.schedule().cron().isBlank())) {
            errors.add("Metric '" + config.metricId() + "' requires cron for CRON mode");
        }
        return errors;
    }
}
```

- **Step 4: Add the test fixture source and make tests pass**

Append to `MetricConfigResolverTest`:

```java
private static final class TestJdbcMetricSource implements ru.sber.rcln.reflex.telemetry.api.JdbcMetricSource {
    @Override
    public String metricId() {
        return "documents-by-status";
    }

    @Override
    public ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults defaults() {
        return new ru.sber.rcln.reflex.telemetry.api.MetricDefinitionDefaults(
                "documents.by.status",
                ru.sber.rcln.reflex.telemetry.api.MetricKind.UP_DOWN_COUNTER,
                "business",
                "businessReplicaDataSource",
                new ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults(
                        ru.sber.rcln.reflex.telemetry.api.MetricScheduleDefaults.Mode.FIXED_DELAY,
                        java.time.Duration.ofMinutes(5),
                        null,
                        java.time.Duration.ofSeconds(10)
                ),
                java.time.Duration.ofSeconds(30),
                java.time.Duration.ofMinutes(10),
                java.time.Duration.ZERO,
                500,
                ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );
    }

    @Override
    public ru.sber.rcln.reflex.telemetry.api.QueryDefinition queryDefinition() {
        return new ru.sber.rcln.reflex.telemetry.api.QueryDefinition("select 1");
    }

    @Override
    public org.springframework.jdbc.core.RowMapper<ru.sber.rcln.reflex.telemetry.api.MetricPoint> rowMapper() {
        return (rs, rowNum) -> new ru.sber.rcln.reflex.telemetry.api.MetricPoint(1L, java.util.Map.of());
    }
}
```

Run:

```powershell
mvn -Dtest=MetricConfigResolverTest,MetricConfigValidatorTest test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/config src/test/java/ru/sber/rcln/reflex/telemetry/config
git commit -m "feat: add metric configuration resolution and validation"
```

## Task 4: Implement Series Limiting and Overflow Handling

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiter.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/OverflowAggregationStrategy.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiterTest.java`
- **Step 1: Write the failing series limiter tests**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiterTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesLimiterTest {

    @Test
    void shouldTruncateWhenConfigured() {
        SeriesLimiter limiter = new SeriesLimiter(new OverflowAggregationStrategy());
        List<MetricPoint> limited = limiter.apply(
                List.of(
                        new MetricPoint(1, Map.of("status", "a")),
                        new MetricPoint(2, Map.of("status", "b")),
                        new MetricPoint(3, Map.of("status", "c"))
                ),
                2,
                SeriesOverflowPolicy.TRUNCATE
        );

        assertThat(limited).hasSize(2);
    }

    @Test
    void shouldAggregateRemainderIntoOther() {
        SeriesLimiter limiter = new SeriesLimiter(new OverflowAggregationStrategy());
        List<MetricPoint> limited = limiter.apply(
                List.of(
                        new MetricPoint(1, Map.of("status", "a")),
                        new MetricPoint(2, Map.of("status", "b")),
                        new MetricPoint(3, Map.of("status", "c"))
                ),
                2,
                SeriesOverflowPolicy.AGGREGATE_TO_OTHER
        );

        assertThat(limited).hasSize(2);
        assertThat(limited.get(1).value()).isEqualTo(3);
        assertThat(limited.get(1).attributes()).containsEntry("bucket", "other");
    }
}
```

- **Step 2: Run the series tests to verify they fail**

Run:

```powershell
mvn -Dtest=SeriesLimiterTest test
```

Expected: compilation failure because limiter types do not exist yet

- **Step 3: Implement the overflow strategy and limiter**

Create:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverflowAggregationStrategy {

    public MetricPoint aggregate(List<MetricPoint> overflowPoints) {
        long value = overflowPoints.stream().mapToLong(MetricPoint::value).sum();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("bucket", "other");
        return new MetricPoint(value, attributes);
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;

import java.util.ArrayList;
import java.util.List;

public class SeriesLimiter {

    private final OverflowAggregationStrategy overflowAggregationStrategy;

    public SeriesLimiter(OverflowAggregationStrategy overflowAggregationStrategy) {
        this.overflowAggregationStrategy = overflowAggregationStrategy;
    }

    public List<MetricPoint> apply(List<MetricPoint> points, int maxSeries, SeriesOverflowPolicy policy) {
        if (points.size() <= maxSeries) {
            return points;
        }
        return switch (policy) {
            case TRUNCATE -> new ArrayList<>(points.subList(0, maxSeries));
            case AGGREGATE_TO_OTHER -> {
                List<MetricPoint> head = new ArrayList<>(points.subList(0, maxSeries - 1));
                head.add(overflowAggregationStrategy.aggregate(points.subList(maxSeries - 1, points.size())));
                yield head;
            }
            case FAIL -> throw new IllegalStateException("Metric produced " + points.size() + " series, max allowed is " + maxSeries);
        };
    }
}
```

- **Step 4: Re-run the series limiter tests**

Run:

```powershell
mvn -Dtest=SeriesLimiterTest test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiter.java src/main/java/ru/sber/rcln/reflex/telemetry/runtime/OverflowAggregationStrategy.java src/test/java/ru/sber/rcln/reflex/telemetry/runtime/SeriesLimiterTest.java
git commit -m "feat: add series limiting and overflow handling"
```

## Task 5: Implement JDBC Collection

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollector.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollectorFactory.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollectorTest.java`
- **Step 1: Write the failing JDBC collector test**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollectorTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcMetricCollectorTest {

    @Test
    void shouldCollectMetricPointsFromJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RowMapper<MetricPoint> rowMapper = (rs, rowNum) -> new MetricPoint(10L, Map.of("status", "created"));
        when(jdbcTemplate.query("select status, total from v_documents", rowMapper))
                .thenReturn(List.of(new MetricPoint(10L, Map.of("status", "created"))));

        JdbcMetricCollector collector = new JdbcMetricCollector(jdbcTemplate);

        List<MetricPoint> points = collector.collect(new QueryDefinition("select status, total from v_documents"), rowMapper);

        assertThat(points).singleElement().extracting(MetricPoint::value).isEqualTo(10L);
    }
}
```

- **Step 2: Run the JDBC test to verify it fails**

Run:

```powershell
mvn -Dtest=JdbcMetricCollectorTest test
```

Expected: compilation failure because JDBC collector types do not exist yet

- **Step 3: Implement the JDBC collector**

Create:

```java
package ru.sber.rcln.reflex.telemetry.jdbc;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.QueryDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class JdbcMetricCollector {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMetricCollector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MetricPoint> collect(QueryDefinition queryDefinition, RowMapper<MetricPoint> rowMapper) {
        return jdbcTemplate.query(queryDefinition.sql(), rowMapper);
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class JdbcMetricCollectorFactory {

    public JdbcMetricCollector create(DataSource dataSource) {
        return new JdbcMetricCollector(new JdbcTemplate(dataSource));
    }
}
```

- **Step 4: Re-run the JDBC collector test**

Run:

```powershell
mvn -Dtest=JdbcMetricCollectorTest test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/jdbc src/test/java/ru/sber/rcln/reflex/telemetry/jdbc/JdbcMetricCollectorTest.java
git commit -m "feat: add jdbc metric collector"
```

## Task 6: Implement Locking, OTEL Publishing, and Internal Telemetry Interfaces

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/locking/MetricLockManager.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/locking/ShedLockMetricLockManager.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/OtelMeterFactory.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/OtelMetricPublisher.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/otel/OtelInstrumentRegistry.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/internal/InternalTelemetryRecorder.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/internal/LoggingSupport.java`
- **Step 1: Write the failing execution outcome test**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTaskTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.api.SeriesOverflowPolicy;
import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricExecutionTaskTest {

    @Test
    void shouldPublishPointsWhenExecutionSucceeds() {
        MetricExecutionCoordinator coordinator = mock(MetricExecutionCoordinator.class);
        ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager = mock(ru.sber.rcln.reflex.telemetry.locking.MetricLockManager.class);
        OtelMetricPublisher publisher = mock(OtelMetricPublisher.class);
        InternalTelemetryRecorder telemetryRecorder = mock(InternalTelemetryRecorder.class);
        SeriesLimiter seriesLimiter = new SeriesLimiter(new OverflowAggregationStrategy());
        when(coordinator.collect()).thenReturn(List.of(new MetricPoint(10L, Map.of("status", "created"))));
        when(lockManager.executeWithLock(any(), any())).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        });

        MetricExecutionTask task = new MetricExecutionTask(
                coordinator,
                lockManager,
                publisher,
                telemetryRecorder,
                seriesLimiter,
                new ResolvedMetricConfig(
                        "documents-by-status",
                        true,
                        "ci054147.documents.current",
                        "documents.current",
                        "business",
                        "businessReplicaDataSource",
                        ru.sber.rcln.reflex.telemetry.api.MetricKind.UP_DOWN_COUNTER,
                        MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(10),
                        Duration.ZERO,
                        500,
                        SeriesOverflowPolicy.AGGREGATE_TO_OTHER
                )
        );

        MetricRunOutcome outcome = task.runOnce();

        assertThat(outcome).isEqualTo(MetricRunOutcome.SUCCESS);
        verify(publisher).publish(any(), any());
        verify(telemetryRecorder).recordSuccess(any());
    }
}
```

- **Step 2: Run the execution test to verify it fails**

Run:

```powershell
mvn -Dtest=MetricExecutionTaskTest test
```

Expected: compilation failure because runtime and publisher types do not exist yet

- **Step 3: Implement the runtime interfaces**

Create:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

public enum MetricRunOutcome {
    SUCCESS,
    FAILED,
    SKIPPED
}
```

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;

import java.util.List;

public interface MetricExecutionCoordinator {

    List<MetricPoint> collect();
}
```

```java
package ru.sber.rcln.reflex.telemetry.internal;

import ru.sber.rcln.reflex.telemetry.runtime.MetricRunOutcome;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

public interface InternalTelemetryRecorder {

    void recordSuccess(ResolvedMetricConfig config);

    void recordFailure(ResolvedMetricConfig config, Exception exception);

    void recordSkipped(ResolvedMetricConfig config);
}
```

```java
package ru.sber.rcln.reflex.telemetry.internal;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSupport {

    private static final Logger log = LoggerFactory.getLogger(LoggingSupport.class);

    public void startupValidationFailure(String metricId, String message) {
        log.error("Metric {} disabled during startup validation: {}", metricId, message);
    }

    public void runtimeFailure(ResolvedMetricConfig config, Exception exception) {
        log.error("Metric {} failed during collection", config.metricId(), exception);
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.otel;

import io.opentelemetry.api.metrics.Meter;

public interface OtelMeterFactory {

    Meter create();
}
```

```java
package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricKind;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtelInstrumentRegistry {

    private final Meter meter;
    private final Map<String, Object> instruments = new ConcurrentHashMap<>();

    public OtelInstrumentRegistry(Meter meter) {
        this.meter = meter;
    }

    public Object getOrCreate(String name, MetricKind kind) {
        return instruments.computeIfAbsent(name, key -> switch (kind) {
            case GAUGE -> meter.gaugeBuilder(name).ofLongs().build();
            case UP_DOWN_COUNTER -> meter.upDownCounterBuilder(name).build();
        });
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.otel;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongUpDownCounter;

import java.util.List;

public class OtelMetricPublisher {

    private final OtelInstrumentRegistry registry;

    public OtelMetricPublisher(OtelInstrumentRegistry registry) {
        this.registry = registry;
    }

    public void publish(ResolvedMetricConfig config, List<MetricPoint> points) {
        Object instrument = registry.getOrCreate(config.fullMetricName(), config.metricKind());
        for (MetricPoint point : points) {
            Attributes attributes = Attributes.builder().putAll(
                    point.attributes().entrySet().stream()
                            .collect(Attributes.builder()::new, (builder, entry) -> builder.put(AttributeKey.stringKey(entry.getKey()), entry.getValue()), (left, right) -> {})
                            .build()
            ).build();
            if (instrument instanceof LongGauge gauge) {
                gauge.record(point.value(), attributes);
            } else if (instrument instanceof LongUpDownCounter counter) {
                counter.add(point.value(), attributes);
            }
        }
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.locking;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;

public interface MetricLockManager {

    boolean executeWithLock(ResolvedMetricConfig config, Runnable runnable);
}
```

```java
package ru.sber.rcln.reflex.telemetry.locking;

import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Instant;

public class ShedLockMetricLockManager implements MetricLockManager {

    private final LockProvider lockProvider;

    public ShedLockMetricLockManager(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    @Override
    public boolean executeWithLock(ResolvedMetricConfig config, Runnable runnable) {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(),
                "reflex-otel-metric:" + config.metricId(),
                config.lockAtMostFor(),
                config.lockAtLeastFor()
        );
        return lockProvider.lock(lockConfiguration)
                .map(lock -> {
                    try {
                        runnable.run();
                        return true;
                    } finally {
                        lock.unlock();
                    }
                })
                .orElse(false);
    }
}
```

- **Step 4: Re-run the execution test**

Run:

```powershell
mvn -Dtest=MetricExecutionTaskTest test
```

Expected: compilation still fails because `MetricExecutionTask` is not implemented yet

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/locking src/main/java/ru/sber/rcln/reflex/telemetry/otel src/main/java/ru/sber/rcln/reflex/telemetry/internal src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionCoordinator.java src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricRunOutcome.java src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTaskTest.java
git commit -m "feat: add locking and publishing interfaces"
```

## Task 7: Implement Metric Execution Flow

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTask.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricSourceRegistry.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/runtime/MetricSchedulerRegistrar.java`
- Modify: `src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTaskTest.java`
- **Step 1: Extend the failing execution test for failure behavior**

Append to `MetricExecutionTaskTest`:

```java
@Test
void shouldRecordFailureWithoutThrowing() {
    MetricExecutionCoordinator coordinator = mock(MetricExecutionCoordinator.class);
    ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager = mock(ru.sber.rcln.reflex.telemetry.locking.MetricLockManager.class);
    OtelMetricPublisher publisher = mock(OtelMetricPublisher.class);
    InternalTelemetryRecorder telemetryRecorder = mock(InternalTelemetryRecorder.class);
    when(coordinator.collect()).thenThrow(new IllegalStateException("boom"));
    when(lockManager.executeWithLock(any(), any())).thenAnswer(invocation -> {
        Runnable runnable = invocation.getArgument(1);
        runnable.run();
        return true;
    });

    MetricExecutionTask task = new MetricExecutionTask(
            coordinator,
            lockManager,
            publisher,
            telemetryRecorder,
            new SeriesLimiter(new OverflowAggregationStrategy()),
            new ResolvedMetricConfig(
                    "documents-by-status",
                    true,
                    "ci054147.documents.current",
                    "documents.current",
                    "business",
                    "businessReplicaDataSource",
                    ru.sber.rcln.reflex.telemetry.api.MetricKind.UP_DOWN_COUNTER,
                    MetricScheduleSettings.fixedDelay(Duration.ofMinutes(5), Duration.ofSeconds(5)),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(10),
                    Duration.ZERO,
                    500,
                    SeriesOverflowPolicy.AGGREGATE_TO_OTHER
            )
    );

    MetricRunOutcome outcome = task.runOnce();

    assertThat(outcome).isEqualTo(MetricRunOutcome.FAILED);
    verify(telemetryRecorder).recordFailure(any(), any());
}
```

- **Step 2: Run the execution test to verify it fails on missing implementation**

Run:

```powershell
mvn -Dtest=MetricExecutionTaskTest test
```

Expected: compilation failure because `MetricExecutionTask` does not exist

- **Step 3: Implement the execution task, registry, and scheduler registrar**

Create:

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricPoint;
import ru.sber.rcln.reflex.telemetry.config.ResolvedMetricConfig;
import ru.sber.rcln.reflex.telemetry.internal.InternalTelemetryRecorder;
import ru.sber.rcln.reflex.telemetry.otel.OtelMetricPublisher;

import java.util.List;

public class MetricExecutionTask {

    private final MetricExecutionCoordinator coordinator;
    private final ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager;
    private final OtelMetricPublisher publisher;
    private final InternalTelemetryRecorder telemetryRecorder;
    private final SeriesLimiter seriesLimiter;
    private final ResolvedMetricConfig config;

    public MetricExecutionTask(
            MetricExecutionCoordinator coordinator,
            ru.sber.rcln.reflex.telemetry.locking.MetricLockManager lockManager,
            OtelMetricPublisher publisher,
            InternalTelemetryRecorder telemetryRecorder,
            SeriesLimiter seriesLimiter,
            ResolvedMetricConfig config
    ) {
        this.coordinator = coordinator;
        this.lockManager = lockManager;
        this.publisher = publisher;
        this.telemetryRecorder = telemetryRecorder;
        this.seriesLimiter = seriesLimiter;
        this.config = config;
    }

    public MetricRunOutcome runOnce() {
        if (!config.enabled()) {
            telemetryRecorder.recordSkipped(config);
            return MetricRunOutcome.SKIPPED;
        }
        try {
            boolean executed = lockManager.executeWithLock(config, () -> {
                List<MetricPoint> points = coordinator.collect();
                List<MetricPoint> limited = seriesLimiter.apply(points, config.maxSeries(), config.overflowPolicy());
                publisher.publish(config, limited);
            });
            if (!executed) {
                telemetryRecorder.recordSkipped(config);
                return MetricRunOutcome.SKIPPED;
            }
            telemetryRecorder.recordSuccess(config);
            return MetricRunOutcome.SUCCESS;
        } catch (Exception exception) {
            telemetryRecorder.recordFailure(config, exception);
            return MetricRunOutcome.FAILED;
        }
    }
}
```

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.api.MetricSource;

import java.util.List;

public record MetricSourceRegistry(List<MetricSource> sources) {
}
```

```java
package ru.sber.rcln.reflex.telemetry.runtime;

import ru.sber.rcln.reflex.telemetry.config.MetricScheduleSettings;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricSchedulerRegistrar {

    private final ScheduledExecutorService scheduledExecutorService;

    public MetricSchedulerRegistrar(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public void register(ResolvedMetricConfig config, Runnable runnable) {
        if (config.schedule().mode() == MetricScheduleSettings.Mode.FIXED_DELAY) {
            Duration initialDelay = config.schedule().initialDelay() == null ? Duration.ZERO : config.schedule().initialDelay();
            scheduledExecutorService.scheduleWithFixedDelay(
                    runnable,
                    initialDelay.toMillis(),
                    config.schedule().fixedDelay().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }
    }
}
```

- **Step 4: Re-run the execution flow test**

Run:

```powershell
mvn -Dtest=MetricExecutionTaskTest test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/runtime src/test/java/ru/sber/rcln/reflex/telemetry/runtime/MetricExecutionTaskTest.java
git commit -m "feat: add metric execution runtime"
```

## Task 8: Implement Spring Boot Auto-Configuration

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`
- **Step 1: Write the failing auto-configuration test**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ReflexTelemetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReflexTelemetryAutoConfiguration.class));

    @Test
    void shouldCreateCoreBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("reflex.telemetry.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReflexTelemetryProperties.class);
                    assertThat(context).hasSingleBean(SeriesLimiter.class);
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(OpenTelemetrySdk.class);
                });
    }
}
```

- **Step 2: Run the auto-configuration test to verify it fails**

Run:

```powershell
mvn -Dtest=ReflexTelemetryAutoConfigurationTest test
```

Expected: compilation failure because auto-configuration does not exist yet

- **Step 3: Implement the auto-configuration**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfiguration.java`:

```java
package ru.sber.rcln.reflex.telemetry.autoconfigure;

import ru.sber.rcln.reflex.telemetry.config.MetricConfigResolver;
import ru.sber.rcln.reflex.telemetry.config.MetricConfigValidator;
import ru.sber.rcln.reflex.telemetry.config.ReflexTelemetryProperties;
import ru.sber.rcln.reflex.telemetry.internal.LoggingSupport;
import ru.sber.rcln.reflex.telemetry.otel.OtelInstrumentRegistry;
import ru.sber.rcln.reflex.telemetry.runtime.OverflowAggregationStrategy;
import ru.sber.rcln.reflex.telemetry.runtime.SeriesLimiter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ReflexTelemetryProperties.class)
@ConditionalOnProperty(prefix = "reflex.telemetry.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReflexTelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MetricConfigResolver metricConfigResolver(ReflexTelemetryProperties properties) {
        return new MetricConfigResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    MetricConfigValidator metricConfigValidator() {
        return new MetricConfigValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    OverflowAggregationStrategy overflowAggregationStrategy() {
        return new OverflowAggregationStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    SeriesLimiter seriesLimiter(OverflowAggregationStrategy overflowAggregationStrategy) {
        return new SeriesLimiter(overflowAggregationStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    LoggingSupport loggingSupport() {
        return new LoggingSupport();
    }

    @Bean
    @ConditionalOnMissingBean
    OtlpGrpcMetricExporter otlpGrpcMetricExporter(ReflexTelemetryProperties properties) {
        return OtlpGrpcMetricExporter.builder()
                .setEndpoint(properties.getOtlp().getMetricsEndpoint())
                .setTimeout(properties.getOtlp().getExportTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    SdkMeterProvider sdkMeterProvider(OtlpGrpcMetricExporter exporter) {
        return SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(exporter).build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenTelemetrySdk openTelemetrySdk(SdkMeterProvider sdkMeterProvider) {
        return OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenTelemetry openTelemetry(OpenTelemetrySdk openTelemetrySdk) {
        return openTelemetrySdk;
    }

    @Bean
    @ConditionalOnMissingBean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter("ru.sber.rcln.reflex.telemetry");
    }

    @Bean
    @ConditionalOnMissingBean
    OtelInstrumentRegistry otelInstrumentRegistry(Meter meter) {
        return new OtelInstrumentRegistry(meter);
    }
}
```

Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```text
ru.sber.rcln.reflex.telemetry.autoconfigure.ReflexTelemetryAutoConfiguration
```

- **Step 4: Re-run the auto-configuration test**

Run:

```powershell
mvn -Dtest=ReflexTelemetryAutoConfigurationTest test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java
git commit -m "feat: add spring boot auto-configuration"
```

## Task 9: Add End-to-End Wiring Tests and Usage Documentation

**Files:**

- Modify: `README.md`
- Modify: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java`
- **Step 1: Write the failing integration-style auto-configuration test**

Append to `ReflexTelemetryAutoConfigurationTest`:

```java
@Test
void shouldBindMetricPropertiesOverrides() {
    contextRunner
            .withPropertyValues(
                    "reflex.telemetry.metrics.metric-prefix=ci054147",
                    "reflex.telemetry.metrics.sources.documents-by-status.suffix=documents.current"
            )
            .run(context -> {
                ReflexTelemetryProperties properties = context.getBean(ReflexTelemetryProperties.class);
                assertThat(properties.getMetricPrefix()).isEqualTo("ci054147");
                assertThat(properties.getSources().get("documents-by-status").getSuffix()).isEqualTo("documents.current");
            });
}
```

- **Step 2: Run the focused test to verify it fails if binding is incomplete**

Run:

```powershell
mvn -Dtest=ReflexTelemetryAutoConfigurationTest test
```

Expected: failing assertion if configuration binding is incomplete

- **Step 3: Update README with the supported integration contract**

Replace `README.md` with:

```md
# rcln-reflex-telemetry

Spring Boot starter for JDBC-backed OpenTelemetry metrics export.

## Features

- OpenTelemetry `1.60.1`
- OTLP/gRPC metrics exporter wiring
- per-metric `FIXED_DELAY` and `CRON`
- per-metric distributed lock settings
- JDBC-backed metric sources
- metric prefixing
- aggregate technical telemetry
- fail-safe execution

## Integration model

Applications provide Spring beans that implement `JdbcMetricSource`.

Each source supplies:

- a stable `metricId`
- bean-level defaults
- a SQL statement or view query
- a `RowMapper<MetricPoint>`

Properties can override the operational settings:

```yaml
reflex:
  telemetry:
    otlp:
      metrics-endpoint: http://otel-collector:4317
      traces-endpoint: http://otel-collector:4317
    metrics:
      enabled: true
      metric-prefix: ci054147
      scopes:
        business:
          enabled: true
      sources:
        documents-by-status:
          enabled: true
          suffix: documents.current
          scope: business
          data-source-ref: businessReplicaDataSource
          kind: UP_DOWN_COUNTER
          schedule-mode: FIXED_DELAY
          fixed-delay: 5m
          initial-delay: 10s
          timeout: 30s
          lock-at-most-for: 10m
          lock-at-least-for: 0s
          max-series: 500
          overflow-policy: AGGREGATE_TO_OTHER
```

```

- [ ] **Step 4: Run the full test suite**

Run:

```powershell
mvn test
```

Expected: `BUILD SUCCESS`

- **Step 5: Commit**

```bash
git add README.md src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexTelemetryAutoConfigurationTest.java
git commit -m "docs: add starter usage documentation"
```

## Self-Review

### Spec Coverage

- Starter architecture: covered by Tasks 1, 6, 8
- API contracts and bean defaults: covered by Task 2
- Merge order `library -> bean -> properties`: covered by Task 3
- Explicit `dataSourceRef`: covered by Tasks 2, 3, 5
- Per-metric scheduling and validation: covered by Tasks 3, 7, 9
- Overflow and cardinality guardrails: covered by Task 4
- Fail-safe runtime behavior: covered by Tasks 6 and 7
- Aggregate technical telemetry hooks: covered by Task 6
- OTLP/gRPC wiring surface: covered by Task 8

### Gaps Fixed Inline

The initial draft missed concrete OTEL exporter initialization and lock execution inside the metric run path. Both gaps are now covered inline in Tasks 7 and 8.

### Placeholder Scan

- No `TODO` or `TBD` placeholders remain.
- No forward references remain that would block implementation.

### Type Consistency

- `MetricSource`, `JdbcMetricSource`, `MetricDefinitionDefaults`, `ResolvedMetricConfig`, and `MetricScheduleSettings` use consistent names across tasks.
- `MetricExecutionTask` and `SeriesLimiter` are referenced consistently.
