package com.resolveit.dto;

import java.util.List;

public record RootCauseResult(
        String possibleRootCause,
        int confidence,
        List<String> evidence) {
}
