# Manual Metric Beans Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Spring bean based manual metrics created through `ReflexMetricFactory`, with validated attributes, optional YAML overrides, OTEL counter support, and README use cases.

**Architecture:** Keep JDBC polling intact while adding a separate manual metrics path. Shared OTEL instrument registration and naming are reused; manual metrics add per-call validation/cardinality and log-and-skip runtime behavior.

**Tech Stack:** Java 17, Spring Boot 3.5, OpenTelemetry API 1.60.1, Maven, JUnit 5, AssertJ, Mockito, ApplicationContextRunner.

---

## File Structure

- Modify `src/main/java/com/reflex/otelmetrics/api/MetricKind.java`: add `COUNTER`.
- Create `src/main/java/com/reflex/otelmetrics/api/MetricDefinition.java`: Java defaults for manual metrics.
- Create `src/main/java/com/reflex/otelmetrics/api/AttributesSchema.java`: required/optional/reject-unknown contract.
- Create `src/main/java/com/reflex/otelmetrics/api/CounterMetric.java`: public counter interface.
- Create `src/main/java/com/reflex/otelmetrics/api/GaugeMetric.java`: public gauge interface.
- Create `src/main/java/com/reflex/otelmetrics/api/UpDownCounterMetric.java`: public up-down-counter interface.
- Create `src/main/java/com/reflex/otelmetrics/config/ManualMetricRuntimeProperties.java`: YAML override model under `manual`.
- Modify `src/main/java/com/reflex/otelmetrics/config/ReflexOtelMetricsProperties.java`: add `manual` map.
- Create `src/main/java/com/reflex/otelmetrics/config/ResolvedManualMetricConfig.java`: immutable resolved manual config.
- Create `src/main/java/com/reflex/otelmetrics/config/ManualMetricConfigResolver.java`: Java defaults plus YAML overrides.
- Create `src/main/java/com/reflex/otelmetrics/manual/AttributeValidationResult.java`: validation result value object.
- Create `src/main/java/com/reflex/otelmetrics/manual/AttributeValidator.java`: per-call schema validation.
- Create `src/main/java/com/reflex/otelmetrics/manual/ManualSeriesTracker.java`: thread-safe cardinality guard.
- Create `src/main/java/com/reflex/otelmetrics/manual/ReflexMetricFactory.java`: factory for manual metric beans.
- Create `src/main/java/com/reflex/otelmetrics/manual/DefaultCounterMetric.java`: counter implementation.
- Create `src/main/java/com/reflex/otelmetrics/manual/DefaultGaugeMetric.java`: gauge implementation.
- Create `src/main/java/com/reflex/otelmetrics/manual/DefaultUpDownCounterMetric.java`: up-down-counter implementation.
- Modify `src/main/java/com/reflex/otelmetrics/otel/OtelInstrumentRegistry.java`: support `LongCounter`.
- Modify `src/main/java/com/reflex/otelmetrics/otel/OtelMetricPublisher.java`: handle `COUNTER` for existing publisher tests.
- Modify `src/main/java/com/reflex/otelmetrics/autoconfigure/ReflexOtelMetricsAutoConfiguration.java`: expose `ManualMetricConfigResolver` and `ReflexMetricFactory`.
- Modify `README.md`: document low-level beans and domain beans.
- Add tests under `src/test/java/com/reflex/otelmetrics/manual`, `config`, `otel`, and `autoconfigure`.

## Task 1: Add Manual API Value Types

**Files:**
- Modify: `src/main/java/com/reflex/otelmetrics/api/MetricKind.java`
- Create: `src/main/java/com/reflex/otelmetrics/api/AttributesSchema.java`
- Create: `src/main/java/com/reflex/otelmetrics/api/MetricDefinition.java`
- Create: `src/main/java/com/reflex/otelmetrics/api/CounterMetric.java`
- Create: `src/main/java/com/reflex/otelmetrics/api/GaugeMetric.java`
- Create: `src/main/java/com/reflex/otelmetrics/api/UpDownCounterMetric.java`
- Test: `src/test/java/com/reflex/otelmetrics/api/AttributesSchemaTest.java`
- Test: `src/test/java/com/reflex/otelmetrics/api/MetricDefinitionTest.java`

- [ ] **Step 1: Write failing API tests**

Create `src/test/java/com/reflex/otelmetrics/api/AttributesSchemaTest.java`:

```java
package com.reflex.otelmetrics.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributesSchemaTest {

    @Test
    void shouldBuildRequiredAndOptionalAttributesWithRejectUnknownByDefault() {
        AttributesSchema schema = AttributesSchema.builder()
                .required("client")
                .optional("region")
                .build();

        assertThat(schema.required()).containsExactly("client");
        assertThat(schema.optional()).containsExactly("region");
        assertThat(schema.rejectUnknown()).isTrue();
    }

    @Test
    void shouldRejectBlankAttributeNames() {
        assertThatThrownBy(() -> AttributesSchema.builder().required(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute name must not be blank");
    }

    @Test
    void shouldRejectDuplicateRequiredAndOptionalAttributes() {
        assertThatThrownBy(() -> AttributesSchema.builder()
                .required("client")
                .optional("client")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared as both required and optional");
    }
}
```

