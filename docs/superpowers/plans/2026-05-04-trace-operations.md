# Trace Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add generic OpenTelemetry trace support that hides span lifecycle and context propagation boilerplate without adding workflow-specific concepts to the library.

**Architecture:** The starter exposes neutral public tracing primitives: `TraceOperations`, `SpanSpec`, and `TraceCarrier`. Applications can embed these primitives into their own base action or workflow runner, store W3C `traceparent` and `tracestate` in any transport, and continue traces across pods without the library knowing about the application domain.

**Tech Stack:** Java 17, Spring Boot 3.5, OpenTelemetry 1.60.1, OTLP/gRPC trace export, Maven, JUnit 5, AssertJ.

---

## Design Decisions

- The library must not expose `WorkflowTracing`, `RclnAction`, `ProcessContext`, `businessServiceId`, or any application-specific API.
- Cross-pod linkage uses standard W3C propagation fields: `traceparent` and optional `tracestate`.
- `businessServiceId`, `processName`, `actionName`, and action class names are span attributes supplied by the application through `SpanSpec.attributes()`.
- `captureCurrent()` exports the current active span context. Future spans use that exported carrier as their parent.
- Span lifecycle is short-lived around executable work. Long workflow waiting time should be represented through metrics or business timestamps, not by keeping spans open.
- Disabled tracing must not require application code branches. A no-op implementation should still execute the wrapped body and return an empty carrier.

## Target Application Pattern

The library stays generic, but the expected application integration is:

```java
TraceCarrier parent = readCarrier(context.getParams());
if (parent.isEmpty()) {
    parent = readCarrier(context.getInParams());
}

traces.inSpan(
        new SpanSpec(
                "workflow.action." + action.getClass().getSimpleName(),
                parent,
                Map.of(
                        "workflow.process.name", processName,
                        "workflow.action.name", action.getClass().getSimpleName(),
                        "workflow.action.class", action.getClass().getName(),
                        "workflow.business_service_id", businessServiceId)),
        () -> {
            action.execute(context);
            writeCarrier(context.getParams(), traces.captureCurrent());
        });
```

When an action creates a child process, the application writes `traces.captureCurrent()` into the child process input context, for example under `_otel.traceparent` and `_otel.tracestate`.

## File Structure

- Create `src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrier.java`: immutable W3C propagation carrier.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/api/SpanSpec.java`: immutable span request with name, parent carrier, and attributes.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceOperations.java`: public facade for running code in spans and capturing the current carrier.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperations.java`: OpenTelemetry-backed implementation.
- Create `src/main/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperations.java`: disabled/no-op implementation.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexOtelMetricsProperties.java`: add trace enablement properties.
- Modify `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexOtelMetricsAutoConfiguration.java`: auto-configure `TraceOperations`.
- Modify `pom.xml`: add `opentelemetry-sdk-testing` as a test dependency if needed for in-memory span assertions.
- Create `src/test/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperationsTest.java`: unit tests for span lifecycle and propagation.
- Create `src/test/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperationsTest.java`: no-op behavior tests.
- Modify `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexOtelMetricsAutoConfigurationTest.java`: auto-configuration coverage.
- Modify `README.md`: document generic tracing API and application-side workflow integration pattern.

---

### Task 1: Public Trace API

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrier.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/SpanSpec.java`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceOperations.java`
- Test: `src/test/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrierTest.java`
- **Step 1: Write tests for empty and non-empty carriers**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrierTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceCarrierTest {

    @Test
    void shouldDetectEmptyCarrier() {
        assertThat(TraceCarrier.empty().isEmpty()).isTrue();
        assertThat(new TraceCarrier(null, null).isEmpty()).isTrue();
        assertThat(new TraceCarrier("", "").isEmpty()).isTrue();
    }

    @Test
    void shouldDetectCarrierWithTraceparent() {
        TraceCarrier carrier = new TraceCarrier(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                null);

        assertThat(carrier.isEmpty()).isFalse();
    }
}
```

- **Step 2: Run the new test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=TraceCarrierTest test
```

