package com.resolveit.dto;

import jakarta.validation.constraints.NotBlank;

public record OpsAiRequest(

        @NotBlank(message = "Action is required")
        String action) {
}
