package com.resolveit.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record MarkReadRequest(

        @NotEmpty(message = "messageIds must contain at least one message ID")
        List<Long> messageIds) {
}