Expected: compilation fails because `TraceCarrier` does not exist.

- **Step 3: Add `TraceCarrier`**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrier.java`:

```java
package ru.sber.rcln.reflex.telemetry.api;

public record TraceCarrier(String traceparent, String tracestate) {

    public static TraceCarrier empty() {
        return new TraceCarrier(null, null);
    }

    public boolean isEmpty() {
        return traceparent == null || traceparent.isBlank();
    }
}
```

- **Step 4: Add `SpanSpec`**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/api/SpanSpec.java`:

```java
package ru.sber.rcln.reflex.telemetry.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SpanSpec(String name, TraceCarrier parent, Map<String, String> attributes) {

    public SpanSpec {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        parent = parent != null ? parent : TraceCarrier.empty();
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
```

- **Step 5: Add `TraceOperations`**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceOperations.java`:

```java
package ru.sber.rcln.reflex.telemetry.api;

import java.util.function.Supplier;

public interface TraceOperations {

    void inSpan(SpanSpec spec, Runnable body);

    <T> T inSpan(SpanSpec spec, Supplier<T> body);

    TraceCarrier captureCurrent();
}
```

- **Step 6: Run API tests**

Run:

```powershell
.\mvnw.cmd -Dtest=TraceCarrierTest test
```

Expected: tests pass.

- **Step 7: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrier.java src/main/java/ru/sber/rcln/reflex/telemetry/api/SpanSpec.java src/main/java/ru/sber/rcln/reflex/telemetry/api/TraceOperations.java src/test/java/ru/sber/rcln/reflex/telemetry/api/TraceCarrierTest.java
git commit -m "feat: add trace operations API"
```

---

### Task 2: No-Op Trace Operations

**Files:**

- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperations.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperationsTest.java`
- **Step 1: Write no-op tests**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperationsTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoopTraceOperationsTest {

    private final NoopTraceOperations traces = new NoopTraceOperations();

    @Test
    void shouldRunRunnableBody() {
        boolean[] called = {false};

        traces.inSpan(new SpanSpec("test.span", TraceCarrier.empty(), Map.of()), () -> called[0] = true);

        assertThat(called[0]).isTrue();
    }

    @Test
    void shouldRunSupplierBody() {
        String result = traces.inSpan(
                new SpanSpec("test.span", TraceCarrier.empty(), Map.of()),
                () -> "value");

        assertThat(result).isEqualTo("value");
    }

    @Test
    void shouldReturnEmptyCarrier() {
        assertThat(traces.captureCurrent().isEmpty()).isTrue();
    }
}
```

- **Step 2: Run no-op tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=NoopTraceOperationsTest test
```

Expected: compilation fails because `NoopTraceOperations` does not exist.

- **Step 3: Add no-op implementation**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperations.java`:

```java
package ru.sber.rcln.reflex.telemetry.tracing;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import java.util.Objects;
import java.util.function.Supplier;

public class NoopTraceOperations implements TraceOperations {

    @Override
    public void inSpan(SpanSpec spec, Runnable body) {
        Objects.requireNonNull(body, "body must not be null").run();
    }

    @Override
    public <T> T inSpan(SpanSpec spec, Supplier<T> body) {
        return Objects.requireNonNull(body, "body must not be null").get();
    }

    @Override
    public TraceCarrier captureCurrent() {
        return TraceCarrier.empty();
    }
}
```

- **Step 4: Run no-op tests**

Run:

```powershell
.\mvnw.cmd -Dtest=NoopTraceOperationsTest test
```

Expected: tests pass.

