package com.dtcc.intern.demo.opsai;

import java.util.Arrays;
import java.util.Optional;

public enum OpsAiAction {

    SUMMARIZE,
    SIMILAR,
    ANALYZE,
    ROOT_CAUSE,
    RESOLUTION;

    public static Optional<OpsAiAction> fromRequest(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(action -> action.name().equalsIgnoreCase(trimmed))
                .findFirst();
    }
}
