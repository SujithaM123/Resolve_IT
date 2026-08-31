package com.resolveit.dto;

public record CreateSupportUserResponse(
        Long userId,
        String name,
        String email,
        String role,
        Long teamId,
        String teamName) {
}
