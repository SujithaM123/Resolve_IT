package com.resolveit.dto;

import java.time.LocalDateTime;

public record UserIncidentSummary(
        Long incidentId,
        String incidentCode,
        String title,
        String status,
        String severity,
        String priority,
        LocalDateTime createdAt) {
}
