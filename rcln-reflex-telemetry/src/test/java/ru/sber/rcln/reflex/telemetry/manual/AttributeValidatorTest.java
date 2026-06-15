package ru.sber.rcln.reflex.telemetry.manual;

import ru.sber.rcln.reflex.telemetry.api.AttributesSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeValidatorTest {

    private final AttributeValidator validator = new AttributeValidator();

    @Test
    void acceptsValidAttributesAndPreservesInputOrder() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("client", "A");
        attributes.put("region", "RU");

        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").optional("region").build(),
                attributes);

        assertThat(result.valid()).isTrue();
        assertThat(result.attributes()).containsExactly(
                Map.entry("client", "A"),
                Map.entry("region", "RU"));
    }

    @Test
    void rejectsMissingRequiredAttribute() {
        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").build(),
                Map.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("missing required attribute 'client'");
    }

    @Test
    void rejectsUnknownAttributeByDefault() {
        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").build(),
                Map.of("client", "A", "extra", "x"));

        assertThat(result.valid()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("unknown attribute 'extra'");
    }

    @Test
    void allowsUnknownAttributeWhenRejectUnknownIsFalse() {
        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").rejectUnknown(false).build(),
                Map.of("client", "A", "extra", "x"));

        assertThat(result.valid()).isTrue();
        assertThat(result.attributes()).containsEntry("extra", "x");
    }

    @Test
    void rejectsBlankAttributeName() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(" ", "A");

        AttributeValidationResult result = validator.validate(AttributesSchema.empty(), attributes);

        assertThat(result.valid()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("attribute name must not be blank");
    }

    @Test
    void rejectsNullAttributeName() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(null, "A");

        AttributeValidationResult result = validator.validate(AttributesSchema.empty(), attributes);

        assertThat(result.valid()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("attribute name must not be blank");
    }

    @Test
    void rejectsBlankAttributeValue() {
        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").build(),
                Map.of("client", " "));

        assertThat(result.valid()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("attribute 'client' value must not be blank");
    }

    @Test
    void rejectsNullAttributeValue() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("client", null);

        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").build(),
                attributes);

        assertThat(result.valid()).isFalse();
        assertThat(result.attributes()).isEmpty();
        assertThat(result.message()).contains("attribute 'client' value must not be blank");
    }

    @Test
    void returnsDefensiveCopyForValidAttributes() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("client", "A");

        AttributeValidationResult result = validator.validate(
                AttributesSchema.builder().required("client").build(),
                attributes);
        attributes.put("client", "B");

        assertThat(result.attributes()).containsEntry("client", "A");
    }
}
