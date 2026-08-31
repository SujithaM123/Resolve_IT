package com.resolveit.dto;

import java.time.LocalDateTime;

public record SupportIncidentUpdateResponse(
        Long incidentId,
        String incidentCode,
        String status,
        String rootCause,
        String resolution,
        LocalDateTime resolvedAt) {
}