- **Step 5: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperations.java src/test/java/ru/sber/rcln/reflex/telemetry/tracing/NoopTraceOperationsTest.java
git commit -m "feat: add no-op trace operations"
```

---

### Task 3: OpenTelemetry Trace Operations

**Files:**

- Modify: `pom.xml`
- Create: `src/main/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperations.java`
- Create: `src/test/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperationsTest.java`
- **Step 1: Add test dependency**

Modify `pom.xml` dependencies:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-testing</artifactId>
    <scope>test</scope>
</dependency>
```

- **Step 2: Write span lifecycle tests**

Create `src/test/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperationsTest.java`:

```java
package ru.sber.rcln.reflex.telemetry.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTraceOperationsTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private DefaultTraceOperations traces;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        Tracer tracer = openTelemetry.getTracer("test.scope");
        traces = new DefaultTraceOperations(tracer, openTelemetry.getPropagators());
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void shouldCreateSpanWithAttributes() {
        traces.inSpan(
                new SpanSpec(
                        "workflow.action.GetContractAction",
                        TraceCarrier.empty(),
                        Map.of("workflow.action.name", "GetContractAction")),
                () -> {
                });

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        assertThat(exporter.getFinishedSpanItems().get(0).getName())
                .isEqualTo("workflow.action.GetContractAction");
        assertThat(exporter.getFinishedSpanItems().get(0).getAttributes()
                .get(AttributeKey.stringKey("workflow.action.name")))
                .isEqualTo("GetContractAction");
    }

    @Test
    void shouldCaptureCurrentCarrierInsideSpan() {
        TraceCarrier carrier = traces.inSpan(
                new SpanSpec("workflow.action.CreateProcessAction", TraceCarrier.empty(), Map.of()),
                traces::captureCurrent);

        assertThat(carrier.traceparent()).isNotBlank();
        assertThat(carrier.traceparent()).startsWith("00-");
    }

    @Test
    void shouldContinueSpanFromCarrier() {
        TraceCarrier parentCarrier = traces.inSpan(
                new SpanSpec("parent.action", TraceCarrier.empty(), Map.of()),
                traces::captureCurrent);

        traces.inSpan(
                new SpanSpec("child.action", parentCarrier, Map.of()),
                () -> {
                });

        assertThat(exporter.getFinishedSpanItems()).hasSize(2);
        var parent = exporter.getFinishedSpanItems().get(0);
        var child = exporter.getFinishedSpanItems().get(1);
        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
    }

    @Test
    void shouldRecordExceptionAndRethrow() {
        RuntimeException failure = new RuntimeException("boom");

        assertThatThrownBy(() -> traces.inSpan(
                new SpanSpec("failing.action", TraceCarrier.empty(), Map.of()),
                () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        assertThat(exporter.getFinishedSpanItems().get(0).getStatus().getStatusCode().name())
                .isEqualTo("ERROR");
        assertThat(exporter.getFinishedSpanItems().get(0).getEvents())
                .anySatisfy(event -> assertThat(event.getName()).isEqualTo("exception"));
    }
}
```

- **Step 3: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=DefaultTraceOperationsTest test
```

Expected: compilation fails because `DefaultTraceOperations` does not exist.

- **Step 4: Add OpenTelemetry implementation**

Create `src/main/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperations.java`:

```java
package ru.sber.rcln.reflex.telemetry.tracing;