Create `src/test/java/com/reflex/otelmetrics/api/MetricDefinitionTest.java`:

```java
package com.reflex.otelmetrics.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricDefinitionTest {

    @Test
    void shouldCreateDefinitionWithDefaults() {
        MetricDefinition definition = MetricDefinition.of("orders.created");

        assertThat(definition.metricSuffix()).isEqualTo("orders.created");
        assertThat(definition.scope()).isEqualTo("default");
        assertThat(definition.description()).isNull();
        assertThat(definition.unit()).isNull();
        assertThat(definition.attributes()).isEqualTo(AttributesSchema.empty());
        assertThat(definition.maxSeries()).isEqualTo(500);
        assertThat(definition.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.FAIL);
    }

    @Test
    void shouldRejectBlankSuffix() {
        assertThatThrownBy(() -> MetricDefinition.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metricSuffix must not be blank");
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=AttributesSchemaTest,MetricDefinitionTest test
```

Expected: compilation fails because `AttributesSchema`, `MetricDefinition`, and `COUNTER` do not exist.

- [ ] **Step 3: Implement API types**

Update `src/main/java/com/reflex/otelmetrics/api/MetricKind.java`:

```java
package com.reflex.otelmetrics.api;

public enum MetricKind {
    COUNTER,
    GAUGE,
    UP_DOWN_COUNTER
}
```

Create `src/main/java/com/reflex/otelmetrics/api/AttributesSchema.java`:

```java
package com.reflex.otelmetrics.api;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record AttributesSchema(
        Set<String> required,
        Set<String> optional,
        boolean rejectUnknown
) {

    public AttributesSchema {
        required = Set.copyOf(Objects.requireNonNull(required, "required must not be null"));
        optional = Set.copyOf(Objects.requireNonNull(optional, "optional must not be null"));
        for (String name : required) {
            validateName(name);
        }
        for (String name : optional) {
            validateName(name);
            if (required.contains(name)) {
                throw new IllegalArgumentException("attribute '" + name + "' is declared as both required and optional");
            }
        }
    }

    public static AttributesSchema empty() {
        return new AttributesSchema(Set.of(), Set.of(), true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<String> allowed() {
        LinkedHashSet<String> allowed = new LinkedHashSet<>(required);
        allowed.addAll(optional);
        return Set.copyOf(allowed);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attribute name must not be blank");
        }
    }

    public static final class Builder {
        private final Set<String> required = new LinkedHashSet<>();
        private final Set<String> optional = new LinkedHashSet<>();
        private boolean rejectUnknown = true;

        public Builder required(String name) {
            validateName(name);
            required.add(name);
            return this;
        }

        public Builder optional(String name) {
            validateName(name);
            optional.add(name);
            return this;
        }

        public Builder rejectUnknown(boolean rejectUnknown) {
            this.rejectUnknown = rejectUnknown;
            return this;
        }

        public AttributesSchema build() {
            return new AttributesSchema(required, optional, rejectUnknown);
        }
    }
}
```

Create `src/main/java/com/reflex/otelmetrics/api/MetricDefinition.java`:

```java
package com.reflex.otelmetrics.api;

import java.util.Objects;

public record MetricDefinition(
        String metricSuffix,
        String scope,
        String description,
        String unit,
        AttributesSchema attributes,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {

    public MetricDefinition {
        if (metricSuffix == null || metricSuffix.isBlank()) {
            throw new IllegalArgumentException("metricSuffix must not be blank");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        attributes = Objects.requireNonNull(attributes, "attributes must not be null");
        if (maxSeries < 1) {
            throw new IllegalArgumentException("maxSeries must be greater than zero");
        }
        overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy must not be null");
    }

    public static Builder of(String metricSuffix) {
        return new Builder(metricSuffix);
    }

    public static final class Builder {
        private final String metricSuffix;
        private String scope = "default";
        private String description;
        private String unit;
        private AttributesSchema attributes = AttributesSchema.empty();
        private int maxSeries = 500;
        private SeriesOverflowPolicy overflowPolicy = SeriesOverflowPolicy.FAIL;

        private Builder(String metricSuffix) {
            this.metricSuffix = metricSuffix;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder attributes(AttributesSchema attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder maxSeries(int maxSeries) {
            this.maxSeries = maxSeries;
            return this;
        }

        public Builder overflowPolicy(SeriesOverflowPolicy overflowPolicy) {
            this.overflowPolicy = overflowPolicy;
            return this;
        }

        public MetricDefinition build() {
            return new MetricDefinition(metricSuffix, scope, description, unit, attributes, maxSeries, overflowPolicy);
        }
    }
}
```

Create metric interfaces:

