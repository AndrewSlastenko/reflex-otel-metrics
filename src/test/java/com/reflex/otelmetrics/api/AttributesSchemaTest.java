package com.reflex.otelmetrics.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributesSchemaTest {

    @Test
    void buildsRequiredAndOptionalAttributesWithRejectUnknownByDefault() {
        AttributesSchema schema = AttributesSchema.builder()
                .required("tenant")
                .required("region")
                .optional("status")
                .optional("type")
                .build();

        assertThat(schema.required()).containsExactly("tenant", "region");
        assertThat(schema.optional()).containsExactly("status", "type");
        assertThat(schema.allowed()).containsExactly("tenant", "region", "status", "type");
        assertThat(schema.rejectUnknown()).isTrue();
    }

    @Test
    void rejectsBlankAttributeNames() {
        assertThatThrownBy(() -> AttributesSchema.builder().required(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute");

        assertThatThrownBy(() -> AttributesSchema.builder().optional(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attribute");
    }

    @Test
    void rejectsAttributeNameThatIsBothRequiredAndOptional() {
        assertThatThrownBy(() -> AttributesSchema.builder()
                .required("tenant")
                .optional("tenant"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required")
                .hasMessageContaining("optional");
    }

    @Test
    void exposesImmutableAttributeSets() {
        AttributesSchema schema = AttributesSchema.builder()
                .required("tenant")
                .optional("status")
                .build();

        assertThatThrownBy(() -> schema.required().add("region"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> schema.optional().add("region"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> schema.allowed().add("region"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
