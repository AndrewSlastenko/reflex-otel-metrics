package com.reflex.otelmetrics.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record AttributesSchema(
        Set<String> required,
        Set<String> optional,
        boolean rejectUnknown
) {

    public AttributesSchema {
        required = immutableValidatedSet(required, "required");
        optional = immutableValidatedSet(optional, "optional");
        for (String name : required) {
            if (optional.contains(name)) {
                throw new IllegalArgumentException("attribute cannot be both required and optional: " + name);
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
        return immutableCopy(allowed);
    }

    private static Set<String> immutableValidatedSet(Set<String> names, String group) {
        if (names == null) {
            throw new IllegalArgumentException(group + " attributes cannot be null");
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String name : names) {
            copy.add(validateName(name));
        }
        return immutableCopy(copy);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attribute name cannot be null or blank");
        }
        return name;
    }

    private static Set<String> immutableCopy(Set<String> names) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(names));
    }

    public static final class Builder {
        private final Set<String> required = new LinkedHashSet<>();
        private final Set<String> optional = new LinkedHashSet<>();
        private boolean rejectUnknown = true;

        private Builder() {
        }

        public Builder required(String name) {
            String validName = validateName(name);
            if (optional.contains(validName)) {
                throw new IllegalArgumentException("attribute cannot be both required and optional: " + validName);
            }
            required.add(validName);
            return this;
        }

        public Builder optional(String name) {
            String validName = validateName(name);
            if (required.contains(validName)) {
                throw new IllegalArgumentException("attribute cannot be both required and optional: " + validName);
            }
            optional.add(validName);
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
