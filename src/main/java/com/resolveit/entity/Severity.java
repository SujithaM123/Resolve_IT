package com.resolveit.entity;

import java.util.Arrays;
import java.util.Optional;

public enum Severity {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static Optional<Severity> fromStored(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(trimmed))
                .findFirst();
    }
}
