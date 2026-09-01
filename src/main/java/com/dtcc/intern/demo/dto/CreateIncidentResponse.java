package com.dtcc.intern.demo.dto;

import java.time.LocalDateTime;

public record CreateIncidentResponse(
        Long incidentId,
        String incidentCode,
        String title,
        String status,
        String severity,
        String priority,
        Long assignedSupportUserId,
        String assignedSupportName,
        LocalDateTime createdAt) {
}
