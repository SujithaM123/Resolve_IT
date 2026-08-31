package com.resolveit.dto;

import java.time.LocalDateTime;

public record MessageResponse(
        Long messageId,
        Long incidentId,
        Long senderId,
        String senderName,
        String senderRole,
        String messageText,
        LocalDateTime sentAt,
        Boolean isRead) {
}
