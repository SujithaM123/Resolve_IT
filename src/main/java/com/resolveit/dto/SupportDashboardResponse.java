package com.resolveit.dto;

import java.util.List;

/**
 * The SUPPORT dashboard: the engineer, their workload counters, their incidents
 * and the analytics strip.
 *
 * All three parts are nested because each only ever appears inside this response.
 */
public record SupportDashboardResponse(
        Long supportUserId,
        String name,
        SupportSummary summary,
        List<SupportIncidentSummary> incidents,
        SupportAnalytics analytics) {

    /** Workload counters for the signed-in engineer. */
    public record SupportSummary(
            long totalAssigned,
            long currentlyOpen,
            long resolved,
            String averageResolutionTime) {
    }

    /** One row in the engineer's incident queue. */
    public record SupportIncidentSummary(
            Long incidentId,
            String incidentCode,
            String title,
            String severity,
            String priority,
            String status) {
    }

    /** Recurrence insight across the engineer's incidents. */
    public record SupportAnalytics(
            String mostCommonIssue,
            long recurringIncidents) {
    }
}
