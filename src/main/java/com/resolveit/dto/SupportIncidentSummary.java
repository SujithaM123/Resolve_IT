package com.resolveit.dto;

public record SupportIncidentSummary(
        Long incidentId,
        String incidentCode,
        String title,
        String severity,
        String priority,
        String status) {
}