```java
package com.reflex.otelmetrics.api;

import java.util.Map;

public interface CounterMetric {
    void add(long value, Map<String, String> attributes);

    default void increment(Map<String, String> attributes) {
        add(1L, attributes);
    }

    default void add(long value) {
        add(value, Map.of());
    }

    default void increment() {
        increment(Map.of());
    }
}
```

```java
package com.reflex.otelmetrics.api;

import java.util.Map;

public interface GaugeMetric {
    void set(long value, Map<String, String> attributes);

    default void set(long value) {
        set(value, Map.of());
    }
}
```

```java
package com.reflex.otelmetrics.api;

import java.util.Map;

public interface UpDownCounterMetric {
    void add(long value, Map<String, String> attributes);

    default void add(long value) {
        add(value, Map.of());
    }
}
```

- [ ] **Step 4: Run API tests**

Run:

```powershell
.\mvnw.cmd -Dtest=AttributesSchemaTest,MetricDefinitionTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/reflex/otelmetrics/api src/test/java/com/reflex/otelmetrics/api
git commit -m "feat: add manual metric API types"
```

## Task 2: Add Manual Configuration Resolution

**Files:**
- Create: `src/main/java/com/reflex/otelmetrics/config/ManualMetricRuntimeProperties.java`
- Modify: `src/main/java/com/reflex/otelmetrics/config/ReflexOtelMetricsProperties.java`
- Create: `src/main/java/com/reflex/otelmetrics/config/ResolvedManualMetricConfig.java`
- Create: `src/main/java/com/reflex/otelmetrics/config/ManualMetricConfigResolver.java`
- Test: `src/test/java/com/reflex/otelmetrics/config/ManualMetricConfigResolverTest.java`

- [ ] **Step 1: Write failing resolver tests**

Create `src/test/java/com/reflex/otelmetrics/config/ManualMetricConfigResolverTest.java`:

```java
package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualMetricConfigResolverTest {

    @Test
    void shouldResolveFromJavaDefinitionWhenYamlIsMissing() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
        ManualMetricConfigResolver resolver = new ManualMetricConfigResolver(properties);

        ResolvedManualMetricConfig resolved = resolver.resolve(
                "orders-created",
                MetricKind.COUNTER,
                MetricDefinition.of("orders.created")
                        .scope("business")
                        .description("Created orders")
                        .unit("1")
                        .maxSeries(25)
                        .overflowPolicy(SeriesOverflowPolicy.FAIL)
                        .build());

        assertThat(resolved.metricId()).isEqualTo("orders-created");
        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.fullMetricName()).isEqualTo("ci054147.orders.created");
        assertThat(resolved.scope()).isEqualTo("business");
        assertThat(resolved.metricKind()).isEqualTo(MetricKind.COUNTER);
        assertThat(resolved.description()).isEqualTo("Created orders");
        assertThat(resolved.unit()).isEqualTo("1");
        assertThat(resolved.maxSeries()).isEqualTo(25);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.FAIL);
    }

    @Test
    void shouldApplyYamlOverrides() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.setMetricPrefix("ci054147");
        ManualMetricRuntimeProperties runtime = new ManualMetricRuntimeProperties();
        runtime.setEnabled(false);
        runtime.setSuffix("orders.created.v2");
        runtime.setScope("business-v2");
        runtime.setMaxSeries(1000);
        runtime.setOverflowPolicy(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
        properties.getManual().put("orders-created", runtime);

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties).resolve(
                "orders-created",
                MetricKind.COUNTER,
                MetricDefinition.of("orders.created").scope("business").build());

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.fullMetricName()).isEqualTo("ci054147.orders.created.v2");
        assertThat(resolved.scope()).isEqualTo("business-v2");
        assertThat(resolved.maxSeries()).isEqualTo(1000);
        assertThat(resolved.overflowPolicy()).isEqualTo(SeriesOverflowPolicy.AGGREGATE_TO_OTHER);
    }

    @Test
    void shouldDisableWhenScopeIsDisabled() {
        ReflexOtelMetricsProperties properties = new ReflexOtelMetricsProperties();
        properties.getScopes().put("business", new ReflexOtelMetricsProperties.ScopeProperties(false));

        ResolvedManualMetricConfig resolved = new ManualMetricConfigResolver(properties).resolve(
                "orders-created",
                MetricKind.COUNTER,
                MetricDefinition.of("orders.created").scope("business").build());

        assertThat(resolved.enabled()).isFalse();
    }
}
```

- [ ] **Step 2: Run resolver tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ManualMetricConfigResolverTest test
```

Expected: compilation fails because manual config classes do not exist.

- [ ] **Step 3: Implement manual config classes**

Create `ManualMetricRuntimeProperties` with getters/setters for `enabled`, `suffix`, `scope`, `maxSeries`, and `overflowPolicy`.

Create `ResolvedManualMetricConfig`:

```java
package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.AttributesSchema;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.api.SeriesOverflowPolicy;

