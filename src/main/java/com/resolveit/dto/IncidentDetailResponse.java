package com.resolveit.dto;

import java.time.LocalDateTime;
import java.util.List;

public record IncidentDetailResponse(
        Long incidentId,
        String incidentCode,
        String title,
        String description,
        String service,
        String category,
        String severity,
        String priority,
        String status,
        AssignedSupport assignedSupport,
        String rootCause,
        String resolution,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        List<MessageResponse> messages,
        List<StatusHistoryEntry> statusHistory) {
}
