package com.dtcc.intern.demo.dto;

import java.util.List;

public record MarkReadResponse(
        Long incidentId,
        List<Long> updatedMessageIds,
        String status) {
}