public record ResolvedManualMetricConfig(
        String metricId,
        boolean enabled,
        String fullMetricName,
        String suffix,
        String scope,
        MetricKind metricKind,
        String description,
        String unit,
        AttributesSchema attributes,
        int maxSeries,
        SeriesOverflowPolicy overflowPolicy
) {
}
```

Add to `ReflexOtelMetricsProperties`:

```java
private Map<String, ManualMetricRuntimeProperties> manual = new LinkedHashMap<>();

public Map<String, ManualMetricRuntimeProperties> getManual() {
    return manual;
}

public void setManual(Map<String, ManualMetricRuntimeProperties> manual) {
    this.manual = manual;
}
```

Create `ManualMetricConfigResolver`:

```java
package com.reflex.otelmetrics.config;

import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import java.util.Objects;

public class ManualMetricConfigResolver {

    private final ReflexOtelMetricsProperties properties;

    public ManualMetricConfigResolver(ReflexOtelMetricsProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public ResolvedManualMetricConfig resolve(String metricId, MetricKind kind, MetricDefinition definition) {
        if (metricId == null || metricId.isBlank()) {
            throw new IllegalArgumentException("metricId must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(definition, "definition must not be null");

        ManualMetricRuntimeProperties runtime = properties.getManual().getOrDefault(metricId, new ManualMetricRuntimeProperties());
        String suffix = runtime.getSuffix() != null ? runtime.getSuffix() : definition.metricSuffix();
        String scope = runtime.getScope() != null ? runtime.getScope() : definition.scope();
        int maxSeries = runtime.getMaxSeries() != null ? runtime.getMaxSeries() : definition.maxSeries();
        var overflowPolicy = runtime.getOverflowPolicy() != null ? runtime.getOverflowPolicy() : definition.overflowPolicy();
        boolean enabled = properties.isEnabled()
                && resolveScopeEnabled(scope)
                && !Boolean.FALSE.equals(runtime.getEnabled());

        return new ResolvedManualMetricConfig(
                metricId,
                enabled,
                properties.getMetricPrefix() + "." + suffix,
                suffix,
                scope,
                kind,
                definition.description(),
                definition.unit(),
                definition.attributes(),
                maxSeries,
                overflowPolicy);
    }

    private boolean resolveScopeEnabled(String scope) {
        ReflexOtelMetricsProperties.ScopeProperties scopeProperties = properties.getScopes().get(scope);
        return scopeProperties == null || scopeProperties.isEnabled();
    }
}
```

- [ ] **Step 4: Run resolver tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ManualMetricConfigResolverTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/reflex/otelmetrics/config src/test/java/com/reflex/otelmetrics/config/ManualMetricConfigResolverTest.java
git commit -m "feat: resolve manual metric configuration"
```

## Task 3: Add OTEL Counter Instrument Support

**Files:**
- Modify: `src/main/java/com/reflex/otelmetrics/otel/OtelInstrumentRegistry.java`
- Modify: `src/main/java/com/reflex/otelmetrics/otel/OtelMetricPublisher.java`
- Modify: `src/test/java/com/reflex/otelmetrics/otel/OtelInstrumentRegistryTest.java`
- Modify or add: `src/test/java/com/reflex/otelmetrics/otel/OtelMetricPublisherTest.java`

- [ ] **Step 1: Add failing registry test for counter**

In `OtelInstrumentRegistryTest`, add:

```java
@Test
void shouldCreateLongCounterInstrument() {
    Meter meter = mock(Meter.class);
    io.opentelemetry.api.metrics.LongCounterBuilder counterBuilder = mock(io.opentelemetry.api.metrics.LongCounterBuilder.class);
    io.opentelemetry.api.metrics.LongCounter counter = mock(io.opentelemetry.api.metrics.LongCounter.class);
    when(meter.counterBuilder("orders.created")).thenReturn(counterBuilder);
    when(counterBuilder.build()).thenReturn(counter);

    OtelInstrumentRegistry registry = new OtelInstrumentRegistry(meter);

    Object instrument = registry.getOrCreate("orders.created", MetricKind.COUNTER);

    assertThat(instrument).isSameAs(counter);
    verify(meter).counterBuilder("orders.created");
}
```

- [ ] **Step 2: Run registry test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=OtelInstrumentRegistryTest test
```

Expected: fails because `COUNTER` is not handled in `OtelInstrumentRegistry`.

- [ ] **Step 3: Implement counter support**

Update `OtelInstrumentRegistry` imports and switch:

```java
import io.opentelemetry.api.metrics.LongCounter;
```

```java
return new RegisteredInstrument(kind, switch (kind) {
    case COUNTER -> (LongCounter) meter.counterBuilder(name).build();
    case GAUGE -> (LongGauge) meter.gaugeBuilder(name).ofLongs().build();
    case UP_DOWN_COUNTER -> (LongUpDownCounter) meter.upDownCounterBuilder(name).build();
});
```

Update `OtelMetricPublisher` to handle `LongCounter`:

```java
} else if (instrument instanceof io.opentelemetry.api.metrics.LongCounter counter) {
    counter.add(point.value(), attributes);
} else if (instrument instanceof LongUpDownCounter counter) {
    counter.add(point.value(), attributes);
}
```

- [ ] **Step 4: Run OTEL tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OtelInstrumentRegistryTest,OtelMetricPublisherTest test
```

Expected: tests pass. If `OtelMetricPublisherTest` does not exist, run `OtelInstrumentRegistryTest` now and add publisher coverage in Task 6 through manual metric implementation tests.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/reflex/otelmetrics/otel src/test/java/com/reflex/otelmetrics/otel
git commit -m "feat: support otel counter instruments"
```

## Task 4: Add Attribute Validation And Series Tracking

**Files:**
- Create: `src/main/java/com/reflex/otelmetrics/manual/AttributeValidationResult.java`
- Create: `src/main/java/com/reflex/otelmetrics/manual/AttributeValidator.java`
- Create: `src/main/java/com/reflex/otelmetrics/manual/ManualSeriesTracker.java`
- Test: `src/test/java/com/reflex/otelmetrics/manual/AttributeValidatorTest.java`
- Test: `src/test/java/com/reflex/otelmetrics/manual/ManualSeriesTrackerTest.java`

- [ ] **Step 1: Write failing validation tests**

Create `AttributeValidatorTest` covering valid attributes, missing required, unknown, blank value, and defensive copy:

```java
package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.AttributesSchema;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeValidatorTest {

    @Test
    void shouldAcceptValidAttributes() {
        AttributeValidationResult result = new AttributeValidator().validate(
                AttributesSchema.builder().required("client").optional("region").build(),
                Map.of("client", "A", "region", "RU"));

        assertThat(result.valid()).isTrue();
        assertThat(result.attributes()).containsEntry("client", "A").containsEntry("region", "RU");
    }

    @Test
    void shouldRejectMissingRequiredAttribute() {
        AttributeValidationResult result = new AttributeValidator().validate(
                AttributesSchema.builder().required("client").build(),
                Map.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("missing required attribute 'client'");
    }

    @Test
    void shouldRejectUnknownAttributeByDefault() {
        AttributeValidationResult result = new AttributeValidator().validate(
                AttributesSchema.builder().required("client").build(),
                Map.of("client", "A", "extra", "x"));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("unknown attribute 'extra'");
    }

    @Test
    void shouldRejectBlankAttributeValue() {
        AttributeValidationResult result = new AttributeValidator().validate(
                AttributesSchema.builder().required("client").build(),
                Map.of("client", " "));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("attribute 'client' value must not be blank");
    }

    @Test
    void shouldReturnDefensiveCopy() {
        HashMap<String, String> input = new HashMap<>();
        input.put("client", "A");

        AttributeValidationResult result = new AttributeValidator().validate(
                AttributesSchema.builder().required("client").build(),
                input);
        input.put("client", "B");

        assertThat(result.attributes()).containsEntry("client", "A");
    }
}
```

Create `ManualSeriesTrackerTest`:

```java
package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualSeriesTrackerTest {

    @Test
    void shouldAllowExistingSeriesAfterLimitIsReached() {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.FAIL);

        assertThat(tracker.apply(Map.of("client", "A")).accepted()).isTrue();
        assertThat(tracker.apply(Map.of("client", "A")).accepted()).isTrue();
    }

    @Test
    void shouldRejectNewSeriesAfterLimitIsReachedForFailPolicy() {
        ManualSeriesTracker tracker = new ManualSeriesTracker(1, SeriesOverflowPolicy.FAIL);

        tracker.apply(Map.of("client", "A"));
        ManualSeriesTracker.Result result = tracker.apply(Map.of("client", "B"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("max series limit 1 exceeded");
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=AttributeValidatorTest,ManualSeriesTrackerTest test
```

Expected: compilation fails because manual validation classes do not exist.

- [ ] **Step 3: Implement validation and tracker**

Create `AttributeValidationResult`:

```java
package com.reflex.otelmetrics.manual;

import java.util.Map;

public record AttributeValidationResult(boolean valid, Map<String, String> attributes, String message) {
    public static AttributeValidationResult valid(Map<String, String> attributes) {
        return new AttributeValidationResult(true, Map.copyOf(attributes), null);
    }

    public static AttributeValidationResult invalid(String message) {
        return new AttributeValidationResult(false, Map.of(), message);
    }
}
```

Create `AttributeValidator`:

```java
package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.AttributesSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AttributeValidator {

    public AttributeValidationResult validate(AttributesSchema schema, Map<String, String> attributes) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");

        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                return AttributeValidationResult.invalid("attribute name must not be blank");
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                return AttributeValidationResult.invalid("attribute '" + key + "' value must not be blank");
            }
            if (schema.rejectUnknown() && !schema.allowed().contains(key)) {
                return AttributeValidationResult.invalid("unknown attribute '" + key + "'");
            }
            copy.put(key, value);
        }

        for (String required : schema.required()) {
            if (!copy.containsKey(required)) {
                return AttributeValidationResult.invalid("missing required attribute '" + required + "'");
            }
        }

        return AttributeValidationResult.valid(copy);
    }
}
```

Create `ManualSeriesTracker`:

```java
package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.SeriesOverflowPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ManualSeriesTracker {

    private final int maxSeries;
    private final SeriesOverflowPolicy overflowPolicy;
    private final Set<Map<String, String>> observedSeries = ConcurrentHashMap.newKeySet();

    public ManualSeriesTracker(int maxSeries, SeriesOverflowPolicy overflowPolicy) {
        if (maxSeries < 1) {
            throw new IllegalArgumentException("maxSeries must be greater than zero");
        }
        this.maxSeries = maxSeries;
        this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy must not be null");
    }

    public Result apply(Map<String, String> attributes) {
        Map<String, String> series = Map.copyOf(attributes);
        if (observedSeries.contains(series)) {
            return Result.accepted(series);
        }
        if (observedSeries.size() >= maxSeries) {
            return Result.rejected("max series limit " + maxSeries + " exceeded");
        }
        observedSeries.add(series);
        return Result.accepted(series);
    }

    public record Result(boolean accepted, Map<String, String> attributes, String message) {
        static Result accepted(Map<String, String> attributes) {
            return new Result(true, attributes, null);
        }

        static Result rejected(String message) {
            return new Result(false, Map.of(), message);
        }
    }
}
```

Note: keep `AGGREGATE_TO_OTHER` as future-compatible but implement `FAIL` as log-and-skip first. Add explicit tests for aggregation only when the implementation can define a stable manual overflow attribute contract.

- [ ] **Step 4: Run validation tests**

Run:

```powershell
.\mvnw.cmd -Dtest=AttributeValidatorTest,ManualSeriesTrackerTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/reflex/otelmetrics/manual src/test/java/com/reflex/otelmetrics/manual
git commit -m "feat: validate manual metric attributes"
```

## Task 5: Implement Manual Metric Beans And Factory

**Files:**
- Create: `src/main/java/com/reflex/otelmetrics/manual/ReflexMetricFactory.java`
- Create: `src/main/java/com/reflex/otelmetrics/manual/DefaultCounterMetric.java`
- Create: `src/main/java/com/reflex/otelmetrics/manual/DefaultGaugeMetric.java`
- Create: `src/main/java/com/reflex/otelmetrics/manual/DefaultUpDownCounterMetric.java`
- Test: `src/test/java/com/reflex/otelmetrics/manual/ReflexMetricFactoryTest.java`
- Test: `src/test/java/com/reflex/otelmetrics/manual/DefaultCounterMetricTest.java`

- [ ] **Step 1: Write failing factory and counter tests**

Create `ReflexMetricFactoryTest`:

```java
package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.CounterMetric;
import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.config.ManualMetricConfigResolver;
import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReflexMetricFactoryTest {

    @Test
    void shouldCreateCounterMetric() {
        ReflexMetricFactory factory = new ReflexMetricFactory(
                new ManualMetricConfigResolver(new ReflexOtelMetricsProperties()),
                new OtelInstrumentRegistry(mock(Meter.class)),
                new AttributeValidator());

        CounterMetric metric = factory.counter("orders-created", MetricDefinition.of("orders.created").build());

        assertThat(metric).isInstanceOf(DefaultCounterMetric.class);
    }
}
```

Create `DefaultCounterMetricTest`:

```java
package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.MetricDefinition;
import com.reflex.otelmetrics.api.MetricKind;
import com.reflex.otelmetrics.config.ManualMetricConfigResolver;
import com.reflex.otelmetrics.config.ReflexOtelMetricsProperties;
import com.reflex.otelmetrics.otel.OtelInstrumentRegistry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCounterMetricTest {

    @Test
    void shouldPublishValidCounterValue() {
        Meter meter = mock(Meter.class);
        LongCounterBuilder builder = mock(LongCounterBuilder.class);
        LongCounter counter = mock(LongCounter.class);
        when(meter.counterBuilder("reflex.orders.created")).thenReturn(builder);
        when(builder.build()).thenReturn(counter);
        ReflexMetricFactory factory = factory(meter);

        factory.counter("orders-created", MetricDefinition.of("orders.created").build())
                .add(2, Map.of());

        verify(counter).add(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSkipNegativeCounterValue() {
        Meter meter = mock(Meter.class);
        LongCounterBuilder builder = mock(LongCounterBuilder.class);
        LongCounter counter = mock(LongCounter.class);
        when(meter.counterBuilder("reflex.orders.created")).thenReturn(builder);
        when(builder.build()).thenReturn(counter);
        ReflexMetricFactory factory = factory(meter);

        factory.counter("orders-created", MetricDefinition.of("orders.created").build())
                .add(-1, Map.of());

        verify(counter, never()).add(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private static ReflexMetricFactory factory(Meter meter) {
        return new ReflexMetricFactory(
                new ManualMetricConfigResolver(new ReflexOtelMetricsProperties()),
                new OtelInstrumentRegistry(meter),
                new AttributeValidator());
    }
}
```

- [ ] **Step 2: Run manual metric tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexMetricFactoryTest,DefaultCounterMetricTest test
```

Expected: compilation fails because factory and implementations do not exist.

- [ ] **Step 3: Implement factory and metric classes**

Implement `ReflexMetricFactory` with constructor dependencies:

```java
public ReflexMetricFactory(
        ManualMetricConfigResolver configResolver,
        OtelInstrumentRegistry instrumentRegistry,
        AttributeValidator attributeValidator)
```

Factory methods:

```java
public CounterMetric counter(String metricId, MetricDefinition definition)
public GaugeMetric gauge(String metricId, MetricDefinition definition)
public UpDownCounterMetric upDownCounter(String metricId, MetricDefinition definition)
```

Each method resolves config with the fixed `MetricKind`, calls `instrumentRegistry.getOrCreate(resolved.fullMetricName(), resolved.metricKind())`, and creates the matching default metric implementation.

Implement shared behavior inside each default metric:

```java
if (!config.enabled()) {
    return;
}
AttributeValidationResult validation = attributeValidator.validate(config.attributes(), attributes);
if (!validation.valid()) {
    logger.warn("Skipping manual metric '{}' because {}", config.metricId(), validation.message());
    return;
}
ManualSeriesTracker.Result series = seriesTracker.apply(validation.attributes());
if (!series.accepted()) {
    logger.warn("Skipping manual metric '{}' because {}", config.metricId(), series.message());
    return;
}
```

Counter-specific rule:

```java
if (value < 0) {
    logger.warn("Skipping manual counter metric '{}' because counter value must not be negative", config.metricId());
    return;
}
```

Publish through OTel instruments:

```java
counter.add(value, toAttributes(series.attributes()));
gauge.set(value, toAttributes(series.attributes()));
upDownCounter.add(value, toAttributes(series.attributes()));
```

Catch `RuntimeException` around validation/tracking/publish and log a warning without rethrowing.

- [ ] **Step 4: Run manual metric tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexMetricFactoryTest,DefaultCounterMetricTest,AttributeValidatorTest,ManualSeriesTrackerTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/reflex/otelmetrics/manual src/test/java/com/reflex/otelmetrics/manual
git commit -m "feat: add reflex manual metric factory"
```

## Task 6: Wire Auto-Configuration And Spring Context Coverage

**Files:**
- Modify: `src/main/java/com/reflex/otelmetrics/autoconfigure/ReflexOtelMetricsAutoConfiguration.java`
- Test: `src/test/java/com/reflex/otelmetrics/autoconfigure/ReflexOtelMetricsAutoConfigurationTest.java`

- [ ] **Step 1: Add failing autoconfiguration test**

Add to `ReflexOtelMetricsAutoConfigurationTest`:

```java
@Test
void shouldCreateManualMetricFactory() {
    contextRunner.run(context -> {
        assertThat(context).hasSingleBean(com.reflex.otelmetrics.config.ManualMetricConfigResolver.class);
        assertThat(context).hasSingleBean(com.reflex.otelmetrics.manual.ReflexMetricFactory.class);
    });
}

@Test
void shouldAllowMultipleManualCounterBeansWithQualifiers() {
    contextRunner
            .withUserConfiguration(ManualMetricBeansConfiguration.class)
            .run(context -> {
                assertThat(context).hasBean("ordersCreatedMetric");
                assertThat(context).hasBean("ordersFailedMetric");
                assertThat(context.getBean("ordersCreatedMetric")).isInstanceOf(com.reflex.otelmetrics.api.CounterMetric.class);
                assertThat(context.getBean("ordersFailedMetric")).isInstanceOf(com.reflex.otelmetrics.api.CounterMetric.class);
            });
}

static class ManualMetricBeansConfiguration {
    @org.springframework.context.annotation.Bean
    com.reflex.otelmetrics.api.CounterMetric ordersCreatedMetric(com.reflex.otelmetrics.manual.ReflexMetricFactory factory) {
        return factory.counter("orders-created", com.reflex.otelmetrics.api.MetricDefinition.of("orders.created").build());
    }

    @org.springframework.context.annotation.Bean
    com.reflex.otelmetrics.api.CounterMetric ordersFailedMetric(com.reflex.otelmetrics.manual.ReflexMetricFactory factory) {
        return factory.counter("orders-failed", com.reflex.otelmetrics.api.MetricDefinition.of("orders.failed").build());
    }
}
```

- [ ] **Step 2: Run autoconfiguration tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexOtelMetricsAutoConfigurationTest test
```

Expected: fails because resolver/factory beans are not auto-configured.

- [ ] **Step 3: Add auto-configuration beans**

Add imports and beans:

```java
@Bean
@ConditionalOnMissingBean
ManualMetricConfigResolver manualMetricConfigResolver(ReflexOtelMetricsProperties properties) {
    return new ManualMetricConfigResolver(properties);
}

@Bean
@ConditionalOnMissingBean
AttributeValidator attributeValidator() {
    return new AttributeValidator();
}

@Bean
@ConditionalOnMissingBean
ReflexMetricFactory reflexMetricFactory(
        ManualMetricConfigResolver manualMetricConfigResolver,
        OtelInstrumentRegistry otelInstrumentRegistry,
        AttributeValidator attributeValidator) {
    return new ReflexMetricFactory(manualMetricConfigResolver, otelInstrumentRegistry, attributeValidator);
}
```

- [ ] **Step 4: Run autoconfiguration tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexOtelMetricsAutoConfigurationTest test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/reflex/otelmetrics/autoconfigure src/test/java/com/reflex/otelmetrics/autoconfigure
git commit -m "feat: auto-configure manual metric factory"
```

## Task 7: Document Manual Metric Use Cases

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add README section**

Add a section after the existing metric source contract:

```markdown
## Manual Metric Beans

JDBC metrics are collected by the starter on a schedule. Manual metrics are emitted directly by application code.

Manual metrics are declared as Spring beans through `ReflexMetricFactory`. The Java bean is the primary contract; YAML is only an optional operational override layer.

### Low-Level Beans With Qualifiers

Use this when a service needs one or two metrics directly.

```java
@Configuration
class BusinessMetricsConfig {

    @Bean
    CounterMetric ordersCreatedMetric(ReflexMetricFactory factory) {
        return factory.counter(
                "orders-created",
                MetricDefinition.of("orders.created")
                        .scope("business")
                        .description("Created orders")
                        .unit("1")
                        .attributes(AttributesSchema.builder()
                                .required("client")
                                .required("channel")
                                .build())
                        .build());
    }
}
```

```java
@Service
class OrderService {
    private final CounterMetric ordersCreatedMetric;

    OrderService(@Qualifier("ordersCreatedMetric") CounterMetric ordersCreatedMetric) {
        this.ordersCreatedMetric = ordersCreatedMetric;
    }

    void createOrder(String client, String channel) {
        ordersCreatedMetric.increment(Map.of(
                "client", client,
                "channel", channel));
    }
}
```

### Domain Metric Bean

For larger domains, group low-level metrics behind an application-owned domain bean.

```java
@Bean
OrderMetrics orderMetrics(ReflexMetricFactory factory) {
    return new OrderMetrics(
            factory.counter("orders-created", MetricDefinition.of("orders.created").scope("business").build()),
            factory.counter("orders-failed", MetricDefinition.of("orders.failed").scope("business").build()),
            factory.gauge("orders-queue-size", MetricDefinition.of("orders.queue-size").scope("business").build()));
}
```

```java
public class OrderMetrics {
    private final CounterMetric created;
    private final CounterMetric failed;
    private final GaugeMetric queueSize;

    public OrderMetrics(CounterMetric created, CounterMetric failed, GaugeMetric queueSize) {
        this.created = created;
        this.failed = failed;
        this.queueSize = queueSize;
    }

    public void created(String client, String channel) {
        created.increment(Map.of("client", client, "channel", channel));
    }

    public void failed(String client, String reason) {
        failed.increment(Map.of("client", client, "reason", reason));
    }

    public void queueSize(long size) {
        queueSize.set(size, Map.of("queue", "orders"));
    }
}
```

### Manual Metric Overrides

```yaml
reflex:
  otel:
    metrics:
      manual:
        orders-created:
          enabled: true
          suffix: orders.created.v2
          scope: business-v2
          max-series: 1000
          overflow-policy: FAIL
```

Manual metric calls are fail-safe. Invalid attributes, disabled metrics, cardinality overflow, or OpenTelemetry publication errors are logged and skipped; they do not fail business code.
```

- [ ] **Step 2: Review README snippets for compile-compatible API**

Check that examples call `MetricDefinition.of(...).build()` and import names match actual package locations.

- [ ] **Step 3: Commit**

```powershell
git add README.md
git commit -m "docs: document manual metric beans"
```

## Task 8: Full Verification

**Files:**
- All modified files

- [ ] **Step 1: Run full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: build succeeds and all tests pass.

- [ ] **Step 2: Inspect git status**

Run:

```powershell
git status --short
```

Expected: no unstaged implementation changes unless intentionally left for review.

- [ ] **Step 3: Final commit if needed**

If Task 8 surfaced small fixes, commit them:

```powershell
git add <changed-files>
git commit -m "test: verify manual metric beans"
```

## Self-Review

- Spec coverage: plan covers `ReflexMetricFactory`, typed metric beans, Java-primary/YAML-optional config, attributes validation, log-and-skip behavior, scope override, descriptions/units, cardinality, thread-safe tracker, README low-level and domain use cases, and tests.
- Placeholder scan: no `TODO`, `TBD`, or open placeholders are intentionally left in executable steps.
- Type consistency: plan uses `SeriesOverflowPolicy.FAIL` as the current enum's reject/log/skip policy instead of introducing a new `REJECT` enum value.
