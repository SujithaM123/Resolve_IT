package com.dtcc.intern.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        @NotBlank(message = "Service is required")
        @Size(max = 120, message = "Service must not exceed 120 characters")
        String service,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must not exceed 50 characters")
        String category,

        @NotBlank(message = "Severity is required")
        @Size(max = 20, message = "Severity must not exceed 20 characters")
        String severity) {
}
