package com.resolveit.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The full incident page: the incident, who is on it, the conversation and the
 * status history.
 *
 * AssignedSupport and StatusHistoryEntry are nested because they only ever appear
 * inside this response. MessageResponse is NOT nested - it is also returned on its
 * own by POST /api/incidents/{id}/messages and broadcast over WebSocket, so it stays
 * a top-level DTO.
 */
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

    /** The engineer currently assigned, or null when the incident is unassigned. */
    public record AssignedSupport(
            Long userId,
            String name) {
    }

    /** One entry in the incident's status timeline. */
    public record StatusHistoryEntry(
            String status,
            LocalDateTime changedAt) {
    }
}
