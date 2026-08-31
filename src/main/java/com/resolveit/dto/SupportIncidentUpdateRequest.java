package com.resolveit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportIncidentUpdateRequest(

        @NotBlank(message = "Status is required")
        @Size(max = 30, message = "Status must not exceed 30 characters")
        String status,

        String rootCause,

        String resolution) {
}
