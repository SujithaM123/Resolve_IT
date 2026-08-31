package com.resolveit.dto;

import java.util.List;

public record SupportDashboardResponse(
        Long supportUserId,
        String name,
        SupportSummary summary,
        List<SupportIncidentSummary> incidents,
        SupportAnalytics analytics) {
}
