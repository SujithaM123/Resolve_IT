package com.resolveit.dto;

import java.time.LocalDateTime;

public record StatusHistoryEntry(
        String status,
        LocalDateTime changedAt) {
}
