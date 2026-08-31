package com.resolveit.dto;

public record SupportSummary(
        long totalAssigned,
        long currentlyOpen,
        long resolved,
        String averageResolutionTime) {
}
