package com.resolveit.dto;

import java.util.List;

public record MarkReadResponse(
        Long incidentId,
        List<Long> updatedMessageIds,
        String status) {
}
