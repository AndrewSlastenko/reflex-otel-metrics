package com.reflex.otelmetrics.manual;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AttributeValidationResult(boolean valid, Map<String, String> attributes, String message) {

    public AttributeValidationResult {
        if (valid) {
            attributes = immutableCopy(attributes);
        } else {
            attributes = Map.of();
        }
    }

    public static AttributeValidationResult valid(Map<String, String> attributes) {
        return new AttributeValidationResult(true, attributes, null);
    }

    public static AttributeValidationResult invalid(String message) {
        return new AttributeValidationResult(false, Map.of(), message);
    }

    private static Map<String, String> immutableCopy(Map<String, String> attributes) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
