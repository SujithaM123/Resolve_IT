package com.dtcc.intern.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The USER dashboard: who the caller is, and the incidents they reported.
 *
 * UserIncidentSummary is nested because it only ever appears inside this response.
 */
public record UserDashboardResponse(
        Long userId,
        String name,
        List<UserIncidentSummary> incidents) {

    /** One row in the caller's incident list. */
    public record UserIncidentSummary(
            Long incidentId,
            String incidentCode,
            String title,
            String status,
            String severity,
            String priority,
            LocalDateTime createdAt) {
    }
}
