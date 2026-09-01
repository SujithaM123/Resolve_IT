package com.dtcc.intern.demo.entity;

import java.util.Arrays;
import java.util.Optional;

public enum Priority {

    P1,
    P2,
    P3,
    P4;

    public static Optional<Priority> fromStored(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(priority -> priority.name().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public double workloadWeight() {
        return switch (this) {
            case P1 -> 4.0;
            case P2 -> 3.0;
            case P3 -> 2.0;
            case P4 -> 1.0;
        };
    }
}
