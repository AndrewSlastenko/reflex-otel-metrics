package com.reflex.otelmetrics.manual;

import com.reflex.otelmetrics.api.AttributesSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AttributeValidator {

    public AttributeValidationResult validate(AttributesSchema schema, Map<String, String> attributes) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");

        Set<String> allowed = schema.allowed();
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

            if (schema.rejectUnknown() && !allowed.contains(key)) {
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
