package com.resolveit.dto;

import java.util.List;

public record UserDashboardResponse(
        Long userId,
        String name,
        List<UserIncidentSummary> incidents) {
}
