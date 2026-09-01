package com.dtcc.intern.demo.dto;

public record CreateSupportUserResponse(
        Long userId,
        String name,
        String email,
        String role,
        Long teamId,
        String teamName) {
}
