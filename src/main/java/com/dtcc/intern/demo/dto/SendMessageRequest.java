package com.dtcc.intern.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(

        @NotBlank(message = "Message text must not be blank")
        String messageText) {
}