import ru.sber.rcln.reflex.telemetry.api.SpanSpec;
import ru.sber.rcln.reflex.telemetry.api.TraceCarrier;
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class DefaultTraceOperations implements TraceOperations {

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;

    private final Tracer tracer;
    private final ContextPropagators propagators;

    public DefaultTraceOperations(Tracer tracer) {
        this(tracer, GlobalOpenTelemetry.getPropagators());
    }

    public DefaultTraceOperations(Tracer tracer, ContextPropagators propagators) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
        this.propagators = Objects.requireNonNull(propagators, "propagators must not be null");
    }

    @Override
    public void inSpan(SpanSpec spec, Runnable body) {
        inSpan(spec, () -> {
            Objects.requireNonNull(body, "body must not be null").run();
            return null;
        });
    }

    @Override
    public <T> T inSpan(SpanSpec spec, Supplier<T> body) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(body, "body must not be null");

        SpanBuilder spanBuilder = tracer.spanBuilder(spec.name());
        Context parentContext = extractParent(spec.parent());
        if (parentContext != null) {
            spanBuilder.setParent(parentContext);
        }
        spanBuilder.setAllAttributes(toAttributes(spec.attributes()));

        Span span = spanBuilder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return body.get();
        } catch (RuntimeException | Error exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @Override
    public TraceCarrier captureCurrent() {
        Map<String, String> carrier = new LinkedHashMap<>();
        propagators.getTextMapPropagator().inject(Context.current(), carrier, MAP_SETTER);
        return new TraceCarrier(carrier.get("traceparent"), carrier.get("tracestate"));
    }

    private Context extractParent(TraceCarrier carrier) {
        if (carrier == null || carrier.isEmpty()) {
            return null;
        }

        Map<String, String> map = new LinkedHashMap<>();
        map.put("traceparent", carrier.traceparent());
        if (carrier.tracestate() != null && !carrier.tracestate().isBlank()) {
            map.put("tracestate", carrier.tracestate());
        }
        return propagators.getTextMapPropagator().extract(Context.current(), map, MAP_GETTER);
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                builder.put(key, value);
            }
        });
        return builder.build();
    }
}
```

- **Step 5: Run tracing tests**

Run:

```powershell
.\mvnw.cmd -Dtest=DefaultTraceOperationsTest test
```

Expected: tests pass.

- **Step 6: Commit**

```powershell
git add pom.xml src/main/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperations.java src/test/java/ru/sber/rcln/reflex/telemetry/tracing/DefaultTraceOperationsTest.java
git commit -m "feat: implement trace operations"
```

---

### Task 4: Trace Auto-Configuration

**Files:**

- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexOtelMetricsProperties.java`
- Modify: `src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexOtelMetricsAutoConfiguration.java`
- Modify: `src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexOtelMetricsAutoConfigurationTest.java`
- **Step 1: Write auto-configuration tests**

Add imports to `ReflexOtelMetricsAutoConfigurationTest`:

```java
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import ru.sber.rcln.reflex.telemetry.tracing.NoopTraceOperations;
```

Add tests:

```java
@Test
void shouldCreateTraceOperationsWhenEnabled() {
    contextRunner
            .withBean(OpenTelemetry.class, OpenTelemetry::noop)
            .run(context -> assertThat(context).hasSingleBean(TraceOperations.class));
}

@Test
void shouldCreateNoopTraceOperationsWhenTracesAreDisabled() {
    contextRunner
            .withPropertyValues("reflex.otel.metrics.traces.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(TraceOperations.class);
                assertThat(context.getBean(TraceOperations.class)).isInstanceOf(NoopTraceOperations.class);
            });
}

@Test
void shouldBackOffWhenTraceOperationsProvidedByApplication() {
    TraceOperations custom = mock(TraceOperations.class);

    contextRunner
            .withBean(TraceOperations.class, () -> custom)
            .run(context -> assertThat(context.getBean(TraceOperations.class)).isSameAs(custom));
}
```

- **Step 2: Run auto-configuration tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexOtelMetricsAutoConfigurationTest test
```

Expected: compilation fails or assertions fail because `TraceOperations` is not auto-configured.

- **Step 3: Add trace properties**

Modify `ReflexOtelMetricsProperties`:

```java
private TraceProperties traces = new TraceProperties();

public TraceProperties getTraces() {
    return traces;
}

public void setTraces(TraceProperties traces) {
    this.traces = traces != null ? traces : new TraceProperties();
}

public static class TraceProperties {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
```

- **Step 4: Configure `TraceOperations` bean**

Modify `ReflexOtelMetricsAutoConfiguration` imports:

```java
import ru.sber.rcln.reflex.telemetry.api.TraceOperations;
import ru.sber.rcln.reflex.telemetry.tracing.DefaultTraceOperations;
import ru.sber.rcln.reflex.telemetry.tracing.NoopTraceOperations;
```

Add bean method:

```java
@Bean
@ConditionalOnMissingBean
TraceOperations traceOperations(Tracer tracer, ReflexOtelMetricsProperties properties) {
    if (!properties.getTraces().isEnabled()) {
        return new NoopTraceOperations();
    }
    return new DefaultTraceOperations(tracer);
}
```

- **Step 5: Run auto-configuration tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ReflexOtelMetricsAutoConfigurationTest test
```

Expected: tests pass.

- **Step 6: Commit**

```powershell
git add src/main/java/ru/sber/rcln/reflex/telemetry/config/ReflexOtelMetricsProperties.java src/main/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexOtelMetricsAutoConfiguration.java src/test/java/ru/sber/rcln/reflex/telemetry/autoconfigure/ReflexOtelMetricsAutoConfigurationTest.java
git commit -m "feat: auto-configure trace operations"
```

---

### Task 5: Documentation

**Files:**

- Modify: `README.md`
- **Step 1: Add tracing configuration docs**

Add under configuration:

```yaml
reflex:
  otel:
    metrics:
      traces:
        enabled: true
      otlp:
        traces-endpoint: http://localhost:4317
```

- **Step 2: Add generic API docs**

Document:

```java
TraceCarrier parent = new TraceCarrier(traceparent, tracestate);

traces.inSpan(
        new SpanSpec(
                "workflow.action.GetContractAction",
                parent,
                Map.of(
                        "workflow.process.name", processName,
                        "workflow.action.name", "GetContractAction",
                        "workflow.action.class", GetContractAction.class.getName(),
                        "workflow.business_service_id", businessServiceId)),
        () -> action.execute(context));
```

- **Step 3: Add propagation docs**

Document these rules:

- Store `traceparent` and `tracestate` as opaque strings.
- Do not store spans in the database.
- Do not parse `tracestate` in application code.
- Call `captureCurrent()` inside an active `inSpan(...)`.
- Put the captured carrier into the next process context, queue headers, HTTP headers, or another transport.
- Use attributes for business IDs and workflow names; use `traceparent` for trace linkage.
- **Step 4: Add `ProcessContext` example**

Document the application-side pattern:

```text
inParams   -> initial trace carrier from previous process
params     -> current runtime trace carrier between actions
outParams  -> trace carrier for the next process
```

Suggested keys:

```text
_otel.traceparent
_otel.tracestate
```

- **Step 5: Commit**

```powershell
git add README.md
git commit -m "docs: describe trace operations usage"
```

---

### Task 6: Full Verification

**Files:**

- No source changes expected.
- **Step 1: Run all tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: all tests pass.

- **Step 2: Inspect changed files**

Run:

```powershell
git status --short
git diff --stat HEAD
```

Expected: working tree only contains intended changes if commits were skipped; otherwise clean.

- **Step 3: Final commit if tasks were batched**

If previous tasks were not committed individually:

```powershell
git add pom.xml README.md src/main/java src/test/java
git commit -m "feat: add generic trace operations"
```

---

## Self-Review

- Spec coverage: The plan covers generic trace API, W3C carrier propagation, no-op behavior, auto-configuration, tests, and README examples for `ProcessContext` integration.
- Placeholder scan: No placeholder markers remain. Optional future items such as `CurrentTrace`, annotations, and span links are intentionally out of scope.
- Type consistency: The same public types are used throughout: `TraceOperations`, `SpanSpec`, and `TraceCarrier`.

## Out Of Scope

- Workflow-specific library API.
- Direct dependency on `RclnAction`, `ProcessContext`, or `t_workflowprocess`.
- Annotation-based tracing.
- `CurrentTrace.attribute(...)` and `CurrentTrace.event(...)` facade.
- Span links for fan-out/fan-in workflows.
- Automatic database or queue instrumentation.

